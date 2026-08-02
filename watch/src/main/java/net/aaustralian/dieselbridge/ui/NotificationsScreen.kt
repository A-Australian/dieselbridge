// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.tooling.preview.devices.WearDevices
import org.aaustralian.dieselbridge.ble.ProbeReport
import org.aaustralian.dieselbridge.ble.ProbeStateHolder
import org.aaustralian.dieselbridge.data.CannedResponsesStore
import org.aaustralian.dieselbridge.data.MusicStore
import org.aaustralian.dieselbridge.data.NotificationActions
import org.aaustralian.dieselbridge.data.NotificationStore
import org.aaustralian.dieselbridge.data.NowPlaying
import org.aaustralian.dieselbridge.data.WatchNotification

private const val KEY_REPLY = "pb_reply"

private val CardBg = Color(0xFF202124)
private val ChipBg = Color(0xFF3C4043)
private val AppAccent = Color(0xFF8AB4F8)
private val TitleColor = Color(0xFFF1F3F4)
private val BodyColor = Color(0xFFC4C7C5)
private val Muted = Color(0xFF9AA0A6)
private val ConnectedColor = Color(0xFF81C995)
private val WarnColor = Color(0xFFFDD663)
private val BannerBg = Color(0xFF2A2B2E)

/** Notification list with per-card Dismiss / Reply actions (watch -> phone back-channel). */
@Composable
fun NotificationsScreen() {
    val notifications by NotificationStore.items.collectAsStateWithLifecycle()
    val status by ProbeStateHolder.state.collectAsStateWithLifecycle()
    val nowPlaying by MusicStore.state.collectAsStateWithLifecycle()
    val canned by CannedResponsesStore.state.collectAsStateWithLifecycle()

    var replyTarget by remember { mutableStateOf<Long?>(null) }
    val replyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = RemoteInput.getResultsFromIntent(result.data)
            ?.getCharSequence(KEY_REPLY)?.toString()
        val id = replyTarget
        if (!text.isNullOrBlank() && id != null) NotificationActions.reply(id, text)
        replyTarget = null
    }
    val startReply: (Long) -> Unit = { id ->
        replyTarget = id
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val builder = RemoteInput.Builder(KEY_REPLY).setLabel("Reply")
        if (canned.isNotEmpty()) builder.setChoices(canned.toTypedArray())
        val inputs = listOf(builder.build())
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, inputs)
        replyLauncher.launch(intent)
    }

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    RotaryScrollableDefaults.behavior(scrollableState = scrollState),
                    focusRequester,
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusHeader(status)
            SetupBanners(status)
            nowPlaying?.let { np ->
                MusicCard(
                    np = np,
                    onPrev = { NotificationActions.music(NotificationActions.MUSIC_PREVIOUS) },
                    onPlayPause = {
                        NotificationActions.music(
                            if (np.playing) NotificationActions.MUSIC_PAUSE else NotificationActions.MUSIC_PLAY,
                        )
                    },
                    onNext = { NotificationActions.music(NotificationActions.MUSIC_NEXT) },
                    onVolDown = { NotificationActions.music(NotificationActions.MUSIC_VOLUMEDOWN) },
                    onVolUp = { NotificationActions.music(NotificationActions.MUSIC_VOLUMEUP) },
                )
            }
            if (notifications.isEmpty()) {
                Text(
                    text = "No notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else {
                notifications.forEach { n ->
                    NotificationCard(
                        n = n,
                        onReply = { startReply(n.id) },
                        onDismiss = { NotificationActions.dismiss(n.id) },
                    )
                }
            }
            // Global actions BELOW the cards: ring the phone, and clear the watch list.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("Find phone", { NotificationActions.findPhone() }, Modifier.weight(1f))
                if (notifications.isNotEmpty()) {
                    ActionChip("Clear all", { NotificationActions.dismissAll() }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SetupBanners(s: ProbeReport) {
    // All informational: Wear OS 5.1 exposes no in-app fix for battery/BT; see docs.
    when {
        !s.bluetoothOn ->
            Banner("Bluetooth is off — enable it", WarnColor)
        !s.ignoringBatteryOptimizations ->
            Banner("Battery not exempt · grant via adb", Muted)
        s.advertising && !s.centralConnected ->
            Banner("Open Gadgetbridge to connect", Muted)
    }
}

@Composable
private fun Banner(text: String, accent: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BannerBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatusHeader(s: ProbeReport) {
    // Connection status only — battery moved off this screen (it's on Gadgetbridge's device card),
    // and the Find-phone / Clear-all actions live in a row below the cards.
    val (dot, label) = when {
        !s.bluetoothOn -> "○" to "Bluetooth off"
        s.centralConnected -> "●" to (s.connectedDeviceName ?: "connected")
        s.advertising -> "◌" to "advertising…"
        else -> "○" to "starting…"
    }
    Text(
        text = "$dot  $label",
        style = MaterialTheme.typography.labelMedium,
        color = if (s.centralConnected) ConnectedColor else Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MusicCard(
    np: NowPlaying,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onVolDown: () -> Unit,
    onVolUp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelSmall,
            color = AppAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = np.track ?: "(unknown track)",
            style = MaterialTheme.typography.titleSmall,
            color = TitleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        np.artist?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = BodyColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActionChip("⏮", onPrev, Modifier.weight(1f))
            ActionChip(if (np.playing) "⏸" else "▶", onPlayPause, Modifier.weight(1f))
            ActionChip("⏭", onNext, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActionChip("🔉", onVolDown, Modifier.weight(1f))
            ActionChip("🔊", onVolUp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotificationCard(
    n: WatchNotification,
    onReply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        n.app?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AppAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = n.title ?: "(no title)",
            style = MaterialTheme.typography.titleSmall,
            color = TitleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        n.body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = BodyColor,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reply only when the phone reported the notification as replyable (has a RemoteInput).
            if (n.replyable) ActionChip("Reply", onReply, Modifier.fillMaxWidth())
            ActionChip("Dismiss", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = TitleColor,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun NotificationCardPreview() {
    MaterialTheme {
        NotificationCard(
            n = WatchNotification(
                id = 1L,
                app = "Signal",
                title = "Alice",
                body = "Coffee later? Around 3pm works for me.",
                sender = "Alice",
                receivedAt = 0L,
                replyable = true,
            ),
            onReply = {},
            onDismiss = {},
        )
    }
}
