package com.anzupop.saki.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anzupop.saki.android.domain.model.AppLanguage
import com.anzupop.saki.android.domain.model.AppPreferences
import com.anzupop.saki.android.domain.model.TextScale
import com.anzupop.saki.android.domain.repository.AppPreferencesRepository
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

    companion object {
        val KEY_TEXT_SCALE = stringPreferencesKey("text_scale")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
    }
}

private fun Preferences.toAppPreferences() = AppPreferences(
    textScale = TextScale.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE]),
    language = AppLanguage.fromTag(this[DataStoreAppPreferencesRepository.KEY_LANGUAGE]),
)
