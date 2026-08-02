// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryReaderTest {

    @Test
    fun percentHalf() {
        assertEquals(50, BatteryReader.percent(50, 100))
    }

    @Test
    fun percentZero() {
        assertEquals(0, BatteryReader.percent(0, 100))
    }

    @Test
    fun percentFull() {
        assertEquals(100, BatteryReader.percent(100, 100))
    }

    @Test
    fun negativeScaleFallsBackTo100() {
        assertEquals(100, BatteryReader.percent(5, -1))
    }

    @Test
    fun zeroScaleFallsBackTo100() {
        assertEquals(100, BatteryReader.percent(1, 0))
    }

    @Test
    fun integerRounding() {
        assertEquals(33, BatteryReader.percent(1, 3))
    }
}
