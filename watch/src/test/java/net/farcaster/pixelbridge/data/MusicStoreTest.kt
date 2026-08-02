// SPDX-License-Identifier: Apache-2.0

package org.aaustralian.dieselbridge.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MusicStoreTest {

    // MusicStore is a process-wide singleton — reset around each test.
    @Before fun setup() = MusicStore.clear()

    @After fun teardown() = MusicStore.clear()

    @Test
    fun infoThenStateMerges() {
        MusicStore.onInfo("Artist", "Album", "Track", durationMs = 200_000)
        MusicStore.onState("play", positionMs = 5_000)
        val np = MusicStore.state.value!!
        assertEquals("Artist", np.artist)
        assertEquals("Album", np.album)
        assertEquals("Track", np.track)
        assertEquals(200_000, np.durationMs)
        assertTrue(np.playing)
        assertEquals(5_000, np.positionMs)
    }

    @Test
    fun stateThenInfoPreservesTransport() {
        MusicStore.onState("play", positionMs = 5_000)
        MusicStore.onInfo("Artist", "Album", "Track", durationMs = 200_000)
        val np = MusicStore.state.value!!
        assertEquals("Track", np.track)
        assertEquals(200_000, np.durationMs)
        assertTrue(np.playing) // transport preserved across the metadata merge
        assertEquals(5_000, np.positionMs)
    }

    @Test
    fun pauseKeepsMetadata() {
        MusicStore.onInfo("Artist", "Album", "Track", durationMs = 200_000)
        MusicStore.onState("pause", positionMs = 5_000)
        val np = MusicStore.state.value!!
        assertEquals("Track", np.track)
        assertFalse(np.playing)
        assertEquals(5_000, np.positionMs)
    }

    @Test
    fun stopClears() {
        MusicStore.onInfo("Artist", "Album", "Track", durationMs = 200_000)
        MusicStore.onState("stop", positionMs = 0)
        assertNull(MusicStore.state.value)
    }

    @Test
    fun emptyStateKeepsTrack() {
        // Gadgetbridge sends state:"" transiently between musicinfo and "play"; it means "unknown",
        // not stopped, so it must NOT clear the just-set track (regression guard for the
        // "(unknown track)" bug). Only an explicit "stop" clears.
        MusicStore.onInfo("Artist", "Album", "Track", durationMs = 200_000)
        MusicStore.onState("play", positionMs = 5_000)
        MusicStore.onState("", positionMs = 0)
        val np = MusicStore.state.value!!
        assertEquals("Track", np.track)
        assertTrue(np.playing) // unchanged by the empty/unknown state
    }
}
