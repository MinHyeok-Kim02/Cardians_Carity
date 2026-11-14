# On-sense 관리 안내

Pleos Connect 앱 창 안에서 실행하는 Android 3D 골목 주행 시뮬레이션 MVP입니다.
표시 거리는 가상 벽 기준이며 센서 실측값이 아닙니다. 속도·핸들 각도는 Pleos 에뮬레이터에서 실제 수신합니다.

최신 실행 방법·차량 신호 연동 구조·검증 상태는 [루트 README](../README.md)와 [Pleos 검증 보고서](../PLEOS_ALLEY_VERIFICATION.md)를 참고하세요. 이 문서는 기능별 함수와 기존 수동 시연 방법을 설명합니다.

## 주행용 정보 패널

`DrivingActivity`만 DO 선언을 갖는 런처입니다. 주행 중 속도·핸들 각도를 수치로만 표시하고, 플랫폼 UX 제한이 해제되면 정차용 `MainActivity`로 전환합니다. 3D 골목, 수동 조작, 설정과 권한 요청은 정차 화면에 남깁니다.

| 함수 | 역할 |
| --- | --- |
| buildScreen / addHeader / addReading | 간단한 주행 정보 화면 생성 |
| onRestrictions | 실제 UX 제한을 읽어 주행 패널 유지 / 정차 화면 복귀 결정 |
| startSignalReceivers | 기존 속도·조향 수신 클래스를 재사용. 주행 중 권한 요청 없음 |
| refreshSignals | 수신값을 500ms 주기로 표시. 5초 이상 지연 시 '--' 표시 |
| openParkedScreen | 기존 정차 화면으로 전환. 중복 전환 방지 |
| onPause | 표시 갱신 예약 취소, 속도·조향·주행 제한 구독 해제 |

실제 확인 결과는 [주행 패널 검증 보고서](../PLEOS_DRIVING_PANEL_VERIFICATION.md)에 기록합니다.

## 차량 신호 연동 및 골목 토글 함수

| 클래스 / 함수 | 역할 |
| --- | --- |
| MainActivity.setAlleyVisible | 기본 화면과 3D 골목 전환, 선택 저장, GL과 이동 루프 시작·중지 |
| MainActivity.startVehicleSpeed / requestSpeedPermission | 차속 수신 시작 / 권한 확인·설명·요청 |
| MainActivity.onRestrictions | 실제 플랫폼 UX 제한을 받아 토글·수동 조작·가상 이동 상태에 반영 |
| MainActivity.updateMotionFrame | 시간에 따른 가상 이동 요청, 표시·경고 갱신 연결 |
| MainActivity.canUseManualControls / updateManualControls | 정지·신호 대기 시 수동 버튼 허용, 제한 중 비활성화 |
| PleosSteeringReceiver.start / stop | Pleos 조향각 구독 / 해제, Observer로 UI에 전달 |
| VehicleSpeedReceiver.start / subscribe / onStale / stop | AAOS 차속 구독·변환·수신 지연·해제 |
| VehicleDrivingMonitor.start / publish / stop | CarUxRestrictionsManager 연결·상태 전달·해제. 제한을 변경하지 않음 |
| VehicleMotionController.tick | 토글·앱 생명주기·UX 제한·최신 신호를 확인한 후에만 위치 적분 허용 |
| AlleySimulation.drive | 속도·핸들 각도·시간으로 가상 차량 이동. 조향비 16:1, 축거 2.7m의 시연 모델 |

신호가 유효하지 않거나 5초 이상 지연되면 가상 이동을 정지합니다. 토글 OFF나 화면 이탈 중의 시간을 재개 시 몰아서 적분하지 않습니다. 주행 제한 시 실제 화면을 가리는 플랫폼 동작과 앱의 비활성화 처리를 모두 유지합니다.


## 파일 경로와 역할

경로는 저장소 루트 기준입니다. 링크는 이 문서가 있는 app 폴더 기준으로 연결했습니다.

