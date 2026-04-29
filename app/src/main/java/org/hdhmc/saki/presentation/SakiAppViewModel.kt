package org.hdhmc.saki.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.hdhmc.saki.R
import org.hdhmc.saki.data.remote.EndpointSelector
import org.hdhmc.saki.data.repository.ConfigBackupManager
import org.hdhmc.saki.data.repository.ImportResult
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.CacheStorageSummary
import org.hdhmc.saki.domain.model.CachedArtistDetail
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot
import org.hdhmc.saki.domain.model.regroupByLocale
import org.hdhmc.saki.domain.model.PlaybackProgressState
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.domain.repository.AppPreferencesRepository
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.LibraryCacheRepository
import org.hdhmc.saki.domain.repository.LocalPlayQueueRepository
import org.hdhmc.saki.domain.repository.PlaybackManager
import org.hdhmc.saki.playback.LyricsHolder
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.ServerConfigRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import org.hdhmc.saki.domain.repository.StreamCacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ALBUMS_PAGE_SIZE = 36

@HiltViewModel
@OptIn(FlowPreview::class)
class SakiAppViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val subsonicRepository: SubsonicRepository,
    private val cachedSongRepository: CachedSongRepository,
    private val streamCacheRepository: StreamCacheRepository,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val libraryCacheRepository: LibraryCacheRepository,
    private val localPlayQueueRepository: LocalPlayQueueRepository,
    private val playbackManager: PlaybackManager,
    private val lyricsHolder: LyricsHolder,
    private val endpointSelector: EndpointSelector,
    private val configBackupManager: ConfigBackupManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SakiAppUiState())
    private val snackbarMessages = MutableSharedFlow<SnackbarMessage>(extraBufferCapacity = 1)
    private val openNowPlayingRequestsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val searchQueryFlow = MutableStateFlow("")
    private var lastLoadedServerId: Long? = null
    private var appliedDefaultBrowsePreference = false

    private val mutableEndpointStatus = MutableStateFlow(EndpointStatus())
    val endpointStatus: StateFlow<EndpointStatus> = mutableEndpointStatus.asStateFlow()

    val uiState = mutableUiState.asStateFlow()
    val playbackProgress: StateFlow<PlaybackProgressState> = playbackManager.playbackProgress
    val messages = snackbarMessages.asSharedFlow()
    val openNowPlayingRequests = openNowPlayingRequestsFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            endpointSelector.probeVersion.collectLatest { refreshEndpointStatus() }
        }
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collectLatest { preferences ->
                val shouldApplyDefaultBrowse = !appliedDefaultBrowsePreference
                if (shouldApplyDefaultBrowse) {
                    appliedDefaultBrowsePreference = true
                }
                mutableUiState.update { state ->
                    state.copy(
                        isAppReady = true,
                        textScale = preferences.textScale,
                        appPreferences = preferences,
                        selectedBrowseSection = if (shouldApplyDefaultBrowse) {
                            preferences.defaultBrowseTab.toBrowseSection()
                        } else {
                            state.selectedBrowseSection
                        },
                        selectedAlbumFeed = if (shouldApplyDefaultBrowse) {
                            preferences.defaultAlbumFeed
                        } else {
                            state.selectedAlbumFeed
                        },
                    )
                }
                if (shouldApplyDefaultBrowse) {
                    uiState.value.selectedServerId?.let { serverId ->
                        loadBrowseSectionIfNeeded(serverId, preferences.defaultBrowseTab.toBrowseSection())
                    }
                }
                // Apply saved locale when preference changes (no-ops if already matching)
                if (preferences.language != AppLanguage.SYSTEM) {
                    val locales = androidx.core.os.LocaleListCompat.forLanguageTags(preferences.language.tag)
                    val current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                    if (current != locales) {
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                    }
                }
                // Apply saved theme mode
                val nightMode = preferences.themeMode.toNightMode()
                if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != nightMode) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
        }

        viewModelScope.launch {
            serverConfigRepository.observeServerConfigs().collectLatest { servers ->
                handleServerConfigsChanged(servers)
            }
        }

        viewModelScope.launch {
            cachedSongRepository.observeCachedSongs().collectLatest { songs ->
                mutableUiState.update { state ->
                    state.copy(cachedSongs = songs)
                }
                refreshCacheStorageSummary(uiState.value.selectedServerId)
            }
        }

        viewModelScope.launch {
            streamCacheRepository.observeCacheVersion().collectLatest {
                refreshCacheStorageSummary(uiState.value.selectedServerId)
            }
        }

        viewModelScope.launch {
            playbackManager.playbackState.collectLatest { playbackState ->
                mutableUiState.update { state ->
                    state.copy(playbackState = playbackState)
                }
            }
        }

        // Fetch lyrics when current track changes
        viewModelScope.launch {
            playbackManager.playbackState
                .map { state ->
                    state.currentItem?.let {
                        val sid = it.serverId ?: return@let null
                        sid to it.songId
                    }
                }
                .distinctUntilChanged()
                .collectLatest { pair ->
                    if (pair == null) {
                        mutableUiState.update { it.copy(currentLyrics = null) }
                        lyricsHolder.update(null)
                        return@collectLatest
                    }
                    val (serverId, songId) = pair
                    mutableUiState.update { it.copy(currentLyrics = null) }
                    lyricsHolder.update(null)
                    try {
                        val lyrics = subsonicRepository.getLyrics(serverId, songId).data
                        mutableUiState.update { it.copy(currentLyrics = lyrics) }
                        lyricsHolder.update(lyrics)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Lyrics not available — silently ignore
                    }
                }
        }

        viewModelScope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.streamQuality }
                .distinctUntilChanged()
                .collectLatest {
                    refreshCacheStorageSummary(uiState.value.selectedServerId)
                }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(350)
                .map(String::trim)
                .distinctUntilChanged()
                .collectLatest(::performSearch)
        }
    }

    fun selectBrowseSection(section: BrowseSection) {
        mutableUiState.update { state ->
            state.copy(selectedBrowseSection = section)
        }
        val serverId = uiState.value.selectedServerId ?: return
        loadBrowseSectionIfNeeded(serverId, section)
    }

    private fun loadBrowseSectionIfNeeded(serverId: Long, section: BrowseSection) {
        when (section) {
            BrowseSection.ARTISTS -> if (uiState.value.libraryIndexes == null) loadArtists(serverId)
            BrowseSection.ALBUMS -> if (uiState.value.albums.isEmpty()) loadAlbums(serverId, uiState.value.selectedAlbumFeed)
            BrowseSection.PLAYLISTS -> if (uiState.value.playlists.isEmpty()) loadPlaylists(serverId)
            BrowseSection.SONGS -> if (uiState.value.songs.isEmpty()) loadSongs(serverId)
        }
    }

    fun setSearchActive(active: Boolean) {
        if (active) {
            mutableUiState.update { state ->
                state.copy(isSearchActive = true)
            }
            return
        }

        searchQueryFlow.value = ""
        mutableUiState.update { state ->
            state.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = SearchResults(),
                isSearchLoading = false,
                searchError = null,
            )
        }
    }

    fun updateSearchQuery(query: String) {
        mutableUiState.update { state ->
            state.copy(
                isSearchActive = true,
                searchQuery = query,
            )
        }
        searchQueryFlow.value = query
    }

    fun removeRecentSearchQuery(query: String) {
        viewModelScope.launch {
            runCatching { appPreferencesRepository.removeRecentSearchQuery(query) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w("SakiApp", "Failed to remove recent search query", throwable)
                }
        }
    }

    fun clearRecentSearchQueries() {
        viewModelScope.launch {
            runCatching { appPreferencesRepository.clearRecentSearchQueries() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w("SakiApp", "Failed to clear recent search queries", throwable)
                }
        }
    }

    fun updateTextScale(textScale: TextScale) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateTextScale(textScale)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_text_size_set, UiText.resource(textScale.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_text_size)))
            }
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateLanguage(language)
            }.onSuccess {
                val locales = when (language) {
                    AppLanguage.SYSTEM -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                    else -> androidx.core.os.LocaleListCompat.forLanguageTags(language.tag)
                }
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }

    private fun ThemeMode.toNightMode(): Int = when (this) {
        ThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateThemeMode(themeMode)
            }.onSuccess {
                val nightMode = themeMode.toNightMode()
                if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != nightMode) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
        }
    }

    fun updateAlbumViewMode(mode: AlbumViewMode) {
        viewModelScope.launch {
            appPreferencesRepository.updateAlbumViewMode(mode)
        }
    }

    fun updateDefaultBrowseTab(tab: DefaultBrowseTab) {
        viewModelScope.launch {
            appPreferencesRepository.updateDefaultBrowseTab(tab)
        }
    }

    fun updateDefaultAlbumFeed(feed: AlbumListType) {
        viewModelScope.launch {
            appPreferencesRepository.updateDefaultAlbumFeed(feed)
        }
    }

    fun openArtistFromPlayback(
        serverId: Long?,
        artistId: String?,
    ) {
        if (serverId == null || artistId.isNullOrBlank()) return

        if (uiState.value.selectedServerId != serverId) {
            selectServer(serverId)
        }
        openArtist(artistId)
    }

    fun openAlbumFromPlayback(
        serverId: Long?,
        albumId: String?,
    ) {
        if (serverId == null || albumId.isNullOrBlank()) return

        if (uiState.value.selectedServerId != serverId) {
            selectServer(serverId)
        }
        openAlbum(albumId)
    }

    fun selectServer(serverId: Long) {
        val previousServerId = uiState.value.selectedServerId
        if (previousServerId == serverId) return

        clearSearchState()
        mutableUiState.update { state ->
            state.copy(
                selectedServerId = serverId,
                selectedArtist = null,
                selectedArtistTopSongs = emptyList(),
                albumFeeds = emptyAlbumFeedStates(),
                selectedAlbum = null,
                selectedPlaylist = null,
            )
        }
        viewModelScope.launch {
            appPreferencesRepository.updateLastSelectedServerId(serverId)
            refreshCacheStorageSummary(serverId)
        }
        loadServerContent(serverId, forceRefresh = true)
    }

    fun selectAlbumFeed(type: AlbumListType) {
        val serverId = uiState.value.selectedServerId ?: return
        mutableUiState.update { state ->
            state.copy(
                selectedAlbumFeed = type,
                albumFeeds = state.albumFeeds.updateFeed(type) { feedState ->
                    feedState.copy(
                        isLoadingMore = false,
                        error = null,
                    )
                },
            )
        }
        loadAlbums(serverId, type)
    }

    fun loadMoreAlbums() {
        val state = uiState.value
        val serverId = state.selectedServerId ?: return
        val type = state.selectedAlbumFeed
        val feedState = state.albumFeedState(type)
        if (
            !type.supportsPagination() ||
            !feedState.hasMore ||
            feedState.isLoading ||
            feedState.isLoadingMore
        ) {
            return
        }
        val offset = feedState.offset

        viewModelScope.launch {
            mutableUiState.update { current ->
                current.copy(
                    albumFeeds = current.albumFeeds.updateFeed(type) {
                        it.copy(isLoadingMore = true, error = null)
                    },
                )
            }

            runCatching {
                subsonicRepository.getAlbumList(
                    serverId = serverId,
                    type = type,
                    size = ALBUMS_PAGE_SIZE,
                    offset = offset,
                ).data
            }.onSuccess { page ->
                if (uiState.value.selectedServerId == serverId) {
                    var mergedAlbums = emptyList<AlbumSummary>()
                    mutableUiState.update { current ->
                        val currentFeed = current.albumFeedState(type)
                        mergedAlbums = (currentFeed.albums + page).distinctBy(AlbumSummary::id)
                        val addedAny = mergedAlbums.size > currentFeed.albums.size
                        current.copy(
                            albumFeeds = current.albumFeeds.updateFeed(type) {
                                it.copy(
                                    albums = mergedAlbums,
                                    offset = mergedAlbums.size,
                                    hasMore = page.size >= ALBUMS_PAGE_SIZE && addedAny,
                                    isLoadingMore = false,
                                    error = null,
                                )
                            },
                        )
                    }
                    runCatching { libraryCacheRepository.saveAlbums(serverId, type, mergedAlbums) }
                        .onFailure { Log.w("SakiApp", "Failed to cache albums", it) }
                }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { current ->
                        current.copy(
                            albumFeeds = current.albumFeeds.updateFeed(type) {
                                it.copy(
                                    isLoadingMore = false,
                                    error = throwable.localizedOr(R.string.error_load_albums),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    fun refreshCurrentTab() {
        val serverId = uiState.value.selectedServerId ?: return
        when (uiState.value.selectedBrowseSection) {
            BrowseSection.ARTISTS -> loadArtists(serverId, forceRefresh = true)
            BrowseSection.ALBUMS -> loadAlbums(serverId, uiState.value.selectedAlbumFeed, forceRefresh = true)
            BrowseSection.PLAYLISTS -> loadPlaylists(serverId, forceRefresh = true)
            BrowseSection.SONGS -> loadSongs(serverId, forceRefresh = true)
        }
    }

    fun openArtist(artistId: String) {
        val serverId = uiState.value.selectedServerId ?: return
        val fallbackArtist = uiState.value.findArtistSummary(artistId)?.toArtist()
        mutableUiState.update { state ->
            state.copy(
                selectedArtist = fallbackArtist,
                selectedArtistTopSongs = emptyList(),
                isArtistLoading = true,
                artistError = null,
                selectedAlbum = null,
            )
        }

        viewModelScope.launch {
            val cached = runCatching { libraryCacheRepository.getArtistDetail(serverId, artistId) }.getOrNull()
            if (cached != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { state ->
                    state.copy(
                        selectedArtist = cached.artist,
                        selectedArtistTopSongs = cached.topSongs,
                        isArtistLoading = !endpointStatus.value.isOfflineDegraded,
                        artistError = null,
                    )
                }
            }
            if (endpointStatus.value.isOfflineDegraded) {
                if (cached == null && fallbackArtist == null) {
                    snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_cached_detail_unavailable)))
                } else if (cached == null) {
                    mutableUiState.update { state ->
                        state.copy(artistError = UiText.resource(R.string.error_cached_detail_unavailable))
                    }
                }
                mutableUiState.update { state -> state.copy(isArtistLoading = false) }
                return@launch
            }
            runCatching {
                val artist = subsonicRepository.getArtist(serverId, artistId).data
                artist to buildArtistTopSongs(serverId, artist)
            }.onSuccess { (artist, topSongs) ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            selectedArtist = artist,
                            selectedArtistTopSongs = topSongs,
                            isArtistLoading = false,
                            artistError = null,
                        )
                    }
                }
                runCatching {
                    libraryCacheRepository.saveArtistDetail(
                        serverId = serverId,
                        detail = CachedArtistDetail(artist = artist, topSongs = topSongs),
                    )
                }.onFailure { Log.w("SakiApp", "Failed to cache artist detail", it) }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            isArtistLoading = false,
                            artistError = if (cached == null && fallbackArtist == null) {
                                throwable.localizedOr(R.string.error_load_artist_details)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun closeArtist() {
        mutableUiState.update { state ->
            state.copy(
                selectedArtist = null,
                selectedArtistTopSongs = emptyList(),
                selectedAlbum = null,
                artistError = null,
                albumError = null,
            )
        }
    }

    fun openAlbum(albumId: String) {
        val serverId = uiState.value.selectedServerId ?: return
        val fallbackAlbum = uiState.value.findAlbumSummary(albumId)?.toAlbum()
        mutableUiState.update { state ->
            state.copy(
                selectedAlbum = fallbackAlbum,
                isAlbumLoading = true,
                albumError = null,
            )
        }

        viewModelScope.launch {
            val cached = runCatching { libraryCacheRepository.getAlbumDetail(serverId, albumId) }.getOrNull()
            if (cached != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { state ->
                    state.copy(
                        selectedAlbum = cached,
                        isAlbumLoading = !endpointStatus.value.isOfflineDegraded,
                        albumError = null,
                    )
                }
            }
            if (endpointStatus.value.isOfflineDegraded) {
                if (cached == null && fallbackAlbum == null) {
                    snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_cached_detail_unavailable)))
                } else if (cached == null) {
                    mutableUiState.update { state ->
                        state.copy(albumError = UiText.resource(R.string.error_cached_detail_unavailable))
                    }
                }
                mutableUiState.update { state -> state.copy(isAlbumLoading = false) }
                return@launch
            }
            runCatching {
                subsonicRepository.getAlbum(serverId, albumId).data
            }.onSuccess { album ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            selectedAlbum = album,
                            isAlbumLoading = false,
                            albumError = null,
                        )
                    }
                }
                runCatching { libraryCacheRepository.saveAlbumDetail(serverId, album) }
                    .onFailure { Log.w("SakiApp", "Failed to cache album detail", it) }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            isAlbumLoading = false,
                            albumError = if (cached == null && fallbackAlbum == null) {
                                throwable.localizedOr(R.string.error_load_album_details)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun closeAlbum() {
        mutableUiState.update { state ->
            state.copy(
                selectedAlbum = null,
                albumError = null,
            )
        }
    }

    fun openPlaylist(playlistId: String) {
        val serverId = uiState.value.selectedServerId ?: return
        val fallbackPlaylist = uiState.value.findPlaylistSummary(playlistId)?.toPlaylist()
        mutableUiState.update { state ->
            state.copy(
                selectedPlaylist = fallbackPlaylist,
                isPlaylistLoading = true,
                playlistError = null,
            )
        }

        viewModelScope.launch {
            val cached = runCatching { libraryCacheRepository.getPlaylistDetail(serverId, playlistId) }.getOrNull()
            if (cached != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { state ->
                    state.copy(
                        selectedPlaylist = cached,
                        isPlaylistLoading = !endpointStatus.value.isOfflineDegraded,
                        playlistError = null,
                    )
                }
            }
            if (endpointStatus.value.isOfflineDegraded) {
                if (cached == null && fallbackPlaylist == null) {
                    snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_cached_detail_unavailable)))
                } else if (cached == null) {
                    mutableUiState.update { state ->
                        state.copy(playlistError = UiText.resource(R.string.error_cached_detail_unavailable))
                    }
                }
                mutableUiState.update { state -> state.copy(isPlaylistLoading = false) }
                return@launch
            }
            runCatching {
                subsonicRepository.getPlaylist(serverId, playlistId).data
            }.onSuccess { playlist ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            selectedPlaylist = playlist,
                            isPlaylistLoading = false,
                            playlistError = null,
                        )
                    }
                }
                runCatching { libraryCacheRepository.savePlaylistDetail(serverId, playlist) }
                    .onFailure { Log.w("SakiApp", "Failed to cache playlist detail", it) }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            isPlaylistLoading = false,
                            playlistError = if (cached == null && fallbackPlaylist == null) {
                                throwable.localizedOr(R.string.error_load_playlist)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun closePlaylist() {
        mutableUiState.update { state ->
            state.copy(
                selectedPlaylist = null,
                playlistError = null,
            )
        }
    }

    fun playSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.playSong(serverId, song)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_playback)))
            }
        }
    }

    fun playSongs(
        songs: List<Song>,
        startIndex: Int = 0,
    ) {
        val serverId = uiState.value.selectedServerId ?: return
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playbackManager.playQueue(serverId, songs, startIndex)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_playback)))
            }
        }
    }

    fun queueSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.addToQueue(serverId, listOf(song))
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_added_to_queue, song.title)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_queue_song)))
            }
        }
    }

    fun playSongNext(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.playNext(serverId, song)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_play_next, song.title)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_reorder_queue)))
            }
        }
    }

    fun toggleSongDownload(song: Song) {
        val selectedServerId = uiState.value.selectedServerId ?: return
        val cachedSong = uiState.value.cachedSongs.firstOrNull { cached ->
            cached.serverId == selectedServerId && cached.songId == song.id
        }
        if (cachedSong != null) {
            deleteCachedSong(cachedSong.cacheId)
            return
        }
        downloadSong(song)
    }

    fun downloadSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        mutableUiState.update { state ->
            state.copy(downloadingSongIds = state.downloadingSongIds + song.id)
        }

        viewModelScope.launch {
            runCatching {
                cachedSongRepository.cacheSong(serverId, song)
            }.onSuccess { cachedSong ->
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(
                        R.string.message_saved_offline,
                        cachedSong.title,
                        UiText.resource(cachedSong.quality.labelRes()),
                    )),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_cache_song)))
            }
            mutableUiState.update { state ->
                state.copy(downloadingSongIds = state.downloadingSongIds - song.id)
            }
        }
    }

    fun playCachedSong(song: CachedSong) {
        viewModelScope.launch {
            runCatching {
                playbackManager.playCachedSong(song)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_play_cached_song)))
            }
        }
    }

    fun playCachedQueue(
        songs: List<CachedSong>,
        startIndex: Int = 0,
    ) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playbackManager.playCachedQueue(songs, startIndex)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_offline_playback)))
            }
        }
    }

    fun showOfflineSongUnavailable() {
        snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_song_unavailable_offline)))
    }

    fun deleteCachedSong(cacheId: String) {
        viewModelScope.launch {
            runCatching {
                cachedSongRepository.deleteCachedSong(cacheId)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_removed_cached_file)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_remove_cached_file)))
            }
        }
    }

    fun clearCachedSongs() {
        val targetServerId = uiState.value.selectedServerId
        viewModelScope.launch {
            runCatching {
                cachedSongRepository.clearCachedSongs(targetServerId)
            }.onSuccess { removed ->
                snackbarMessages.emit(
                    SnackbarMessage(if (removed > 0) {
                        UiText.plural(R.plurals.message_cleared_download_count, removed, removed)
                    } else {
                        UiText.resource(R.string.message_no_downloads_to_clear)
                    }),
                )
                refreshCacheStorageSummary(targetServerId)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_clear_downloads)))
            }
        }
    }

    fun clearStreamCache() {
        val targetServerId = uiState.value.selectedServerId
        viewModelScope.launch {
            runCatching {
                streamCacheRepository.clearStreamCache(targetServerId)
            }.onSuccess { removed ->
                snackbarMessages.emit(
                    SnackbarMessage(if (removed > 0) {
                        UiText.plural(R.plurals.message_cleared_stream_cache_entry_count, removed, removed)
                    } else {
                        UiText.resource(R.string.message_no_stream_cache_to_clear)
                    }),
                )
                refreshCacheStorageSummary(targetServerId)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_clear_stream_cache)))
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = appContext.cacheDir.resolve("image_cache")
                    dir.deleteRecursively()
                    dir.mkdirs()
                }
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_cover_art_cache_cleared)))
                refreshCacheStorageSummary(uiState.value.selectedServerId)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_clear_cover_art_cache)))
            }
        }
    }

    fun updateStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateStreamQuality(quality)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_stream_quality_set, UiText.resource(quality.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_stream_quality)))
            }
        }
    }

    fun updateDownloadQuality(quality: StreamQuality) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateDownloadQuality(quality)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_download_quality_set, UiText.resource(quality.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_download_quality)))
            }
        }
    }

    fun updateAdaptiveQuality(enabled: Boolean) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateAdaptiveQuality(enabled)
        }
    }

    fun updateWifiStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateWifiStreamQuality(quality)
        }
    }

    fun updateMobileStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateMobileStreamQuality(quality)
        }
    }

    fun updateSoundBalancing(mode: SoundBalancingMode) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateSoundBalancing(mode)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_sound_balancing_set, UiText.resource(mode.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_sound_balancing)))
            }
        }
    }

    fun updateStreamCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateStreamCacheSizeMb(sizeMb)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_stream_cache_limit_updated)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_stream_cache_size)))
            }
        }
    }

    fun updateBluetoothLyrics(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateBluetoothLyrics(enabled)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_bluetooth_lyrics)))
            }
        }
    }

    fun updateBufferStrategy(strategy: org.hdhmc.saki.domain.model.BufferStrategy) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateBufferStrategy(strategy)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(
                        text = UiText.resource(R.string.message_buffer_strategy_set, UiText.resource(strategy.labelRes())),
                        action = SnackbarAction.RESTART,
                    ),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_buffer_strategy)))
            }
        }
    }

    fun updateCustomBufferSeconds(seconds: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateCustomBufferSeconds(seconds)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(
                    text = UiText.resource(R.string.message_custom_buffer_set, seconds),
                    action = SnackbarAction.RESTART,
                ))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_custom_buffer)))
            }
        }
    }

    fun updateImageCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateImageCacheSizeMb(sizeMb)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(
                    text = UiText.resource(R.string.message_cover_art_cache_limit_updated),
                    action = SnackbarAction.RESTART,
                ))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_cover_art_cache_size)))
            }
        }
    }

    fun pausePlayback() {
        viewModelScope.launch {
            playbackManager.pause()
        }
    }

    fun resumePlayback() {
        viewModelScope.launch {
            playbackManager.resume()
        }
    }

    fun skipToNext() {
        viewModelScope.launch {
            playbackManager.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            playbackManager.skipToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playbackManager.seekTo(positionMs)
        }
    }

    fun cycleRepeatMode() {
        viewModelScope.launch {
            playbackManager.cycleRepeatMode()
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            playbackManager.toggleShuffle()
        }
    }

    fun skipToQueueItem(index: Int) {
        viewModelScope.launch {
            playbackManager.skipToQueueItem(index)
        }
    }

    fun removeQueueItem(index: Int) {
        viewModelScope.launch {
            playbackManager.removeQueueItem(index)
        }
    }

    private fun handleServerConfigsChanged(servers: List<ServerConfig>) {
        val previousServerId = uiState.value.selectedServerId
            ?: uiState.value.appPreferences.lastSelectedServerId
        val selectedServerId = when {
            servers.isEmpty() -> null
            previousServerId != null && servers.any { it.id == previousServerId } -> previousServerId
            else -> servers.first().id
        }
        val serverChanged = previousServerId != selectedServerId

        if (serverChanged) {
            clearSearchState()
        }
        mutableUiState.update { state ->
            state.copy(
                servers = servers,
                selectedServerId = selectedServerId,
                selectedArtist = if (serverChanged) null else state.selectedArtist,
                selectedArtistTopSongs = if (serverChanged) emptyList() else state.selectedArtistTopSongs,
                albumFeeds = if (serverChanged) emptyAlbumFeedStates() else state.albumFeeds,
                selectedAlbum = if (serverChanged) null else state.selectedAlbum,
                selectedPlaylist = if (serverChanged) null else state.selectedPlaylist,
            )
        }

        viewModelScope.launch {
            refreshCacheStorageSummary(selectedServerId)
        }

        if (selectedServerId != null && (serverChanged || lastLoadedServerId != selectedServerId)) {
            // Show cached content immediately, then probe + network refresh
            loadCachedContent(selectedServerId)
            viewModelScope.launch {
                servers.find { it.id == selectedServerId }?.let { server ->
                    endpointSelector.probe(selectedServerId, server)
                    refreshEndpointStatus()
                }
                val hasReachableEndpoint = endpointSelector.getActiveEndpointId(selectedServerId) != null &&
                    !endpointStatus.value.isOfflineDegraded
                if (uiState.value.playbackState.currentItem == null) {
                    if (hasReachableEndpoint) {
                        restorePlayQueue(selectedServerId)
                    } else {
                        restoreLocalPlayQueue(selectedServerId, offlineOnly = true)
                    }
                }
                if (hasReachableEndpoint) {
                    refreshServerContent(selectedServerId, forceRefresh = serverChanged)
                }
            }
        } else if (selectedServerId != null) {
            // Server didn't change but config may have (e.g. endpoints added/removed) — re-probe
            viewModelScope.launch {
                servers.find { it.id == selectedServerId }?.let { server ->
                    endpointSelector.registerServer(server)
                    endpointSelector.probe(selectedServerId, server)
                }
            }
        }
    }

    private suspend fun refreshCacheStorageSummary(serverId: Long?) {
        val downloadSummary = cachedSongRepository.getCacheStorageSummary(serverId)
        val fullStreamSummary = streamCacheRepository.getStreamCacheSummary(serverId)
        val imageCacheDir = appContext.cacheDir.resolve("image_cache")
        val imageCacheBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            imageCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        mutableUiState.update { state ->
            if (state.selectedServerId == serverId) {
                state.copy(
                    cacheStorageSummary = downloadSummary.copy(
                        streamCachedSongCount = fullStreamSummary.cachedSongIds.size,
                        streamCacheBytes = fullStreamSummary.bytes,
                        hasStreamingCache = true,
                        imageCacheBytes = imageCacheBytes,
                    ),
                    streamCachedSongIds = fullStreamSummary.cachedSongIds,
                )
            } else {
                state
            }
        }
    }

    private fun restorePlayQueue(serverId: Long) {
        viewModelScope.launch {
            runCatching {
                subsonicRepository.getPlayQueue(serverId).data
            }.onSuccess { savedQueue ->
                if (savedQueue.songs.isEmpty()) return@onSuccess
                if (uiState.value.playbackState.queue.isNotEmpty()) return@onSuccess
                val startIndex = if (savedQueue.currentSongId != null) {
                    savedQueue.songs.indexOfFirst { it.id == savedQueue.currentSongId }.coerceAtLeast(0)
                } else {
                    0
                }
                playbackManager.restoreQueue(
                    serverId = serverId,
                    songs = savedQueue.songs,
                    startIndex = startIndex,
                    positionMs = savedQueue.positionMs,
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                restoreLocalPlayQueueSnapshot(
                    serverId = serverId,
                    offlineOnly = endpointStatus.value.isOfflineDegraded,
                )
            }
        }
    }

    private fun restoreLocalPlayQueue(
        serverId: Long,
        offlineOnly: Boolean,
    ) {
        viewModelScope.launch {
            restoreLocalPlayQueueSnapshot(serverId, offlineOnly)
        }
    }

    private suspend fun restoreLocalPlayQueueSnapshot(
        serverId: Long,
        offlineOnly: Boolean,
    ) {
        if (uiState.value.playbackState.currentItem != null || uiState.value.playbackState.queue.isNotEmpty()) {
            return
        }
        val snapshot = localPlayQueueRepository.get(serverId) ?: return
        if (snapshot.songs.isEmpty()) return

        val restored = if (offlineOnly) {
            snapshot.offlinePlayableRestorePlan(serverId) ?: return
        } else {
            val rawStartIndex = snapshot.songs.indexOfFirst { song -> song.id == snapshot.currentSongId }
            LocalPlayQueueRestorePlan(
                songs = snapshot.songs,
                startIndex = rawStartIndex.coerceAtLeast(0),
                positionMs = if (rawStartIndex >= 0) snapshot.positionMs else 0L,
            )
        }
        playbackManager.restoreQueue(
            serverId = serverId,
            songs = restored.songs,
            startIndex = restored.startIndex,
            positionMs = restored.positionMs,
        )
    }

    private suspend fun LocalPlayQueueSnapshot.offlinePlayableRestorePlan(
        serverId: Long,
    ): LocalPlayQueueRestorePlan? {
        val localCacheQuality = StreamQuality.entries.last()
        val downloadedSongIds = cachedSongRepository.getPlayableCachedSongs(serverId, localCacheQuality).keys
        val streamCachedSongIds = songs.asSequence()
            .map(Song::id)
            .filter { songId -> streamCacheRepository.findCachedQualityKey(serverId, songId, localCacheQuality) != null }
            .toSet()
        val playableSongIds = downloadedSongIds + streamCachedSongIds
        val originalStartIndex = songs.indexOfFirst { song -> song.id == currentSongId }
            .coerceAtLeast(0)
        val playableSongs = songs.withIndex()
            .filter { (_, song) -> song.id in playableSongIds }
        if (playableSongs.isEmpty()) return null

        val startItem = playableSongs.firstOrNull { (index, _) -> index >= originalStartIndex }
            ?: playableSongs.first()
        val startIndex = playableSongs.indexOfFirst { (index, _) -> index == startItem.index }
        return LocalPlayQueueRestorePlan(
            songs = playableSongs.map { (_, song) -> song },
            startIndex = startIndex.coerceAtLeast(0),
            positionMs = if (startItem.value.id == currentSongId) positionMs else 0L,
        )
    }

    private fun loadCachedContent(serverId: Long) {
        lastLoadedServerId = serverId
        viewModelScope.launch {
            suspend fun <T> loadCachedOrNull(block: suspend () -> T): T? = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }

            val artists = loadCachedOrNull { libraryCacheRepository.getArtists(serverId) }
            if (artists != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(libraryIndexes = artists.regroupByLocale()) }
            }
            AlbumListType.entries.forEach { albumFeed ->
                val albums = loadCachedOrNull { libraryCacheRepository.getAlbums(serverId, albumFeed) }
                if (!albums.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(albumFeed) {
                                it.copy(
                                    albums = albums,
                                    offset = albums.size,
                                    hasMore = albumFeed.supportsPagination(),
                                )
                            },
                        )
                    }
                }
            }
            val playlists = loadCachedOrNull { libraryCacheRepository.getPlaylists(serverId) }
            if (!playlists.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(playlists = playlists) }
            }
            val songs = loadCachedOrNull { libraryCacheRepository.getSongs(serverId) }
            if (!songs.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(songs = songs) }
            }
        }
    }

    private fun refreshServerContent(serverId: Long, forceRefresh: Boolean = false) {
        loadServerContent(serverId, forceRefresh)
    }

    private fun loadServerContent(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        lastLoadedServerId = serverId
        loadArtists(serverId, forceRefresh)
        loadAlbums(serverId, uiState.value.selectedAlbumFeed, forceRefresh)
        loadPlaylists(serverId, forceRefresh)
        loadSongs(serverId, forceRefresh)
    }

    private fun loadArtists(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && uiState.value.libraryIndexes != null) return

        viewModelScope.launch {
            mutableUiState.update { it.copy(isArtistsLoading = true, artistsError = null) }

            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getArtists(serverId) }.getOrNull()
                if (cached != null && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(libraryIndexes = cached.regroupByLocale()) }
                }
            }

            runCatching {
                subsonicRepository.getIndexes(serverId).data
            }.onSuccess { indexes ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(libraryIndexes = indexes.regroupByLocale(), isArtistsLoading = false, artistsError = null) }
                }
                runCatching { libraryCacheRepository.saveArtists(serverId, indexes) }
                    .onFailure { Log.w("SakiApp", "Failed to cache artists", it) }
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(isArtistsLoading = false, artistsError = throwable.localizedOr(R.string.error_load_artists))
                }
            }
        }
    }

    private fun loadAlbums(
        serverId: Long,
        type: AlbumListType,
        forceRefresh: Boolean = false,
    ) {
        val currentFeed = uiState.value.albumFeedState(type)
        if (!forceRefresh && (currentFeed.isLoading || currentFeed.hasLoadedFromNetwork)) {
            return
        }

        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getAlbums(serverId, type) }.getOrNull()
                if (!cached.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(type) {
                                it.copy(
                                    albums = cached,
                                    offset = cached.size,
                                    hasMore = type.supportsPagination(),
                                    error = null,
                                )
                            },
                        )
                    }
                }
            }

            mutableUiState.update { state ->
                state.copy(
                    albumFeeds = state.albumFeeds.updateFeed(type) {
                        it.copy(
                            isLoading = true,
                            isLoadingMore = false,
                            hasMore = type.supportsPagination(),
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                subsonicRepository.getAlbumList(
                    serverId = serverId,
                    type = type,
                    size = ALBUMS_PAGE_SIZE,
                    offset = 0,
                ).data
            }.onSuccess { albums ->
                val uniqueAlbums = albums.distinctBy(AlbumSummary::id)
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(type) {
                                it.copy(
                                    albums = uniqueAlbums,
                                    offset = uniqueAlbums.size,
                                    hasMore = type.supportsPagination() && albums.size >= ALBUMS_PAGE_SIZE,
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = null,
                                    hasLoadedFromNetwork = true,
                                )
                            },
                        )
                    }
                }
                runCatching { libraryCacheRepository.saveAlbums(serverId, type, uniqueAlbums) }
                    .onFailure { Log.w("SakiApp", "Failed to cache albums", it) }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(type) {
                                it.copy(
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = throwable.localizedOr(R.string.error_load_albums),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun loadPlaylists(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && uiState.value.playlists.isNotEmpty()) return

        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getPlaylists(serverId) }.getOrNull()
                if (!cached.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(playlists = cached) }
                }
            }

            mutableUiState.update { it.copy(isPlaylistsLoading = true, playlistsError = null) }

            runCatching {
                subsonicRepository.getPlaylists(serverId).data
            }.onSuccess { playlists ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(playlists = playlists, isPlaylistsLoading = false, playlistsError = null) }
                }
                runCatching { libraryCacheRepository.savePlaylists(serverId, playlists) }
                    .onFailure { Log.w("SakiApp", "Failed to cache playlists", it) }
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(isPlaylistsLoading = false, playlistsError = throwable.localizedOr(R.string.error_load_playlists))
                }
            }
        }
    }

    private fun loadSongs(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && uiState.value.songs.isNotEmpty()) return

        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getSongs(serverId) }.getOrNull()
                if (!cached.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(songs = cached) }
                }
            }

            if (uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(isSongsLoading = true, songsError = null) }
            }

            runCatching {
                fetchAllSongs(serverId)
            }.onSuccess { songs ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(songs = songs, isSongsLoading = false, songsError = null) }
                }
                runCatching { libraryCacheRepository.saveSongs(serverId, songs) }
                    .onFailure { Log.w("SakiApp", "Failed to cache songs", it) }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(isSongsLoading = false, songsError = throwable.localizedOr(R.string.error_load_songs))
                    }
                }
            }
        }
    }

    // Navidrome supports empty query in search3 to return all songs.
    // This is not standard Subsonic behavior and may not work on other servers.
    private suspend fun fetchAllSongs(serverId: Long): List<Song> = withContext(Dispatchers.IO) {
        val pageSize = 500
        val maxPages = 100
        val allSongs = mutableListOf<Song>()
        var offset = 0
        var page = 0
        while (page < maxPages) {
            val results = subsonicRepository.search(
                serverId = serverId,
                query = "",
                artistCount = 0,
                albumCount = 0,
                songCount = pageSize,
                songOffset = offset,
            ).data
            allSongs.addAll(results.songs)
            if (results.songs.isEmpty() || results.songs.size < pageSize) break
            offset += pageSize
            page++
        }
        allSongs.sortBy { it.title.lowercase() }
        allSongs
    }

    private suspend fun buildArtistTopSongs(
        serverId: Long,
        artist: Artist,
    ): List<Song> = coroutineScope {
        artist.albums.take(4)
            .map { album ->
                async {
                    subsonicRepository.getAlbum(serverId, album.id).data
                }
            }
            .map { deferred -> deferred.await() }
            .flatMap { album ->
                album.songs.map { song ->
                    song.withFallbackAlbumMetadata(album)
                }
            }
            .distinctBy(Song::id)
            .take(8)
    }

    private suspend fun performSearch(query: String) {
        val serverId = uiState.value.selectedServerId
        if (!uiState.value.isSearchActive || serverId == null || query.isBlank()) {
            mutableUiState.update { state ->
                state.copy(
                    searchResults = SearchResults(),
                    isSearchLoading = false,
                    searchError = null,
                )
            }
            return
        }

        mutableUiState.update { state ->
            state.copy(
                isSearchLoading = true,
                searchError = null,
            )
        }

        runCatching {
            subsonicRepository.search(
                serverId = serverId,
                query = query,
                artistCount = 8,
                albumCount = 10,
                songCount = 20,
            ).data
        }.onSuccess { results ->
            if (
                uiState.value.selectedServerId == serverId &&
                uiState.value.isSearchActive &&
                uiState.value.searchQuery.trim() == query
            ) {
                mutableUiState.update { state ->
                    state.copy(
                        searchResults = results,
                        isSearchLoading = false,
                        searchError = null,
                    )
                }
                runCatching { appPreferencesRepository.addRecentSearchQuery(query) }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        Log.w("SakiApp", "Failed to save recent search query", throwable)
                    }
            }
        }.onFailure { throwable ->
            if (
                uiState.value.selectedServerId == serverId &&
                uiState.value.isSearchActive &&
                uiState.value.searchQuery.trim() == query
            ) {
                mutableUiState.update { state ->
                    state.copy(
                        searchResults = SearchResults(),
                        isSearchLoading = false,
                        searchError = throwable.localizedOr(R.string.error_search_server),
                    )
                }
            }
        }
    }

    private fun clearSearchState() {
        searchQueryFlow.value = ""
        mutableUiState.update { state ->
            state.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = SearchResults(),
                isSearchLoading = false,
                searchError = null,
            )
        }
    }

    fun refreshEndpointStatus() {
        val serverId = uiState.value.selectedServerId ?: return
        val results = endpointSelector.getLastProbeResults(serverId)
        val activeId = endpointSelector.getActiveEndpointId(serverId)
        mutableEndpointStatus.update { current ->
            current.copy(
                activeEndpointLabel = results.find { it.endpoint.id == activeId }?.endpoint?.label,
                activeEndpointId = activeId,
                isForced = endpointSelector.isForced(serverId),
                probeResults = results.map { r ->
                    EndpointProbeInfo(
                        id = r.endpoint.id,
                        label = r.endpoint.label,
                        baseUrl = r.endpoint.baseUrl,
                        latencyMs = r.latencyMs,
                        reachable = r.reachable,
                    )
                },
            )
        }
    }

    fun forceEndpoint(endpointId: Long) {
        val serverId = uiState.value.selectedServerId ?: return
        if (endpointSelector.getActiveEndpointId(serverId) == endpointId && endpointSelector.isForced(serverId)) {
            endpointSelector.clearForce(serverId)
        } else {
            endpointSelector.forceEndpoint(serverId, endpointId)
        }
    }

    fun reprobeEndpoints() {
        val serverId = uiState.value.selectedServerId ?: return
        val server = uiState.value.servers.find { it.id == serverId } ?: return
        mutableEndpointStatus.update { it.copy(isProbing = true) }
        viewModelScope.launch {
            try {
                endpointSelector.probe(serverId, server)
                refreshEndpointStatus()
            } finally {
                mutableEndpointStatus.update { it.copy(isProbing = false) }
            }
        }
    }

    fun exportConfig(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val json = configBackupManager.exportToJson()
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                    ?: error("Cannot open output stream")
            }
                .onSuccess { snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_backup_exported))) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    snackbarMessages.tryEmit(
                        SnackbarMessage(UiText.resource(R.string.message_export_failed, e.message.orEmpty())),
                    )
                }
        }
    }

    fun importConfig(uri: android.net.Uri, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val json = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                } ?: run {
                    snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_cannot_read_backup)))
                    return@launch
                }
                when (val result = configBackupManager.importFromJson(json)) {
                    is ImportResult.Success -> {
                        val settingsSuffix = if (result.settingsRestored) {
                            UiText.resource(R.string.message_import_settings_suffix)
                        } else {
                            ""
                        }
                        snackbarMessages.tryEmit(
                            SnackbarMessage(UiText.plural(
                                R.plurals.message_import_success,
                                result.serversImported,
                                result.serversImported,
                                settingsSuffix,
                            )),
                        )
                        onSuccess?.invoke()
                    }
                    is ImportResult.InvalidFormat -> snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_invalid_backup)))
                    is ImportResult.UnsupportedVersion -> snackbarMessages.tryEmit(
                        SnackbarMessage(UiText.resource(R.string.message_unsupported_backup_version, result.version)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_import_failed, e.message.orEmpty())))
            }
        }
    }
}

