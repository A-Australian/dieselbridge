// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aaustralian.dieselbridge.R
import org.aaustralian.dieselbridge.ui.FindAlertActivity

/**
 * Drives the "find my watch" alert: an alarm-usage repeating vibration plus an ongoing,
 * full-screen-intent notification that launches [FindAlertActivity]. Triggered by the phone's
 * `find` message (see NotificationRouter) and stopped from the alert UI, the phone, or a ~60s
 * safety timeout so a stray start can never buzz forever.
 */
object FindAlertController {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    @Volatile
    private var vibrator: Vibrator? = null

    @Volatile
    private var ringtone: Ringtone? = null

    // Alarm volume saved on the first start() so stop() can restore it after we boost to max.
    @Volatile
    private var savedAlarmVolume: Int? = null

    /** Idempotent — safe to call while already alerting; it just re-arms vibration and the timeout. */
    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS requested at runtime in MainActivity; notify() is also runCatching-guarded
    @Suppress("DEPRECATION") // vibrate(effect, AudioAttributes) is deprecated on API 33+, but we need it for minSdk 30
    fun start(context: Context) {
        val ctx = context.applicationContext
        createChannel(ctx)

        val vib = obtainVibrator(ctx).also { vibrator = it }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        vib.vibrate(VibrationEffect.createWaveform(WAVEFORM, 0), attrs)
        startAlarmSound(ctx)

        _active.value = true
        armAutoStop(ctx)
        runCatching {
            NotificationManagerCompat.from(ctx).notify(FIND_NOTIF_ID, buildNotification(ctx))
        }
    }

    fun stop(context: Context) {
        val ctx = context.applicationContext
        (vibrator ?: obtainVibrator(ctx)).cancel()
        vibrator = null
        stopAlarmSound(ctx)
        NotificationManagerCompat.from(ctx).cancel(FIND_NOTIF_ID)
        autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
        autoStopRunnable = null
        _active.value = false
    }

    /** One-shot buzz for the phone's `vibrate` message — independent of the find alert. */
    @Suppress("DEPRECATION") // vibrate(VibrationEffect) is deprecated on API 33+, but we need it for minSdk 30
    fun buzzOnce(context: Context, ms: Long = 400) {
        obtainVibrator(context).vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    private fun armAutoStop(context: Context) {
        autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { stop(context) }
        autoStopRunnable = r
        mainHandler.postDelayed(r, AUTO_STOP_MS)
    }

    /**
     * Plays the default alarm ringtone on a loop at max alarm volume so the watch is audible across a
     * room. Alarm usage rings even under Do-Not-Disturb. Idempotent: a re-arm won't double-play or
     * re-save the volume. The boosted volume is restored in [stopAlarmSound].
     */
    private fun startAlarmSound(context: Context) {
        if (ringtone?.isPlaying == true) return
        val audio = context.getSystemService<AudioManager>()
        if (audio != null && savedAlarmVolume == null) {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            runCatching {
                audio.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0,
                )
            }
        }
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val rt = RingtoneManager.getRingtone(context, uri) ?: return
        rt.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        rt.isLooping = true // API 28+; minSdk is 30
        ringtone = rt
        runCatching { rt.play() }
    }

    private fun stopAlarmSound(context: Context) {
        runCatching { ringtone?.stop() }
        ringtone = null
        savedAlarmVolume?.let { vol ->
            runCatching {
                context.getSystemService<AudioManager>()?.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
            }
        }
        savedAlarmVolume = null
    }

    private fun buildNotification(context: Context): android.app.Notification {
        val intent = Intent(context, FindAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Find watch")
            .setContentText("Your phone is looking for this watch")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Find watch",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Full-screen alert when the phone is looking for this watch"
            enableVibration(false) // vibration is driven manually with alarm usage
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun obtainVibrator(context: Context): Vibrator {
        val ctx = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService<VibratorManager>()!!.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService<Vibrator>()!!
        }
    }

    private const val CHANNEL_ID = "pixelbridge_find"

    /** Distinct from DieselBridgeService.NOTIF_ID (1) so the two never collide. */
    private const val FIND_NOTIF_ID = 0x7654_3210

    private const val AUTO_STOP_MS = 60_000L
    private val WAVEFORM = longArrayOf(0, 600, 400)
}
