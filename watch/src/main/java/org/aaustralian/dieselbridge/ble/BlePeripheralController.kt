// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ble

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import org.aaustralian.dieselbridge.BuildConfig
import org.aaustralian.dieselbridge.data.NotificationActions
import org.aaustralian.dieselbridge.data.NotificationStore
import org.aaustralian.dieselbridge.notify.NotificationRouter
import org.aaustralian.dieselbridge.notify.WatchNotifier
import org.aaustralian.dieselbridge.protocol.GbMessage
import org.aaustralian.dieselbridge.protocol.GbProtocol

/**
 * Owns the BLE peripheral stack: capability probe -> open GATT server -> advertise, and routes
 * inbound `GB({...})` lines into [NotificationStore].
 *
 * Idempotent and Bluetooth-aware: [start] is a no-op while Bluetooth is off, and the service's
 * Bluetooth-state receiver calls [onBluetoothStateOn]/[onBluetoothStateOff] so advertising begins
 * automatically once BT is enabled (handles the "BT off at startup" case).
 *
 * BLUETOOTH_CONNECT is requested at runtime in MainActivity, hence @SuppressLint (matches NusGattServer).
 */
@SuppressLint("MissingPermission")
class BlePeripheralController(private val context: Context) {
    private var advertiser: NusAdvertiser? = null
    private var gattServer: NusGattServer? = null
    private var running = false
    private val notifier = WatchNotifier(context)
    private val router = NotificationRouter(context, notifier)

    // Last battery snapshot pushed to the phone; used to suppress duplicate `status` lines.
    @Volatile
    private var lastSentPct = -1

    @Volatile
    private var lastSentChg = -1

    fun start() {
        if (running) return
        val caps = CapabilityProbe.run(context)
        ProbeStateHolder.update {
            it.copy(
                bluetoothOn = caps.bluetoothOn,
                advertiserAvailable = caps.advertiserAvailable,
                multipleAdvSupported = caps.multipleAdvSupported,
            )
        }
        if (!caps.bluetoothOn) {
            ProbeStateHolder.log("Bluetooth OFF — advertising will start automatically when it turns on")
            return
        }
        if (!caps.advertiserAvailable) {
            ProbeStateHolder.log("FAIL: no BLE advertiser — this watch cannot be a peripheral")
            return
        }

        val server = NusGattServer(
            context,
            onLine = ::onRxLine,
            onStateChange = ::publishServerState,
            onSubscribed = ::onSubscribed,
        )
        val opened = server.open()
        gattServer = server
        ProbeStateHolder.update { it.copy(gattServerOpen = opened) }

        val adv = NusAdvertiser(context)
        advertiser = adv
        adv.start { success, error ->
            ProbeStateHolder.update { it.copy(advertising = success, advertiseError = error) }
            ProbeStateHolder.log(
                if (success) "advertising as '${BleUuids.ADVERTISED_NAME}'" else "advertise FAIL: $error",
            )
        }
        running = true
    }

    fun stop() {
        advertiser?.stop()
        gattServer?.close()
        advertiser = null
        gattServer = null
        running = false
    }

    /**
     * Watch -> phone action (dismiss / reply / open). Sent as raw newline-terminated JSON over the
     * NUS TX characteristic; Gadgetbridge maps DISMISS -> cancelNotification and REPLY -> RemoteInput.
     * On DISMISS we also clear it locally (optimistic; the phone's `notify-` round-trip is idempotent).
     */
    fun sendAction(id: Long, action: String, reply: String?): Boolean {
        val line = GbProtocol.encodeAction(id, action, reply)
        val sent = gattServer?.sendLine(line) ?: false
        Log.i(TAG, "action TX: $line (sent=$sent)")
        ProbeStateHolder.log("action $action #$id (sent=$sent)")
        if (action == NotificationActions.ACTION_DISMISS) {
            NotificationStore.remove(id)
            notifier.cancel(id)
        }
        if (action == NotificationActions.ACTION_DISMISS_ALL) {
            // Cancel only the cards we posted (never NotificationManagerCompat.cancelAll()).
            NotificationStore.items.value.forEach { notifier.cancel(it.id) }
            NotificationStore.clear()
        }
        return sent
    }

    // NOTE: these watch->phone sends do NOT gate on isNotifyEnabled. That flag goes stale across
    // Gadgetbridge reconnects (it holds the link but our tracked subscription resets), which
    // silently swallowed Answer/Decline/music taps. sendLine() already no-ops when no central is
    // connected, and the stack harmlessly drops a notify if the central isn't subscribed — exactly
    // how the dismiss/reply path (sendAction) has always worked reliably.

    /** Watch -> phone: ask Gadgetbridge to ring the phone (findPhone). */
    fun sendFindPhone(active: Boolean): Boolean {
        val sent = gattServer?.sendLine(GbProtocol.encodeFindPhone(active)) ?: false
        Log.i(TAG, "findPhone TX: active=$active (sent=$sent)")
        return sent
    }

    /** Watch -> phone: telephony action (accept / reject / ignore / end). */
    fun sendCall(action: String): Boolean {
        val line = GbProtocol.encodeCall(action)
        val sent = gattServer?.sendLine(line) ?: false
        Log.i(TAG, "call TX: $line (sent=$sent)")
        ProbeStateHolder.log("call $action (sent=$sent)")
        return sent
    }

