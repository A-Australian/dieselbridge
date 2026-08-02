// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import org.aaustralian.dieselbridge.R
import org.aaustralian.dieselbridge.data.WatchNotification

/**
 * Posts each bridged notification as a REAL Wear OS system notification on a high-importance,
 * vibrating channel. This is what makes the watch buzz and show the notification in its native
 * stream even when the PixelBridge app is in the background. `notify-` cancels the matching one.
 *
 * POST_NOTIFICATIONS is requested at runtime in MainActivity (hence @SuppressLint); notify() is
 * additionally guarded so a missing grant never crashes the BLE thread.
 */
class WatchNotifier(private val context: Context) {

    init {
        createChannel()
    }

    @SuppressLint("MissingPermission")
    fun show(n: WatchNotification) {
        val title = n.title ?: n.app ?: "Notification"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(n.body)
            .setSubText(n.app)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(VIBRATION_PATTERN) // pre-O; the channel drives vibration on O+
            .setAutoCancel(true)
        n.body?.let { builder.setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
        runCatching {
            NotificationManagerCompat.from(context).notify(idOf(n.id), builder.build())
        }
    }

    fun cancel(id: Long) {
        NotificationManagerCompat.from(context).cancel(idOf(id))
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming notifications",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Phone notifications bridged over BLE"
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /** Notification ids are Int; fold the Long message id stably into an Int. */
    private fun idOf(id: Long): Int = (id and 0x7FFFFFFF).toInt()

    private companion object {
        const val CHANNEL_ID = "pixelbridge_incoming"
        val VIBRATION_PATTERN = longArrayOf(0, 140, 70, 140)
    }
}
