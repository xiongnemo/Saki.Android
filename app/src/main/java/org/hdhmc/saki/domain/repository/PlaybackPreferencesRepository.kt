package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.BufferStrategy
import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import kotlinx.coroutines.flow.Flow

interface PlaybackPreferencesRepository {
    fun observePreferences(): Flow<PlaybackPreferences>

    suspend fun getPreferences(): PlaybackPreferences

    suspend fun updateStreamQuality(quality: StreamQuality)

    suspend fun updateAdaptiveQuality(enabled: Boolean)

    suspend fun updateWifiStreamQuality(quality: StreamQuality)

    suspend fun updateMobileStreamQuality(quality: StreamQuality)

    suspend fun updateSoundBalancing(mode: SoundBalancingMode)

    suspend fun updateStreamCacheSizeMb(sizeMb: Int)

    suspend fun updateBluetoothLyrics(enabled: Boolean)

    suspend fun updateBufferStrategy(strategy: BufferStrategy)

    suspend fun updateCustomBufferSeconds(seconds: Int)

    suspend fun updateImageCacheSizeMb(sizeMb: Int)

    suspend fun updateShuffleState(seed: Long, anchorIndex: Int)

    suspend fun clearShuffleState()

    /** Returns (seed, anchorIndex) or null if no shuffle state saved. */
    suspend fun getShuffleState(): Pair<Long, Int>?
}
