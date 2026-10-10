plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version: bump MAJOR/MINOR by hand for meaningful releases. The build number is the number of git commits on
// this branch, so every commit automatically gets a higher versionCode (which Android needs to install an
// update) and a distinct versionName. Falls back to 1 outside a git checkout.
val versionMajorMinor = "0.1"
val buildNumber: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1
val appVersionName = "$versionMajorMinor.$buildNumber"

// APKs are named keyspace-<version>-<buildType>.apk (e.g. keyspace-0.1.17-debug.apk) instead of app-debug.apk.
base {
    archivesName = "keyspace-$appVersionName"
}

android {
    namespace = "ai.stxt.keyspace"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.stxt.keyspace"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key for now, so a release build installs over a debug one (same signature,
            // no uninstall, settings kept). Replace with a real release key before sharing it widely or
            // publishing to Google Play.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // A working XML pull parser for unit tests; Android's own is unavailable off-device.
    testImplementation(libs.kxml2)
}
