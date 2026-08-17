plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.nico.scouterdirect"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.nico.scouterdirect"
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "1.0-direct"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.ai.edge.litert:litert:2.1.5")
}
