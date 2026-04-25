package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.AppLanguage
import com.anzupop.saki.android.domain.model.AppPreferences
import com.anzupop.saki.android.domain.model.TextScale
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun getPreferences(): AppPreferences

    suspend fun updateTextScale(textScale: TextScale)

    suspend fun updateLanguage(language: AppLanguage)
}
