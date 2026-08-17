import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.nico.scouterdirect"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.nico.scouterdirect.v14fullfr"
        minSdk = 23
        targetSdk = 35
        versionCode = 15
        versionName = "1.4-full-fr"
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

    androidResources { noCompress += "tflite" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.ai.edge.litert:litert:2.1.5") {
        exclude(group = "com.google.android.play", module = "ai-delivery")
    }

    testImplementation("junit:junit:4.13.2")
}
