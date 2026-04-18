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

    suspend fun clearStreamCache(serverId: Long? = null): Int
}
