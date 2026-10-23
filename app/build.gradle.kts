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
        versionCode = providers.gradleProperty("verificationVersionCode").orNull?.toInt() ?: 29
        versionName = "1.13.0-beta3"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

