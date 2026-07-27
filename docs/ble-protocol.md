# BLE protocol spec

The watch is the **peripheral / GATT server**. The phone (Gadgetbridge, BLE central) writes to RX and
subscribes to TX. The wire format is Gadgetbridge's **Bangle.js JSON-over-Nordic-UART** dialect, so an
**unmodified Gadgetbridge** speaks it. Constants live in `watch/.../ble/BleUuids.kt` — **keep this doc
and that file in sync.**

## Transport: Nordic UART Service (NUS)

| Attribute | UUID | Properties | Direction |
|-----------|------|------------|-----------|
| **NUS service** | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | primary service | — |
| **RX** | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` | `WRITE` / `WRITE_NO_RESPONSE` | phone → watch |
| **TX** | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | `NOTIFY` | watch → phone |
| **CCCD** | `00002902-0000-1000-8000-00805f9b34fb` | descriptor on TX | phone subscribes |

The phone enables TX notifications by writing `ENABLE_NOTIFICATION_VALUE` to the CCCD.

**Advertising:** the watch advertises the NUS service UUID (primary packet) and a device name
matching Gadgetbridge's Bangle.js coordinator regex **`Bangle\.js.*`** — we use
**`Bangle.js PixelBridge`**, carried in the scan-response packet (a 128-bit UUID + a 21-char name
won't both fit in one 31-byte advertisement).

## Framing

- **phone → watch:** bytes are `0x10` + `"GB("` + `<JSON>` + `")"` + `"\n"`. The leading **`0x10`**
  is Espruino's DLE/echo-off control byte — **strip it**. The shorthand `GB({...})` omits it.
- **watch → phone:** raw JSON terminated with **`\r\n`** (CRLF), one object per line. ⚠️ Gadgetbridge's
  line splitter does `substring(0, p-1)` on the `\n` index (it expects a trailing `\r`), so a bare
  `\n` would eat your closing `}` → "Malformed JSON". Gadgetbridge parses any line beginning with `{`.
- NUS is an **opaque byte stream** → **reassemble by the `\n` delimiter, never by packet boundary.**
  The GATT server buffers inbound RX bytes and only parses a message when it sees `0x0A`. Our
  `NusGattServer` does exactly this.

## MTU & chunking

- Default ATT MTU is **23 bytes** (20 usable). The central negotiates via `requestMtu` (max 517) and
  reads `onMtuChanged`; Gadgetbridge's Bangle.js driver requests **MTU 131** when `allowHighMTU` is
  on, otherwise **chunks at 20 bytes**.
- **Never assume a notification fits one packet.** On the watch, buffer + split on `\n`. On outbound,
  emit each JSON object followed by `\n` and let the stack/chunking carry it.
- Use **NOTIFY** (unacknowledged) for the notification firehose; use write-with-response / indicate
  only where delivery must not drop.

## Message set (JSON, `t` = type)

### Inbound (phone → watch) — ✅ = implemented
- ✅ `{"t":"notify","id":…,"src":…,"title":…,"subject":…,"body":…,"sender":…,"reply":<bool>?}` — show/create.
  `reply:true` (Gadgetbridge 0.80+) means the source notification has a `RemoteInput`; the watch shows
  the **Reply** action only when it's present. Cards are always dismissible.
- ✅ `{"t":"notify-","id":…}` — dismiss on the watch
- ✅ `{"t":"find","n":<boolean>}` — start (`true`) / stop (`false`) the full-screen find-my-watch
  alert. Gadgetbridge's *Find Device* sends this; `n` is a JSON **boolean**.
- ✅ `{"t":"vibrate","n":<int>}` — single buzz (handled defensively; Espruino sends it, Gadgetbridge
  does not).
- ✅ `{"t":"call","cmd":"incoming|outgoing|accept|start|end|reject|ignore","name":…,"number":…}` —
  full-screen call screen (ring on `incoming`, In-call on `accept`/`start`, clear on `end`/`reject`/`ignore`).
- ✅ `{"t":"musicinfo","artist":…,"album":…,"track":…,"dur":<ms>,"c":<count>,"n":<nr>}` — now-playing metadata.
- ✅ `{"t":"musicstate","state":"play|pause|stop|","position":…,"shuffle":…,"repeat":…}` — transport (empty/`stop` clears).
- ✅ `{"t":"canned_responses_sync","d":[{"text":…,"disp":…?}]}` — synced quick-reply choices.
- later: `{"t":"alarm",…}`, `{"t":"weather",…}`

### Outbound (watch → phone) — the action back-channel — ✅ = implemented
- ✅ `{"t":"notify","id":…,"n":"DISMISS"|"DISMISS_ALL"|"REPLY","msg":"<text>"}`
  ⚠️ The reply text goes in **`msg`** (not `reply`) — Gadgetbridge's `handleNotificationControl`
  reads `json.getString("msg")` and maps `id`→the stored RemoteInput handle for REPLY. Gadgetbridge
  upper-cases `n` and does `Event.valueOf(n)`. The watch UI emits only these three; Gadgetbridge also
  accepts `OPEN`/`MUTE` if ever sent, but the notification card no longer exposes them.
- ✅ `{"t":"status","bat":<0-100>,"volt":<double>,"chg":<0|1>}` — battery (on subscribe + on change).
- ✅ `{"t":"ver","fw":"<app-version>","hw":"<Build.MODEL>"}` — version handshake (on subscribe).
- ✅ `{"t":"findPhone","n":<boolean>}` — ring the phone (`n` a JSON **boolean**).
- ✅ `{"t":"call","n":"ACCEPT|REJECT|IGNORE|END"}` — call control (`Event.valueOf(n.uppercase())`).
- ✅ `{"t":"music","n":"play|pause|next|previous|volumeup|volumedown"}` — media remote (lowercase).

**Canonical examples**
```
phone→watch:  GB({"t":"notify","id":1575479849,"src":"Signal","title":"Alice","body":"hi"})
phone→watch:  GB({"t":"notify-","id":1575479849})
watch→phone:  {"t":"notify","id":1575479849,"n":"DISMISS"}
```

## Actions on the phone side (no Google stack)

Gadgetbridge maps a watch-sent **DISMISS** → `NotificationListenerService.cancelNotification(key)`
and **REPLY** → the notification's `Notification.Action` + `RemoteInput` `PendingIntent` — full
dismiss/reply parity on stock Android.

## Correlation, bonding, robustness

- **Always carry and echo the notification `id`.** `NotificationListener` doesn't always set stable
  `NotificationSpec` IDs, so the watch keys its UI and back-channel off the `id` it received (the
  Bangle.js dialect already uses `id`).
- **Bonding** is optional for NUS data (no encryption required) but makes
  `connectGatt(autoConnect=true)` reconnection durable across BT-cache clears. Choose the
  coordinator's bonding style (`NONE` / `ASK`) accordingly.
- **Serialize GATT ops** through one queue to avoid `status 133`.

## Sources

[Bangle.js protocol (Gadgetbridge)](https://gadgetbridge.org/internals/specifics/banglejs-protocol/) ·
[Espruino Gadgetbridge protocol](https://github.com/espruino/EspruinoDocs/blob/master/info/Gadgetbridge.md) ·
[Nordic UART Service](https://docs.nordicsemi.com/bundle/ncs-3.2.0/page/nrf/libraries/bluetooth/services/nus.html) ·
[Android BLE transfer](https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data) ·
[Android BLE guide (Punch Through)](https://punchthrough.com/android-ble-guide/)
