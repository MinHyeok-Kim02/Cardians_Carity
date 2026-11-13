# Alley Pass 개발 진행상황

최종 정리: 2026-11-13 · 프로젝트: Pleostest / com.example.pleostest

## 현재 상태 한눈에 보기

**맞은편 차량 코드를 삭제했다. 앱의 D/R/N/P 버튼과 방향키가 Pleos 에뮬레이터 Console을 조작하고, AAOS·Pleos SDK로 돌아온 값으로 3D 차량을 움직인다. 주차 차량·측면 전체 거리 경고는 유지했다. 정차·P단 후 사용자 확인을 거치는 미러·창문·비상등 매크로는 가상 실행이다.**

| 구분 | 현재 구현·검증 결과 |
| --- | --- |
| 속도 | AAOS로 수신. Console 가속·감속에 따른 값 변화와 가상 차량 이동 확인 |
| 브레이크 | SDK 누름·해제 수신 및 현재값 주기 조회. 15초 내 해제→누름 3회를 감지 조건으로 사용 |
| 조향 | Pleos SDK 원본 유지·모델 좌표 부호 변환으로 좌우 반전 수정. 키 입력1초 램프 제거, 양방향 SDK 이동 확인 |
| 주변 거리 | 주차 차량·골목 벽과 내 차 전체 외곽의 가상 간격. 맞은편 차량 없음 |
| 화면 | 오른쪽 앱 영역 전체3D·상단 단계 카드·하단 D/R/N/P 및 조작 버튼. 왼쪽 Pleos 차량 정보 유지 |
| 접촉 | 가상 접촉 시 빨간 경고와 시뮬레이션 정지 확인 |
| 매크로 | 조건 감지→정차·브레이크 확인→사용자 확인→가상 미러·창문·비상등3회 표시. 실제 액추에이터 호출0 |
| 검증 | Android 단위 테스트·debug 빌드 통과. D단·6km/h 왼쪽 시스템 표시, SDK 이동·근접 경고, P단 확인창·가상 매크로 완료 확인 |
| 미완료 | Android TTS 연결은 추가했으나 현재 한국어 준비 실패로 음성 출력 미검증. 팀 음성 통합·실내 조명 제어·실차 검증은 남음 |

Ghost View·미래 궤적은 개발 범위에서 제외한다. 아래 계산은 시연용 가정이며 실차의 통과 가능성이나 충돌 회피 성능을 검증한 결과가 아니다.

## SDK 조회·제어 결과 요약

조회 성공, 제어 API 존재, 실제 변경값 재조회 성공은 각각 구분한다.

| 기능 | 조회 | 앱에서 제어 시험한 결과 |
| --- | --- | --- |
| 속도·브레이크·조향 | 성공 | 읽기 용도로 사용 |
| 창문 위치 | 성공 | 요청 완료 응답 수신, 변경값 재조회 불일치 → 적용 미확인 |
| 도어 잠금·위치 | 성공한 지원 영역 있음 | 값 유지 또는 개별 영역 미지원 오류 → 적용 미확인 |
| 앰비언트 스위치 | 성공 | OFF→AUTOMATIC→OFF 재조회 확인. ON 점등은 미검증 |
| 앰비언트 밝기·색상 | 성공 | 요청 후 기존값 유지 → 적용 미확인 |
| 사이드미러 열선 | 성공 | 0→1→0 변경·복원 재조회 확인 |
| 공조 설정온도·풍량 | 성공 | 변경 요청 후 기존값 유지 → 적용 미확인 |
| 공조 현재온도·냉방·전원 | 성공 | 냉방·전원 쓰기 미시험 |
| 사이드미러 접기·펴기, 비상등 제어 | 관련 읽기와 별개 | Vehicle 2.3.2 공개 SDK에서 해당 제어 API 미발견 |
| 주변 장애물 거리 | 해당 API 미발견 | 가상 환경으로 구현 |

차량 제어 결과는 앱의 재조회 기준이며, 실제 물리 동작 검증과는 구분한다. SDK 전체 기능을 모두 시험한 상태는 아니다.

## 계산 근거와 실제 적용식

구현 기준: [AlleyWorld.kt](app/src/main/java/com/example/pleostest/AlleyWorld.kt), [PleosSteeringSource.kt](app/src/main/java/com/example/pleostest/PleosSteeringSource.kt), [AlleyPassSimulationView.kt](app/src/main/java/com/example/pleostest/AlleyPassSimulationView.kt).

### 입력과 가정

