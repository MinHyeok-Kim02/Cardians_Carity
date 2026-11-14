# On-sense 버전 1 — 현재 기능·함수 설명·실행 방법

이 문서는 로드뷰를 제거한 현재 MOVO 프로젝트의 버전 1 기준입니다.
실행 진입점은 `com.example.pleostest.MainActivity`이며 Android 앱 버전 설정과 Gradle·AGP·JDK는 유지했습니다.
기능 추가보다 기존 동작을 보존하면서 화면 생성·입력·갱신·시연·그리기를 담당 함수로 분리한 정리입니다.

## 1. 현재 진행 사항

| 기능 | 버전 1 상태 |
| --- | --- |
| 차량 정보 | AAOS 차속·기어, Pleos 조향각·브레이크 수신. 원본 값은 진단에서 확인 |
| 3D 골목 | 기존 가상 차량 운동·주차 차량·가상 벽의 거리 계산 유지 |
| 화면 구성 | 왼쪽 3D 골목 / 오른쪽 실내 그림 50:50, 상세 정보 접기/펼치기 |
| 키보드 | Console → 에뮬레이터 → AAOS/Pleos 수신 경로 유지 |
| 자동 시연 | 실제 Console 값을 변경하지 않는 10초 가상 입력 시나리오 |
| 기능 2 | 좌우 경고·빨강 진입 3회 점멸·실내 조명 연출 |
| 조명 연출 | 좌우 각각 16구간, 중앙→도어 순차 점등 / 도어→중앙 역순 소등 |
| 거리별 표현 | 간격이 줄면 초록→노랑→주황→빨강 중간 색, 켜지는 구간 증가. 1.6m 이상 전부 소등 |
| Alley Pass | 정차·브레이크·거리 조건 확인 후 사용자 확인을 거치는 가상 매크로 |
| 위치 | 기존 Pleos 위치 수신 진단 유지. 외부 지도 전송 없음 |
| 실제 Light API | 지원 영역·변경 전 값·SET·GET 재조회·종료 복원 경로 유지 |
| 로드뷰 | 버튼·WebView·사진 인식·카카오 키 주입·모델 다운로드 제거 |

실제 속도·조향은 차량 데이터이고, 장애물까지의 거리는 가상 골목에서 계산한 값입니다.
실내 그림의 조명은 시각적 연출입니다. 기존 정차 AVD 시험에서 Light SET 콜백은 왔지만 독립 재조회값이 흰색이어서 실제 색 적용은 **적용 미확인**입니다. 원래 흰색 재조회 일치는 확인했습니다.
주행 중 실제 조명의 연속 제어·구간별 하드웨어 점등·점멸 동기화·실차 검증은 완료한 기능이 아닙니다.

## 2. 실행 방법 — Windows PowerShell

프로젝트 루트: `C:\Users\pcu17\AndroidStudioProjects\MOVO`

Android Studio에서 현재 Pleos AVD를 선택하고 app을 Run합니다.
Virtual Console을 PC 브라우저에서 열 때는 다음 명령을 사용합니다.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb -s emulator-5554 forward tcp:8088 tcp:8088
Start-Process "http://127.0.0.1:8088/"
```

- `emulator-5554`는 현재 시험 AVD의 시리얼입니다. `devices` 출력이 다르면 해당 시리얼로 바꾸세요.
- 포워딩은 PC의 8088 포트를 AVD의 Virtual Console 8088 포트에 연결합니다. Console 자체를 설치하거나 실행하는 명령은 아닙니다.
- 앱 내부 키보드 브리지는 AVD의 `http://127.0.0.1:8088`에 직접 접속합니다. PC 포워딩은 브라우저 확인용입니다.
- 연결 시험은 Console의 **P·0km/h**에서 시작합니다. 주행 제한으로 버튼이 비활성화되면 정차/P 상태를 먼저 확인하세요.
- 키보드 제어 중 다른 브라우저/Console에서 같은 값을 동시에 조작하면 재조회 불일치로 연결이 중단될 수 있습니다.

