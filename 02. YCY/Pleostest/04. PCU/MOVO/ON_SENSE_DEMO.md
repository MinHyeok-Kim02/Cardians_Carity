# On-sense MVP 구현 및 시연 기록

## 기존 환경과 통합
- 단일 :app 모듈, 패키지 com.example.myapplication. 기존 main 소스에 Activity/Compose/XML 화면 없음.
- Pleos SDK 의존성, 속도·조향 입력, 시뮬레이션, AmbientSideProbe.kt 없음.
- 기존 AGP 9.4.1, Gradle 9.6.0, daemon JDK 25, Java 타깃 11, SDK 37/minSdk 24 유지. 버전/의존성 변경 없음.
- Android View + Canvas 기반 MainActivity를 런처로 추가. Pleos 기본 Driving View와 독립적인 앱 내부 시뮬레이션.

## 파일
- app/src/main/java/com/example/myapplication/AlleySimulation.java: 시나리오, 회전 차체 외곽, 최소 간격, 단계와 근접 진입 이벤트.
- app/src/main/java/com/example/myapplication/AlleyView.java: 골목/차량/방향별 기준선, 250ms 표시+250ms 어둡게 3주기(1500ms), 이후 빨강 유지.
- app/src/main/java/com/example/myapplication/WarningAudio.java: 직렬 큐, 짧은 비프 3개 뒤 Android TTS 방향 음성, 초기화/백그라운드/종료 시 오디오 정리.
- app/src/main/java/com/example/myapplication/MainActivity.java: 화면·조작·상태 보존. 각 탭으로 조향 5도/전진 25cm.
- app/src/test/java/com/example/myapplication/AlleySimulationTest.java: 신규 테스트 4개.
- app/src/main/AndroidManifest.xml: 런처 Activity, Android TTS 서비스 조회.
- app/src/main/res/values/strings.xml: On-sense 앱명.
- app/src/main/res/values/themes.xml 및 values-night/themes.xml: NoActionBar.
- ON_SENSE_DEMO.md: 이 기록.

## 좌표와 판정
미터 단위 월드 좌표. x는 오른쪽, y는 전방, 0도는 전방. 평행한 좌우 벽 x=0/4m, 차량 폭 1.8m/길이 4m, 초기 중심 (2,0), 방향 0도.
AlleySimulation.Scenario에서 초기값을 수정할 수 있다.
회전된 4개 모서리의 x 최솟값/최댓값과 벽 사이 간격을 계산한다. 벽 침범은 0. 중심 거리로 판단하지 않는다.
무한히 이어지는 골목에서 카메라는 y를 따라가며, 벽 접촉 시 추가 횡이동을 제한한다.
60cm 초과 초록 / 30cm 초과~60cm 주황 / 30cm 이하 빨강은 시연용 가상 기준이다.
근접 상태가 계속되면 이벤트가 추가되지 않는다. 해제 후 재진입하거나 초기화 후 접근하면 다시 알림.
화면 재생성 시 위치·방향·카운트·튜토리얼 설정을 보존하고 기존 근접 진입을 재생하지 않는다.

## 음성 방식
이번 구현은 Android TextToSpeech(Locale.KOREAN) 시연용이다. Pleos SDK 및 연결된 Pleos AVD가 없는 환경이므로 Pleos TTS 통합은 하지 않았다.
공식 문서: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
Pleos SDK는 전용 Speech Service와 에뮬레이터가 필요하다. 향후 실제 Pleos 환경에서는 공개 SDK ON_DEVICE 경로로 교체 후 검증할 수 있다.
앱에 한국어 TTS 준비 여부를 표시한다. 엔진/한국어 데이터가 없으면 음성 미준비를 표시하며 비프와 시각 경고는 계속 제공한다.
비프 시작 0/180/360ms, 마지막 비프 종료 약 540ms, TTS 요청 620ms. 반대 방향 이벤트도 큐에서 직렬 처리한다.
실제 소리 출력과 한국어 엔진의 가용성은 기기에서 확인해야 한다.

## 빌드·검증 결과
실행 명령:
    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain

최종 결과: BUILD SUCCESSFUL in 7s, exit code 0.
첫 빌드 테스트 콘솔: 5 tests completed, BUILD SUCCESSFUL (신규 4 + 기존 1).
검증한 로직: 경계값 30/60cm, 회전 차체 간격 수식, 벽 겹침 0, 좌우 각각 근접 1회, 유지 중 50회 무알림, 해제/재진입/초기화, 전진·조향으로 초록→주황→빨강→해제→재진입.
최종 assembleDebug와 testDebugUnitTest 모두 성공. 구형 Android 호환 API 사용에 따른 deprecation 경고 있음.
최종 빌드 전체 로그:
C:/Users/pcu17/.android/studio/agent/conversations/My-Application-e0e218e95c927ef8711590a98c24fa/ab84f793-e56e-4ff2-90d8-29c6c5c45c6f/session/agent/shell-logs/shell-3.log

adb devices -l 결과는 List of devices attached 헤더만 표시됨. 연결 기기 0대.
따라서 설치·실행, 화면 배치/색상, 점멸 실제 시간, 비프 청취, TTS 청취, Pleos 창에서의 표시, 기기 회전/백그라운드 복귀는 미검증.
빌드 산출물 폴더는 IDE AI exclude 정책으로 직접 열 수 없어 APK 파일 자체 검사는 하지 않았고 assembleDebug 성공으로 확인했다.

## 영상 시연 순서 (기기 연결 후)
Android Studio Run으로 앱 실행. 튜토리얼 음성 ON, 미디어 볼륨 확인, 한국어 TTS 준비 상태 확인.
1. 초기화: 좌우 약 110cm, 초록.
2. 우향 2회: 방향 +10도, 오른쪽 약 77cm, 초록.
3. 전진 5회: 오른쪽 약 55cm, 주황.
4. 전진 7회 추가: 오른쪽 약 25cm, 빨강. 1.5초 3주기 점멸 뒤 빨강 유지, 비프 뒤 오른쪽 방향 음성.
5. 2초 이상 유지 후 전진 1회: 같은 근접 상태, 근접 진입 카운트 1회 유지, 재알림 없음.
6. 좌향 4회: 방향 -10도. 전진 8회: 오른쪽 약 55cm, 주황으로 해제.
7. 우향 4회: 방향 +10도. 전진 8회: 오른쪽 약 20cm, 근접 진입 카운트 2회, 재알림.
8. 초기화 후 좌향 2회 → 전진 5회 → 전진 7회: 왼쪽 초록→주황→빨강 및 왼쪽 음성 확인.

Android Studio Logcat에서 OnSense 태그로 확인:
- STATE left=...cm right=...cm heading=... events=...
- NEAR_ENTER side=RIGHT/LEFT gapCm=... event=...
- AUDIO_START side=... event=...
- Android TTS Korean ready=true/false
- TTS_REQUEST side=... result=...
위 항목은 코드에 추가한 로그 형식이며 실제 기기 로그는 아직 수집하지 못했다.

자동 기준점 매핑, 실차/센서 입력, 앰비언트 제어, 사이드미러/창문/비상등, Gleo/외부 LLM, 구체적 조향 음성, 좌석별 오디오는 포함하지 않았다.
