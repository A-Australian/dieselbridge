// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide store of the canned reply choices synced from the phone. Shared between the BLE
 * controller (producer, via `canned_responses_sync`) and the Compose UI (consumer, which offers
 * them as RemoteInput choices when replying).
 */
object CannedResponsesStore {
    private val _state = MutableStateFlow<List<String>>(emptyList())
    val state: StateFlow<List<String>> = _state.asStateFlow()

    fun set(list: List<String>) {
        _state.value = list
    }
}
