# 진행현황
## 11.11 작업내역
첨부된 KR 파일 참고

### 정상 동작 확인 항목
- 차량 제어 : 차량 속도 변동 및 조향각 입력 데이터 정상 전송
- UI 렌더링 : Mapbox Core 800~900 FPS, DrivingView ~49.5 FPS로 그래픽 렌더링 안정적ㄴ

### 해결이 필요한 것
1) GPS NMEA 데이터 Checksum 불일치(IllegalArgumentException 발생)
- 위치 : ai.umos.maps.android.navigation.app

2) SecureStorage 및 VHAL RPC 통신 거부(Connection refused (os error 111))
- V2-sdv-service(PID 213)에서 SecureStorage 데몬 호출 시 발생
- 보안 저장소 백엔드 서비스 실행 상태 점검 필요

3) SELinux 권한 거부 (AVC Denials)
- logcatProxy 엔트리포인트 차단 (entrypoint)
- icvi-service 실행 차단 (execute)
- 권한 문제로 인한 flags_health_check 재시작 반복

4) 서비스 바인딩 및 네트워크 타임아웃
- 미설치 패키지 Intent 바인딩 실패 (ai.pleos.iap2.eap.proxy 등)
- MediaStorageRepository (Storage Manager Service) 5000ms 연결 타임아웃 발생