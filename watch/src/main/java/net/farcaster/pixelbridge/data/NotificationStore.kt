// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification currently shown on the watch. */
data class WatchNotification(
    val id: Long,
    val app: String?,
    val title: String?,
    val body: String?,
    val sender: String?,
    val receivedAt: Long,
    /** True when the source notification is replyable (has a RemoteInput); gates the Reply action. */
    val replyable: Boolean = false,
)

/**
 * Process-wide store of active notifications, newest first. Shared between the BLE controller
 * (producer) and the Compose UI (consumer). Keyed by the message `id` so a repeat updates in place
 * and a `notify-` removes the right one.
 */
object NotificationStore {
    private const val MAX = 50

    private val _items = MutableStateFlow<List<WatchNotification>>(emptyList())
    val items: StateFlow<List<WatchNotification>> = _items.asStateFlow()

    fun upsert(n: WatchNotification) {
        _items.value = (listOf(n) + _items.value.filterNot { it.id == n.id }).take(MAX)
    }

    fun remove(id: Long) {
        _items.value = _items.value.filterNot { it.id == id }
    }

    fun clear() {
        _items.value = emptyList()
    }
}
