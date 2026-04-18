package com.anzupop.saki.android.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.SearchResults
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.presentation.AppTab
import com.anzupop.saki.android.presentation.BrowseSection
import com.anzupop.saki.android.presentation.SakiAppUiState
import com.anzupop.saki.android.presentation.rememberAppTabBackgroundBrush
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    uiState: SakiAppUiState,
    contentPadding: PaddingValues,
    onManageServers: () -> Unit,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (AlbumListType) -> Unit,
    onOpenArtist: (String) -> Unit,
    onCloseArtist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onQueueSong: (Song) -> Unit,
    onPlaySongNext: (Song) -> Unit,
    onToggleSongDownload: (Song) -> Unit,
) {
    val background = rememberAppTabBackgroundBrush(AppTab.BROWSE)
    val currentServer = uiState.servers.firstOrNull { it.id == uiState.selectedServerId }
    val cachedSongsBySongId = remember(uiState.cachedSongs, uiState.selectedServerId) {
        uiState.cachedSongs
            .asSequence()
            .filter { cachedSong -> cachedSong.serverId == uiState.selectedServerId }
            .associateBy(CachedSong::songId)
    }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var detailSong by remember { mutableStateOf<Song?>(null) }

    BackHandler(
        enabled = uiState.selectedArtist != null ||
            uiState.selectedAlbum != null ||
            uiState.selectedPlaylist != null ||
            uiState.isSearchActive,
    ) {
        when {
            uiState.selectedAlbum != null -> onCloseAlbum()
            uiState.selectedArtist != null -> onCloseArtist()
            uiState.selectedPlaylist != null -> onClosePlaylist()
            uiState.isSearchActive -> onSetSearchActive(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        if (currentServer == null) {
            NoServerBrowseState(
                modifier = Modifier.weight(1f),
                onManageServers = onManageServers,
            )
        } else {
            AnimatedContent(
                targetState = BrowseDetailTarget(
                    hasArtist = uiState.selectedArtist != null,
                    hasAlbum = uiState.selectedAlbum != null,
                    hasPlaylist = uiState.selectedPlaylist != null,
                ),
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    ) togetherWith androidx.compose.animation.fadeOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                },
                label = "browseTarget",
            ) { target ->
                when {
                    target.hasAlbum && uiState.selectedAlbum != null -> AlbumDetailScreen(
                        server = currentServer,
                        album = uiState.selectedAlbum,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = uiState.streamCachedSongIds,
                        downloadingSongIds = uiState.downloadingSongIds,
                        isLoading = uiState.isAlbumLoading,
                        error = uiState.albumError,
                        onPlaySongs = onPlaySongs,
                        onPlaySong = onPlaySong,
                        onShowActions = { actionSong = it },
                    )

                    target.hasArtist && uiState.selectedArtist != null -> ArtistDetailScreen(
                        server = currentServer,
                        artist = uiState.selectedArtist,
                        topSongs = uiState.selectedArtistTopSongs,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = uiState.streamCachedSongIds,
                        downloadingSongIds = uiState.downloadingSongIds,
                        isLoading = uiState.isArtistLoading,
                        error = uiState.artistError,
                        onOpenAlbum = onOpenAlbum,
                        onPlaySong = onPlaySong,
                        onShowActions = { actionSong = it },
                    )

                    target.hasPlaylist && uiState.selectedPlaylist != null -> PlaylistDetailScreen(
                        server = currentServer,
                        playlist = uiState.selectedPlaylist,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = uiState.streamCachedSongIds,
                        downloadingSongIds = uiState.downloadingSongIds,
                        isLoading = uiState.isPlaylistLoading,
                        error = uiState.playlistError,
                        onPlaySongs = onPlaySongs,
                        onPlaySong = onPlaySong,
                        onShowActions = { actionSong = it },
                    )

                    else -> BrowsePager(
                        modifier = Modifier.fillMaxSize(),
                        uiState = uiState,
                        currentServer = currentServer,
                        cachedSongsBySongId = cachedSongsBySongId,
                        onSelectBrowseSection = onSelectBrowseSection,
                        onSetSearchActive = onSetSearchActive,
                        onUpdateSearchQuery = onUpdateSearchQuery,
                        onRefreshCurrentTab = onRefreshCurrentTab,
                        onSelectAlbumFeed = onSelectAlbumFeed,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenPlaylist = onOpenPlaylist,
                        onPlaySong = onPlaySong,
                        onShowSongActions = { actionSong = it },
                    )
                }
            }
        }
    }

    actionSong?.let { song ->
        SongActionsSheet(
            song = song,
            isDownloaded = cachedSongsBySongId.containsKey(song.id),
            isDownloading = song.id in uiState.downloadingSongIds,
            onDismiss = { actionSong = null },
            onPlayNext = {
                onPlaySongNext(song)
                actionSong = null
            },
            onToggleDownload = {
                onToggleSongDownload(song)
                actionSong = null
            },
            onDetails = {
                detailSong = song
                actionSong = null
            },
            onQueueSong = {
                onQueueSong(song)
                actionSong = null
            },
        )
    }

    detailSong?.let { song ->
        SongDetailsDialog(song = song, onDismiss = { detailSong = null })
    }
}

