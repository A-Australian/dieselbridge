# Gadgetbridge integration

[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) is a FOSS Android app that captures
notifications and pushes them to gadgets over BLE **without Google Play Services**. It is our phone
side, used **completely unmodified** — we never fork, patch, re-brand, or add a custom device driver
to it. The watch simply speaks a protocol Gadgetbridge already supports.

## How it works (zero phone-side code)

Gadgetbridge's **Bangle.js driver connects (as BLE central) to any peripheral that exposes the Nordic
UART Service and speaks the `GB({...})` JSON dialect.** So if our watch:

1. hosts an **NUS GATT server** (`6E400001/2/3-…`), and
2. advertises a name matching the Bangle.js coordinator regex **`Bangle\.js.*`** (we use
   `Bangle.js PixelBridge`), and
3. parses/emits the JSON message set ([ble-protocol.md](ble-protocol.md)),

then **stock Gadgetbridge drives it** — configured as a *Bangle.js* device. **No new Gadgetbridge Java,
no fork, no AGPL obligation** (verified against Gadgetbridge's published Bangle.js protocol).

**Setup:** install Gadgetbridge from **F-Droid**, grant **Notification Access**, add a **Bangle.js**
device, and let it discover the watch. Full step-by-step is in the [README](../README.md).

### How notifications flow inside Gadgetbridge
`externalevents/NotificationListener` (extends `NotificationListenerService`, needs
`BIND_NOTIFICATION_LISTENER_SERVICE` + user-granted Notification Access) → filters → builds
`model/NotificationSpec` (title/body/sender/type + `attachedActions`) →
`GBApplication.deviceService().onNotification(spec)` → `DeviceCommunicationService` → the active
`DeviceSupport.onNotification(spec)` → the Bangle.js driver emits `{"t":"notify",…}` over NUS. Removals
go `onNotificationRemoved` → `onDeleteNotification(id)` → `{"t":"notify-",…}`.

### Actions back
Watch-sent `DISMISS` → `NotificationListenerService.cancelNotification(key)`; `REPLY` → the
notification's `Notification.Action` + `RemoteInput` (reply text carried in the **`msg`** field). ⚠️
`NotificationSpec` IDs aren't always stable — **carry/echo the `id`** (the Bangle.js dialect already
does). Wire details in [ble-protocol.md](ble-protocol.md).

## Sources
[Bangle.js protocol](https://gadgetbridge.org/internals/specifics/banglejs-protocol/) ·
[Notifications feature](https://gadgetbridge.org/basics/features/notifications/) ·
[Gadgetbridge Bangle.js driver source](https://codeberg.org/Freeyourgadget/Gadgetbridge/src/branch/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/banglejs)
(AGPLv3 — for protocol reference; no code copied here)
