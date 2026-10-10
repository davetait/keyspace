// A minimal icon pack for testing Keyspace's icon pack support. Debug use only; never published.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ai.stxt.keyspace.testiconpack"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.stxt.keyspace.testiconpack"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}
