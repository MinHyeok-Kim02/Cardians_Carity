# On-sense 골목 보기 검증

> 이 문서는 별도 DO 주행 정보 패널 도입 전 3D 골목 Activity의 검증 기록입니다. 현재 주행 중 속도·조향각 표시 결과는 [주행 패널 검증 보고서](PLEOS_DRIVING_PANEL_VERIFICATION.md)를 참고하세요. 3D 골목은 정차 전용으로 유지합니다.

검증 대상: Pleos_Connect_v2.1.0 / emulator-5554
실행일: 2026-11-12 (에뮬레이터 로그 시각 기준)

## 구현

- 기본 화면은 차량 신호 모니터. '골목 보기 켜기/끄기' 스위치로 기존 3D 골목 전환.
- 선택 상태를 저장하고 화면 재생성 시 복원. 최종 APK 재설치·재실행 후에도 ON 상태 복원 확인.
- 기존 수동 테스트 버튼은 정지/신호 대기 시 유지. 신호에 따라 이동 중이거나 주행 제한 시 비활성화.
- 속도: AAOS PERF_VEHICLE_SPEED, m/s → km/h. 조향: Pleos Vehicle 2.3.2 SteeringWheelAngleListener.
- 골목 보기 ON + 앱 foreground + 플랫폼 requiresDO=false + 최신 속도/조향 신호가 있을 때만 가상 위치 적분.
- 가상 조향비 16:1, 축거 2.7m. 실차 보정 모델이 아님.
- 5초 이상 신호 지연, 수신 오류, 토글 OFF, onPause 또는 주행 규제 시 정지. 재개 시 과거 시간을 몰아서 이동하지 않음.
- '좌우 간격은 가상 벽 기준입니다 · 실차 주변 장애물 실측값이 아닙니다' 상시 표시.
- 가상 벽을 넘어가지 않도록 충돌 시 이동 중지.
- CarUxRestrictionsManager를 읽고 기록함. 제한 설정 변경, allowlist 변경, DO 메타데이터 추가, 앱 카테고리 위장은 하지 않음.

## 빌드와 테스트

Windows 루트에서 실행:
`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`

BUILD SUCCESSFUL. 기존 AlleySimulationTest 4개와 신규 VehicleMotionControllerTest 7개 통과.
설치: adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk → Success.
수동 테스트 버튼 유지 변경을 포함한 최종 소스로 assembleDebug/testDebugUnitTest 재실행 성공, 최종 APK 재설치·실행 성공.

## 실제 기기 결과

| 기어 / 속도 | 플랫폼 DO / 플래그 | 실제 앱 화면 | 토글 실제 조작 |
| --- | --- | --- | --- |
| P / 0 km/h | false / 0x0, Driving State 0 | 오른쪽 앱 영역 기본 화면 및 3D 화면 확인 | ON → OFF → ON 성공, OFF에서 기본 화면 복귀 |
| D / 5 km/h 목표 | true / 0x50, Driving State 2 | 앱 UI 트리 5.0 수신, 실제 캡처 시 계기 7km/h에서 시스템 차단. 후속 캡처 3km/h도 차단 | UI 트리 enabled=false. 시스템 차단막 때문에 스위치 자체가 보이지 않아 직접 누를 수 없음. 고정 5km/h 유지 캡처는 미완료 |
| D / 10 km/h 목표 | 주행 구간 DO true / 0x50 | 00:37:37.108에 10.0 콜백 확인. 화면 캡처는 14km/h에서 차단. 고정 10km/h 유지 캡처는 미완료 | 스위치 enabled=false 관찰. 고정 10km/h 직접 조작은 미완료 |
| D / 15 km/h 목표 | 주행 구간 DO true / 0x50 | 00:37:55.905에 15.000001 콜백 확인. 고정 15km/h 유지 캡처는 미완료 | 고정 15km/h 직접 조작은 미완료 |

속도가 계속 변하는 상태였으므로 콜백 수신 시점, UI 트리 시점, 스크린샷 시점의 속도를 같은 값으로 취급하지 않음. 캡처에서 확인한 계기 값은 7, 3, 14, 11, 9km/h이며 모두 시스템의 ‘주행 중에는 사용할 수 없습니다’ 표시로 가려짐.