private data class BrowseDetailTarget(
    val hasArtist: Boolean,
    val hasAlbum: Boolean,
    val hasPlaylist: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowsePager(
    modifier: Modifier,
    uiState: SakiAppUiState,
    currentServer: ServerConfig,
    cachedSongsBySongId: Map<String, CachedSong>,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (AlbumListType) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val sections = BrowseSection.entries
    val selectedSectionState = rememberUpdatedState(uiState.selectedBrowseSection)
    val pagerState = rememberPagerState(
        initialPage = sections.indexOf(uiState.selectedBrowseSection).coerceAtLeast(0),
        pageCount = { sections.size },
    )
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )

    LaunchedEffect(uiState.selectedBrowseSection) {
        val targetPage = sections.indexOf(uiState.selectedBrowseSection).coerceAtLeast(0)
        if (pagerState.settledPage != targetPage && pagerState.targetPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .map { sections[it] }
            .filter { it != selectedSectionState.value }
            .collect { onSelectBrowseSection(it) }
    }

    Column(modifier = modifier) {
        BrowseHeroCard(
            currentServer = currentServer,
            isSearchActive = uiState.isSearchActive,
            searchQuery = uiState.searchQuery,
            onSearchActiveChange = onSetSearchActive,
            onSearchQueryChange = onUpdateSearchQuery,
            onRefresh = onRefreshCurrentTab,
        )
        if (uiState.isSearchActive) {
            SearchResultsPage(
                modifier = Modifier.weight(1f),
                currentServer = currentServer,
                query = uiState.searchQuery,
                results = uiState.searchResults,
                isLoading = uiState.isSearchLoading,
                error = uiState.searchError,
                cachedSongsBySongId = cachedSongsBySongId,
                streamCachedSongIds = uiState.streamCachedSongIds,
                downloadingSongIds = uiState.downloadingSongIds,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onPlaySong = onPlaySong,
                onShowSongActions = onShowSongActions,
            )
        } else {
            PrimaryTabRow(
                selectedTabIndex = sections.indexOf(uiState.selectedBrowseSection).coerceAtLeast(0),
                modifier = Modifier.padding(vertical = 12.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                divider = {},
            ) {
                sections.forEach { section ->
                    Tab(
                        selected = uiState.selectedBrowseSection == section,
                        onClick = { onSelectBrowseSection(section) },
                        text = {
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                flingBehavior = pagerFlingBehavior,
            ) { page ->
                when (sections[page]) {
                    BrowseSection.ARTISTS -> ArtistsPage(
                        indexes = uiState.libraryIndexes,
                        server = currentServer,
                        isLoading = uiState.isArtistsLoading,
                        error = uiState.artistsError,
                        onOpenArtist = onOpenArtist,
                    )

                    BrowseSection.ALBUMS -> AlbumsPage(
                        albums = uiState.albums,
                        server = currentServer,
                        selectedFeed = uiState.selectedAlbumFeed,
                        isLoading = uiState.isAlbumsLoading,
                        error = uiState.albumsError,
                        onSelectFeed = onSelectAlbumFeed,
                        onOpenAlbum = onOpenAlbum,
                    )

                    BrowseSection.PLAYLISTS -> PlaylistsPage(
                        playlists = uiState.playlists,
                        server = currentServer,
                        isLoading = uiState.isPlaylistsLoading,
                        error = uiState.playlistsError,
                        onOpenPlaylist = onOpenPlaylist,
                    )

                    BrowseSection.SONGS -> SongsPage(
                        songs = uiState.songs,
                        server = currentServer,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = uiState.streamCachedSongIds,
                        downloadingSongIds = uiState.downloadingSongIds,
                        isLoading = uiState.isSongsLoading,
                        error = uiState.songsError,
                        onPlaySong = onPlaySong,
                        onShowSongActions = onShowSongActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseHeroCard(
    currentServer: ServerConfig,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSearchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        text = "Search ${currentServer.name}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { onSearchActiveChange(false) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close search")
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
            )
        } else {
            Text(
                text = currentServer.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { onSearchActiveChange(true) }) {
                Icon(Icons.Rounded.Search, contentDescription = "Search this server")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun SearchResultsPage(
    modifier: Modifier,
    currentServer: ServerConfig,
    query: String,
    results: SearchResults,
    isLoading: Boolean,
    error: String?,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val trimmedQuery = query.trim()
    when {
        trimmedQuery.isBlank() -> Box(modifier = modifier) {
            EmptyStateCard(
                title = "Search this server",
                body = "Look up artists, albums, and songs on ${currentServer.name}.",
            )
        }

        isLoading -> Box(modifier = modifier) {
            LoadingStateCard("Searching ${currentServer.name}")
        }

        error != null -> Box(modifier = modifier) {
            ErrorStateCard(error)
        }

        results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty() -> Box(modifier = modifier) {
            EmptyStateCard(
                title = "No results",
                body = "Nothing matched \"$trimmedQuery\" on ${currentServer.name}.",
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (results.artists.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Artists",
                        subtitle = "${results.artists.size} match${if (results.artists.size == 1) "" else "es"}",
                    )
                }
                items(results.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, server = currentServer, onOpenArtist = onOpenArtist)
                }
            }

            if (results.albums.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Albums",
                        subtitle = "${results.albums.size} match${if (results.albums.size == 1) "" else "es"}",
                    )
                }
                items(results.albums, key = { it.id }) { album ->
                    AlbumRow(album = album, server = currentServer, onOpenAlbum = onOpenAlbum)
                }
            }

            if (results.songs.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Songs",
                        subtitle = "${results.songs.size} match${if (results.songs.size == 1) "" else "es"}",
                    )
                }
                items(results.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        server = currentServer,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        onClick = { onPlaySong(song) },
                        onMore = { onShowSongActions(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsPage(
    indexes: com.anzupop.saki.android.domain.model.LibraryIndexes?,
    server: ServerConfig,
    isLoading: Boolean,
    error: String?,
    onOpenArtist: (String) -> Unit,
) {
    if (isLoading && indexes == null) {
        LoadingStateCard("Loading artists")
        return
    }
    if (error != null && indexes == null) {
        ErrorStateCard(error)
        return
    }
    if (indexes == null) {
        EmptyStateCard("No artists", "The server did not return artist indexes.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (indexes.shortcuts.isNotEmpty()) {
            item { SectionTitle("Shortcuts", "Pinned artists") }
            item {
                LazyRow {
                    items(indexes.shortcuts, key = { it.id }) { artist ->
                        ArtistShortcutCard(artist = artist, onOpenArtist = onOpenArtist)
                    }
                }
            }
        }
        indexes.sections.forEach { section ->
            if (section.artists.isNotEmpty()) {
                item { SectionTitle(section.name, "${section.artists.size} ${if (section.artists.size == 1) "artist" else "artists"}") }
                items(section.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, server = server, onOpenArtist = onOpenArtist)
                }
            }
        }
    }
}

@Composable
private fun AlbumsPage(
    albums: List<com.anzupop.saki.android.domain.model.AlbumSummary>,
    server: ServerConfig,
    selectedFeed: AlbumListType,
    isLoading: Boolean,
    error: String?,
    onSelectFeed: (AlbumListType) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val feeds = listOf(
        AlbumListType.NEWEST,
        AlbumListType.RECENT,
        AlbumListType.RANDOM,
        AlbumListType.HIGHEST,
        AlbumListType.ALPHABETICAL_BY_NAME,
    )
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Albums", "Swipe to another tab or switch feed")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp)) {
                items(feeds) { feed ->
                    FilterChip(
                        selected = selectedFeed == feed,
                        onClick = { onSelectFeed(feed) },
                        label = { Text(feed.displayLabel) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        when {
            isLoading && albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingStateCard("Loading albums")
            }

            error != null && albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorStateCard(error)
            }

            albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStateCard("No albums", "Try another album feed.")
            }

            else -> items(albums, key = { it.id }) { album ->
                AlbumCard(album = album, server = server, onOpenAlbum = onOpenAlbum)
            }
        }
    }
}

@Composable
private fun PlaylistsPage(
    playlists: List<com.anzupop.saki.android.domain.model.PlaylistSummary>,
    server: ServerConfig,
    isLoading: Boolean,
    error: String?,
    onOpenPlaylist: (String) -> Unit,
) {
    if (isLoading && playlists.isEmpty()) {
        LoadingStateCard("Loading playlists")
        return
    }
    if (error != null && playlists.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (playlists.isEmpty()) {
        EmptyStateCard("No playlists", "Create a playlist on your server to see it here.", icon = Icons.AutoMirrored.Rounded.QueueMusic)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SectionTitle("Playlists", "Open a full playlist page") }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist = playlist, server = server, onOpenPlaylist = onOpenPlaylist)
        }
    }
}

@Composable
private fun SongsPage(
    songs: List<Song>,
    server: ServerConfig,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    onPlaySong: (Song) -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    if (isLoading && songs.isEmpty()) {
        LoadingStateCard("Loading songs")
        return
    }
    if (error != null && songs.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (songs.isEmpty()) {
        EmptyStateCard("No songs", "The current song feed is empty.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { SectionTitle("Songs", "Artwork, metadata, download state, and more actions") }
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                server = server,
                cachedSong = cachedSongsBySongId[song.id],
                isStreamCached = song.id in streamCachedSongIds,
                isDownloading = song.id in downloadingSongIds,
                onClick = { onPlaySong(song) },
                onMore = { onShowSongActions(song) },
            )
        }
    }
}

private val BrowseSection.label: String
    get() = when (this) {
        BrowseSection.ARTISTS -> "Artists"
        BrowseSection.ALBUMS -> "Albums"
        BrowseSection.PLAYLISTS -> "Playlists"
        BrowseSection.SONGS -> "Songs"
    }

private val BrowseSection.headline: String
    get() = when (this) {
        BrowseSection.ARTISTS -> "Browse the library"
        BrowseSection.ALBUMS -> "Flip through releases"
        BrowseSection.PLAYLISTS -> "Queue curated mixes"
        BrowseSection.SONGS -> "Scan every song"
    }

private val AlbumListType.displayLabel: String
    get() = when (this) {
        AlbumListType.NEWEST -> "Newest"
        AlbumListType.RECENT -> "Recent"
        AlbumListType.RANDOM -> "Random"
        AlbumListType.HIGHEST -> "Top rated"
        AlbumListType.FREQUENT -> "Frequent"
        AlbumListType.ALPHABETICAL_BY_NAME -> "A-Z"
        AlbumListType.ALPHABETICAL_BY_ARTIST -> "By artist"
        AlbumListType.STARRED -> "Starred"
        AlbumListType.BY_YEAR -> "By year"
        AlbumListType.BY_GENRE -> "By genre"
    }
