# The "no Google" constraint & prior art

The hard requirement: notifications reach the watch over **direct native BLE**, using **none** of
Google's transports.

## What must NOT be used (and why)

| Forbidden | What it is | Why avoid |
|-----------|-----------|-----------|
| **Wearable Data Layer API** (`DataClient`/`MessageClient`/`ChannelClient`) | Part of **Google Play Services** (`com.google.android.gms:play-services-wearable`); a single GMS-managed encrypted BT channel | It's the standard Google transport we're explicitly replacing. Requires same package + signature on both ends. Google **forbids** bypassing it "with low-level sockets" — but that caution is **scoped to the Data-Layer channel only.** |
| **Notification bridging** | Platform/GMS auto-shares phone notifications to the watch | Performed by the GMS Wearable stack + the **Google Pixel Watch companion app** (`com.google.android.apps.wear.companion`) |
| **Companion pairing flow** | The standard Pixel Watch setup | Requires a Google account + the companion app |

**Sources:** [Data Layer overview](https://developer.android.com/training/wearables/data/overview),
[bridging](https://developer.android.com/training/wearables/notifications/bridger),
[setup requires account](https://support.google.com/googlepixelwatch/answer/12651780).

## What IS allowed (and confirmed)

- A Pixel Watch is **full Android**. A **standalone**, sideloaded app can open its **own
  `android.bluetooth` GATT** connection **independent of the Data Layer** — Google's "no low-level
  sockets" rule applies only to the phone↔watch *Data-Layer* channel, and Google itself permits
  standalone apps to do direct I/O. (Consistent with Google's own documentation.)
- The phone captures notifications with **`NotificationListenerService`** — **no Play Services needed**
  (this is exactly what Gadgetbridge does).

## Prior art

- **Gadgetbridge** — the reference architecture: a FOSS, **GMS-free** app whose
  `NotificationListenerService` captures notifications and pushes them over BLE to sideloaded-firmware
  watches (PineTime/InfiniTime, Bangle.js, Amazfit, Pebble). We reuse it unchanged.
  [gadgetbridge.org](https://gadgetbridge.org/)
- **AsteroidOS** — direct precedent for the *topology*: watch = advertising GATT-server peripheral,
  phone (AsteroidOSSync) = central that writes structured notifications. No Google stack.
  [BLE profiles](https://wiki.asteroidos.org/index.php/BLE_profiles)
- **Bangle.js / Espruino** — the *protocol* we reuse: JSON wrapped as `GB({...})` over NUS. Any device
  exposing NUS + JSON is supported, so a Wear OS NUS GATT server is protocol-compatible.
  [Espruino Gadgetbridge](https://www.espruino.com/Gadgetbridge)
- **Gadgetbridge issue [#3257](https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/3257)** —
  "general Wear OS support" is **proposed but unimplemented** ("help wanted"; blocker = reverse-
  engineering a new platform). So **nobody currently drives a Pixel Watch this way** — PixelBridge's
  novel piece is the standalone Wear OS app that *receives* the BLE push. We sidestep the reverse-
  engineering by making the watch speak an existing protocol Gadgetbridge already supports.

## The motivating setup: a de-googled phone + a Google-free watch

PixelBridge exists to make a 1st-gen Pixel Watch useful next to a **de-googled
[GrapheneOS](https://grapheneos.org/) phone** — which has **no Google Play Services at all**, so the
standard Wear OS notification path (Play Services + the Pixel Watch companion app) simply isn't
available. The confirmed, working configuration is:

- **Phone:** GrapheneOS (or any Android without GMS), running **unmodified Gadgetbridge** from F-Droid
  — notifications are captured by `NotificationListenerService`, no Google account or Play Services
  required.
- **Watch:** a Pixel Watch on stock Wear OS with PixelBridge sideloaded, used **without** the Google
  Pixel Watch companion app or the Wearable Data Layer. It boots far enough to enable ADB, sideload,
  grant Notification Access + BLE permissions, and hold a BLE link **with no companion app in the
  loop** — validated on real hardware.
