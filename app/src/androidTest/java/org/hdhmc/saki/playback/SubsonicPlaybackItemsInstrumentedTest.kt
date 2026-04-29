package org.hdhmc.saki.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hdhmc.saki.domain.model.ServerEndpoint
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SubsonicStreamCandidate
import org.hdhmc.saki.domain.model.SubsonicStreamRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubsonicPlaybackItemsInstrumentedTest {
    @Test
    fun requestMetadataRoundTripsThroughLogicalMediaItem() {
        val song = Song(
            id = "song-1",
            parentId = null,
            title = "Blue Hour",
            album = "Skyline",
            albumId = "album-1",
            artist = "Saki",
            artistId = "artist-1",
            coverArtId = null,
            durationSeconds = 245,
            track = 4,
            discNumber = 1,
            year = 2024,
            genre = null,
            bitRate = 320,
            sampleRate = 44_100,
            suffix = "flac",
            contentType = "audio/flac",
            sizeBytes = null,
            path = null,
            created = null,
        )

        val mediaItem = song.toPlaybackRequestMediaItem(
            serverId = 42L,
            qualityLabel = "Original",
            streamCacheKey = "42:song-1:original",
        )

        val request = requireNotNull(mediaItem.toPlaybackRequestOrNull())
        assertEquals(42L, request.serverId)
        assertEquals("song-1", request.songId)
        assertEquals("Blue Hour", request.title)
        assertEquals("Skyline", request.album)
        assertEquals("Saki", request.artist)
        assertEquals(245_000L, request.durationMs)
        assertEquals("audio/flac", request.mimeType)
        assertEquals(320, request.bitRate)
        assertEquals(320, request.sourceBitRate)
        assertEquals(44_100, request.sampleRate)

        val transcoded = requireNotNull(
            song.toPlaybackRequestMediaItem(
                serverId = 42L,
                qualityLabel = "128 kbps",
                streamCacheKey = "42:song-1:128",
                maxBitRate = 128,
            ).toPlaybackRequestOrNull(),
        )
        assertEquals(128, transcoded.bitRate)
        assertEquals(320, transcoded.sourceBitRate)
    }

    @Test
    fun nextStreamCandidateAdvancesUntilTheListIsExhausted() {
        val request = PlaybackRequest(
            serverId = 7L,
            songId = "song-2",
            title = "Fallback Track",
            album = null,
            albumId = null,
            artist = "Saki",
            artistId = null,
            durationMs = 180_000L,
            track = null,
            discNumber = null,
            mimeType = "audio/mpeg",
            coverArtId = null,
            coverArtPath = null,
            artworkUri = null,
            localPath = null,
            qualityLabel = "320 kbps",
            streamCacheKey = "7:song-2:320",
            isCached = false,
            maxBitRate = null,
            format = null,
            suffix = "mp3",
            bitRate = 320,
            sourceBitRate = 320,
            sampleRate = 44_100,
        )
        val streamRequest = SubsonicStreamRequest(
            songId = "song-2",
            candidates = listOf(
                SubsonicStreamCandidate(
                    endpoint = ServerEndpoint(
                        id = 1L,
                        label = "LAN",
                        baseUrl = "http://192.168.1.200:9033",
                        isPrimary = true,
                        order = 0,
                    ),
                    url = "http://192.168.1.200:9033/rest/stream.view?id=song-2",
                ),
                SubsonicStreamCandidate(
                    endpoint = ServerEndpoint(
                        id = 2L,
                        label = "WAN",
                        baseUrl = "https://music.example.com",
                        isPrimary = false,
                        order = 1,
                    ),
                    url = "https://music.example.com/rest/stream.view?id=song-2",
                ),
            ),
        )

        val first = request.toPlayableMediaItem(streamRequest)
        val second = requireNotNull(first.nextStreamCandidateOrNull())

        assertEquals(
            "https://music.example.com/rest/stream.view?id=song-2",
            requireNotNull(second.localConfiguration).uri.toString(),
        )
        assertNull(second.nextStreamCandidateOrNull())
    }
}
