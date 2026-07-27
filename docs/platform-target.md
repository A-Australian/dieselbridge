# Platform target — Google Pixel Watch (1st gen)

> Sources are linked inline.

## Hardware

| | |
|--|--|
| SoC | Samsung **Exynos 9110** (dual Cortex-A53 ~1.15 GHz, Mali-T720) |
| Co-processor | ARM **Cortex-M33** (always-on: AOD, background HR) |
| RAM / storage | 2 GB LPDDR4 / 32 GB eMMC |
| Bluetooth | **5.0** (BLE central *and* peripheral capable at the controller level) |
| Radios | Wi-Fi 802.11n (2.4 GHz only), NFC, optional LTE, GNSS |
| Battery | ~294 mAh |
| Charging/data | Proprietary **magnetic pogo puck — no USB data port** |

The Exynos 9110 is the same 2018-era chip as the Galaxy Watch, so **CPU/battery headroom for a
long-lived background service is modest** — keep the BLE service lightweight.
Sources: [Google spec sheet](https://support.google.com/googlepixelwatch/answer/12651869),
[Wikipedia](https://en.wikipedia.org/wiki/Pixel_Watch), [GSMArena](https://www.gsmarena.com/google_pixel_watch-11546.php).

## OS: frozen on Wear OS 5.1 / API 35

Shipped on Wear OS 3.5 (Android 11) → Wear OS 4 (Android 13, Oct 2023) → Wear OS 5 → **Wear OS 5.1
(Android 15, API 35)**. **Last update Oct 2025; it did NOT get Wear OS 6/7.** Guaranteed support
ended ~1 Oct 2025 (3-year policy). **Treat the platform as fixed at API 35** — no newer platform API
will ever reach this watch.
Sources: [endoflife.date/pixel-watch](https://endoflife.date/pixel-watch),
[9to5google Oct 2025 update](https://9to5google.com/2025/10/27/pixel-watch-october-update/),
[Wear OS 7 excludes Gen-1](https://9to5google.com/2026/06/19/pixel-watch-wear-os-7-june-2026-update/).

**SDK targets:** `compileSdk 36`, `targetSdk 35` (36 also fine; sideloading isn't gated by Play's
target-SDK rule), `minSdk 30` (covers every Gen-1 firmware).

## BLE peripheral capability

Android exposes both roles: **central** (`BluetoothLeScanner` + `connectGatt`) and **peripheral**
(`BluetoothLeAdvertiser` + `BluetoothGattServer`, since API 21). Wear OS is full Android, so these
classes are present — and on the Pixel Watch Gen-1 the **peripheral role works**: it advertises the
NUS service and hosts a GATT server that a phone (Gadgetbridge) connects to. This is confirmed on real
hardware and is the topology PixelBridge ships.

The app still validates the role defensively at startup:

1. `BluetoothAdapter.getBluetoothLeAdvertiser() != null` (returns null if the chipset can't advertise).
2. `startAdvertising()` + `openGattServer()`, then a central connects.
3. **Do NOT gate on `isMultipleAdvertisementSupported()`** — it only reports *concurrent*
   advertisement support and is ambiguous/unreliable.

Sources: [BLE overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview),
[AOSP advertising](https://source.android.com/docs/core/connect/bluetooth/ble_advertising).

## Debugging: ADB over Wi-Fi only (no USB data port)

1. **Enable dev mode:** Settings → System → About → tap **Build number ×7**.
2. Settings → Developer options → enable **ADB debugging** *and* **Wireless debugging**.
3. Watch + Mac on the **same Wi-Fi** (corporate/AP-isolated networks block this — use a phone
   hotspot). `adb` must be ≥ 30.0.0.
4. **One-time pair:** watch → Wireless debugging → *Pair new device* shows IP + **pairing port** +
   6-digit code → `adb pair <ip>:<pairing-port>` → enter code.
5. **Each session:** read the (different) **connection port** on the Wireless debugging screen →
   `adb connect <ip>:<connection-port>` → `adb devices`.
6. Re-`connect` after every reboot / wireless-debugging toggle / Wi-Fi change. Recover with
   `adb kill-server && adb start-server`.

Source: [Debug over Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi).

## Sideloading & install-time realities

- Install with `adb install app.apk` on the **stock, locked** device (no bootloader unlock, no root).
  The bootloader *is* unlockable but only via a special pogo-pin data cable — **not needed** for us.
- Must be a **standalone** Wear OS APK (self-contained; `com.google.android.wearable.standalone` =
  true), which this app is.
- **ABI:** ⚠️ **Do NOT ship an arm64-only native lib.** Community reports show arm64-only native code
  can fail to install (`INSTALL_FAILED_NO_MATCHING_ABIS`). Our **app code has no native libs**; the
  only transitive `.so` (`androidx.graphics.path`, via Wear Compose) ships **all four ABIs**
  (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` — verified in the built APK), so it installs on the
  watch. Never add an arm64-only NDK dependency.
- **Play Protect:** ⚠️ Sideloading does **not** bypass Play Protect. Expect a scan and possibly a
  warning; handle it in onboarding.

Sources: [sideloading Wear OS](https://www.howtogeek.com/how-to-sideload-apps-on-your-wear-os-smartwatch/),
[Pixel Watch bootloader](https://9to5google.com/2022/10/21/pixel-watch-bootloader-unlock-cable/).

## Background execution (why the design looks the way it does)

Wear OS applies Doze + App-Standby + FGS-launch limits. A persistent BLE link **must** be a
**foreground service** with:

- `android:foregroundServiceType="connectedDevice"` (mandatory on API 34+),
- permissions `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`, plus
  `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT`.

Even then, deep Doze (screen off + stationary) can defer/drop BLE. The reliable mitigation is a
**user-granted battery-optimization exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and a
`CompanionDeviceManager` association. Favor `WorkManager` for deferrable work and an `OngoingActivity`
for the persistent notification; avoid manual wakelocks.
Sources: [Wear power](https://developer.android.com/training/wearables/apps/power),
[FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[BLE in background](https://developer.android.com/develop/connectivity/bluetooth/ble/background).