| 기호·항목 | 의미·값 |
| --- | --- |
| v | AAOS 수신 속도, m/s. km/h 표시는 v × 3.6. 후진은 음수 속도 사용 |
| a, R | SDK 조향 원본값과 Capability 절댓값 최대치. 확인 환경의 범위 -470~500 → R=500 |
| u | 정규화 조향, -1~1 |
| δ | 가상 앞바퀴 조향각, 최대 ±28도 |
| L | 가정한 휠베이스 2.7m |
| x, y, θ | 가상 차체 중심 좌표(m), 방향(rad). 초기값 (0, 4, 0) |
| 좌표 방향 | θ=0일 때 +y로 전진. +x는 오른쪽, -x는 왼쪽 |
| 도로·차체 | 도로 폭 7m, 내 차 폭 1.8m·길이 4.2m, 주차 차 폭 1.8m·길이 4.4m |

차량 제원을 SDK에서 자동 취득한 것이 아니다. 조향 ±28도 매핑도 데모 설정값이며 SDK 원본각과 실제 차량 운동의 보정은 미완료다.

### 1) 속도·조향으로 위치 갱신

저속 운동을 단순화한 자전거 모델 형태의 방향 변화식에 속도×시간 이동을 결합한다. 실제 코드는 아래 순서대로 방향을 먼저 바꾸고, 바뀐 방향으로 이동한다.

```text
u = clamp(-a / R, -1, 1)            // Pleos 수신 양수=왼쪽 → 모델 음수=왼쪽
δ = u × 28 × π / 180                 // 라디안 변환

Δt = min(프레임 경과 시간, 0.1초)
N = max(1, ceil(|v| × Δt / 0.08m))
h = Δt / N

N번 반복:
  Δs = v × h
  θ새값 = θ이전값 + (v / L) × tan(δ) × h
  x새값 = x이전값 + Δs × sin(θ새값)
  y새값 = y이전값 + Δs × cos(θ새값)
```

예: 조향 0, 속도 2m/s로 0.05초 진행하면 Δs=0.1m다. 초기 위치 (0, 4)에서 (0, 4.1)로 움직인다.

내 차의 이동 거리가 최대 약 8cm가 되도록 분할하여 장애물을 건너뛰는 현상을 줄인다. 완전한 연속 충돌 검사는 아니다. 긴 프레임 지연은 0.1초로 제한하므로 실제 경과 시간 전체를 따라잡지 않는다.

### 2) 차체 모서리 좌표

차체를 중심 기준 네 모서리 (±0.9, ±2.1)m의 직사각형으로 표현한다. 로컬 모서리 (px, py)를 다음처럼 회전·이동한다.

```text
X = x + px × cos(θ) + py × sin(θ)
Y = y - px × sin(θ) + py × cos(θ)
```

따라서 핸들을 돌리면 차체 모서리 위치도 바뀌며, 장애물까지의 여유가 달라진다.

### 3) 장애물·골목 경계까지의 최소 거리

차체와 주차 차량 직사각형이 닿거나 겹치는지는 분리축 정리(SAT)로 검사한다. 각 변의 법선축에 두 도형을 투영했을 때 분리되는 축이 하나도 없으면 거리를 0으로 둔다.

떨어져 있다면 두 도형의 모든 변 쌍 사이 거리 중 최소값을 사용한다. 변이 교차하면0, 그렇지 않으면 양쪽 선분의 끝점에서 상대 선분 전체까지의 최소 거리를 구한다. 점 P와 선분 AB 사이 거리는 다음과 같다.

```text
t = clamp(((P-A) · (B-A)) / |B-A|², 0, 1)
Q = A + t × (B-A)
점-선분 거리 = √((Px-Qx)² + (Py-Qy)²)

왼쪽 경계 여유 = min(차체 모서리 X) + 3.5
오른쪽 경계 여유 = 3.5 - max(차체 모서리 X)

표시 거리 = min(양쪽 경계 여유, 각 주차 차량과의 거리)
표시 거리의 최솟값은 0
```

현재는 세로 중심 차이가18m 이내인 주차 차량을 거리 계산에 포함한다. 표시값은 **가상 차체 표면 사이의 최소 여유**다. 벽 밖으로 넘어가도 음수 대신0으로 표시한다.

추가로 왼쪽·오른쪽·앞·뒤 변 전체의 간격을 각각 계산한다.

```text
선분 AB와 CD 교차 → 거리0
그 외 → min(d(A,CD), d(B,CD), d(C,AB), d(D,AB))
왼쪽 측면 간격 = min(왼쪽 측면 전체와 각 장애물 변의 거리, 골목 벽까지 거리)
오른쪽·앞·뒤도 같은 방법
```

작은 장애물이 측면 중앙에 있어도 장애물 끝점에서 내 차 측면 안쪽으로 내린 수선으로 검출한다. 측면 중앙25cm 장애물에 대한 테스트를 추가했다. 앞·뒤 값도 각 변의 최소 여유이며 전후 방향으로 쏜 센서 거리나 실제 측정값이 아니다.

### 4) 접근·경고 판단

