# SDK 기능별 조회·제어 현황

기준: 현재 프로젝트의 Vehicle 2.3.2, FusedLocation 1.1.3 및 android.car 사용 코드.
최종 정리: 2026-11-12. 실측 대상: Pleos 에뮬레이터 emulator-5554.

## 추가 실측: 공조와 미시험 기능 조회

- 공조 설정온도: 좌우 22℃, 지원 범위 15~30℃, Capability READ_WRITE.
- 실내 현재온도: 좌우 19.1℃ 조회 성공.
- 풍량: 3단계, 지원 범위 1~8, Capability READ_WRITE. 사용자 승인 후 3→4 요청을 단독 시험했으나 완료 콜백 뒤에도 반복 조회값 3 유지. 적용 미확인. 복원 요청 후 원래값 3 재확인 완료.
- 냉방·전원: true 조회 성공, 각각 READ_WRITE. 제어 미시험.
- 공조 온도 단독 제어: 22→23℃ 요청 완료 콜백 수신, 반복 조회 22℃ 유지. 적용 미확인. 원래 22℃ 재확인 완료.
- 글로브박스 잠금: false 조회 성공. 제어 미시험.
- 창문 잠금: 지원 영역에서 false 조회 성공, READ_WRITE. 제어 미시험.
- ETCS 음량: 조회 시 장치 property 0x21400d01 미지원 오류. 현재 환경에서 사용 불가 응답 확인.
- 이번 추가 실측은 위 항목에 한정한다. 다른 도메인과 공조 세부 기능을 전부 시험한 것은 아니다.
- 공조·잠금·와이퍼·ETCS를 한 번에 변경하는 추가 배치는 자동 승인 검토에서 거부되어 실행하지 않았다.
  대신 조회 전용 시험을 수행했고, 공조 설정온도만 별도 버튼으로 시험했다.

## 표 읽는 방법

- **조회 API 있음**: 현재 설치된 SDK 소스에 조회 메서드가 있다. 장치 지원이나 실제 수신 성공을 뜻하지 않는다.
- **제어 API 있음**: SDK에 쓰기 메서드가 있다. 권한·Capability·안전 조건·재조회 검증을 별도로 통과해야 한다.
- **제어 검증 성공**: 우리 앱에서 요청한 다른 값이 별도 조회로 확인됐고 원래값 복원도 확인됐다.
- **적용 미확인**: 요청의 완료 콜백은 왔으나 반복 재조회가 기존값을 유지했다. 영구 미지원으로 단정하지 않는다.
- **미시험**: 소스만 확인했으며 이 앱에서 장치 검증을 하지 않았다.
- **API 미발견**: 확인한 현재 SDK 인터페이스에 해당 기능을 찾지 못했다. 다른 비공개 API까지 없다는 뜻은 아니다.

이번 표는 SDK가 제공하는 기능과 현재 앱의 구현·실측을 구분한 개발 판단용 목록이다.
조회에 성공했다고 해당 값을 제어할 수 있는 것은 아니다.
Vehicle 외 AAOS로 읽는 속도·기어는 출처를 별도로 표시한다.

## 1. Alley Pass 관련 기능과 실제 검증 결과

