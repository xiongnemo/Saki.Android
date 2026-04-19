package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.StreamCacheSummary
import com.anzupop.saki.android.domain.model.StreamQuality
import kotlinx.coroutines.flow.Flow

interface StreamCacheRepository {
    fun observeCacheVersion(): Flow<Long>

    fun buildCacheKey(
        serverId: Long,
        songId: String,
        quality: StreamQuality,
    ): String

    suspend fun getStreamCacheSummary(
        serverId: Long? = null,
        quality: StreamQuality? = null,
    ): StreamCacheSummary

    /**
     * Find the best cached quality for a song. Returns the requested quality if cached,
     * or a higher quality if available, or null if not cached at all.
     */
    fun findCachedQualityKey(serverId: Long, songId: String, preferredQuality: StreamQuality): String?

    suspend fun clearStreamCache(serverId: Long? = null): Int
}