빌드·단위 테스트:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

선택한 AVD의 로그 확인:

```powershell
& $adb -s emulator-5554 logcat -v time CarTalkSpeed:I CarTalkSteering:D CarTalkKeyboard:I CarTalkConsole:I CarTalkAlley:D OnSensePleosF2:I CarTalkMacro:I '*:S'
```

## 3. 화면 조작

1. **3D 자동 시연**: 기존 10초 시나리오를 실행합니다. 실제 차속/기어를 바꾸지 않습니다.
2. **SDK 연동 시작**: 최신 SDK 차속·조향으로 가상 차량을 움직입니다. 입력이 오래되면 이동을 보류합니다.
3. **Console 키보드 연결**: debug Pleos AVD의 P·0km/h에서 연결한 뒤 D/R을 선택합니다. 앱 화면에 포커스를 두세요.
4. ↑/W 길게: 가속, ↓/S: 브레이크, ←/A·→/D: 조향, Space: 정지, R: 정차/P에서 재준비.
5. D는 기어 선택 키가 아니라 오른쪽 조향 키입니다. 기어는 화면의 D/R/N/P 버튼을 사용합니다.
6. **3D 초기화**: 가상 위치·시연·매크로를 초기화하고 Console 제어를 중지합니다.
7. **패스 매크로(SIM)**: 조건 충족 후 확인창의 **가상 실행**을 누릅니다. 미러·창문·비상등은 실제 제어가 아닙니다.
8. **차량 무드라이트 연동**: 실제 수신이 P·0km/h인 debug AVD에서 별도로 켭니다. 종료/화면 이탈 시 복원을 요청합니다.
9. **진단 정보**: 위치·원본 조향·입력 상태·조명 API 재조회 상세를 확인합니다.

초기화는 기존 동작처럼 선택된 입력 모드 플래그를 별도로 전환하지 않습니다. 주행을 재개할 때 원하는 SDK/키보드/자동 시연 버튼을 명시적으로 선택하세요.

## 4. 전체 데이터 흐름

```text
MainActivity의 수신기
  → AlleyPassSimulationView.updateSpeed/updateSteering/updateBrake/updateGear
  → motion(now): 입력 출처 및 신선도 확인
  → tickFrame(now) → advanceWorld() → AlleyWorld.advance()
  → AlleyWorld.clearance()
  → publishFeature2()
       ├─ SideClearanceWarning: 등급·점멸·색·구간 진행 목표
       ├─ ClearanceGuideView: 3D 기준선
       ├─ InteriorDemoView → AmbientStripAnimation: 실내 구간 연출
       └─ AmbientLightBridge: 실제 API 요청·독립 재조회·복원
  → render(): 차량 정보·안내·매크로·버튼 갱신
```

운동 계산은 AlleyWorld 한 곳에서 수행합니다. 실내 그림은 거리나 차량 운동을 다시 계산하지 않습니다.
차속 입력 단위는 m/s, 화면 표시는 km/h, 가상 위치/간격은 m, world.heading은 라디안입니다.
조향은 SDK 원본 각도와 기존 매핑의 정규화 값(-1~1)을 구분합니다.

## 5. 함수별 유지보수 안내

모든 파일 경로는 `app/src/main/java/com/example/pleostest/` 기준입니다.
한 책임을 가진 기존 SDK 수신기/거리 엔진은 유지하고, 길었던 실행/화면 함수와 내부 지역 함수를 담당 함수로 분리했습니다.

### MainActivity.kt — 진입점·구독 수명주기

| 함수 | 담당 |
| --- | --- |
| onCreate → createWindowContent | Pleos 앱 영역 안의 루트 화면 생성, 시스템 바/컷아웃 인셋 |
| attachSimulation | 3D/실내 화면 부착과 위치 버튼 연결 |
| onStart → startVehicleSources | 활성 화면·UX 제한·기어·브레이크·조향·위치 수신 시작 |
| startSpeedSourceIfPermitted | Automotive 여부 및 기존 차속 권한 확인 |
| locationAction | 위치 수신 중지/재시작, 지원·권한·정차 조건 분기 |
| showLocationPermissionPrompt | 기존 권한 설명/앱 설정 안내/최초 요청 |
| dispatchKeyEvent / onWindowFocusChanged | 키 입력과 포커스 변경을 운전 화면에 전달 |
| onStop → stopVehicleSources | 구독·시연·음성·Console 정리 |
| onSaveInstanceState / onDestroy | 가상 상태 저장 / 화면 소유 리소스 해제 |