| 기능 | SDK/API로 조회할 수 있는 정보 | 제어 API | 우리 앱 조회 결과 | 현재 에뮬레이터 제어 판정 |
| --- | --- | --- | --- | --- |
| 브레이크 | 페달 눌림 Boolean | 없음 | 눌림·해제 변화 수신 성공 | 조회 전용 |
| 차량 속도 (AAOS) | 속도 m/s, 앱에서 km/h 환산 | 현재 사용하는 속도 속성은 읽기 전용 | 15 m/s 및 정차 0 m/s 수신 확인. 사용자의 가속·감속 연속 대응 시험은 미완료 | 조회 전용 |
| 기어 (AAOS) | 선택 기어 | 현재 프로젝트에서 제어하지 않음 | 제어 테스트 전 P단 확인 성공 | 조회 용도로만 사용 |
| 앞바퀴 조향각 | 각도 degrees, 지원 범위 | 각도 쓰기 없음 | 현재값·Capability READ 확인 | 조회 전용. 핸들 회전각으로 재해석하지 않음 |
| 창문 위치 | 영역별 위치·범위·Capability | setWindowPosition | 4개 창문, 0~10, 현재값 0 확인 | 적용 미확인. 조수석에 1·10 요청했으나 조회값 0 |
| 창문 잠금 | 영역별 잠금 여부·Capability | setWindowLock | 미시험 | 제어 API 있음, 미시험 |
| 창문 블라인드 | 위치·지원 영역 | moveWindowBlind | 미시험 | 제어 API 있음, 미시험 |
| 스마트 글라스 | 상태·구역·단계·설정 | setSmartGlassState / setSmartGlassZoneState / setSmartGlassLevel | 미시험 | 제어 API 있음, 미시험 |
| 앰비언트 밝기 | 영역별 밝기·범위 | setAmbientLightBrightness | 0~100, 현재 100 | 적용 미확인. 99·50 요청 후 100 유지 |
| 앰비언트 색상 | 영역별 색상 정수 | setAmbientLightBrightnessColor | 흰색 16777215 확인 | 적용 미확인. ARGB·RGB 빨강 요청 후 흰색 유지 |
| 앰비언트 스위치 | OFF / ON / AUTOMATIC 등 | setAmbientLightSwitch | OFF 확인 | **모드 변경·복원 성공: OFF→AUTOMATIC→OFF**. ON 점등은 미시험 |
| 사이드미러 열선 | 영역별 열선 단계·범위 | setHvacSideMirrorHeat | 범위 0~3, 현재 0 | **변경·복원 성공: 0→1→0** |
| 사이드미러 폴딩 | 해당 폴딩 상태 API 미발견 | 폴딩 API 미발견 | 열선과 구분 | 현재 확인한 SDK로 폴딩 구현 불가 |
| 도어 잠금 | 영역별 잠금 상태 | setDoorLock | 묶음 영역 잠금 true 조회 성공 | 묶음 영역 해제 요청 후 true 유지. 조수석 단독 영역은 unsupported 오류 |
| 도어 위치 | 영역별 위치·범위 | setDoorPosition | 조수석 0~1, 현재 0 | 적용 미확인. 1 요청 후 0 유지 |
| 글로브박스 잠금 | 잠금 상태 | setGloveBoxLock | false 조회 성공 | 제어 API 있음, 제어 미시험 |
| 방향지시등 | 상태·Capability | TurnSignal에 쓰기 없음 | Capability READ 확인. 현재 상태 조회 미시험 | 조회 API만 확인 |
| 비상등 작동 | 비상등 전용 기능 미확인. 방향지시등 상태와 구분 | 전용 제어 API 미발견 | 미시험 | 현재 확인한 SDK로 제어 구현 불가 |
| 장애물·주변 차량 거리 | 해당 거리·PDW API 미발견 | 없음 | 실제 센서 거리 수신 없음 | 거리 시뮬레이션 분리 필요 |
| 차량 식별 | CarInfo의 VIN | VIN 쓰기 없음 | 미시험 | 조회 API만 확인 |
| 차량 모델·차폭·전장 | 현재 CarInfo에서 조회 API 미발견 | 없음 | 없음 | VIN 조회를 제원 조회로 간주하지 않음 |
| 위치 (FusedLocation) | 위치 데이터 콜백 | 위치를 변경하는 제어 기능으로 사용하지 않음 | 기존 앱에서 위치 콜백 수신 확인 | 조회 용도 |

제어 성공은 에뮬레이터 SDK 조회값 기준이다. 실차의 물리적 움직임이나 Console 화면 시각 변화까지 검증한 것은 아니다.
모든 쓰기 시험은 사용자의 요청으로 앱 버튼을 눌러 수행했고 앱에서 속도 0·P단을 확인했다.

## 2. Vehicle SDK 전체 도메인 기능 요약

아래는 현재 Vehicle 진입점의 도메인 전체를 기능 단위로 정리했다.
1절의 실측 항목 외 기능들은 API 존재만 확인했으며 추가 권한 설치나 실제 제어 시험을 하지 않았다.
같은 도메인 안에서도 읽기 전용 속성과 쓰기 가능한 속성이 섞여 있다.

