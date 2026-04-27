package org.hdhmc.saki.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.DefaultBrowseTab
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
import org.json.JSONArray

private const val MAX_RECENT_SEARCH_QUERIES = 20

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

    override suspend fun updateDefaultBrowseTab(tab: DefaultBrowseTab) {
        dataStore.edit { it[KEY_DEFAULT_BROWSE_TAB] = tab.storageKey }
    }

    override suspend fun updateDefaultAlbumFeed(feed: AlbumListType) {
        dataStore.edit { it[KEY_DEFAULT_ALBUM_FEED] = feed.apiValue }
    }

    override suspend fun updateLastSelectedServerId(serverId: Long?) {
        dataStore.edit {
            if (serverId == null) {
                it.remove(KEY_LAST_SELECTED_SERVER_ID)
            } else {
                it[KEY_LAST_SELECTED_SERVER_ID] = serverId
            }
        }
    }

    override suspend fun addRecentSearchQuery(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return

        updateRecentSearchQueries { queries ->
            (listOf(trimmedQuery) + queries.filterNot { it.equals(trimmedQuery, ignoreCase = true) })
                .take(MAX_RECENT_SEARCH_QUERIES)
        }
    }

    override suspend fun removeRecentSearchQuery(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return

        updateRecentSearchQueries { queries ->
            queries.filterNot { it.equals(trimmedQuery, ignoreCase = true) }
        }
    }

    override suspend fun clearRecentSearchQueries() {
        dataStore.edit { it.remove(KEY_RECENT_SEARCH_QUERIES) }
    }

    private suspend fun updateRecentSearchQueries(transform: (List<String>) -> List<String>) {
        dataStore.edit { preferences ->
            val updated = transform(preferences[KEY_RECENT_SEARCH_QUERIES].decodeRecentSearchQueries())
                .normalizedRecentSearchQueries()
            if (updated.isEmpty()) {
                preferences.remove(KEY_RECENT_SEARCH_QUERIES)
            } else {
                preferences[KEY_RECENT_SEARCH_QUERIES] = updated.encodeRecentSearchQueries()
            }
        }
    }

    companion object {
        val KEY_TEXT_SCALE = stringPreferencesKey("text_scale")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ALBUM_VIEW_MODE = stringPreferencesKey("album_view_mode")
        val KEY_DEFAULT_BROWSE_TAB = stringPreferencesKey("default_browse_tab")
        val KEY_DEFAULT_ALBUM_FEED = stringPreferencesKey("default_album_feed")
        val KEY_LAST_SELECTED_SERVER_ID = longPreferencesKey("last_selected_server_id")
        val KEY_RECENT_SEARCH_QUERIES = stringPreferencesKey("recent_search_queries")
    }
}

private fun Preferences.toAppPreferences() = AppPreferences(
    textScale = TextScale.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE]),
    language = AppLanguage.fromTag(this[DataStoreAppPreferencesRepository.KEY_LANGUAGE]),
    themeMode = ThemeMode.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_THEME_MODE]),
    albumViewMode = AlbumViewMode.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_ALBUM_VIEW_MODE]),
    defaultBrowseTab = DefaultBrowseTab.fromStorageKey(this[DataStoreAppPreferencesRepository.KEY_DEFAULT_BROWSE_TAB]),
    defaultAlbumFeed = AlbumListType.fromApiValue(
        this[DataStoreAppPreferencesRepository.KEY_DEFAULT_ALBUM_FEED],
    )?.takeIf { it in AlbumListType.defaultBrowseFeeds } ?: AlbumListType.NEWEST,
    lastSelectedServerId = this[DataStoreAppPreferencesRepository.KEY_LAST_SELECTED_SERVER_ID],
    recentSearchQueries = this[DataStoreAppPreferencesRepository.KEY_RECENT_SEARCH_QUERIES]
        .decodeRecentSearchQueries(),
)

private fun String?.decodeRecentSearchQueries(): List<String> {
    if (isNullOrBlank()) return emptyList()

    return runCatching {
        val array = JSONArray(this)
        buildList<String> {
            for (index in 0 until array.length()) {
                val query = array.optString(index).trim()
                if (query.isNotEmpty() && !any { it.equals(query, ignoreCase = true) }) {
                    add(query)
                }
            }
        }.take(MAX_RECENT_SEARCH_QUERIES)
    }.getOrDefault(emptyList())
}

private fun List<String>.normalizedRecentSearchQueries(): List<String> {
    return fold(emptyList<String>()) { acc, query ->
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || acc.any { it.equals(trimmedQuery, ignoreCase = true) }) {
            acc
        } else {
            acc + trimmedQuery
        }
    }.take(MAX_RECENT_SEARCH_QUERIES)
}

private fun List<String>.encodeRecentSearchQueries(): String {
    val array = JSONArray()
    forEach { array.put(it) }
    return array.toString()
}
