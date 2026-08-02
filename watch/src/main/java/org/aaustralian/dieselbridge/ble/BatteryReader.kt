// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ble

import android.content.Intent
import android.os.BatteryManager

/** Battery snapshot pushed to the phone as a `status` line (docs/ble-protocol.md). */
data class BatteryStatus(val percent: Int, val volts: Double, val charging: Int)

/**
 * Reads a battery snapshot from an `ACTION_BATTERY_CHANGED` [Intent] (broadcast or sticky).
 *
 * Kept free of Android framework calls in [percent] so the arithmetic is unit-testable on the JVM.
 */
object BatteryReader {
    fun read(intent: Intent): BatteryStatus {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val charging =
            if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            ) {
                1
            } else {
                0
            }
        val volts = if (mv <= 0) 0.0 else mv / 1000.0
        return BatteryStatus(percent = percent(level, scale), volts = volts, charging = charging)
    }

    fun percent(level: Int, scale: Int): Int =
        if (scale <= 0) 100 else ((level * 100) / scale).coerceIn(0, 100)
}
