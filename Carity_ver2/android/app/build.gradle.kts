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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ANTHROPIC_API_KEY", javaString(envValue("ANTHROPIC_API_KEY", "CLAUDE_API_KEY")))
        buildConfigField("String", "CLAUDE_MODEL", javaString(envValue("CLAUDE_MODEL", default = "claude-haiku-5-5")))
        buildConfigField("String", "QWEN_BASE_URL", javaString(envValue("QWEN_BASE_URL", default = "http://10.0.2.2:11434")))
        buildConfigField("String", "QWEN_MODEL", javaString(envValue("QWEN_MODEL", default = "qwen2.5:3b")))
        buildConfigField("String", "LABEL_SERVER_URL", javaString(envValue("LABEL_SERVER_URL", default = "http://10.0.2.2:8765")))
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
