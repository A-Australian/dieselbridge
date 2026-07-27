// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationActionsTest {

    private var captured: Triple<Long, String, String?>? = null
    private var findActive: Boolean? = null
    private var callAction: String? = null
    private var musicCmd: String? = null

    // NotificationActions is a process-wide singleton — install/reset handlers around each test.
    @Before fun setup() {
        captured = null
        findActive = null
        callAction = null
        musicCmd = null
        NotificationActions.handler = { id, action, reply -> captured = Triple(id, action, reply) }
        NotificationActions.findPhoneHandler = { active -> findActive = active }
        NotificationActions.callHandler = { action -> callAction = action }
        NotificationActions.musicHandler = { cmd -> musicCmd = cmd }
    }

    @After fun teardown() {
        NotificationActions.handler = null
        NotificationActions.findPhoneHandler = null
        NotificationActions.callHandler = null
        NotificationActions.musicHandler = null
    }

    @Test
    fun dismissAllCapturesDismissAllWithZeroId() {
        NotificationActions.dismissAll()
        assertEquals(Triple(0L, "DISMISS_ALL", null), captured)
    }

    @Test
    fun replyCapturesReplyActionWithText() {
        NotificationActions.reply(9L, "hi")
        assertEquals(Triple(9L, "REPLY", "hi"), captured)
    }

    @Test
    fun findPhoneDefaultsToActive() {
        NotificationActions.findPhone()
        assertEquals(true, findActive)
    }

    @Test
    fun findPhoneForwardsInactive() {
        NotificationActions.findPhone(false)
        assertEquals(false, findActive)
    }

    @Test
    fun callForwardsAction() {
        NotificationActions.call(NotificationActions.ACTION_ACCEPT)
        assertEquals("ACCEPT", callAction)
    }

    @Test
    fun musicForwardsCommand() {
        NotificationActions.music(NotificationActions.MUSIC_NEXT)
        assertEquals("next", musicCmd)
    }
}
