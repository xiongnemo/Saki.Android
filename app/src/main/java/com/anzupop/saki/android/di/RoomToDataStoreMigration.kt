package com.anzupop.saki.android.di

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.room.Room
import com.anzupop.saki.android.data.local.database.SakiDatabase
import com.anzupop.saki.android.data.repository.DataStoreAppPreferencesRepository
import com.anzupop.saki.android.data.repository.DataStorePlaybackPreferencesRepository
import com.anzupop.saki.android.domain.model.SoundBalancingMode

import androidx.datastore.preferences.core.booleanPreferencesKey

private val KEY_ROOM_MIGRATION_DONE = booleanPreferencesKey("room_migration_done")

/**
 * One-time migration from Room preferences tables to DataStore.
 * Runs before the first DataStore read, so no race with ViewModel.
 */
class RoomToDataStoreMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        return currentData[KEY_ROOM_MIGRATION_DONE] != true
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        if (!context.getDatabasePath("saki.db").exists()) {
            // Fresh install, no Room data to migrate
            return currentData.toMutablePreferences().apply {
                this[KEY_ROOM_MIGRATION_DONE] = true
            }.toPreferences()
        }

        val db = Room.databaseBuilder(context, SakiDatabase::class.java, "saki.db")
            .addMigrations(*DatabaseModule.allMigrations())
            .build()

        val result = currentData.toMutablePreferences()
        try {
            migrateAppPreferences(db, result)
            migratePlaybackPreferences(db, result)
        } finally {
            db.close()
        }
        result[KEY_ROOM_MIGRATION_DONE] = true
        return result.toPreferences()
    }

    override suspend fun cleanUp() {
        // Room tables kept for now; can be dropped in a future DB migration
    }

    private suspend fun migrateAppPreferences(db: SakiDatabase, ds: MutablePreferences) {
        val entity = db.appPreferencesDao().getPreferences() ?: return
        ds[DataStoreAppPreferencesRepository.KEY_ONBOARDING_COMPLETED] = entity.onboardingCompleted
        ds[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE] = entity.textScaleKey
    }

    private suspend fun migratePlaybackPreferences(db: SakiDatabase, ds: MutablePreferences) {
        val entity = db.playbackPreferencesDao().getPreferences() ?: return
        ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY] = entity.streamQualityKey

        // Preserve legacy fallback: blank modeKey + enabled=true → MEDIUM
        val modeKey = if (entity.soundBalancingModeKey.isNotBlank()) {
            entity.soundBalancingModeKey
        } else if (entity.soundBalancingEnabled) {
            SoundBalancingMode.MEDIUM.storageKey
        } else {
            SoundBalancingMode.OFF.storageKey
        }
        ds[DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE] = modeKey

        ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB] = entity.streamCacheSizeMb
        ds[DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS] = entity.bluetoothLyricsEnabled
    }
}
