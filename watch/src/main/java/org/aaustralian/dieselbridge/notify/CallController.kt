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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aaustralian.dieselbridge.R
import org.aaustralian.dieselbridge.data.NotificationActions
import org.aaustralian.dieselbridge.ui.CallActionActivity
import org.aaustralian.dieselbridge.ui.CallActivity

/** Current call, exposed to [CallActivity]. `active` is false while ringing, true once answered. */
data class CallInfo(val name: String?, val number: String?, val active: Boolean)

/**
 * Drives incoming/outgoing call handling relayed from the phone's `call` message (see
 * NotificationRouter): a ringtone-usage repeating vibration plus an ongoing, full-screen-intent
 * notification that launches [CallActivity]. State transitions arrive over BLE ([onCall]) while
 * local UI taps ([answer]/[reject]/[ignore]/[end]) send commands back to the phone.
 *
 * The ~90s safety timeout only guards the *ringing* state (it clears, never sends); once a call is
 * answered/outgoing the timeout is cancelled so a live call is never torn down under us. Inbound
 * teardown ([onCall] "end"/"reject"/"ignore" -> [clear]) never sends an outbound command.
 */
object CallController {

    private val _state = MutableStateFlow<CallInfo?>(null)
    val state: StateFlow<CallInfo?> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    // Written on the BLE binder thread (onCall->armAutoStop) and read/cleared on the main thread
    // (UI answer/reject/ignore/end->clear->cancelAutoStop); @Volatile so a main-thread Accept can't
    // read a stale null and let the 90s safety runnable tear down an already-answered call.
    @Volatile
    private var autoStopRunnable: Runnable? = null

    @Volatile
    private var vibrator: Vibrator? = null

    @Volatile
    private var ringtone: Ringtone? = null

    // Ring volume saved on the first ring so [stopRing] can restore it after we boost to max.
    @Volatile
    private var savedRingVolume: Int? = null

    /** Inbound state update from the phone. Never sends anything back over BLE. */
    fun onCall(context: Context, cmd: String, name: String?, number: String?) {
        val ctx = context.applicationContext
        Log.i(TAG, "onCall cmd=$cmd name=$name number=$number")
        when (cmd) {
            "incoming" -> {
                createChannel(ctx)
                _state.value = CallInfo(name, number, active = false)
                startRing(ctx)
                armAutoStop(ctx)
                postNotification(ctx)
            }
            "outgoing" -> {
                createChannel(ctx)
                stopRing(ctx)
                cancelAutoStop()
                _state.value = CallInfo(name, number, active = true)
                postNotification(ctx)
            }
            "accept", "start" -> {
                createChannel(ctx)
                stopRing(ctx)
                cancelAutoStop()
                val cur = _state.value ?: CallInfo(name, number, active = true)
                _state.value = cur.copy(active = true)
                postNotification(ctx)
            }
            "end", "reject", "ignore" -> clear(ctx)
        }
    }

    /** Local "Accept" tap: go in-call and tell the phone. Does NOT clear — the call continues. */
    fun answer(context: Context) {
        val ctx = context.applicationContext
        stopRing(ctx)
        cancelAutoStop()
        _state.value = (_state.value ?: CallInfo(null, null, active = true)).copy(active = true)
        postNotification(ctx)
        NotificationActions.call(NotificationActions.ACTION_ACCEPT)
    }

    /** Local "Reject" tap: tear down locally, then tell the phone. */
    fun reject(context: Context) {
        clear(context)
        NotificationActions.call(NotificationActions.ACTION_REJECT)
    }

    /** Local "Ignore" tap: tear down locally, then tell the phone. */
    fun ignore(context: Context) {
        clear(context)
        NotificationActions.call(NotificationActions.ACTION_IGNORE)
    }

    /** Local "End" tap: tear down locally, then tell the phone. */
    fun end(context: Context) {
        clear(context)
        NotificationActions.call(NotificationActions.ACTION_END)
    }

    /** Stop ring, cancel notification + timeout, drop state. Sends nothing (inbound teardown path). */
    private fun clear(context: Context) {
        val ctx = context.applicationContext
        stopRing(ctx)
        NotificationManagerCompat.from(ctx).cancel(CALL_NOTIF_ID)
        cancelAutoStop()
        _state.value = null
    }

    @SuppressLint("MissingPermission") // vibrate has no runtime permission; ring uses public APIs
    @Suppress("DEPRECATION") // vibrate(effect, AudioAttributes) is deprecated on API 33+, but we need it for minSdk 30
    private fun startRing(context: Context) {
        val ctx = context.applicationContext
        val vib = obtainVibrator(ctx).also { vibrator = it }
        // USAGE_ALARM so the buzz bypasses a muted/silent ringer — Android gates ring/notification
        // vibration on the ringer mute, so with the ring muted (common on this watch) a call would
        // otherwise be completely silent. Alarm usage is what makes find-my-watch buzz here; a
        // relayed call is important enough to alert the wrist the same way.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        vib.vibrate(VibrationEffect.createWaveform(WAVEFORM, 0), attrs)
        Log.i(TAG, "startRing: vibrating (alarm usage)")
        startRingtone(ctx)
    }

