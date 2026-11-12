# Alley Pass 개발 진행상황

동료 공유용 요약: [팀공유_AlleyPass_개발요약.md](팀공유_AlleyPass_개발요약.md) — 실제 기능별 제어 판정과 최신 가상 골목 구현만 요약. 이번 문서 작업에서 앱 코드는 변경하지 않음.

최초 작성: 2026-11-12  
기준 프로젝트: Pleostest / com.example.pleostest

이후 개발 작업마다 이 문서에 변경 파일, 변경 이유, 검증 결과 및 미검증 사항을 갱신한다.
이 문서는 현재까지 직접 확인한 결과와 향후 작업을 구분한다.

최신 기능별 통합표: [SDK_기능별_조회_제어_현황.md](SDK_기능별_조회_제어_현황.md). 초기 기록과 최신 실측이 다르면 통합표와 10절 이후를 우선한다.

## 1. 개발 범위와 보존 원칙

- 담당 기능: Alley Pass(좁은 골목길 교행 보조).
- 프로젝트 루트의 CarTalk_Pleos_Codex_인수인계_2026-11-12.txt를 읽고 개발 맥락을 확인했다.
- Ghost View / Ghost Line / 미래 주행 궤적은 개발 범위에서 제외한다.
- 기존 구현을 임의 삭제하거나 전면 재작성하지 않는다.
- 기존 위치 수신 테스트와 기본 화면을 유지했다.
- 초기에는 읽기 전용 진단을 추가했고, 이후 10절에서 디버그 에뮬레이터 전용 실제 쓰기·재조회·복원 테스트를 추가했다. 비상등·미러 폴딩 코드는 없다.
- 에뮬레이터 SDK 수신 검증이며 실차 검증은 아니다.

## 2. 초기 프로젝트 분석

| 항목 | 확인 결과 |
| --- | --- |
| 모듈 | 단일 Android 앱 모듈 :app |
| UI | AppCompatActivity와 XML 기반의 빈 기본 화면 |
| 기존 기능 | MainActivity에 FusedLocation 지원 확인, 권한 요청, 위치 콜백 수신 및 정리 코드 |
| Pleos Vehicle SDK | ai.pleos.playground:Vehicle:2.3.2 |
| 위치 SDK | ai.pleos.playground:FusedLocation:1.1.3 |
| SDK 저장소 | settings.gradle.kts에 Pleos Maven 저장소 설정 |
| Android 설정 | compileSdk 37 / targetSdk 37 / minSdk 28 |
| AGP | 버전 카탈로그 기준 9.5.0 |
| 초기 Manifest 권한 | pleos.car.permission.FUSED_LOCATION |
| 초기 빌드 | Android Studio Gradle 빌드 성공 |

환경 메모:
- 일반 셸의 gradlew 실행은 JAVA_HOME 및 PATH의 Java 미설정으로 실패했다.
- Android Studio의 Gradle 빌드는 성공했으므로 프로젝트 빌드 오류와 구분한다.
- 프로젝트 루트에서 git status 실행 시 Git 저장소가 아니라는 응답을 받았다. 따라서 Git diff 기반 변경 목록은 아니다.
- 실행 검증 장치: emulator-5554.

## 3. 완료: Pleos SDK 브레이크 수신 검증

### 구현

현재 의존성의 SDK 소스에서 타입·시그니처와 권한을 직접 확인했다.

- Vehicle.initialize() 및 getBrake() 사용.
- checkBrakePedalStatusCapabilities()로 읽기 지원 확인.
- getCurrentBrakePedalStatus()로 현재값 조회.
- registerBrakePedalStatus()로 변경 이벤트 구독.
- 화면 이탈 시 unregisterBrakePedalStatus() 및 Vehicle.release() 호출.
- Boolean true는 PRESSED, false는 RELEASED, null은 UNKNOWN으로 기록.
- 오류 또는 미지원 시 UNKNOWN으로 기록하며 해제로 간주하지 않는다.
- 로그 태그: CarTalkBrake.
- 데이터 출처: source=PLEOS.

### 검증 결과

빌드·설치·실행 성공. 앱에서 Capability READ 확인.

실제 수신 로그:

```text
Brake capability = READ
Brake = RELEASED; event=INITIAL; source=PLEOS
Brake = RELEASED; event=UPDATE; source=PLEOS
```

사용자가 Virtual Console에서 브레이크를 조작한 후, 자체 앱 Logcat에서 다음 변화를 직접 확인했다.

```text
Brake = PRESSED; event=UPDATE; source=PLEOS
Brake = RELEASED; event=UPDATE; source=PLEOS
Brake = PRESSED; event=UPDATE; source=PLEOS
Brake = RELEASED; event=UPDATE; source=PLEOS
```

판정: Virtual Console → Pleos Vehicle SDK → 자체 앱의 브레이크 누름·해제 실시간 전달 검증 완료.

## 4. 진행 중: AAOS 차량 속도 수신 검증

### API 확인 및 구현

