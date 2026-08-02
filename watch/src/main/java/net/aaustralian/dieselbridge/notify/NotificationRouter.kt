// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.notify

import android.content.Context
import org.aaustralian.dieselbridge.data.CannedResponsesStore
import org.aaustralian.dieselbridge.data.MusicStore
import org.aaustralian.dieselbridge.data.NotificationStore
import org.aaustralian.dieselbridge.data.WatchNotification
import org.aaustralian.dieselbridge.protocol.GbMessage
import org.aaustralian.dieselbridge.protocol.GbProtocol

/**
 * Applies one inbound `GB({...})` line to the [NotificationStore] + system notifications. Shared by
 * the BLE controller (real notifications from Gadgetbridge) and the debug inject receiver (simulated
 * ones), so both drive the exact same pipeline. Returns the parsed message so callers can log.
 */
class NotificationRouter(private val context: Context, private val notifier: WatchNotifier) {

    fun handle(line: String): GbMessage? {
        val msg = GbProtocol.parseLine(line)
        when (msg) {
            is GbMessage.Notify -> {
                val wn = WatchNotification(
                    id = msg.id,
                    app = msg.src,
                    title = msg.title ?: msg.sender,
                    body = msg.body ?: msg.subject,
                    sender = msg.sender,
                    receivedAt = System.currentTimeMillis(),
                    replyable = msg.replyable,
                )
                NotificationStore.upsert(wn)
                notifier.show(wn)
            }
            is GbMessage.NotifyDelete -> {
                NotificationStore.remove(msg.id)
                notifier.cancel(msg.id)
            }
            is GbMessage.Find -> if (msg.active) FindAlertController.start(context) else FindAlertController.stop(context)
            is GbMessage.Vibrate -> FindAlertController.buzzOnce(context)
            is GbMessage.Call -> CallController.onCall(context, msg.cmd, msg.name, msg.number)
            is GbMessage.MusicInfo -> MusicStore.onInfo(msg.artist, msg.album, msg.track, msg.durMs)
            is GbMessage.MusicState -> MusicStore.onState(msg.state, msg.position)
            is GbMessage.CannedResponses -> CannedResponsesStore.set(msg.list)
            else -> {}
        }
        return msg
    }
}
