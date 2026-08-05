// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.health

/**
 * Reads heart rate + steps from whichever sensor API is available on this OS version.
 *
 * Two implementations exist: [HealthServicesProvider] (Wear OS 3+, API 30+) and
 * [SensorManagerProvider] (Wear OS 2, API 23-29). Use [createHealthDataProvider] to pick the
 * right one rather than constructing either directly.
 */
interface HealthDataProvider {
    /** Begins listening. [onSample] is invoked on every new reading; may fire on a background thread. */
    fun start(onSample: (ActivitySample) -> Unit)

    /** Stops listening and releases sensor resources. Safe to call even if never started. */
    fun stop()
}
