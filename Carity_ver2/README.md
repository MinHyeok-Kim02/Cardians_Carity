# Carity — 좁은 길 통과 보조

![version](https://img.shields.io/badge/version-1.0.0-teal) ![platform](https://img.shields.io/badge/Pleos%20Connect-Android%2014-blue)

초보 운전자가 **좁은 골목, 양쪽 주차 구간, 좁은 길 대향차** 상황을 지나갈 수 있도록,
남은 간격과 지나갈 길을 계산하고 **음성과 화면으로 안내**하는 Pleos 차량용 앱입니다.
현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

> 기능·판단 규칙·검증 결과의 자세한 설명은 **[Carity.md](Carity.md)**(기술서)를 보세요.

---

## 무엇을 하나요

| 앱 | 하는 일 |
|---|---|
| **Pleos test** (On-sense 시뮬레이션) | 3D 골목에서 좌우 간격 경고·실내 조명 연출. **[라벨 장애물·AI 판단]** 탭에서 실제 블랙박스 라벨로 만든 장면을 불러와 판단·음성 안내 |
| **On-sense 튜토리얼** | 상황 설명 → AI 판단 → 노면 방향 화살표 → 차가 먼저 움직이는 미리보기 → 따라 주행 순서로 연습 |
| **가디언즈 라벨 뷰어** | PC의 블랙박스 영상을 태블릿에서 보고, 라벨이 없으면 YOLO로 라벨링 |

## 작동 방식

```
블랙박스 영상 ──YOLO 라벨링(PC 서버)──► 라벨(차량·보행자 박스)
   ──► 도로 위 장애물로 변환 · 좌우 간격 계산
   ──► 경로 계획 (지나감 / 양보 / 정차)
   ──► 규칙 엔진이 행동 결정  ──►  AI(Qwen 또는 Claude)가 안내 문장 다듬기
   ──► 음성 안내 + 화면 표시 (시뮬레이션 · 튜토리얼)
```

- **결정은 규칙 엔진**이 즉시 내립니다. AI는 문장만 다듬고, 더 위험한 행동을 말하면 무시합니다.
- AI는 `.env`에 Claude 키가 있으면 Claude, 없으면 PC의 **Qwen**(Ollama, `qwen2.5:3b`)을 씁니다.
- 음성은 **Pleos TTS → Android TTS → PC 음성(Windows 한국어)** 순서로 가능한 것을 씁니다.

---

## 실행 방법

### 준비물

| 항목 | 비고 |
|---|---|
| Windows PC | PowerShell 5 이상 |
| Android Studio + Pleos Connect 에뮬레이터(AVD) | Android SDK 기본 경로 사용 |
| Python 3.10 이상 | 라벨링 서버용 (처음 설치 때 사용) |
| (자동 설치) Ollama + qwen2.5:3b | 약 1.9 GB, `Carity.bat setup`이 설치 |

### 1. 처음 한 번 — 설치

`Carity.bat`를 더블클릭하고 **2) 처음 설치**를 고릅니다. (또는 `Carity.bat setup`)

- 라벨링 서버의 Python 환경을 준비합니다. 기존 환경이 있으면 복사하고, 없으면 새로 설치합니다.
- Ollama를 설치하고 `qwen2.5:3b`를 받습니다.
- `.env` 설정 파일을 만듭니다(`.env.example` 복사).

### 2. 매번 — 전체 시작

`Carity.bat` → **1) 전체 시작** (또는 `Carity.bat start`)

1. Ollama(Qwen)를 켭니다.
2. 라벨링 서버를 켭니다. 새 창이 열리며, 닫으면 서버도 꺼집니다.
3. Pleos 에뮬레이터를 확인하고, 꺼져 있으면 실행합니다.
4. 앱이 없으면 빌드해서 설치합니다.
5. 앱 권한을 미리 허용합니다.

### 3. 앱 실행

에뮬레이터 하단의 **앱 목록**에서 실행합니다.

- **Pleos test** → 오른쪽 패널의 **[라벨 장애물·AI 판단]** 탭 → **장면 선택** → **3D 자동 시연**
- **On-sense 튜토리얼** → **장면 선택** → 자동으로 6단계 진행
- **가디언즈 라벨 뷰어** → **연결** → 영상 선택

### 메뉴 전체

