// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import net.farcaster.pixelbridge.R
import net.farcaster.pixelbridge.ble.BatteryReader
import net.farcaster.pixelbridge.ble.BlePeripheralController
import net.farcaster.pixelbridge.data.MusicStore
import net.farcaster.pixelbridge.data.NotificationActions
import net.farcaster.pixelbridge.data.NotificationStore
import net.farcaster.pixelbridge.tile.MusicTileService
import net.farcaster.pixelbridge.tile.PixelBridgeTileService

/**
 * connectedDevice-typed foreground service that owns the BLE peripheral stack so the link survives
 * screen-off / backgrounding. Also listens for Bluetooth on/off so advertising (re)starts
 * automatically when the user enables Bluetooth. A foreground service alone does NOT beat Doze —
 * the app also needs a battery-optimization exemption (see docs/architecture.md).
 */
class PixelBridgeService : Service() {

    private var controller: BlePeripheralController? = null

    /** Coarse-signal watcher that pokes the tile to redraw when connection/battery/latest-notif change. */
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> controller?.onBluetoothStateOn()
                BluetoothAdapter.STATE_OFF -> controller?.onBluetoothStateOff()
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            controller?.onBatteryUpdate(BatteryReader.read(intent))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        controller = BlePeripheralController(applicationContext).also { it.start() }
        // Route UI action taps (dismiss / reply / open) to the controller's BLE back-channel.
        NotificationActions.handler = { id, action, reply -> controller?.sendAction(id, action, reply) }
        // Route find-phone taps to the controller so the watch can buzz the phone over BLE.
        NotificationActions.findPhoneHandler = { active -> controller?.sendFindPhone(active) }
        // Route call action taps (accept / reject / ignore / end) over the BLE back-channel.
        NotificationActions.callHandler = { action -> controller?.sendCall(action) }
        // Route music transport taps (play / pause / next / …) over the BLE back-channel.
        NotificationActions.musicHandler = { cmd -> controller?.sendMusic(cmd) }
        // Debounce a coarse status signature and ask the tiles to redraw when it changes.
        tileScope.launch {
            // Refresh the tiles when their content changes: the notification digest (count + the
            // top few ids) for the status tile, and the now-playing track/state for the music tile.
            combine(NotificationStore.items, MusicStore.state) { items, np ->
                listOf(items.size, items.take(3).map { it.id }, np?.playing, np?.track)
            }
                .distinctUntilChanged()
                .drop(1)
                .debounce(750)
                .collect {
                    TileService.getUpdater(applicationContext).apply {
                        requestUpdate(PixelBridgeTileService::class.java)
                        requestUpdate(MusicTileService::class.java)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        NotificationActions.handler = null
        NotificationActions.findPhoneHandler = null
        NotificationActions.callHandler = null
        NotificationActions.musicHandler = null
        tileScope.cancel()
        runCatching { unregisterReceiver(bluetoothReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
        controller?.stop()
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        // minSdk 30 => the typed startForeground and the connectedDevice type constant always exist.
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "pixelbridge_link"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, PixelBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PixelBridgeService::class.java))
        }
    }
}
