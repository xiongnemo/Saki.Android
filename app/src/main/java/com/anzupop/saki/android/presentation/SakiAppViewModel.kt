package com.anzupop.saki.android.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anzupop.saki.android.domain.model.Album
import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.Artist
import com.anzupop.saki.android.domain.model.CacheStorageSummary
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.LibraryIndexes
import com.anzupop.saki.android.domain.model.PlaybackSessionState
import com.anzupop.saki.android.domain.model.Playlist
import com.anzupop.saki.android.domain.model.PlaylistSummary
import com.anzupop.saki.android.domain.model.SearchResults
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.domain.model.SongLyrics
import com.anzupop.saki.android.domain.model.SoundBalancingMode
import com.anzupop.saki.android.domain.model.StreamQuality
import com.anzupop.saki.android.domain.model.TextScale
import com.anzupop.saki.android.domain.repository.AppPreferencesRepository
import com.anzupop.saki.android.domain.repository.CachedSongRepository
import com.anzupop.saki.android.domain.repository.LibraryCacheRepository
import com.anzupop.saki.android.domain.repository.PlaybackManager
import com.anzupop.saki.android.playback.LyricsHolder
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import com.anzupop.saki.android.domain.repository.ServerConfigRepository
import com.anzupop.saki.android.domain.repository.SubsonicRepository
import com.anzupop.saki.android.domain.repository.StreamCacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(FlowPreview::class)
class SakiAppViewModel @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val subsonicRepository: SubsonicRepository,
    private val cachedSongRepository: CachedSongRepository,
    private val streamCacheRepository: StreamCacheRepository,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val libraryCacheRepository: LibraryCacheRepository,
    private val playbackManager: PlaybackManager,
    private val lyricsHolder: LyricsHolder,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SakiAppUiState())
    private val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val openNowPlayingRequestsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val searchQueryFlow = MutableStateFlow("")
    private var lastLoadedServerId: Long? = null

    val uiState = mutableUiState.asStateFlow()
    val messages = snackbarMessages.asSharedFlow()
    val openNowPlayingRequests = openNowPlayingRequestsFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collectLatest { preferences ->
                mutableUiState.update { state ->
                    state.copy(
                        isAppReady = true,
                        hasCompletedOnboarding = preferences.hasCompletedOnboarding,
                        textScale = preferences.textScale,
                    )
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
        when (section) {
            BrowseSection.ARTISTS -> if (uiState.value.libraryIndexes == null) loadArtists(serverId)
            BrowseSection.ALBUMS -> if (uiState.value.albums.isEmpty()) loadAlbums(serverId, uiState.value.selectedAlbumFeed)
            BrowseSection.PLAYLISTS -> if (uiState.value.playlists.isEmpty()) loadPlaylists(serverId)
            BrowseSection.SONGS -> if (uiState.value.songs.isEmpty()) loadSongs(serverId)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            appPreferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun replayOnboarding() {
        viewModelScope.launch {
            appPreferencesRepository.setOnboardingCompleted(false)
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

    fun updateTextScale(textScale: TextScale) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateTextScale(textScale)
            }.onSuccess {
                snackbarMessages.emit("Text size set to ${textScale.label}.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to update text size.")
            }
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
                selectedAlbum = null,
                selectedPlaylist = null,
            )
        }
        viewModelScope.launch {
            refreshCacheStorageSummary(serverId)
        }
        loadServerContent(serverId, forceRefresh = true)
    }

    fun selectAlbumFeed(type: AlbumListType) {
        val serverId = uiState.value.selectedServerId ?: return
        mutableUiState.update { state ->
            state.copy(selectedAlbumFeed = type)
        }
        loadAlbums(serverId, type, forceRefresh = true)
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
        mutableUiState.update { state ->
            state.copy(
                selectedArtist = null,
                selectedArtistTopSongs = emptyList(),
                isArtistLoading = true,
                artistError = null,
                selectedAlbum = null,
            )
        }

        viewModelScope.launch {
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
            }.onFailure { throwable ->
                mutableUiState.update { state ->
                    state.copy(
                        isArtistLoading = false,
                        artistError = throwable.message ?: "Unable to load artist details.",
                    )
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
        mutableUiState.update { state ->
            state.copy(
                isAlbumLoading = true,
                albumError = null,
            )
        }

        viewModelScope.launch {
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
            }.onFailure { throwable ->
                mutableUiState.update { state ->
                    state.copy(
                        isAlbumLoading = false,
                        albumError = throwable.message ?: "Unable to load album details.",
                    )
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
        mutableUiState.update { state ->
            state.copy(
                isPlaylistLoading = true,
                playlistError = null,
            )
        }

        viewModelScope.launch {
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
            }.onFailure { throwable ->
                mutableUiState.update { state ->
                    state.copy(
                        isPlaylistLoading = false,
                        playlistError = throwable.message ?: "Unable to load playlist.",
                    )
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
                snackbarMessages.emit(throwable.message ?: "Unable to start playback.")
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
                snackbarMessages.emit(throwable.message ?: "Unable to start playback.")
            }
        }
    }

    fun queueSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.addToQueue(serverId, listOf(song))
            }.onSuccess {
                snackbarMessages.emit("Added ${song.title} to the queue.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to queue the song.")
            }
        }
    }

    fun playSongNext(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.playNext(serverId, song)
            }.onSuccess {
                snackbarMessages.emit("${song.title} will play next.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to reorder the queue.")
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
                snackbarMessages.emit("Saved ${cachedSong.title} for offline playback at ${cachedSong.quality.label}.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to cache the song.")
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
                snackbarMessages.emit(throwable.message ?: "Unable to play the cached song.")
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
                snackbarMessages.emit(throwable.message ?: "Unable to start offline playback.")
            }
        }
    }

    fun deleteCachedSong(cacheId: String) {
        viewModelScope.launch {
            runCatching {
                cachedSongRepository.deleteCachedSong(cacheId)
            }.onSuccess {
                snackbarMessages.emit("Removed cached file.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to remove the cached file.")
            }
        }
    }

    fun clearCachedSongs() {
        val targetServerId = uiState.value.selectedServerId
        viewModelScope.launch {
            runCatching {
                val removedDownloads = cachedSongRepository.clearCachedSongs(targetServerId)
                val removedStreamCacheEntries = streamCacheRepository.clearStreamCache(targetServerId)
                removedDownloads to removedStreamCacheEntries
            }.onSuccess { (removedDownloads, removedStreamCacheEntries) ->
                snackbarMessages.emit(
                    if (removedDownloads > 0 || removedStreamCacheEntries > 0) {
                        buildString {
                            append("Cleared ")
                            append("$removedDownloads download")
                            if (removedDownloads != 1) append("s")
                            append(" and ")
                            append("$removedStreamCacheEntries stream cache entr")
                            append(if (removedStreamCacheEntries == 1) "y." else "ies.")
                        }
                    } else {
                        "No stored audio to clear."
                    },
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to clear stored audio.")
            }
        }
    }

    fun updateStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateStreamQuality(quality)
            }.onSuccess {
                snackbarMessages.emit("Streaming quality set to ${quality.label}.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to update stream quality.")
            }
        }
    }

    fun updateSoundBalancing(mode: SoundBalancingMode) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateSoundBalancing(mode)
            }.onSuccess {
                snackbarMessages.emit("Sound balancing set to ${mode.label}.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to update sound balancing.")
            }
        }
    }

    fun updateStreamCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateStreamCacheSizeMb(sizeMb)
            }.onSuccess {
                snackbarMessages.emit("Stream cache limit updated.")
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to update stream cache size.")
            }
        }
    }

    fun updateBluetoothLyrics(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateBluetoothLyrics(enabled)
            }.onFailure { throwable ->
                snackbarMessages.emit(throwable.message ?: "Unable to update Bluetooth lyrics.")
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
                selectedAlbum = if (serverChanged) null else state.selectedAlbum,
                selectedPlaylist = if (serverChanged) null else state.selectedPlaylist,
            )
        }

        viewModelScope.launch {
            refreshCacheStorageSummary(selectedServerId)
        }

        if (selectedServerId != null && (serverChanged || lastLoadedServerId != selectedServerId)) {
            loadServerContent(selectedServerId, forceRefresh = true)
            if (uiState.value.playbackState.currentItem == null) {
                restorePlayQueue(selectedServerId)
            }
        }
    }

    private suspend fun refreshCacheStorageSummary(serverId: Long?) {
        val downloadSummary = cachedSongRepository.getCacheStorageSummary(serverId)
        val fullStreamSummary = streamCacheRepository.getStreamCacheSummary(serverId)
        val playableStreamSummary = streamCacheRepository.getStreamCacheSummary(
            serverId = serverId,
            quality = uiState.value.playbackState.preferences.streamQuality,
        )
        mutableUiState.update { state ->
            if (state.selectedServerId == serverId) {
                state.copy(
                    cacheStorageSummary = downloadSummary.copy(
                        streamCachedSongCount = fullStreamSummary.cachedSongIds.size,
                        streamCacheBytes = fullStreamSummary.bytes,
                        hasStreamingCache = true,
                    ),
                    streamCachedSongIds = playableStreamSummary.cachedSongIds,
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
            }.onFailure { /* server unreachable, skip restore */ }
        }
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
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getArtists(serverId) }.getOrNull()
                if (cached != null && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(libraryIndexes = cached) }
                }
            }

            mutableUiState.update { it.copy(isArtistsLoading = true, artistsError = null) }

            runCatching {
                subsonicRepository.getIndexes(serverId).data
            }.onSuccess { indexes ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(libraryIndexes = indexes, isArtistsLoading = false, artistsError = null) }
                }
                runCatching { libraryCacheRepository.saveArtists(serverId, indexes) }
                    .onFailure { Log.w("SakiApp", "Failed to cache artists", it) }
            }.onFailure { throwable ->
                mutableUiState.update { it.copy(isArtistsLoading = false, artistsError = throwable.message ?: "Unable to load artists.") }
            }
        }
    }

    private fun loadAlbums(
        serverId: Long,
        type: AlbumListType,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && uiState.value.albums.isNotEmpty() && uiState.value.selectedAlbumFeed == type) {
            return
        }

        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getAlbums(serverId, type) }.getOrNull()
                if (!cached.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(albums = cached) }
                }
            }

            mutableUiState.update { it.copy(isAlbumsLoading = true, albumsError = null) }

            runCatching {
                subsonicRepository.getAlbumList(serverId = serverId, type = type, size = 36).data
            }.onSuccess { albums ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(albums = albums, isAlbumsLoading = false, albumsError = null) }
                }
                runCatching { libraryCacheRepository.saveAlbums(serverId, type, albums) }
                    .onFailure { Log.w("SakiApp", "Failed to cache albums", it) }
            }.onFailure { throwable ->
                mutableUiState.update { it.copy(isAlbumsLoading = false, albumsError = throwable.message ?: "Unable to load albums.") }
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
                mutableUiState.update { it.copy(isPlaylistsLoading = false, playlistsError = throwable.message ?: "Unable to load playlists.") }
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

            mutableUiState.update { it.copy(isSongsLoading = true, songsError = null) }

            runCatching {
                fetchAllSongs(serverId)
            }.onSuccess { songs ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(songs = songs, isSongsLoading = false, songsError = null) }
                }
                runCatching { libraryCacheRepository.saveSongs(serverId, songs) }
                    .onFailure { Log.w("SakiApp", "Failed to cache songs", it) }
            }.onFailure { throwable ->
                mutableUiState.update { it.copy(isSongsLoading = false, songsError = throwable.message ?: "Unable to load songs.") }
            }
        }
    }

    private suspend fun fetchAllSongs(serverId: Long): List<Song> {
        val pageSize = 500
        val allSongs = mutableListOf<Song>()
        var offset = 0
        while (true) {
            val results = subsonicRepository.search(
                serverId = serverId,
                query = "",
                artistCount = 0,
                albumCount = 0,
                songCount = pageSize,
                songOffset = offset,
            ).data
            allSongs.addAll(results.songs)
            if (results.songs.size < pageSize) break
            offset += pageSize
        }
        return allSongs
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
                        searchError = throwable.message ?: "Unable to search this server.",
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
}

