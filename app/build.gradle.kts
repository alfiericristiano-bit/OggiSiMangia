plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.oggisimangia.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.oggisimangia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("verificationVersionCode").orNull?.toInt() ?: 6
        versionName = "1.2.2"
    }
    val signingPath = System.getenv("ANDROID_KEYSTORE_PATH")
    if (!signingPath.isNullOrBlank()) {
        signingConfigs.create("persistent") {
            storeFile = file(signingPath)
            storePassword = System.getenv("ANDROID_STORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
        listOf("debug", "release").forEach { variant ->
            buildTypes.getByName(variant) {
                signingConfig = signingConfigs.getByName("persistent")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("org.jsoup:jsoup:1.18.3")
}

