package org.hdhmc.saki.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SubsonicPlaybackItemsTest {
    @Test
    fun estimatedPlaybackBitRateUsesSourceWhenBelowRequestedMaximum() {
        assertEquals(128, estimatedPlaybackBitRateKbps(sourceBitRate = 128, maxBitRate = 320))
    }

    @Test
    fun estimatedPlaybackBitRateUsesRequestedMaximumWhenSourceIsHigher() {
        assertEquals(320, estimatedPlaybackBitRateKbps(sourceBitRate = 921, maxBitRate = 320))
    }

    @Test
    fun estimatedPlaybackBitRateKeepsSourceForOriginalQuality() {
        assertEquals(921, estimatedPlaybackBitRateKbps(sourceBitRate = 921, maxBitRate = 0))
    }
}