### AlleyPassSimulationView.kt — 화면 조립과 시연 제어

| 함수 | 담당 |
| --- | --- |
| configureRoot / restoreState / buildLayout | 화면 속성·복원·전체 조립. init은 이 함수들을 호출 |
| createLocationBar / createDiagnosticsPanel | 위치 진단 및 상세 상태 패널 |
| createScenePanels / createSceneHeader | 50:50 배치와 3D 안내·진단 토글 |
| toggleDiagnostics | 진단 패널 및 위치 표시 토글 |
| populateCabin | 실내 그림, 조명 결과, 실제 조명 스위치 구성 |
| populateAlleyHud / populateMacroBanner | 좌우 간격 HUD와 매크로 배너 배치 |
| createControls / createGearControls | 하단 조작·기어 버튼 생성 |
| createMacroConfirmation / createActions | 실행 확인창과 SDK/시연/키보드/초기화/매크로 버튼 |
| toggleSdkSimulation / resetScene | SDK 재개/일시정지, 가상 장면 초기화 |
| requestMacroConfirmation | 매크로 조건 확인 및 사용자 확인창 열기 |
| prepareScene / prepareKeyboard | 가상/SDK 입력 준비, Console 키보드 연결 준비 |
| motion | 가상 시나리오 또는 실제 신선한 수신값 선택. R 기어에서는 속도 부호 반전 |
| tickFrame / frameDelta | 50ms 갱신 및 프레임 시간 계산 |
| advanceWorld / finishSceneIfNeeded | 기존 가상 운동 호출, 시연 종료·접촉 정지 |
| logMotion | 기존 1초 주기 위치/입력 로그 |
| handleDrivingKey / drivingWindowFocusChanged | 기존 키 매핑·누름/뗌·포커스 상실 시 제어 정리 |
| freshGear / parkConfirmed | 수신 신선도 및 실제 정차/P 확인 |
| updateSpeed / updateSteering / updateBrake / updateGear | SDK 콜백 값과 수신 시각 저장 |
| updateRestrictions / setActive / release | 주행 제한 반영, 화면 수명주기, API/Console 정리 |
| setLocationAction / showLocation | 위치 진단 UI 연결 |
| stoppedForMacro / executeMacro | 정차 검증 후 가상 매크로 시작 |
| scheduleMacroStep / cancelMacro | 토큰·정차 조건을 매 단계 확인하고 예약 취소 |
| displayedClearance / publishFeature2 | 기존 가상 간격을 경고·실내·실제 Light API에 전달 |
| showAmbientStatus / endAmbientDemo | 독립 API 재조회 결과 표시, 종료/복원 요청 |
| save | world 좌표·방향·접촉·경과 시간과 가상 시연 상태 저장 |
| dp / label / panel / configure / buttonParams / TextView.show | 공통 UI 생성·중복 텍스트 갱신 방지 |

화면 갱신 함수:

| 함수 | 담당 |
| --- | --- |
| render | 아래 함수의 호출 순서 조정. 직접 거리 계산/SDK 쓰기 구현을 추가하지 않음 |
| renderVehicleInfo | 실제 SDK 값과 시연 값을 구분해 차량 정보 표시 |
| updateMacroDetection | 브레이크·거리·정차 조건과 확인 유효성 갱신 |
| renderInputDiagnostics / renderClearanceLabels | 입력 진단과 좌우 가상 간격 표시 |
| guidanceMessage / renderGuidance | 상태별 문구 선택과 근접 경고 표시 |
| renderMacroPresentation | 매크로 카드/일반 HUD 전환 |
| renderControls | 제한·신선도·정차·매크로에 따른 버튼 활성화 |
| announceGuidance | 기존 음성 안내 조건 및 안내 키 유지 |

