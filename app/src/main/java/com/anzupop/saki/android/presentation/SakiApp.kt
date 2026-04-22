package com.anzupop.saki.android.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anzupop.saki.android.presentation.library.BrowseScreen
import com.anzupop.saki.android.presentation.library.NowPlayingOverlay
import com.anzupop.saki.android.presentation.library.NowPlayingCapsule
import com.anzupop.saki.android.presentation.onboarding.OnboardingScreen
import com.anzupop.saki.android.presentation.serverconfig.ServerConfigRoute
import com.anzupop.saki.android.presentation.settings.SettingsScreen
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SakiApp(
    modifier: Modifier = Modifier,
    viewModel: SakiAppViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val endpointStatus by viewModel.endpointStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showServerManager by rememberSaveable { mutableStateOf(false) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val appDensity = remember(density, uiState.textScale) {
        Density(
            density = density.density,
            fontScale = density.fontScale * uiState.textScale.multiplier,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
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

                !uiState.hasCompletedOnboarding -> {
                    OnboardingScreen(
                        onContinue = viewModel::completeOnboarding,
                        onSetUpNow = {
                            viewModel.completeOnboarding()
                            showServerManager = true
                        },
                        onImportConfig = { uri ->
                            viewModel.importConfig(uri) { viewModel.completeOnboarding() }
                        },
                    )
                }

                else -> {
                    RootShell(
                        uiState = uiState,
                        snackbarHostState = snackbarHostState,
                        showNowPlaying = showNowPlaying,
                        onManageServers = { showServerManager = true },
                        onOpenNowPlaying = {
                            if (uiState.playbackState.currentItem != null) {
                                showNowPlaying = true
                            }
                        },
                        onDismissNowPlaying = { showNowPlaying = false },
                        onSelectBrowseSection = viewModel::selectBrowseSection,
                        onSelectServer = viewModel::selectServer,
                        onSetSearchActive = viewModel::setSearchActive,
                        onUpdateSearchQuery = viewModel::updateSearchQuery,
                        onRefreshCurrentTab = viewModel::refreshCurrentTab,
                        onSelectAlbumFeed = viewModel::selectAlbumFeed,
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
                        onUpdateAdaptiveQuality = viewModel::updateAdaptiveQuality,
                        onUpdateWifiStreamQuality = viewModel::updateWifiStreamQuality,
                        onUpdateMobileStreamQuality = viewModel::updateMobileStreamQuality,
                        onUpdateSoundBalancing = viewModel::updateSoundBalancing,
                        onUpdateStreamCacheSizeMb = viewModel::updateStreamCacheSizeMb,
                        onUpdateTextScale = viewModel::updateTextScale,
                        onReplayOnboarding = viewModel::replayOnboarding,
                        onUpdateBluetoothLyrics = viewModel::updateBluetoothLyrics,
                        onUpdateBufferStrategy = viewModel::updateBufferStrategy,
                        onUpdateCustomBufferSeconds = viewModel::updateCustomBufferSeconds,
                        onExportConfig = viewModel::exportConfig,
                        onImportConfig = { uri -> viewModel.importConfig(uri) },
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
                        endpointStatus = endpointStatus,
                        onReprobeEndpoints = viewModel::reprobeEndpoints,
                        onForceEndpoint = viewModel::forceEndpoint,
                    )
                }
            }

            if (showServerManager) {
                ServerConfigRoute(
                    modifier = Modifier.fillMaxSize(),
                    onCloseManager = { showServerManager = false },
                )
            }
        }
    }
}

