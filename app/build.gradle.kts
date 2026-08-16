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
        versionCode = 1
        versionName = "0.1"
    }
}
