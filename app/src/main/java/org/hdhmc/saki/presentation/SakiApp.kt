package org.hdhmc.saki.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.hdhmc.saki.domain.model.PlaybackProgressState
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.presentation.library.BrowseScreen
import org.hdhmc.saki.presentation.library.NowPlayingCapsule
import org.hdhmc.saki.presentation.library.NowPlayingOverlay
import org.hdhmc.saki.presentation.serverconfig.ServerConfigRoute
import org.hdhmc.saki.presentation.settings.SettingsScreen

@Composable
fun SakiApp(
    modifier: Modifier = Modifier,
    viewModel: SakiAppViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val endpointStatus by viewModel.endpointStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showServerManager by rememberSaveable { mutableStateOf(false) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val appDensity = remember(density, uiState.textScale) {
        Density(
            density = density.density,
            fontScale = density.fontScale * uiState.textScale.multiplier,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text.asString(context),
                actionLabel = msg.action?.let { context.getString(it.labelRes) },
                duration = msg.duration,
            )
            if (result == SnackbarResult.ActionPerformed && msg.action == SnackbarAction.RESTART) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openNowPlayingRequests.collectLatest {
            showNowPlaying = true
            viewModel.refreshEndpointStatus()
        }
    }

    CompositionLocalProvider(LocalDensity provides appDensity) {
        Box(modifier = modifier.fillMaxSize()) {
            when {
                !uiState.isAppReady -> {
                    Surface(modifier = Modifier.fillMaxSize()) {}
                }

                else -> {
                    RootShell(
                        uiState = uiState,
                        snackbarHostState = snackbarHostState,
                        showSettings = showSettings,
                        onShowSettingsChange = { showSettings = it },
                        onManageServers = { showServerManager = true },
                        onOpenNowPlaying = {
                            if (uiState.playbackState.currentItem != null) {
                                showNowPlaying = true
                            }
                        },
                        onSelectBrowseSection = viewModel::selectBrowseSection,
                        onSelectServer = viewModel::selectServer,
                        onSetSearchActive = viewModel::setSearchActive,
                        onUpdateSearchQuery = viewModel::updateSearchQuery,
                        onRemoveRecentSearchQuery = viewModel::removeRecentSearchQuery,
                        onClearRecentSearchQueries = viewModel::clearRecentSearchQueries,
                        onRefreshCurrentTab = viewModel::refreshCurrentTab,
                        onSelectAlbumFeed = viewModel::selectAlbumFeed,
                        onLoadMoreAlbums = viewModel::loadMoreAlbums,
                        onUpdateAlbumViewMode = viewModel::updateAlbumViewMode,
                        onOpenArtist = viewModel::openArtist,
                        onCloseArtist = viewModel::closeArtist,
                        onOpenAlbum = viewModel::openAlbum,
                        onCloseAlbum = viewModel::closeAlbum,
                        onOpenPlaylist = viewModel::openPlaylist,
                        onClosePlaylist = viewModel::closePlaylist,
                        onPlaySongs = viewModel::playSongs,
                        onQueueSong = viewModel::queueSong,
                        onPlaySongNext = viewModel::playSongNext,
                        onToggleSongDownload = viewModel::toggleSongDownload,
                        onPlayCachedSong = viewModel::playCachedSong,
                        onPlayCachedQueue = viewModel::playCachedQueue,
                        onDeleteCachedSong = viewModel::deleteCachedSong,
                        onClearCachedSongs = viewModel::clearCachedSongs,
                        onUpdateStreamQuality = viewModel::updateStreamQuality,
                        onUpdateDownloadQuality = viewModel::updateDownloadQuality,
                        onUpdateAdaptiveQuality = viewModel::updateAdaptiveQuality,
                        onUpdateWifiStreamQuality = viewModel::updateWifiStreamQuality,
                        onUpdateMobileStreamQuality = viewModel::updateMobileStreamQuality,
                        onUpdateSoundBalancing = viewModel::updateSoundBalancing,
                        onUpdateStreamCacheSizeMb = viewModel::updateStreamCacheSizeMb,
                        onClearStreamCache = viewModel::clearStreamCache,
                        onUpdateImageCacheSizeMb = viewModel::updateImageCacheSizeMb,
                        onClearImageCache = viewModel::clearImageCache,
                        onUpdateTextScale = viewModel::updateTextScale,
                        onUpdateLanguage = viewModel::updateLanguage,
                        onUpdateThemeMode = viewModel::updateThemeMode,
                        onUpdateDefaultBrowseTab = viewModel::updateDefaultBrowseTab,
                        onUpdateDefaultAlbumFeed = viewModel::updateDefaultAlbumFeed,
                        onUpdateBluetoothLyrics = viewModel::updateBluetoothLyrics,
                        onUpdateBufferStrategy = viewModel::updateBufferStrategy,
                        onUpdateCustomBufferSeconds = viewModel::updateCustomBufferSeconds,
                        onExportConfig = viewModel::exportConfig,
                        onImportConfig = { uri -> viewModel.importConfig(uri) },
                        onPausePlayback = viewModel::pausePlayback,
                        onResumePlayback = viewModel::resumePlayback,
                        onSkipToNext = viewModel::skipToNext,
                        onSkipToPrevious = viewModel::skipToPrevious,
                    )
                    NowPlayingOverlayHost(
                        visible = showNowPlaying,
                        playbackState = uiState.playbackState,
                        playbackProgressFlow = viewModel.playbackProgress,
                        servers = uiState.servers,
                        selectedServerId = uiState.selectedServerId,
                        libraryIndexes = uiState.libraryIndexes,
                        endpointStatus = endpointStatus,
                        lyrics = uiState.currentLyrics,
                        onDismiss = { showNowPlaying = false },
                        onCloseSettings = { showSettings = false },
                        onOpenArtistFromPlayback = viewModel::openArtistFromPlayback,
                        onOpenAlbumFromPlayback = viewModel::openAlbumFromPlayback,
                        onPausePlayback = viewModel::pausePlayback,
                        onResumePlayback = viewModel::resumePlayback,
                        onSkipToNext = viewModel::skipToNext,
                        onSkipToPrevious = viewModel::skipToPrevious,
                        onSeekTo = viewModel::seekTo,
                        onCycleRepeatMode = viewModel::cycleRepeatMode,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onSkipToQueueItem = viewModel::skipToQueueItem,
                        onRemoveQueueItem = viewModel::removeQueueItem,
                        onReprobeEndpoints = viewModel::reprobeEndpoints,
                        onForceEndpoint = viewModel::forceEndpoint,
                    )
                }
            }

            if (showServerManager) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ServerConfigRoute(
                        modifier = Modifier.fillMaxSize(),
                        onCloseManager = { showServerManager = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootShell(
    uiState: SakiAppUiState,
    snackbarHostState: SnackbarHostState,
    showSettings: Boolean,
    onShowSettingsChange: (Boolean) -> Unit,
    onManageServers: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSelectServer: (Long) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (org.hdhmc.saki.domain.model.AlbumListType) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onUpdateAlbumViewMode: (org.hdhmc.saki.domain.model.AlbumViewMode) -> Unit,
    onOpenArtist: (String) -> Unit,
    onCloseArtist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlaySongs: (List<org.hdhmc.saki.domain.model.Song>, Int) -> Unit,
    onQueueSong: (org.hdhmc.saki.domain.model.Song) -> Unit,
    onPlaySongNext: (org.hdhmc.saki.domain.model.Song) -> Unit,
    onToggleSongDownload: (org.hdhmc.saki.domain.model.Song) -> Unit,
    onPlayCachedSong: (org.hdhmc.saki.domain.model.CachedSong) -> Unit,
    onPlayCachedQueue: (List<org.hdhmc.saki.domain.model.CachedSong>, Int) -> Unit,
    onDeleteCachedSong: (String) -> Unit,
    onClearCachedSongs: () -> Unit,
    onUpdateStreamQuality: (org.hdhmc.saki.domain.model.StreamQuality) -> Unit,
    onUpdateDownloadQuality: (org.hdhmc.saki.domain.model.StreamQuality) -> Unit,
    onUpdateAdaptiveQuality: (Boolean) -> Unit,
    onUpdateWifiStreamQuality: (org.hdhmc.saki.domain.model.StreamQuality) -> Unit,
    onUpdateMobileStreamQuality: (org.hdhmc.saki.domain.model.StreamQuality) -> Unit,
    onUpdateSoundBalancing: (org.hdhmc.saki.domain.model.SoundBalancingMode) -> Unit,
    onUpdateStreamCacheSizeMb: (Int) -> Unit,
    onClearStreamCache: () -> Unit,
    onUpdateImageCacheSizeMb: (Int) -> Unit,
    onClearImageCache: () -> Unit,
    onUpdateTextScale: (org.hdhmc.saki.domain.model.TextScale) -> Unit,
    onUpdateLanguage: (org.hdhmc.saki.domain.model.AppLanguage) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateDefaultBrowseTab: (org.hdhmc.saki.domain.model.DefaultBrowseTab) -> Unit,
    onUpdateDefaultAlbumFeed: (org.hdhmc.saki.domain.model.AlbumListType) -> Unit,
    onUpdateBluetoothLyrics: (Boolean) -> Unit,
    onUpdateBufferStrategy: (org.hdhmc.saki.domain.model.BufferStrategy) -> Unit,
    onUpdateCustomBufferSeconds: (Int) -> Unit,
    onExportConfig: (android.net.Uri) -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
) {
    val currentOrQueuedTrack = uiState.playbackState.currentItem ?: uiState.playbackState.queue.firstOrNull()
    val shellBackgroundBrush = rememberBrowseBackgroundBrush()
    val density = LocalDensity.current
    val defaultCapsuleHeightPx = with(density) { 72.dp.roundToPx() }
    var capsuleHeightPx by remember { mutableIntStateOf(defaultCapsuleHeightPx) }
    val capsuleOverlayPadding = with(density) { capsuleHeightPx.toDp() }

    BackHandler(enabled = showSettings) {
        onShowSettingsChange(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shellBackgroundBrush),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                if (showSettings) {
                    SettingsScreen(
                        uiState = uiState,
                        contentPadding = PaddingValues(),
                        bottomOverlayPadding = capsuleOverlayPadding,
                        onManageServers = onManageServers,
                        onSelectServer = onSelectServer,
                        onUpdateStreamQuality = onUpdateStreamQuality,
                        onUpdateDownloadQuality = onUpdateDownloadQuality,
                        onUpdateAdaptiveQuality = onUpdateAdaptiveQuality,
                        onUpdateWifiStreamQuality = onUpdateWifiStreamQuality,
                        onUpdateMobileStreamQuality = onUpdateMobileStreamQuality,
                        onUpdateSoundBalancing = onUpdateSoundBalancing,
                        onUpdateStreamCacheSizeMb = onUpdateStreamCacheSizeMb,
                        onClearStreamCache = onClearStreamCache,
                        onUpdateImageCacheSizeMb = onUpdateImageCacheSizeMb,
                        onClearImageCache = onClearImageCache,
                        onUpdateTextScale = onUpdateTextScale,
                        onUpdateLanguage = onUpdateLanguage,
                        onUpdateThemeMode = onUpdateThemeMode,
                        onUpdateDefaultBrowseTab = onUpdateDefaultBrowseTab,
                        onUpdateDefaultAlbumFeed = onUpdateDefaultAlbumFeed,
                        onUpdateBluetoothLyrics = onUpdateBluetoothLyrics,
                        onUpdateBufferStrategy = onUpdateBufferStrategy,
                        onUpdateCustomBufferSeconds = onUpdateCustomBufferSeconds,
                        onExportConfig = onExportConfig,
                        onImportConfig = onImportConfig,
                        onPlayCachedSong = onPlayCachedSong,
                        onPlayCachedQueue = onPlayCachedQueue,
                        onDeleteCachedSong = onDeleteCachedSong,
                        onClearCachedSongs = onClearCachedSongs,
                    )
                } else {
                    BrowseScreen(
                        uiState = uiState,
                        contentPadding = PaddingValues(),
                        bottomOverlayPadding = capsuleOverlayPadding,
                        onManageServers = onManageServers,
                        onSelectBrowseSection = onSelectBrowseSection,
                        onSetSearchActive = onSetSearchActive,
                        onUpdateSearchQuery = onUpdateSearchQuery,
                        onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
                        onClearRecentSearchQueries = onClearRecentSearchQueries,
                        onRefreshCurrentTab = onRefreshCurrentTab,
                        onSelectAlbumFeed = onSelectAlbumFeed,
                        onLoadMoreAlbums = onLoadMoreAlbums,
                        onUpdateAlbumViewMode = onUpdateAlbumViewMode,
                        onOpenArtist = onOpenArtist,
                        onCloseArtist = onCloseArtist,
                        onOpenAlbum = onOpenAlbum,
                        onCloseAlbum = onCloseAlbum,
                        onOpenPlaylist = onOpenPlaylist,
                        onClosePlaylist = onClosePlaylist,
                        onPlaySongs = onPlaySongs,
                        onQueueSong = onQueueSong,
                        onPlaySongNext = onPlaySongNext,
                        onToggleSongDownload = onToggleSongDownload,
                        onOpenSettings = { onShowSettingsChange(true) },
                        onImportConfig = onImportConfig,
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = capsuleOverlayPadding),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coordinates ->
                            val measuredHeight = coordinates.size.height
                            if (capsuleHeightPx != measuredHeight) {
                                capsuleHeightPx = measuredHeight
                            }
                        },
                ) {
                    NowPlayingCapsule(
                        track = currentOrQueuedTrack,
                        isPlaying = uiState.playbackState.isPlaying,
                        currentServer = currentOrQueuedTrack?.serverId?.let { sid ->
                            uiState.servers.firstOrNull { it.id == sid }
                        },
                        onExpand = onOpenNowPlaying,
                        onPlayPause = {
                            if (uiState.playbackState.isPlaying) onPausePlayback() else onResumePlayback()
                        },
                        onSkipToPrevious = onSkipToPrevious,
                        onSkipToNext = onSkipToNext,
                    )
                }
            }
        }
    }
}

/**
 * Isolates [playbackProgressFlow] collection so progress ticks only recompose
 * the overlay subtree, not [RootShell].
 */
@Composable
private fun NowPlayingOverlayHost(
    visible: Boolean,
    playbackState: PlaybackSessionState,
    playbackProgressFlow: StateFlow<PlaybackProgressState>,
    servers: List<ServerConfig>,
    selectedServerId: Long?,
    libraryIndexes: org.hdhmc.saki.domain.model.LibraryIndexes?,
    endpointStatus: EndpointStatus,
    lyrics: SongLyrics?,
    onDismiss: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenArtistFromPlayback: (Long?, String?) -> Unit,
    onOpenAlbumFromPlayback: (Long?, String?) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onReprobeEndpoints: () -> Unit,
    onForceEndpoint: (Long) -> Unit,
) {
    val activeTrack = playbackState.currentItem
        ?: playbackState.queue.getOrNull(playbackState.currentIndex)
    var stableTrack by remember { mutableStateOf(activeTrack) }
    LaunchedEffect(visible, activeTrack) {
        if (activeTrack != null) {
            stableTrack = activeTrack
        } else if (!visible) {
            stableTrack = null
        }
    }
    val track = activeTrack ?: stableTrack ?: return
    val availableArtistIds = remember(libraryIndexes) {
        libraryIndexes
            ?.let { indexes ->
                (
                    indexes.shortcuts.map { it.id } +
                        indexes.sections.flatMap { section -> section.artists.map { artist -> artist.id } }
                    ).toSet()
            }
            .orEmpty()
    }
    val canOpenArtist = track.artistId != null && (
        libraryIndexes == null ||
            track.serverId == null ||
            track.serverId != selectedServerId ||
            track.artistId in availableArtistIds
        )
    val progress = if (visible) {
        playbackProgressFlow.collectAsStateWithLifecycle().value
    } else {
        playbackState.toProgressState()
    }
    NowPlayingOverlay(
        visible = visible,
        playbackState = playbackState,
        playbackProgress = progress,
        track = track,
        onDismiss = onDismiss,
        canOpenArtist = canOpenArtist,
        onOpenArtist = {
            onCloseSettings()
            onOpenArtistFromPlayback(track.serverId, track.artistId)
            onDismiss()
        },
        onOpenAlbum = {
            onCloseSettings()
            onOpenAlbumFromPlayback(track.serverId, track.albumId)
            onDismiss()
        },
        onPlayPause = {
            if (playbackState.isPlaying) onPausePlayback() else onResumePlayback()
        },
        onSkipToNext = onSkipToNext,
        onSkipToPrevious = onSkipToPrevious,
        onSeekTo = onSeekTo,
        onCycleRepeatMode = onCycleRepeatMode,
        onToggleShuffle = onToggleShuffle,
        onSkipToQueueItem = onSkipToQueueItem,
        onRemoveQueueItem = onRemoveQueueItem,
        currentServer = servers.firstOrNull { it.id == track.serverId },
        servers = servers,
        activeEndpointLabel = endpointStatus.activeEndpointLabel,
        activeEndpointId = endpointStatus.activeEndpointId,
        isEndpointForced = endpointStatus.isForced,
        endpointProbeResults = endpointStatus.probeResults,
        isProbing = endpointStatus.isProbing,
        onReprobeEndpoints = onReprobeEndpoints,
        onForceEndpoint = onForceEndpoint,
        lyrics = lyrics,
    )
}

private fun PlaybackSessionState.toProgressState(): PlaybackProgressState {
    return PlaybackProgressState(
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
    )
}
