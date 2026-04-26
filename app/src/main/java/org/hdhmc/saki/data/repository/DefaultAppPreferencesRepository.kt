package org.hdhmc.saki.data.repository

import org.hdhmc.saki.data.local.dao.AppPreferencesDao
import org.hdhmc.saki.data.local.entity.AppPreferencesEntity
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.domain.repository.AppPreferencesRepository
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

    override suspend fun updateTextScale(textScale: TextScale): Unit = withContext(ioDispatcher) {
        val current = appPreferencesDao.getPreferences() ?: AppPreferencesEntity(
            onboardingCompleted = false,
        )
        appPreferencesDao.upsertPreferences(current.copy(textScaleKey = textScale.storageKey))
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        // Language preference is only supported via DataStore; no-op here
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        // Theme preference is only supported via DataStore; no-op here
    }

    override suspend fun updateAlbumViewMode(mode: AlbumViewMode) {
        // Album view mode preference is only supported via DataStore; no-op here
    }

    override suspend fun updateDefaultBrowseTab(tab: DefaultBrowseTab) {
        // Default browse tab preference is only supported via DataStore; no-op here
    }

    override suspend fun updateDefaultAlbumFeed(feed: AlbumListType) {
        // Default album feed preference is only supported via DataStore; no-op here
    }

    override suspend fun updateLastSelectedServerId(serverId: Long) {
        // Last selected server is only supported via DataStore; no-op here
    }
}

private fun AppPreferencesEntity.toDomain(): AppPreferences {
    return AppPreferences(
        textScale = TextScale.fromStorageKey(textScaleKey),
    )
}
