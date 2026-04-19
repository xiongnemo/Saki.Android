package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.PlaybackPreferences
import com.anzupop.saki.android.domain.model.SoundBalancingMode
import com.anzupop.saki.android.domain.model.StreamQuality
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
}