| 경로 | 역할 |
| --- | --- |
| [DrivingActivity.java](src/main/java/com/example/myapplication/DrivingActivity.java) | 주행 중 읽기 전용 정보 패널, DO 런처와 정차 화면 분기. |
| [app/src/main/java/com/example/myapplication/MainActivity.java](src/main/java/com/example/myapplication/MainActivity.java) | 정차 화면. 화면 구성, 버튼 입력, 근접 이벤트를 모델·화면·오디오에 연결합니다. |
| [PleosSteeringReceiver.java](src/main/java/com/example/myapplication/PleosSteeringReceiver.java) | Pleos 조향각 구독·오류 기록·해제. |
| [VehicleSpeedReceiver.java](src/main/java/com/example/myapplication/VehicleSpeedReceiver.java) | AAOS 현재 차속 수신, 단위 변환, 지연·오류 처리. |
| [VehicleDrivingMonitor.java](src/main/java/com/example/myapplication/VehicleDrivingMonitor.java) | 실제 플랫폼 주행 UX 제한 수신. |
| [VehicleMotionController.java](src/main/java/com/example/myapplication/VehicleMotionController.java) | 신호·토글·제한 상태에 따른 가상 이동 허용과 시간 관리. |
| [VehicleMotionControllerTest.java](src/test/java/com/example/myapplication/VehicleMotionControllerTest.java) | 신호에 따른 이동과 제한·단절·충돌 검증 7개. |
| [app/src/main/java/com/example/myapplication/AlleySimulation.java](src/main/java/com/example/myapplication/AlleySimulation.java) | 차량 위치·방향·크기, 벽 좌표, 이동, 회전 차체 간격, 경고 단계와 근접 진입 이벤트의 유일한 기준입니다. |
| [app/src/main/java/com/example/myapplication/AlleyView.java](src/main/java/com/example/myapplication/AlleyView.java) | GLSurfaceView 설정, 방향별 점멸 시작 시간, GL 스레드로 상태 전달을 담당합니다. |
| [app/src/main/java/com/example/myapplication/AlleyScene.java](src/main/java/com/example/myapplication/AlleyScene.java) | UI에서 GL로 전달하는 상태 스냅샷입니다. 렌더러가 가변 모델을 직접 읽지 않게 합니다. |
| [app/src/main/java/com/example/myapplication/AlleyRenderer.java](src/main/java/com/example/myapplication/AlleyRenderer.java) | 카메라·바닥·벽·차량·기준선의 3D 그리기만 담당합니다. 이동이나 거리 판정을 별도로 계산하지 않습니다. |
| [app/src/main/java/com/example/myapplication/WarningAudio.java](src/main/java/com/example/myapplication/WarningAudio.java) | 경고 큐, 비프 패턴, 방향 음성, 완료·시간 초과·취소·자원 해제를 담당합니다. |
| [app/src/test/java/com/example/myapplication/AlleySimulationTest.java](src/test/java/com/example/myapplication/AlleySimulationTest.java) | 거리 경계값, 회전 차체, 겹침, 좌우 중복 알림 방지·재진입, 시연 흐름 테스트 4개입니다. |
| [app/src/test/java/com/example/myapplication/ExampleUnitTest.kt](src/test/java/com/example/myapplication/ExampleUnitTest.kt) | 기존 기본 단위 테스트 1개입니다. |
| [app/src/androidTest/java/com/example/myapplication/ExampleInstrumentedTest.kt](src/androidTest/java/com/example/myapplication/ExampleInstrumentedTest.kt) | 기기에서 패키지명을 확인하는 기존 계측 테스트입니다. 단위 테스트 명령에 포함되지 않습니다. |
| [app/src/main/AndroidManifest.xml](src/main/AndroidManifest.xml) | DO 런처 DrivingActivity와 비공개 정차용 MainActivity, OpenGL ES 2.0, TTS 조회, CAR_DRIVE·CAR_SPEED 권한 선언입니다. |
| [app/src/main/res/values/strings.xml](src/main/res/values/strings.xml) | 앱 이름입니다. 현재 화면 문구는 MainActivity에서 구성합니다. |
| [app/src/main/res/values/themes.xml](src/main/res/values/themes.xml), [야간 테마](src/main/res/values-night/themes.xml) | 앱 테마입니다. |
| [app/build.gradle.kts](build.gradle.kts) | 패키지·SDK·Java 타깃·앱 의존성입니다. |
| [gradle/libs.versions.toml](../gradle/libs.versions.toml) | AGP·라이브러리 버전입니다. |
| [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) | Gradle 버전입니다. |
| [gradle/gradle-daemon-jvm.properties](../gradle/gradle-daemon-jvm.properties) | Gradle daemon JDK 설정입니다. |