    /** Watch -> phone: music control (play / pause / next / previous / volumeup / volumedown). */
    fun sendMusic(cmd: String): Boolean {
        val line = GbProtocol.encodeMusic(cmd)
        val sent = gattServer?.sendLine(line) ?: false
        Log.i(TAG, "music TX: $cmd (sent=$sent)")
        ProbeStateHolder.log("music $cmd (sent=$sent)")
        return sent
    }

    /**
     * Watch -> phone: activity sample (heart rate / steps), sourced from [org.aaustralian.dieselbridge.health.HealthDataProvider].
     * No local-state side effects (unlike sendAction's optimistic dismiss) - this is pure telemetry.
     * Not logged via ProbeStateHolder.log at Log.i level to avoid flooding the debug log, since
     * samples can arrive frequently; bump to Log.i only if debugging the health pipeline itself.
     */
    fun sendActivity(timestampMs: Long, heartRateBpm: Int?, steps: Int?): Boolean {
        val line = GbProtocol.encodeActivity(timestampMs, heartRateBpm, steps)
        val sent = gattServer?.sendLine(line) ?: false
        Log.d(TAG, "act TX: $line (sent=$sent)")
        return sent
    }

    /**
     * Battery broadcast callback from the service. Mirrors the level/charging into [ProbeStateHolder]
     * and pushes a `status` line when the value changed (dedup against [lastSentPct]/[lastSentChg]);
     * the BLE send no-ops if no central is connected.
     */
    fun onBatteryUpdate(s: BatteryStatus) {
        ProbeStateHolder.update { it.copy(batteryPct = s.percent, charging = s.charging == 1) }
        if (s.percent == lastSentPct && s.charging == lastSentChg) return
        lastSentPct = s.percent
        lastSentChg = s.charging
        sendStatus(s.percent, s.volts, s.charging)
    }

    fun onBluetoothStateOn() {
        ProbeStateHolder.update { it.copy(bluetoothOn = true) }
        ProbeStateHolder.log("Bluetooth ON")
        start()
    }

    fun onBluetoothStateOff() {
        stop()
        ProbeStateHolder.update {
            it.copy(
                bluetoothOn = false,
                advertising = false,
                gattServerOpen = false,
                centralConnected = false,
                notifySubscribed = false,
            )
        }
        ProbeStateHolder.log("Bluetooth OFF — advertising stopped")
    }

    private fun onRxLine(line: String) {
        Log.i(TAG, "RX: $line")
        ProbeStateHolder.update { it.copy(bytesReceived = it.bytesReceived + line.length, lastLine = line) }
        when (val msg = router.handle(line)) {
            is GbMessage.Notify -> ProbeStateHolder.log("notify #${msg.id}: ${msg.title ?: msg.body ?: ""}")
            is GbMessage.NotifyDelete -> ProbeStateHolder.log("dismiss #${msg.id}")
            is GbMessage.Find -> ProbeStateHolder.log("find " + (if (msg.active) "start" else "stop"))
            is GbMessage.Vibrate -> ProbeStateHolder.log("vibrate n=" + msg.n)
            is GbMessage.Call -> ProbeStateHolder.log("call " + msg.cmd + " " + (msg.name ?: msg.number ?: ""))
            is GbMessage.MusicInfo -> ProbeStateHolder.log("musicinfo " + (msg.track ?: ""))
            is GbMessage.MusicState -> ProbeStateHolder.log("musicstate " + msg.state)
            is GbMessage.CannedResponses -> ProbeStateHolder.log("canned x" + msg.list.size)
            is GbMessage.Other -> ProbeStateHolder.log("msg t=${msg.type}")
            null -> ProbeStateHolder.log("unparsed: ${line.take(40)}")
        }
    }

    /** CCCD rising-edge callback: greet the freshly-subscribed central with version + battery. */
    private fun onSubscribed() {
        sendVer()
        pushBatteryStatus()
    }

    private fun sendVer(): Boolean =
        gattServer?.sendLine(GbProtocol.encodeVer(BuildConfig.VERSION_NAME, Build.MODEL ?: Build.PRODUCT)) ?: false

    private fun sendStatus(bat: Int, volt: Double, chg: Int): Boolean =
        gattServer?.sendLine(GbProtocol.encodeStatus(bat, volt, chg)) ?: false

    /** Reads the sticky battery intent and pushes it as a `status` line. */
    private fun pushBatteryStatus(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val s = BatteryReader.read(intent)
        ProbeStateHolder.update { it.copy(batteryPct = s.percent, charging = s.charging == 1) }
        lastSentPct = s.percent
        lastSentChg = s.charging
        return sendStatus(s.percent, s.volts, s.charging)
    }

    private fun publishServerState() {
        val server = gattServer ?: return
        val device = server.connectedDevice
        ProbeStateHolder.update {
            it.copy(
                centralConnected = device != null,
                connectedDeviceName = device?.let { d -> runCatching { d.name }.getOrNull() ?: d.address },
                notifySubscribed = server.isNotifyEnabled,
            )
        }
    }

    private companion object {
        const val TAG = "BleController"
    }
}
