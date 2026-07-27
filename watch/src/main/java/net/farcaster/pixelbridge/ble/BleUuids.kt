// SPDX-License-Identifier: Apache-2.0

package net.farcaster.pixelbridge.ble

import java.util.UUID

/**
 * Nordic UART Service (NUS) UUIDs + Bangle.js framing constants.
 *
 * The watch is the BLE PERIPHERAL / GATT server. Gadgetbridge (phone, BLE central, unmodified,
 * configured as a Bangle.js device) connects and speaks the JSON-over-NUS dialect. See
 * docs/ble-protocol.md.
 */
object BleUuids {
    /** NUS primary service. */
    val NUS_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    /** RX — central -> watch. Property WRITE / WRITE_NO_RESPONSE. The phone writes here. */
    val NUS_RX_CHAR: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    /** TX — watch -> central. Property NOTIFY. We push newline-terminated lines here. */
    val NUS_TX_CHAR: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    /** Client Characteristic Configuration Descriptor (standard 0x2902). */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Advertised BLE name — MUST match Gadgetbridge's Bangle.js coordinator regex `Bangle\.js.*`. */
    const val ADVERTISED_NAME = "Bangle.js PixelBridge"

    /** Message frames are newline-delimited; the phone->watch frame is prefixed with a 0x10 DLE byte. */
    const val NEWLINE: Byte = 0x0A
    const val DLE: Byte = 0x10
}
