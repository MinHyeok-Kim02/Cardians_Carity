"""YOLO 라벨러: GPU 판별 → 모델 선택, 객체 검출/추적 + 차선 검출, 라벨 JSON 생성.

모델 선택 규칙 (요구사항 3):
  - CUDA GPU 가 인식되고 실제 연산까지 성공 → YOLO11s (GPU, FP16)
  - 그 외 (GPU 없음 / CUDA 빌드 아님 / 커널 미지원) → YOLOv8 (CPU)
  환경변수로 모델 파일 변경 가능: YOLO_GPU_MODEL (기본 yolo11s.pt), YOLO_CPU_MODEL (기본 yolov8n.pt)

라벨 JSON (version 2) 은 오전 작업의 data/raw/*.json 형식을 그대로 확장한 것이라
기존 라벨 파일도 앱에서 그대로 읽힙니다.
  {version, kind, source, clip, width, height, src_fps, sample_fps, duration,
   model, device, gpu_name, created, classes,
   frames: [{t, objects: [{id, cls, conf, box:[x1,y1,x2,y2](0~1)}],
             lanes:   [{side, pts:[[x,y],[x,y]](0~1), conf}]}]}
"""
import os
import time
import json
import math

import cv2
import numpy as np

import media

# COCO → 앱에서 쓰는 클래스 (오전 prelabel_yolo.py 와 동일)
CLASSES = {0: "person", 1: "bicycle", 2: "car", 3: "motorcycle", 5: "bus", 7: "truck"}
GPU_MODEL = os.environ.get("YOLO_GPU_MODEL", "yolo11s.pt")
CPU_MODEL = os.environ.get("YOLO_CPU_MODEL", "yolov8n.pt")
IMGSZ = 640
CONF = 0.25


# ---------------------------------------------------------------- backend 선택
def select_backend(force_cpu=False):
    info = {"device": "cpu", "gpu": False, "gpu_name": None, "model": CPU_MODEL,
            "family": "YOLOv8", "half": False, "reason": ""}
    if force_cpu:
        info["reason"] = "--force-cpu 옵션으로 CPU 사용"
        return info
    try:
        import torch
        if not torch.cuda.is_available():
            build = getattr(torch.version, "cuda", None)
            info["reason"] = ("CUDA 장치 미검출" if build else
                              "CPU 전용 PyTorch 빌드 (CUDA 버전 torch 필요)")
            return info
        # 장치가 보이더라도 커널이 없을 수 있음(RTX 50 + 구버전 torch) → 실제 연산으로 확인
        x = torch.ones(16, device="cuda")
        float((x * 2).sum().item())
        info.update(device="cuda:0", gpu=True, gpu_name=torch.cuda.get_device_name(0),
                    model=GPU_MODEL, family="YOLO11", half=True,
                    reason="CUDA GPU 인식")
    except Exception as e:  # GPU 가 있어도 쓸 수 없으면 CPU
        info["reason"] = f"GPU 사용 불가 → CPU 전환 ({type(e).__name__}: {str(e)[:120]})"
    return info


def load_model(backend):
    from ultralytics import YOLO
    model = YOLO(backend["model"])
    return model


def _yolo_kwargs(backend):
    kw = dict(imgsz=IMGSZ, conf=CONF, classes=list(CLASSES), verbose=False,
              device=0 if backend["gpu"] else "cpu")
    if backend["half"]:
        kw["half"] = True          # GPU FP16
    return kw


def is_ego_hood(box):
    """블랙박스 하단 보닛(자차)을 차량으로 오검출한 박스 제거: 화면 아래쪽에 붙은 넓고 납작한 박스."""
    x1, y1, x2, y2 = box
    return y1 > 0.55 and y2 > 0.90 and (x2 - x1) > 0.30


def _objects(result, with_ids=True):
    objs = []
    b = result.boxes
    if b is None or len(b) == 0:
        return objs
    ids = b.id.int().tolist() if (with_ids and b.id is not None) else [-1] * len(b)
    for tid, c, cf, xy in zip(ids, b.cls.int().tolist(), b.conf.tolist(), b.xyxyn.tolist()):
        if c not in CLASSES or is_ego_hood(xy):
            continue
        objs.append({"id": int(tid), "cls": CLASSES[c], "conf": round(float(cf), 3),
                     "box": [round(float(v), 4) for v in xy]})
    return objs


# ---------------------------------------------------------------- 차선 검출 (고전 CV)
HORIZON = 0.53    # 오전 작업 카메라 설정 (지평선 y, 정규화)