### 경고·실내 조명

| 파일·함수 | 담당 |
| --- | --- |
| SideClearanceWarning.update / reset | 좌우 독립 경고 상태와 진입 시점 관리 |
| level | 기존 등급: 0.3m 이하 빨강, 0.8m 이하 주황, 나머지 초록, 미확인 회색 |
| ambientColor | 거리별 연속 RGB 중간 색 계산 |
| route | 실제 지원 영역이 합쳐졌을 때 기존 위험 등급 라우팅 |
| InteriorDemoView.publish | 같은 warning Frame 수신 |
| onDraw | 배경→실내→조명→핸들→Pleos 화면→설명 순서로 담당 함수 호출 |
| updateAnimation / scheduleAnimationFrame | 조명 진행 계산과 필요한 다음 프레임만 요청 |
| drawWindshield / drawCabin / drawSteeringWheel | 기존 차량 실내 그림 |
| drawAmbientStrips / drawAmbientStrip | 좌우 조명 경로와 구간별 빛 번짐·얇은 중심선 |
| drawPleosDisplay / drawCaptions | 같은 Frame의 기준선·점멸과 설명 표시 |
| fillColor / drawRect / drawPolygon / drawText | Canvas 공통 그리기 도구 |
| ClearanceGuideView.publish / onDraw | 기존 3D 좌우 기준선 표시 |
| AmbientStripAnimation.resume | 화면 복귀 시 애니메이션 시계 재설정 |
| advance | 좌우 목표까지 독립 진행. 전체 길이 진행 시간 1800ms, 프레임 최대 64ms |
| needsFrame | 목표와의 차이가 남은 경우에만 재그리기 요청 |
| sectionOpacity | 16구간 각각의 등장·퇴장 투명도 계산 |

접근 시 중앙→도어로 구간이 늘고, 멀어지면 도어→중앙으로 줄어듭니다.
색/구간 표시와 별개로 빨강 기준선은 500ms 전체 주기(250ms ON/OFF) 3회 점멸 후 고정하며 같은 빨강 구간에서는 반복하지 않습니다.
실제 Light API에는 기존 경고 등급 변경을 전달합니다. 화면의 16구간 그림을 차량 하드웨어의 16영역 제어로 해석하면 안 됩니다.

### 거리·매크로·Console·SDK

| 파일·주요 함수 | 담당 |
| --- | --- |
| AlleyWorld.advance / restore / reset | 유일한 가상 차량 운동·상태 복원/초기화 |
| clearance / corners / obstacles | 회전한 차량 몸체·가상 벽/주차 차량과 간격 |
| guidance | 세 가지 짧은 가상 경로를 비교해 안내. 실제 조향 명령 아님 |
| polygonDistance / edgeDistance / segmentPairDistance / segmentDistance | 기존 기하 계산 |
| AlleyPassDistances | 근접/주의 거리 기준 상수 |
| AlleyDemoScenario.sample | 기존 10초 시연의 속도·브레이크 입력 |
| AlleyMacroGate.observe / consume / reset | 10km/h 이하·15초 내 브레이크 3회·1.2m 이내·정차·상호작용 조건 |
| AlleyMacroPresentation.card | 기존 가상 매크로의 단계별 문구/표시 |
| PleosConsoleDriver.start / press / release / select / stop / close | Console 연결·키 조작·기어 요청·종료 정차/복원 |
| ConsoleDriveModel.select / press / release / tick / clear | 키 누름 상태에서 Console 명령 계산 |
| VehicleSpeedTest / VehicleGearSource | AAOS 차속·기어 start/stop |
| PleosSteeringSource / PleosSteeringMapping | Pleos 조향 구독·해제 및 원본 각도 정규화 |
| PleosBrakeTest | 기존 브레이크 구독 start/stop |
| PleosLocationProbe | 위치 진단 start/stop, 환경 지원 확인 |
| DrivingUxSource | 기존 시스템 주행 제한 start/stop |
| AmbientLightBridge.visible / enable / warnings / close | 실제 Light 시연 조건·위험 등급 입력·종료 |
| connect / parked / read / write / readback / apply / restore / pulse | 권한·영역 확인, 원래 값 저장, 쓰기·재조회·복원 처리 |

