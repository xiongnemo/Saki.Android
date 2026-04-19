package com.anzupop.saki.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.anzupop.saki.android.data.local.dao.AppPreferencesDao
import com.anzupop.saki.android.data.local.dao.PlaybackPreferencesDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val KEY_MIGRATION_DONE = booleanPreferencesKey("room_migration_done")

@Singleton
class PreferencesMigration @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val appPreferencesDao: AppPreferencesDao,
    private val playbackPreferencesDao: PlaybackPreferencesDao,
) {
    suspend fun migrateIfNeeded() {
        val prefs = dataStore.data.first()
        if (prefs[KEY_MIGRATION_DONE] == true) return

        val appEntity = appPreferencesDao.getPreferences()
        val playbackEntity = playbackPreferencesDao.getPreferences()

        dataStore.edit { ds ->
            if (appEntity != null) {
                ds[DataStoreAppPreferencesRepository.KEY_ONBOARDING_COMPLETED] = appEntity.onboardingCompleted
                ds[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE] = appEntity.textScaleKey
            }
            if (playbackEntity != null) {
                ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY] = playbackEntity.streamQualityKey
                ds[DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE] = playbackEntity.soundBalancingModeKey
                ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB] = playbackEntity.streamCacheSizeMb
                ds[DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS] = playbackEntity.bluetoothLyricsEnabled
            }
            ds[KEY_MIGRATION_DONE] = true
        }
    }
}