data class EndpointStatus(
    val activeEndpointLabel: String? = null,
    val activeEndpointId: Long? = null,
    val isForced: Boolean = false,
    val probeResults: List<EndpointProbeInfo> = emptyList(),
    val isProbing: Boolean = false,
) {
    val isOfflineDegraded: Boolean
        get() = !isProbing &&
            probeResults.isNotEmpty() &&
            probeResults.none { it.reachable }
}

data class EndpointProbeInfo(
    val id: Long,
    val label: String,
    val baseUrl: String,
    val latencyMs: Long?,
    val reachable: Boolean,
)

private data class LocalPlayQueueRestorePlan(
    val songs: List<Song>,
    val startIndex: Int,
    val positionMs: Long,
)

enum class BrowseSection {
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    SONGS,
}

private fun DefaultBrowseTab.toBrowseSection(): BrowseSection = when (this) {
    DefaultBrowseTab.ARTISTS -> BrowseSection.ARTISTS
    DefaultBrowseTab.ALBUMS -> BrowseSection.ALBUMS
    DefaultBrowseTab.PLAYLISTS -> BrowseSection.PLAYLISTS
    DefaultBrowseTab.SONGS -> BrowseSection.SONGS
}

data class AlbumFeedState(
    val albums: List<AlbumSummary> = emptyList(),
    val isLoading: Boolean = false,
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
    val hasLoadedFromNetwork: Boolean = false,
)

