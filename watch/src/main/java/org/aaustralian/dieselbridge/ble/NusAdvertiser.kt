// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

/**
 * Advertises the NUS service UUID (primary packet) + a Bangle.js-matching device name
 * (scan-response packet).
 *
 * The 31-byte advertisement cannot hold a 128-bit service UUID *and* a long name, so the name
 * goes in the scan response. The advertised name is the adapter name, which we set to
 * [BleUuids.ADVERTISED_NAME] so Gadgetbridge's Bangle.js coordinator regex matches. (Setting the
 * adapter name changes the watch's global Bluetooth name — acceptable for a dedicated device.)
 */
@SuppressLint("MissingPermission")
class NusAdvertiser(private val context: Context) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    fun start(onResult: (success: Boolean, error: String?) -> Unit) {
        val adapter = manager.adapter ?: run { onResult(false, "no adapter"); return }
        runCatching {
            val currentName = adapter.name ?: "Smartwatch"
            // Prevent duplicate prefixes if the service restarts
            if (!currentName.startsWith("Bangle.js")) {
                val newName = "Bangle.js $currentName"
                adapter.name = newName
                Log.i(TAG, "Renamed Bluetooth adapter from '$currentName' to '$newName'")
            }
        }
        val adv = adapter.bluetoothLeAdvertiser ?: run {
            onResult(false, "getBluetoothLeAdvertiser() == null — peripheral role NOT supported")
            return
        }
        advertiser = adv

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleUuids.NUS_SERVICE))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "advertising started")
                onResult(true, null)
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "advertising failed: $errorCode")
                onResult(false, "AdvertiseCallback error $errorCode")
            }
        }
        callback = cb
        adv.startAdvertising(settings, advertiseData, scanResponse, cb)
    }

    fun stop() {
        callback?.let { advertiser?.stopAdvertising(it) }
        callback = null
    }

    private companion object {
        const val TAG = "NusAdvertiser"
    }
}
