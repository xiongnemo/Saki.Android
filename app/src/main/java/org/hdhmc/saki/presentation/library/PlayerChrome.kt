package org.hdhmc.saki.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import org.hdhmc.saki.R
import org.hdhmc.saki.presentation.EndpointProbeInfo
import org.hdhmc.saki.domain.model.PlaybackQueueItem
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.RepeatModeSetting
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SongLyrics
import java.io.File
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingCapsule(
    track: PlaybackQueueItem?,
    isPlaying: Boolean,
    currentServer: ServerConfig?,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSkipToNext: () -> Unit,
) {
    val elevation by animateDpAsState(
        targetValue = if (isPlaying) 12.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "capsuleElevation",
    )
    val onExpandState = rememberUpdatedState(onExpand)

    Card(
        onClick = onExpand,
        enabled = track != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .pointerInput(track != null) {
                if (track == null) return@pointerInput
                val distanceThresholdPx = 72.dp.toPx()
                val velocityThresholdPxPerSecond = 300.dp.toPx()
                var upwardDistance = 0f
                var dragStartedAtNanos = 0L
                var didOpen = false

                fun openFromSwipe() {
                    if (!didOpen) {
                        didOpen = true
                        onExpandState.value()
                    }
                }

                detectVerticalDragGestures(
                    onDragStart = {
                        upwardDistance = 0f
                        dragStartedAtNanos = System.nanoTime()
                        didOpen = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < 0f) {
                            upwardDistance -= dragAmount
                            if (upwardDistance >= distanceThresholdPx) {
                                openFromSwipe()
                            }
                        } else {
                            upwardDistance = (upwardDistance - dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        val elapsedSeconds = (System.nanoTime() - dragStartedAtNanos) / 1_000_000_000f
                        if (elapsedSeconds > 0f &&
                            upwardDistance / elapsedSeconds >= velocityThresholdPxPerSecond
                        ) {
                            openFromSwipe()
                        }
                    },
                    onDragCancel = {
                        upwardDistance = 0f
                        didOpen = false
                    },
                )
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(100),
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = Pair(track?.queueArtworkModel(currentServer), track?.title),
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "capsule-artwork",
                ) { (model, title) ->
                    ArtworkCard(
                        model = model,
                        contentDescription = title,
                        modifier = Modifier.size(46.dp),
                        cornerRadiusDp = 14,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = track?.title ?: stringResource(R.string.player_nothing_playing),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.let { listOfNotNull(it.artist, it.album).joinToString(" • ") }
                            ?: stringResource(R.string.player_start_from_browse),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onSkipToPrevious, enabled = track != null) {
                    Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = null)
                }
                IconButton(onClick = onPlayPause, enabled = track != null) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onSkipToNext, enabled = track != null) {
                    Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingOverlay(
    visible: Boolean,
    playbackState: PlaybackSessionState,
    track: PlaybackQueueItem,
    onDismiss: () -> Unit,
    canOpenArtist: Boolean,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    currentServer: ServerConfig?,
    servers: List<ServerConfig> = emptyList(),
    activeEndpointLabel: String? = null,
    activeEndpointId: Long? = null,
    isEndpointForced: Boolean = false,
    endpointProbeResults: List<EndpointProbeInfo> = emptyList(),
    isProbing: Boolean = false,
    onReprobeEndpoints: () -> Unit = {},
    onForceEndpoint: (Long) -> Unit = {},
    lyrics: SongLyrics? = null,
) {
    val serversById = remember(servers) { servers.associateBy { it.id } }
    val artwork = rememberArtworkPresentation(
        fallbackModel = track.queueArtworkModel(currentServer),
    )
    var showDetails by remember(track.songId) { mutableStateOf(false) }
    var showMenu by remember(track.songId) { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showEndpointStatus by remember { mutableStateOf(false) }

    // Preload adjacent cover art into Coil cache
    val context = LocalContext.current
    val queue = playbackState.queue
    val currentIdx = playbackState.currentIndex
    val prevSongId = queue.getOrNull(currentIdx - 1)?.songId
    val nextSongId = queue.getOrNull(currentIdx + 1)?.songId
    LaunchedEffect(track.songId, prevSongId, nextSongId, currentServer) {
        val adjacentIndices = listOfNotNull(
            if (currentIdx > 0) currentIdx - 1 else null,
            if (currentIdx < queue.lastIndex) currentIdx + 1 else null,
        )
        for (i in adjacentIndices) {
            val item = queue[i]
            val server = item.serverId?.let { serversById[it] }
            val model = item.queueArtworkModel(server) ?: continue
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(coil3.size.Size.ORIGINAL)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    BackHandler(enabled = visible) {
        onDismiss()
    }

    // Reset lyrics overlay when Now Playing is dismissed
    LaunchedEffect(visible) {
        if (!visible) showLyrics = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessVeryLow,
            ),
            initialOffsetY = { fullHeight -> fullHeight / 4 },
        ),
        exit = fadeOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            targetOffsetY = { fullHeight -> fullHeight / 3 },
        ),
    ) {
        val colorScheme = MaterialTheme.colorScheme
        val dominant = artwork.dominantColor ?: colorScheme.primary
        val accent = artwork.accentColor ?: colorScheme.tertiary
        val background = remember(dominant, accent, colorScheme) {
            Brush.verticalGradient(
                listOf(
                    dominant.copy(alpha = 0.20f).compositeOver(colorScheme.background),
                    accent.copy(alpha = 0.18f).compositeOver(colorScheme.surface),
                    colorScheme.background,
                ),
            )
        }
        var sliderValue by remember(track.songId) {
            mutableFloatStateOf(playbackState.positionMs.toFloat())
        }
        var isDragging by remember(track.songId) { mutableStateOf(false) }

        LaunchedEffect(playbackState.positionMs, track.songId) {
            if (!isDragging) {
                sliderValue = playbackState.positionMs.toFloat()
            }
        }

        // Artwork pager state synced with playback queue
        var stableQueue by remember { mutableStateOf(playbackState.queue) }
        val artworkPagerState = rememberPagerState(
            initialPage = playbackState.currentIndex.coerceAtLeast(0),
            pageCount = { stableQueue.size.coerceAtLeast(1) },
        )
        // Sync pager when track changes externally (button skip, queue tap)
        var lastTrackId by remember { mutableStateOf(track.songId) }
        // Guard: suppress pager-driven skips during programmatic scroll
        var suppressPagerSkip by remember { mutableStateOf(false) }
        // Buffer queue to avoid pager flash during shuffle toggle:
        // update queue snapshot only after pager has scrolled to correct page
        val targetPage = playbackState.currentIndex.coerceAtLeast(0)
        LaunchedEffect(targetPage, track.songId, playbackState.queue) {
            if (track.songId == lastTrackId && artworkPagerState.currentPage != targetPage) {
                suppressPagerSkip = true
                try { artworkPagerState.scrollToPage(targetPage) } finally { suppressPagerSkip = false }
            }
            stableQueue = playbackState.queue
            if (track.songId != lastTrackId && artworkPagerState.currentPage != targetPage) {
                suppressPagerSkip = true
                try { artworkPagerState.animateScrollToPage(targetPage) } finally { suppressPagerSkip = false }
            }
            lastTrackId = track.songId
        }
        // When user swipes pager, trigger skip
        val currentPlaybackIndex by rememberUpdatedState(playbackState.currentIndex)
        val currentQueueSize by rememberUpdatedState(playbackState.queue.size)
        LaunchedEffect(artworkPagerState) {
            snapshotFlow { artworkPagerState.settledPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (suppressPagerSkip) return@collect
                    if (page != currentPlaybackIndex && page in 0 until currentQueueSize) {
                        onSkipToQueueItem(page)
                    }
                }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            val playerSnackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var snackbarJob by remember { mutableStateOf<Job?>(null) }
            fun showPlayerSnackbar(message: String) {
                playerSnackbarHostState.currentSnackbarData?.dismiss()
                snackbarJob?.cancel()
                snackbarJob = scope.launch {
                    playerSnackbarHostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
                }
                scope.launch {
                    delay(1500)
                    snackbarJob?.cancel()
                    playerSnackbarHostState.currentSnackbarData?.dismiss()
                }
            }
            val combinedMetadata = listOfNotNull(track.artist, track.album).joinToString(" • ")
            val denseTitle = track.title.length >= 24
            val denseMetadata = combinedMetadata.length >= 42
            val compactControls = maxHeight < 640.dp
            val shortScreen = maxHeight < 700.dp
            val titleStyle = when {
                track.title.length >= 34 -> MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                )
                denseTitle || shortScreen -> MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                )
                else -> MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                )
            }
            val metadataStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (denseMetadata || shortScreen) 16.sp else 17.sp,
                lineHeight = if (denseMetadata || shortScreen) 20.sp else 22.sp,
            )
            val horizontalPadding = if (shortScreen) 16.dp else 20.dp
            val verticalSpacing = if (shortScreen) 8.dp else 12.dp
            val showQueueAffordance = playbackState.queue.size > 1

            // Queue bottom sheet state
            val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            var showQueueSheet by remember { mutableStateOf(false) }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                    Spacer(Modifier.heightIn(min = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_now_playing),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                        ) {
                            Text(
                                text = when {
                                    track.isCached -> stringResource(R.string.player_offline)
                                    playbackState.isStreamCached -> stringResource(R.string.player_cached)
                                    else -> stringResource(R.string.player_streaming)
                                } + " • ${localizeQualityLabel(track.qualityLabel)}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    // Cover art — fills remaining vertical space
                    val hasLyrics = lyrics != null && lyrics.lines.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable {
                                if (showLyrics) showLyrics = false
                                else if (hasLyrics) showLyrics = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        HorizontalPager(
                            state = artworkPagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(34.dp)),
                            pageSpacing = 16.dp,
                            beyondViewportPageCount = 1,
                        ) { page ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val queueItem = stableQueue.getOrNull(page)
                                ArtworkCard(
                                    model = queueItem?.queueArtworkModel(queueItem.serverId?.let { serversById[it] }),
                                    contentDescription = queueItem?.title,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .fillMaxHeight(),
                                    cornerRadiusDp = 34,
                                )
                            }
                        }
                        // Lyrics overlay on artwork
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showLyrics && hasLyrics,
                            enter = fadeIn(tween(250)),
                            exit = fadeOut(tween(250)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(34.dp))
                                    .background(Color.Black.copy(alpha = 0.65f)),
                            ) {
                                if (lyrics != null && lyrics.lines.isNotEmpty()) {
                                    SyncedLyricsView(
                                        lyrics = lyrics,
                                        positionMs = playbackState.positionMs,
                                        isPlaying = playbackState.isPlaying,
                                        onSeekTo = onSeekTo,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                                        textColor = Color.White,
                                    )
                                }
                                IconButton(
                                    onClick = { showLyrics = false },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.player_close_lyrics),
                                        tint = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                    // Repeat / Shuffle / More row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val repeatDescription = stringResource(R.string.player_repeat)
                        val repeatOneLabel = stringResource(R.string.player_repeat_one)
                        val repeatAllLabel = stringResource(R.string.player_repeat_all)
                        val repeatOffLabel = stringResource(R.string.player_repeat_off)
                        val shuffleDescription = stringResource(R.string.player_shuffle)
                        val shuffleOnLabel = stringResource(R.string.player_shuffle_on)
                        val shuffleOffLabel = stringResource(R.string.player_shuffle_off)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleIconButton(
                                icon = if (playbackState.repeatMode == RepeatModeSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                active = playbackState.repeatMode != RepeatModeSetting.OFF,
                                contentDescription = repeatDescription,
                                onClick = {
                                    onCycleRepeatMode()
                                    val label = when (playbackState.repeatMode) {
                                        RepeatModeSetting.OFF -> repeatAllLabel
                                        RepeatModeSetting.ALL -> repeatOneLabel
                                        RepeatModeSetting.ONE -> repeatOffLabel
                                    }
                                    showPlayerSnackbar(label)
                                },
                                compact = true,
                            )
                            ToggleIconButton(
                                icon = Icons.Rounded.Shuffle,
                                active = playbackState.shuffleEnabled,
                                contentDescription = shuffleDescription,
                                onClick = {
                                    onToggleShuffle()
                                    val label = if (!playbackState.shuffleEnabled) shuffleOnLabel else shuffleOffLabel
                                    showPlayerSnackbar(label)
                                },
                                compact = true,
                            )
                        }
                        Box {
                            PlayerActionButton(Icons.Rounded.MoreVert, stringResource(R.string.player_more), { showMenu = true }, true)
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_song_details)) },
                                    onClick = {
                                        showMenu = false
                                        showDetails = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(activeEndpointLabel ?: stringResource(R.string.player_no_endpoint)) },
                                    onClick = {
                                        showMenu = false
                                        showEndpointStatus = true
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        text = track.title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetadataLinkRow(
                        track = track,
                        textStyle = metadataStyle,
                        canOpenArtist = canOpenArtist,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                    )
                    val duration = playbackState.durationMs.coerceAtLeast(1L).toFloat()
                    val bufferFraction = if (playbackState.durationMs > 0) {
                        (playbackState.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                    } else 0f
                    val isCachedTrack = track.isCached || playbackState.isStreamCached
                    val sliderColors = if (isCachedTrack) {
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor = MaterialTheme.colorScheme.tertiary,
                            inactiveTrackColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        )
                    } else {
                        SliderDefaults.colors(
                            inactiveTrackColor = Color.Transparent,
                        )
                    }
                    val bufferColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Slider(
                        value = sliderValue.coerceIn(0f, duration),
                        onValueChange = {
                            isDragging = true
                            sliderValue = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeekTo(sliderValue.roundToLong())
                        },
                        valueRange = 0f..duration,
                        colors = sliderColors,
                        modifier = if (!isCachedTrack) {
                            Modifier.drawBehind {
                                val trackHeight = 4.dp.toPx()
                                val y = size.height / 2
                                val padding = 6.dp.toPx()
                                val trackWidth = size.width - padding * 2
                                val start = if (isRtl) size.width - padding else padding
                                val end = if (isRtl) padding else size.width - padding
                                drawLine(
                                    color = trackBgColor,
                                    start = Offset(start, y),
                                    end = Offset(end, y),
                                    strokeWidth = trackHeight,
                                    cap = StrokeCap.Round,
                                )
                                if (bufferFraction > 0f) {
                                    val bufferEnd = if (isRtl) {
                                        start - trackWidth * bufferFraction
                                    } else {
                                        start + trackWidth * bufferFraction
                                    }
                                    drawLine(
                                        color = bufferColor,
                                        start = Offset(start, y),
                                        end = Offset(bufferEnd, y),
                                        strokeWidth = trackHeight,
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                        } else Modifier,
                    )
                    Column(
                        modifier = Modifier.pointerInput(showQueueAffordance) {
                            if (!showQueueAffordance) return@pointerInput
                            val thresholdPx = 80.dp.toPx()
                            var accumulated = 0f
                            detectVerticalDragGestures(
                                onDragStart = { accumulated = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount < 0f) {
                                        accumulated -= dragAmount
                                        if (accumulated >= thresholdPx) {
                                            accumulated = 0f
                                            showQueueSheet = true
                                        }
                                    } else {
                                        accumulated = 0f
                                    }
                                },
                            )
                        },
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = formatDuration(sliderValue.roundToLong()), color = MaterialTheme.colorScheme.onBackground)
                            Text(text = formatDuration(playbackState.durationMs), color = MaterialTheme.colorScheme.onBackground)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerActionButton(
                                Icons.Rounded.SkipPrevious,
                                stringResource(R.string.player_previous),
                                onSkipToPrevious,
                                compactControls,
                            )
                            Spacer(Modifier.width(14.dp))
                            Surface(
                                modifier = Modifier.size(
                                    width = if (compactControls) 132.dp else 148.dp,
                                    height = if (compactControls) 64.dp else 72.dp,
                                ),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(onClick = onPlayPause)
                                        .padding(horizontal = 22.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = if (playbackState.isPlaying) {
                                            stringResource(R.string.player_pause)
                                        } else {
                                            stringResource(R.string.player_play)
                                        },
                                        modifier = Modifier.padding(start = 10.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            PlayerActionButton(
                                Icons.Rounded.SkipNext,
                                stringResource(R.string.player_next),
                                onSkipToNext,
                                compactControls,
                            )
                        }
                        // Tech info bar
                        val runtimeInfo = playbackState.runtimeInfo
                        val codec = runtimeInfo?.sampleMimeType?.let { mime ->
                            when {
                                "flac" in mime -> "FLAC"
                                "opus" in mime -> "Opus"
                                "vorbis" in mime -> "Vorbis"
                                "mp4a" in mime || "aac" in mime -> "AAC"
                                "mp3" in mime || "mpeg" in mime -> "MP3"
                                "wav" in mime || "raw" in mime -> "WAV"
                                else -> mime.substringAfter("/")
                            }
                        } ?: runtimeInfo?.codecs ?: track.suffix?.uppercase(java.util.Locale.ROOT)
                        val sampleRate = runtimeInfo?.sampleRate?.let {
                            if (it >= 1_000) {
                                val khz = it / 1_000.0
                                if (it % 1_000 == 0) "${it / 1_000} kHz" else "${"%.1f".format(khz)} kHz"
                            } else "$it Hz"
                        }
                        val bitrate = runtimeInfo?.averageBitrate?.let(::formatBitrate)
                            ?: track.bitRateKbps?.let { "$it kbps" }
                        val techParts = listOfNotNull(codec, sampleRate, bitrate)
                        if (techParts.isNotEmpty()) {
                            Text(
                                text = techParts.joinToString(" | "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        // Queue toggle
                        if (showQueueAffordance) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.player_show_queue),
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { showQueueSheet = true },
                                )
                            }
                        }
                    }
                }
            }

            // Queue BottomSheet
            if (showQueueSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQueueSheet = false },
                    sheetState = queueSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = stringResource(R.string.player_queue),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    val queueListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = (playbackState.currentIndex - 2).coerceAtLeast(0),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = queueListState,
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    ) {
                        itemsIndexed(playbackState.queue) { index, item ->
                            QueueRow(
                                item = item,
                                isCurrent = index == playbackState.currentIndex,
                                currentServer = item.serverId?.let { serversById[it] },
                                onClick = { onSkipToQueueItem(index) },
                                onRemove = { onRemoveQueueItem(index) },
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = playerSnackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.small,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.common_close))
                }
            },
            title = { Text(track.title) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { DetailLine(stringResource(R.string.detail_artist), track.artist) }
                    item { DetailLine(stringResource(R.string.detail_album), track.album) }
                    item { DetailLine(stringResource(R.string.detail_quality), localizeQualityLabel(track.qualityLabel)) }
                    item { DetailLine(stringResource(R.string.detail_server), currentServer?.name) }
                    item {
                        DetailLine(
                            stringResource(R.string.detail_mode),
                            if (track.isCached) {
                                stringResource(R.string.player_offline)
                            } else {
                                stringResource(R.string.player_streaming)
                            },
                        )
                    }
                    item { DetailLine(stringResource(R.string.detail_mime_type), playbackState.runtimeInfo?.sampleMimeType ?: track.contentType) }
                    item { DetailLine(stringResource(R.string.detail_container), playbackState.runtimeInfo?.containerMimeType) }
                    item { DetailLine(stringResource(R.string.detail_codec), playbackState.runtimeInfo?.codecs ?: track.suffix?.uppercase(java.util.Locale.ROOT)) }
                    item { DetailLine(stringResource(R.string.detail_average_bitrate), playbackState.runtimeInfo?.averageBitrate?.let(::formatBitrate) ?: track.bitRateKbps?.let { formatBitrate(it * 1_000) }) }
                    item { DetailLine(stringResource(R.string.detail_peak_bitrate), playbackState.runtimeInfo?.peakBitrate?.let(::formatBitrate)) }
                    item { DetailLine(stringResource(R.string.detail_sample_rate), playbackState.runtimeInfo?.sampleRate?.let(::formatSampleRate)) }
                    item { DetailLine(stringResource(R.string.detail_channels), playbackState.runtimeInfo?.channelCount?.toString()) }
                    item { DetailLine(stringResource(R.string.detail_language), playbackState.runtimeInfo?.language) }
                    item { DetailLine(stringResource(R.string.detail_cover_art_id), track.coverArtId) }
                    item { DetailLine(stringResource(R.string.detail_local_file), track.localPath) }
                }
            },
        )
    }

    if (showEndpointStatus) {
        AlertDialog(
            onDismissRequest = { showEndpointStatus = false },
            confirmButton = {
                TextButton(onClick = { showEndpointStatus = false }, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.common_close))
                }
            },
            title = { Text(stringResource(R.string.player_endpoint_status)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (endpointProbeResults.isEmpty()) {
                        Text(stringResource(R.string.player_no_probe_results), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        endpointProbeResults.forEach { result ->
                            val isActive = result.id == activeEndpointId
                            Surface(
                                onClick = { onForceEndpoint(result.id) },
                                enabled = result.reachable || (isActive && isEndpointForced),
                                shape = MaterialTheme.shapes.medium,
                                color = when {
                                    isActive -> MaterialTheme.colorScheme.primaryContainer
                                    !result.reachable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.label + when {
                                                isActive && isEndpointForced -> " 📌"
                                                isActive -> " ✓"
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (!result.reachable) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        Text(
                                            text = result.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (!result.reachable) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Text(
                                        text = if (result.reachable) {
                                            stringResource(R.string.server_config_latency_ms, result.latencyMs ?: 0)
                                        } else {
                                            stringResource(R.string.player_unreachable)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (result.reachable) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onReprobeEndpoints,
                        enabled = !isProbing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            if (isProbing) {
                                stringResource(R.string.player_probing)
                            } else {
                                stringResource(R.string.player_retest_endpoints)
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(if (compact) 48.dp else 56.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}

@Composable
private fun ToggleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val onText = stringResource(R.string.common_on)
    val offText = stringResource(R.string.common_off)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(if (compact) 48.dp else 56.dp)
                .semantics {
                    role = Role.Switch
                    stateDescription = "$contentDescription: ${if (active) onText else offText}"
                },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (compact) Modifier.size(20.dp) else Modifier,
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetadataLinkRow(
    track: PlaybackQueueItem,
    textStyle: TextStyle,
    canOpenArtist: Boolean,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(iterations = Int.MAX_VALUE),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        track.artist?.takeIf(String::isNotBlank)?.let { artist ->
            Text(
                text = artist,
                style = textStyle,
                color = if (canOpenArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    enabled = canOpenArtist,
                    onClick = onOpenArtist,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        if (!track.artist.isNullOrBlank() && !track.album.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(percent = 50),
                    ),
            )
        }
        track.album?.takeIf(String::isNotBlank)?.let { album ->
            Text(
                text = album,
                style = textStyle,
                color = if (track.albumId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    enabled = track.albumId != null,
                    onClick = onOpenAlbum,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: PlaybackQueueItem,
    isCurrent: Boolean,
    currentServer: ServerConfig?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkCard(
                model = item.queueArtworkModel(currentServer),
                contentDescription = item.title,
                modifier = Modifier.size(48.dp),
                cornerRadiusDp = 16,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(item.artist, item.album).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isCurrent) {
                TextButton(onClick = onRemove, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.common_remove))
                }
            }
        }
    }
}

@Composable
private fun localizeQualityLabel(label: String): String {
    val resId = when (label) {
        "Original" -> R.string.stream_quality_original
        "320 kbps" -> R.string.stream_quality_320_kbps
        "256 kbps" -> R.string.stream_quality_256_kbps
        "192 kbps" -> R.string.stream_quality_192_kbps
        "160 kbps" -> R.string.stream_quality_160_kbps
        "128 kbps" -> R.string.stream_quality_128_kbps
        "96 kbps" -> R.string.stream_quality_96_kbps
        else -> return label
    }
    return stringResource(resId)
}

@Composable
private fun DetailLine(label: String, value: String?) {
    val unknown = stringResource(R.string.detail_unknown)
    Text(
        text = stringResource(
            R.string.detail_line_format,
            label,
            value.orEmpty().ifBlank { unknown },
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun PlaybackQueueItem.queueArtworkModel(server: ServerConfig?): Any? {
    return when {
        !coverArtPath.isNullOrBlank() -> File(coverArtPath)
        server != null -> resolveArtworkModel(server, coverArtId, null)
        !artworkUri.isNullOrBlank() -> artworkUri
        else -> null
    }
}

private data class ArtworkPresentation(
    val image: ImageBitmap? = null,
    val dominantColor: Color? = null,
    val accentColor: Color? = null,
)

@Composable
private fun rememberArtworkPresentation(
    fallbackModel: Any?,
): ArtworkPresentation {
    val context = LocalContext.current.applicationContext
    return produceState(
        initialValue = ArtworkPresentation(),
        key1 = fallbackModel,
    ) {
        value = loadArtworkPresentation(context, fallbackModel)
    }.value
}

private suspend fun loadArtworkPresentation(
    context: android.content.Context,
    model: Any?,
): ArtworkPresentation = withContext(Dispatchers.IO) {
    if (model == null) return@withContext ArtworkPresentation()
    val request = coil3.request.ImageRequest.Builder(context)
        .data(model)
        .size(300)
        .build()
    val image = context.imageLoader.execute(request).image
        ?: return@withContext ArtworkPresentation()
    val bitmap = image.toBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)

    val palette = Palette.from(bitmap).clearFilters().generate()
    ArtworkPresentation(
        image = bitmap.asImageBitmap(),
        dominantColor = palette.getDominantColor(0).takeIf { it != 0 }?.let(::Color),
        accentColor = palette.getVibrantColor(0).takeIf { it != 0 }?.let(::Color),
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hours = minutes / 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes % 60L, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatBitrate(bitrate: Int): String {
    return if (bitrate >= 1_000_000) {
        "%.2f Mbps".format(bitrate / 1_000_000f)
    } else {
        "%.0f kbps".format(bitrate / 1_000f)
    }
}

private fun formatSampleRate(sampleRate: Int): String = "$sampleRate Hz"

@Composable
private fun SyncedLyricsView(
    lyrics: SongLyrics,
    positionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val lines = lyrics.lines
    val hasWordLevelTiming = lyrics.synced && lines.any { it.words != null }

    // High-frequency position: interpolate between 500ms global updates (only for karaoke)
    val smoothPositionMs by produceState(
        initialValue = positionMs,
        key1 = positionMs,
        key2 = isPlaying,
        key3 = hasWordLevelTiming,
    ) {
        value = positionMs
        if (!isPlaying || !hasWordLevelTiming) return@produceState
        var lastFrame = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val delta = (now - lastFrame) / 1_000_000
            lastFrame = now
            value += delta
        }
    }

    val activeIndex = if (lyrics.synced) {
        lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    } else {
        -1
    }

    val lyricsListState = rememberLazyListState()
    val density = LocalDensity.current

    if (lyrics.synced && activeIndex >= 0 && isPlaying) {
        LaunchedEffect(activeIndex) {
            val offsetPx = with(density) { 80.dp.roundToPx() }
            lyricsListState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -offsetPx,
            )
        }
    }

    LazyColumn(
        state = lyricsListState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            val style = if (isActive) {
                MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
            } else {
                MaterialTheme.typography.bodyMedium
            }
            val activeColor = if (isActive) textColor else textColor.copy(alpha = 0.45f)
            val dimColor = textColor.copy(alpha = 0.45f)

            if (isActive && line.words != null) {
                val lineEndMs = lines.getOrNull(index + 1)?.startMs ?: (line.words.last().startMs + 1000)
                val lineDurationMs = lineEndMs - line.startMs
                if (lineDurationMs > 0) {
                    val progress = ((smoothPositionMs - line.startMs).toFloat() / lineDurationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (lyrics.synced && line.startMs >= 0) {
                                    Modifier.clickable { onSeekTo(line.startMs) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Text(text = line.text, style = style, color = dimColor)
                        Text(
                            text = line.text,
                            style = style,
                            color = textColor,
                            modifier = Modifier.drawWithContent {
                                clipRect(right = size.width * progress) { this@drawWithContent.drawContent() }
                            },
                        )
                    }
                } else {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = style,
                        color = activeColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (lyrics.synced && line.startMs >= 0) {
                                    Modifier.clickable { onSeekTo(line.startMs) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 4.dp),
                    )
                }
            } else {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = style,
                    color = activeColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (lyrics.synced && line.startMs >= 0) {
                                Modifier.clickable { onSeekTo(line.startMs) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