```text
접근 중 = 현재 거리 < 직전 거리 - 0.002m
```

직전 거리 조회와 비교하는 간단한 감소 판정이며, 접근 속도나 충돌 예상 시간(TTC)을 계산하지 않는다. 실행·입력 상태가 유효할 때 아래 경고를 위에서부터 우선 적용한다.

| 우선순위 | 조건 | 표시·동작 |
| --- | --- | --- |
| 1 | 가상 접촉 | 빨간 경고, 가상 이동 중지 |
| 2 | 거리 ≤ 0.3m | 근접 · 정차 후 확인 |
| 3 | 속력 > 10km/h | 속도를 낮추세요 |
| 4 | 가상 후보 경로 모두 여유45cm 미만 | 정차·교행 공간 확인 |
| 5 | 좌/우 후보가 직진보다 여유 있음 | 해당 방향 여유 확인 안내 |
| 6 | 거리 ≤ 1.2m이면서 접근 중 | 가까워지고 있어요 |
| 7 | 거리 ≤ 0.8m | 공간 주의 |
| 8 | 그 외 | 주변을 확인하며 천천히 이동 |

후보는 현재 속도에서 정규화 조향 -0.35·0·+0.35로2.5초 진행시킨 전체 차체 최소 여유를 비교한다. 실제 조향 변화 시간·운전자 반응시간·상대 차의 임의 행동은 반영하지 않는다. 현재30cm 이하에서는 방향 추천보다 정차를 우선한다. 미래 궤적은 화면에 그리지 않는다.

이 값들은 시연용 규칙이다. 브레이크 입력은 상태 표시용이며, 이동 감소는 수신 속도 변화로 반영한다. 속도·조향 미수신 또는 3초 이상 지연 시 이동을 보류한다. 실제 차량에 자동 제동·조향 명령을 보내지 않는다.

## 시연 방법과 남은 작업

