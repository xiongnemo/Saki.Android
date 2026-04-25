package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.TextScale
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun getPreferences(): AppPreferences

    suspend fun updateTextScale(textScale: TextScale)

    suspend fun updateLanguage(language: AppLanguage)
}
