import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Off-device benchmark harness: runs the SAME relocalization code as the app, but against
// desktop OpenCV (native mac/linux/win) so it can be measured on a workstation and in CI.
// Layer 1.5 (synthetic correspondences → real solvePnPRansac) runs everywhere; the RGB-D
// benchmark (7-Scenes / TUM) is gated on the dataset being present. See docs/relocalization-testing.md.
dependencies {
    implementation(project(":relocalization"))
    implementation(libs.kotlin.math)
    implementation(libs.opencv.desktop)

    testImplementation(libs.kotlin.test.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}