@Composable
private fun RootShell(
    uiState: SakiAppUiState,
    snackbarHostState: SnackbarHostState,
    showNowPlaying: Boolean,
    onManageServers: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onDismissNowPlaying: () -> Unit,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSelectServer: (Long) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (com.anzupop.saki.android.domain.model.AlbumListType) -> Unit,
    onOpenArtist: (String) -> Unit,
    onCloseArtist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlaySongs: (List<com.anzupop.saki.android.domain.model.Song>, Int) -> Unit,
    onQueueSong: (com.anzupop.saki.android.domain.model.Song) -> Unit,
    onPlaySongNext: (com.anzupop.saki.android.domain.model.Song) -> Unit,
    onToggleSongDownload: (com.anzupop.saki.android.domain.model.Song) -> Unit,
    onPlayCachedSong: (com.anzupop.saki.android.domain.model.CachedSong) -> Unit,
    onPlayCachedQueue: (List<com.anzupop.saki.android.domain.model.CachedSong>, Int) -> Unit,
    onDeleteCachedSong: (String) -> Unit,
    onClearCachedSongs: () -> Unit,
    onUpdateStreamQuality: (com.anzupop.saki.android.domain.model.StreamQuality) -> Unit,
    onUpdateAdaptiveQuality: (Boolean) -> Unit,
    onUpdateWifiStreamQuality: (com.anzupop.saki.android.domain.model.StreamQuality) -> Unit,
    onUpdateMobileStreamQuality: (com.anzupop.saki.android.domain.model.StreamQuality) -> Unit,
    onUpdateSoundBalancing: (com.anzupop.saki.android.domain.model.SoundBalancingMode) -> Unit,
    onUpdateStreamCacheSizeMb: (Int) -> Unit,
    onUpdateTextScale: (com.anzupop.saki.android.domain.model.TextScale) -> Unit,
    onReplayOnboarding: () -> Unit,
    onUpdateBluetoothLyrics: (Boolean) -> Unit,
    onUpdateBufferStrategy: (com.anzupop.saki.android.domain.model.BufferStrategy) -> Unit,
    onUpdateCustomBufferSeconds: (Int) -> Unit,
    onExportConfig: (android.net.Uri) -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
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
    endpointStatus: EndpointStatus = EndpointStatus(),
    onReprobeEndpoints: () -> Unit = {},
    onForceEndpoint: (Long) -> Unit = {},
) {
    val currentOrQueuedTrack = uiState.playbackState.currentItem ?: uiState.playbackState.queue.firstOrNull()
    val shellBackgroundBrush = rememberBrowseBackgroundBrush()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val availableArtistIds = remember(uiState.libraryIndexes) {
        uiState.libraryIndexes
            ?.let { indexes ->
                (
                    indexes.shortcuts.map { it.id } +
                        indexes.sections.flatMap { section -> section.artists.map { artist -> artist.id } }
                    ).toSet()
            }
            .orEmpty()
    }

    BackHandler(enabled = showNowPlaying) {
        onDismissNowPlaying()
    }

    BackHandler(enabled = !showNowPlaying && showSettings) {
        showSettings = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shellBackgroundBrush),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (showSettings) {
                        SettingsScreen(
                            uiState = uiState,
                            contentPadding = PaddingValues(),
                            onManageServers = onManageServers,
                            onSelectServer = onSelectServer,
                            onUpdateStreamQuality = onUpdateStreamQuality,
                            onUpdateAdaptiveQuality = onUpdateAdaptiveQuality,
                            onUpdateWifiStreamQuality = onUpdateWifiStreamQuality,
                            onUpdateMobileStreamQuality = onUpdateMobileStreamQuality,
                            onUpdateSoundBalancing = onUpdateSoundBalancing,
                            onUpdateStreamCacheSizeMb = onUpdateStreamCacheSizeMb,
                            onUpdateTextScale = onUpdateTextScale,
                            onReplayOnboarding = onReplayOnboarding,
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
                            onManageServers = onManageServers,
                            onSelectBrowseSection = onSelectBrowseSection,
                            onSetSearchActive = onSetSearchActive,
                            onUpdateSearchQuery = onUpdateSearchQuery,
                            onRefreshCurrentTab = onRefreshCurrentTab,
                            onSelectAlbumFeed = onSelectAlbumFeed,
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
                            onOpenSettings = { showSettings = true },
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    )
                }

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

    uiState.playbackState.currentItem?.let { track ->
        val canOpenArtistFromNowPlaying = track.artistId != null && (
            uiState.libraryIndexes == null ||
                track.serverId == null ||
                track.serverId != uiState.selectedServerId ||
                track.artistId in availableArtistIds
            )
        NowPlayingOverlay(
            visible = showNowPlaying,
            playbackState = uiState.playbackState,
            track = track,
            onDismiss = onDismissNowPlaying,
            canOpenArtist = canOpenArtistFromNowPlaying,
            onOpenArtist = {
                onDismissNowPlaying()
                onOpenArtistFromPlayback(track.serverId, track.artistId)
            },
            onOpenAlbum = {
                onDismissNowPlaying()
                onOpenAlbumFromPlayback(track.serverId, track.albumId)
            },
            onPlayPause = {
                if (uiState.playbackState.isPlaying) onPausePlayback() else onResumePlayback()
            },
            onSkipToNext = onSkipToNext,
            onSkipToPrevious = onSkipToPrevious,
            onSeekTo = onSeekTo,
            onCycleRepeatMode = onCycleRepeatMode,
            onToggleShuffle = onToggleShuffle,
            onSkipToQueueItem = onSkipToQueueItem,
            onRemoveQueueItem = onRemoveQueueItem,
            currentServer = uiState.servers.firstOrNull { it.id == track.serverId },
            servers = uiState.servers,
            activeEndpointLabel = endpointStatus.activeEndpointLabel,
            activeEndpointId = endpointStatus.activeEndpointId,
            isEndpointForced = endpointStatus.isForced,
            endpointProbeResults = endpointStatus.probeResults,
            isProbing = endpointStatus.isProbing,
            onReprobeEndpoints = onReprobeEndpoints,
            onForceEndpoint = onForceEndpoint,
            lyrics = uiState.currentLyrics,
        )
    }
}
