"""가디언즈 라벨링 서버 (PC).

태블릿/에뮬레이터 앱이 HTTP 로 PC 폴더의 영상·이미지를 탐색하고, 라벨 여부를 확인하고,
라벨이 없으면 YOLO 로 라벨링(GPU→YOLO11s / CPU→YOLOv8)한 뒤 결과를 받아 갑니다.

실행:  python server.py --root "D:\\02. SSAFY\\Hyundai Test"  [--port 8765] [--force-cpu]
에뮬레이터에서 접속: http://10.0.2.2:8765   실기기(태블릿): http://<PC LAN IP>:8765

저장 위치 (root 아래, 목록에서는 숨김):
  _labels/<상대경로>.json    라벨 결과 (sidecar)
  _preview/<상대경로>.mp4    앱 재생용 변환본 (AVI 등)
  _uploads/                 태블릿에서 올린 파일

라벨 판별 순서: ① _labels/<경로>.json ② 같은 폴더의 <파일명>.labels.json
               ③ --legacy 폴더의 <파일 stem>.json (오전 작업 data/raw 형식)
"""
import argparse
import hashlib
import subprocess
import tempfile
import itertools
import json
import os
import queue
import re
import threading
import time
import traceback
from dataclasses import dataclass, field

import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse

import labeler
import media

HIDDEN_DIRS = {"_labels", "_preview", "labeling_server", "GuardiansViewer", ".venv", "__pycache__", ".git", ".gradle", ".idea", "build"}


# ================================================================ 설정
class Cfg:
    root = ""
    legacy = []
    sample_fps = 10.0
    backend = {}


def rel_of(abs_path):
    return os.path.relpath(abs_path, Cfg.root).replace("\\", "/")


def safe_abs(rel):
    rel = (rel or "").replace("\\", "/").lstrip("/")
    p = os.path.normpath(os.path.join(Cfg.root, rel))
    root = os.path.normpath(Cfg.root)
    if os.path.commonpath([p, root]) != root:
        raise HTTPException(400, "root 밖 경로는 접근할 수 없습니다")
    return p


def sidecar_path(rel):
    return os.path.join(Cfg.root, "_labels", rel.replace("/", os.sep) + ".json")


def preview_path(rel):
    return os.path.join(Cfg.root, "_preview", rel.replace("/", os.sep) + ".mp4")


def find_label(rel):
    """(경로, 출처) 또는 (None, None). 라벨 파일 유무로 '라벨링된 영상'을 판별."""
    sc = sidecar_path(rel)
    if os.path.isfile(sc):
        return sc, "sidecar"
    adj = safe_abs(rel) + ".labels.json"
    if os.path.isfile(adj):
        return adj, "adjacent"
    stem = os.path.splitext(os.path.basename(rel))[0]
    for d in Cfg.legacy:
        lp = os.path.join(d, stem + ".json")
        if os.path.isfile(lp):
            return lp, "legacy"
    return None, None


def load_label(rel):
    lp, src = find_label(rel)
    if not lp:
        return None, None
    try:
        with open(lp, encoding="utf-8") as f:
            d = json.load(f)
        if not isinstance(d.get("frames"), list):
            return None, None
    except Exception:
        return None, None
    # 오전 형식(version 없음) 보정
    d.setdefault("version", 1)
    d.setdefault("kind", media.kind_of(rel) or "video")
    d.setdefault("source", rel)
    d.setdefault("device", "unknown")
    d["label_source"] = src
    return d, src


def playable_path(rel):
    """앱에 보낼 재생 파일. 변환이 필요한데 아직 없으면 None."""
    ap = safe_abs(rel)
    if media.kind_of(ap) == "image" or not media.needs_transcode(ap):
        return ap
    pp = preview_path(rel)
    if os.path.isfile(pp) and os.path.getmtime(pp) >= os.path.getmtime(ap):
        return pp
    return None


