import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module with the image → ASCII conversion engine.
// It has no Android dependencies, so it builds and tests fast on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.test {
    useJUnit()
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
