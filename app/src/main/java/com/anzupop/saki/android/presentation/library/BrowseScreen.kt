package com.anzupop.saki.android.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anzupop.saki.android.R
import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.SearchResults
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.presentation.BrowseSection
import com.anzupop.saki.android.presentation.rememberBrowseBackgroundBrush
import com.anzupop.saki.android.presentation.SakiAppUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    onPlaySongs: (List<Song>, Int) -> Unit,
    onQueueSong: (Song) -> Unit,
    onPlaySongNext: (Song) -> Unit,
    onToggleSongDownload: (Song) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val background = rememberBrowseBackgroundBrush()
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
                        onPlaySongs = onPlaySongs,
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
                        onPlaySongs = onPlaySongs,
                        onShowSongActions = { actionSong = it },
                        onOpenSettings = onOpenSettings,
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
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowSongActions: (Song) -> Unit,
    onOpenSettings: () -> Unit,
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
            onOpenSettings = onOpenSettings,
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
                onPlaySongs = onPlaySongs,
                onShowSongActions = onShowSongActions,
            )
        } else {
            val isRefreshing = uiState.isArtistsLoading || uiState.isAlbumsLoading ||
                uiState.isPlaylistsLoading || uiState.isSongsLoading
            val pullState = rememberPullToRefreshState()
            val haptic = LocalHapticFeedback.current
            val isOverThreshold = !isRefreshing && pullState.distanceFraction >= 1f
            LaunchedEffect(isOverThreshold) {
                if (isOverThreshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefreshCurrentTab,
                modifier = Modifier.weight(1f),
                state = pullState,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sections) { section ->
                            FilterChip(
                                selected = uiState.selectedBrowseSection == section,
                                onClick = { onSelectBrowseSection(section) },
                                label = {
                                    Text(
                                        text = section.localizedLabel(),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                },
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
                                onPlaySongs = onPlaySongs,
                                onShowSongActions = onShowSongActions,
                            )
                        }
                    }
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
    onOpenSettings: () -> Unit,
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
                        text = stringResource(R.string.browse_search_placeholder, currentServer.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { onSearchActiveChange(false) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.browse_close_search))
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
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.browse_search_server))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.browse_settings))
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
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val trimmedQuery = query.trim()
    when {
        trimmedQuery.isBlank() -> Box(modifier = modifier) {
            EmptyStateCard(
                title = stringResource(R.string.browse_search_server),
                body = stringResource(R.string.browse_search_server_body, currentServer.name),
            )
        }

        isLoading -> Box(modifier = modifier) {
            LoadingStateCard(stringResource(R.string.browse_searching_server, currentServer.name))
        }

        error != null -> Box(modifier = modifier) {
            ErrorStateCard(error)
        }

        results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty() -> Box(modifier = modifier) {
            EmptyStateCard(
                title = stringResource(R.string.browse_no_results),
                body = stringResource(R.string.browse_no_results_body, trimmedQuery, currentServer.name),
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (results.artists.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.browse_artists),
                        subtitle = matchCountText(results.artists.size),
                    )
                }
                items(results.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onOpenArtist = onOpenArtist)
                }
            }

            if (results.albums.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.library_albums),
                        subtitle = matchCountText(results.albums.size),
                    )
                }
                items(results.albums, key = { it.id }) { album ->
                    AlbumRow(album = album, server = currentServer, onOpenAlbum = onOpenAlbum)
                }
            }

            if (results.songs.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.browse_songs),
                        subtitle = matchCountText(results.songs.size),
                    )
                }
                itemsIndexed(results.songs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        server = currentServer,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        onClick = { onPlaySongs(results.songs, index) },
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
        LoadingStateCard(stringResource(R.string.browse_loading_artists))
        return
    }
    if (error != null && indexes == null) {
        ErrorStateCard(error)
        return
    }
    if (indexes == null) {
        EmptyStateCard(
            stringResource(R.string.browse_no_artists),
            stringResource(R.string.browse_no_artists_body),
        )
        return
    }

    // Build section-to-item-index mapping for scroll bar
    val nonEmptySections = remember(indexes) { indexes.sections.filter { it.artists.isNotEmpty() } }
    // Scroll bar: # A-Z, plus … for any non-Latin sections
    val scrollBarMapping = remember(nonEmptySections) {
        val result = mutableListOf<Pair<String, Int>>()
        var hasOther = false
        var firstOtherIdx = -1
        nonEmptySections.forEachIndexed { idx, section ->
            val name = section.name
            when {
                name.length == 1 && name[0] in 'A'..'Z' -> result.add(name to idx)
                name == "#" -> result.add(0, "#" to idx) // # always first
                else -> {
                    if (!hasOther) { firstOtherIdx = idx; hasOther = true }
                }
            }
        }
        if (hasOther) result.add("…" to firstOtherIdx)
        result
    }
    val visibleScrollLabels = remember(scrollBarMapping) { scrollBarMapping.map { it.first } }
    val scrollLabelToSection = remember(scrollBarMapping) { scrollBarMapping.toMap() }
    val sectionItemIndices = remember(indexes, nonEmptySections) {
        val map = mutableMapOf<Int, Int>()
        var itemIndex = if (indexes.shortcuts.isNotEmpty()) 2 else 0 // shortcuts title + row
        nonEmptySections.forEachIndexed { sectionIdx, section ->
            map[sectionIdx] = itemIndex
            itemIndex += 1 + section.artists.size // section title + artists
        }
        map
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp, end = 24.dp),
        ) {
            if (indexes.shortcuts.isNotEmpty()) {
                item {
                    SectionTitle(
                        stringResource(R.string.browse_shortcuts),
                        stringResource(R.string.browse_shortcuts_subtitle),
                    )
                }
                item {
                    LazyRow {
                        items(indexes.shortcuts, key = { it.id }) { artist ->
                            ArtistShortcutCard(artist = artist, onOpenArtist = onOpenArtist)
                        }
                    }
                }
            }
            nonEmptySections.forEach { section ->
                item { SectionTitle(section.name, artistCountText(section.artists.size)) }
                items(section.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onOpenArtist = onOpenArtist)
                }
            }
        }

        var showScrollBar by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(500)
            showScrollBar = true
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showScrollBar && visibleScrollLabels.size > 1,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(vertical = 8.dp),
        ) {
            AlphabetScrollBar(
                labels = visibleScrollLabels,
                onScrollTo = { idx ->
                    val label = visibleScrollLabels.getOrNull(idx) ?: return@AlphabetScrollBar
                    val sectionIdx = scrollLabelToSection[label] ?: return@AlphabetScrollBar
                    val itemIdx = sectionItemIndices[sectionIdx] ?: return@AlphabetScrollBar
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch { listState.scrollToItem(itemIdx) }
                },
            )
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
            SectionTitle(
                stringResource(R.string.library_albums),
                stringResource(R.string.browse_albums_subtitle),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp)) {
                items(feeds) { feed ->
                    FilterChip(
                        selected = selectedFeed == feed,
                        onClick = { onSelectFeed(feed) },
                        label = { Text(feed.localizedLabel()) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        when {
            isLoading && albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingStateCard(stringResource(R.string.browse_loading_albums))
            }

            error != null && albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorStateCard(error)
            }

            albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStateCard(
                    stringResource(R.string.browse_no_albums),
                    stringResource(R.string.browse_no_albums_body),
                )
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
        LoadingStateCard(stringResource(R.string.browse_loading_playlists))
        return
    }
    if (error != null && playlists.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (playlists.isEmpty()) {
        EmptyStateCard(
            stringResource(R.string.browse_no_playlists),
            stringResource(R.string.browse_no_playlists_body),
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            SectionTitle(
                stringResource(R.string.browse_playlists),
                stringResource(R.string.browse_playlists_subtitle),
            )
        }
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
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    if (isLoading && songs.isEmpty()) {
        LoadingStateCard(stringResource(R.string.browse_loading_songs))
        return
    }
    if (error != null && songs.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (songs.isEmpty()) {
        EmptyStateCard(
            stringResource(R.string.browse_no_songs),
            stringResource(R.string.browse_no_songs_body),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            SectionTitle(
                stringResource(R.string.browse_songs),
                stringResource(R.string.browse_songs_subtitle),
            )
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            SongRow(
                song = song,
                server = server,
                cachedSong = cachedSongsBySongId[song.id],
                isStreamCached = song.id in streamCachedSongIds,
                isDownloading = song.id in downloadingSongIds,
                onClick = { onPlaySongs(songs, index) },
                onMore = { onShowSongActions(song) },
            )
        }
    }
}

@Composable
private fun BrowseSection.localizedLabel(): String = when (this) {
    BrowseSection.ARTISTS -> stringResource(R.string.browse_artists)
    BrowseSection.ALBUMS -> stringResource(R.string.library_albums)
    BrowseSection.PLAYLISTS -> stringResource(R.string.browse_playlists)
    BrowseSection.SONGS -> stringResource(R.string.browse_songs)
}

@Composable
private fun AlbumListType.localizedLabel(): String = when (this) {
    AlbumListType.NEWEST -> stringResource(R.string.album_feed_newest)
    AlbumListType.RECENT -> stringResource(R.string.album_feed_recent)
    AlbumListType.RANDOM -> stringResource(R.string.album_feed_random)
    AlbumListType.HIGHEST -> stringResource(R.string.album_feed_top_rated)
    AlbumListType.FREQUENT -> stringResource(R.string.album_feed_frequent)
    AlbumListType.ALPHABETICAL_BY_NAME -> stringResource(R.string.album_feed_a_z)
    AlbumListType.ALPHABETICAL_BY_ARTIST -> stringResource(R.string.album_feed_by_artist)
    AlbumListType.STARRED -> stringResource(R.string.album_feed_starred)
    AlbumListType.BY_YEAR -> stringResource(R.string.album_feed_by_year)
    AlbumListType.BY_GENRE -> stringResource(R.string.album_feed_by_genre)
}

@Composable
private fun artistCountText(count: Int): String =
    pluralStringResource(R.plurals.browse_artist_count, count, count)

@Composable
private fun matchCountText(count: Int): String =
    pluralStringResource(R.plurals.browse_match_count, count, count)

@Composable
private fun AlphabetScrollBar(
    labels: List<String>,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(labels) {
                detectTapGestures { offset ->
                    val idx = (offset.y / (size.height.toFloat() / labels.size))
                        .toInt()
                        .coerceIn(0, labels.lastIndex)
                    onScrollTo(idx)
                }
            }
            .pointerInput(labels) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val idx = (offset.y / (size.height.toFloat() / labels.size))
                            .toInt()
                            .coerceIn(0, labels.lastIndex)
                        activeIndex = idx
                        onScrollTo(idx)
                    },
                    onDragEnd = { activeIndex = -1 },
                    onDragCancel = { activeIndex = -1 },
                    onVerticalDrag = { change, _ ->
                        val idx = (change.position.y / (size.height.toFloat() / labels.size))
                            .toInt()
                            .coerceIn(0, labels.lastIndex)
                        if (idx != activeIndex) {
                            activeIndex = idx
                            onScrollTo(idx)
                        }
                    },
                )
            }
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fontSize = if (labels.size > 30) 8.sp else 10.sp
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                fontSize = fontSize,
                lineHeight = fontSize,
                color = if (index == activeIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
