// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.health

/**
 * A single point-in-time reading from the watch's body sensors.
 *
 * Either field may be null when that sensor is unavailable or not currently producing a value
 * (e.g. heart rate sensor not on wrist). Null is used rather than 0 because 0 has a specific
 * "no reading" meaning downstream in [org.aaustralian.dieselbridge.protocol.GbProtocol.encodeActivity].
 */
data class ActivitySample(
    val timestampMs: Long,
    val heartRateBpm: Int? = null,
    val steps: Int? = null,
)
