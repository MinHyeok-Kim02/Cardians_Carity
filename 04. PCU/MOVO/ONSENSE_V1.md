# On-sense 버전 1 — 현재 기능·함수 설명·실행 방법

이 문서는 로드뷰를 제거한 현재 MOVO 프로젝트의 버전 1 기준입니다.
실행 진입점은 `com.example.pleostest.MainActivity`이며 Android 앱 버전 설정과 Gradle·AGP·JDK는 유지했습니다.
기능 추가보다 기존 동작을 보존하면서 화면 생성·입력·갱신·시연·그리기를 담당 함수로 분리한 정리입니다.

## 1. 현재 진행 사항

| 기능 | 버전 1 상태 |
| --- | --- |
| 차량 정보 | AAOS 차속·기어, Pleos 조향각·브레이크 수신. 원본 값은 진단에서 확인 |
| 3D 골목 | 기존 가상 차량 운동·주차 차량·가상 벽 유지. 도로 끝 안전봉 5개를 기하 장애물로 추가 |
| 화면 구성 | 왼쪽 3D 골목 / 오른쪽 실내 그림 50:50, 상세 정보 접기/펼치기 |
| 키보드 | 기존 Console → AAOS/Pleos 경로 유지. 별도로 실제 값이 바뀌지 않는 가상 키보드 모드 추가 |
| 자동 시연 | 실제 Console 값을 변경하지 않는 10초 가상 입력 시나리오 |
| 기능 2 | 좌우 경고·빨강 진입 3회 점멸·실내 조명 연출 |
| 조명 연출 | 좌우 각각 16구간을 기본 초록으로 유지. 경고색은 도어→중앙 방향으로 구간별 전파 |
| 거리별 표현 | 간격이 줄면 경고색 영역 증가, 멀어지면 중앙→도어 순서로 초록 복귀. 1.6m 이상 전체 초록 |
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
3. **가상 키보드 시연**: 실제 P·0km/h에서 시작합니다. '가상 R' 버튼 또는 R 키 → ↑/W로 후진, '가상 D' → ↑/W로 전진합니다. ↓/S 브레이크, Space 정지. 실제 SDK/Console 기어·속도는 변경하지 않습니다. 화면에 '가상' 기어가 표시됩니다.
4. **Console 키보드 연결**: debug Pleos AVD의 P·0km/h에서 연결한 뒤 D/R을 선택합니다. 앱 화면에 포커스를 두세요.
4. ↑/W 길게: 가속, ↓/S: 브레이크, ←/A·→/D: 조향, Space: 정지, R: 정차 상태에서 후진 기어 선택. R 기어에서 ↑/W는 후진 가속입니다. 재준비는 초기화/키보드 연결 버튼을 사용합니다.
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
| requestGear | 화면 기어 버튼과 R 단축키의 공통 경로. 신선한 정차 입력에서 기어 요청 |
| createMacroConfirmation / createActions | 실행 확인창과 SDK/시연/키보드/초기화/매크로 버튼 |
| toggleSdkSimulation / resetScene | SDK 재개/일시정지, 가상 장면 초기화 |
| requestMacroConfirmation | 매크로 조건 확인 및 사용자 확인창 열기 |
| prepareScene / prepareKeyboard | 가상/SDK 입력 준비, Console 키보드 연결 준비 |
| prepareVirtualKeyboard | 실제 P·0km/h를 확인한 뒤 독립 가상 키보드 입력 시작 |
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
| sectionOpacity | 구간별 보간 계수 계산 |
| sectionColor | 도어 쪽부터 기본 초록과 경고색 사이의 중간 색 계산. 조명 자체는 끄지 않음 |

기본 상태는 모든 구간이 초록입니다. 접근 시 도어→중앙 순서로 경고색이 늘고, 멀어지면 중앙→도어 순서로 초록으로 돌아옵니다. 미확인 입력은 초록 대신 회색으로 구분합니다.
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
| VirtualKeyboardDrive.reset / stop / select / press / release / advance / input | 순수 로컬 가상 기어·키 입력·정지 처리. Context/SDK/HTTP 참조 없음 |
| VehicleMotionInput.signedSpeed | R 수신 시 AAOS 속도 크기를 음수 이동 속도로 변환. 이미 음수여도 방향을 두 번 반전하지 않음 |
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

## 도로 끝 부근의 중앙선 안전봉

- 안전봉은 도로를 가로막는 배치가 아니라 **중앙선 x=0**을 따라 y=76.5 / 80.5 / 84.5 / 88.5 / 92.5m에 4m 간격으로 놓입니다.
- 높이 0.95m, 받침 폭 0.45m이며 주황 몸체와 흰 반사띠로 그립니다. 바닥의 좁은 노란 표시도 주행 방향과 평행합니다.
- 안전봉 양옆으로 지나갈 수 있습니다. 예를 들어 차량 중심 x=+1.4m로 통과하면 왼쪽 안전봉 간격이 약 0.275m여서 왼쪽 빨강 경고가 나옵니다. x=-1.4m에서는 오른쪽이 같은 방식으로 경고됩니다.
- 중앙선을 그대로 밟고 직진하면 안전봉과 가상 접촉합니다. 자동 회피/차선 고정 기능은 추가하지 않았습니다.
- 마지막 안전봉을 충분히 지나갈 수 있도록 도로 끝은 y=100m, 전진 종료 위치는 차량 중심 y=97.5m로 옮겼습니다. 정차·후진은 종료 위치에서도 허용합니다.
- 안전봉을 지나 멀어지면 경고가 해제됩니다. 차로 측 벽이나 기존 주차 차량이 가까우면 해당 장애물의 경고는 계속 유지됩니다.
- 카메라/AI 검출이 아니라 등록된 가상 장애물의 거리 계산입니다. 화면과 충돌 계산은 같은 SafetyBollard 데이터를 사용합니다.
- 시연: 실제 P·0km/h → 가상 키보드 시연 → 가상 D → 기존 주차 차량을 지난 뒤 중앙선의 한쪽으로 조향 → 안전봉 옆 통과. 기존 10초 자동 시연은 변경하지 않았습니다.

