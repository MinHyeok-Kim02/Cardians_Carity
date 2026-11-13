# On-sense 기능 2 — 가상 골목 간격과 실내 조명

기준: 사용자가 최신화한 현재 Pleos test 프로젝트. 외부 ZIP을 다시 덮어쓰지 않았습니다.

## 적용 방법

배포 ZIP을 기존 Pleostest 프로젝트 루트(settings.gradle.kts가 있는 폴더)에 풀어 덮어씁니다.
수정·추가 파일만 포함합니다. Gradle, AGP, JDK, applicationId, 차량 데이터 수신기, 거리 계산은 변경하지 않았습니다.
기존 앱 데이터 삭제/재설치 전에 반드시 차량 조명 복원을 완료하세요.

## 화면과 계산

- YCY 병합 후 상단에는 실제 속도·SDK 원본 조향각·기어·브레이크 정보 바가 표시됩니다. 상세: [YCY 병합 기록](YCY_MERGE_README.md).
- 그 아래 콘텐츠는 좌우 50:50입니다. 왼쪽은 기존 3D 골목·간격·수직 기준선, 오른쪽은 차량 실내 연출·Light 재조회 결과입니다.
- 하단 키보드 연결·기어 선택·SDK 연동·자동 시연·Alley Pass 가상 매크로를 유지했습니다.
- 현재 제공된 이미지는 Virtual Console 화면이므로 실내는 별도 비트맵 없이 Canvas로 그린 운전석·대시보드·도어·Pleos 디스플레이 연출입니다.
- 기존 AAOS 차속·Pleos 조향각 → 기존 AlleyWorld 이동 → clearance().leftMeters/rightMeters를 그대로 사용합니다.
- 실내 그림에는 거리 계산이나 근접 재현 버튼이 없습니다. SideClearanceWarning의 동일한 좌우 Frame을 받아 표시합니다.
- 거리 기준: 0.3m 이하 빨강, 0.3m 초과~0.8m 이하 주황, 0.8m 초과 초록. 미확인 값은 회색입니다.
- 빨강에 새로 진입한 쪽 기준선만 500ms 전체 주기(250ms 켬+250ms 끔)로 3회 점멸하고 계속 켭니다.
  같은 빨강 구간에 머무르면 반복하지 않습니다. 빨강을 벗어난 뒤 재진입하면 다시 3회 점멸합니다.
- 앱 기준선과 그림 속 Pleos 기준선은 동일한 Frame을 사용합니다. 그림 속 도어·대시보드 조명은 해당 경고색을 유지합니다.
- 좌우 간격은 가상 벽·가상 주차 차량 기준이며 실제 장애물 거리 센서 데이터가 아닙니다.
- 자동 시연은 기존의 가상 차속·조향 입력 경로이며 SDK 수신값이라고 표시하지 않습니다.

## 차량 조명

1. Pleos AVD를 실제 P·0km/h로 유지합니다.
2. 오른쪽 '차량 무드라이트 연동 · 정차 AVD'를 켭니다.
3. 기존 계산의 좌우 경고 상태가 바뀌면 지원되는 Light 영역에 색을 요청합니다.
4. 스위치를 끄거나 시연을 종료/초기화하거나 앱이 화면을 벗어나면 원래 색 복원을 요청하고 재조회합니다.

읽기/쓰기 모두 Vehicle 2.3.2 Light 소스에 명시된 pleos.car.permission.CAR_LIGHTS를 사용합니다.
기존 Manifest에 이미 선언되어 있어 권한을 추가하지 않았습니다.

