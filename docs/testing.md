# Testing

## Unit tests (JVM, no device)

Pure-logic classes are covered under `watch/src/test/`:
- `GbProtocolTest` — parse `GB({...})` / raw-JSON `notify` + `notify-`, unknown / `null` cases, and
  encode actions (guards that REPLY text goes in **`msg`**, not `reply`).
- `NotificationStoreTest` — newest-first, dedupe-by-id, remove, clear, 50-item cap.

`org.json` is a throw-only stub in Android unit tests, so `org.json:json` is a `testImplementation`.

```
./gradlew :watch:testDebugUnitTest
```

## Wear OS emulator

The Pixel Watch Gen-1 runs Wear OS 5.1 (API 35); use a matching emulator.

```
export ANDROID_HOME="$HOME/Library/Android/sdk"; export ANDROID_SDK_ROOT="$ANDROID_HOME"

# one-time: SDK-local cmdline-tools + the Wear OS 5.1 arm64 image + platform
sdkmanager --sdk_root="$ANDROID_HOME" "cmdline-tools;latest" \
  "system-images;android-35-ext15;android-wear;arm64-v8a" "platforms;android-35"

# create the AVD
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n WearOS51 \
  -k "system-images;android-35-ext15;android-wear;arm64-v8a" --device wearos_small_round

# boot it (headless)
"$ANDROID_HOME/emulator/emulator" -avd WearOS51 -no-window -no-audio -no-boot-anim &
adb wait-for-device
```

> **BLE is not available on the emulator** — the real Gadgetbridge ↔ watch BLE link is a
> physical-watch test. On the emulator you exercise the UI + notification rendering via the injector.

## Simulate notifications (debug injector)

Debug builds include `DebugInjectReceiver` (`src/debug`), which pushes a notification through the real
pipeline (`NotificationRouter`) — no phone, Gadgetbridge, or BLE needed. Ideal for screenshots and
manual UI checks on the emulator.

```
adb install -r watch/build/outputs/apk/debug/watch-debug.apk
adb shell am start -n net.farcaster.pixelbridge/.ui.MainActivity

# inject a notification (convenience form)
adb shell am broadcast -a net.farcaster.pixelbridge.INJECT \
  -n net.farcaster.pixelbridge/.debug.DebugInjectReceiver \
  --es app Signal --es title Alice --es body "Coffee?" --el id 42

# dismiss it
adb shell am broadcast -a net.farcaster.pixelbridge.INJECT \
  -n net.farcaster.pixelbridge/.debug.DebugInjectReceiver --el del 42

# or inject a raw Bangle.js line
adb shell am broadcast -a net.farcaster.pixelbridge.INJECT \
  -n net.farcaster.pixelbridge/.debug.DebugInjectReceiver \
  --es line 'GB({"t":"notify","id":7,"src":"X","title":"Hi","body":"yo"})'

# screenshot
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

The receiver is compiled **only into debug builds** — never shipped in release.

## Instrumented UI test (emulator or watch)

`watch/src/androidTest/.../ui/NotificationsScreenTest` seeds a notification, renders
`NotificationsScreen`, and asserts the title + **Dismiss** + **Reply** are displayed (guards the
button-layout fix). With the emulator (or a real watch) connected:

```
./gradlew :watch:connectedDebugAndroidTest
```

## Layout preview (no emulator)

`NotificationCardPreview` in `NotificationsScreen.kt` renders a sample card in Android Studio's Compose
preview — the fastest way to eyeball the card/button layout without building.
