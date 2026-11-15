"""미디어 유틸: 파일 종류 판별, ffmpeg 위치, 영상 정보(probe), 미리보기 MP4 변환, 프레임 디코딩.

- Windows 한글 경로에서 cv2.VideoCapture 가 열리지 않는 문제를 피하려고
  영상 디코딩은 전부 ffmpeg 파이프(rawvideo)로 처리합니다.
- 블랙박스 AVI 는 전방(1080p)+후방(720p) 2개 영상 스트림이라 안드로이드에서 바로 재생되지 않습니다.
  첫 번째 영상 스트림(전방)만 뽑아 H.264 MP4(최대 1280px)로 변환합니다.
"""
import os
import re
import shutil
import subprocess

import numpy as np

VIDEO_EXT = {".mp4", ".m4v", ".avi", ".mov", ".mkv", ".3gp", ".webm", ".ts"}
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
# 원본을 그대로 앱에 보내도 되는 컨테이너 (그 외는 변환)
DIRECT_PLAY_EXT = {".mp4", ".m4v"}
PREVIEW_MAX_W = 1280

_FFMPEG = None


def kind_of(path):
    ext = os.path.splitext(path)[1].lower()
    if ext in VIDEO_EXT:
        return "video"
    if ext in IMAGE_EXT:
        return "image"
    return None


def ffmpeg_exe():
    """시스템 ffmpeg 우선, 없으면 imageio-ffmpeg 내장 바이너리."""
    global _FFMPEG
    if _FFMPEG:
        return _FFMPEG
    exe = shutil.which("ffmpeg")
    if not exe:
        try:
            import imageio_ffmpeg
            exe = imageio_ffmpeg.get_ffmpeg_exe()
        except Exception as e:  # pragma: no cover
            raise RuntimeError("ffmpeg 를 찾을 수 없습니다. pip install imageio-ffmpeg 를 실행하세요.") from e
    _FFMPEG = exe
    return exe


_RE_DUR = re.compile(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)")
_RE_VID = re.compile(r"Stream #\d+:\d+.*?Video:.*?(\d{2,5})x(\d{2,5})")
_RE_FPS = re.compile(r"(\d+(?:\.\d+)?)\s*fps")


def probe(path):
    """ffmpeg -i 의 stderr 를 읽어 첫 영상 스트림의 (w, h, fps, duration)."""
    p = subprocess.run([ffmpeg_exe(), "-hide_banner", "-nostdin", "-i", path],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    err = p.stderr.decode("utf-8", "replace")
    info = {"width": 0, "height": 0, "fps": 0.0, "duration": 0.0}
    m = _RE_DUR.search(err)
    if m:
        h, mi, s = m.groups()
        info["duration"] = int(h) * 3600 + int(mi) * 60 + float(s)
    for line in err.splitlines():
        if "Video:" in line:
            mv = _RE_VID.search(line)
            if mv:
                info["width"], info["height"] = int(mv.group(1)), int(mv.group(2))
            mf = _RE_FPS.search(line)
            if mf:
                info["fps"] = float(mf.group(1))
            break
    if info["width"] == 0:
        raise RuntimeError(f"영상 스트림을 찾지 못했습니다: {os.path.basename(path)}")
    return info


def needs_transcode(path):
    return os.path.splitext(path)[1].lower() not in DIRECT_PLAY_EXT


def transcode_preview(src, dst, on_progress=None, cancel=None):
    """첫 영상 스트림 → H.264 MP4 (faststart, 오디오 제거). on_progress(0~1)."""
    info = probe(src)
    dur = max(info["duration"], 0.001)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    tmp = dst + ".part.mp4"
    cmd = [ffmpeg_exe(), "-hide_banner", "-nostdin", "-y", "-i", src,
           "-map", "0:v:0", "-an", "-sn", "-dn",
           "-vf", f"scale='min({PREVIEW_MAX_W},iw)':-2",
           "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
           "-movflags", "+faststart", "-progress", "pipe:1", "-nostats", tmp]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    try:
        for raw in proc.stdout:
            line = raw.decode("ascii", "ignore").strip()
            if line.startswith("out_time_us=") or line.startswith("out_time_ms="):
                try:
                    us = int(line.split("=", 1)[1])
                    if on_progress:
                        on_progress(min(0.999, us / 1e6 / dur))
                except ValueError:
                    pass
            if cancel and cancel():
                proc.kill()
                raise RuntimeError("취소됨")
        rc = proc.wait()
    finally:
        if proc.poll() is None:
            proc.kill()
    if rc != 0 or not os.path.exists(tmp):
        raise RuntimeError(f"ffmpeg 변환 실패 (code {rc})")
    os.replace(tmp, dst)
    if on_progress:
        on_progress(1.0)
    return dst


def iter_frames(path, sample_fps, max_w=PREVIEW_MAX_W):
    """ffmpeg 로 sample_fps 간격 프레임을 BGR ndarray 로 순차 반환: (index, t, frame)."""
    info = probe(path)
    w, h = info["width"], info["height"]
    if w > max_w:
        h = int(round(h * max_w / w / 2) * 2)
        w = max_w
    cmd = [ffmpeg_exe(), "-hide_banner", "-nostdin", "-loglevel", "error", "-i", path,
           "-map", "0:v:0", "-vf", f"fps={sample_fps},scale={w}:{h}",
           "-f", "rawvideo", "-pix_fmt", "bgr24", "pipe:1"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=w * h * 3 * 2)
    size = w * h * 3
    k = 0
    try:
        while True:
            buf = proc.stdout.read(size)
            if not buf or len(buf) < size:
                break
            frame = np.frombuffer(buf, np.uint8).reshape(h, w, 3)
            yield k, round(k / sample_fps, 3), frame
            k += 1
    finally:
        if proc.poll() is None:
            proc.kill()
        proc.wait()


def read_image(path):
    """한글 경로 안전한 이미지 읽기."""
    import cv2
    data = np.fromfile(path, dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError(f"이미지를 읽지 못했습니다: {os.path.basename(path)}")
    return img
