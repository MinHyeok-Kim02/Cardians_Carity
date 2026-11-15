plugins {
    alias(libs.plugins.android.application)
}

/**
 * 프로젝트 루트의 .env (git 제외) 에서 AI 설정을 읽습니다.
 *  ANTHROPIC_API_KEY(또는 CLAUDE_API_KEY) 가 있으면 Claude, 없으면 Qwen(Ollama) 로 판단합니다.
 *  주의: 빌드에 들어간 키는 APK 에서 추출할 수 있습니다. 시연·개발용으로만 쓰세요.
 */
// Carity 통합 폴더에서는 Carity_ver1\.env 를 우선 사용 (없으면 android\.env)
val dotEnv: Map<String, String> = listOf(rootProject.file("../.env"), rootProject.file(".env")).firstOrNull { it.isFile }.let { file ->
    if (file == null) emptyMap() else file.readLines(Charsets.UTF_8)
        .map { it.removePrefix("\uFEFF").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val key = line.substringBefore('=').trim().removePrefix("export ").trim()
            val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
            key to value
        }
}
fun envValue(vararg keys: String, default: String = ""): String =
    keys.firstNotNullOfOrNull { k -> dotEnv[k]?.takeIf { it.isNotBlank() } } ?: default
fun javaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * 릴리스(Pleos App Market 제출용) 설정. 모두 .env 에서 읽습니다. (Carity.bat releasekey 가 자동으로 채움)
 *  RELEASE_STORE_FILE      서명 키 파일 (Carity 폴더 기준 상대경로 또는 절대경로)
 *  RELEASE_STORE_PASSWORD  키 저장소 비밀번호
 *  RELEASE_KEY_ALIAS       키 별칭 (기본 carity)
 *  RELEASE_KEY_PASSWORD    키 비밀번호 (비우면 저장소 비밀번호와 같음)
 *  RELEASE_APP_ID          제출용 패키지 이름 (기본 com.carity.app). debug 는 com.example.pleostest 유지
 */
val releaseStoreFile: File? = envValue("RELEASE_STORE_FILE").takeIf { it.isNotBlank() }?.let { path ->
    File(path).let { if (it.isAbsolute) it else rootProject.file("../$path") }
}?.takeIf { it.isFile }
val releaseAppId = envValue("RELEASE_APP_ID", default = "com.carity.app")

android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.pleostest"
    useLibrary("android.car")
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleostest"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLAUDE_MODEL", javaString(envValue("CLAUDE_MODEL", default = "claude-haiku-5-5")))
        buildConfigField("String", "QWEN_MODEL", javaString(envValue("QWEN_MODEL", default = "qwen2.5:3b")))
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = envValue("RELEASE_STORE_PASSWORD")
                keyAlias = envValue("RELEASE_KEY_ALIAS", default = "carity")
                keyPassword = envValue("RELEASE_KEY_PASSWORD", "RELEASE_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // 에뮬레이터 개발·시연용: PC 의 Qwen(Ollama)·라벨링 서버·위치 진단 사용
            buildConfigField("String", "ANTHROPIC_API_KEY", javaString(envValue("ANTHROPIC_API_KEY", "CLAUDE_API_KEY")))
            buildConfigField("String", "QWEN_BASE_URL", javaString(envValue("QWEN_BASE_URL", default = "http://10.0.2.2:11434")))
            buildConfigField("String", "LABEL_SERVER_URL", javaString(envValue("LABEL_SERVER_URL", default = "http://10.0.2.2:8765")))
            buildConfigField("boolean", "LOCATION_ENABLED", "true")
        }
        release {
            // Pleos App Market 제출용: PC 서버 없이 단독 실행. API 키는 넣지 않음 (APK 에서 추출 가능)
            buildConfigField("String", "ANTHROPIC_API_KEY", javaString(""))
            buildConfigField("String", "QWEN_BASE_URL", javaString(envValue("RELEASE_QWEN_BASE_URL")))
            buildConfigField("String", "LABEL_SERVER_URL", javaString(envValue("RELEASE_LABEL_SERVER_URL")))
            buildConfigField("boolean", "LOCATION_ENABLED", "false")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            // R8 최적화를 끕니다. androidx·kotlin 패키지만 최적화(packageScope)하면 kotlin 내부 클래스 접근 오류
            // (IllegalAccessError: kotlin.collections ... is inaccessible)로 실행 직후 꺼지는 문제가 있었습니다.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// 제출용(release) APK 만 패키지 이름을 바꿉니다. debug 는 기존 이름 그대로라 실행기·권한 스크립트가 그대로 동작
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.applicationId.set(releaseAppId)
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    // Pleos 음성 합성 (한국어 ko-KR, ON_DEVICE 모드) — 에뮬레이터/차량의 Pleos Speech Service 사용
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
}
