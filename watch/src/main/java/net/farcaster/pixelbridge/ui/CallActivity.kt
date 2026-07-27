// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import net.farcaster.pixelbridge.notify.CallController

// Local call palette — deliberately independent of NotificationsScreen's private colors.
private val CallBg = Color(0xFF102A21)
private val TitleColor = Color(0xFFFFFFFF)
private val BodyColor = Color(0xFFCDECDD)
private val AcceptBg = Color(0xFF1E8E3E)
private val RejectBg = Color(0xFFC5221F)
private val IgnoreBg = Color(0xFF3C4043)
private val EndBg = Color(0xFFC5221F)
private val PillText = Color(0xFFFFFFFF)

/**
 * Full-screen call UI launched by [CallController]'s full-screen intent. Shows over the lock screen
 * and turns the screen on. While ringing it offers Accept / Reject / Ignore; once answered (state
 * becomes active) it recomposes to a single End action. Any external teardown clears the state and
 * finishes this activity.
 */
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            CallScreen(
                onAnswer = { CallController.answer(this) }, // must NOT finish — screen recomposes to End
                onReject = { CallController.reject(this) },
                onIgnore = { CallController.ignore(this) },
                onEnd = { CallController.end(this) },
                onEnded = { finish() },
            )
        }
    }
}

@Composable
private fun CallScreen(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onIgnore: () -> Unit,
    onEnd: () -> Unit,
    onEnded: () -> Unit,
) {
    val info by CallController.state.collectAsStateWithLifecycle()
    LaunchedEffect(info) {
        if (info == null) onEnded()
    }
    val current = info ?: return
    val title = current.name ?: current.number ?: "Unknown"
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CallBg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "📞",
                    style = MaterialTheme.typography.displaySmall,
                    color = TitleColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TitleColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = if (current.active) "In call" else "Incoming call",
                    style = MaterialTheme.typography.bodySmall,
                    color = BodyColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (current.active) {
                    Pill("End", EndBg, onEnd)
                } else {
                    Pill("Accept", AcceptBg, onAnswer)
                    Pill("Reject", RejectBg, onReject)
                    Pill("Ignore", IgnoreBg, onIgnore)
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, bg: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = PillText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
}
