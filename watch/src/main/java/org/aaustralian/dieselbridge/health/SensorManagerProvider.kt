// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wear OS 2 (API 23-29) backend using raw [SensorManager]. Used when Health Services isn't
 * available (minSdk for Health Services is 30 — see docs/dev-environment.md).
 *
 * Two quirks handled here:
 *  - Sensor.TYPE_HEART_RATE reports 0 when not currently measuring (e.g. off-wrist), which we
 *    treat as "no reading" (null) rather than a literal 0 bpm.
 *  - Sensor.TYPE_STEP_COUNTER is cumulative since last device boot, not daily. We persist a
 *    per-day baseline in SharedPreferences so steps reset at local midnight and survive process
 *    restarts, rather than resetting to 0 every time the service restarts.
 */
class SensorManagerProvider(private val context: Context) : HealthDataProvider {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var listener: SensorEventListener? = null
    private var lastHr: Int? = null
    private var lastSteps: Int? = null

    override fun start(onSample: (ActivitySample) -> Unit) {
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_HEART_RATE -> {
                        val hr = event.values[0].toInt()
                        lastHr = if (hr > 0) hr else null
                    }
                    Sensor.TYPE_STEP_COUNTER -> {
                        lastSteps = stepsSinceMidnight(event.values[0].toInt())
                    }
                }
                onSample(ActivitySample(System.currentTimeMillis(), lastHr, lastSteps))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listener = l

        hrSensor?.let { sensorManager.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepSensor?.let { sensorManager.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        lastHr = null
        lastSteps = null
    }

    /**
     * Converts the sensor's cumulative-since-boot count into "steps since local midnight" by
     * storing a (date, baselineCount) pair in SharedPreferences. On first read of a new day, or
     * first read ever, the baseline resets to the current cumulative count (so today's steps
     * start at 0 rather than showing the device's full since-boot total).
     */
    private fun stepsSinceMidnight(cumulative: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = dayFormat.format(Date())
        val storedDay = prefs.getString(KEY_DAY, null)
        val baseline = if (storedDay == today) {
            prefs.getInt(KEY_BASELINE, cumulative)
        } else {
            // New day (or first run ever) - reset baseline to current cumulative count.
            prefs.edit().putString(KEY_DAY, today).putInt(KEY_BASELINE, cumulative).apply()
            cumulative
        }
        return (cumulative - baseline).coerceAtLeast(0)
    }

    private companion object {
        const val PREFS_NAME = "health_step_baseline"
        const val KEY_DAY = "day"
        const val KEY_BASELINE = "baseline"
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
