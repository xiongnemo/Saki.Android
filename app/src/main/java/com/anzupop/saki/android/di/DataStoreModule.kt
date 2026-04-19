package com.anzupop.saki.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import com.anzupop.saki.android.data.local.dao.AppPreferencesDao
import com.anzupop.saki.android.data.local.dao.PlaybackPreferencesDao
import com.anzupop.saki.android.data.repository.DataStoreAppPreferencesRepository
import com.anzupop.saki.android.data.repository.DataStorePlaybackPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    produceMigrations = { context ->
        // DataStore migrations run before first read, avoiding race conditions
        listOf(
            RoomToDataStoreMigration(
                context = context,
            ),
        )
    },
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
