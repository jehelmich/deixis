import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// A pure-Kotlin library: the offline relocalization map model, storage, geometry and the
// OpenCV-based feature/relocalizer code. No Android and no bundled OpenCV — OpenCV is
// compileOnly, so each consumer supplies the native build (the Android AAR in :app, desktop
// OpenCV in :relocalization-bench). That is what lets the identical matcher run off-device.
dependencies {
    implementation(libs.kotlin.math)
    compileOnly(libs.opencv.desktop)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.opencv.desktop)
}

// Match the app's JVM target (21) using the JDK that runs Gradle, without a separate toolchain.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}
