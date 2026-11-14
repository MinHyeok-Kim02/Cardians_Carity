plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.guardians.viewer"
    useLibrary("android.car")
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.guardians.viewer"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
}
