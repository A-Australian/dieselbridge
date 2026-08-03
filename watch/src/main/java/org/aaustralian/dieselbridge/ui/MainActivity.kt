// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.aaustralian.dieselbridge.ble.ProbeStateHolder
import org.aaustralian.dieselbridge.service.DieselBridgeService
import org.aaustralian.dieselbridge.system.PowerHelper

/**
 * Launcher activity: requests the BLE runtime permissions, starts the foreground BLE service,
 * and renders the live BLE self-check state.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Start once the BLE permissions are granted (POST_NOTIFICATIONS is best-effort).
        val blePermsGranted = result.entries
            .filter { it.key != Manifest.permission.POST_NOTIFICATIONS }
            .all { it.value }
        if (blePermsGranted) startBridge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotificationsScreen() }
        ensurePermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        // Reflect the current battery-optimization exemption in the UI (user may toggle it in Settings).
        val exempt = PowerHelper.isIgnoringBatteryOptimizations(this)
        ProbeStateHolder.update { it.copy(ignoringBatteryOptimizations = exempt) }
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ modern permissions
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 9 (Wear OS 2.14) legacy requirements for BLE advertising
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensurePermissionsThenStart() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startBridge() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startBridge() {
        DieselBridgeService.start(this)
    }
}
