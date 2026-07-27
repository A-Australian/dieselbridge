# PixelBridge

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

See and react to your Android phone's notifications on a **Google Pixel Watch (1st gen)** over a
**direct Bluetooth LE link** — **without Google Play Services, the Wearable Data Layer, or the Pixel
Watch companion app.**

The phone side is **unmodified [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge)**.
The watch runs a small standalone Wear OS app (this repo) that acts as a BLE **peripheral** speaking
the **Bangle.js JSON-over-Nordic-UART** protocol, so Gadgetbridge — set up as a *Bangle.js device* —
pushes notifications to it with **zero new phone-side code**.

```
┌─────────────────────────┐        BLE GATT (Nordic UART Service)        ┌──────────────────────────┐
│ Android phone           │  ── GB({"t":"notify","title":…,"body":…}) ─▶ │ Pixel Watch (Wear OS 5.1)│
│ Gadgetbridge (unchanged)│                                              │ PixelBridge app          │
│  • NotificationListener │  ◀─ {"t":"notify","id":…,"n":"DISMISS"} ───  │  • NUS GATT server        │
│  • BLE central          │                                              │  • Wear Compose UI        │
└─────────────────────────┘                                              └──────────────────────────┘
       no Google Play Services · no Wearable Data Layer · no companion app · native BLE only
```

## Status

**v1.0.0 — working end-to-end on real hardware.** A phone notification reaches the watch (native card
+ vibration + in-app list), and you can act on it from the wrist — all with no Google services:

- **Notifications** with **Reply / Dismiss / Clear-all** (Reply appears only on replyable
  notifications).
- The watch's **battery** and **version** reported to Gadgetbridge; **find-my-watch** (full-screen
  alarm + sound), a **Find phone** button, and **rotary-crown** scrolling.
- A full-screen **incoming-call screen** (Answer / Decline / End), **music control** (now-playing +
  transport + volume), and **canned quick-replies**.
- Two glanceable **Wear OS Tiles** — a notifications digest and a music tile.

See [`CHANGELOG.md`](CHANGELOG.md) for the full feature list.

## What you need

- **Google Pixel Watch (1st gen)** running Wear OS 5.1 (API 35).
- **An Android phone** (to run Gadgetbridge) — the phone and watch talk over Bluetooth.
- **A computer with the Android SDK, JDK 21, and `adb`** to build and sideload. Full install list in
  [`docs/dev-environment.md`](docs/dev-environment.md) (macOS Apple-Silicon walkthrough).
- The watch and your computer on the **same Wi-Fi** (the watch has no USB data port — ADB is Wi-Fi
  only).

## Setup

### 1. Build the watch app

```bash
git clone <this-repo> && cd PixelBridge
cp local.properties.example local.properties      # set sdk.dir, or let Android Studio write it
./gradlew :watch:assembleDebug
# → watch/build/outputs/apk/debug/watch-debug.apk
```
Toolchain (verified): AGP 9.0.1 / Gradle 9.1.0 (wrapper) / Kotlin 2.2.0 / JDK 21 / compileSdk 36.
Open in Android Studio once to install the SDK if you haven't. Details: [`docs/dev-environment.md`](docs/dev-environment.md).

### 2. Connect the watch over ADB (Wi-Fi)

On the watch:
1. **Settings → System → About → tap "Build number" 7×** to unlock Developer options.
2. **Settings → Developer options →** enable **ADB debugging** *and* **Wireless debugging**.
3. Open **Wireless debugging → Pair new device with pairing code** — note the `IP:port` and 6-digit code.

On the computer:
```bash
adb pair 192.168.x.y:<pairing-port>       # one-time; enter the 6-digit code
adb connect 192.168.x.y:<connection-port> # each session; the port is on the MAIN Wireless-debugging screen
adb devices                               # confirm it shows as "device"
```
> The **connection port differs from the pairing port**, and Wear OS **rotates the connection port**
> after sleep/reboot — re-read it and `adb connect` again if the link drops.

