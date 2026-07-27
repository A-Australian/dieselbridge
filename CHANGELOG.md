# Changelog

All notable changes to PixelBridge. Loosely follows [Keep a Changelog](https://keepachangelog.com/);
versions map to git tags and GitHub releases.

## [1.0.0] — 2026-07-27

First public release. A standalone Wear OS app for the 1st-gen Google Pixel Watch that displays and
acts on Android phone notifications over a **direct BLE link**, driven by **unmodified Gadgetbridge** —
**no Google Play Services, no Wearable Data Layer, no Pixel Watch companion app.**

### Notifications
- Phone notifications appear on the watch as native cards (with vibration) and in an in-app list;
  scroll with the rotary crown.
- Act on them from the wrist: **Reply** (voice/keyboard, or a canned quick-reply synced from the
  phone; shown only when the notification is replyable), **Dismiss**, and **Clear all** — all applied
  on the phone via Gadgetbridge.

### Watch ↔ phone
- **Battery level** and a **version handshake** reported to Gadgetbridge.
- **Find-my-watch** — a full-screen alarm with sound + vibration (rings even under Do-Not-Disturb),
  and a **Find phone** button that rings the phone.
- **Incoming-call screen** — Answer / Decline for incoming calls, End for an ongoing one; the phone
  handles the call audio, the watch is the remote.
- **Music control** — a now-playing card with transport (⏮ ▶/⏸ ⏭) and volume.

### Wear OS Tiles
- A **notifications** tile (a count + the latest few, with an Open button) and a **music** tile
  (now-playing + transport + volume rows) — built on androidx tiles/protolayout Material 3, no GMS.

### Under the hood
- Inbound text decoded as ISO-8859-1 to match Gadgetbridge's Bangle.js UART (umlauts etc. render
  correctly).
- All GATT operations serialized through a single queue for reliability.
- JVM unit tests, an instrumented Compose UI test, and a Wear OS 5.1 emulator setup.
