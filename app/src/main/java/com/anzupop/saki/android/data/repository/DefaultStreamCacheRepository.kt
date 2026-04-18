package com.anzupop.saki.android.data.repository

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.StreamCacheSummary
import com.anzupop.saki.android.domain.model.StreamQuality
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import com.anzupop.saki.android.domain.repository.StreamCacheRepository
import com.anzupop.saki.android.playback.ConfigurableLeastRecentlyUsedCacheEvictor
import com.anzupop.saki.android.playback.buildStreamCacheKey
import com.anzupop.saki.android.playback.parseStreamCacheKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
@UnstableApi
class DefaultStreamCacheRepository @Inject constructor(
    private val streamCache: SimpleCache,
    private val cacheEvictor: ConfigurableLeastRecentlyUsedCacheEvictor,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StreamCacheRepository {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val cacheVersion = MutableStateFlow(0L)
    private var lastSnapshot = StreamCacheSnapshot()

    init {
        scope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.streamCacheSizeBytes }
                .distinctUntilChanged()
                .collectLatest { maxBytes ->
                    cacheEvictor.updateMaxBytes(maxBytes)
                    refreshSnapshot(forceEmit = true)
                }
        }
        scope.launch {
            refreshSnapshot(forceEmit = true)
            while (isActive) {
                delay(2_500L)
                refreshSnapshot()
            }
        }
    }

    override fun observeCacheVersion(): Flow<Long> = cacheVersion.asStateFlow()

    override fun buildCacheKey(
        serverId: Long,
        songId: String,
        quality: StreamQuality,
    ): String = buildStreamCacheKey(serverId, songId, quality)

    override suspend fun getStreamCacheSummary(
        serverId: Long?,
        quality: StreamQuality?,
    ): StreamCacheSummary = withContext(ioDispatcher) {
        val snapshot = snapshotForQueries()
        if (serverId == null) {
            StreamCacheSummary(
                cachedSongIds = snapshot.cachedSongIdsByServerAndQuality.values
                    .flatMap { byQuality -> byQuality.values }
                    .flattenToSet(),
                bytes = snapshot.bytesByServer.values.sum(),
            )
        } else {
            StreamCacheSummary(
                cachedSongIds = snapshot.cachedSongIdsByServerAndQuality[serverId]
                    ?.let { byQuality ->
                        quality?.let { byQuality[it.storageKey].orEmpty() }
                            ?: byQuality.values.flattenToSet()
                    }
                    .orEmpty(),
                bytes = snapshot.bytesByServer[serverId] ?: 0L,
            )
        }
    }

    override suspend fun clearStreamCache(serverId: Long?): Int = withContext(ioDispatcher) {
        val matchingKeys = streamCache.keys
            .mapNotNull { key ->
                val parsed = parseStreamCacheKey(key) ?: return@mapNotNull null
                key.takeIf { serverId == null || parsed.serverId == serverId }
            }

        matchingKeys.forEach(streamCache::removeResource)
        refreshSnapshot(forceEmit = true)
        matchingKeys.size
    }

    private suspend fun refreshSnapshot(forceEmit: Boolean = false) {
        val snapshot = buildSnapshot()
        if (forceEmit || snapshot != lastSnapshot) {
            lastSnapshot = snapshot
            cacheVersion.update { version -> version + 1 }
        }
    }

    private fun snapshotForQueries(): StreamCacheSnapshot {
        return if (cacheVersion.value == 0L) {
            buildSnapshot()
        } else {
            lastSnapshot
        }
    }

    private fun buildSnapshot(): StreamCacheSnapshot {
        val cachedSongIdsByServerAndQuality = mutableMapOf<Long, MutableMap<String, MutableSet<String>>>()
        val bytesByServer = mutableMapOf<Long, Long>()

        streamCache.keys.forEach { key ->
            val parsed = parseStreamCacheKey(key) ?: return@forEach
            val cachedBytes = streamCache.getCachedSpans(key).sumOf { span -> span.length }
            bytesByServer[parsed.serverId] = (bytesByServer[parsed.serverId] ?: 0L) + cachedBytes

            val contentLength = ContentMetadata.getContentLength(streamCache.getContentMetadata(key))
            if (contentLength != C.LENGTH_UNSET.toLong() && contentLength > 0L && streamCache.isCached(key, 0, contentLength)) {
                cachedSongIdsByServerAndQuality
                    .getOrPut(parsed.serverId) { mutableMapOf() }
                    .getOrPut(parsed.qualityKey) { mutableSetOf() }
                    .add(parsed.songId)
            }
        }

        return StreamCacheSnapshot(
            cachedSongIdsByServerAndQuality = cachedSongIdsByServerAndQuality.mapValues { (_, byQuality) ->
                byQuality.mapValues { (_, ids) -> ids.toSet() }
            },
            bytesByServer = bytesByServer.toMap(),
        )
    }
}

private data class StreamCacheSnapshot(
    val cachedSongIdsByServerAndQuality: Map<Long, Map<String, Set<String>>> = emptyMap(),
    val bytesByServer: Map<Long, Long> = emptyMap(),
)

private fun Collection<Set<String>>.flattenToSet(): Set<String> {
    return mutableSetOf<String>().apply {
        this@flattenToSet.forEach { ids -> addAll(ids) }
    }
}
