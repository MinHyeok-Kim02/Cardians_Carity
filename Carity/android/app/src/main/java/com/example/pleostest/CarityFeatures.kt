package com.example.pleostest

/**
 * 빌드 종류별 기능 스위치 (app/build.gradle.kts 의 buildConfigField 로 정해짐).
 *  - debug  : PC 의 Qwen(Ollama)·라벨링 서버·위치 진단을 사용 (에뮬레이터 개발·시연용)
 *  - release: Pleos App Market 제출용. PC 서버 없이 단독 실행 (규칙 엔진 + 앱 내장 라벨 장면 + Pleos TTS)
 */
object CarityFeatures {
    /** AI(Claude/Qwen) 문장 다듬기를 쓰는지. 쓰지 않으면 규칙 엔진 문장을 바로 읽음 */
    val aiEnabled: Boolean get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank() || BuildConfig.QWEN_BASE_URL.isNotBlank()
    /** PC 라벨링 서버(라벨 불러오기·PC 음성)를 쓰는지 */
    val pcServerEnabled: Boolean get() = BuildConfig.LABEL_SERVER_URL.isNotBlank()
    /** 위치 진단(Pleos FusedLocation)을 쓰는지. release 는 위치 권한을 선언하지 않음 */
    val locationEnabled: Boolean get() = BuildConfig.LOCATION_ENABLED
}