| 도메인 | 조회 API가 제공하는 정보 | 제어 메서드가 있는 기능 | 장치 검증 |
| --- | --- | --- | --- |
| Brake | 브레이크 페달 상태 | 없음 | 1절 참조 |
| SteeringWheel | 앞바퀴 조향각, 핸들 열선 상태 | 핸들 열선. 조향각 제어 없음 | 각도 조회만 확인 |
| Window | 창문 위치·잠금, 블라인드 위치, 스마트 글라스 | 창문 위치·잠금, 블라인드 이동, 스마트 글라스 상태·구역·단계 | 창문 위치만 1절처럼 시험 |
| Door | 도어 잠금·위치, 글로브박스 잠금 | 도어 잠금·위치, 글로브박스 잠금 | 도어 잠금·위치만 시험 |
| Light | 독서등·실내등·상하향등·미등·전후방 안개등 상태/스위치, 앰비언트 밝기·색상·스위치·상태, 주행모드별 색상 자동 적용 여부 | 해당 조명 스위치, 앰비언트 밝기·색상·스위치 및 자동 적용 설정. 비상등 전용 제어는 미발견 | 앰비언트 3항목만 시험 |
| SideMirror | 사이드미러 열선 | 열선 단계. 폴딩 없음 | 열선 변경·복원 성공 |
| TurnSignal | 방향지시등 상태 | 없음 | Capability READ |
| Safety | 에어백, 안전벨트 경고, 엔진/브레이크/변속기 오일 상태, 워셔액·출력 저하 경고, 일부 경고등 정보 | 확인한 인터페이스에 set 메서드 없음. 장애물 거리 없음 | 미시험 |
| CarInfo | VIN, deprecated CRN, 별도 private 권한 Capability 정보 | 확인한 인터페이스에 쓰기 없음 | 미시험 |
| Display | 온도 표시 단위 | 확인한 인터페이스에 쓰기 없음 | 미시험 |
| Odometer | 누적 주행거리, 구간별 주행거리·시간. deprecated 잔여거리 API | 잔여거리 setter는 DeprecationLevel.ERROR이므로 신규 구현 대상으로 사용하지 않음 | 미시험 |
| DrivingMode | 주행 모드, 크루즈 상태·목표 속도 | 확인한 인터페이스에 쓰기 없음 | 미시험. 주행 제어 시험 안 함 |
| Gear | deprecated 기어 선택 상태 조회·구독. SDK는 AAOS GEAR_SELECTION 사용 권고 | 현재 인터페이스에 기어 set 메서드 없음 | 앱은 AAOS로 P단 조회 |
| Fuel | 순간·현재·누적·급유 이후 연비, 연료 비율 | 확인한 인터페이스에 쓰기 없음 | 미시험 |
| EvBattery | 순간·현재·누적·충전 이후 전비, SOC, 충전 전류 제한·충전 스위치 상태. 일부 배터리량·용량·포트 API는 deprecated | 충전 전류 제한, 충전 비율 제한, 충전 포트 열림, 충전 스위치 | 미시험. 전비·SOC 자체를 쓰는 API로 오해하지 않음 |
| HVAC | 냉방·자동·내외기·성에 제거·좌우 연동·송풍 방향/세기·최대 냉방·전원·공기청정·설정/현재 온도 | 냉방/자동/내외기/성에 제거/연동/풍향/풍량/전원/공기청정/설정 온도 등 | 온도·풍량·냉방·전원 조회 성공. 온도23℃ 요청 후22℃ 유지. 나머지 제어·세부 항목 미시험 |
| Seat | 착석, 벨트 체결·높이, 시트 전후/높이/기울기/깊이/등받이/요추/헤드레스트 위치, 시트 온도·통풍 | 좌석·벨트 높이 등의 이동 제어, 시트 온도·통풍. 착석·벨트 체결 사실을 쓰는 기능은 아님 | 미시험 |
| Wiper | 와이퍼 상태·스위치 | 와이퍼 스위치 | 미시험 |
| Tire | 저압 관련 값. 실제 타이어 압력 API는 PRIVATE_CAR_SAFETY 요구 | 확인한 인터페이스에 쓰기 없음 | 미시험 |
| Toll | ETCS 음성 안내 음량 | ETCS 음성 안내 음량 | 조회 시 장치 속성 미지원 오류. 제어 미시험 |

PUBLIC 메서드가 존재해도 PRIVATE_* 권한, deprecated, 영역·값 범위 및 장치 Capability 제약은 별도로 확인해야 한다.
모든 SDK 제어를 이번에 실행한 것은 아니며, Alley Pass 관련 7항목의 실측 범위는 1절과 상세 보고서에 명시했다.

## 3. 주요 API 이름

| 기능 | 읽기 / Capability | 쓰기 |
| --- | --- | --- |
| 브레이크 | checkBrakePedalStatusCapabilities, getCurrentBrakePedalStatus | 없음 |
| 창문 | checkWindowPositionCapabilities, getCurrentWindowPosition | setWindowPosition |
| 도어 | checkDoorLockCapabilities, getDoorLockState, checkDoorPositionCapabilities, getCurrentDoorPosition | setDoorLock, setDoorPosition |
| 앰비언트 | checkAmbientLightBrightnessCapabilities, getAmbientLightBrightness, checkAmbientLightBrightnessColorCapabilities, getAmbientLightBrightnessColor, checkAmbientLightSwitchCapabilities, getAmbientLightSwitch | setAmbientLightBrightness, setAmbientLightBrightnessColor, setAmbientLightSwitch |
| 미러 열선 | checkHvacSideMirrorHeatCapabilities, getHvacSideMirrorHeatState | setHvacSideMirrorHeat |
| 조향각 | checkSteeringWheelAngleCapabilities, getCurrentSteeringWheelAngle | 없음 |
| 와이퍼 | checkWiperStateCapabilities, getCurrentWiperState, checkWiperSwitchCapabilities, getCurrentWiperSwitch | setWiperSwitch |
| 공조 | getHvacTemperature, getHvacTemperatureCurrent, getHvacFanSpeed 등 | setHvacTemperature, setHvacFanSpeed 등 |
| 좌석 | getCurrentSeatForeAftPosition, getCurrentSeatOccupancy 등 | moveSeatForeAft, setHvacSeatTemperature 등 |
| EV | getEvBatteryStateOfCharge, getEvChargeCurrentDrawLimit, getEvChargeSwitchState 등 | setEvChargeCurrentDrawLimit, setEvChargePercentLimit, setEvChargePortOpen, setEvChargeSwitch |
| ETCS | getCurrentEtcsVoiceGuidanceVolume | setEtcsVoiceGuidanceVolume |

