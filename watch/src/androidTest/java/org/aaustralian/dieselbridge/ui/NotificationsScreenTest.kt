// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.aaustralian.dieselbridge.data.CannedResponsesStore
import org.aaustralian.dieselbridge.data.MusicStore
import org.aaustralian.dieselbridge.data.NotificationStore
import org.aaustralian.dieselbridge.data.WatchNotification

/**
 * Compose UI test (runs on the Wear emulator or a real watch). Guards the notification card's
 * action surface: only Reply + Dismiss (no Open/Mute), and Reply appears only when the notification
 * is replyable.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun seed() {
        NotificationStore.clear()
        MusicStore.clear()
        CannedResponsesStore.set(emptyList())
    }

    @Test
    fun replyableNotificationShowsReplyAndDismissOnly() {
        NotificationStore.upsert(
            WatchNotification(1L, "Signal", "Alice", "Coffee later?", "Alice", 0L, replyable = true),
        )
        composeRule.setContent { NotificationsScreen() }
        // The card scrolls on a small round screen — scroll each node into view before asserting.
        composeRule.onNodeWithText("Find phone").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Clear all").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reply").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
        // Open and Mute were removed from the action surface.
        composeRule.onNodeWithText("Open").assertDoesNotExist()
        composeRule.onNodeWithText("Mute").assertDoesNotExist()
    }

    @Test
    fun nonReplyableNotificationHidesReply() {
        NotificationStore.upsert(
            WatchNotification(2L, "System", "Update", "Installed", null, 0L, replyable = false),
        )
        composeRule.setContent { NotificationsScreen() }
        composeRule.onNodeWithText("Update").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reply").assertDoesNotExist()
    }

    @Test
    fun rendersNowPlayingTransportControls() {
        MusicStore.onInfo(
            artist = "Radiohead",
            album = "OK Computer",
            track = "No Surprises",
            durationMs = 0,
        )
        MusicStore.onState("play", positionMs = 0)
        composeRule.setContent { NotificationsScreen() }
        // The card scrolls on a small round screen — scroll each node into view before asserting.
        composeRule.onNodeWithText("⏮").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("⏸").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("⏭").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🔉").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🔊").performScrollTo().assertIsDisplayed()
    }
}