class LaneDetector:
    """블랙박스 전방 영상용 차선 검출 (COCO YOLO 에 차선 클래스가 없어서 OpenCV 사용).

    1) 밝기(L) 채널 가로 top-hat → 주변보다 밝고 '가는' 줄무늬만 남김 (차량 차체·벽 같은 넓은 밝은 면 제거)
       + 노란색(중앙선) 마스크
    2) 사다리꼴 ROI(지평선 아래 ~ 보닛 위) 안에서 확률적 Hough 선분
    3) 선분을 하단/상단 x 위치로 군집 → 군집별 가중 직선 피팅 → 근거(선분 길이 합) 상위 3개
    4) 프레임 간 매칭 + EMA, 일시 미검출은 hold 프레임 동안 유지
    side: 하단 x 가 화면 중앙±8% 이내면 center(정면 중앙선), 그 외 left/right
    """

    def __init__(self, y_top=0.56, y_bot=0.80, smooth=0.5, hold=4, max_lanes=3):
        self.y_top, self.y_bot, self.smooth, self.hold, self.max_lanes = y_top, y_bot, smooth, hold, max_lanes
        self.tracks = []        # [{xb, xt, conf, miss}] (정규화 좌표)

    def _segments(self, frame, boxes=()):
        H, W = frame.shape[:2]
        sw = 640
        sh = int(round(H * sw / W))
        small = cv2.resize(frame, (sw, sh), interpolation=cv2.INTER_AREA)
        hls = cv2.cvtColor(small, cv2.COLOR_BGR2HLS)
        L = hls[:, :, 1]
        tophat = cv2.morphologyEx(L, cv2.MORPH_TOPHAT, np.ones((1, 21), np.uint8))
        stripe = ((tophat > 28) & (L > 115)).astype(np.uint8) * 255
        yellow = cv2.inRange(hls, (12, 70, 90), (38, 230, 255))
        mask = cv2.bitwise_or(stripe, yellow)
        yt, yb = int(self.y_top * sh), int(self.y_bot * sh)
        roi = np.zeros_like(mask)
        poly = np.array([[(0, yb), (int(0.30 * sw), yt), (int(0.70 * sw), yt), (sw, yb)]], np.int32)
        cv2.fillPoly(roi, poly, 255)
        mask = cv2.bitwise_and(mask, roi)
        for x1, y1, x2, y2 in boxes:                          # 차량·사람 박스 안의 밝은 줄(차체 반사 등) 제외
            mx, my = 0.02 * (x2 - x1) + 0.005, 0.02 * (y2 - y1) + 0.005
            cv2.rectangle(mask, (int((x1 - mx) * sw), int((y1 - my) * sh)),
                          (int((x2 + mx) * sw), int((y2 + my) * sh)), 0, -1)
        lines = cv2.HoughLinesP(mask, 1, np.pi / 180, threshold=18, minLineLength=14, maxLineGap=10)
        segs = []
        if lines is None:
            return segs, sw, sh
        for x1, y1, x2, y2 in np.asarray(lines).reshape(-1, 4).astype(float):
            dx, dy = x2 - x1, y2 - y1
            if abs(dy) < 0.3 * abs(dx) or abs(dy) < 4:       # 수평에 가까운 선(정지선·그림자) 제외
                continue
            inv = dx / dy                                     # x = x1 + (y - y1) * inv
            xb = (x1 + (yb - y1) * inv) / sw
            xt = (x1 + (yt - y1) * inv) / sw
            if not (-0.2 < xb < 1.2):
                continue
            segs.append((xb, xt, math.hypot(dx, dy) / sh, (x1, y1, x2, y2)))
        return segs, sw, sh

    def detect(self, frame, boxes=()):
        segs, sw, sh = self._segments(frame, boxes)
        yt, yb = self.y_top * sh, self.y_bot * sh
        clusters = []
        for s in sorted(segs, key=lambda s: -s[2]):
            for c in clusters:
                if abs(s[0] - c["xb"]) < 0.09 and abs(s[1] - c["xt"]) < 0.045:
                    c["segs"].append(s)
                    c["len"] += s[2]
                    break
            else:
                clusters.append({"xb": s[0], "xt": s[1], "len": s[2], "segs": [s]})
        found = []
        for c in sorted(clusters, key=lambda c: -c["len"])[: self.max_lanes]:
            if c["len"] < 0.10:                                # 근거 부족
                continue
            ys = np.array([v for s in c["segs"] for v in (s[3][1], s[3][3])])
            xs = np.array([v for s in c["segs"] for v in (s[3][0], s[3][2])])
            ws = np.array([s[2] for s in c["segs"] for _ in (0, 1)])
            if np.ptp(ys) > 3:
                a, b = np.polyfit(ys, xs, 1, w=np.sqrt(ws))      # x = a*y + b
                xb, xt = (a * yb + b) / sw, (a * yt + b) / sw
            else:
                xb, xt = c["xb"], c["xt"]
            # 원근 일관성: 평지 차선은 지평선(소실점 부근)으로 모인다 → 지평선 높이에서 x 가 화면 중앙부여야 함
            xh = xt + (xt - xb) * (self.y_top - HORIZON) / (self.y_bot - self.y_top)
            if not (0.2 <= xh <= 0.8):
                continue
            found.append({"xb": xb, "xt": xt, "conf": min(1.0, c["len"] / 0.45)})

        # 이전 프레임 트랙과 매칭 → EMA
        k = self.smooth
        used = set()
        for tr in self.tracks:
            best, bd = None, 0.07
            for i, f in enumerate(found):
                d = abs(f["xb"] - tr["xb"]) * 0.5 + abs(f["xt"] - tr["xt"])
                if i not in used and d < bd:
                    best, bd = i, d
            if best is None:
                tr["miss"] += 1
                tr["conf"] *= 0.7
            else:
                f = found[best]
                used.add(best)
                tr.update(xb=k * tr["xb"] + (1 - k) * f["xb"], xt=k * tr["xt"] + (1 - k) * f["xt"],
                          conf=max(f["conf"], 0.5 * tr["conf"]), miss=0)
        self.tracks = [t for t in self.tracks if t["miss"] <= self.hold]
        for i, f in enumerate(found):
            if i not in used:
                self.tracks.append(dict(f, miss=0))
        # 거의 같은 선(평행 중복) 제거
        kept = []
        for t in sorted(self.tracks, key=lambda t: -t["conf"]):
            if all(abs(t["xb"] - u["xb"]) > 0.08 or abs(t["xt"] - u["xt"]) > 0.04 for u in kept):
                kept.append(t)
        self.tracks = kept[: self.max_lanes]

        out = []
        for t in sorted(self.tracks, key=lambda t: t["xb"]):
            side = "center" if abs(t["xb"] - 0.5) < 0.08 else ("left" if t["xb"] < 0.5 else "right")
            out.append({"side": side,
                        "pts": [[round(t["xb"], 4), self.y_bot], [round(t["xt"], 4), self.y_top]],
                        "conf": round(t["conf"], 2)})
        return out