| 파일·함수 | 담당 |
| --- | --- |
| AlleyWorld.SafetyBollard / CENTER_BOLLARDS | 중앙선 안전봉 위치·높이·받침 크기 |
| BOLLARD_START_Y / BOLLARD_SPACING | 시작 위치 및 4m 간격 |
| AlleyWorld.obstacles / clearance | 받침을 기존 다각형 거리·충돌 계산에 포함 |
| AlleyWorld.shouldStopAtEnd | 전체 안전봉 구간 이후의 전진 정지 |
| Alley3dView.publish / Alley3dFrame | 같은 위치를 GL 스레드에 불변 스냅샷 전달 |
| Alley3dRenderer.drawCenterlineBollards / drawSafetyBollard | 중앙선의 세로 표시와 안전봉·반사띠 렌더링 |
| cylinderMesh / bindBollardMesh / cylinder | 원통형 메시 재사용 |
| finishSceneIfNeeded | 종료 시 가상 입력 정지, R 재선택 허용 |

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
- 단위 테스트: `:app:testDebugUnitTest` **86개 통과**, 실패 0.
- 기존 80개와 중앙선 안전봉 6개 테스트를 포함합니다: 세로 간격, 좌우 방향별 경고, 양쪽 차로의 전 구간 무접촉 통과, 통과 후 초록 복귀, 중앙선 충돌, 기존 시작 상태 유지.
- 추가 확인: 좌우 독립 점등/소등, 구간별 중간 투명도, 바깥쪽 우선 소등, 화면 복귀 시 시간 보존, 긴/역행 시간의 점프 방지, 비정상 목표값 처리.
- 설치: Pleos AVD `emulator-5554`에 정리한 앱 설치 성공.
- AVD 자동 시연 완료: 좌측 가상 0.70m 주황 / 우측 2.50m 초록, 실내 구간 조명 표시 확인.
- Alley Pass: 조건 충족 → 확인창 → 가상 실행 → '가상 실행 완료 · SIMULATION' 화면 확인.
- 이전 기준의 초기화: 좌우 2.60m 복귀와 소등을 확인했습니다. 최신 변경은 소등 대신 초록 기본색 복귀입니다.
- 진단 정보 펼침/접기 및 기본 화면 복귀 확인.
- P·0km/h에서 Console 키보드 연결 성공. 기어 P 재조회와 D 조향 키 DOWN/UP 수신 확인.
- 실제 로그: KEY_DOWN code=32 control=RIGHT accepted=true → CONTROL_REQUEST speedKmh=0 steering=500 → Pleos 콜백 raw=-500.0 normalized=1.0 → KEY_UP → steering=0.
- 검증 종료 시 Console STOP_VERIFIED speed=0 gear=P 로그 확인.
- 실제 R 전환 시험: Pleos 후방카메라 ai.umos.car.evs.CarEvsCameraPreviewActivity가 전면에 표시되고, 기존 포커스 상실 처리가 Console을 정지/P 복귀시키는 것을 확인했습니다. 후방카메라를 비활성화하거나 주행 제한을 우회하지 않았습니다.
- 이를 위해 실제 Console 모드와 분리한 가상 키보드 시연을 추가했습니다. 가상 R + W 입력으로 y=4.0000m → 3.3330m 후진, 가상 D + W 입력으로 y=3.3330m → 3.9727m 전진, Space 정지를 실제 앱 로그에서 확인했습니다.
- 해당 로그 출처는 source=VIRTUAL_KEYBOARD입니다. 같은 시험 후 Virtual Console 재조회는 actualGear=Park, actualSpeedKph=0이었습니다.
- 최신 중앙선 세로 배치: 빌드·단위 테스트 및 Pleos AVD 설치 완료. x=±1.4m 차로에서 y=72→96m를 모두 통과하며 해당 방향 안전봉 경고가 발생하고, 접촉 없이 빠져나오는 것을 기하/운동 테스트로 검증했습니다.
- 이전 가로 배치의 y=74m 정지·후진 시험 기록은 현재 배치의 기준이 아닙니다. 최신 중앙선 배치의 수동 AVD 전 구간 통과는 이번 수정에서 별도로 재현하지 않았으며 위 통과 결과는 자동 테스트 결과입니다.
- 이 시험은 실제 기어 P·0km/h를 유지한 가상 키보드 주행입니다. 안전봉은 등록된 가상 장애물 거리 계산이며 카메라 감지가 아닙니다.
- 기본 초록 조명이 전체 구간에 표시된 화면을 확인했습니다. 그라데이션은 기존 중앙→도어에서 도어→중앙 경고색 전파로 반전했고, 거리가 멀어지면 초록으로 복귀합니다.
- 실제 R 상태에서 On-sense 화면을 강제로 유지하거나 실차 후진을 검증한 결과는 아닙니다.
- 실제 차량 Light 색 적용 성공·주행 중 제어·실차 성능은 이번 리팩터링으로 검증 완료된 항목이 아닙니다.

이 문서는 V1 기능 기준과 유지보수 안내이며 APK 서명·배포 승인·실차 검증을 대신하지 않습니다.
