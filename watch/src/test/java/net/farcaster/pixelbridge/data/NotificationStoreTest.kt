// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationStoreTest {

    private fun n(id: Long, title: String = "t") =
        WatchNotification(id = id, app = "app", title = title, body = "body", sender = "s", receivedAt = 0L)

    // NotificationStore is a process-wide singleton — reset around each test.
    @Before fun setup() = NotificationStore.clear()

    @After fun teardown() = NotificationStore.clear()

    @Test
    fun upsertPrependsNewestFirst() {
        NotificationStore.upsert(n(1))
        NotificationStore.upsert(n(2))
        assertEquals(listOf(2L, 1L), NotificationStore.items.value.map { it.id })
    }

    @Test
    fun upsertDedupesByIdAndMovesToFront() {
        NotificationStore.upsert(n(1))
        NotificationStore.upsert(n(2))
        NotificationStore.upsert(n(1, title = "updated"))
        val items = NotificationStore.items.value
        assertEquals(listOf(1L, 2L), items.map { it.id })
        assertEquals("updated", items.first().title)
    }

    @Test
    fun removeDropsMatchingId() {
        NotificationStore.upsert(n(1))
        NotificationStore.upsert(n(2))
        NotificationStore.remove(1L)
        assertEquals(listOf(2L), NotificationStore.items.value.map { it.id })
    }

    @Test
    fun clearEmpties() {
        NotificationStore.upsert(n(1))
        NotificationStore.clear()
        assertEquals(0, NotificationStore.items.value.size)
    }

    @Test
    fun capsAtFiftyKeepingNewest() {
        for (i in 1..60) NotificationStore.upsert(n(i.toLong()))
        val items = NotificationStore.items.value
        assertEquals(50, items.size)
        assertEquals(60L, items.first().id) // newest kept
        assertEquals(11L, items.last().id) // oldest 10 dropped
    }
}
