# 가디언즈 라벨 뷰어 (GuardiansViewer + labeling_server)

태블릿(Pleos 에뮬레이터)에서 **PC 폴더의 영상·이미지**를 열고,
라벨된 파일은 **차량·사람·차선 박스**와 함께 재생합니다. 라벨이 없는 파일은 **로딩 화면**으로 바뀐 뒤
PC에서 YOLO로 라벨링하고 나서 재생합니다.

```
[태블릿 앱]                         [PC: labeling_server]
 폴더 목록 ──── /api/files ──────►  파일 나열 + 라벨 여부 판별
 파일 선택 ──── /api/prepare ────►  필요한 단계만 작업 등록
   │                                 ① AVI → MP4 변환(전방 채널)   ffmpeg
   │  (로딩 화면, 0.7초마다 진행률)   ② YOLO 라벨링 + 차선 검출
   │◄─── /api/jobs/{id} ───────────     GPU 인식 → YOLO11s (FP16)
   │                                    GPU 없음 → YOLOv8n (CPU)
 재생 ◄─── /api/labels, /api/media   _labels/…json, _preview/…mp4 저장
   └ 박스 오버레이 (추적 ID 기준 프레임 사이 보간)
```

## 1. PC 서버 실행 (최초 1회 설치)
1. `Hyundai Test\labeling_server\setup_server.bat` 더블클릭 → `.venv` 에 PyTorch(CUDA 12.8, RTX 50 지원)·ultralytics 설치
2. `run_server.bat` 더블클릭 → 콘솔에 `YOLO : yolo11s.pt on cuda:0 …` 또는 `yolov8n.pt on cpu` 표시
   - 보여주는 폴더(root) = `Hyundai Test` 전체 (`--root` 로 변경)
   - 강제로 CPU: `run_server.bat --force-cpu`
   - 모델 변경: 환경변수 `YOLO_GPU_MODEL`, `YOLO_CPU_MODEL` (기본 yolo11s.pt / yolov8n.pt, 첫 실행 시 자동 다운로드)

## 2. 앱 실행
Android Studio 로 `Hyundai Test\GuardiansViewer` 열기 → Sync → Pleos Connect 에뮬레이터에서 Run.
서버 주소: 에뮬레이터 `http://10.0.2.2:8765`, 실제 태블릿 `http://<PC IP>:8765` (같은 Wi-Fi, 방화벽 8765 허용)

## 3. 라벨 판별 규칙 (서버)
1. `Hyundai Test\_labels\<상대경로>.json` (서버가 라벨링한 결과)
2. 영상 옆 `<파일명>.labels.json`
3. `YOLO_분석_작업\guardians\data\raw\<파일명>.json` (오전 작업 사전 라벨, YOLO11s) → `clips\*.mp4` 7개는 바로 박스 재생

## 4. 라벨 JSON (version 2, 오전 data/raw 형식 확장)
```json
{"version":2,"kind":"video","source":"normal/LAP2_….avi","width":1280,"height":720,
 "sample_fps":10,"duration":14.77,"model":"yolo11s.pt","device":"cuda:0","gpu_name":"…",
 "frames":[{"t":0.0,
   "objects":[{"id":3,"cls":"car","conf":0.89,"box":[x1,y1,x2,y2]}],
   "lanes":[{"side":"center","pts":[[x,0.80],[x,0.56]],"conf":0.9}]}]}
```
좌표는 모두 0~1 정규화. 차선은 COCO YOLO 에 클래스가 없어 OpenCV(가는 밝은 줄 top-hat + Hough + 원근 검사)로 추정합니다.

## 5. 앱 소스
| 파일 | 역할 |
|---|---|
| `ui/MainActivity.kt` | 서버 연결·YOLO 백엔드 표시, PC 폴더 탐색, 라벨됨/미라벨 필터·배지, 기기 파일 업로드 |
| `ui/ViewerActivity.kt` | 준비(변환·라벨링) 작업 폴링 → 로딩 화면, 다운로드, VideoView 재생, vsync 동기 오버레이 |
| `ui/OverlayView.kt` | 박스(승용/트럭·버스/사람/이륜 색 구분)·라벨·차선(중앙선 노랑) 그리기 |
| `model/Models.kt` | 라벨 JSON 파서, 시각별 객체 조회(이진 탐색 + 추적 ID 보간) |
| `net/ServerApi.kt` | HTTP 클라이언트(외부 라이브러리 없음) |
