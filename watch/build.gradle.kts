import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // NOTE: AGP 9.0 has built-in Kotlin support and registers the `kotlin` extension itself, so we do
    // NOT apply org.jetbrains.kotlin.android (doing so throws "extension 'kotlin' already registered").
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "net.farcaster.pixelbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.farcaster.pixelbridge"
        minSdk = 28          // Wear OS 3 — covers every Pixel Watch Gen-1 firmware
        targetSdk = 28         // Wear OS 5.1 (Android 15) — the Gen-1 terminal OS. 36 is also valid.
        versionCode = 8
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No NDK / no abiFilters in our code. The only transitive .so (androidx.graphics.path, via
        // Wear Compose) ships all four ABIs, so the APK installs on the watch regardless. Never add
        // an arm64-ONLY native lib — Wear may reject it. See docs/platform-target.md.
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Match Gadgetbridge's toolchain style: Java 17 source/target, built with JDK 21.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
	lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Base Compose via BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Wear Compose (Material 3)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    // Ongoing Activity for the foreground-service notification
    implementation(libs.androidx.wear.ongoing)
    // On-watch reply input (RemoteInput)
    implementation(libs.androidx.wear.input)

    // Wear Tiles + ProtoLayout (Material 3) for the glanceable status/find-phone tile
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.tiles.tooling.preview)
    debugImplementation(libs.androidx.wear.tiles.tooling)
    // CallbackToFutureAdapter for the tile's ListenableFuture returns
    implementation(libs.androidx.concurrent.futures)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.json) // real org.json for GbProtocol JVM unit tests

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
