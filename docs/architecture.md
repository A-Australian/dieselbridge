# Architecture

## Goal

Deliver Android phone notifications to a Pixel Watch Gen-1 and let the user **dismiss/reply**, over
**direct BLE only** — no Google Play Services, no Wearable Data Layer, no companion app.

## Primary topology — watch = peripheral, phone = central

```
        Android phone (unmodified Gadgetbridge)                 Pixel Watch (PixelBridge app)
   ┌───────────────────────────────────────────┐        ┌────────────────────────────────────────┐
   │ NotificationListenerService                │        │ MainActivity (Wear Compose UI)          │
   │   captures + filters notifications         │        │   renders the notification list         │
   │                │                           │        │                ▲                        │
   │                ▼                           │        │                │                        │
   │ Bangle.js DeviceSupport (BLE CENTRAL)      │        │ DieselBridgeService (connectedDevice FGS)│
   │   connects to the advertising watch        │        │   owns the BLE stack                    │
   │                │                           │        │                │                        │
   └────────────────┼───────────────────────────┘        └────────────────┼───────────────────────┘
                    │  BLE GATT · Nordic UART Service (NUS)                 │
                    │                                                       │
   phone→watch RX ──┤  GB({"t":"notify","title":…,"body":…})\n  ──────────▶│ NusGattServer (GATT SERVER)
   watch→phone TX ◀─┤  {"t":"notify","id":…,"n":"DISMISS"}\n     ──────────┤   RX write / TX notify
                    │                                                       │ NusAdvertiser advertises NUS
                    └───────────────────────────────────────────────────────┘   name "Bangle.js PixelBridge"
```

**Why this topology:**
- It's the **proven [AsteroidOS](https://wiki.asteroidos.org/index.php/BLE_profiles) model** (watch =
  advertising GATT server, phone = central that writes).
- It **co-locates notification capture + connection management + the foreground service on the
  phone**, where background execution and battery-optimization exemptions are mature — the watch's
  role stays simple and low-power (advertise + serve GATT).
- By speaking the **Bangle.js JSON-over-NUS** dialect and advertising a `Bangle.js…` name, an
  **unmodified Gadgetbridge** drives it as a *Bangle.js device* — **zero new phone-side code**
  (verified against Gadgetbridge's published Bangle.js protocol) and **no AGPL** obligation.

## Components

| Component | Where | Role | Tech |
|-----------|-------|------|------|
| **PixelBridge watch app** | this repo (`:watch`) | BLE peripheral: NUS GATT server + advertiser; parse `GB({...})`; render UI; send action back-channel; foreground service | Kotlin, Jetpack Compose for Wear OS, `android.bluetooth`, **no native libs** |
| **Gadgetbridge (unmodified)** | phone (F-Droid) | BLE central + notification source; `NotificationListenerService`; maps actions → `cancelNotification` / `RemoteInput` | Existing Bangle.js `Coordinator`/`Support` (used **as-is** — never forked or modified) |

## End-to-end data flow

1. Phone app posts a notification → Gadgetbridge `NotificationListener.onNotificationPosted` builds a
   `NotificationSpec` → routed to the Bangle.js `DeviceSupport.onNotification`.
2. Gadgetbridge writes `GB({"t":"notify","id":…,"src":…,"title":…,"body":…})\n` to the watch's **RX**.
3. Watch `NusGattServer` reassembles bytes on `\n`, strips the `0x10` DLE, parses JSON, shows it.
4. User acts on the watch → watch writes `{"t":"notify","id":…,"n":"DISMISS"}` (or `"REPLY"`,`reply`)
   to **TX** (notify) → Gadgetbridge maps DISMISS → `NotificationListenerService.cancelNotification`,
   REPLY → the notification's `Notification.Action` + `RemoteInput`.

Full wire details: [ble-protocol.md](ble-protocol.md). Gadgetbridge internals:
[gadgetbridge-integration.md](gadgetbridge-integration.md).

## Background-survival model

Long-lived BLE dies when the process is killed, and Doze defers BLE even for foreground services.
The layered mitigation (applies to whichever device is central — here, the phone):

1. **Foreground service**, `foregroundServiceType="connectedDevice"` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`
   (mandatory API 34+). The watch app also runs one (`DieselBridgeService`) for its GATT server.
2. **Battery-optimization exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — a foreground service
   alone does **not** beat Doze.
3. **`CompanionDeviceManager` association** — grants `REQUEST_COMPANION_RUN_IN_BACKGROUND` and, on
   Android 15, **restores access to "sensitive" notifications** (OTP codes) otherwise hidden from
   `NotificationListenerService`. This is how Gadgetbridge already does it.
4. **Serialize all GATT operations** through a single queue (Gadgetbridge's `BtLEQueue`; on the watch
   our server is inherently single-threaded via its callback) — unordered ops cause `status 133`.
5. **Reconnection:** bond (`createBond`, wait `BOND_BONDED`); direct `connectGatt(autoConnect=false)`
   for speed, `autoConnect=true` as passive fallback; always `close()` the old `BluetoothGatt` first
   and inspect the `status` arg in `onConnectionStateChange`.