# ================================================================ 작업 큐
@dataclass
class Job:
    id: str
    path: str
    kind: str
    steps: list
    relabel: bool = False
    state: str = "queued"          # queued | running | done | error | canceled
    step: str = ""
    step_index: int = 0
    progress: float = 0.0          # 현재 단계 진행률 0~1
    done_frames: int = 0
    total_frames: int = 0
    fps: float = 0.0
    message: str = "대기 중"
    error: str = ""
    created: float = field(default_factory=time.time)
    finished: float = 0.0
    cancel: bool = False

    def view(self):
        eta = None
        if self.step == "label" and self.fps > 0 and self.total_frames:
            eta = round((self.total_frames - self.done_frames) / self.fps, 1)
        overall = (self.step_index + self.progress) / max(1, len(self.steps))
        if self.state == "done":
            overall = 1.0
        return {"id": self.id, "path": self.path, "kind": self.kind, "state": self.state,
                "steps": self.steps, "step": self.step, "step_index": self.step_index,
                "progress": round(self.progress, 4), "overall": round(overall, 4),
                "done_frames": self.done_frames, "total_frames": self.total_frames,
                "fps": round(self.fps, 2), "eta_s": eta, "message": self.message, "error": self.error,
                "model": Cfg.backend.get("model"), "device": Cfg.backend.get("device"),
                "gpu_name": Cfg.backend.get("gpu_name"), "queue_pos": queue_position(self.id)}


JOBS = {}
JOB_Q = queue.Queue()
JOB_LOCK = threading.Lock()
_ids = itertools.count(1)


def queue_position(job_id):
    with JOB_LOCK:
        pending = [j for j in sorted(JOBS.values(), key=lambda j: j.created) if j.state == "queued"]
    for i, j in enumerate(pending):
        if j.id == job_id:
            return i + 1
    return 0


def active_job_for(rel):
    with JOB_LOCK:
        for j in JOBS.values():
            if j.path == rel and j.state in ("queued", "running"):
                return j
    return None


def worker():
    while True:
        job = JOB_Q.get()
        if job.cancel:
            job.state = "canceled"
            continue
        job.state = "running"
        try:
            run_job(job)
            job.state, job.message, job.progress = "done", "완료", 1.0
        except Exception as e:
            traceback.print_exc()
            job.state = "canceled" if job.cancel else "error"
            job.error = str(e)
            job.message = "취소됨" if job.cancel else f"실패: {e}"
        job.finished = time.time()


def run_job(job):
    ap = safe_abs(job.path)
    cancel = lambda: job.cancel
    for i, step in enumerate(job.steps):
        job.step, job.step_index, job.progress = step, i, 0.0
        if step == "transcode":
            job.message = "재생용 MP4 변환 중"

            def tp(p):
                job.progress = p
            media.transcode_preview(ap, preview_path(job.path), tp, cancel)
        elif step == "label":
            be = Cfg.backend
            job.message = f"라벨링 중 · {be['family']} ({'GPU' if be['gpu'] else 'CPU'})"
            if job.kind == "image":
                out = labeler.label_image(ap, job.path, be)
            else:
                src = playable_path(job.path) or ap

                def lp(p, done, total, fps):
                    job.progress, job.done_frames, job.total_frames, job.fps = p, done, total, fps
                out = labeler.label_video(src, job.path, be, Cfg.sample_fps, lp, cancel)
            labeler.write_json_atomic(out, sidecar_path(job.path))
            print(f"[label] {job.path}: {len(out['frames'])} frames, {out.get('elapsed_s')} s, "
                  f"{be['model']} on {be['device']}", flush=True)
        job.progress = 1.0


# ================================================================ API
app = FastAPI(title="Guardians Labeling Server")


@app.get("/", response_class=HTMLResponse)
def index():
    be = Cfg.backend
    return (f"<h2>Guardians Labeling Server</h2><p>root: {Cfg.root}</p>"
            f"<p>YOLO: {be.get('model')} on {be.get('device')} ({be.get('gpu_name') or 'CPU'})"
            f" — {be.get('reason')}</p><p><a href='/api/files'>/api/files</a></p>")


@app.get("/api/health")
def health():
    be = Cfg.backend
    return {"ok": True, "root": Cfg.root, "sample_fps": Cfg.sample_fps,
            "model": be.get("model"), "family": be.get("family"), "device": be.get("device"),
            "gpu": be.get("gpu"), "gpu_name": be.get("gpu_name"), "reason": be.get("reason"),
            "legacy_dirs": [rel_of(d) for d in Cfg.legacy],
            "jobs_running": sum(1 for j in JOBS.values() if j.state == "running"),
            "jobs_queued": sum(1 for j in JOBS.values() if j.state == "queued")}


def _natural_key(name):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", name)]


