plugins {
    id("com.android.application")
}

android {
    namespace = "fr.nico.scouter"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.nico.scouter"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
}
