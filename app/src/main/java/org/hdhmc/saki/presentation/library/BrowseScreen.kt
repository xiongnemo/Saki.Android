package org.hdhmc.saki.presentation.library

import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.presentation.BrowseSection
import org.hdhmc.saki.presentation.AlbumFeedState
import org.hdhmc.saki.presentation.labelRes
import org.hdhmc.saki.presentation.bottomContentPadding
import org.hdhmc.saki.presentation.rememberBrowseBackgroundBrush
import org.hdhmc.saki.presentation.SakiAppUiState
import org.hdhmc.saki.presentation.asString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    uiState: SakiAppUiState,
    isOfflineDegraded: Boolean,
    contentPadding: PaddingValues,
    bottomOverlayPadding: Dp = 0.dp,
    onManageServers: () -> Unit,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (AlbumListType) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadPreviousSongs: () -> Unit,
    onLoadMoreSongs: () -> Unit,
    onUpdateAlbumViewMode: (AlbumViewMode) -> Unit,
    onOpenArtist: (String) -> Unit,
    onCloseArtist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onQueueSong: (Song) -> Unit,
    onPlaySongNext: (Song) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onToggleSongDownload: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
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
    val offlineAwarePlaySongs: (List<Song>, Int) -> Unit = { songs, startIndex ->
        playOfflineAwareSongs(
            songs = songs,
            startIndex = startIndex,
            isOfflineDegraded = isOfflineDegraded,
            cachedSongsBySongId = cachedSongsBySongId,
            streamCachedSongIds = uiState.streamCachedSongIds,
            onPlaySongs = onPlaySongs,
            onUnavailable = onOfflineSongUnavailable,
        )
    }

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
            .background(background)
            .padding(contentPadding)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        if (currentServer == null) {
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) onImportConfig(uri) }
            NoServerBrowseState(
                modifier = Modifier.weight(1f),
                onManageServers = onManageServers,
                onImportBackup = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        } else {
            if (isOfflineDegraded) {
                OfflineModeBanner(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
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
                        error = uiState.albumError?.asString(),
                        bottomOverlayPadding = bottomOverlayPadding,
                        isOfflineDegraded = isOfflineDegraded,
                        onOfflineSongUnavailable = onOfflineSongUnavailable,
                        onPlaySongs = offlineAwarePlaySongs,
                        onShowActions = { actionSong = it },
                    )

                    target.hasArtist && uiState.selectedArtist != null -> ArtistDetailScreen(
                        server = currentServer,
                        artist = uiState.selectedArtist,
                        songs = uiState.selectedArtistSongs,
                        songsAreTopSongs = uiState.selectedArtistSongsAreTopSongs,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = uiState.streamCachedSongIds,
                        downloadingSongIds = uiState.downloadingSongIds,
                        isLoading = uiState.isArtistLoading,
                        error = uiState.artistError?.asString(),
                        bottomOverlayPadding = bottomOverlayPadding,
                        isOfflineDegraded = isOfflineDegraded,
                        onOpenAlbum = onOpenAlbum,
                        onPlaySongs = offlineAwarePlaySongs,
                        onShowActions = { actionSong = it },
                    )

                    target.hasPlaylist && uiState.selectedPlaylist != null -> PlaylistDetailScreen(
                        server = currentServer,
                        playlist = uiState.selectedPlaylist,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = uiState.streamCachedSongIds,
                        downloadingSongIds = uiState.downloadingSongIds,
                        isLoading = uiState.isPlaylistLoading,
                        error = uiState.playlistError?.asString(),
                        bottomOverlayPadding = bottomOverlayPadding,
                        isOfflineDegraded = isOfflineDegraded,
                        onOfflineSongUnavailable = onOfflineSongUnavailable,
                        onPlaySongs = offlineAwarePlaySongs,
                        onShowActions = { actionSong = it },
                    )

                    else -> BrowsePager(
                        modifier = Modifier.fillMaxSize(),
                        uiState = uiState,
                        currentServer = currentServer,
                        cachedSongsBySongId = cachedSongsBySongId,
                        isOfflineDegraded = isOfflineDegraded,
                        bottomOverlayPadding = bottomOverlayPadding,
                        onSelectBrowseSection = onSelectBrowseSection,
                        onSetSearchActive = onSetSearchActive,
                        onUpdateSearchQuery = onUpdateSearchQuery,
                        onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
                        onClearRecentSearchQueries = onClearRecentSearchQueries,
                        onRefreshCurrentTab = onRefreshCurrentTab,
                        onSelectAlbumFeed = onSelectAlbumFeed,
                        onLoadMoreAlbums = onLoadMoreAlbums,
                        onLoadPreviousSongs = onLoadPreviousSongs,
                        onLoadMoreSongs = onLoadMoreSongs,
                        onUpdateAlbumViewMode = onUpdateAlbumViewMode,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenPlaylist = onOpenPlaylist,
                        onPlaySongs = onPlaySongs,
                        onPlayLibrarySongs = onPlayLibrarySongs,
                        onOfflineSongUnavailable = onOfflineSongUnavailable,
                        onShowSongActions = { actionSong = it },
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        }
    }

    actionSong?.let { song ->
        val isDownloaded = cachedSongsBySongId.containsKey(song.id)
        val isOfflinePlayable = song.isOfflinePlayable(
            cachedSongsBySongId = cachedSongsBySongId,
            streamCachedSongIds = uiState.streamCachedSongIds,
        )
        SongActionsSheet(
            song = song,
            isDownloaded = isDownloaded,
            isDownloading = song.id in uiState.downloadingSongIds,
            onDismiss = { actionSong = null },
            onPlayNext = {
                if (isOfflineDegraded && !isOfflinePlayable) {
                    onOfflineSongUnavailable()
                } else {
                    onPlaySongNext(song)
                }
                actionSong = null
            },
            onToggleDownload = {
                if (isOfflineDegraded && !isDownloaded) {
                    onOfflineSongUnavailable()
                } else {
                    onToggleSongDownload(song)
                }
                actionSong = null
            },
            onDetails = {
                detailSong = song
                actionSong = null
            },
            onQueueSong = {
                if (isOfflineDegraded && !isOfflinePlayable) {
                    onOfflineSongUnavailable()
                } else {
                    onQueueSong(song)
                }
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

@Composable
private fun OfflineModeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.browse_offline_degraded_banner),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowsePager(
    modifier: Modifier,
    uiState: SakiAppUiState,
    currentServer: ServerConfig,
    cachedSongsBySongId: Map<String, CachedSong>,
    isOfflineDegraded: Boolean,
    bottomOverlayPadding: Dp,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (AlbumListType) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadPreviousSongs: () -> Unit,
    onLoadMoreSongs: () -> Unit,
    onUpdateAlbumViewMode: (AlbumViewMode) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
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
                error = uiState.searchError?.asString(),
                recentSearchQueries = uiState.appPreferences.recentSearchQueries,
                cachedSongsBySongId = cachedSongsBySongId,
                streamCachedSongIds = uiState.streamCachedSongIds,
                downloadingSongIds = uiState.downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                bottomOverlayPadding = bottomOverlayPadding,
                onSearchQuery = onUpdateSearchQuery,
                onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
                onClearRecentSearchQueries = onClearRecentSearchQueries,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onPlaySongs = onPlaySongs,
                onOfflineSongUnavailable = onOfflineSongUnavailable,
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
                                error = uiState.artistsError?.asString(),
                                bottomOverlayPadding = bottomOverlayPadding,
                                onOpenArtist = onOpenArtist,
                            )

                            BrowseSection.ALBUMS -> AlbumsPage(
                                albumFeeds = uiState.albumFeeds,
                                browsePagerState = pagerState,
                                server = currentServer,
                                selectedFeed = uiState.selectedAlbumFeed,
                                viewMode = uiState.appPreferences.albumViewMode,
                                onSelectFeed = onSelectAlbumFeed,
                                onLoadMore = onLoadMoreAlbums,
                                onUpdateViewMode = onUpdateAlbumViewMode,
                                onOpenAlbum = onOpenAlbum,
                                bottomOverlayPadding = bottomOverlayPadding,
                            )

                            BrowseSection.PLAYLISTS -> PlaylistsPage(
                                playlists = uiState.playlists,
                                server = currentServer,
                                isLoading = uiState.isPlaylistsLoading,
                                error = uiState.playlistsError?.asString(),
                                bottomOverlayPadding = bottomOverlayPadding,
                                onOpenPlaylist = onOpenPlaylist,
                            )

                            BrowseSection.SONGS -> SongsPage(
                                songs = uiState.songs,
                                songsOffset = uiState.songsOffset,
                                hasPrevious = uiState.hasPreviousSongs,
                                hasMore = uiState.hasMoreSongs,
                                server = currentServer,
                                cachedSongsBySongId = cachedSongsBySongId,
                                streamCachedSongIds = uiState.streamCachedSongIds,
                                downloadingSongIds = uiState.downloadingSongIds,
                                isOfflineDegraded = isOfflineDegraded,
                                isLoading = uiState.isSongsLoading,
                                isLoadingPrevious = uiState.isSongsLoadingPrevious,
                                isLoadingMore = uiState.isSongsLoadingMore,
                                error = uiState.songsError?.asString(),
                                bottomOverlayPadding = bottomOverlayPadding,
                                onLoadPrevious = onLoadPreviousSongs,
                                onLoadMore = onLoadMoreSongs,
                                onPlaySongs = onPlaySongs,
                                onPlayLibrarySongs = onPlayLibrarySongs,
                                onOfflineSongUnavailable = onOfflineSongUnavailable,
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
    recentSearchQueries: List<String>,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    bottomOverlayPadding: Dp,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val trimmedQuery = query.trim()
    when {
        trimmedQuery.isBlank() -> RecentSearchesPage(
            modifier = modifier,
            currentServer = currentServer,
            recentSearchQueries = recentSearchQueries,
            bottomOverlayPadding = bottomOverlayPadding,
            onSearchQuery = onSearchQuery,
            onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
            onClearRecentSearchQueries = onClearRecentSearchQueries,
        )

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

        else -> {
            var artistsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
            var albumsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
            var songsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
            val visibleArtists = if (artistsExpanded) {
                results.artists
            } else {
                results.artists.take(SearchResultPreviewCount)
            }
            val visibleAlbums = if (albumsExpanded) {
                results.albums
            } else {
                results.albums.take(SearchResultPreviewCount)
            }
            val visibleSongs = if (songsExpanded) {
                results.songs
            } else {
                results.songs.take(SearchResultPreviewCount)
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
                if (results.artists.isNotEmpty()) {
                    item(key = "artists-header") {
                        SearchResultSectionHeader(
                            title = stringResource(R.string.browse_artists),
                            subtitle = matchCountText(results.artists.size),
                            expanded = artistsExpanded,
                            canToggle = results.artists.size > SearchResultPreviewCount,
                            onToggle = { artistsExpanded = !artistsExpanded },
                        )
                    }
                    items(visibleArtists, key = { "artist-${it.id}" }) { artist ->
                        ArtistRow(artist = artist, onOpenArtist = onOpenArtist)
                    }
                }

                if (results.albums.isNotEmpty()) {
                    item(key = "albums-header") {
                        SearchResultSectionHeader(
                            title = stringResource(R.string.library_albums),
                            subtitle = matchCountText(results.albums.size),
                            expanded = albumsExpanded,
                            canToggle = results.albums.size > SearchResultPreviewCount,
                            onToggle = { albumsExpanded = !albumsExpanded },
                        )
                    }
                    items(visibleAlbums, key = { "album-${it.id}" }) { album ->
                        AlbumRow(album = album, server = currentServer, onOpenAlbum = onOpenAlbum)
                    }
                }

                if (results.songs.isNotEmpty()) {
                    item(key = "songs-header") {
                        SearchResultSectionHeader(
                            title = stringResource(R.string.browse_songs),
                            subtitle = matchCountText(results.songs.size),
                            expanded = songsExpanded,
                            canToggle = results.songs.size > SearchResultPreviewCount,
                            onToggle = { songsExpanded = !songsExpanded },
                        )
                    }
                    itemsIndexed(visibleSongs, key = { _, s -> "song-${s.id}" }) { index, song ->
                        val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
                        SongRow(
                            song = song,
                            server = currentServer,
                            cachedSong = cachedSongsBySongId[song.id],
                            isStreamCached = song.id in streamCachedSongIds,
                            isDownloading = song.id in downloadingSongIds,
                            isOfflineDegraded = isOfflineDegraded,
                            isOfflinePlayable = isOfflinePlayable,
                            onClick = {
                                playOfflineAwareSongs(
                                    songs = results.songs,
                                    startIndex = index,
                                    isOfflineDegraded = isOfflineDegraded,
                                    cachedSongsBySongId = cachedSongsBySongId,
                                    streamCachedSongIds = streamCachedSongIds,
                                    onPlaySongs = onPlaySongs,
                                    onUnavailable = onOfflineSongUnavailable,
                                )
                            },
                            onMore = { onShowSongActions(song) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSearchesPage(
    modifier: Modifier,
    currentServer: ServerConfig,
    recentSearchQueries: List<String>,
    bottomOverlayPadding: Dp,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
) {
    if (recentSearchQueries.isEmpty()) {
        Box(modifier = modifier) {
            EmptyStateCard(
                title = stringResource(R.string.browse_search_server),
                body = stringResource(R.string.browse_search_server_body, currentServer.name),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        item(key = "recent-searches-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.browse_recent_searches), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClearRecentSearchQueries, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.browse_clear_search_history))
                }
            }
        }

        items(recentSearchQueries, key = { it }) { query ->
            RecentSearchRow(
                query = query,
                onSearchQuery = onSearchQuery,
                onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSearchRow(
    query: String,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
) {
    val searchLabel = stringResource(R.string.browse_run_recent_search, query)
    val removeLabel = stringResource(R.string.browse_remove_recent_search, query)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { onSearchQuery(query) },
                onClickLabel = searchLabel,
                onLongClick = { onRemoveRecentSearchQuery(query) },
                onLongClickLabel = removeLabel,
                role = Role.Button,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = query,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { onRemoveRecentSearchQuery(query) }) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = removeLabel,
            )
        }
    }
}

@Composable
private fun SearchResultSectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
) {
    val actionLabel = stringResource(
        if (expanded) R.string.browse_collapse_results else R.string.browse_show_all_results,
    )
    val headerModifier = if (canToggle) {
        Modifier.clickable(onClick = onToggle)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(headerModifier)
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (canToggle) {
                TextButton(onClick = onToggle, shape = MaterialTheme.shapes.small) {
                    Text(actionLabel)
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
            }
        }
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val SearchResultPreviewCount = 5

@Composable
private fun ArtistsPage(
    indexes: org.hdhmc.saki.domain.model.LibraryIndexes?,
    server: ServerConfig,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
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
            contentPadding = PaddingValues(bottom = 24.dp + bottomOverlayPadding, end = 24.dp),
        ) {
            if (indexes.shortcuts.isNotEmpty()) {
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
                .padding(top = 8.dp, bottom = 8.dp + bottomOverlayPadding),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsPage(
    albumFeeds: Map<AlbumListType, AlbumFeedState>,
    browsePagerState: PagerState,
    server: ServerConfig,
    selectedFeed: AlbumListType,
    viewMode: AlbumViewMode,
    onSelectFeed: (AlbumListType) -> Unit,
    onLoadMore: () -> Unit,
    onUpdateViewMode: (AlbumViewMode) -> Unit,
    onOpenAlbum: (String) -> Unit,
    bottomOverlayPadding: Dp,
) {
    val feeds = AlbumListType.defaultBrowseFeeds
    val selectedFeedState = rememberUpdatedState(selectedFeed)
    val feedPagerState = rememberPagerState(
        initialPage = feeds.indexOf(selectedFeed).coerceAtLeast(0),
        pageCount = { feeds.size },
    )
    val feedPagerFlingBehavior = PagerDefaults.flingBehavior(
        state = feedPagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    val context = LocalContext.current
    val boundaryFlingVelocityThreshold = remember(context) {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }
    val feedBoundaryHandoffConnection = rememberAlbumFeedBoundaryHandoffConnection(
        feedPagerState = feedPagerState,
        browsePagerState = browsePagerState,
        boundaryFlingVelocityThreshold = boundaryFlingVelocityThreshold,
    )
    val coroutineScope = rememberCoroutineScope()
    val highlightedFeed = feeds[feedPagerState.targetPage.coerceIn(0, feeds.lastIndex)]

    LaunchedEffect(selectedFeed) {
        val targetPage = feeds.indexOf(selectedFeed).coerceAtLeast(0)
        if (feedPagerState.settledPage != targetPage && feedPagerState.targetPage != targetPage) {
            feedPagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(feedPagerState) {
        snapshotFlow { feedPagerState.settledPage }
            .distinctUntilChanged()
            .map { feeds[it] }
            .filter { it != selectedFeedState.value }
            .collect { onSelectFeed(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AlbumFeedControls(
            feeds = feeds,
            selectedFeed = highlightedFeed,
            viewMode = viewMode,
            onSelectFeed = { feed ->
                val targetPage = feeds.indexOf(feed)
                if (targetPage >= 0 && feedPagerState.targetPage != targetPage) {
                    coroutineScope.launch { feedPagerState.animateScrollToPage(targetPage) }
                }
            },
            onUpdateViewMode = onUpdateViewMode,
        )

        HorizontalPager(
            state = feedPagerState,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(feedBoundaryHandoffConnection),
            flingBehavior = feedPagerFlingBehavior,
        ) { page ->
            val feed = feeds[page]
            val feedState = albumFeeds[feed] ?: AlbumFeedState()
            AlbumFeedPageContent(
                albums = feedState.albums,
                server = server,
                viewMode = viewMode,
                isLoading = feedState.isLoading,
                hasMore = feedState.hasMore,
                isLoadingMore = feedState.isLoadingMore,
                error = feedState.error?.asString(),
                canLoadMore = feed == selectedFeed,
                onLoadMore = onLoadMore,
                onOpenAlbum = onOpenAlbum,
                bottomOverlayPadding = bottomOverlayPadding,
            )
        }
    }
}

@Composable
private fun rememberAlbumFeedBoundaryHandoffConnection(
    feedPagerState: PagerState,
    browsePagerState: PagerState,
    boundaryFlingVelocityThreshold: Float,
): NestedScrollConnection {
    return remember(feedPagerState, browsePagerState, boundaryFlingVelocityThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) {
                    return Offset.Zero
                }

                val scrollDelta = -available.x
                if (!feedPagerState.shouldHandOffAlbumFeedDelta(scrollDelta)) {
                    return Offset.Zero
                }

                val consumed = browsePagerState.dispatchRawDelta(scrollDelta)
                return Offset(x = -consumed, y = 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val scrollVelocity = -available.x
                val browsePagerNeedsSettling = abs(browsePagerState.currentPageOffsetFraction) >
                    PagerOffsetSettlingEpsilon
                if (scrollVelocity != 0f && !feedPagerState.shouldHandOffAlbumFeedDelta(scrollVelocity)) {
                    return Velocity.Zero
                }
                if (scrollVelocity == 0f && !browsePagerNeedsSettling) {
                    return Velocity.Zero
                }

                val direction = if (scrollVelocity > 0f) 1 else -1
                val targetPage = if (abs(scrollVelocity) > boundaryFlingVelocityThreshold) {
                    browsePagerState.settledPage + direction
                } else {
                    browsePagerState.currentPage
                }.coerceIn(0, browsePagerState.pageCount - 1)

                val isAlreadySettled = targetPage == browsePagerState.currentPage &&
                    abs(browsePagerState.currentPageOffsetFraction) <= PagerOffsetSettlingEpsilon
                if (!isAlreadySettled) {
                    browsePagerState.animateScrollToPage(targetPage)
                }
                return Velocity(x = available.x, y = 0f)
            }
        }
    }
}

private fun PagerState.shouldHandOffAlbumFeedDelta(scrollDelta: Float): Boolean {
    return when {
        scrollDelta > 0f -> !canScrollForward
        scrollDelta < 0f -> !canScrollBackward
        else -> false
    }
}

private const val PagerOffsetSettlingEpsilon = 0.001f

@Composable
private fun AlbumFeedPageContent(
    albums: List<AlbumSummary>,
    server: ServerConfig,
    viewMode: AlbumViewMode,
    isLoading: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    bottomOverlayPadding: Dp,
) {
    when (viewMode) {
        AlbumViewMode.GRID -> {
            val gridState = rememberLazyGridState()
            LaunchedEffect(gridState, canLoadMore, hasMore, isLoading, isLoadingMore, albums.size) {
                snapshotFlow {
                    if (!canLoadMore || !hasMore || isLoading || isLoadingMore || albums.isEmpty()) {
                        false
                    } else {
                        val layoutInfo = gridState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisibleIndex >= layoutInfo.totalItemsCount - 5
                    }
                }
                    .distinctUntilChanged()
                    .filter { shouldLoad -> shouldLoad }
                    .collect { onLoadMore() }
            }

            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(2),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
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
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadingStateCard(stringResource(R.string.browse_loading_albums))
                    }
                }
            }
        }

        AlbumViewMode.LIST -> {
            val listState = rememberLazyListState()
            LaunchedEffect(listState, canLoadMore, hasMore, isLoading, isLoadingMore, albums.size) {
                snapshotFlow {
                    if (!canLoadMore || !hasMore || isLoading || isLoadingMore || albums.isEmpty()) {
                        false
                    } else {
                        val layoutInfo = listState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisibleIndex >= layoutInfo.totalItemsCount - 5
                    }
                }
                    .distinctUntilChanged()
                    .filter { shouldLoad -> shouldLoad }
                    .collect { onLoadMore() }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
                when {
                    isLoading && albums.isEmpty() -> item {
                        LoadingStateCard(stringResource(R.string.browse_loading_albums))
                    }

                    error != null && albums.isEmpty() -> item {
                        ErrorStateCard(error)
                    }

                    albums.isEmpty() -> item {
                        EmptyStateCard(
                            stringResource(R.string.browse_no_albums),
                            stringResource(R.string.browse_no_albums_body),
                        )
                    }

                    else -> items(albums, key = { it.id }) { album ->
                        AlbumRow(album = album, server = server, onOpenAlbum = onOpenAlbum)
                    }
                }
                if (isLoadingMore) {
                    item {
                        LoadingStateCard(stringResource(R.string.browse_loading_albums))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumFeedControls(
    feeds: List<AlbumListType>,
    selectedFeed: AlbumListType,
    viewMode: AlbumViewMode,
    onSelectFeed: (AlbumListType) -> Unit,
    onUpdateViewMode: (AlbumViewMode) -> Unit,
) {
    val nextMode = when (viewMode) {
        AlbumViewMode.GRID -> AlbumViewMode.LIST
        AlbumViewMode.LIST -> AlbumViewMode.GRID
    }
    val contentDescription = when (nextMode) {
        AlbumViewMode.GRID -> stringResource(R.string.browse_show_album_grid)
        AlbumViewMode.LIST -> stringResource(R.string.browse_show_album_list)
    }

    val selectedIndex = feeds.indexOf(selectedFeed).coerceAtLeast(0)
    val lazyRowState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        lazyRowState.animateScrollToItem(selectedIndex)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = lazyRowState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            items(feeds) { feed ->
                FilterChip(
                    selected = selectedFeed == feed,
                    onClick = { onSelectFeed(feed) },
                    label = { Text(stringResource(feed.labelRes())) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = { onUpdateViewMode(nextMode) }) {
            Icon(
                imageVector = when (nextMode) {
                    AlbumViewMode.GRID -> Icons.Rounded.GridView
                    AlbumViewMode.LIST -> Icons.Rounded.ViewList
                },
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun PlaylistsPage(
    playlists: List<org.hdhmc.saki.domain.model.PlaylistSummary>,
    server: ServerConfig,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
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
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist = playlist, server = server, onOpenPlaylist = onOpenPlaylist)
        }
    }
}

@Composable
private fun SongsPage(
    songs: List<Song>,
    songsOffset: Int,
    hasPrevious: Boolean,
    hasMore: Boolean,
    server: ServerConfig,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    isLoading: Boolean,
    isLoadingPrevious: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    onLoadPrevious: () -> Unit,
    onLoadMore: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val listState = rememberLazyListState()
    var wasLoadingPrevious by remember { mutableStateOf(isLoadingPrevious) }
    var previousLoadAnchorIndex by remember { mutableStateOf<Int?>(null) }
    var previousLoadAnchorScrollOffset by remember { mutableStateOf(0) }
    LaunchedEffect(songsOffset, isLoadingPrevious, songs.size) {
        if (!wasLoadingPrevious && isLoadingPrevious) {
            previousLoadAnchorIndex = songsOffset + listState.firstVisibleItemIndex
            previousLoadAnchorScrollOffset = listState.firstVisibleItemScrollOffset
        }

        val finishedPreviousLoad = wasLoadingPrevious && !isLoadingPrevious
        val anchorIndex = previousLoadAnchorIndex
        if (finishedPreviousLoad && anchorIndex != null && songs.isNotEmpty()) {
            val targetIndex = anchorIndex - songsOffset
            if (
                targetIndex in songs.indices &&
                (
                    listState.firstVisibleItemIndex != targetIndex ||
                        listState.firstVisibleItemScrollOffset != previousLoadAnchorScrollOffset
                    )
            ) {
                listState.scrollToItem(
                    index = targetIndex,
                    scrollOffset = previousLoadAnchorScrollOffset,
                )
            }
            previousLoadAnchorIndex = null
        }
        wasLoadingPrevious = isLoadingPrevious
    }
    LaunchedEffect(listState, hasPrevious, isLoading, isLoadingPrevious, songsOffset, songs.size) {
        snapshotFlow {
            if (!hasPrevious || isLoading || isLoadingPrevious || songs.isEmpty()) {
                false
            } else {
                val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                firstVisibleIndex <= 8
            }
        }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .collect { onLoadPrevious() }
    }
    LaunchedEffect(listState, hasMore, isLoading, isLoadingMore, songsOffset, songs.size) {
        snapshotFlow {
            if (!hasMore || isLoading || isLoadingMore || songs.isEmpty()) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex >= layoutInfo.totalItemsCount - 8
            }
        }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .collect { onLoadMore() }
    }

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
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        itemsIndexed(songs, key = { index, s -> "${songsOffset + index}-${s.id}" }) { index, song ->
            val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
            SongRow(
                song = song,
                server = server,
                cachedSong = cachedSongsBySongId[song.id],
                isStreamCached = song.id in streamCachedSongIds,
                isDownloading = song.id in downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                isOfflinePlayable = isOfflinePlayable,
                onClick = {
                    if (isOfflineDegraded) {
                        playOfflineAwareSongs(
                            songs = songs,
                            startIndex = index,
                            isOfflineDegraded = true,
                            cachedSongsBySongId = cachedSongsBySongId,
                            streamCachedSongIds = streamCachedSongIds,
                            onPlaySongs = onPlaySongs,
                            onUnavailable = onOfflineSongUnavailable,
                        )
                    } else {
                        onPlayLibrarySongs(index)
                    }
                },
                onMore = { onShowSongActions(song) },
            )
        }
        if (isLoadingMore) {
            item {
                LoadingStateCard(stringResource(R.string.browse_loading_songs))
            }
        }
    }
}

private fun playOfflineAwareSongs(
    songs: List<Song>,
    startIndex: Int,
    isOfflineDegraded: Boolean,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onUnavailable: () -> Unit,
) {
    if (!isOfflineDegraded) {
        onPlaySongs(songs, startIndex)
        return
    }
    val startSong = songs.getOrNull(startIndex)
    if (startSong == null || !startSong.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)) {
        onUnavailable()
        return
    }

    val playableSongs = songs.filter { song -> song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds) }
    val playableStartIndex = songs.take(startIndex).count { song ->
        song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
    }
    if (playableSongs.isEmpty()) {
        onUnavailable()
    } else {
        onPlaySongs(playableSongs, playableStartIndex)
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