@app.get("/api/files")
def files(dir: str = ""):
    ad = safe_abs(dir)
    if not os.path.isdir(ad):
        raise HTTPException(404, "폴더가 없습니다")
    entries = []
    with os.scandir(ad) as it:
        for e in it:
            if e.name.startswith(".") or e.name in HIDDEN_DIRS:
                continue
            rel = rel_of(e.path)
            if e.is_dir():
                if e.name.startswith("_") and e.name != "_uploads":
                    continue
                n_media = 0
                try:
                    n_media = sum(1 for x in os.scandir(e.path) if x.is_file() and media.kind_of(x.name))
                except OSError:
                    pass
                entries.append({"name": e.name, "path": rel, "type": "dir", "media_count": n_media})
                continue
            k = media.kind_of(e.name)
            if not k or e.name.endswith(".labels.json"):
                continue
            lp, src = find_label(rel)
            job = active_job_for(rel)
            st = e.stat()
            entries.append({"name": e.name, "path": rel, "type": k, "size": st.st_size,
                            "mtime": int(st.st_mtime), "labeled": lp is not None, "label_source": src,
                            "preview_ready": playable_path(rel) is not None,
                            "job": job.id if job else None,
                            # 목록에서 일괄 분석 진행률 표시용
                            "job_state": job.state if job else None,
                            "job_step": job.step if job else None,
                            "job_progress": job.view()["overall"] if job else None,
                            "job_queue": queue_position(job.id) if job else None})
    entries.sort(key=lambda x: (x["type"] != "dir", _natural_key(x["name"])))
    parent = None if not dir.strip("/") else os.path.dirname(dir.strip("/"))
    s = [x for x in entries if x["type"] != "dir"]
    return {"dir": dir.strip("/"), "parent": parent, "entries": entries,
            "summary": {"media": len(s), "labeled": sum(1 for x in s if x["labeled"])}}


@app.get("/api/info")
def info(path: str):
    ap = safe_abs(path)
    if not os.path.isfile(ap):
        raise HTTPException(404, "파일이 없습니다")
    lp, src = find_label(path)
    job = active_job_for(path)
    return {"path": path, "kind": media.kind_of(ap), "size": os.path.getsize(ap),
            "labeled": lp is not None, "label_source": src,
            "preview_ready": playable_path(path) is not None, "job": job.view() if job else None}


@app.post("/api/prepare")
def prepare(path: str, relabel: int = 0):
    """재생 준비: 필요한 단계(transcode/label)만 작업으로 등록. 모두 준비돼 있으면 ready=true."""
    ap = safe_abs(path)
    kind = media.kind_of(ap)
    if not os.path.isfile(ap) or not kind:
        raise HTTPException(404, "영상/이미지 파일이 아닙니다")
    existing = active_job_for(path)
    if existing:
        return {"ready": False, "job": existing.view()}
    steps = []
    if kind == "video" and playable_path(path) is None:
        steps.append("transcode")
    lp, _ = find_label(path)
    if relabel or lp is None:
        steps.append("label")
    if not steps:
        return {"ready": True, "job": None, "labeled": True}
    job = Job(id=f"j{next(_ids)}", path=path, kind=kind, steps=steps, relabel=bool(relabel))
    with JOB_LOCK:
        JOBS[job.id] = job
    JOB_Q.put(job)
    return {"ready": False, "job": job.view()}


@app.get("/api/jobs/{job_id}")
def job_status(job_id: str):
    j = JOBS.get(job_id)
    if not j:
        raise HTTPException(404, "작업이 없습니다")
    return j.view()


@app.post("/api/jobs/{job_id}/cancel")
def job_cancel(job_id: str):
    j = JOBS.get(job_id)
    if not j:
        raise HTTPException(404, "작업이 없습니다")
    j.cancel = True
    return j.view()


@app.get("/api/media")
def get_media(path: str):
    """앱 재생용 파일 (영상은 변환본, 이미지는 원본)."""
    pp = playable_path(path)
    if not pp:
        raise HTTPException(409, "재생 파일이 아직 준비되지 않았습니다 (/api/prepare 먼저)")
    mt = "video/mp4" if media.kind_of(path) == "video" else None
    return FileResponse(pp, media_type=mt, filename=os.path.basename(pp))


@app.get("/api/labels")
def get_labels(path: str):
    d, _ = load_label(path)
    if d is None:
        raise HTTPException(404, "라벨이 없습니다")
    return JSONResponse(d)


