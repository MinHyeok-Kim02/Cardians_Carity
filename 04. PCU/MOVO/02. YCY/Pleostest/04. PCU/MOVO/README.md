# On-sense

Pleos Connect 오른쪽 앱 영역에서 주행 중에는 읽기 전용 속도·핸들 각도 패널을, 정차 시에는 기존 3D 가상 골목을 제공하는 Java Android 앱입니다.

**좌우 간격은 가상 벽 기준이며 실제 차량 주변 장애물의 실측 거리가 아닙니다.** 차량 신호 수신 성공, 가상 주행 계산 검증, 실제 화면 표시 성공은 서로 구분해서 기록합니다.

## 화면과 동작

- 런처: `DrivingActivity`. 플랫폼 UX 제한을 확인하고 화면을 선택합니다.
- 주행 제한 활성: 공식 DO 선언을 적용한 읽기 전용 속도·핸들 각도 패널. 설정, 권한 요청, 수동 조작, 영상·3D·오디오는 제공하지 않습니다.
- 정차로 제한 해제: `MainActivity`로 자동 전환합니다. 정차 화면에서 D로 전환되면 주행 패널로 이동합니다.
- 닫기/뒤로: 앱 Task를 백그라운드로 이동하고 구독을 해제합니다. 차단된 3D 화면을 뒤로 노출하지 않습니다.

- 정차 화면의 골목 토글 OFF: 기본 차량 신호 모니터 화면.
- 정차 화면의 골목 토글 ON: 기존 3D 골목과 가상 좌우 간격 표시. 최신 속도·조향 신호로 가상 차량의 위치·방향을 갱신합니다.
- 정지하거나 신호를 기다릴 때 기존 수동 테스트 버튼을 사용할 수 있습니다.
- 토글 OFF, 앱 화면 이탈, 신호 오류·5초 이상 지연, 플랫폼 주행 제한 시 가상 이동을 정지합니다.
- 토글 상태를 저장하고 재실행 시 복원합니다. 중단한 시간을 몰아서 이동하지 않습니다.
- 가상 조향비 16:1, 축거 2.7m를 사용하는 시연용 모델입니다. 실차 보정값이 아닙니다.
- 가상 차체가 가상 벽을 넘어가기 전에 이동을 멈춥니다.

## 코드 구조

함수는 기능 단위로 나누고, 상태 계산·신호 수신·화면 그리기를 별도 클래스로 관리합니다.

| 파일 | 담당 책임 / 주요 함수 |
| --- | --- |
| [DrivingActivity.java](app/src/main/java/com/example/myapplication/DrivingActivity.java) | DO 주행 정보 화면·런처. `buildScreen`, `addHeader`, `addReading`, `onRestrictions`, `startSignalReceivers`, `refreshSignals`, `openParkedScreen`, `onPause` |
| [MainActivity.java](app/src/main/java/com/example/myapplication/MainActivity.java) | 정차 화면과 생명주기 연결. `buildScreen`, `addHeader`, `addHome`, `createControls`, `setAlleyVisible`, `startVehicleSpeed`, `onRestrictions`, `updateMotionFrame` |
| [PleosSteeringReceiver.java](app/src/main/java/com/example/myapplication/PleosSteeringReceiver.java) | Pleos 조향각 구독과 해제. `start` / `stop`, 실제 콜백을 UI 스레드의 Observer에 전달 |
| [VehicleSpeedReceiver.java](app/src/main/java/com/example/myapplication/VehicleSpeedReceiver.java) | AAOS 차속 구독, 단위 변환, 오류·수신 지연 처리. `start`, `subscribe`, `onStale`, `stop` |
| [VehicleDrivingMonitor.java](app/src/main/java/com/example/myapplication/VehicleDrivingMonitor.java) | 플랫폼 UX 제한 읽기. `start`, `publish`, `stop` |
| [VehicleMotionController.java](app/src/main/java/com/example/myapplication/VehicleMotionController.java) | 토글·화면 상태·UX 제한·신호 신선도에 따른 이동 허용. `setEnabled`, `setForeground`, `setRestrictions`, `speed`, `steering`, `tick` |
| [AlleySimulation.java](app/src/main/java/com/example/myapplication/AlleySimulation.java) | 가상 위치·방향·벽·거리의 기준. `drive`, `forward`, `steer`, `corners`, `gap`, `pollEntries` |
| [AlleyView.java](app/src/main/java/com/example/myapplication/AlleyView.java) / [AlleyScene.java](app/src/main/java/com/example/myapplication/AlleyScene.java) | GL 화면 관리와 UI → GL 상태 스냅샷 전달. `publish`, `entered`, `resetBlink` |
| [AlleyRenderer.java](app/src/main/java/com/example/myapplication/AlleyRenderer.java) | 3D 그리기. `updateCamera`, `drawGround`, `drawWalls`, `drawNeighborhood`, `drawCarBody`, `drawGuidelines` |
| [WarningAudio.java](app/src/main/java/com/example/myapplication/WarningAudio.java) | 근접 경고 큐·비프·Android TTS. `enqueue`, `cancel`, `pause`, `release` |

`MainActivity`에는 UI 생성과 연결 함수가 남아 있습니다. 물리 계산과 차량 수신 처리를 Activity나 렌더러 안에 중복 구현하지 않습니다. 상세 기존 함수 안내는 [app/README.md](app/README.md)를 참고하세요.

## 데이터 흐름

```text
Pleos SteeringWheelAngleListener → PleosSteeringReceiver → MainActivity
AAOS PERF_VEHICLE_SPEED          → VehicleSpeedReceiver  → MainActivity
AAOS CarUxRestrictionsManager    → VehicleDrivingMonitor → MainActivity
                                                          ↓
                                               VehicleMotionController.tick
                                                          ↓
                                                AlleySimulation.drive
                                                          ↓
                              근접 이벤트 → 점멸 / WarningAudio
                              화면 갱신   → AlleyView.publish → AlleyScene → AlleyRenderer
```

