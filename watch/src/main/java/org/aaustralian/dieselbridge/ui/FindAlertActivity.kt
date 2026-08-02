// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.aaustralian.dieselbridge.notify.FindAlertController

// Local alert palette — deliberately independent of NotificationsScreen's private colors.
private val AlertBg = Color(0xFFC5221F)
private val AlertTitle = Color(0xFFFFFFFF)
private val AlertBody = Color(0xFFFFE0DE)
private val StopBg = Color(0xFF2A0A08)
private val StopText = Color(0xFFFFFFFF)

/**
 * Full-screen red "find my watch" alert launched by [FindAlertController]'s full-screen intent.
 * Shows over the lock screen and turns the screen on; tapping Stop (or any external stop) ends the
 * alert and finishes the activity.
 */
class FindAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            FindAlertScreen(
                onStop = {
                    FindAlertController.stop(this)
                    finish()
                },
                onDismissed = { finish() },
            )
        }
    }
}

@Composable
private fun FindAlertScreen(onStop: () -> Unit, onDismissed: () -> Unit) {
    val active by FindAlertController.active.collectAsStateWithLifecycle()
    LaunchedEffect(active) {
        if (!active) onDismissed()
    }
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AlertBg),
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
                    text = "📱",
                    style = MaterialTheme.typography.displaySmall,
                    color = AlertTitle,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Find watch",
                    style = MaterialTheme.typography.titleMedium,
                    color = AlertTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "Your phone is looking for this watch",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Stop",
                    style = MaterialTheme.typography.titleSmall,
                    color = StopText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(StopBg)
                        .clickable(onClick = onStop)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        }
    }
}