## 데이터 흐름과 책임

```text
버튼 → MainActivity → AlleySimulation
                          ↓ 새 근접 이벤트
                    MainActivity.dispatchProximityEntries
                          ├→ AlleyView.entered → 점멸
                          └→ WarningAudio.enqueue → 비프 후 음성

화면 갱신 → AlleyView.publish → AlleyScene → AlleyRenderer
```

큰 함수는 작업 순서를 연결하고 실제 처리는 기능별 작은 함수에 맡깁니다.
모델은 UI 스레드에서만 갱신하며 GL 스레드는 전달된 스냅샷만 읽습니다.
좌표는 미터 단위이며 모델의 (x, y)를 GL의 (x, 높이, -y)로 옮깁니다.

## 기능별 함수 안내

### MainActivity — 화면과 조작 연결

| 함수 | 역할 |
| --- | --- |
| restoreSimulation | 저장한 차량 상태와 이벤트 카운트 복원. 기존 근접을 재알림하지 않습니다. |
| buildScreen | 화면 생성 순서 연결. |
| createRoot / addHeader | 루트 배경·시스템 여백 / 제목·거리 표시 생성. |
| addSimulationAndControls / createControls | 3D와 우측 패널 배치 / 패널 내용 구성. |
| createTutorialSwitch / addDrivingButtons | 음성 스위치 생성 / 버튼 동작 연결. |
| initializeAudio | 경고 오디오 초기화와 음성 스위치 연결. |
| steer / advance / resetSimulation | 각각 조향 / 전진 / 초기화. |
| onSimulationChanged | 변경 후 이벤트 처리·화면 갱신 순서 연결. |
| dispatchProximityEntries | 새 근접 이벤트만 점멸·오디오로 전달. |
| refreshScreen | 표시·렌더링 상태 갱신 순서 연결. |
| updateDistanceText / updateEventText | 각각 거리 / 주행 상태 문구 갱신. |
| logSimulationState | 현재 상태를 Logcat에 기록. |

### AlleySimulation — 이동과 거리 판정

- drive: 수신 속도·핸들 각도·시간을 이용하는 가상 주행. 가상 벽 충돌 시 중지.

- Scenario: 벽·차체 크기·초기 위치·방향·전진량·조향량.
- forward / steer: 전진 / 조향. 조향 범위는 ±35도입니다.
- clampPositionToWalls: 벽 접촉 시 횡이동 제한.
- corners / rotateCornerIntoWorld: 차체 모서리 생성 / 각 모서리의 월드 좌표 변환.
- gap(side): 회전된 차체 외곽과 해당 벽 사이의 최소 간격. 겹침은 0.
- Level.from: 간격을 여유·주의·근접으로 분류.
- pollEntries: 같은 근접 상태에서는 한 번만 알림. 해제 후 재진입 시 다시 반환.

### AlleyView / AlleyScene — GL 화면과 상태 전달

- configureSurface: OpenGL 환경 설정.
- entered / resetBlink: 방향별 점멸 시작 / 초기화.
- publish: 현재 모델을 AlleyScene에 담아 렌더러로 전달.
- color: 경고 단계별 기준선 색.
- AlleyScene 생성자: 위치·방향·크기·벽 좌표·경고 단계·점멸 시간을 복사.

### AlleyRenderer — 요소별 3D 그리기

- createShaderProgram / locateShaderInputs: 셰이더 생성 / 입력 위치 조회.
- updateCamera / updateCarTransform: 카메라 / 차체 변환.
- drawGround / drawWalls / drawRoadJoints: 바닥 / 벽 / 이동을 보여 주는 이음선.
- drawNeighborhood: 건물·창문·가로수·가로등 등 도심 배경.
- drawCarBody / drawLamps / drawWheels / drawDirectionMarker: 차체 / 램프 / 바퀴 / 방향 표시.
- drawGuidelines: 상태별 기준선과 점멸. 다음 프레임 필요 여부를 반환.
- box / cube: 공통 박스 그리기 / 기본 메시 생성.
- onDrawFrame: 위 기능을 그리기 순서대로 호출.

### WarningAudio — 비프와 방향 음성