enum class BrowseSection {
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    SONGS,
}

data class SakiAppUiState(
    val isAppReady: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val textScale: TextScale = TextScale.DEFAULT,
    val selectedBrowseSection: BrowseSection = BrowseSection.ARTISTS,
    val servers: List<ServerConfig> = emptyList(),
    val selectedServerId: Long? = null,
    val selectedAlbumFeed: AlbumListType = AlbumListType.NEWEST,
    val libraryIndexes: LibraryIndexes? = null,
    val isArtistsLoading: Boolean = false,
    val artistsError: String? = null,
    val selectedArtist: Artist? = null,
    val selectedArtistTopSongs: List<Song> = emptyList(),
    val isArtistLoading: Boolean = false,
    val artistError: String? = null,
    val albums: List<AlbumSummary> = emptyList(),
    val isAlbumsLoading: Boolean = false,
    val albumsError: String? = null,
    val selectedAlbum: Album? = null,
    val isAlbumLoading: Boolean = false,
    val albumError: String? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val playlistsError: String? = null,
    val selectedPlaylist: Playlist? = null,
    val isPlaylistLoading: Boolean = false,
    val playlistError: String? = null,
    val songs: List<Song> = emptyList(),
    val isSongsLoading: Boolean = false,
    val songsError: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchLoading: Boolean = false,
    val searchError: String? = null,
    val cachedSongs: List<CachedSong> = emptyList(),
    val cacheStorageSummary: CacheStorageSummary = CacheStorageSummary(),
    val streamCachedSongIds: Set<String> = emptySet(),
    val downloadingSongIds: Set<String> = emptySet(),
    val playbackState: PlaybackSessionState = PlaybackSessionState(),
    val currentLyrics: SongLyrics? = null,
)

private fun Song.withFallbackAlbumMetadata(album: Album): Song {
    return copy(
        album = this.album ?: album.name,
        albumId = albumId ?: album.id,
        artist = artist ?: album.artist,
        artistId = artistId ?: album.artistId,
        coverArtId = coverArtId ?: album.coverArtId,
    )
}
