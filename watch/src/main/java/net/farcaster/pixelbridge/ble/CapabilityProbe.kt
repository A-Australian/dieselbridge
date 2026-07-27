// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * The single load-bearing check for the whole primary topology (docs/architecture.md):
 * can THIS watch act as a BLE peripheral / advertiser?
 *
 * The authoritative test is `getBluetoothLeAdvertiser() != null` PLUS actually starting to
 * advertise + open a GATT server (done by [BlePeripheralController]). Do NOT gate on
 * `isMultipleAdvertisementSupported()` — it only reports whether *concurrent* advertisements are
 * possible and is famously ambiguous.
 */
object CapabilityProbe {
    data class Result(
        val bluetoothOn: Boolean,
        val advertiserAvailable: Boolean,
        val multipleAdvSupported: Boolean,
    )

    fun run(context: Context): Result {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = manager.adapter
        val on = adapter?.isEnabled == true
        val advertiser = if (on) adapter?.bluetoothLeAdvertiser else null
        return Result(
            bluetoothOn = on,
            advertiserAvailable = advertiser != null,
            multipleAdvSupported = adapter?.isMultipleAdvertisementSupported == true,
        )
    }
}
