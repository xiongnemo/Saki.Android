package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.PlaybackSessionState
import com.anzupop.saki.android.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

interface PlaybackManager {
    val playbackState: StateFlow<PlaybackSessionState>

    suspend fun playSong(
        serverId: Long,
        song: Song,
    )

    suspend fun playCachedSong(
        song: CachedSong,
    )

    suspend fun playQueue(
        serverId: Long,
        songs: List<Song>,
        startIndex: Int = 0,
    )

    suspend fun playCachedQueue(
        songs: List<CachedSong>,
        startIndex: Int = 0,
    )

    suspend fun addToQueue(
        serverId: Long,
        songs: List<Song>,
    )

    suspend fun playNext(
        serverId: Long,
        song: Song,
    )

    suspend fun pause()

    suspend fun resume()

    suspend fun skipToNext()

    suspend fun skipToPrevious()

    suspend fun seekTo(positionMs: Long)

    suspend fun cycleRepeatMode()

    suspend fun toggleShuffle()

    suspend fun skipToQueueItem(index: Int)

    suspend fun removeQueueItem(index: Int)
}