Alley3dView/Alley3dFrame/Alley3dRenderer는 기존 3D 전달·렌더링 경로를 유지합니다.
VehicleInfoPanel과 AlleyVoiceCoach도 기존 정보 표시·음성 기능을 유지합니다.
`com.example.myapplication`의 이전 Java 코드와 독립 SDK 시험 코드는 삭제하거나 새 실행 진입점으로 바꾸지 않았습니다.

## 6. 기능 보존을 위한 수정 위치

- 거리/차량 운동 변경: AlleyWorld에서 수정하고 기존 geometry 테스트를 실행합니다.
- 위험 판정 변경: SideClearanceWarning.level, AlleyPassDistances, AlleyMacroGate를 함께 검토합니다.
- 중간 색만 변경: SideClearanceWarning.ambientColor를 수정합니다.
- 조명 구간 수/속도: AmbientStripAnimation의 SEGMENT_COUNT/DURATION_MS를 수정합니다.
- 조명 형태/빛 번짐: InteriorDemoView.drawAmbientStrip을 수정합니다.
- 실제 차량 조명 정책: AmbientLightBridge에서 수정하고 SET ACK와 독립 GET 결과를 구분합니다.
- 버튼/배치: create.../populate... 함수에서 수정합니다.
- UI 글자/버튼 상태: render... 함수에서 수정합니다.
- 기능 해제/화면 종료: stopVehicleSources, setActive(false), release의 정리 순서를 유지합니다.
- 주행 제한을 해제하거나 시스템 정책을 우회하는 변경은 포함하지 않았습니다.

## 7. 버전 1 검증 기록

- 빌드: `:app:assembleDebug` 성공.
- 단위 테스트: `:app:testDebugUnitTest` **73개 통과**, 실패 0.
- 기존 67개 테스트와 구간 애니메이션 회귀 테스트 6개를 포함합니다.
- 추가 확인: 좌우 독립 점등/소등, 구간별 중간 투명도, 바깥쪽 우선 소등, 화면 복귀 시 시간 보존, 긴/역행 시간의 점프 방지, 비정상 목표값 처리.
- 설치: Pleos AVD `emulator-5554`에 정리한 앱 설치 성공.
- AVD 자동 시연 완료: 좌측 가상 0.70m 주황 / 우측 2.50m 초록, 실내 구간 조명 표시 확인.
- Alley Pass: 조건 충족 → 확인창 → 가상 실행 → '가상 실행 완료 · SIMULATION' 화면 확인.
- 초기화: 좌우 2.60m로 복귀하고 실내 조명 구간이 꺼진 화면 확인.
- 진단 정보 펼침/접기 및 기본 화면 복귀 확인.
- P·0km/h에서 Console 키보드 연결 성공. 기어 P 재조회와 D 조향 키 DOWN/UP 수신 확인.
- 실제 로그: KEY_DOWN code=32 control=RIGHT accepted=true → CONTROL_REQUEST speedKmh=0 steering=500 → Pleos 콜백 raw=-500.0 normalized=1.0 → KEY_UP → steering=0.
- 검증 종료 시 Console STOP_VERIFIED speed=0 gear=P 로그 확인.
- 이번 AVD 회귀 시험의 키 조작은 P·0km/h에서 수행했습니다. D/R 기어로 장거리 이동하는 시험 및 실차 주행은 이번에 반복하지 않았습니다.
- 실제 차량 Light 색 적용 성공·주행 중 제어·실차 성능은 이번 리팩터링으로 검증 완료된 항목이 아닙니다.

이 문서는 V1 기능 기준과 유지보수 안내이며 APK 서명·배포 승인·실차 검증을 대신하지 않습니다.