- checkAmbientLightBrightnessColorCapabilities의 READ_WRITE와 실제 반환 SeatArea만 사용합니다.
- 좌측 전용 영역은 왼쪽, 우측 전용 영역은 오른쪽 경고색입니다. 공통·중앙 영역은 더 높은 위험색입니다.
- 겹치는 영역을 중복 제어하지 않도록 단일 좌석 영역부터 선택합니다. 지원되지 않는 좌우 제어 영역을 만들어 보내지 않습니다.
- 색상만 변경합니다. 원래 밝기·스위치를 조회·기록하고 유지합니다. OFF 상태를 강제로 ON으로 만들지 않습니다.
- 모든 SET 직전 AAOS의 실제 GEAR_SELECTION=P, PERF_VEHICLE_SPEED=0 및 AVAILABLE 상태를 별도로 검사합니다.
- 디버그 automotive 에뮬레이터에서만 쓰기를 허용합니다. 실제 주행 중 연속 조명 제어는 이번 범위 밖입니다.
- 콜백의 반환색을 성공으로 간주하지 않습니다. SET 뒤 별도 GET을 최대 3회 실행하고 요청색과 RGB를 비교합니다.
- 원래 색은 첫 SET 이전 SharedPreferences에 저장합니다. 복원 재조회가 일치해야 기록을 지웁니다.
- 복원 중 정차 조건이 풀리거나 SDK 오류가 나면 '복원 대기/미확인'으로 남기고 다음 정차 또는 앱 실행 시 재시도합니다.
  앱 강제 종료·데이터 삭제·AVD 종료로 인한 즉시 복원은 보장할 수 없습니다.
- 차량 조명 실패는 화면 색 연출·기존 수동/키보드 동작을 중단시키지 않습니다.
- 실내 그림의 색은 '실내 조명 연출'이며 실제 조명 적용 성공의 증거로 사용하지 않습니다.

## 코드

| 상대 경로 | 역할 |
| --- | --- |
| app/src/main/java/com/example/pleostest/AlleyPassSimulationView.kt | 50:50 배치, 기존 gap 전달, 시연 생명주기 연결 |
| app/src/main/java/com/example/pleostest/SideClearanceWarning.kt | 좌우 단계·빨강 진입·점멸·영역별 색 선택 |
| app/src/main/java/com/example/pleostest/InteriorDemoView.kt | 실내 Canvas 연출과 앱 수직 기준선 |
| app/src/main/java/com/example/pleostest/AmbientLightBridge.kt | 지원 조회·원래 상태 저장·정차 검사·SET/GET·복원 |
| app/src/test/java/com/example/pleostest/SideClearanceWarningTest.kt | 경계값·독립 점멸·재진입·공통 영역·기존 기하 연결 |
| app/src/androidTest/java/com/example/pleostest/AmbientLightParkedTest.kt | 정차 AVD 빨강 요청/독립 재조회/원래 색 복원 진단 |

차량 요청은 단일 백그라운드 worker에서 직렬 처리하고, UI는 메인 스레드에서 갱신합니다.
경고색이 같으면 SET을 반복하지 않으며 최신 경고색으로 합칩니다. 점멸마다 실제 조명 SET을 보내지 않습니다.

## 빌드와 검사

PowerShell, 프로젝트 루트:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