@app.put("/api/upload")
async def upload(request: Request, name: str):
    """태블릿 로컬 파일 업로드 (본문 = 파일 바이트). _uploads/ 에 저장 후 상대경로 반환."""
    base = re.sub(r"[\\/:*?\"<>|]+", "_", os.path.basename(name)).strip() or "upload"
    if not media.kind_of(base):
        raise HTTPException(400, "영상/이미지 확장자만 업로드할 수 있습니다")
    d = os.path.join(Cfg.root, "_uploads")
    os.makedirs(d, exist_ok=True)
    stem, ext = os.path.splitext(base)
    dst, n = os.path.join(d, base), 1
    while os.path.exists(dst):
        dst = os.path.join(d, f"{stem}_{n}{ext}")
        n += 1
    tmp = dst + ".part"
    size = 0
    with open(tmp, "wb") as f:
        async for chunk in request.stream():
            f.write(chunk)
            size += len(chunk)
    os.replace(tmp, dst)
    return {"path": rel_of(dst), "size": size}



# ================================================================ PC 음성 (에뮬레이터에 한국어 TTS 엔진이 없을 때 대체)
TTS_DIR = os.path.join(tempfile.gettempdir(), "guardians_tts")
_PS_TTS = r"""
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq 'ko-KR' } | Select-Object -First 1
if (-not $v) { [Console]::Error.WriteLine('NO_KOREAN_VOICE'); exit 3 }
$s.SelectVoice($v.VoiceInfo.Name)
$s.SetOutputToWaveFile($env:TTS_OUT)
$s.Speak([IO.File]::ReadAllText($env:TTS_TEXT, [Text.Encoding]::UTF8))
$s.Dispose()
"""
_tts_lock = threading.Lock()


@app.get("/api/tts")
def tts(text: str):
    """Windows 한국어 음성(SAPI)으로 WAV 합성. MOVO 앱이 기기 TTS 엔진이 없을 때 재생합니다."""
    text = (text or "").strip()[:200]
    if not text:
        raise HTTPException(400, "text 가 필요합니다")
    if os.name != "nt":
        raise HTTPException(501, "Windows 음성 합성만 지원합니다")
    os.makedirs(TTS_DIR, exist_ok=True)
    key = hashlib.sha1(text.encode("utf-8")).hexdigest()[:16]
    out = os.path.join(TTS_DIR, key + ".wav")
    with _tts_lock:
        if not os.path.exists(out):
            tf = os.path.join(TTS_DIR, key + ".txt")
            with open(tf, "w", encoding="utf-8") as f:
                f.write(text)
            part = out + ".part.wav"
            env = dict(os.environ, TTS_OUT=part, TTS_TEXT=tf)
            r = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                                "-Command", _PS_TTS], env=env, capture_output=True, text=True, errors="replace", timeout=40)
            if r.returncode != 0 or not os.path.exists(part):
                raise HTTPException(503, "PC 한국어 음성 합성 실패: " + ((r.stderr or "") + (r.stdout or ""))[-300:])
            os.replace(part, out)
    return FileResponse(out, media_type="audio/wav")


# ================================================================ main
def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.dirname(here), help="앱에 보여줄 PC 폴더 (기본: 서버 폴더의 상위)")
    ap.add_argument("--legacy", action="append", default=None, help="기존 라벨 JSON 폴더 (여러 번 지정 가능)")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--sample-fps", type=float, default=10.0)
    ap.add_argument("--force-cpu", action="store_true")
    a = ap.parse_args()

    Cfg.root = os.path.abspath(a.root)
    legacy = a.legacy if a.legacy is not None else [os.path.join("YOLO_분석_작업", "guardians", "data", "raw")]
    Cfg.legacy = [d if os.path.isabs(d) else os.path.join(Cfg.root, d) for d in legacy]
    Cfg.legacy = [d for d in Cfg.legacy if os.path.isdir(d)]
    Cfg.sample_fps = a.sample_fps
    Cfg.backend = labeler.select_backend(a.force_cpu)
    be = Cfg.backend
    print("=" * 64)
    print(f" root    : {Cfg.root}")
    print(f" legacy  : {Cfg.legacy}")
    print(f" ffmpeg  : {media.ffmpeg_exe()}")
    print(f" YOLO    : {be['model']} on {be['device']} {be['gpu_name'] or ''}")
    print(f" 판단 근거: {be['reason']}")
    print(f" 접속    : 에뮬레이터 http://10.0.2.2:{a.port}  /  같은 LAN http://<PC IP>:{a.port}")
    print("=" * 64, flush=True)
    threading.Thread(target=worker, daemon=True).start()
    uvicorn.run(app, host=a.host, port=a.port, log_level="warning")


if __name__ == "__main__":
    main()
