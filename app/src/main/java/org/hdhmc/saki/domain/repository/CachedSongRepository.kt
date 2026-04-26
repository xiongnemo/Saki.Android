package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.CacheStorageSummary
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import kotlinx.coroutines.flow.Flow

interface CachedSongRepository {
    fun observeCachedSongs(): Flow<List<CachedSong>>

    suspend fun getCachedSong(
        serverId: Long,
        songId: String,
    ): CachedSong?

    suspend fun getPlayableCachedSong(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): CachedSong?

    suspend fun getPlayableCachedSongs(
        serverId: Long,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong>

    suspend fun cacheSong(
        serverId: Long,
        song: Song,
    ): CachedSong

    suspend fun deleteCachedSong(cacheId: String)

    suspend fun clearCachedSongs(serverId: Long? = null): Int

    suspend fun getCacheStorageSummary(serverId: Long? = null): CacheStorageSummary
}
