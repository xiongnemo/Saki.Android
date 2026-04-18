package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.CacheStorageSummary
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface CachedSongRepository {
    fun observeCachedSongs(): Flow<List<CachedSong>>

    suspend fun getCachedSong(
        serverId: Long,
        songId: String,
    ): CachedSong?

    suspend fun cacheSong(
        serverId: Long,
        song: Song,
    ): CachedSong

    suspend fun deleteCachedSong(cacheId: String)

    suspend fun clearCachedSongs(serverId: Long? = null): Int

    suspend fun getCacheStorageSummary(serverId: Long? = null): CacheStorageSummary
}
