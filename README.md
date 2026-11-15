# Carity — 좁은 길 통과 보조

![version](https://img.shields.io/badge/version-2.1.0-blue) ![platform](https://img.shields.io/badge/Pleos%20Connect-Android%2014-blue)

초보 운전자가 **좁은 골목, 양쪽 주차 구간, 좁은 길 대향차** 상황을 지나갈 수 있도록
남은 간격과 지나갈 길을 계산하고 **음성과 화면으로 안내**하는 Pleos 차량용 앱입니다.
현대자동차그룹 차량용 앱 개발 공모전 출품작입니다. (팀 Cardiance)

| 항목     | 내용                                                                                                             |
| ------ | -------------------------------------------------------------------------------------------------------------- |
| 앱 이름   | **Carity** (앱 목록 표시: Carity 시뮬레이션 · Carity 튜토리얼)                                                               |
| 한 줄 소개 | 좁은 골목·양쪽 주차 구간에서 차와 장애물 사이 간격을 계산해, 지나갈지·양보할지를 음성과 화면으로 알려 주는 좁은 길 통과 보조 앱                                     |
| 실행 방법  | 제출용 APK만 실행: [받은 APK로 바로 실행하기](#받은-apk로-바로-실행하기) / 전체 개발 환경: [처음 설치하는 PC에서 실행하기](#처음-설치하는-pc에서-실행하기)           |
| 테스트 환경 | [테스트 환경](#테스트-환경) 참고 (Windows PC + Android Studio + Pleos Connect 에뮬레이터)                                       |
| 에뮬레이터  | **Pleos Connect AVD `Pleos_Connect_v2.1.0`** (Android 14 기반 AAOS) · 화면 **2560 × 1440** 가로                      |
| APK    | `Cardiance_Carity_release.apk`(release/Cardiance_Carity_release.apk) (패키지 `com.carity.app`, 버전 2.0.0, 26.7 MB) |

> 기능, 판단 규칙, 검증 결과는 **[Carity.md](Carity.md)**(기술서)에 있습니다.

---

## 무엇을 하나요

에뮬레이터 앱 목록에 아이콘 3개가 생깁니다.

| 앱                | 하는 일                                                                                                                    |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **Carity 시뮬레이션** | 3D 골목에서 차체와 장애물 사이 간격을 계산해 경고하고, 실내 조명으로 위험 방향을 보여 줍니다. **[라벨 장애물·AI 판단]** 탭에서는 실제 블랙박스 라벨로 만든 장면을 불러와 판단하고 음성으로 안내합니다. |
| **Carity 튜토리얼**  | 상황 설명 → AI 판단 → 노면 화살표 → 차가 먼저 지나가는 미리 보기 → 따라 주행 순서로 연습합니다.                                                            |
| **Carity 라벨 뷰어** | PC에 있는 블랙박스 영상을 태블릿에서 보고, 라벨이 없으면 PC에서 YOLO로 라벨링합니다.                                                                    |

## 작동 방식

```
블랙박스 영상 ──YOLO 라벨링(PC 서버)──► 차량·보행자 위치
   ──► 도로 위 장애물로 변환 · 좌우 간격 계산
   ──► 경로 계획 (지나감 / 양보 / 정차)
   ──► 규칙 엔진이 행동 결정 ──► AI(Qwen 또는 Claude)가 안내 문장 다듬기
   ──► 음성 안내 + 화면 표시
```

- **결정은 규칙 엔진**이 바로 내립니다. AI는 문장만 다듬고, 더 위험한 행동을 말하면 무시합니다.
- AI는 `.env`에 Claude 키가 있으면 Claude, 없으면 PC의 **Qwen**(Ollama, `qwen2.5:3b`)을 씁니다.
- 음성은 **Pleos TTS → Android TTS → PC 음성(Windows 한국어)** 순서로 되는 것을 씁니다.

---

## 처음 설치하는 PC에서 실행하기

처음 한 번은 1~6단계를 모두 하고, 그다음부터는 [매번 실행하기](#매번-실행하기)만 하면 됩니다.

### 1. 준비물 설치

| 항목                  | 설치 방법 / 확인                                                                                                     |
| ------------------- | -------------------------------------------------------------------------------------------------------------- |
| Windows 10 또는 11    | PowerShell 5 이상 (기본 포함)                                                                                        |
| Android Studio      | 설치 후 한 번 실행해 Android SDK를 기본 위치(`%LOCALAPPDATA%\Android\Sdk`)에 받습니다. JDK는 Android Studio에 들어 있는 것을 씁니다.        |
| Pleos Connect 에뮬레이터 | Pleos 개발자 안내에 따라 Android Studio **Device Manager**에 Pleos Connect AVD를 추가합니다. AVD 이름에 `Pleos`가 들어가면 자동으로 찾습니다. |
| Python 3.10 이상      | [python.org](https://www.python.org/)에서 설치할 때 **Add python.exe to PATH**를 체크합니다. 라벨링 서버용입니다.                   |
| 인터넷 연결              | 처음 설치 때 Gradle 라이브러리, PyTorch(약 5 GB), Ollama와 Qwen 모델(약 1.9 GB)을 받습니다.                                        |
| 디스크 여유              | 약 15 GB                                                                                                        |

NVIDIA 그래픽카드가 있으면 라벨링 서버가 GPU(YOLO11s)를 쓰고, 없으면 CPU(YOLOv8n)를 씁니다. 둘 다 동작합니다.

### 2. 소스 받기

팀 GitHub 저장소를 받거나(`git clone`), 공유받은 `Carity_ver2` 폴더를 원하는 위치에 복사합니다.
아래 설명에서는 이 폴더를 **Carity 폴더**라고 부릅니다.

> 경로에 한글이나 공백이 있어도 됩니다. 다만 폴더를 다른 프로그램이 열고 있는 동안에는 이름을 바꾸지 마세요(문제 해결 표 참고).

### 3. 블랙박스 영상 폴더 정하기

라벨링 서버가 보여 줄 영상 폴더가 필요합니다. 둘 중 하나를 고릅니다.

- **기존 폴더를 그대로 쓰기**: 4단계에서 `.env`의 `DATA_ROOT`를 그 폴더로 바꿉니다.
- **Carity 폴더 안에 넣기**: 영상과 라벨을 `Carity 폴더\data`에 복사하고 `DATA_ROOT=data`로 둡니다.

앱 안에 검증용 라벨 장면 6개가 들어 있어서, 영상 폴더가 없어도 시뮬레이션과 튜토리얼은 동작합니다.

### 4. 설정 파일 `.env` 확인

1. Carity 폴더의 `Carity.bat`를 한 번 더블클릭했다가 `0`(나가기)을 누르면 `.env`가 자동으로 생깁니다. 직접 `.env.example`을 복사해 `.env`로 이름을 바꿔도 됩니다.
2. `.env`를 메모장으로 열고 아래 두 줄을 이 PC에 맞게 고칩니다.

```ini
DATA_ROOT=D:\내\영상\폴더          # 3단계에서 정한 폴더
LEGACY_SERVER=                     # 이미 설치된 라벨링 서버 폴더가 없으면 비워 둡니다
```

나머지 설정은 [설정 표](#설정-env)를 보세요. Claude를 쓰려면 `ANTHROPIC_API_KEY`에 키를 넣습니다. 비워 두면 Qwen을 씁니다.

### 5. 처음 설치 — `Carity.bat` → **2**

`Carity.bat`를 더블클릭하고 **2) 처음 설치**를 고릅니다.

1. 라벨링 서버의 Python 환경을 만듭니다. `LEGACY_SERVER`에 기존 환경이 있으면 그대로 쓰고, 없으면 PyTorch(CUDA 12.8)와 필요한 패키지를 설치합니다. 10분 이상 걸릴 수 있습니다.
2. Ollama를 설치하고 `qwen2.5:3b` 모델을 받습니다.
3. 마지막에 상태 점검 결과가 나옵니다. `!!` 표시가 있으면 [문제 해결](#문제-해결)을 보세요.

### 6. 앱 빌드와 첫 실행 — `Carity.bat` → **1**

**1) 전체 시작**을 고릅니다. 처음에는 앱이 설치되어 있지 않아 자동으로 빌드합니다(5~10분).

1. Ollama를 켜고 Qwen 모델을 메모리에 미리 올립니다.
2. 라벨링 서버를 켭니다. **"Carity labeling_server"라는 새 창**이 열립니다. 이 창을 닫으면 서버도 꺼집니다.
3. Pleos 에뮬레이터를 켭니다(이미 켜져 있으면 그대로 씁니다).
4. 두 앱을 빌드해서 에뮬레이터에 설치합니다.
5. 앱 권한을 미리 허용하고, 화면을 가리는 창이 없는지 확인합니다.

`OK`가 이어지고 마지막에 `상태`가 나오면 준비가 끝난 것입니다.

---

## 매번 실행하기

1. `Carity.bat` → **1) 전체 시작**
2. 에뮬레이터 하단 **앱 목록**에서 앱을 엽니다.

| 앱            | 사용 순서                                                                                      |
| ------------ | ------------------------------------------------------------------------------------------ |
| Carity 시뮬레이션 | 오른쪽 패널 **[라벨 장애물·AI 판단]** 탭 → **장면 선택** → **3D 자동 시연**. 판단 결과와 AI 문장이 패널에 나오고 음성으로 읽어 줍니다. |
| Carity 튜토리얼  | **장면 선택** → 6단계가 자동으로 진행됩니다. 일시정지, 다음 단계, 음성 켬/끔 버튼이 있습니다.                                 |
| Carity 라벨 뷰어 | 서버 주소 `http://10.0.2.2:8765` 확인 → **연결** → 폴더와 영상 선택. 라벨이 없는 영상은 PC에서 라벨링한 뒤 재생합니다.        |

3. 끝낼 때는 서버 창과 에뮬레이터를 닫습니다. Carity 폴더 이름을 바꾸거나 옮길 계획이면 먼저 `Carity.bat` → **8) 전체 종료**를 합니다.

---

## 메뉴 전체

| 메뉴  | 명령                      | 설명                                                                   |
| --- | ----------------------- | -------------------------------------------------------------------- |
| 1   | `Carity.bat start`      | 전체 시작                                                                |
| 2   | `Carity.bat setup`      | 처음 설치                                                                |
| 3   | `Carity.bat build`      | 앱 2개 다시 빌드·설치 (코드나 `.env`의 AI 키를 바꾼 뒤)                               |
| 4   | `Carity.bat server`     | 라벨링 서버만 실행                                                           |
| 5   | `Carity.bat status`     | 서버, Ollama, 에뮬레이터, 앱 설치, 현재 화면 점검                                    |
| 6   | `Carity.bat logs`       | 앱 로그를 `logs\`에 저장 (앱 실행·강제 종료 기록 포함)                                 |
| 7   | `Carity.bat copydata`   | 영상·라벨을 `data\`로 복사                                                   |
| 8   | `Carity.bat stopall`    | 서버·빌드 데몬·adb 등 Carity 폴더를 잡고 있는 프로그램 종료                              |
| 9   | `Carity.bat diag`       | 에뮬레이터 → PC 네트워크 진단                                                   |
| 10  | `Carity.bat reset`      | 에뮬레이터를 처음 상태로 다시 부팅하고 앱을 최신 빌드로 재설치                                  |
| 11  | `Carity.bat releasekey` | 제출용 서명 키 만들기 (처음 1회)                                                 |
| 12  | `Carity.bat release`    | Pleos App Market 제출용 APK 빌드 → `release\Cardiance_Carity_release.apk` |
| 13  | `Carity.bat shot`       | 스토어 스크린샷 (에뮬레이터 화면 PNG → `release\store\`)                           |
| —   | `Carity.bat stop`       | 라벨링 서버만 종료                                                           |

실행 기록은 `logs\carity.log`에 남습니다.

---

## 제출용 APK 만들기 (Pleos App Market)

1. `Carity.bat` → **11** 서명 키 만들기. 비밀번호를 입력합니다. 만들어진 `release\carity-release.jks`와 비밀번호는 꼭 따로 백업하세요.
2. `Carity.bat` → **12** 제출용 APK 빌드. `release\Cardiance_Carity_release.apk`(버전·SHA-256은 같은 이름의 `.txt`)가 만들어지고, 원하면 에뮬레이터에 바로 설치합니다.
3. `Carity.bat` → **13**으로 스토어 스크린샷을 찍습니다.
4. `release\제출_체크리스트.md`를 보고 콘솔에 등록합니다(설명 초안, 아이콘, 개인정보처리방침 초안 포함).

제출용 APK는 PC 서버 없이 혼자 동작합니다. AI 문장 다듬기, PC 라벨 불러오기, 위치 진단, 개발용 버튼은 빠지고 규칙 엔진 판단, 앱 내장 라벨 장면, Pleos 음성 안내만 들어갑니다. 패키지 이름은 `com.carity.app`이라 개발용 앱과 따로 설치됩니다.

---

## 받은 APK로 바로 실행하기

PC 서버 없이 앱만 확인할 때의 방법입니다. 제출용 APK는 규칙 엔진 판단, 앱 내장 라벨 장면 6개, Pleos 음성 안내로 혼자 동작합니다.

1. Android Studio **Device Manager**에서 Pleos Connect 에뮬레이터(`Pleos_Connect_v2.1.0`)를 켭니다. 화면이 멈추면 **Cold Boot Now**로 다시 켭니다.
2. APK를 설치합니다. 에뮬레이터 창에 APK 파일을 끌어다 놓거나, 명령으로 설치합니다.
   
   ```powershell
   adb install -r release\Cardiance_Carity_release.apk
   ```
3. (권장) 차속·음성 권한을 미리 허용합니다. 허용하지 않으면 실행 중 권한 창이 뜰 수 있습니다.
   
   ```powershell
   $u = (adb shell am get-current-user).Trim()
   adb shell pm grant --user $u com.carity.app android.car.permission.CAR_SPEED
   adb shell pm grant --user $u com.carity.app pleos.car.permission.TTS_SERVICE
   ```
4. 앱 목록에서 **Carity 시뮬레이션**을 엽니다. 오른쪽 패널 **[라벨 장애물·AI 판단]** → **장면 선택** → **3D 자동 시연**. **Carity 튜토리얼**은 장면을 고르면 6단계가 자동으로 진행됩니다.

## 테스트 환경

| 구분            | 내용                                                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------------------------------- |
| 개발·테스트 PC     | Windows 10/11, NVIDIA RTX 5060 (라벨링 서버 GPU용, 없어도 CPU로 동작)                                                            |
| 개발 도구         | Android Studio (내장 JDK), Android SDK Build-Tools 36.0.0                                                              |
| 빌드 설정         | Android Gradle Plugin 9.5.0, Gradle 9.6, `compileSdk`·`targetSdk` 37, `minSdk` 28, Kotlin                            |
| 에뮬레이터         | **Pleos Connect AVD `Pleos_Connect_v2.1.0`**, Android 14 기반 AAOS                                                     |
| 화면 규격         | 전체 **2560 × 1440** (가로). 스토어 스크린샷 규격은 Pleos 가이드 기준 2560 × 1380 / 1700 × 1212 / 840 × 121                             |
| 부팅 방식         | 콜드 부트 권장 (`EMULATOR_COLD_BOOT=true`, 스냅샷 부팅 시 일부 PC에서 터치 무반응)                                                        |
| Pleos SDK     | Vehicle SDK 2.3.2, TextToSpeech SDK 3.3.1 (`ON_DEVICE`), FusedLocation 1.1.3 (개발용만)                                  |
| 필요 권한         | `INTERNET`, `android.car.permission.CAR_SPEED`, `pleos.car.permission.CAR_DRIVE`, `pleos.car.permission.TTS_SERVICE` |
| 개발용 추가 구성     | Python 3.11 라벨링 서버(YOLO11s/YOLOv8n), Ollama `qwen2.5:3b`, 선택: Claude API 키                                           |
| 에뮬레이터 → PC 주소 | `10.0.2.2` (라벨링 서버 `:8765`, Ollama `:11434`)                                                                         |

제출용 APK는 위 표에서 **에뮬레이터와 필요 권한만** 있으면 됩니다. 나머지는 개발용 빌드(AI 문장 다듬기, PC 라벨 불러오기, 라벨 뷰어)에 필요합니다.

---

## 설정 (`.env`)

| 키                      | 기본값                                         | 설명                                                                |
| ---------------------- | ------------------------------------------- | ----------------------------------------------------------------- |
| `ANTHROPIC_API_KEY`    | (비움)                                        | 넣으면 Claude 사용, 비우면 Qwen. **바꾼 뒤 메뉴 3(build) 필요**                  |
| `CLAUDE_MODEL`         | `claude-haiku-5-5`                          | Claude 모델                                                         |
| `QWEN_MODEL`           | `qwen2.5:3b`                                | Ollama 모델                                                         |
| `QWEN_BASE_URL`        | `http://10.0.2.2:11434`                     | 앱에서 본 Ollama 주소                                                   |
| `DATA_ROOT`            | `D:\02. SSAFY\Hyundai Test`                 | 영상·라벨 폴더. 상대경로(`data`)는 Carity 폴더 기준                              |
| `SERVER_PORT`          | `8765`                                      | 라벨링 서버 포트                                                         |
| `SERVER_ARGS`          | (비움)                                        | 예: `--force-cpu`                                                  |
| `LABEL_SERVER_URL`     | `http://10.0.2.2:8765`                      | 앱에서 본 라벨링 서버 주소                                                   |
| `LEGACY_SERVER`        | `D:\02. SSAFY\Hyundai Test\labeling_server` | 이미 설치된 서버 Python 환경 위치. 있으면 그대로 사용                                |
| `EMULATOR_AVD`         | (비움)                                        | 비우면 이름에 Pleos가 들어간 AVD 자동 선택                                      |
| `EMULATOR_COLD_BOOT`   | `true`                                      | 저장된 상태 없이 처음부터 부팅(1~3분). 스냅샷 부팅은 일부 PC에서 터치가 먹지 않아 기본값을 `true`로 둠 |
| `BUILD_ON_START`       | `false`                                     | `true`면 시작할 때마다 앱을 다시 빌드                                          |
| `ANDROID_SDK`          | (비움)                                        | 비우면 `%LOCALAPPDATA%\Android\Sdk`                                  |
| `RELEASE_STORE_FILE` 등 | (메뉴 11이 채움)                                 | 제출용 서명 키 위치·비밀번호                                                  |
| `RELEASE_APP_ID`       | `com.carity.app`                            | 제출용 패키지 이름. 제출 후에는 바꿀 수 없음                                        |

> `.env`는 git에 올리지 마세요. API 키는 빌드된 APK 안에 들어가므로 외부 배포용 빌드에는 넣지 않습니다.

---

## 문제 해결

| 증상                                  | 해결                                                                                    |
| ----------------------------------- | ------------------------------------------------------------------------------------- |
| 앱 버튼을 눌러도 반응이 없음                    | `.env`의 `EMULATOR_COLD_BOOT=true`인지 확인하고 에뮬레이터를 닫은 뒤 메뉴 1. 그래도 안 되면 **10) 에뮬레이터 초기화** |
| 제출용 앱이 실행 직후 꺼짐                     | 2.1.0에서 해결(R8 최적화 끔). 다시 생기면 앱을 한 번 실행한 직후 메뉴 6으로 로그를 저장해 `FATAL` 줄을 확인합니다            |
| Carity 시뮬레이션을 눌러도 화면이 안 뜸           | 메뉴 1로 다시 시작합니다(권한을 미리 허용해 보이지 않는 권한 창을 막습니다).                                         |
| 폴더 이름 변경·삭제가 "다른 프로그램에서 사용 중"으로 안 됨 | 메뉴 **8**, Android Studio에서 File > Close Project, 서버 창과 `Carity.bat` 창 닫기 순서로 진행합니다.   |
| 라벨 탭에서 "PC 라벨 서버 연결 실패"             | 메뉴 5로 서버 확인. 꺼져 있으면 메뉴 4. 그래도 안 되면 메뉴 9                                               |
| 라벨 뷰어 연결 실패                         | 서버 주소가 `http://10.0.2.2:8765`인지 확인, 메뉴 9                                              |
| "영상 라벨링 데이터가 없음"                    | `.env`의 `DATA_ROOT`가 실제 영상 폴더인지 확인. 메뉴 5에 영상·라벨 개수가 나옵니다.                             |
| AI 판단이 "연결 실패 → 규칙 엔진 문장 사용"        | Ollama가 꺼져 있거나 첫 응답이 느린 경우입니다. 메뉴 5로 확인합니다. 규칙 판단은 정상 동작합니다.                          |
| 소리가 안 남                             | 화면의 "음성:" 줄 확인. PC 음성을 쓰려면 라벨링 서버가 켜져 있어야 합니다. PC 볼륨도 확인합니다.                          |
| "서버 Python 환경 없음"                   | 메뉴 2(처음 설치)                                                                           |
| 빌드 실패                               | Android Studio로 `android` 폴더를 열어 Gradle Sync 오류를 확인합니다.                               |
| 에뮬레이터가 켜졌다가 바로 꺼짐                   | 콜드 부트(`EMULATOR_COLD_BOOT=true`)로 다시 시작, 또는 메뉴 10. 계속되면 메뉴 6으로 로그를 저장해 팀에 공유합니다.      |

---

## 폴더 구조

```
Carity 폴더/
├─ README.md            이 파일 (소개·설치·실행)
├─ Carity.md            기술서 (기능·구현·판단 규칙·검증)
├─ Carity.bat           실행기 (더블클릭)
├─ carity.ps1           실행기 본체
├─ .env.example         설정 예시 (.env 로 복사해 사용)
├─ android/             Android Studio 프로젝트 (이 폴더를 열면 됨)
│  ├─ app/              Carity 시뮬레이션 + Carity 튜토리얼
│  └─ viewer/           Carity 라벨 뷰어
├─ server/labeling_server/   라벨링 서버 (YOLO, PC 음성)
├─ tools/               보조 스크립트
├─ docs/                아이콘, 통합 전 원본 문서(docs/원본)
├─ release/             제출용 APK(Cardiance_Carity_release.apk), 제출 체크리스트, 스토어 자료
├─ demo/                시연 영상용 블랙박스 클립
├─ data/                (선택) 영상·라벨
└─ logs/                실행·앱 로그
```

## 개발자용

```powershell
cd android
.\gradlew.bat :app:assembleDebug :viewer:assembleDebug   # Android Studio 없이 빌드
.\gradlew.bat :app:testDebugUnitTest                      # 단위 테스트
```

- 에뮬레이터에서 PC 주소는 `10.0.2.2`입니다 (서버 `:8765`, Ollama `:11434`).
- 앱 로그 태그: `CarityVoice`(음성), `CarityAI`(AI 판단), `CarityLabel`(라벨 장면), `CarityVehicle`(차량 데이터). 전체 목록은 Carity.md 15-4절.
- 패키지 이름: 개발용 `com.example.pleostest`(시뮬레이션·튜토리얼), `com.guardians.viewer`(라벨 뷰어) / 제출용 `com.carity.app`.
- 제출용과 개발용을 함께 설치하면 앱 목록에 Carity 시뮬레이션·튜토리얼 아이콘이 두 개씩 보입니다. 개발용 버튼(Console 키보드 등)이 없는 쪽이 제출용입니다.

## 버전 기록

| 버전    | 날짜         | 내용                                                                                                                  |
| ----- | ---------- | ------------------------------------------------------------------------------------------------------------------- |
| 2.1.0 | 2026-11-15 | 제출용 APK(`release\Cardiance_Carity_release.apk`) 빌드·에뮬레이터 실행 검증, 실행 직후 종료 문제 해결(R8 끔), 메뉴 11~13, 콜드 부트 기본값, 로그 수집 확대 |
| 2.0.0 | 2026-11-15 | 이름을 Carity로 통일, Carity 아이콘, 기술서 통합, 실행기 메뉴 8·9·10, 서버 환경 재사용, Qwen 응답 대기 개선                                         |
| 1.0.0 | 2026-11-14 | Carity ver1 통합: 시뮬레이션 + 라벨 장애물·AI 판단 + 음성 + 튜토리얼 + 라벨 뷰어 + 라벨링 서버 + 통합 실행기                                          |

버전 규칙: `주.부.수` (주: 구조·판단 규칙 변경 / 부: 기능 추가 / 수: 수정·문서). 자세한 이력은 [Carity.md 14장](Carity.md#14-변경-이력)을 보세요.
