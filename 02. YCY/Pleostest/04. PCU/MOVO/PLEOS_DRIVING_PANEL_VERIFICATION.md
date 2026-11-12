# On-sense 주행용 DO 패널 검증

## 구현 범위

- DrivingActivity: 런처이자 주행 중 읽기 전용 차량 정보 화면. 속도·핸들 각도만 큰 수치로 표시하며 닫기 버튼을 제공합니다.
- MainActivity: 비공개 정차 화면. 기존 3D 골목·토글·수동 테스트·권한 요청·경고 오디오를 유지합니다.
- distractionOptimized=true는 DrivingActivity에만 선언합니다. MainActivity에는 선언하지 않습니다.
- VehicleDrivingMonitor의 실제 CarUxRestrictions를 기준으로 화면을 선택합니다. 속도만으로 정차/주행을 추정하지 않습니다.
- DrivingActivity에서 requiresDO=false가 확인되면 MainActivity로 전환합니다. MainActivity에서 requiresDO=true가 확인되면 DrivingActivity로 전환합니다.
- 주행 패널에는 3D, 영상, 장식 애니메이션, 수동 조작, 설정, 권한 요청, 경고 오디오가 없습니다.
- 신호는 기존 VehicleSpeedReceiver/PleosSteeringReceiver를 재사용합니다. 5초 이상 지연 시 '--' 표시, onPause 시 구독 해제.
- 주행 제한 설정·allowlist·기어·차속을 앱이 변경하지 않습니다. 앱 카테고리를 위장하지 않습니다.

## 변경 파일 및 함수

- app/src/main/java/com/example/myapplication/DrivingActivity.java (신규)
  - buildScreen / addHeader / addReading: 주행 정보 화면 생성
  - onRestrictions / openParkedScreen: 실제 UX 제한에 따른 정차 화면 분기
  - startSignalReceivers / refreshSignals: 기존 수신기 재사용과 수치 표시
  - onPause: 갱신 예약 취소와 수신기 해제
- app/src/main/java/com/example/myapplication/MainActivity.java
  - onRestrictions: 주행용 패널 전환
  - onResume / updateMotionFrame: 전환 중 중복 구독·가상 이동 방지
- app/src/main/AndroidManifest.xml
  - DrivingActivity를 DO 런처로 추가
  - MainActivity는 외부에서 직접 실행할 수 없는 정차용 Activity로 유지

## 빌드 및 설치

2026-11-12, Windows 프로젝트 루트:
`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`

BUILD SUCCESSFUL in 10s. 단위 테스트 통과. DrivingActivity 정적 검사 오류 없음.
Android Studio 배포 도구로 Pleos_Connect_v2.1.0 / emulator-5554에 com.example.myapplication 설치·실행 성공. ADB 설치 명령은 응답 지연으로 완료되지 않아 IDE 배포 결과를 사용했습니다.

## 실제 기기 검증

| 조건 | 실제 결과 |
| --- | --- |
| D · 13km/h | 오른쪽 앱 영역에서 DrivingActivity 표시 성공. 시스템 차단막 없음. UI에 속도 13.0km/h, 핸들 각도 -100.0° 표시 확인. |
| 주행 제한 | known=true, requiresDO=true, flags=0x50 유지. 제한을 해제하지 않은 상태에서 DO 패널 표시. |
| P · 0km/h 복귀 | Console을 P·0으로 변경하는 사용자 조작 대기. 이번 DO 도입 후 자동 복귀와 토글 재동작은 미검증. |
| D · 5 / 10 / 15km/h 각각 고정 | 이번 DO 패널에서 각각 고정한 화면·터치 검증은 미완료. D·13km/h 결과로 대체해 성공 처리하지 않음. |
| 주행 중 닫기 버튼 | D·13km/h에서 실제 터치 후 포커스가 시스템 DrivingViewActivity로 이동하는 것 확인. IDE로 앱 재실행 후 DrivingActivity와 13.0km/h·-100.0° 표시 재확인. |

실제 OnSensePleos Logcat 발췌(기기 epoch 초):

```text
1794476548: DRIVING_PANEL_RESUMED
1794476548: DRIVING_PANEL_POLICY known=false requiresDO=true flags=0xffffffff
1794476548: UX_RESTRICTIONS requiresDO=true flags=0x50
1794476548: DRIVING_PANEL_POLICY known=true requiresDO=true flags=0x50
1794476549: SPEED_RECEIVED speedMps=3.6111112 speedKmh=13.0
1794476575: STEERING_RECEIVED angleDeg=-100.0
```

known=false는 연결 전의 보수적 대기 상태입니다. 이후 실제 제한 콜백과 화면을 함께 확인했습니다. 수신 로그와 패널의 표시값을 대조했으며, 3D 골목의 주행 중 사용 성공을 뜻하지 않습니다.

## 공식 근거와 배포 범위

- AAOS는 주의 분산 지침을 충족하는 Activity에 DO 메타데이터를 선언하는 방법을 안내합니다.
  https://source.android.com/docs/automotive/driver_distraction/guidelines
- Pleos는 DO 선언 앱도 CarUxRestrictions와 ActiveRestrictions를 확인하고 기능별 제한을 적용하도록 안내합니다.
  https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide

이 문서는 개발용 에뮬레이터 구현·검증 기록입니다. Pleos App Market 또는 양산 차량 배포 승인을 의미하지 않습니다.
3D 시뮬레이션의 주행 중 표시를 허용한 것이 아닙니다. 이전 3D 앱 차단 검증은 PLEOS_ALLEY_VERIFICATION.md에 남깁니다.