data class SakiAppUiState(
    val isAppReady: Boolean = false,
    val textScale: TextScale = TextScale.DEFAULT,
    val appPreferences: AppPreferences = AppPreferences(),
    val selectedBrowseSection: BrowseSection = BrowseSection.ARTISTS,
    val servers: List<ServerConfig> = emptyList(),
    val selectedServerId: Long? = null,
    val selectedAlbumFeed: AlbumListType = AlbumListType.NEWEST,
    val libraryIndexes: LibraryIndexes? = null,
    val isArtistsLoading: Boolean = false,
    val artistsError: UiText? = null,
    val selectedArtist: Artist? = null,
    val selectedArtistTopSongs: List<Song> = emptyList(),
    val isArtistLoading: Boolean = false,
    val artistError: UiText? = null,
    val albumFeeds: Map<AlbumListType, AlbumFeedState> = emptyAlbumFeedStates(),
    val selectedAlbum: Album? = null,
    val isAlbumLoading: Boolean = false,
    val albumError: UiText? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val playlistsError: UiText? = null,
    val selectedPlaylist: Playlist? = null,
    val isPlaylistLoading: Boolean = false,
    val playlistError: UiText? = null,
    val songs: List<Song> = emptyList(),
    val isSongsLoading: Boolean = false,
    val songsError: UiText? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchLoading: Boolean = false,
    val searchError: UiText? = null,
    val cachedSongs: List<CachedSong> = emptyList(),
    val cacheStorageSummary: CacheStorageSummary = CacheStorageSummary(),
    val streamCachedSongIds: Set<String> = emptySet(),
    val downloadingSongIds: Set<String> = emptySet(),
    val playbackState: PlaybackSessionState = PlaybackSessionState(),
    val currentLyrics: SongLyrics? = null,
) {
    fun albumFeedState(type: AlbumListType): AlbumFeedState {
        return albumFeeds[type] ?: AlbumFeedState(hasMore = type.supportsPagination())
    }

    val selectedAlbumFeedState: AlbumFeedState
        get() = albumFeedState(selectedAlbumFeed)
    val albums: List<AlbumSummary>
        get() = selectedAlbumFeedState.albums
    val isAlbumsLoading: Boolean
        get() = selectedAlbumFeedState.isLoading
    val hasMoreAlbums: Boolean
        get() = selectedAlbumFeedState.hasMore
    val isLoadingMoreAlbums: Boolean
        get() = selectedAlbumFeedState.isLoadingMore
    val albumsError: UiText?
        get() = selectedAlbumFeedState.error
}