- 현재 Pleos SDK의 Odometer는 주행 거리·시간 관련 API이며 실시간 속도로 사용하지 않았다.
- Android Automotive 표준 PERF_VEHICLE_SPEED 경로로 읽기 전용 진단을 추가했다.
- 에뮬레이터의 자동차 기능 선언 및 CarPropertyService의 속도 속성을 확인했다.
- Car 서비스 연결 후 속도 속성 설정을 확인하고 변경 콜백을 등록한다.
- 원본 m/s와 환산한 km/h를 함께 기록한다.
- 속도 권한 승인 후 시작하며 화면 이탈 시 콜백 해제 및 연결 종료.
- 사용 불가능한 값이나 오류는 UNKNOWN으로 기록한다.
- 로그 태그: CarTalkSpeed.
- 데이터 출처: source=AAOS. 브레이크의 source=PLEOS와 구분한다.
- 표준 API 참고: https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED

### 검증 결과

Gradle 빌드·동기화·설치·실행 성공. 실제 앱 로그:

```text
Speed capability access=1; source=AAOS
Speed listener registered=true; source=AAOS
Speed = 15.0 m/s; 54.0 km/h; event=UPDATE; source=AAOS
```

- 15.0 m/s(54.0 km/h) 값의 반복 수신을 직접 확인했다.
- 아직 Virtual Console에서 속도를 변화시켰을 때 앱 값도 변하는지는 검증하지 않았다.
- 따라서 속도 현재값 수신은 확인됐으나, 가속·감속 연동 검증은 남아 있다.
- 권한 팝업의 도구 탭 입력이 반영되지 않아 에뮬레이터 사용자 10의 해당 앱에 ADB로 android.car.permission.CAR_SPEED 권한을 부여하고 앱을 재시작했다.
- 앱에는 별도로 런타임 속도 권한 요청 코드가 있다. 일반 사용자의 권한 승인 UI 흐름 전체는 별도 확인이 필요하다.

## 5. 변경 파일 목록

경로는 프로젝트 루트 기준이다.

| 파일 | 변경 종류 | 변경 내용 |
| --- | --- | --- |
| app/src/main/java/com/example/pleostest/PleosBrakeTest.kt | 신규 | Pleos SDK 브레이크 Capability, 현재값 조회, 이벤트 구독 및 정리 진단 |
| app/src/main/java/com/example/pleostest/VehicleSpeedTest.kt | 신규 | AAOS CarPropertyManager 기반 속도 구독 및 연결 정리 진단 |
| app/src/main/java/com/example/pleostest/MainActivity.kt | 수정 | onStart/onStop에 브레이크·속도 진단 연결, 자동차 기능 확인, 속도 런타임 권한 요청과 결과 처리 추가 |
| app/src/main/AndroidManifest.xml | 수정 | pleos.car.permission.CAR_DRIVE 및 android.car.permission.CAR_SPEED 추가, android.car 선택적 uses-library 선언 추가 |
| app/build.gradle.kts | 수정 | useLibrary("android.car") 추가 |
| Alley_Pass_개발_진행상황.md | 신규 | 개발 진행상황 및 검증 기록 |

기존 FusedLocation 테스트, activity_main.xml 화면, SDK 의존성 버전, compileSdk/targetSdk 설정은 이번 작업에서 변경하지 않았다.

## 6. 재현 방법

1. Android Studio에서 app을 실행한다.
2. 앱을 에뮬레이터 전면에 유지한다. 화면은 기존처럼 비어 있다.
3. 필요하면 속도 읽기 권한을 허용한다.
4. PC에서 Virtual Console에 접속한다: http://127.0.0.1:8088/
5. 포트 포워딩이 없으면 다음 명령을 실행한다.

