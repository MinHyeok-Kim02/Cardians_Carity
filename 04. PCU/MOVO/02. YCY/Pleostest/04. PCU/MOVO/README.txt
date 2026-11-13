[이전 수동 MVP 관리 기록]
최신 차량 신호 연동·골목 토글·검증 상태: 프로젝트 루트 README.md
기능별 함수 안내: app/README.md
실제 Pleos 검증 로그와 미완료 항목: PLEOS_ALLEY_VERIFICATION.md
아래 내용은 차량 신호 연동 이전의 수동 MVP 기록입니다.

On-sense — 프로젝트 관리 안내
============================

Pleos Connect 앱 창에서 실행하는 Android 3D 골목 주행 시뮬레이션 MVP.
거리는 가상 값이며 센서 실측/실차 연동 값이 아닙니다.

[파일 경로 / 역할]
모든 경로는 저장소 루트 기준입니다.
주요 소스 폴더: app/src/main/java/com/example/myapplication/

MainActivity.java
  앱 진입점. 화면 구성, 조작 입력, 근접 이벤트 전달, 상태 복원을 연결합니다.
  buildScreen/addHeader/createControls: 화면 영역별 생성
  steer/advance/resetSimulation: 조향/전진/초기화
  dispatchProximityEntries: 새 근접 이벤트만 점멸·오디오로 전달
  updateDistanceText/updateEventText: 거리/주행 상태 표시
  initializeAudio: 경고 오디오 연결

AlleySimulation.java
  차량 위치·방향·크기·벽 좌표·거리 판정의 유일한 기준입니다.
  Scenario: 초기값과 크기, 전진량, 조향량
  forward/steer: 전진/조향
  clampPositionToWalls: 벽 접촉 시 횡이동 제한
  corners/rotateCornerIntoWorld: 차체 모서리/월드 좌표 변환
  gap: 회전 차체 외곽과 벽 사이 최소 간격, 겹침은 0
  Level.from: 초록/주황/빨강 판정
  pollEntries: 근접 진입 1회 알림, 해제 후 재진입 시 재알림

AlleyView.java
  GLSurfaceView 설정, 점멸 시작 시간, 모델 스냅샷 전달.
  configureSurface: GL 환경
  entered/resetBlink: 점멸 시작/초기화
  publish: AlleyScene을 렌더러에 전달
  color: 상태별 기준선 색

AlleyScene.java
  UI 스레드에서 만든 상태 스냅샷.
  위치·방향·차체 크기·벽 좌표·경고 단계·점멸 시간을 전달합니다.
  GL 스레드는 가변 모델을 직접 읽지 않습니다.

AlleyRenderer.java
  전달된 스냅샷을 이용한 3D 그리기만 담당합니다.
  createShaderProgram/locateShaderInputs: 셰이더 생성/입력 조회
  updateCamera/updateCarTransform: 카메라/차체 변환
  drawGround/drawWalls/drawRoadJoints: 바닥/벽/이음선
  drawCarBody/drawLamps/drawWheels/drawDirectionMarker: 차체/램프/바퀴/방향 표시
  drawGuidelines: 상태별 기준선과 3회 점멸
  box/cube: 공통 박스 그리기/기본 메시
  렌더러에서 주행이나 거리 판정을 별도로 계산하지 않습니다.

WarningAudio.java
  경고 큐와 비프 뒤 방향 음성을 직렬 재생합니다.
  initializeTone/initializeSpeech: 비프/Android TTS 준비
  enqueue/next: 큐 등록/다음 경고 선택
  startWarning: 비프 후 음성 순서 예약
  playBeepPattern: 0/180/360ms 비프
  speakTutorial/directionMessage: 620ms 이후 방향 음성/문구 선택
  completeUtterance/stopTimedOutSpeech: 완료/8초 제한
  cancel/pause/release: 취소/백그라운드 처리/자원 정리

[설정 및 테스트 경로]
app/src/main/AndroidManifest.xml: 런처, OpenGL ES 2.0, TTS 서비스 조회
app/src/main/res/values/strings.xml: 앱 이름
app/src/main/res/values/themes.xml: 기본 테마
app/src/main/res/values-night/themes.xml: 야간 테마
app/build.gradle.kts: 앱 패키지, SDK, Java 타깃, 의존성
gradle/libs.versions.toml: AGP·라이브러리 버전
gradle/wrapper/gradle-wrapper.properties: Gradle 버전
gradle/gradle-daemon-jvm.properties: Gradle daemon JDK
app/src/test/java/com/example/myapplication/AlleySimulationTest.java: 시뮬레이션 테스트 4개
app/src/test/java/com/example/myapplication/ExampleUnitTest.kt: 기존 단위 테스트 1개
app/src/androidTest/java/com/example/myapplication/ExampleInstrumentedTest.kt: 기존 기기 패키지 검사

[데이터 흐름]
버튼 → MainActivity → AlleySimulation → 근접 진입 이벤트
  → AlleyView.entered / WarningAudio.enqueue
화면 갱신 → AlleyView.publish → AlleyScene → AlleyRenderer
거리 계산은 모델, 이벤트 연결은 Activity, 화면 그리기는 렌더러에서 관리합니다.

[어디를 수정할까?]
차량·벽·초기값: AlleySimulation.Scenario
60cm/30cm 기준: AlleySimulation.Level.from
전진·조향: AlleySimulation.forward/steer
UI 배치·글자 크기: MainActivity의 화면 생성 함수
카메라·벽 높이·차량 외형: AlleyRenderer의 해당 함수
기준선 색: AlleyView.color
점멸: AlleyRenderer.drawGuidelines (250ms 밝음/어두움, 총 1500ms)
비프·음성 문구: WarningAudio.playBeepPattern/directionMessage

[빌드]
PowerShell, 저장소 루트:
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain

리팩터링 후 결과: BUILD SUCCESSFUL in 8s. 기존 단위 테스트 통과.
이번 리팩터링 이후 기기 화면·소리는 다시 검증하지 않았습니다.
환경: AGP 9.4.1 / Gradle 9.6.0 / daemon JDK 25 / Java 타깃 11
compileSdk·targetSdk 37 / minSdk 24 / com.example.myapplication

[시연]
Pleos AVD P / 0km/h → app 실행 → 한국어 TTS·미디어 볼륨 확인.
초기화(좌우110cm) → 우향2회(+10도, 우측77cm)
→ 전진5회(우측55cm/주황) → 전진7회 추가(25cm/빨강)
→ 점멸 종료 후 빨강 유지 → 근접 중 전진1회(재알림 없음)
→ 좌향4회·전진8회(해제) → 우향4회·전진8회(재알림).
왼쪽은 초기화 후 좌향2회로 같은 접근 절차를 반복합니다.
Logcat 태그 OnSense: STATE, NEAR_ENTER, GL_READY, GL_LINE, AUDIO_START, TTS_REQUEST.

[제약]
음성은 시연용 Android TTS 한국어입니다. Pleos TTS SDK는 미통합입니다.
이전 AVD에서 TTS 엔진 미연결 로그가 있었으므로 엔진/한국어 데이터 확인이 필요합니다.
Pleos가 주행 중이면 사용 제한 화면이 표시될 수 있습니다.
자동 기준점 매핑, 실차 입력, 앰비언트·미러·창문·비상등 제어,
Gleo/외부 LLM 판단, 좌석별 오디오는 포함하지 않습니다.
