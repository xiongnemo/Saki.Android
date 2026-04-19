package com.anzupop.saki.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anzupop.saki.android.domain.model.DEFAULT_STREAM_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.MAX_STREAM_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.MIN_STREAM_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.PlaybackPreferences
import com.anzupop.saki.android.domain.model.STREAM_CACHE_SIZE_STEP_MB
import com.anzupop.saki.android.domain.model.SoundBalancingMode
import com.anzupop.saki.android.domain.model.StreamQuality
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DataStorePlaybackPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PlaybackPreferencesRepository {

    override fun observePreferences(): Flow<PlaybackPreferences> =
        dataStore.data.map { it.toPlaybackPreferences() }

    override suspend fun getPreferences(): PlaybackPreferences =
        dataStore.data.first().toPlaybackPreferences()

    override suspend fun updateStreamQuality(quality: StreamQuality) {
        dataStore.edit { it[KEY_STREAM_QUALITY] = quality.storageKey }
    }

    override suspend fun updateSoundBalancing(mode: SoundBalancingMode) {
        dataStore.edit { it[KEY_SOUND_BALANCING_MODE] = mode.storageKey }
    }

    override suspend fun updateStreamCacheSizeMb(sizeMb: Int) {
        dataStore.edit { it[KEY_STREAM_CACHE_SIZE_MB] = sizeMb.normalizeStreamCacheSizeMb() }
    }

    override suspend fun updateBluetoothLyrics(enabled: Boolean) {
        dataStore.edit { it[KEY_BLUETOOTH_LYRICS] = enabled }
    }

    companion object {
        val KEY_STREAM_QUALITY = stringPreferencesKey("stream_quality")
        val KEY_SOUND_BALANCING_MODE = stringPreferencesKey("sound_balancing_mode")
        val KEY_STREAM_CACHE_SIZE_MB = intPreferencesKey("stream_cache_size_mb")
        val KEY_BLUETOOTH_LYRICS = booleanPreferencesKey("bluetooth_lyrics_enabled")
    }
}

private fun Preferences.toPlaybackPreferences() = PlaybackPreferences(
    streamQuality = StreamQuality.fromStorageKey(
        this[DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY],
    ),
    soundBalancingMode = SoundBalancingMode.fromStorageKey(
        this[DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE],
    ),
    streamCacheSizeMb = (this[DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB]
        ?: DEFAULT_STREAM_CACHE_SIZE_MB).normalizeStreamCacheSizeMb(),
    bluetoothLyricsEnabled = this[DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS] ?: false,
)

private fun Int.normalizeStreamCacheSizeMb(): Int {
    val clamped = coerceIn(MIN_STREAM_CACHE_SIZE_MB, MAX_STREAM_CACHE_SIZE_MB)
    val stepsFromMin = ((clamped - MIN_STREAM_CACHE_SIZE_MB) / STREAM_CACHE_SIZE_STEP_MB.toDouble()).toInt()
    val lower = MIN_STREAM_CACHE_SIZE_MB + (stepsFromMin * STREAM_CACHE_SIZE_STEP_MB)
    val upper = (lower + STREAM_CACHE_SIZE_STEP_MB).coerceAtMost(MAX_STREAM_CACHE_SIZE_MB)
    return if (clamped - lower < upper - clamped) lower else upper
}