저속 통과 구간 실제 로그:
```text
11-12 00:36:03.404 SPEED_RECEIVED speedMps=1.388889 speedKmh=5.0
11-12 00:36:40.000 VIRTUAL_MOTION status=RESTRICTED x=2.000 y=0.000 headingDeg=0.000
11-12 00:37:37.108 SPEED_RECEIVED speedMps=2.777778 speedKmh=10.0
11-12 00:37:55.905 SPEED_RECEIVED speedMps=4.166667 speedKmh=15.000001
```

P/0 실제 로그:
```text
11-12 00:32:08.481 ALLEY_VIEW enabled=false
11-12 00:32:08.517 UX_RESTRICTIONS requiresDO=false flags=0x0
11-12 00:32:10.266 SPEED_RECEIVED speedMps=0.0 speedKmh=0.0
11-12 00:32:52.771 ALLEY_VIEW enabled=true
11-12 00:32:52.905 VIRTUAL_MOTION status=STOPPED x=2.000 y=0.000 headingDeg=0.000
```

속도 0이므로 차량이 이동하지 않는 것이 정상. 움직임 계산은 단위 테스트로 확인했으며 실제 D/저속 화면의 동작은 별도 기기 검증 대상.

## 공식 주행 중 표시 경로 조사

- Pleos 주행 규제 정책: DO 미선언 앱은 Parking 사용 가능, Drive 사용 불가.
  https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- 공식 연동: CarUxRestrictionsManager 상태와 기능별 ActiveRestrictions를 반영.
  https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- Music/Audio, Maps/Navigation, Mobility Services 일부 기능은 주행 관련 정보와 최소 조작에 한해 제한적으로 허용 가능. 현재 앱의 가상 3D 골목 시뮬레이션에 대한 자동 예외는 공개 문서에서 확인하지 못함.
  https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- Maps/Navigation은 실제 위치 기반 경로 안내 목적이어야 하고 카테고리와 기능 불일치는 반려 대상. 내비 카테고리 선언만으로 이 앱을 표시하는 방법은 적용하지 않음.
  https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide

본 앱에 적용 가능한 DO UI/카테고리 및 3D 표시 승인 여부는 Pleos 측 확인이 필요함. 공개 문서만으로 본 앱의 주행 중 표시 승인을 확정할 수 없음.

## 변경 파일

- app/src/main/java/com/example/myapplication/MainActivity.java
- app/src/main/java/com/example/myapplication/PleosSteeringReceiver.java
- app/src/main/java/com/example/myapplication/AlleySimulation.java
- app/src/main/java/com/example/myapplication/VehicleMotionController.java (신규)
- app/src/main/java/com/example/myapplication/VehicleDrivingMonitor.java (신규)
- app/src/test/java/com/example/myapplication/VehicleMotionControllerTest.java (신규)

## 미검증 및 제약

Windows 브라우저 자동화가 현재 URL을 충분히 확인하지 못해 중단됨. Virtual Console 상태 변경은 사용자 협조를 요청했고, 각 속도는 실제 기기 상태 확인 후에만 결과표를 갱신함.
속도별 제한을 추정하거나 규제를 OFF로 바꾸어 성공으로 기록하지 않음.

최종 설치 후 9km/h에서 시스템 차단막과 토글 enabled=false를 확인. 가상 주행은 정지되어 있음.
사용자가 10km/h 고정 후 준비 알림을 선택했으나 현재 실제 계기판/앱 수신값은 9.0km/h여서, Console 값을 조정해 계기판 10km/h로 맞춰 달라고 요청한 상태.
고정 5/10/15km/h 각각의 화면·터치 검증 및 최종 P/0 복귀 확인은 다음 기기 검증 단계로 남음.
시스템 차단막에 가려진 스위치에 직접 입력을 주입하지 않았음. 시스템이 실제 화면을 가린 상태를 '토글 직접 조작 불가'와 '수신값 확인'으로 구분함.
가상 차량이 움직이는 계산은 테스트로 확인했으나, D/저속에서는 제한이 활성화되어 실제 화면에서 신호 연동 이동이 보이는 성공 사례는 확인하지 못함.
