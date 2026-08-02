// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The track currently playing on the phone, as reported over the `music*` messages. */
data class NowPlaying(
    val artist: String? = null,
    val album: String? = null,
    val track: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/**
 * Process-wide store of the phone's now-playing state. Shared between the BLE controller (producer)
 * and the Compose UI (consumer). `musicinfo` (metadata) and `musicstate` (transport) arrive in
 * separate messages and in any order, so each merges into the existing value via a CAS `update`.
 */
object MusicStore {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    fun onInfo(artist: String?, album: String?, track: String?, durationMs: Int) {
        _state.update {
            (it ?: NowPlaying()).copy(
                artist = artist,
                album = album,
                track = track,
                durationMs = durationMs,
            )
        }
    }

    fun onState(state: String, positionMs: Int) {
        when (state) {
            "play" -> _state.update { (it ?: NowPlaying()).copy(playing = true, positionMs = positionMs) }
            "pause" -> _state.update { (it ?: NowPlaying()).copy(playing = false, positionMs = positionMs) }
            "stop" -> _state.value = null
            // "" / unknown: Gadgetbridge emits state:"" transiently between a musicinfo and the
            // following "play" (it means "unknown", NOT stopped). Ignore it — clearing here wiped the
            // just-set track, leaving "(unknown track)"/"Nothing playing" for every real track. Only
            // an explicit "stop" clears.
            else -> {}
        }
    }

    fun clear() {
        _state.value = null
    }
}