private fun SakiAppUiState.findArtistSummary(artistId: String): ArtistSummary? {
    val indexes = libraryIndexes ?: return null
    return indexes.shortcuts.firstOrNull { it.id == artistId }
        ?: indexes.sections.asSequence()
            .flatMap { section -> section.artists.asSequence() }
            .firstOrNull { it.id == artistId }
}

private fun SakiAppUiState.findAlbumSummary(albumId: String): AlbumSummary? {
    return albumFeeds.values.asSequence()
        .flatMap { feed -> feed.albums.asSequence() }
        .firstOrNull { it.id == albumId }
        ?: selectedArtist?.albums?.firstOrNull { it.id == albumId }
}

private fun SakiAppUiState.findPlaylistSummary(playlistId: String): PlaylistSummary? {
    return playlists.firstOrNull { it.id == playlistId }
}

private fun ArtistSummary.toArtist() = Artist(
    id = id,
    name = name,
    coverArtId = coverArtId,
    artistImageUrl = artistImageUrl,
    albumCount = albumCount,
    albums = emptyList(),
)

private fun AlbumSummary.toAlbum() = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    coverArtId = coverArtId,
    songCount = songCount,
    durationSeconds = durationSeconds,
    year = year,
    genre = genre,
    created = created,
    songs = emptyList(),
)