1. Virtual Console(http://127.0.0.1:8088/)에서 속도 0·P단·조향 중앙으로 설정한다.
2. Pleos 런처에서 앱을 열고 '처음 위치로' → '시뮬레이션 시작'을 누른다.
3. D단에서 3~5km/h 정도로 진행하며 조향을 바꿔 가상 위치·거리·경고 변화를 확인한다.
4. 가상 접촉 후에는 P단에서 초기화하고 다시 시작한다.

주행 중 화면 유지 설정은 debug 에뮬레이터 시연용이다. 출시 환경의 주행 허용 검증은 별도로 필요하다.

- [x] 차량 상태 수신 및 가상 골목 이동·거리·경고 연동
- [x] 가상 접촉 정지, 앱 화면 영역 보정, 에뮬레이터 시연
- [x] 계산식·단위·모델 가정 및 한계를 문서화
- [ ] Android TTS 한국어 출력 확인 및 팀원 음성 코칭 모듈 통합
- [ ] 차량 제어 상태 미반영 원인 확인 및 필요한 기능만 재검증
- [ ] 일반 권한 승인·거부 흐름, 회전·다른 화면 크기 검증
- [ ] 실제 거리 센서·실차 조향 보정 및 실차 검증

## 관련 문서

- [3D·자동 시연 실행 가이드](AlleyPass_3D_자동시연_가이드.md)

- [팀 공유용 간단 요약](팀공유_AlleyPass_개발요약.md)
- [SDK 기능별 조회·제어 상세 현황](SDK_기능별_조회_제어_현황.md)
- [SDK 실제 제어 시험 보고서](SDK_실제제어_검증_2026-11-12.md)
- [SDK 초기 분석](Pleos_SDK_점검_2026-11-12.md)

아래에는 기존 개발 이력을 보관한다. 초기 절의 '미검증'·'남은 작업'은 당시 상태이며, 현재 상태는 위 요약과 다음21절·20절을 우선한다. 19절 이하의 맞은편 차량·내부 가상 키보드 설명은 당시 구현 기록이며 현재는 대체되었다.

## 21. 조향 좌우 반전·키 입력 반응 개선 (2026-11-13)

### 원인과 수정

- 실기에서 ←키 → Console 음수 → Pleos SDK 양수, →키 → Console 양수 → SDK 음수를 확인.
- 모델은 양수=오른쪽이므로 그대로 전달하던 부호가 반대였다. 신규 PleosSteeringMapping에서 모델용 값만 -raw/range로 변환. SDK 원본값은 변경하지 않음.
- AlleyKeyboardDriver의 조향 도달1초·중앙 복귀0.5초 램프 제거. 다음 Console 전송 주기에 목표 조향·중앙 복귀 적용.
- Console 송신 약100ms 주기와 SDK 콜백 +1초 보조 조회는 유지. 조회 주기를 무작정 늘리거나 키 입력값을 이동에 직접 주입하지 않음.
- 실제 이동·거리·경고는 기존대로 AAOS 속도와 Pleos SDK 조향 수신값을 사용. 미수신/지연 입력 보류 유지.
- PleosConsoleDriver에 변경된 조향 요청 시각, PleosSteeringSource에 SDK 원본·정규화 값·수신 시각을 debug 로그로 기록.
- 신규 PleosSteeringMappingTest와 기존 AlleyKeyboardDriverTest 확장: 양방향 SDK 부호→곡선 이동, 잘못된 수신값, 첫 tick 반응·방향 전환·중앙 복귀·동시 좌우·정차 무이동 검사.

### 검증

- :app:testDebugUnitTest :app:assembleDebug 성공. Android44개 통과·실패0. emulator-5554 최신 debug 배포.
- D단 가속 후 키를 놓아 관성 이동하는 동안 ←/→ 입력을 각각 시험.
- ←: Console -500 요청2343360ms → SDK raw=500/normalized=-1 수신2343378ms. x=-0.0391m, heading=-0.0962rad로 왼쪽 이동.
- →: Console 500 요청2397294ms → SDK raw=-500/normalized=1 수신2397326ms. x=0.0835m, heading=0.1348rad로 오른쪽 이동.
- 위 조향 수신 차이는18ms·32ms 표본이다. 오른쪽 키 해제 중앙 복귀는220ms 표본도 관찰. 키 누름부터 화면 표시까지 전체 지연의 보장값이나 평균이 아님.
- 방향키가 전달돼도 수신 전에는 이동 모델에 예측 조향을 적용하지 않음. 실제 Console/DDS/SDK 전달 지연은 남을 수 있음.
- 시험 후 실제 속도0·P단으로 복귀.
- 변경 파일: AlleyKeyboardDriver.kt, PleosSteeringSource.kt, PleosConsoleDriver.kt, 신규 PleosSteeringMapping.kt 및 관련 테스트2개, 진행 문서·실행 가이드.
- 이 부호 매핑은 현재 Pleos Connect 에뮬레이터 실측 기준. 실차/다른 에뮬레이터 버전의 조향 방향은 별도 검증 필요.

## 20. 맞은편 차량 삭제·Console 키보드 연동 (2026-11-13)

### 삭제·보존

- AlleyWorld의 맞은편 차량 생성·접근·정차·저장 복원·후보 복사와 3D 표시를 제거.
- AlleyOncomingTest.kt와 tools/scenarios/oncoming.json 삭제. 이에만 해당하는 검사 제거.
- 기존 주차 차량·벽·차체 측면 전체 거리·근접 경고·매크로·SDK 진단 소스는 보존. 과거 문서는 이력으로 유지.

### 새 조작 방식

- 'Console 키보드 연결' → D 또는 R을 한 번 선택 → ↑ 가속. 키를 놓으면 감속.
- ←/→는 주행·관성 이동 중 곡선 이동에 반영. 0에서는 조향만 변경.
- ↓ 브레이크, Space 에뮬레이터 속도0 요청. 시연 최고속도6km/h.
- N은 중립이며 정지 명령이 아니다. P/D/R 전환은 최신 SDK 및 Console 정지값 확인 후 허용.
- 앱 → 에뮬레이터 내부 localhost:8088 → Console 상태 재조회 → AAOS 속도·기어/Pleos 조향·브레이크 → 앱 모델 경로. 앱에 키보드 속도를 직접 주입하지 않음.
- 가속페달과 속도값을 함께 Console에 전송하는 시연 모델이다. 실차 물리 엔진이 페달만으로 속도를 계산하는 시험은 아님.
- debug 에뮬레이터 전용. 인터넷·HTTP 허용은 debug Manifest에만 추가. SDK 버전·패키지 유지.
- 창 포커스 상실·화면 이탈·가상 접촉·연결 오류 시 입력 정리 및 속도0/P단 정리를 시도하고 재조회 로그 기록. 연결 단절 시 정리 성공은 보장할 수 없음.
- 화면 이탈 또는 매크로 후 새 주행은 P단에서 'Console 키보드 연결'로 다시 시작.
- 다른 Console 브라우저 탭·PC 자동 실행기를 동시에 조작하지 말 것.

### 매크로 조건

- 저속·15초 내 브레이크 해제→누름3회·가상 거리1.2m 이하 감지는 유지.
- Console 키보드 모드에서는 정차·AAOS P단·Console P단·UX 제한 해제를 확인한 뒤 버튼과 사용자 확인으로 가상 실행.
- P단이 확인되면 브레이크 키를 계속 누를 필요 없음. 기어 전환 중 잠깐 발생하는 HTTP/SDK 불일치에서 유효한 정지 브레이크 이력을 보존.
- 미러·창문·비상등과 실제 앰비언트 제어 추가 없음. 완료 로그 actuatorWrites=0.

### 변경 파일

- 신규 ConsoleDriveModel.kt / PleosConsoleDriver.kt: 기어·키 입력 모델, 세션 유지·HTTP 제어·재조회·연결 정리.
- 신규 VehicleGearSource.kt: AAOS 기어 주기 조회·생명주기 정리.
- AlleyPassSimulationView.kt / MainActivity.kt: 기어 UI·실제 SDK 입력 연결·포커스 처리.
- AlleyMacroGate.kt: 확인된 P단을 정차 유지 조건으로 사용.
- AlleyWorld.kt / Alley3dView.kt 및 관련 테스트: 맞은편 차량 제거.
- debug/AndroidManifest.xml, ConsoleDriveModelTest.kt, AlleyMacroGateTest.kt, Node 시나리오 테스트, 실행 가이드 갱신.

### 검증과 한계

- :app:testDebugUnitTest :app:assembleDebug 성공. Android41개·Node2개 테스트 통과, 실패0. emulator-5554에 최신 debug 배포 완료. 기어 고정·움직이는 중 전환 거부·N 관성 감속·브레이크 우선·P단 확인 관련 단위 검사 포함.
- 실제 기기에서 D단 한 번 선택 후 방향키로 가속하여 왼쪽 Pleos 시스템 D·6km/h 표시와 앱 CONSOLE_SDK 이동·근접 경고 확인.
- 브레이크3회 → 정차 → P단 → 매크로 확인창 → 가상 실행 확인. 상단 창문 단계 카드 및 SIMULATION_COMPLETED; actuatorWrites=0 로그 확인.
- R 선택 경로는 구현했으나 이번 실기 시험에서 앱 포커스 상실로 연결이 정리되어, 연속 후진 이동 검증은 미완료. N 주행 중 전환도 단위 검사 외 실기 검증은 남음.
- ADB 키 이벤트로 전달을 검증했다. PC 물리 키보드 동시 입력은 에뮬레이터 포커스·전달 설정에 따라 별도 확인 필요.
- localhost 요청은 공식 차량 SDK가 아닌 현재 에뮬레이터 내부 인터페이스. 버전 변경 시 재검증 필요.
- 한국어 TTS·실제 거리 센서·실제 차량 액추에이터 제어는 검증되지 않음.

## 19. 맞은편 차량 중앙 배치·거리 기반 정차 (2026-11-13)

### 변경 내용

- 맞은편 차량의 x좌표를1m에서 골목 중앙0m로 수정. 기존 평행 주차 차량 사이를 통과하는 위치로 배치.
- 주의 거리0.8m×1.5=1.2m를 맞은편 차량 정차 기준으로 사용. 차량 중심이 아닌 회전 차체 외곽 간 최단 거리.
- 첫5초 대기 후1.1m/s 접근은 유지. 내 차와1.2m 이내가 되면 정차하고 초기화 전까지 대기.
- 정차 경계를 넘는 상대 차량 이동은 이분 탐색으로 잘라 프레임 이동량만큼 불필요하게 접근하지 않게 처리.
- 내 차가 이미 정차한 경우 상대 차가 계속 밀고 와 충돌하던 시나리오 해소.
- 내 차는 기존 키보드·SDK 입력을 유지하며 자동 제동하지 않는다. 상대 차 정차 후에도 내 차를 계속 전진시키면 접촉할 수 있으므로 ↓ 또는 Space로 정차해야 함.
- 매크로의15초 내 브레이크3회·정차·브레이크 유지·사용자 확인 조건은 유지.1.2m 경계의 미세한 부동소수점 오차만 허용.
- HUD 및 매크로 카드 하단에 맞은편 차량 정차 대기 상태 표시.
- 상대 차량 위치·정차 상태를 저장·복원하고 가상 후보 경로 비교에도 동일 상태 복사.

### 변경 파일

- AlleyWorld.kt: 중앙 배치, 지속되는 상대 차량 위치·정차 상태, 거리 기반 정차, 저장 복원·후보 비교 반영.
- 신규 AlleyPassDistances.kt: 주의0.8m·접근/정차1.2m 공통 기준.
- AlleyMacroGate.kt: 동일 접근 거리와 수치 오차 허용.
- AlleyPassSimulationView.kt: 상대 차 정차 상태·차체 간격 표시, Bundle 저장·복원.
- 신규 AlleyOncomingTest.kt:8개 검증.
- AlleySurfaceTest.kt: 기존 충돌 검사를 '상대 정차 후 내 차가 계속 움직일 때 접촉'으로 수정. 충돌 정지 검사는 보존.
- 진행상황·실행 가이드 갱신. 실제 차량 SDK 제어 추가 없음.

### 검증

- :app:testDebugUnitTest :app:assembleDebug 성공.44개 테스트 통과, 실패0.
- 중앙 경로에서 모든 기본 주차 차량과0.59m 초과 간격 확인.
- 상대 차량1.2m 정차와 내 차 정지 후60초 추가 대기 시 접촉 없음 확인.
- 회전한 내 차의 차체 외곽 거리1.2m, 프레임 경계 넘김 방지, 저장·복원·초기화, 후보 비교 중 상태 보존 확인.
-1.2m 경계에서 브레이크3회·내 차 정차 후 매크로 확인 가능한지 검사 통과.
- 기기에서 정차 유지·표시 확인 결과는 추가 기록.

## 18. 키보드 가상 운전·상단 단계 안내 (2026-11-13)

### 조작 방법

하단 '키보드 운전(가상)' 선택 후 에뮬레이터 창에 키보드 포커스를 둔다.

| 키 | 동작 |
| --- | --- |
| ↑ | 누르는 동안 가속, 가상 최고속도6km/h |
| ↓ | 브레이크. ↑와 함께 누르면 브레이크 우선 |
| ← / → | 앞바퀴 조향. 전진 속도와 결합하면 곡선 이동 |
| 방향키 해제 | 가속 해제 후 자연 감속, 조향은 중앙으로 복귀 |
| Space | 즉시 가상 속도0. 누르는 동안 브레이크 상태 |
| R | 키보드 시나리오 처음 위치로 초기화 |

정차 중 좌우 키만 눌러도 조향만 바뀌고 위치는 움직이지 않는다. 가속키를 놓아도 남은 속도로 움직이는 동안 조향이 반영된다.
키보드 값은 '키보드 가상 입력'과 source=KEYBOARD로 구분하며 SDK·Console·실차에는 쓰지 않는다.
실제 SDK 모드·자동 시연 모드는 유지한다. 키보드 운전은 debug 에뮬레이터에서만 허용한다.

앱의 창 포커스 상실·화면 이탈 시 키·가상 속도·조향을 해제하고 일시정지한다. 재진입 시 버튼으로 다시 시작한다.
확인 패널에서 키보드 매크로를 시험하려면 정차한 뒤 ↓ 또는 Space를 유지해야 한다. 브레이크 해제 시 가상 실행은 취소된다.

### 상단 큰 안내

사용자 확인 → 1/3 미러 접기 → 2/3 조수석 창문 → 3/3 비상등 → 완료를 아이콘·큰 제목·설명으로 한 번에 하나씩 표시한다.
미러와 창문 안내는 각각2.2초, 비상등은0.5초 ON/OFF3회, 완료는 약7.6초 후 표시한다.
모든 단계는 SIMULATION이며 실제 액추에이터 호출은 없다.
가상 접촉·30cm 이하 급박한 근접 또는80cm 이하 접근 중 경고는 단계 안내보다 우선한다.
확인 중 감지30초 유효시간이 끝나면 패널을 닫고 다시 시연하도록 안내한다.
실행 중 정차·브레이크·UX 조건이 깨지거나 급박한 근접이 발생하면 가상 매크로를 취소한다.

### 변경 파일

- 신규 AlleyKeyboardDriver.kt: 키 상태, 가속·감속·브레이크·조향 복귀·즉시 정지. 기존 AlleyWorld 이동·외곽 거리 계산 사용.
- 신규 AlleyMacroPresentation.kt: 상단 단계 안내와 위험 경고 우선순위.
- AlleyPassSimulationView.kt: 키보드 버튼·입력 모드·포커스 정리·큰 상단 카드·단계 타이밍·확인 만료/취소 처리.
- MainActivity.kt: 키 이벤트와 창 포커스를 화면에 전달.
- 신규 AlleyKeyboardDriverTest.kt / AlleyMacroPresentationTest.kt: 신규10개 테스트.
- SDK 의존성·권한·패키지·기존 기능 소스 유지.

### 검증 결과

- :app:testDebugUnitTest :app:assembleDebug 통과. 총36개 테스트(기존26+신규10), 실패0.
- 동시 가속/좌우 조향의 대칭 곡선, 정차 조향 무이동, 키 반복 중복 방지·속도 제한·감속·브레이크 우선·Space·입력 해제·매크로 브레이크 유지 검사 통과.
- 모든 단계의 명칭·진행 표시와 위험 경고 우선순위 검사 통과.
- 기기에서 방향키 long-press 이벤트 전달 후 전진·조향 확인. 로그 예: x=0.0230, y=5.7990, heading=0.0296, source=KEYBOARD.
- 키 해제 후 speed=0 및 steering=0 확인. 앱 이탈·재진입 후0km/h·중앙 조향·시작 대기 확인.
- 가상 접촉 시 상단 '근접 경고 우선 / 가상 접촉·정지' 카드 표시를 UI 계층에서 확인.
- 자동 시연→정차 확인→가상 실행 후 상단 큰 미러 카드와 완료 카드를 스크린샷으로 확인.
- 완료 로그: SIMULATION_COMPLETED; actuatorWrites=0.
- 확인 패널 만료·조건 취소 보완 후 최종 빌드·배포 성공.
- 물리 PC 키보드의 Android Studio 전달 설정·개별 키보드 조합은 기기마다 별도 확인 필요. 동시 곡선은 단위 테스트, 기기 이벤트 연결은 ADB 키 이벤트로 검증했다.
- 한국어 음성 출력·실내 앰비언트·실제 미러/창문/비상등 제어는 여전히 미검증이다.

## 17. 매크로 중심 화면·하단 버튼·앱 내부 시연 (2026-11-12)

### 현재 조작 방법

| 하단 버튼 | 동작 |
| --- | --- |
| SDK 연동 시작 / 일시정지 | 실제 수신 속도·조향으로 가상 차량 이동. Console 조작이나 기존 PC 자동 도구와 함께 사용 |
| 자동 시연(가상) | debug 에뮬레이터에서 앱 내부 가상 속도·브레이크로10초 시연. 실제 SDK·Console·차량 상태는 변경하지 않음 |
| 초기화 | 가상 위치·반복 브레이크 이력·확인 패널·진행 중 가상 매크로 취소 및 초기화 |
| 패스 매크로(SIM) | 감지 후 정차·브레이크 유지와 UX 제한 해제를 확인한 경우에만 활성화. 하단 확인 패널에서 다시 '가상 실행' 필요 |

화면의 입력 출처를 '차량 SDK 입력'과 '가상 입력'으로 구분한다. 앱 내부 자동 시연을 실제 SDK 수신 시험으로 집계하지 않는다.

### 감지·확인 규칙

- 속도0~10km/h에서15초 내 브레이크 해제→누름3회와 가상 근접1.2m 이하가 충족되면 감지.
- 눌림 유지 콜백·주기 조회·최초 미확인→눌림은 반복 횟수로 세지 않음.
- 감지 상태는30초 유효. 초과속·후진·미확인 데이터는 이력과 감지를 취소.
- 매크로 확인은 최신 속도0.01m/s 이하·브레이크 눌림·화면 활성·UX 제한 해제 조건을 다시 검사.
- SDK 입력은3초 초과 시 미확인으로 취급. 브레이크는 읽기 전용1초 주기 재조회로 눌림 유지 상태의 신선도 확인.
- 미러 폴딩·창문 미세 개방·비상등3회는 가상 상태 문구로 순차 재생. 실제 제어·25cm 차폭 감소·3cm 개방을 검증한 것이 아님.
- 확인 패널도 앱 내부 하단에 표시. 주행 제한·화면 이탈·초기화·정차/브레이크 조건 상실 시 가상 실행 취소.
- 감지 조건과 거리·시간 기준은 시연용 설정이며 실제 차량 검증 기준이 아님.

### 변경 파일

- AlleyPassSimulationView.kt: ScrollView에서 FrameLayout으로 전환.3D 배경을 MATCH_PARENT로 배치하고 HUD·하단 제어·확인 패널 중첩.
- Alley3dRenderer.java: 전체 높이에 맞게 추적 카메라 조정, 내 차 전체가 하단 버튼 위에 보이도록 보완.
- 신규 AlleyMacroGate.kt: 저속·반복 브레이크·근접 감지와 정차 확인·소비 로직. 차량 제어 코드 없음.
- 신규 AlleyDemoScenario.kt: 앱 내부10초 가상 속도·브레이크 시나리오.
- PleosBrakeTest.kt: 기존 이벤트 구독·초기 조회 보존,1초 비중첩 재조회와 종료 시 취소 추가.
- 신규 AlleyMacroGateTest.kt: 유지 입력 중복 방지, 시간창·누락·과속·후진·UX 제한·브레이크 해제·만료·확인 재검사·단일 소비·전체 가상 시연 검사.
- 진행상황과3D 실행 가이드 갱신. 기존 패키지·SDK 버전·진단 소스 보존.

### 확인 결과

- :app:testDebugUnitTest :app:assembleDebug 성공.26개 테스트 통과(기존19+신규7).
- 카메라 및 브레이크 주기 조회 보완 후 최종 debug 빌드·배포 성공. 기기에서 source=PLEOS / event=REFRESH / Brake=RELEASED 반복 재조회 로그 확인.
- UI 계층에서3D 뷰가 앱 영역 [853,90]~[2535,1300] 전체를 채우며, 네 버튼이 하단에 고정됨을 확인.
- 스크린샷에서 왼쪽 Pleos 차량 정보와 시스템 바 유지, 내 차 전체·좌우 경고선·하단 버튼 확인.
- 자동 시연 버튼→가상 속도·브레이크→좌측0.70m 근접→정차→패스 매크로 확인 패널→가상 실행 완료를 기기에서 확인.
- 로그: SIMULATION_COMPLETED; actuatorWrites=0.
- 초기화 후 좌우2.60m, 반복 브레이크0/3, 시작 대기 화면 복귀 확인.
- 한국어 TTS·실내 앰비언트·실제 미러/창문/비상등 제어는 이전 미확인 판정을 유지한다.

## 16. 3D·측면 전체 거리·자동 시연 (2026-11-12)

### 변경 내용

- AlleyWorld.kt: 내 차 네 변 전체와 장애물 변 사이의 거리, 좌우·앞뒤 개별 간격 추가.
- 맞은편 차량1대: 가상 x=1m, 초기 y=48m, 가상5초 후1.1m/s 접근. 실제 차량 검출이 아님.
- 좌·직진·우 후보의2.5초 가상 이동을 비교해 여유 있는 방향 또는 정차 안내.
- Alley3dFrame.java / Alley3dRenderer.java / Alley3dView.kt: PCU의 GL 골목·추적 카메라를 참고해 우리 모델의 상태를 렌더링. 물리 계산은 AlleyWorld 한 곳에서 수행.
- AlleyPassSimulationView.kt: 3D·방향별 간격·안내 연결. 첫 속도 변화 때 가상 시간 시작, 이후 내 차 정지 중에도 상대 차량 이동.
- AlleyVoiceCoach.kt: Android TTS, 일반6초·긴급2초 반복 제한, 종료 정리. 한국어 미준비 표시.
- PleosSteeringSource.kt: debug 조향 수신 로그. Manifest에는 TTS 조회 선언만 추가.
- tools/console-scenario.mjs: Console 로컬 세션 heartbeat·AVD 연결·시간표 제어·재조회·앱 SDK 로그 확인·종료 정차.
- tools/scenarios/left-approach.json 및 oncoming.json: 저속 접근·교행 시나리오.
- AlleySurfaceTest.kt와 Node 테스트 추가. 기존2D 화면 소스와 SDK 진단 파일 보관.
- 상세 계산·실행 방법: [자동 시연 가이드](AlleyPass_3D_자동시연_가이드.md).

### 확인 결과

- Android 테스트19개 및 debug 빌드 성공. 측면 중앙의 작은 장애물25cm, 회전·교차 선분, 양측 근접 정차, 맞은편 이동·접촉·상태 보존 검사 통과.
- 최종 Node 시나리오 검증 테스트3개 통과. 통신·세션 유지·SDK 로그 확인을 보완한 뒤 재실행 성공.
- 3D를 Pleos 오른쪽 앱 영역에서 확인. 왼쪽 시스템 차량 정보 영역 유지.
- 브라우저 창 없이 저속 접근 시나리오 실행 성공. Console 속도0→4→0km/h, 조향0→-70→0, 브레이크 및 P단 복귀 재조회.
- 실행 중 앱 로그로 다음 결과 확인:

```text
SDK_OBSERVED {"speedChanged":true,"steeringChanged":true,"brakePressed":true,"virtualMovement":true}
PARK_VERIFIED
```

- 저속 접근 시연 이후 스크린샷에서 가상 맞은편 차량 접촉·빨간 정지 경고, 좌측0.44m·우측0.08m·앞면0.00m 표시 확인.
- oncoming.json도 자동 재생 완료, exit0. speedChanged=true / brakePressed=true / virtualMovement=true, 최종 PARK_VERIFIED 확인. 이 시나리오는 조향0 유지이므로 steeringChanged=false가 정상.
- 검증 이미지 alley3d-verification.png는 로컬 캡처로 Git 추적에서 제외.
- 한국어 TTS ready=false를 실제 확인. 음성 청취 성공으로 표시하지 않음.
- 최초 DDS OFFLINE은 브라우저 세션 heartbeat가 없어 발생. 자동화에 세션 유지 추가 후 DDS 연결 성공.
- 최초 Node fetch 통신에서 런타임 오류가 발생해, 기본 HTTP 모듈에서 연결을 닫고 응답을 모두 읽는 방식으로 수정. 이후 재생 정상 완료.
- Console 재조회와 앱 SDK 수신은 별개로 확인했다. 정해진 속도와 페달값을 함께 재생하며, 가속페달만으로 실제 차량 물리를 시험한 것은 아님.

### 아직 확인하지 않은 것

실차 안전성·센서 거리, 차종별 미러 돌출 제원, 한국어 음성 청취, 실내 앰비언트 좌우 제어, 임의 상대차 행동, 다른 화면 크기는 미검증이다. SDK 제어 성공 여부는 기존 표의 판정을 유지한다.

## 상세 개발 이력

아래 내용은 작업 당시의 기록이다.


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








