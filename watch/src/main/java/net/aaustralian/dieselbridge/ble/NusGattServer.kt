// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Minimal Nordic UART Service GATT server (watch = peripheral).
 *  - RX (write): central -> watch. NUS is an opaque byte stream, so we reassemble by NEWLINE,
 *    not by packet boundary, and strip the leading 0x10 DLE byte of the `\x10 GB(...)` framing.
 *  - TX (notify): watch -> central. [sendLine] enqueues a newline-terminated line, which is CHUNKED
 *    to the negotiated ATT MTU (default 23 -> 20 usable) and sent one packet at a time, each after
 *    the previous packet's onNotificationSent — otherwise the stack truncates/drops a long payload
 *    and the central (Gadgetbridge) never sees a complete line.
 *
 * Permissions (BLUETOOTH_CONNECT) are requested at runtime in MainActivity, hence @SuppressLint.
 */
@SuppressLint("MissingPermission")
class NusGattServer(
    private val context: Context,
    private val onLine: (String) -> Unit,
    private val onStateChange: () -> Unit,
    private val onSubscribed: () -> Unit = {},
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server: BluetoothGattServer? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var connected: BluetoothDevice? = null
    // Written on the GATT binder thread (CCCD write); read cross-thread from the battery receiver /
    // findPhone path via isNotifyEnabled, so it must be @Volatile for visibility.
    @Volatile
    private var notifyEnabled = false
    private var mtu = DEFAULT_MTU
    private val rxBuffer = ByteArrayOutputStream()

    private val txLock = Any()
    private val txQueue = ArrayDeque<ByteArray>()
    private var txBusy = false

    val connectedDevice: BluetoothDevice? get() = connected
    val isNotifyEnabled: Boolean get() = notifyEnabled

    fun open(): Boolean {
        val gattServer = manager.openGattServer(context, callback) ?: return false

        val rx = BluetoothGattCharacteristic(
            BleUuids.NUS_RX_CHAR,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val tx = BluetoothGattCharacteristic(
            BleUuids.NUS_TX_CHAR,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    BleUuids.CCCD,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                ),
            )
        }
        val service = BluetoothGattService(BleUuids.NUS_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(rx)
            addCharacteristic(tx)
        }
        gattServer.addService(service)
        server = gattServer
        txChar = tx
        return true
    }

    fun close() {
        server?.close()
        server = null
        connected = null
        notifyEnabled = false
        synchronized(txLock) { txQueue.clear(); txBusy = false }
    }

    /**
     * watch -> phone: enqueue one newline-terminated line, chunked to the MTU and streamed via the
     * TX notify characteristic. Returns true if a central is connected and the line was queued.
     */
    fun sendLine(line: String): Boolean {
        val device = connected ?: run { Log.w(TAG, "sendLine: no central connected"); return false }
        // Terminate with CRLF, not LF: Gadgetbridge's line splitter does substring(0, p-1) on the
        // '\n' index, assuming a trailing '\r' — with only '\n' it would eat our closing brace.
        val payload = (line + "\r\n").toByteArray(Charsets.UTF_8)
        val chunkSize = (mtu - 3).coerceAtLeast(MIN_CHUNK)
        synchronized(txLock) {
            var i = 0
            while (i < payload.size) {
                val end = minOf(i + chunkSize, payload.size)
                txQueue.add(payload.copyOfRange(i, end))
                i = end
            }
        }
        Log.i(TAG, "sendLine queued ${payload.size}B (mtu=$mtu chunk=$chunkSize) notifySubscribed=$notifyEnabled")
        pumpTx(device)
        return true
    }

    private fun pumpTx(device: BluetoothDevice) {
        val chunk: ByteArray
        synchronized(txLock) {
            if (txBusy) return
            chunk = txQueue.removeFirstOrNull() ?: return
            txBusy = true
        }
        sendChunk(device, chunk)
    }

    private fun sendChunk(device: BluetoothDevice, chunk: ByteArray) {
        val gattServer = server ?: return
        val characteristic = txChar ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattServer.notifyCharacteristicChanged(device, characteristic, false, chunk)
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.value = chunk
                gattServer.notifyCharacteristicChanged(device, characteristic, false)
            }
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connected = device
                    Log.i(TAG, "central connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connected = null
                    notifyEnabled = false
                    mtu = DEFAULT_MTU
                    rxBuffer.reset()
                    synchronized(txLock) { txQueue.clear(); txBusy = false }
                    Log.i(TAG, "central disconnected")
                }
            }
            onStateChange()
        }

        override fun onMtuChanged(device: BluetoothDevice, newMtu: Int) {
            mtu = newMtu
            Log.i(TAG, "MTU changed to $newMtu")
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(txLock) { txBusy = false }
            pumpTx(device)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BleUuids.NUS_RX_CHAR) {
                for (b in value) {
                    when (b) {
                        BleUuids.NEWLINE -> {
                            // Gadgetbridge's Bangle.js UART sends non-ASCII as single-byte ISO-8859-1
                            // (a 'ü' arrives as 0xFC, not UTF-8's 0xC3 0xBC), so decode as Latin-1 —
                            // UTF-8 turned those bytes into '�' and could corrupt the JSON. ASCII and
                            // \uXXXX-escaped chars are identical under both charsets.
                            val line = rxBuffer.toString("ISO-8859-1").trim()
                            rxBuffer.reset()
                            if (line.isNotEmpty()) onLine(line)
                        }
                        BleUuids.DLE -> { /* skip Espruino echo-off byte */ }
                        else -> rxBuffer.write(b.toInt())
                    }
                }
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            var justSubscribed = false
            if (descriptor.uuid == BleUuids.CCCD) {
                // Non-zero CCCD value = subscribe (notify/indicate enabled).
                val was = notifyEnabled
                notifyEnabled = value.isNotEmpty() && value[0].toInt() != 0
                justSubscribed = !was && notifyEnabled
                Log.i(TAG, "CCCD write from ${device.address}: notifySubscribed=$notifyEnabled")
                onStateChange()
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            if (justSubscribed) onSubscribed()
        }
    }

    private companion object {
        const val TAG = "NusGattServer"
        const val DEFAULT_MTU = 23
        const val MIN_CHUNK = 20
    }
}
