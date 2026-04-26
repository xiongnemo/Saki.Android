package org.hdhmc.saki.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.presentation.library.BrowseScreen
import org.hdhmc.saki.presentation.library.NowPlayingOverlay
import org.hdhmc.saki.presentation.library.NowPlayingCapsule
import org.hdhmc.saki.presentation.serverconfig.ServerConfigRoute
import org.hdhmc.saki.presentation.settings.SettingsScreen
import kotlinx.coroutines.flow.collectLatest

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
    showNowPlaying: Boolean,
    onManageServers: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onDismissNowPlaying: () -> Unit,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSelectServer: (Long) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (org.hdhmc.saki.domain.model.AlbumListType) -> Unit,
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
    onUpdateBluetoothLyrics: (Boolean) -> Unit,
    onUpdateBufferStrategy: (org.hdhmc.saki.domain.model.BufferStrategy) -> Unit,
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
            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (showSettings) {
                        SettingsScreen(
                            uiState = uiState,
                            contentPadding = PaddingValues(),
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
                            onImportConfig = onImportConfig,
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
                showSettings = false
                onOpenArtistFromPlayback(track.serverId, track.artistId)
                onDismissNowPlaying()
            },
            onOpenAlbum = {
                showSettings = false
                onOpenAlbumFromPlayback(track.serverId, track.albumId)
                onDismissNowPlaying()
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
