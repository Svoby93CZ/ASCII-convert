import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generates the app's Baseline Profile and measures cold starts with and without it. It runs on
// a device or emulator, see .github/workflows/baseline-profile.yml.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "cz.svoby93.asciistudio.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Profiles are collected without root from Android 13 on, and with root from Android 9.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

baselineProfile {
    // The generator runs on the device that is connected, e.g. the emulator of the CI workflow.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
