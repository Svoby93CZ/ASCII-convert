import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// Release builds are signed with your upload key when it is configured: in keystore.properties
// for local builds, through RELEASE_* environment variables on CI (see README). Otherwise they
// fall back to the shared debug key, so the build always works, but Google Play rejects them.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigning(property: String, variable: String): String? =
    keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(variable).orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "cz.svoby93.asciistudio"
    compileSdk = 37

    defaultConfig {
        // Fixed by the app's Google Play Console entry and can never change. The code keeps its namespace.
        applicationId = "com.asciistudio"
        minSdk = 26
        targetSdk = 37
        // CI passes its run number, so every bundle uploaded to Google Play has a higher version code.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = "1.0.0"
    }

    androidResources {
        // Drops library translations to languages the app itself does not speak.
        localeFilters += setOf("en", "cs")
    }

    bundle {
        language {
            // The app language can be changed in Android settings (locales_config.xml), so Google Play
            // has to install every translation, not only the languages of the device.
            enableSplit = false
        }
    }

    signingConfigs {
        getByName("debug") {
            // Committed on purpose: every machine and CI run signs debug builds with the same key,
            // so newer builds install over older ones.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releaseStoreFile = releaseSigning("storeFile", "RELEASE_STORE_FILE")
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseSigning("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning("keyAlias", "RELEASE_KEY_ALIAS")
                // Key stores made by keytool (PKCS12) use the same password for the store and the key.
                keyPassword = releaseSigning("keyPassword", "RELEASE_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Print every finding into the build log, handy when lint fails on CI.
        textReport = true
        textOutput = file("stdout")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // Installs the Baseline Profile when the app does not come from Google Play.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    baselineProfile(project(":baselineprofile"))
}
