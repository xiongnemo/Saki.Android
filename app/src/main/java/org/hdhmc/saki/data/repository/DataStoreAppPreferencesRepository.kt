package org.hdhmc.saki.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.domain.repository.AppPreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreAppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesRepository {

    override fun observePreferences(): Flow<AppPreferences> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it.toAppPreferences() }

    override suspend fun getPreferences(): AppPreferences =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
            .toAppPreferences()

    override suspend fun updateTextScale(textScale: TextScale) {
        dataStore.edit { it[KEY_TEXT_SCALE] = textScale.storageKey }
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.tag }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = themeMode.storageKey }
    }

    override suspend fun updateAlbumViewMode(mode: AlbumViewMode) {
        dataStore.edit { it[KEY_ALBUM_VIEW_MODE] = mode.storageKey }
    }

    companion object {
        val KEY_TEXT_SCALE = stringPreferencesKey("text_scale")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ALBUM_VIEW_MODE = stringPreferencesKey("album_view_mode")
    }
}

private fun Preferences.toAppPreferences() = AppPreferences(
    textScale = TextScale.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE]),
    language = AppLanguage.fromTag(this[DataStoreAppPreferencesRepository.KEY_LANGUAGE]),
    themeMode = ThemeMode.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_THEME_MODE]),
    albumViewMode = AlbumViewMode.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_ALBUM_VIEW_MODE]),
)