| 메뉴 | 명령 | 설명 |
|---|---|---|
| 1 | `Carity.bat start` | 전체 시작 |
| 2 | `Carity.bat setup` | 처음 설치 |
| 3 | `Carity.bat build` | 앱 2개 다시 빌드·설치 (코드나 `.env`의 AI 키를 바꾼 뒤) |
| 4 | `Carity.bat server` | 라벨링 서버만 실행 |
| 5 | `Carity.bat status` | 서버·Ollama·에뮬레이터·앱 상태 점검 |
| 6 | `Carity.bat logs` | 앱 로그를 `logs\`에 저장 |
| 7 | `Carity.bat copydata` | 영상·라벨을 `data\`로 복사 |
| 8 | `Carity.bat stop` | 라벨링 서버 종료 |

실행 기록은 `logs\carity.log`에 남습니다.

---

## 설정 (`.env`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `ANTHROPIC_API_KEY` | (비움) | 넣으면 Claude 사용. 비우면 Qwen. **바꾼 뒤 `build` 필요** |
| `QWEN_MODEL` | `qwen2.5:3b` | Ollama 모델 |
| `DATA_ROOT` | `D:\02. SSAFY\Hyundai Test` | 영상·라벨 폴더. `copydata` 후 `data`로 바꾸면 이 폴더만 사용 |
| `SERVER_PORT` | `8765` | 라벨링 서버 포트 |
| `SERVER_ARGS` | (비움) | 예: `--force-cpu` |
| `EMULATOR_AVD` | (비움) | 비우면 이름에 Pleos가 들어간 AVD 자동 선택 |
| `BUILD_ON_START` | `false` | `true`면 시작할 때마다 앱을 다시 빌드 |

> `.env`는 git에 올리지 마세요. API 키는 빌드된 APK 안에 들어가므로 외부 배포용 빌드에는 넣지 않습니다.

---

## 폴더 구조

```
Carity_ver1/
├─ README.md            이 파일 (소개·실행 방법)
├─ Carity.md            기술서 (기능·구현·판단 규칙·검증)
├─ Carity.bat           실행기 (더블클릭)
├─ carity.ps1           실행기 본체
├─ .env.example         설정 예시
├─ android/             Android Studio 프로젝트 (이 폴더를 열면 됨)
│  ├─ app/              Pleos test(시뮬레이션) + On-sense 튜토리얼
│  └─ viewer/           가디언즈 라벨 뷰어
├─ server/labeling_server/   라벨링 서버 (YOLO, PC 음성)
├─ tools/               보조 스크립트
├─ docs/                참고 문서 (On-sense V1, YOLO 분석 보고서, 처리 시퀀스)
├─ data/                (선택) 영상·라벨
└─ logs/                실행·앱 로그
```

## 개발자용

```powershell
# Android Studio 없이 빌드
cd android
.\gradlew.bat :app:assembleDebug :viewer:assembleDebug

# 단위 테스트
.\gradlew.bat :app:testDebugUnitTest
```

- 에뮬레이터에서 PC 주소는 `10.0.2.2`입니다 (서버 `:8765`, Ollama `:11434`).
- 앱 로그 태그: `CarTalkVoice`(음성), `OnSenseAI`(AI 판단), `OnSenseLabel`(라벨 장면)

## 문제 해결

| 증상 | 확인 |
|---|---|
| Pleos test를 눌러도 화면이 안 뜸 | `Carity.bat start`로 권한을 미리 허용한 뒤 다시 실행 (보이지 않는 권한 창 문제) |
| 라벨 탭에서 "PC 라벨 서버 연결 실패" | `Carity.bat status`로 서버 확인, 꺼져 있으면 `Carity.bat server` |
| AI 판단이 "연결 실패 → 규칙 엔진 문장 사용" | Ollama 실행 여부 확인 (`Carity.bat status`). 규칙 판단은 정상 동작 |
| 소리가 안 남 | 화면의 "음성:" 줄 확인. PC 음성을 쓰려면 라벨링 서버가 켜져 있어야 함 |
| 빌드 실패 | Android Studio로 `android` 폴더를 열어 오류 확인 |

## 버전 기록

| 버전 | 날짜 | 내용 |
|---|---|---|
| 1.0.0 | 2026-11-14 | Carity ver1 통합: 시뮬레이션 + 라벨 장애물·AI 판단 + 음성 + 튜토리얼 + 라벨 뷰어 + 라벨링 서버 + 통합 실행기 |

버전 규칙: `주.부.수` (주: 구조·판단 규칙 변경 / 부: 기능 추가 / 수: 수정·문서). 자세한 이력은 [Carity.md 11장](Carity.md#11-변경-이력)을 보세요.