### 3. Install (sideload)

```bash
adb install -r watch/build/outputs/apk/debug/watch-debug.apk
# If "more than one device/emulator": target the watch explicitly, e.g.
# adb -s 192.168.x.y:<port> install -r watch/build/outputs/apk/debug/watch-debug.apk
```

### 4. Watch: permissions, Bluetooth, battery

1. **Launch PixelBridge** on the watch and **grant the prompts**: **Nearby devices** (Bluetooth) and
   **Notifications**.
2. **Enable Bluetooth on the watch** — it defaults **off** on a watch never paired via the companion
   app (`adb shell settings get global bluetooth_on` should read `1`; the app shows "Bluetooth is off"
   if not).
3. **Battery-optimization exemption** so Doze doesn't drop the link. Wear OS 5.1 has **no in-app
   dialog** for this, so grant it over adb:
   ```bash
   adb shell dumpsys deviceidle whitelist +net.farcaster.pixelbridge
   ```
   When done, the watch shows `● <phone>` (connected) and no "Battery not exempt" banner.

The watch advertises its Bluetooth name as **`Bangle.js PixelBridge`** (so Gadgetbridge recognizes it).

### 5. Phone: set up Gadgetbridge

1. Install **Gadgetbridge** from **[F-Droid](https://f-droid.org/packages/nodomain.freeyourgadget.gadgetbridge/)**
   (unmodified — no build needed).
2. Open it and **grant Notification Access** when prompted (or Android **Settings → Apps → Special app
   access → Notification access → Gadgetbridge**). Without this it captures nothing.
3. **＋ / Connect new device →** let it scan → pick **`Bangle.js PixelBridge`** (a *Bangle.js* device) →
   connect. The watch header flips to **`● <phone>`**.
4. **Gadgetbridge → Settings → Notifications → enable "Send notifications when the screen is on"** —
   otherwise notifications are only forwarded while the phone screen is off.

### 6. Verify

Send yourself a message (or any app notification). The watch should **buzz** and show a **native
notification card** plus an entry in the PixelBridge list. Tap **Dismiss** (clears it on the phone) or
**Reply** (Wear voice/keyboard → posts in the conversation).

## Using it

- Incoming notifications appear as **system notifications** (buzz + native card, even when PixelBridge
  is backgrounded) and in the **in-app list**. Scroll the list with the **rotary crown**.
- Each card has stacked, full-width **Reply / Dismiss** buttons. Reply opens Wear's voice/keyboard
  input and only shows when the notification is replyable (Gadgetbridge's `reply` flag); Dismiss acts
  on the notification on the phone via Gadgetbridge. The header has **Find phone** (rings the phone)
  and **Clear all** (dismisses the watch list).
- The status header shows the watch **battery** (🔋/⚡ + %); Gadgetbridge's device card also shows it.
- **Find-my-watch:** trigger Gadgetbridge's **Find Device** and the watch shows a full-screen alarm
  and buzzes/rings until you stop it (from the watch, from Gadgetbridge, or after ~60 s).
- **Incoming calls:** a call on the phone rings/buzzes the watch and posts a call notification with
  **Answer / Decline** buttons (tap the card to open the full call screen; **End** hangs up an active
  call). The phone handles the call audio — the watch is the remote. Silent/vibrate ringer → buzz
  only. *Requires Gadgetbridge's **Phone** permission so it relays call state* — without it, calls
  arrive as ordinary notifications.
- **Music:** while media plays on the phone, a **Now playing** card shows the track with **⏮ ▶/⏸ ⏭**
  and **🔉 / 🔊**.
- **Canned replies:** configure quick replies in **Gadgetbridge's per-device settings**; they then
  appear as tap-to-send choices in the watch's reply picker.
- **Tiles:** add PixelBridge tiles (long-press the watch face → Tiles → +): a **notifications** tile
  (a count + the latest few, tap **Open** for the full list + actions) and a **music** tile
  (now-playing + a transport row **⏮ ▶/⏸ ⏭** and a volume row **🔉 🔊**).
- **Heads-up/vibration only fire while the watch is on your wrist** — Wear suppresses haptics and
  heads-ups while it's on the charger. Calls buzz through a muted ringer by design (alarm-usage).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Connected but **no notifications arrive** | Grant Gadgetbridge **Notification Access**; enable **"send when screen on"** (or lock the phone). |
| **Nothing after reinstalling / restarting the watch app** | Gadgetbridge holds a **stale connection** — **disconnect + reconnect** the device in Gadgetbridge (the watch shows an "Open Gadgetbridge to connect" hint). |
| Watch screen shows **"Bluetooth is off"** | Turn Bluetooth on (`adb shell svc bluetooth enable`, or the BT quick-settings tile). |
| `adb: device offline` / `connection refused` | Wear OS rotated the wireless-debug port — re-read it on the watch and `adb connect` again. |
| `more than one device/emulator` | Same watch on two transports — target it: `adb -s <ip:port> …`. |
| Reply shows **"(Google canned reply)"** in the chat | Added by Molly/Signal downstream, not by PixelBridge. |
| Link drops when the watch sleeps (dev) | Keep the watch on its charger / screen-on while developing. |

## Development

Common tasks are wrapped in a **Makefile** — run `make help`:

```bash
make build        # build the debug APK
make test         # JVM unit tests
make test-ui      # Compose UI test (needs a device/emulator)
make check        # lint + unit tests
make run          # install + launch on the connected device/emulator
make emulator-setup   # one-time: install the Wear OS 5.1 image + create the AVD
make emulator     # boot the Wear OS 5.1 emulator (headless)
make inject TITLE=Alice BODY="Coffee?" APP=Signal   # simulate a notification (debug builds only)
make screenshot   # pull a screenshot from the device
make release VERSION=x.y.z   # verify, tag, push, and publish the APK as a GitHub release
```

Emulator + notification-injection testing (no phone/BLE needed) is in
[`docs/testing.md`](docs/testing.md); full toolchain setup is in
[`docs/dev-environment.md`](docs/dev-environment.md). Release history:
[`CHANGELOG.md`](CHANGELOG.md).

## How it works

Watch = BLE **peripheral** hosting the **Nordic UART Service**; phone = **Gadgetbridge** as BLE
**central**, driving it with its existing Bangle.js `GB({...})` JSON protocol. Full details:

- **Architecture & constraints:** [`docs/architecture.md`](docs/architecture.md)
- **Dev environment:** [`docs/dev-environment.md`](docs/dev-environment.md)
- **BLE wire protocol:** [`docs/ble-protocol.md`](docs/ble-protocol.md) (UUIDs, framing, MTU chunking, CRLF, actions)
- **Gadgetbridge integration & the "no Google" constraint:** [`docs/gadgetbridge-integration.md`](docs/gadgetbridge-integration.md), [`docs/no-google-constraint.md`](docs/no-google-constraint.md)

## Why

This is a personal project, built to solve a personal problem: pairing a 1st-gen Pixel Watch with a
**de-googled [GrapheneOS](https://grapheneos.org/) phone**. GrapheneOS has **no Google Play Services**,
so the standard Wear OS notification path (Play Services + the Pixel Watch companion app) simply
doesn't work — and the 1st-gen watch is EOL anyway (frozen on Wear OS 5.1, no updates after Oct 2025).

PixelBridge keeps the watch useful as a **private, Google-free notification display**, using only
stock `android.bluetooth` on the watch and **unmodified Gadgetbridge** on the phone. It works with any
Android phone that can run Gadgetbridge, not just GrapheneOS.

Built by **Christoph Lühr** with **[Claude Code](https://www.anthropic.com/claude-code)**.

## License

The watch app is **Apache-2.0** — a separate, independent implementation that talks to Gadgetbridge
only across the public Bangle.js wire protocol (no Gadgetbridge source is copied here). See
[`LICENSE`](LICENSE), [`docs/licensing.md`](docs/licensing.md), and
[`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).