```powershell
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

6. Logcat에서 브레이크는 tag:CarTalkBrake, 속도는 tag:CarTalkSpeed로 필터링한다.
7. Console에서 브레이크를 눌렀다 놓아 PRESSED / RELEASED를 확인한다.
8. Console에서 가속·감속하여 속도 로그 값이 바뀌는지 확인한다.

주의: 화면 이탈 시 진단 구독을 해제하므로, 테스트 중 앱을 전면에 유지한다.
source=PLEOS / source=AAOS는 수신 API 경로이며 실차 센서 검증을 뜻하지 않는다.

## 7. 남은 작업

- [x] 인수인계 문서와 기존 프로젝트 분석
- [x] 기존 프로젝트 빌드 확인
- [x] Pleos SDK 브레이크 읽기 API·권한·Capability 확인
- [x] 브레이크 초기값 및 변경 이벤트 수신
- [x] Console 브레이크 누름·해제 연동 확인
- [x] AAOS 속도 속성 구독 및 현재값 수신
- [ ] Console 가속·감속에 따른 속도 변화 수신 확인
- [ ] 일반 권한 팝업 승인·거부 및 재진입 흐름 검증
- [x] Window Capability 및 읽기 권한·지원 범위 확인 (4개 창문 READ_WRITE, 범위 0~10, 현재값 조회 성공)
- [ ] 안전 정차 조건과 명시적 사용자 요청을 전제로 창문 쓰기 및 재조회 검증
- [ ] Alley Pass 화면 및 공통 상태 구조 설계·연동
- [ ] 비상등·사이드미러·주변 거리 API 지원 여부 확인
- [ ] 미지원 기능의 시뮬레이션 구분 및 팀 기능 통합

속도만으로 정차·P단·제어 안전 조건이 검증됐다고 간주하지 않는다.
아래 8절은 정적 분석 당시 기록이다. 이후 Window 등 실제 Capability·읽기 검증 결과는 9절을 참고한다. 실제 쓰기 제어는 아직 미검증이다.

## 8. 추가 SDK 점검 (2026-11-12)

상세 결과: [Pleos_SDK_점검_2026-11-12.md](Pleos_SDK_점검_2026-11-12.md)

- 이번에는 분석 요청에 따라 앱 코드·Manifest·Gradle을 수정하지 않고 문서만 작성했다.
- Vehicle 2.3.2의 Window / Light / SideMirror / Safety / CarInfo / SteeringWheel / TurnSignal 및 PleosPermission 소스를 확인했다.
- CAR_SAFETY는 SDK와 설치된 Vehicle Service에 실제 존재하는 normal 권한이다.
- pleos.car.permission.CAR_SPEED는 확인되지 않았다. 기존 android.car.permission.CAR_SPEED 사용을 유지한다.
- Window 읽기·쓰기 API가 존재한다. 지원 영역·범위·장치 Capability 및 실제 호출은 아직 미검증이다.
- Light 앰비언트 ARGB 색상 쓰기 API가 존재한다. 현재 인터페이스는 일부 앰비언트 쓰기에도 CAR_LIGHTS를 요구한다. 장치 적용은 미검증이다.
- SteeringWheel 각도는 앞바퀴 조향각이며 핸들 회전각이 아니다.
- SideMirror는 열선만 제공하며 폴딩 메서드가 없다.
- TurnSignal은 읽기만 제공한다. 비상등 쓰기 API는 확인되지 않았다.
- Safety에 주변 차량·장애물 거리 API가 없으며 현재 Vehicle 진입점에 별도 ADAS 도메인도 확인되지 않았다.
- CarInfo는 VIN 중심이며 모델·차폭·전장 조회 API는 확인되지 않았다.
- 최근 앱 로그에서도 속도 15 m/s(54 km/h) 반복 수신을 확인했다. 가속·감속 변화 검증 상태는 미완료로 유지한다.
- 추가 Pleos SDK 설치는 하지 않았다.
- 다음 제안: 기존 속도 변화 검증 → 창문 Capability·읽기 전용 테스트 → 안전 정차 및 사용자 버튼 기반 쓰기 검증.

## 9. 에뮬레이터 Capability·현재값 실측 (2026-11-12)

이 절은 8절의 정적 분석 이후 자체 앱에서 직접 조회한 최신 결과이다.
이번 기록 보완에서는 앱 코드를 추가 수정하지 않았다.

### 직전 구현에서 변경한 파일

- 신규: app/src/main/java/com/example/pleostest/PleosCapabilityTest.kt
  - Capability·현재값·권한 승인 상태를 기록하는 읽기 전용 진단.
  - 로그 태그: CarTalkCapability. 실제 차량 쓰기 호출 없음.
- 수정: app/src/main/java/com/example/pleostest/MainActivity.kt
  - onStart/onStop에 Capability 진단 시작·해제 연결. 기존 테스트 보존.
- 수정: app/src/main/AndroidManifest.xml
  - pleos.car.permission.CAR_WINDOWS, CAR_LIGHTS, CAR_MIRRORS 추가.
  - CONTROL_CAR_WINDOWS / CONTROL_CAR_LIGHTS / CONTROL_CAR_MIRRORS는 추가하지 않음.
- 수정: 이 MD 파일에 검증 결과와 남은 작업 기록.

### 확인 결과

Android Studio 빌드·설치·실행 성공. emulator-5554의 자체 앱 Logcat에서 직접 확인했다.

| 기능 | 장치 Capability | 지원 범위 및 현재값 | 판정 |
| --- | --- | --- | --- |
| 창문 위치 | READ_WRITE | ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT 각각 0~10, 현재값 각 0 | 읽기 성공. 쓰기 지원 선언 확인, 실제 제어는 미검증 |
| 앰비언트 밝기 | READ_WRITE | 1·2열 좌석이 묶인 영역, 0~100, 현재값 100 | 읽기 성공, 쓰기 후보 |
| 앰비언트 색상 | READ_WRITE | 같은 좌석 그룹, 현재 정수값 16777215 (0x00FFFFFF) | 읽기 성공, 실제 색상 적용 미검증 |
| 앰비언트 스위치 | READ_WRITE | 같은 좌석 그룹, AUTOMATIC / ON / OFF | Capability 확인. 현재 스위치 값·쓰기 미검증 |
| 사이드미러 열선 | READ_WRITE | DRIVER_LEFT, DRIVER_RIGHT 및 좌우 조합, 0~3, 현재값 각 0 | 읽기 성공. 접기·펴기와 별개 |
| 조향각 | READ | 장치 응답 범위 -470~500, 현재값 -0.0 degrees | 읽기 성공. SDK KDoc상 앞바퀴 조향각, Console 대응 추가 검증 필요 |
| 방향지시등 상태 | READ | 전역 상태 | Capability 확인. 현재값 조회·쓰기 없음 |
| 사이드미러 접기·펴기 | 해당 공개 API 미발견 | Vehicle 2.3.2 SideMirror는 열선 API만 제공 | 현재 확인한 공개 SDK로 구현 불가 |
| 비상등 작동 | 제어 API 미발견 | Light / TurnSignal 인터페이스 확인 | 현재 확인한 공개 SDK로 제어 구현 불가 |
| 장애물·주변 차량 거리 | 해당 API 미발견 | 현재 Vehicle / Safety 공개 인터페이스 기준 | 거리 시뮬레이션 분리 필요 |

실제 로그 예시:

```text
Permission CAR_WINDOWS granted=true
Permission CONTROL_CAR_WINDOWS granted=false
Permission CAR_LIGHTS granted=true
Permission CONTROL_CAR_LIGHTS granted=false
Permission CAR_MIRRORS granted=true
Permission CONTROL_CAR_MIRRORS granted=false
Window.position access=READ_WRITE; source=PLEOS; writeTest=NOT_RUN
Window area=ROW_1_RIGHT bounds=AreaBounds(minValue=0, maxValue=10)
Window area=ROW_1_RIGHT position=0; source=PLEOS
Ambient.brightness access=READ_WRITE; source=PLEOS; writeTest=NOT_RUN
Ambient.color access=READ_WRITE; source=PLEOS; writeTest=NOT_RUN
Ambient.switch access=READ_WRITE; source=PLEOS; writeTest=NOT_RUN
Mirror.heat access=READ_WRITE; source=PLEOS; writeTest=NOT_RUN
FrontWheel.angle access=READ; source=PLEOS; writeTest=NOT_RUN
TurnSignal.state access=READ; source=PLEOS; writeTest=NOT_RUN
```

### 해석 시 주의

- READ_WRITE는 장치의 지원 선언이다. 실제 set 요청과 재조회 성공을 뜻하지 않는다.
- 창문·열선 쓰기 권한은 현재 앱에 없다. 지원이 없어서가 아니라 아직 추가·검증하지 않은 상태다.
- 일부 앰비언트 set 메서드는 현재 SDK 선언상 CAR_LIGHTS를 요구한다. CONTROL_CAR_LIGHTS 미승인만으로 제어 불가 판정하지 않는다.
- 창문 위치 0이 열림인지 닫힘인지, 0~10이 어느 정도 개방량인지 아직 확정하지 않았다.
- 묶인 좌석 영역 응답은 좌석별 독립 제어 성공 증거가 아니다.
- 색상 KDoc의 불투명 ARGB 설명과 읽기값 0x00FFFFFF의 alpha 표현이 다르므로 쓰기·재조회 시 확인이 필요하다.
- 조향각 장치 범위가 크므로 핸들 회전각으로 임의 재해석하거나 조향 명령에 사용하지 않는다.
- 직전 문서 편집·로그 저장 도구가 지연되어 중단됐다. 이번 확인에서 기존 MD에 실측 내용이 없고 별도 실측 로그 파일도 없는 것을 확인하여 이 절로 보완했다.

### 남은 작업과 다음 순서

- [x] 창문·조명·열선 Capability 및 지원 영역·범위 확인
- [x] 창문 위치·앰비언트 밝기/색상·열선 현재값 조회
- [ ] 속도·기어 등 최신 상태로 정차 조건 확인
- [ ] 사용자 버튼 → 창문 범위 내 제어 요청 → 완료/오류 → 위치 재조회 → Console 실제 움직임 확인
- [ ] 앰비언트 원래 값 보관 → 제어 → 재조회 → 원래 값 복원 검증
- [ ] Console 가속·감속에 따른 속도 변화 검증
- [ ] 기능별 Capability 확인 / 실제 제어 성공 / 미지원 / 미확인을 구분한 Alley Pass UI 구현

위 내용은 읽기 검증 당시 기록이다. 이후 실제 쓰기 시험 결과는 다음 10절을 우선한다.

## 10. SDK 실제 쓰기·재조회·복원 검증 (2026-11-12)

상세 보고서: [SDK_실제제어_검증_2026-11-12.md](SDK_실제제어_검증_2026-11-12.md)

- 사용자가 Console 정차 완료를 알린 후, 앱에서도 매 요청 전 속도 0.0 m/s 및 P단 확인.
- 신규 PleosControlTest.kt 및 debug/AndroidManifest.xml 추가.
- MainActivity.kt에 디버그 에뮬레이터용 명시적 테스트 버튼과 종료 정리 연결.
- 추가 제어 권한은 debug Manifest에만 선언. 기존 수신 코드·패키지·SDK 버전 보존.
- Android Studio 빌드·설치·실행 성공. 실제 앱 버튼으로 7개 항목을 순차 시험.
- 사이드미러 열선: 0→1→0, 변경 및 복원 재조회 성공.
- 앰비언트 스위치: OFF→AUTOMATIC→OFF, 모드 변경 및 복원 재조회 성공. 실제 점등 ON 모드는 미검증.
- 창문: 0→1 및 0→10 요청의 완료 콜백은 수신했으나 재조회 0 유지.
- 앰비언트 밝기: 100→99 및 100→50 요청 후 재조회 100 유지.
- 앰비언트 색상: ARGB/RGB 빨강 요청 후 재조회 흰색 유지.
- 도어 잠금: 묶음 영역 요청 후 기존 잠금값 유지. 개별 조수석 영역은 장치의 unsupported area 오류.
- 도어 위치: 0→1 완료 콜백 수신, 재조회 0 유지.
- 모든 실제 요청 후 복원 시퀀스를 수행했다. 최종 원래값 재조회 확인.
- 중요 정정: Light 인터페이스/KDoc와 달리 실제 LightImpl의 앰비언트 쓰기는 CONTROL_CAR_LIGHTS를 요구한다. 추가 후 권한 오류는 해소됐다.
- 현재 환경에서 창문·도어·밝기·색상은 '제어 성공'이 아니라 '요청 후 적용 미확인'이다. 영구 미지원으로 단정하지 않는다.
- 원인은 장치 미구현·Console 덮어쓰기·모드/잠금 조건·캐시 중 아직 미분리.
- 앱 로그 기반 상태 검증이며 Console 시각 변화 및 실차 동작 검증은 별도다.
- 다음: 앰비언트 ON 모드 조건, 창문 잠금 및 상태 미반영 원인 확인. 제어가 검증되기 전 해당 MVP 버튼은 성공 기능으로 표시하지 않는다.

## 11. 기능별 조회·제어 통합 정리 (2026-11-12)

- 신규 [SDK_기능별_조회_제어_현황.md](SDK_기능별_조회_제어_현황.md) 작성.
- 조회 정보 / 제어 API 존재 / 앱 조회 성공 / 실제 제어 검증 결과를 분리한 표 제공.
- Vehicle의 전체 도메인 및 FusedLocation·AAOS 사용 경로를 정리.
- HVAC, Seat, EvBattery, Fuel, DrivingMode, Display, Wiper, Tire, Toll 소스를 추가 분석.
- 추가 기능은 API 존재만 확인했으며 장치에서 미시험이라고 명시. API 존재를 실제 제어 성공으로 표시하지 않음.
- 권한, 대표 메서드, 현재 환경 적용 미확인 항목과 SDK 미발견 항목을 구분.
- 당시 작업은 문서만 변경했으며 앱 코드·Manifest·Gradle 변경 및 추가 제어 호출은 없었음.

## 12. 공조 단독 제어 및 추가 조회 (2026-11-12)

- 최초 추가 테스트 준비는 사용자 중단으로 완료되지 않았음. 파일·로그 재확인 후 재개.
- 다기능 쓰기 배치는 자동 승인 검토에서 '공조·잠금·와이퍼·ETCS를 묶은 제어 범위에 대한 구체적 승인 부족'으로 거부됨. 거부된 파일·버튼은 실행하지 않음.
- 중간의 미완성 extendedTests 참조를 조회 전용 readOnlyTests로 교체해 컴파일 정상화.
- 신규 PleosReadOnlyTests.kt: 공조 온도/현재온도/풍량/냉방/전원, 글로브박스·창문 잠금, ETCS 음량 조회.
- PleosControlTest.kt: 조회 전용 경로와 공조 온도만 시험하는 별도 경로 추가. 기존 7항목 테스트 유지.
- MainActivity.kt: 조회 전용 버튼 및 '공조 온도만 1도 변경·재조회·복원' 버튼 추가.
- debug/AndroidManifest.xml: CAR_CLIMATE / CAR_TOLL 읽기 권한 및 온도 단독 시험용 CONTROL_CAR_CLIMATE 추가.
  CONTROL_CAR_TOLL은 제거. 새 글로브박스·창문 잠금·와이퍼 쓰기는 실행하지 않음.
- 빌드 및 에뮬레이터 배포 성공. 앱 버튼으로 조회와 온도 단독 시험 실행.

| 추가 항목 | 결과 |
| --- | --- |
| 공조 설정온도 | 좌우22℃, 범위15~30℃, READ_WRITE |
| 실내 현재온도 | 좌우19.1℃ 조회 성공 |
| 풍량 | 3, 범위1~8, READ_WRITE. 제어 미시험 |
| 냉방 / 전원 | 각각 true, READ_WRITE. 제어 미시험 |
| 글로브박스 잠금 | false 조회 성공. 제어 미시험 |
| 창문 잠금 | 지원 영역 false 조회 성공, READ_WRITE. 제어 미시험 |
| ETCS 음량 | property 0x21400d01 장치 미지원 오류. 제어 미시험 |
| 공조 온도 단독 제어 | 속도0·P 확인 후22→23℃ 요청. 완료 콜백 수신했으나 반복 조회22℃ 유지. 적용 미확인. 복원 요청 후22℃ 재확인 |

핵심 로그:

```text
HVAC.Temperature BEFORE=22.0 REQUEST=23.0
HVAC.Temperature SET_CALLBACK received; verifying independent readback
HVAC.Temperature requested=23.0 actual=22.0
HVAC.Temperature RESTORE_VERIFIED original=22.0
HVAC.Temperature NOT_VERIFIED: HVAC.Temperature readback did not match
```

모든 SDK 기능을 다 시험한 상태는 아니다. 좌석·충전·와이퍼 등 기존 미시험 표시는 유지한다.
추가 제어가 필요하면 항목별 범위를 명시해 순차 진행한다.

## 13. 공조 풍량 단독 제어 시험 (2026-11-12)

- 사용자가 풍량 3→4→3 단독 시험을 승인하여 실행.
- PleosControlTest.kt: fanOnly 실행 경로와 hvacFanSpeed() 추가.
- MainActivity.kt: '공조 풍량만 3→4→3 시험' 전용 버튼 추가.
- 기존 공조 권한 사용. Manifest·SDK 버전은 이번 단계에서 변경하지 않음.
- 디버그 에뮬레이터에서만 시험. 속도0·P, 현재 풍량3 및 3/4의 지원 범위를 확인한 후 요청.
- 빌드·배포 성공. 앱의 풍량 전용 버튼을 도구로 눌러 시험.
- Capability READ_WRITE, 좌우1열 묶음 영역, 지원 범위1~8.
- 4단계 요청 완료 콜백은 수신했으나 약4초 반복 재조회 최종값은3.
- 원래값3 복원 요청 후 재조회 확인.
- 판정: 조회 성공 / 쓰기 완료 응답 수신 / 변경값 적용 미확인. 제어 성공으로 표시하지 않음.
- 기능별 현황 MD의 풍량 상태도 갱신. 냉방·전원 등 다른 기능은 이번에 제어하지 않음.
- 이후 Alley Pass 시뮬레이션 구현은 아래 14절 참고.

```text
Safety speed=0.0 m/s gear=P
HVAC.FanSpeed BEFORE=3 REQUEST=4
HVAC.FanSpeed SET_CALLBACK received; verifying independent readback
HVAC.FanSpeed requested=4 actual=3
HVAC.FanSpeed RESTORE_VERIFIED original=3
HVAC.FanSpeed NOT_VERIFIED: HVAC.FanSpeed readback did not match
```

## 14. Alley Pass 가상 거리 시뮬레이션 화면 (2026-11-12)

이 절은 수동 거리 입력 방식의 이전 구현 기록이다. 최신 가상 골목·실제 속도/조향 연동은 15절을 참고한다.

### 구현 내용

- 실제 에뮬레이터 속도(AAOS)·브레이크(Pleos SDK)와 시험용 가상 좌우 거리를 한 화면에 표시.
- 거리 입력은 0~150cm 슬라이더. 거리 여유(120/120), 오른쪽 근접(70/20), 왼쪽 근접(20/70) 프리셋 제공.
- 거리 출처를 SIMULATION으로 명시하며 차량 센서 측정값이 아니라고 화면에 표시.
- 좁은 골목 시나리오 켜기/끄기 및 위험 방향에 따른 안내 제공.
- 규칙은 시연용: 30cm 이하 근접, 60cm 이하 주의, 속도 10km/h 초과 시 감속·주변 확인 안내.
- 통과 가능·안전 보장 판정은 하지 않으며 실제 차폭·장애물 형상은 반영하지 않음.
- 속도 미수신 또는 5초 이상 갱신 지연은 대기/지연으로 표시하고 0으로 대체하지 않음.
- 브레이크는 마지막 수신값 표시. 10초 이상 경과 시 마지막 수신값임을 별도로 표시.
- 화면 이탈 시 차량 표시값 초기화. 회전·재생성 시 가상 거리와 시나리오 설정을 Bundle로 복원.
- 기존 SDK 제어 버튼은 삭제하지 않고 접힌 '개발자 SDK 테스트' 영역에 보존.
- 이 시뮬레이션 화면은 차량 제어를 자동 실행하지 않음. 음성 코칭 연결은 아직 없음.

### 변경 파일

- 신규 app/src/main/java/com/example/pleostest/AlleyPassEngine.kt
  - 가상 거리·속도 기반 순수 판단 함수.
- 신규 app/src/main/java/com/example/pleostest/AlleyPassSimulationView.kt
  - 기존 View/XML 방식과 호환되는 ScrollView 기반 화면, 거리 입력, 상태·출처·안내 표시.
- 신규 app/src/test/java/com/example/pleostest/AlleyPassEngineTest.kt
  - 시나리오 끄기, 경계값, 방향, 미수신/NaN, 후진 속도 판단 시험.
- 수정 MainActivity.kt
  - 메인에 시뮬레이션 화면 추가, 기존 진단 UI 연결, 화면 상태 저장·복원.
- 수정 VehicleSpeedTest.kt / PleosBrakeTest.kt
  - 기존 로그 유지. 읽은 값을 UI에 전달하는 선택적 콜백 추가 및 종료 시 UNKNOWN 전달.
- 이번 단계에서 Manifest 권한, SDK 버전, 차량 제어 테스트 로직은 변경하지 않음.

### 검증 결과

- :app:testDebugUnitTest 및 :app:assembleDebug 성공. 6개 테스트 통과, 실패0.
- emulator-5554에 배포·실행 성공. 실행 화면을 스크린샷으로 직접 확인.
- 차량 속도 64→48km/h 표시 변화 확인. 브레이크 해제 상태 표시 확인.
- 오른쪽 근접 프리셋: 좌70/우20cm, '오른쪽 근접 · 정차 후 확인' 표시 확인.
- 거리 여유 프리셋: 좌우120cm, 속도48km/h에서 '속도를 낮추고 주변 확인' 표시 확인.
- 시나리오 끄기: '시나리오 대기' 및 슬라이더 비활성화 확인. 이후 시나리오 다시 활성화.
- 왼쪽 근접 화면도 실행 스크린샷에서 확인. 규칙 경계는 단위 테스트로 확인.
- 회전 시 Bundle 복원 코드는 추가했으나 기기 회전 동작 자체는 별도 미검증.
- UI 계층 덤프 도구가 일부 실패해 화면 검증은 스크린샷과 앱 로그를 사용.

### 실행 방법

1. Android Studio에서 app 실행.
2. 좁은 골목 시나리오를 켜고 좌우 슬라이더 또는 프리셋 버튼 조작.
3. Virtual Console(127.0.0.1:8088)의 가속·브레이크로 차량 상태를 바꾸면 화면 수신값 확인.
4. 가상 거리는 우리 앱에서 입력하며 Console의 실제 장애물 센서값이 아님.
5. 기존 진단은 '개발자 SDK 테스트 펼치기'에서 사용 가능. 시뮬레이션 실행에 제어 테스트 버튼을 누를 필요는 없음.

다음 남은 범위: 팀 음성 모듈 연동, 가상 시나리오 확장, 차량 제어 상태 미반영 원인 조사.

## 15. 가상 골목·주차 차량 + 속도·조향 연동 앱 화면 (2026-11-12)

### 현재 동작

- 수동 거리 슬라이더·프리셋을 제거하고, 고정된 주차 차량 6대와 골목 경계가 있는 2D 가상 환경으로 전환.
- 실제 수신 속도(AAOS)와 조향(Pleos SDK)에 따라 가상 내 차의 위치·방향을 시간에 따라 갱신.
- 조향이 바뀌면 차체 방향과 좌우 위치가 바뀌고, 속도가 높으면 더 빠르게 이동.
- 차체와 주차 차량 간 폴리곤 거리 및 골목 경계 여유를 계산하여 근접·접근 방향 경고.
- 가상 접촉 시 해당 시뮬레이션 이동을 멈추고 빨간 경고 표시. 실제 차량 제동·조향 명령은 보내지 않음.
- 속도·조향 미수신 또는 3초 갱신 지연이면 가상 이동 보류.
- 실제 센서 장애물 거리·실제 차량 위치를 측정하는 기능이 아님. 지도와 거리 모두 SIMULATION으로 표시.
- 미래 궤적이나 Ghost View는 추가하지 않음.
- SDK 점검 버튼과 진단 정보, MainActivity의 자동 Capability/위치/제어 진단 시작을 제거.
- 기존 진단 Kotlin 파일과 과거 보고서는 보관하며, 현재 메인 화면에서 호출하지 않음.

### 시뮬레이션 모델의 한계

- 가상 도로 폭7m, 내 차 폭1.8m·길이4.2m·휠베이스2.7m, 주차 차량 폭1.8m·길이4.4m를 가정.
- 실제 모델·제원 자동 인식이 아님.
- SDK 조향 KDoc는 앞바퀴 각도라고 명시하지만 에뮬레이터 Capability 범위가 -470~500이므로,
  원본을 실제 핸들 회전각으로 단정하지 않음.
- 현재 시연에서는 Capability 절댓값 최대치로 정규화한 -1~1 입력을 가상 모델의 ±28도 조향으로 매핑.
- 조향에 따른 움직임을 재현하는 데모 이득값이며, 실차 물리 보정·충돌 회피 검증 결과가 아님.
- 한 프레임의 이동을 작은 구간으로 나눠 가상 장애물을 건너뛰는 현상을 줄임.
- 카메라는 내 차를 따라가며 현재 상태만 표시.

### 화면 영역 수정

- 이전 일반 ADB am start 실행으로 만들어진 fullscreen 태스크가 좌측 Driving view를 덮는 상태였음을 dumpsys로 확인.
- 실제 앱 창을 Pleos 앱 라이브러리에서 실행하여 Driving view와 분리.
- MainActivity의 enableEdgeToEdge 제거, 시스템 바·컷아웃 인셋 처리 및 부모/Canvas 클리핑 적용.
- 보라색 ActionBar 제거. 화면은 앱에 배정된 View 크기만 사용하며 디스플레이 전체 크기나 오버레이를 사용하지 않음.
- landscape 화면 설정으로 오른쪽 앱 영역을 사용. portrait 고정 시 발생하던 내부 검은 여백도 해소.
- 설치는 NO_ACTIVITY 후 Pleos 런처 아이콘으로 실행해 검증했으며, 일반 ADB 전체 화면 실행은 하지 않음.
- 최종 스크린샷에서 왼쪽 차량 기본 정보, 상단 상태 바, 하단 GNB가 유지되고 앱은 오른쪽 영역에만 표시됨.
- 다른 해상도·장치의 전체 검증은 아직 미완료. Window 크기에 맞춘 스크롤·클리핑 적용.

### 주행 제한 처리

- 일반 앱 상태에서는 D단에서 Pleos가 주행 중 사용 불가 화면으로 가리는 것을 직접 확인.
- 공식 DO 및 CarUxRestrictions 개발 문서를 확인.
- **debug Manifest에만** MainActivity의 distractionOptimized 메타데이터 추가. release Manifest에는 선언하지 않음.
- DrivingUxSource에서 실제 CarUxRestrictions를 구독하고, 미확인 상태는 제한 중으로 취급.
- 주행 제한 중 시작/초기화 버튼 비활성화, 상세 설명 축소.
- 디버그 에뮬레이터의 주행 입력 시연에 한정. 에뮬레이터가 아니면서 주행 제한이 활성화되면 시뮬레이션 이동 중지.
- 출시 앱의 주행 중 허용·심사를 통과했다는 의미가 아니며 출시 정책·안전 UI 검증은 별도 필요.
- 다시 시연하려면 P단에서 초기화·시작을 수행한 후 Console에서 저속·조향 입력.

공식 참고:
- [Pleos 앱 화면·멀티윈도우 모델](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)
- [Pleos 주행 제한 개발 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [AAOS DO 선언](https://source.android.com/docs/automotive/driver_distraction/guidelines)

### 변경 파일

- 신규 AlleyWorld.kt: 가상 차체 이동·거리·접촉 모델.
- 신규 AlleyMapView.kt: 앱 View 경계 내 가상 골목·주차 차량·내 차 렌더링.
- 신규 PleosSteeringSource.kt: 조향 Capability·현재값·콜백, 갱신 확인용1초 조회, 정리.
- 신규 DrivingUxSource.kt: UX 제한 상태 구독·정리.
- 신규 AlleyWorldTest.kt: 정지, 전진·후진, 좌우 조향, 접근 거리, 접촉 정지, 무효 입력 검증.
- AlleyPassSimulationView.kt: 수동 입력 화면을 실제 속도·조향 연동 골목 화면으로 교체.
- MainActivity.kt: 진단 UI/진단 자동 시작 제거, 속도·브레이크·조향·UX 상태 수신 연결.
- main/AndroidManifest.xml: landscape 및 resizeableActivity 설정.
- debug/AndroidManifest.xml: 에뮬레이터 시연용 DO 선언.
- values/themes.xml 및 values-night/themes.xml: NoActionBar 적용.
- 기존 SDK 의존성 버전·패키지 유지. 차량 기능 제어 코드는 추가하지 않음.

### 검증 결과

- :app:testDebugUnitTest :app:assembleDebug 통과. 전체12개 테스트 성공, 실패0 (새 모델6개 + 기존6개).
- Pleos 런처의 실제 앱 영역에서 배포·실행 확인.
- P단에서 시작 버튼 동작과 속도0·조향중앙 수신 확인.
- 사용자 Console 가속·조향 후 D단에서도 앱 표시 유지 및 UX 제한에 따른 버튼 비활성화 확인.
- 속도에 따른 y좌표 변화와 좌조향에 따른 x·heading 변화를 앱 로그로 직접 확인.
- 가상 왼쪽 주차 차량 접촉 시 빨간 '가상 접촉 · 시뮬레이션 정지', 거리0.00m 표시 확인.
- 동일 스크린샷에서 시스템 Driving view와 앱 모두11km/h 표시 확인.
- 왼쪽 차량 정보 영역을 덮지 않는 상태로 주행 시연 확인.
- 시스템 전체 UI 계층 덤프는 애니메이션 중 일부 실패하여 스크린샷 및 로그로 검증.

실제 로그 예:

```text
x=0.0 y=4.0 heading=0.0 speed=0.0 steering=-0.0
x=0.0 y=9.428611357569695 heading=0.0 speed=1.9444445 steering=-0.0
x=-0.07125740755756772 y=12.599266369485845 heading=-0.06547315561721422 speed=1.1111112 steering=-0.172
x=-0.23978636854368876 y=14.384992234678288 heading=-0.12145151251323219 speed=0.0 steering=-0.14
```

최종 상태: 사용자의 조작으로 가상 접촉 후 시뮬레이션 정지. P단에서 '처음 위치로' 후 '시뮬레이션 시작'으로 재시험 가능.








