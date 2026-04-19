package com.anzupop.saki.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anzupop.saki.android.domain.model.AppPreferences
import com.anzupop.saki.android.domain.model.TextScale
import com.anzupop.saki.android.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreAppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesRepository {

    override fun observePreferences(): Flow<AppPreferences> =
        dataStore.data.map { it.toAppPreferences() }

    override suspend fun getPreferences(): AppPreferences =
        dataStore.data.first().toAppPreferences()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun updateTextScale(textScale: TextScale) {
        dataStore.edit { it[KEY_TEXT_SCALE] = textScale.storageKey }
    }

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_TEXT_SCALE = stringPreferencesKey("text_scale")
    }
}

private fun Preferences.toAppPreferences() = AppPreferences(
    hasCompletedOnboarding = this[DataStoreAppPreferencesRepository.KEY_ONBOARDING_COMPLETED] ?: false,
    textScale = TextScale.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE]),
)
