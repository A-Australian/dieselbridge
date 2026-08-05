// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.health

import android.content.Context
import android.os.Build

/** Picks [HealthServicesProvider] on API 30+ (Wear OS 3+), else [SensorManagerProvider]. */
fun createHealthDataProvider(context: Context): HealthDataProvider =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HealthServicesProvider(context)
    } else {
        SensorManagerProvider(context)
    }