명시적인 차량 쓰기를 포함하는 정차 AVD 진단(실제 Console P·0 유지, 다른 기기는 연결 해제):

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.pleostest.AmbientLightParkedTest"
```

진단은 기존 AlleyWorld를 정차한 가상 위치로 구성해 빨강 상태를 얻으며 차량이나 Console을 움직이지 않습니다.
요청/재조회/복원 경로의 통과와 실제 색 적용 성공은 별개입니다. 결과는 아래 실제 로그를 확인하세요.

```powershell
adb -s emulator-5554 logcat -v time OnSensePleosF2:I "*:S"
```

주요 로그: CLEARANCE_STATE, RED_ENTRY, AMBIENT_CAPABILITY, AMBIENT_ROUTING,
AMBIENT_ORIGINAL, AMBIENT_REQUEST, AMBIENT_READBACK, AMBIENT_RESULT, AMBIENT_RESTORE_VERIFIED.

## 실제 검증 — 2026-11-13

- assembleDebug/testDebugUnitTest: 성공, 단위 테스트 61 passed / 0 failed.
- emulator-5554 (Connect_on_x86_64_emulator)에 com.example.pleostest 설치·실행 성공.
- P·0km/h 화면: 좌우 50:50 3D/실내 표시, 동일한 초록 → 왼쪽 주황/오른쪽 초록 표시 확인.
- Light capability: READ_WRITE, 하나의 공통 SeatArea:
  ROW_1_LEFT, ROW_1_RIGHT, ROW_2_CENTER, ROW_2_LEFT, ROW_2_RIGHT.
  이 AVD 결과로 좌우 독립 물리 조명 제어를 주장하지 않습니다.
- 원래 값: color=#00FFFFFF, brightness=70, switch=OFF. 스위치/밝기 변경 없음.
- 초록 요청 #FF4DDB83 → SET callback #FF4DDB83 → 독립 GET 3회 모두 #00FFFFFF.
  **차량 색 적용 미확인.**
- 종료 후 원래 색 #00FFFFFF 복원 요청 → GET #00FFFFFF 일치.
  원래 색 일치는 확인했지만 초록으로 바뀌었다 돌아온 것은 확인하지 못했습니다.
- 첫 빨강 진단은 실제 차량이 D로 바뀌어 정차 검사에서 차단됐고 테스트가 실패했습니다. 이 시도는 성공으로 기록하지 않았습니다.
- P·0km/h 유지 후 재실행: connectedDebugAndroidTest의 AmbientLightParkedTest 1 PASSED.
  기존 AlleyWorld의 가상 위치에서 leftMeters=0.20000000000000018, rightMeters=2.0을 얻어 왼쪽 빨강을 계산한 진단입니다.
  실제 차량은 정차한 상태이며 주행 중 연속 연동 검증은 아닙니다.
- 빨강 요청 #FFFF7272 → 독립 GET #00FF7272: **RGB 색 변경 재조회 일치**.
  AVD의 재조회 알파는 00이므로 RGB 24비트로 비교했고 원래 반환값도 그대로 기록했습니다.
- 빨강 진단 종료: 원래 #00FFFFFF 복원 요청 → GET #00FFFFFF 일치, 복원 기록 비움 확인.
  이번에는 빨강으로 변경된 재조회값과 흰색으로 복원된 재조회값을 모두 확인했습니다.
- 주행 중 연속 반영, 실제 차량 조명과 화면의 정확한 점멸 동기화, 실물 조명 관찰은 후속 검증입니다.
- 기존 시스템 주행 차단 설정·allowlist·DO 선언은 변경하지 않았습니다.

실제 로그 발췌(기기 epoch 초):

```text
1794526172 AMBIENT_ORIGINAL color=#00FFFFFF brightness=70 switch=OFF
1794526172 AMBIENT_REQUEST BEFORE=#00FFFFFF REQUEST=#FF4DDB83
1794526172 AMBIENT_SET_CALLBACK phase=APPLY value=#FF4DDB83
1794526173 AMBIENT_READBACK phase=APPLY requested=#FF4DDB83 actual=#00FFFFFF attempt=3 matched=false
1794526173 AMBIENT_RESULT outcome=적용 미확인
1794526199 AMBIENT_RESTORE_VERIFIED original=#00FFFFFF actual=#00FFFFFF

1794526613 PARKED_TEST_SOURCE AlleyWorld.clearance left=0.20000000000000018 right=2.0; synthetic fixture
1794526617 AMBIENT_ORIGINAL color=#00FFFFFF brightness=70 switch=OFF
1794526617 AMBIENT_REQUEST BEFORE=#00FFFFFF REQUEST=#FFFF7272
1794526618 AMBIENT_READBACK phase=APPLY requested=#FFFF7272 actual=#00FF7272 attempt=1 matched=true
1794526618 AMBIENT_RESULT outcome=색 변경 재조회 일치 requested=#FFFF7272 actual=#00FF7272
1794526618 AMBIENT_RESTORE_BEFORE current=#00FF7272 original=#00FFFFFF
1794526618 AMBIENT_READBACK phase=RESTORE requested=#00FFFFFF actual=#00FFFFFF attempt=1 matched=true
1794526618 AMBIENT_RESTORE_VERIFIED original=#00FFFFFF actual=#00FFFFFF
```

## SDK 근거

- 공식 Light API: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-ambient-light-brightness-color
- 내려받은 Vehicle-2.3.2-sources.jar의 Light.kt, SeatArea.kt를 함께 확인했습니다.
