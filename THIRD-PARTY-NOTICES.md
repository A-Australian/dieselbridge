# Third-party notices

PixelBridge (the watch app in this repository) is licensed under **Apache-2.0** (see [`LICENSE`](LICENSE)).
This file lists the third-party components it builds on and their licenses. **No third-party source
code is copied into this repository** — everything below is pulled in as a normal build/runtime
dependency (from Google's Maven and Maven Central) or is merely spoken to over a wire protocol.

## Runtime / shipped dependencies (bundled into the APK)

All of the following are **Apache License 2.0**:

- **Kotlin** standard library and `org.jetbrains.kotlinx:kotlinx-coroutines-android` — JetBrains
- **AndroidX** libraries — the Android Open Source Project / Google:
  - `androidx.core:core-ktx`
  - `androidx.lifecycle:lifecycle-service`, `androidx.lifecycle:lifecycle-runtime-compose`
  - `androidx.activity:activity-compose`
  - `androidx.compose:*` (via the Compose BOM): `androidx.compose.ui:ui`, `ui-tooling-preview`
  - `androidx.wear.compose:compose-material3`, `androidx.wear.compose:compose-foundation`
  - `androidx.wear:wear-tooling-preview`, `androidx.wear:wear-ongoing`, `androidx.wear:wear-input`
  - `androidx.wear.tiles:tiles`, `tiles-tooling-preview`
  - `androidx.wear.protolayout:protolayout`, `protolayout-material3`, `protolayout-expression`
  - `androidx.concurrent:concurrent-futures`

The only transitive native library in the APK is **`androidx.graphics.path`** (pulled in via Wear
Compose), which is Apache-2.0 and ships all four ABIs. PixelBridge itself contains no native code.

## Build / debug / test-only dependencies (NOT shipped in the APK)

- `androidx.compose.ui:ui-tooling`, `androidx.wear.compose:compose-ui-tooling`,
  `androidx.wear.tiles:tiles-tooling`, `androidx.compose.ui:ui-test-manifest` — **Apache-2.0**
  (debug only).
- `androidx.test.ext:junit`, `androidx.test:runner`, `androidx.compose.ui:ui-test-junit4` —
  **Apache-2.0** (test only).
- `junit:junit` (4.13.2) — **Eclipse Public License 1.0** (test only).
- `org.json:json` (20240303) — the **"JSON License"** (a.k.a. the "Good, not Evil" license). This is
  used **only on the JVM unit-test classpath** (Android ships its own `org.json` stub at runtime), so
  it is **not bundled into the released APK**. It is listed here for completeness.

## Build tooling

- **Gradle** and the **Gradle Wrapper** (`gradle/wrapper/gradle-wrapper.jar`) — Apache-2.0.
- **Android Gradle Plugin** (`com.android.application`) — Apache-2.0.
- **Kotlin Gradle plugin** (`org.jetbrains.kotlin.plugin.compose`) — Apache-2.0.

## Gadgetbridge (phone side — referenced, not included)

The phone side of the system is **[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge)**,
which is licensed under the **GNU AGPL-3.0**. PixelBridge uses Gadgetbridge **completely unmodified**
and installed separately (from F-Droid). **No Gadgetbridge source code is included, forked, or
distributed in this repository.** PixelBridge only communicates with Gadgetbridge across the public
Bangle.js JSON-over-Nordic-UART wire protocol, so it is a separate, independent work and carries no
AGPL obligation. See [`docs/licensing.md`](docs/licensing.md).

## Trademarks

- **"Bangle.js"** is a trademark of **Espruino / Pur3 Ltd**. PixelBridge is not affiliated with or
  endorsed by them; the name is used only to identify the wire protocol and BLE device name required
  for interoperability with Gadgetbridge's Bangle.js driver.
- **"Google", "Pixel", "Pixel Watch", "Wear OS", and "Android"** are trademarks of **Google LLC**.
  PixelBridge is an independent project and is not affiliated with or endorsed by Google.
- **"GrapheneOS"** is a project/name of the GrapheneOS developers, referenced only descriptively.
