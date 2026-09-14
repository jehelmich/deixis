// Root build script. Plugin versions live in gradle/libs.versions.toml; applying them here with
// `apply false` puts them on the classpath once so the app module can apply them by alias.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
