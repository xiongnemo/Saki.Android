package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.AppPreferences
import com.anzupop.saki.android.domain.model.TextScale
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun getPreferences(): AppPreferences

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun updateTextScale(textScale: TextScale)
}