    /**
     * Plays the default ringtone on a loop at max ring volume, but only when the ringer mode is
     * normal — under silent/vibrate we skip the tone and just buzz. Idempotent: a re-arm won't
     * double-play or re-save the volume. The boosted volume is restored in [stopRing].
     */
    private fun startRingtone(context: Context) {
        if (ringtone?.isPlaying == true) return
        val audio = context.getSystemService<AudioManager>()
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (audio != null && savedRingVolume == null) {
            savedRingVolume = audio.getStreamVolume(AudioManager.STREAM_RING)
            runCatching {
                audio.setStreamVolume(
                    AudioManager.STREAM_RING,
                    audio.getStreamMaxVolume(AudioManager.STREAM_RING),
                    0,
                )
            }
        }
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val rt = RingtoneManager.getRingtone(context, uri) ?: return
        rt.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        rt.isLooping = true // API 28+; minSdk is 30
        ringtone = rt
        runCatching { rt.play() }
    }

    private fun stopRing(context: Context) {
        val ctx = context.applicationContext
        (vibrator ?: obtainVibrator(ctx)).cancel()
        vibrator = null
        runCatching { ringtone?.stop() }
        ringtone = null
        savedRingVolume?.let { vol ->
            runCatching {
                ctx.getSystemService<AudioManager>()?.setStreamVolume(AudioManager.STREAM_RING, vol, 0)
            }
        }
        savedRingVolume = null
    }

    private fun armAutoStop(context: Context) {
        cancelAutoStop()
        val ctx = context.applicationContext
        val r = Runnable { clear(ctx) }
        autoStopRunnable = r
        mainHandler.postDelayed(r, AUTO_STOP_MS)
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
        autoStopRunnable = null
    }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS requested at runtime in MainActivity; notify() is also runCatching-guarded
    private fun postNotification(context: Context) {
        val r = runCatching {
            NotificationManagerCompat.from(context).notify(CALL_NOTIF_ID, buildNotification(context))
        }
        Log.i(TAG, "postNotification active=${_state.value?.active} ok=${r.isSuccess}${r.exceptionOrNull()?.let { " err=$it" } ?: ""}")
    }

    private fun buildNotification(context: Context): android.app.Notification {
        val info = _state.value
        val title = info?.name ?: info?.number ?: "Unknown"
        val text = if (info?.active == true) "In call" else "Incoming call"
        val intent = Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            // CATEGORY_MESSAGE, not CALL: Wear's system UI suppresses third-party CATEGORY_CALL
            // notifications and full-screen intents, so they never surface on the watch. A
            // high-priority message notification on a vibrating channel heads-ups + buzzes exactly
            // like the incoming-notification cards that already work. (No setFullScreenIntent — Wear
            // rejects it for non-dialer apps anyway.)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Ongoing ONLY once the call is active; a ringing (non-ongoing) notification heads-ups.
            .setOngoing(info?.active == true)
            .setAutoCancel(false)
            .setContentIntent(pi)
        // Wear doesn't auto-launch the full-screen CallActivity, so surface tappable actions in the
        // stream. They route through CallActionActivity (notification actions can't target a
        // receiver). tap-to-open (setContentIntent) still opens the full CallActivity on user tap.
        if (info?.active == true) {
            builder.addAction(R.drawable.ic_launcher_foreground, "End", callActionIntent(context, "END", 103))
        } else {
            builder.addAction(R.drawable.ic_launcher_foreground, "Answer", callActionIntent(context, "ACCEPT", 101))
            builder.addAction(R.drawable.ic_launcher_foreground, "Decline", callActionIntent(context, "REJECT", 102))
        }
        return builder.build()
    }

    /**
     * PendingIntent that fires the [CallActionActivity] trampoline for [action]. Each action gets a
     * distinct [requestCode] so the intents don't collapse onto one another under FLAG_UPDATE_CURRENT.
     */
    private fun callActionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionActivity::class.java)
            .putExtra(CallActionActivity.EXTRA_ACTION, action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        // The original "pixelbridge_call" channel was silent (no vibration, no sound), which Wear
        // classifies as non-interruptive — the call never produced a heads-up. Recreate under a new
        // id (channel settings are immutable once created) WITH vibration so an incoming call alerts
        // (heads-up + buzz), like the regular notification channel does. Sound stays null so our
        // manual ringtone still honours the ringer/DND state.
        nm.deleteNotificationChannel("pixelbridge_call")
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming and ongoing phone calls relayed from your phone"
            enableVibration(true)
            vibrationPattern = CALL_VIBRATION
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
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

    private const val TAG = "CallController"

    // v2: the original silent channel couldn't produce a heads-up; this one enables vibration.
    private const val CHANNEL_ID = "pixelbridge_call_v2"

    /** Distinct from FindAlertController.FIND_NOTIF_ID (0x7654_3210) and DieselBridgeService.NOTIF_ID (1). */
    private const val CALL_NOTIF_ID = 0x7654_3211

    private const val AUTO_STOP_MS = 90_000L
    private val WAVEFORM = longArrayOf(0, 1000, 1000)

    /** Channel-level vibration so an incoming call is interruptive (heads-up) even if the manual loop lags. */
    private val CALL_VIBRATION = longArrayOf(0, 600, 400, 600, 400)
}