- initializeTone / initializeSpeech: 비프 / Android TTS 준비.
- onSpeechInitialized / createSpeechListener: 준비 결과 / 재생 완료 리스너.
- enqueue / next: 큐 등록 / 다음 경고 선택.
- startWarning: 비프 후 음성이 나오도록 순서 예약.
- playBeepPattern: 0/180/360ms에 비프.
- speakTutorial: 620ms 이후 방향 음성 요청.
- directionMessage: 왼쪽 / 오른쪽 음성 문구 선택.
- completeUtterance / stopTimedOutSpeech: 재생 완료 / 8초 시간 초과 처리.
- cancel / pause / release: 예약·재생 취소 / 백그라운드 처리 / 자원 해제.

## 수정할 기능별 위치

| 수정 목적 | 먼저 볼 함수·설정 |
| --- | --- |
| 골목 폭·차체 크기·초기값 | AlleySimulation.Scenario |
| 60cm/30cm 기준 | AlleySimulation.Level.from |
| 전진·조향 | AlleySimulation.forward / steer |
| UI 배치·글자 크기 | MainActivity의 화면 생성 함수 |
| 카메라 시점 | AlleyRenderer.updateCamera |
| 벽 높이·차량 외형 | AlleyRenderer의 해당 draw 함수 |
| 기준선 색 | AlleyView.color |
| 3회 점멸 | AlleyRenderer.drawGuidelines: 250ms 밝음/어두움, 총 1500ms |
| 비프·음성 문구 | WarningAudio.playBeepPattern / directionMessage |

## 빌드와 검증

PowerShell, **저장소 루트**에서 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

최신 코드의 assembleDebug·testDebugUnitTest 성공 및 Pleos Connect v2.1.0 설치·실행 성공.
P·0km/h 토글 ON/OFF와 화면 복귀는 검증했습니다. 5·10·약 15km/h 신호 수신과 각 속도 고정 화면 검증은 구분하며, 상세 결과는 [검증 보고서](../PLEOS_ALLEY_VERIFICATION.md)에 기록합니다. 소리 청취는 별도 확인이 필요합니다.

기존 환경: AGP 9.4.1 / Gradle 9.6.0 / daemon JDK 25 / Java 타깃 11.
compileSdk·targetSdk 37 / minSdk 24 / 패키지 com.example.myapplication.

## 시연 순서

Pleos AVD를 **P / 0km/h**로 설정하고 Android Studio에서 app을 실행합니다.
‘골목 보기’를 켜고 차량 속도 0인 상태에서 수동 테스트 버튼을 사용합니다.
한국어 TTS 엔진·데이터와 미디어 볼륨을 확인합니다.

1. 초기화: 좌우 약 110cm.
2. 우향 2회: +10도, 우측 약 77cm.
3. 전진 5회: 우측 약 55cm, 주황.
4. 전진 7회 추가: 우측 약 25cm, 빨강·점멸·비프·방향 음성 요청.
5. 근접 유지 중 전진 1회: 진입 카운트 유지, 재알림 없음.
6. 좌향 4회 → 전진 8회: 근접 해제.
7. 우향 4회 → 전진 8회: 재진입 알림.
8. 초기화 후 좌향 2회로 같은 접근 절차를 반복해 왼쪽 표시 확인.

Logcat의 OnSense 태그: STATE, NEAR_ENTER, GL_READY, GL_LINE, AUDIO_START, TTS_REQUEST.

## 현재 제약

- 음성은 시연용 Android TTS 한국어입니다. Pleos TTS SDK는 미통합입니다.
- 이전 Pleos 실행에서 TTS 엔진 미연결 로그가 있었으므로 한국어 엔진 연결 확인이 필요합니다.
- 3D 골목은 정차 전용이며 주행 제한 시 읽기 전용 DO 패널로 전환합니다. 주행 패널의 실제 표시 결과는 별도 검증 보고서를 참고합니다.
- 단위 테스트는 거리·경고 진입 로직을 검증합니다. GPU 화면·점멸 실제 표시·소리 청취는 기기 확인이 필요합니다.
- 자동 기준점 매핑, 실차 주변 거리 실측·실차 모델 보정, 앰비언트·미러·창문·비상등 제어,
  Gleo/외부 LLM 판단, 좌석별 오디오는 이번 MVP 범위에 포함하지 않습니다.
