# 02. YCY + 현재 On-sense 기능 병합

## 기준과 결과

- 대상: 현재 열린 MOVO 프로젝트(com.example.pleostest).
- 입력: 프로젝트 안에 사용자가 추가한 02. YCY/Pleostest 폴더.
- 입력 폴더를 덮어쓰거나 삭제하지 않았습니다.
- 실행 소스 25개를 비교했습니다. 24개는 동일했고 AlleyPassSimulationView.kt의 화면 구성/원본 조향각 표시 연결이 달랐습니다.
- 입력의 새 VehicleInfoPanel.kt를 가져와 상단 가로 정보 바 표시 옵션을 추가했습니다.
- 기존 50:50 골목/실내 그림, 거리 경고·빨강 진입 3회 점멸, Light 요청/재조회/원래 색 복원, 키보드와 Alley Pass 매크로를 유지했습니다.

## 합친 화면

상단: YCY 차량 정보 바
- 실제 AAOS 차속(km/h)
- 실제 Pleos SDK 원본 조향각(°)
- 조향 방향, 기어, 브레이크, 플랫폼 제한 상태
- 자동 시연 중에도 이 정보 바는 실제 SDK 수신값을 표시하며, 3D의 가상 입력과 구분합니다.
- 3초 이상 오래된 값은 --로 표시합니다. 화면을 벗어나면 원본 조향각도 비웁니다.

중앙: 기존 좌우 50:50
- 왼쪽: 기존 3D 골목·가상 좌우 간격·기준선
- 오른쪽: 실내 조명 연출·Light 재조회 결과·정차 AVD 조명 연동 스위치

하단: 기존 SDK 연동, 자동 시연, Console 키보드, 기어, 초기화, Alley Pass 매크로

새 차량 정보 바는 기존 VehicleSpeedTest/PleosSteeringSource/VehicleGearSource/PleosBrakeTest의 수신값만 전달받습니다.
추가 차량 구독이나 별도 거리 계산을 만들지 않았습니다.

## 이번 변경

- app/src/main/java/com/example/pleostest/VehicleInfoPanel.kt 추가
  - 입력의 패널 기능 유지, compact=true 가로 표시 옵션 추가
- app/src/main/java/com/example/pleostest/AlleyPassSimulationView.kt 병합
  - 상단 패널 배치, rawSteering 보존/갱신/정리, 기존 render 경로로 값 전달
  - 기능 2·키보드·매크로 연결 유지
- FEATURE2_README.md: 병합 화면 설명 링크 추가
- YCY_MERGE_README.md: 이 문서

## 유지/비교 항목

- 공유하는 거리 계산·차량 수신·기존 3D 렌더러·Console 드라이버·매크로·음성 코치는 입력과 현재 코드가 동일했습니다.
- 공유하는 단위 테스트 9개 파일, Console 시나리오 도구와 시나리오 JSON도 동일했습니다.
- Gradle 설정/의존성/버전, main/debug Manifest, 주요 XML 레이아웃/문자열/테마는 동일해 변경하지 않았습니다.
- 입력 안의 03. KGY와 04. PCU는 별도 참고 프로젝트/초안입니다. 실행되는 Pleostest 소스와 별개인 MainActivity로 런처를 교체하지 않았습니다.
- 기존 한국어 음성 코치와 기능 2 테스트는 보존했습니다.

## 적용

병합 패치 ZIP을 기존 Pleostest 프로젝트 루트(settings.gradle.kts가 있는 폴더)에 풉니다.
ZIP에는 기능 2 파일과 새 차량 정보 패널을 포함한 수정·추가 파일만 상대 경로로 들어 있습니다.
02. YCY/Pleostest 폴더 전체, 빌드 출력, SDK 캐시, 로컬 설정은 넣지 않았습니다.

기능 2 상세 및 이전 실제 Light 검증 로그: FEATURE2_README.md.
이전 검증에서 정차 빨강 요청/독립 재조회 RGB 일치와 원래 흰색 복원을 확인했고, 초록은 재조회 흰색 유지로 적용 미확인입니다.
이번 병합은 표시 연결 변경이므로 실제 차량 조명 쓰기 시험을 자동으로 반복하지 않습니다.

## 검증

- assembleDebug / testDebugUnitTest: 성공. 단위 테스트 61 passed / 0 skipped / 0 failed.
- com.example.pleostest를 emulator-5554에 설치·실행 성공.
- 실제 화면: 상단 정보 바에 33.0km/h, SDK 원본 조향각 -0.0°, 기어 D, 브레이크 해제 표시 확인.
- 아래 골목/실내 콘텐츠의 폭은 각각 841px로 50:50 유지 확인. 기준선·실내 연출·Light 연동 스위치가 함께 표시됐습니다.
- 플랫폼 주행 제한 적용 중 표시와 기존 시작/초기화/키보드/매크로 버튼 비활성 상태도 확인했습니다.
- 이번 병합에서 차량 조명 쓰기·물리 점멸 동기화·TTS 청취·키보드 실제 주행·매크로 실행은 재시험하지 않았습니다. 기존 코드/테스트를 보존했고 조명 결과는 FEATURE2_README.md의 이전 정차 검증을 참고합니다.