모델은 UI 스레드에서 갱신하고 GL 스레드는 스냅샷만 읽습니다. 속도는 m/s를 km/h로 표시하고, 위치 적분 시 다시 m/s로 사용합니다. 핸들 각도와 가상 차체의 진행 방향은 서로 다른 값입니다.

## SDK와 권한

- 공식 Maven 저장소와 `ai.pleos.playground:Vehicle:2.3.2`를 사용합니다.
- 조향: `Vehicle.initialize()` → `getSteeringWheel()` → `registerSteeringWheelAngle()`.
- 현재 차속: `CarPropertyManager`의 `PERF_VEHICLE_SPEED`. 크루즈 목표 속도를 현재 속도로 사용하지 않습니다.
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml): `pleos.car.permission.CAR_DRIVE`, `android.car.permission.CAR_SPEED`.
- 속도 읽기는 앱의 권한 버튼에서 요청합니다. Pleos 시스템 대화상자는 ‘위치 정보’ 문구를 표시했지만 앱이 요청한 권한 목록에는 GPS 위치 권한이 없습니다.
- VIN·웹 Vehicle Data API·액세스 토큰은 사용하지 않습니다.
- 기존 Java 구조와 Gradle·AGP·JDK 설정을 유지합니다.

## 빌드와 설치

Windows PowerShell, 프로젝트 루트:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

연결된 기기를 확인하고 **Pleos AVD의 serial을 확인한 후** 해당 기기에만 설치합니다. 이번 검증 serial은 `emulator-5554`였습니다.

```powershell
adb devices -l
adb -s emulator-5554 emu avd name
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell am start -n com.example.myapplication/.DrivingActivity
adb -s emulator-5554 logcat -v time OnSensePleos:I '*:S'
```

주요 로그: `STEERING_RECEIVED`, `SPEED_RECEIVED`, `UX_RESTRICTIONS`, `ALLEY_VIEW`, `VIRTUAL_MOTION`. 초기화·등록 함수 반환만으로 데이터 수신 성공으로 판단하지 않습니다.

## 검증 상태 — 2026-11-12

주행용 DO 패널의 빌드·단위 테스트와 Pleos 설치·실행에 성공했습니다. **D·13km/h에서 속도 13.0km/h·핸들 각도 -100.0°가 차단 없이 표시**됐으며, 플랫폼 제한은 `requiresDO=true / flags=0x50`로 유지됐습니다. DO 도입 후 P·0 정차 화면 자동 복귀는 검증 대기입니다. 실제 로그와 미검증 항목은 [주행 패널 검증 보고서](PLEOS_DRIVING_PANEL_VERIFICATION.md)에 기록합니다.

아래는 기존 3D 골목 화면의 검증 기록입니다.

- 최종 `assembleDebug` / `testDebugUnitTest` 성공, Pleos Connect v2.1.0 설치·실행 성공.
- [AlleySimulationTest](app/src/test/java/com/example/myapplication/AlleySimulationTest.java): 거리·회전 차체·근접 이벤트 등 4개.
- [VehicleMotionControllerTest](app/src/test/java/com/example/myapplication/VehicleMotionControllerTest.java): 이동 거리, 좌우 조향, 후진, 제한·신호 단절·토글 중지, 가상 벽 충돌 등 7개.
- P·0km/h: 오른쪽 앱 영역에서 골목 보기 ON/OFF, 기본 화면 복귀, 재실행 후 토글 상태 복원 확인.
- 5·10·약 15km/h 콜백 수신 확인. 실제 캡처 속도와 콜백 시점의 속도가 달라, 각 목표 속도를 고정한 화면·터치 검증은 미완료.
- 실제 저속 주행 캡처에서는 시스템 차단막이 앱을 가렸고, `requiresDO=true`, `flags=0x50`, 토글 비활성화와 가상 이동 정지를 확인.
- 제한 없이 실제 화면에서 신호 연동 가상 주행이 보이는 성공 사례는 아직 확인하지 못했습니다.
- 소리 청취·한국어 TTS 엔진 정상 동작은 별도 확인이 필요합니다.

실제 속도·로그·미검증 범위와 공식 문서 조사 결과는 [PLEOS_ALLEY_VERIFICATION.md](PLEOS_ALLEY_VERIFICATION.md)를 기준으로 확인하세요. 단위 테스트 통과를 주행 중 표시 성공으로 해석하지 않습니다.

## 주행 제한

플랫폼 제한 설정과 allowlist를 변경하거나 앱 카테고리를 위장하지 않습니다. `distractionOptimized=true`는 속도·핸들 각도만 표시하는 별도 `DrivingActivity`에 적용합니다. 3D 골목이 있는 `MainActivity`는 DO를 선언하지 않은 정차용 비공개 Activity입니다.

공식 근거:
- [AAOS Distraction Optimized Activity 선언](https://source.android.com/docs/automotive/driver_distraction/guidelines)
- [Pleos CarUxRestrictions 연동](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)

에뮬레이터에서의 DO 화면 실행 검증은 양산 차량·Pleos App Market 배포 심사를 대신하지 않습니다. 3D 가상 골목 화면 자체에 대한 주행 중 표시 승인은 확인하지 않았습니다.

기존 수동 시연과 오디오 함수 설명은 [app/README.md](app/README.md)에 유지합니다. `README.txt`는 이전 수동 MVP 구조를 기록한 문서이며 최신 기능은 이 README를 우선합니다.