private fun PlaylistSummary.toPlaylist() = Playlist(
    id = id,
    name = name,
    owner = owner,
    isPublic = isPublic,
    songCount = songCount,
    durationSeconds = durationSeconds,
    coverArtId = coverArtId,
    created = created,
    changed = changed,
    songs = emptyList(),
)

private fun emptyAlbumFeedStates(): Map<AlbumListType, AlbumFeedState> {
    return AlbumListType.entries.associateWith { AlbumFeedState(hasMore = it.supportsPagination()) }
}

private fun Map<AlbumListType, AlbumFeedState>.updateFeed(
    type: AlbumListType,
    transform: (AlbumFeedState) -> AlbumFeedState,
): Map<AlbumListType, AlbumFeedState> {
    val current = this[type] ?: AlbumFeedState(hasMore = type.supportsPagination())
    return this + (type to transform(current))
}

private fun Song.withFallbackAlbumMetadata(album: Album): Song {
    return copy(
        album = this.album ?: album.name,
        albumId = albumId ?: album.id,
        artist = artist ?: album.artist,
        artistId = artistId ?: album.artistId,
        coverArtId = coverArtId ?: album.coverArtId,
    )
}

private fun AlbumListType.supportsPagination(): Boolean {
    return this != AlbumListType.RANDOM && this != AlbumListType.STARRED
}
