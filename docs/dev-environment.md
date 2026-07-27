# Dev environment — macOS Apple Silicon (M1)

> Version numbers below are a starting point — **let Android Studio's first sync align the exact
> AGP/Gradle/Kotlin/AndroidX versions.**

## Prerequisites

- **Homebrew** and **`adb`** (`adb version` ≥ 30).
- **JDK 21** — AGP 9 / Gradle 9 require it (a JDK 19 or older will not work).
- **Android Studio** + the Android SDK (installed below).

## Install checklist

```bash
# 1. JDK 21 for command-line/CI Gradle (Android Studio also bundles JBR 21).
brew install --cask temurin@21

# 2. Android Studio (Quail 2 / 2026.1.2 as of mid-2026).
brew install --cask android-studio

# 3. adb + fastboot are already present via Homebrew. If you reinstall, use:
#    brew install --cask android-platform-tools   (adb 37.x)
#    ⚠️ Keep ONE adb on PATH (Homebrew's OR the SDK's) to avoid "adb server version" clashes.
```

Then:

4. **Launch Android Studio** → first-run wizard installs the SDK into `~/Library/Android/sdk`.
5. **SDK Manager** (or `sdkmanager`) — install:
   - `platform-tools`, `emulator`
   - `platforms;android-36` and `platforms;android-35`
   - `build-tools;36.0.0`
   - **`system-images;android-35;android-wear;arm64-v8a`** (Wear OS 5.1 emulator, the Gen-1 target)
   - optional: `system-images;android-30;android-wear;arm64-v8a` for min-version testing
   - Verify exact IDs: `sdkmanager --list | grep wear`
   > On Apple Silicon you **must** use the **`arm64-v8a`** Wear images (Wear OS 4+/API 33+ images are
   > 64-bit only). x86 images won't run natively.
6. **zsh env** (`~/.zshrc`):
   ```bash
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
   ```
7. **local.properties** — `cp local.properties.example local.properties` and set
   `sdk.dir=$HOME/Library/Android/sdk` (Android Studio also writes this on first sync). Git-ignored.

Sources: [Android Studio releases](https://developer.android.com/studio/releases),
[JDKs for AGP](https://developer.android.com/build/jdks),
[Wear emulator](https://developer.android.com/training/wearables/get-started/emulator).

## Toolchain versions (this repo, `gradle/libs.versions.toml`)

| | Version | Note |
|--|--|--|
| AGP | 9.0.1 | needs Gradle ≥ 9.1.0; **provides built-in Kotlin** (don't apply `kotlin.android`) |
| Gradle | 9.1.0 | pinned in `gradle/wrapper/gradle-wrapper.properties`; embeds Kotlin 2.2.0 |
| Kotlin | 2.2.0 (K2) | match Gradle's embedded Kotlin; Compose Compiler = the `kotlin.plugin.compose` plugin |
| JDK (build) | 21 | source/target compat = **17** (matches Gadgetbridge) |
| Wear Compose | 1.6.2 | `androidx.wear.compose:compose-material3` (+ foundation, ui-tooling) |
| Compose BOM | 2026.05.00 | ⚠️ bump to latest at first sync |

> This exact set (AGP 9.0.1 / Gradle 9.1.0 / Kotlin 2.2.0 / compileSdk 36 / build-tools 36.0.0) builds
> `watch-debug.apk`.

### gradlew troubleshooting

- **`Cannot add extension with name 'kotlin', as there is an extension already registered`** — AGP 9.0
  has **built-in Kotlin** and registers the `kotlin` extension itself. Fix: do **not** apply
  `org.jetbrains.kotlin.android`; keep only `com.android.application` + `kotlin.plugin.compose`.
- **`SDK location not found`** — create `local.properties` with `sdk.dir=$HOME/Library/Android/sdk`
  (git-ignored; Android Studio writes it on first sync). `cp local.properties.example local.properties`.
- **`Cannot add extension … kotlin`** can also appear if the Kotlin plugin version ≠ the Kotlin
  embedded in your Gradle — keep `kotlin` in the catalog matched to the Gradle wrapper.

## Wear OS emulator (for UI work without the watch)

```bash
sdkmanager "platform-tools" "emulator" "platforms;android-35" "system-images;android-35;android-wear;arm64-v8a"
avdmanager create avd -n WearOS51 -k "system-images;android-35;android-wear;arm64-v8a" --device wearos_small_round
emulator -avd WearOS51
```
Or use **Device Manager → Create Device → Wear OS Small Round → API 35 (arm64)**. The emulator is
fine for Compose UI, but **BLE peripheral behavior must be validated on the real watch**.

## Build & sideload the watch app

```bash
./gradlew :watch:assembleDebug
# APK: watch/build/outputs/apk/debug/watch-debug.apk

# Connect the watch (see docs/platform-target.md for the full ADB-over-Wi-Fi flow):
adb pair <watch-ip>:<pairing-port>       # one-time, enter the 6-digit code
adb connect <watch-ip>:<connection-port> # each session
adb devices
adb install -r watch/build/outputs/apk/debug/watch-debug.apk
# or: ./gradlew :watch:installDebug
```
On the watch, **grant** the Bluetooth permissions and, when prompted, **disable battery optimization**
for PixelBridge.

## Phone side — Gadgetbridge (unmodified)

Install **Gadgetbridge from [F-Droid](https://f-droid.org/packages/nodomain.freeyourgadget.gadgetbridge/)**
(used **unmodified — no build, no fork**). Grant **Notification Access**, then add a **Bangle.js
device** and let it discover the advertising watch. Details in
[gadgetbridge-integration.md](gadgetbridge-integration.md).

## Verify the toolchain end-to-end

Once the SDK + JDK 21 are installed, from the repo root:
```bash
./gradlew :watch:assembleDebug --stacktrace   # should produce watch-debug.apk
```
If Gradle complains about AGP/Kotlin versions, open the project in Android Studio and accept the
**AGP Upgrade Assistant** suggestions, then re-run.
