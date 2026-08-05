// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * Wear OS 3+ (API 30+) backend using Health Services' [PassiveMonitoringClient].
 *
 * PassiveMonitoringClient (not ExerciseClient) is deliberate: we want ambient, always-on
 * background readings to mirror upstream over BLE, not a tracked workout session.
 *
 * Requires BODY_SENSORS (API <=35) / android.permission.health.READ_HEART_RATE (API 36+) to be
 * granted before [start] is called — see docs/dev-environment.md for the manifest entries. If the
 * permission is missing, Health Services will simply never deliver data points (no crash).
 */
class HealthServicesProvider(context: Context) : HealthDataProvider {

    private val client: PassiveMonitoringClient =
        HealthServices.getClient(context).passiveMonitoringClient

    private var callback: PassiveListenerCallback? = null
    private var lastHr: Int? = null
    private var lastSteps: Int? = null

    override fun start(onSample: (ActivitySample) -> Unit) {
        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_DAILY))
            .build()

        val cb = object : PassiveListenerCallback {
            override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let {
                    lastHr = it.value.toInt()
                }
                dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let {
                    lastSteps = it.value.toInt()
                }
                onSample(ActivitySample(System.currentTimeMillis(), lastHr, lastSteps))
            }
        }
        callback = cb
        client.setPassiveListenerCallback(config, cb)
    }

    override fun stop() {
        client.clearPassiveListenerCallbackAsync()
        callback = null
        lastHr = null
        lastSteps = null
    }
}
