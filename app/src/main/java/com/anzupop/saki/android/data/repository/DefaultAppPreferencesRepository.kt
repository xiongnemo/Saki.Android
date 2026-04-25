package com.anzupop.saki.android.data.repository

import com.anzupop.saki.android.data.local.dao.AppPreferencesDao
import com.anzupop.saki.android.data.local.entity.AppPreferencesEntity
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.AppLanguage
import com.anzupop.saki.android.domain.model.AppPreferences
import com.anzupop.saki.android.domain.model.TextScale
import com.anzupop.saki.android.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DefaultAppPreferencesRepository @Inject constructor(
    private val appPreferencesDao: AppPreferencesDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppPreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> {
        return appPreferencesDao.observePreferences()
            .map { entity -> entity?.toDomain() ?: AppPreferences() }
    }

    override suspend fun getPreferences(): AppPreferences = withContext(ioDispatcher) {
        appPreferencesDao.getPreferences()?.toDomain() ?: AppPreferences()
    }

    override suspend fun setOnboardingCompleted(completed: Boolean): Unit = withContext(ioDispatcher) {
        val current = appPreferencesDao.getPreferences() ?: AppPreferencesEntity(
            onboardingCompleted = false,
        )
        appPreferencesDao.upsertPreferences(current.copy(onboardingCompleted = completed))
    }

    override suspend fun updateTextScale(textScale: TextScale): Unit = withContext(ioDispatcher) {
        val current = appPreferencesDao.getPreferences() ?: AppPreferencesEntity(
            onboardingCompleted = false,
        )
        appPreferencesDao.upsertPreferences(current.copy(textScaleKey = textScale.storageKey))
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        // Language preference is only supported via DataStore; no-op here
    }
}

private fun AppPreferencesEntity.toDomain(): AppPreferences {
    return AppPreferences(
        hasCompletedOnboarding = onboardingCompleted,
        textScale = TextScale.fromStorageKey(textScaleKey),
    )
}
