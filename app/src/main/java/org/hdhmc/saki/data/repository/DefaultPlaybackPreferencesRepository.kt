package org.hdhmc.saki.data.repository

import org.hdhmc.saki.data.local.dao.PlaybackPreferencesDao
import org.hdhmc.saki.data.local.entity.PlaybackPreferencesEntity
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.DEFAULT_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.BufferStrategy
import org.hdhmc.saki.domain.model.MAX_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.MIN_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.STREAM_CACHE_SIZE_STEP_MB
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DefaultPlaybackPreferencesRepository @Inject constructor(
    private val playbackPreferencesDao: PlaybackPreferencesDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaybackPreferencesRepository {
    override fun observePreferences(): Flow<PlaybackPreferences> {
        return playbackPreferencesDao.observePreferences()
            .map { entity -> entity?.toDomain() ?: PlaybackPreferences() }
    }

    override suspend fun getPreferences(): PlaybackPreferences = withContext(ioDispatcher) {
        playbackPreferencesDao.getPreferences()?.toDomain() ?: PlaybackPreferences()
    }

    override suspend fun updateStreamQuality(quality: StreamQuality): Unit = withContext(ioDispatcher) {
        updatePreferences { current ->
            current.copy(streamQualityKey = quality.storageKey)
        }
    }

    override suspend fun updateAdaptiveQuality(enabled: Boolean) =
        error("Room-backed repository does not support adaptive quality. Use DataStore implementation.")
    override suspend fun updateWifiStreamQuality(quality: StreamQuality) =
        error("Room-backed repository does not support adaptive quality. Use DataStore implementation.")
    override suspend fun updateMobileStreamQuality(quality: StreamQuality) =
        error("Room-backed repository does not support adaptive quality. Use DataStore implementation.")

    override suspend fun updateSoundBalancing(mode: SoundBalancingMode): Unit = withContext(ioDispatcher) {
        updatePreferences { current ->
            current.copy(
                soundBalancingEnabled = mode != SoundBalancingMode.OFF,
                soundBalancingModeKey = mode.storageKey,
            )
        }
    }

    override suspend fun updateStreamCacheSizeMb(sizeMb: Int): Unit = withContext(ioDispatcher) {
        updatePreferences { current ->
            current.copy(streamCacheSizeMb = sizeMb.normalizeStreamCacheSizeMb())
        }
    }

    override suspend fun updateBluetoothLyrics(enabled: Boolean): Unit = withContext(ioDispatcher) {
        updatePreferences { current ->
            current.copy(bluetoothLyricsEnabled = enabled)
        }
    }

    override suspend fun updateBufferStrategy(strategy: BufferStrategy) =
        error("Room-backed repository does not support buffer settings. Use DataStore implementation.")
    override suspend fun updateCustomBufferSeconds(seconds: Int) =
        error("Room-backed repository does not support buffer settings. Use DataStore implementation.")
    override suspend fun updateImageCacheSizeMb(sizeMb: Int) =
        error("Room-backed repository does not support image cache settings. Use DataStore implementation.")

    override suspend fun updateShuffleState(seed: Long, anchorIndex: Int) = Unit
    override suspend fun clearShuffleState() = Unit
    override suspend fun getShuffleState(): Pair<Long, Int>? = null

    private suspend fun updatePreferences(
        transform: (PlaybackPreferencesEntity) -> PlaybackPreferencesEntity,
    ) {
        val current = playbackPreferencesDao.getPreferences() ?: PlaybackPreferencesEntity(
            streamQualityKey = StreamQuality.ORIGINAL.storageKey,
            soundBalancingEnabled = false,
            soundBalancingModeKey = SoundBalancingMode.OFF.storageKey,
            streamCacheSizeMb = DEFAULT_STREAM_CACHE_SIZE_MB,
        )
        playbackPreferencesDao.upsertPreferences(transform(current))
    }
}

private fun PlaybackPreferencesEntity.toDomain(): PlaybackPreferences {
    return PlaybackPreferences(
        streamQuality = StreamQuality.fromStorageKey(streamQualityKey),
        soundBalancingMode = soundBalancingModeKey
            .takeIf(String::isNotBlank)
            ?.let(SoundBalancingMode::fromStorageKey)
            ?: if (soundBalancingEnabled) SoundBalancingMode.MEDIUM else SoundBalancingMode.OFF,
        streamCacheSizeMb = streamCacheSizeMb.normalizeStreamCacheSizeMb(),
        bluetoothLyricsEnabled = bluetoothLyricsEnabled,
    )
}

private fun Int.normalizeStreamCacheSizeMb(): Int {
    val clamped = coerceIn(MIN_STREAM_CACHE_SIZE_MB, MAX_STREAM_CACHE_SIZE_MB)
    val stepsFromMin = ((clamped - MIN_STREAM_CACHE_SIZE_MB) / STREAM_CACHE_SIZE_STEP_MB.toDouble()).toInt()
    val lower = MIN_STREAM_CACHE_SIZE_MB + (stepsFromMin * STREAM_CACHE_SIZE_STEP_MB)
    val upper = (lower + STREAM_CACHE_SIZE_STEP_MB).coerceAtMost(MAX_STREAM_CACHE_SIZE_MB)
    return if (clamped - lower < upper - clamped) lower else upper
}
