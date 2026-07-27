// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of the BLE-peripheral self-check, observed by the UI. */
data class ProbeReport(
    val bluetoothOn: Boolean = false,
    val advertiserAvailable: Boolean = false,
    val multipleAdvSupported: Boolean = false,
    val advertising: Boolean = false,
    val advertiseError: String? = null,
    val gattServerOpen: Boolean = false,
    val centralConnected: Boolean = false,
    val connectedDeviceName: String? = null,
    val notifySubscribed: Boolean = false,
    val bytesReceived: Int = 0,
    val lastLine: String? = null,
    val ignoringBatteryOptimizations: Boolean = true,
    val batteryPct: Int? = null,
    val charging: Boolean = false,
    val log: List<String> = emptyList(),
) {
    /** True once we can advertise AND a central actually connects to our GATT server. */
    val peripheralReady: Boolean
        get() = advertiserAvailable && advertising && gattServerOpen && centralConnected
}

/**
 * Process-wide holder so the foreground service (producer) and the Compose UI (consumer)
 * observe one shared state without binding. A larger app would inject a repository instead.
 */
object ProbeStateHolder {
    private val _state = MutableStateFlow(ProbeReport())
    val state: StateFlow<ProbeReport> = _state.asStateFlow()

    fun update(transform: (ProbeReport) -> ProbeReport) {
        _state.value = transform(_state.value)
    }

    fun log(line: String) {
        _state.value = _state.value.copy(log = (_state.value.log + line).takeLast(30))
    }
}