# ---------------------------------------------------------------- 라벨링
def _header(kind, rel, backend, w, h, src_fps, sample_fps, duration):
    return {"version": 2, "kind": kind, "source": rel.replace("\\", "/"),
            "clip": os.path.splitext(os.path.basename(rel))[0],
            "width": w, "height": h, "src_fps": src_fps, "sample_fps": sample_fps,
            "duration": round(duration, 3), "model": backend["model"], "device": backend["device"],
            "gpu_name": backend["gpu_name"], "created": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "classes": sorted(set(CLASSES.values())) + ["lane"], "frames": []}


def label_video(video_path, rel, backend, sample_fps=10.0, on_progress=None, cancel=None):
    info = media.probe(video_path)
    total = max(1, int(math.ceil(info["duration"] * sample_fps)))
    model = load_model(backend)            # 영상마다 새로 로드 → 트래커 초기화
    lanes = LaneDetector()
    kw = _yolo_kwargs(backend)
    w = h = 0
    frames = []
    t0 = time.time()
    for k, t, frame in media.iter_frames(video_path, sample_fps):
        if cancel and cancel():
            raise RuntimeError("취소됨")
        h, w = frame.shape[:2]
        r = model.track(frame, persist=True, tracker="bytetrack.yaml", **kw)[0]
        objs = _objects(r)
        frames.append({"t": t, "objects": objs, "lanes": lanes.detect(frame, [o["box"] for o in objs])})
        if on_progress:
            done = k + 1
            el = time.time() - t0
            on_progress(min(0.999, done / total), done, total, done / el if el > 0 else 0.0)
    out = _header("video", rel, backend, w or info["width"], h or info["height"],
                  info["fps"], sample_fps, info["duration"])
    out["frames"] = frames
    out["elapsed_s"] = round(time.time() - t0, 2)
    return out


def label_image(image_path, rel, backend):
    img = media.read_image(image_path)
    model = load_model(backend)
    t0 = time.time()
    r = model.predict(img, **_yolo_kwargs(backend))[0]
    h, w = img.shape[:2]
    out = _header("image", rel, backend, w, h, 0, 0, 0)
    objs = _objects(r, with_ids=False)
    out["frames"] = [{"t": 0.0, "objects": objs,
                      "lanes": LaneDetector(smooth=0, hold=0).detect(img, [o["box"] for o in objs])}]
    out["elapsed_s"] = round(time.time() - t0, 2)
    return out


def write_json_atomic(obj, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".part"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)
