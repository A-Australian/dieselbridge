// Root build script — plugins are declared here (apply false) and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9.0 provides built-in Kotlin; the standalone kotlin-android plugin is intentionally absent.
    alias(libs.plugins.kotlin.compose) apply false
}
