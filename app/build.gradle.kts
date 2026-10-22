plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.diegomatheus.contadortroco"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.diegomatheus.contadortroco"
        minSdk = 26
        targetSdk = 35
        // Cresce a cada GitHub Actions run; nunca reduzir entre atualizacoes assinadas.
        versionCode = (System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 2).coerceAtLeast(2)
        versionName = "1.1"
    }
    signingConfigs {
        if (!System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()) {
            create("permanentRelease") {
                storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingConfigs.findByName("permanentRelease") != null) {
                signingConfig = signingConfigs.getByName("permanentRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
