// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.aaustralian.dieselbridge.service.DieselBridgeService

/**
 * Restarts the BLE bridge foreground service after a watch reboot so the notification link comes
 * back without the user relaunching the app. Starting a connectedDevice FGS from BOOT_COMPLETED can
 * be refused on some OS builds, so the start is guarded.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            runCatching { DieselBridgeService.start(context) }
                .onFailure { Log.w(TAG, "boot start failed: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