## 4. 권한과 실제 사용 조건

Pleos 권한 접두사는 pleos.car.permission. 이다.

| 대상 | 조회 | 제어 |
| --- | --- | --- |
| 창문 / 와이퍼 | CAR_WINDOWS | CONTROL_CAR_WINDOWS |
| 도어 / 글로브박스 | CAR_DOORS | CONTROL_CAR_DOORS |
| 미러 열선 | CAR_MIRRORS | CONTROL_CAR_MIRRORS |
| 조명 | CAR_LIGHTS | CONTROL_CAR_LIGHTS. 앰비언트의 인터페이스 주석과 실제 구현이 달라 런타임으로 확인 |
| 브레이크 / 조향각 / 주행상태 | CAR_DRIVE | 각도·브레이크 쓰기 API 없음 |
| 공조 | CAR_CLIMATE | CONTROL_CAR_CLIMATE |
| 좌석 | CAR_SEATS (시트 온도·통풍 조회 포함) | CONTROL_CAR_SEATS (시트 온도·통풍 설정 포함) |
| EV / 연료 | CAR_ENERGY 중심 | EV 설정은 CONTROL_CAR_ENERGY. 연료 비율 쓰기 없음 |
| 식별 / 표시단위 | CAR_INFO | 해당 확인 기능 쓰기 없음 |
| 안전 / 타이어 | CAR_SAFETY. 일부 정보 PRIVATE_CAR_SAFETY | 확인한 기능 쓰기 없음 |
| ETCS | CAR_TOLL | CONTROL_CAR_TOLL |
| 속도 / 기어 (AAOS) | android.car.permission.CAR_SPEED / android.car.permission.CAR_POWERTRAIN | 현재 앱에서 제어하지 않음 |
| 위치 | pleos.car.permission.FUSED_LOCATION | 현재 앱에서 제어하지 않음 |

위 표는 대표 메서드 기준이다. 등록/구독, deprecated 및 private 메서드는 다른 권한을 요구할 수 있다.
특히 SideMirror 열선 구독 선언은 CAR_CLIMATE인 반면 현재값 읽기는 CAR_MIRRORS이다.
소스의 메서드별 선언·실제 구현과 장치 승인 여부를 함께 확인해야 한다.
권한이 필요하다는 이유만으로 이번 문서 작업에서 추가 권한을 선언하지 않았다.

## 5. Alley Pass 구현 판단

- 실제 차량 정보 표시: 브레이크·속도·기어·조향각의 조회를 활용할 수 있다. 조향각의 장치 의미·범위는 추가 확인.
- 검증된 제어: 앰비언트 AUTOMATIC 모드 변경, 사이드미러 열선. 열선을 폴딩 기능의 대체로 제시하지 않는다.
- 핵심 후보지만 추가 조사 필요: 창문 부분 개방, 앰비언트 밝기·빨강 경고, 도어 제어.
- 현재 SDK에서 미발견: 미러 폴딩, 비상등 작동, 주변 장애물 거리.
- 거리 시연은 SIMULATION으로 필드별 출처를 표시하고 실제 센서 측정이라고 안내하지 않는다.
- SDK 제어 가능 여부와 MVP에 넣을 필요성은 별개다. 공조·좌석·충전 제어는 이번 Alley Pass 범위로 자동 확장하지 않는다.

## 관련 기록

- [실제 제어 시험 상세](SDK_실제제어_검증_2026-11-12.md)
- [초기 SDK 정적 점검](Pleos_SDK_점검_2026-11-12.md)
- [개발 진행상황](Alley_Pass_개발_진행상황.md)

초기 작성 시에는 정적 분석만 수행했다. 이후 상단 추가 실측 절에 기재한 조회와 공조 온도 단독 제어 시험을 수행했다.
