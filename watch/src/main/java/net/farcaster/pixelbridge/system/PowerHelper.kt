// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.system

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * Battery-optimization exemption status. A `connectedDevice` foreground service alone does NOT beat
 * Doze; on the power-constrained Pixel Watch the app should be exempted or the BLE link is dropped
 * when the screen is off and the watch is idle (see docs/architecture.md).
 *
 * Note: Wear OS 5.1 on the Pixel Watch Gen-1 exposes NO exemption dialog
 * (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` -> "No activity found"), so the exemption must be granted
 * over adb: `adb shell dumpsys deviceidle whitelist +net.farcaster.pixelbridge`. We only detect the
 * state here and surface it in the UI.
 */
object PowerHelper {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
