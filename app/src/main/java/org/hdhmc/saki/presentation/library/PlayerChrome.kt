package org.hdhmc.saki.presentation.library

import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
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
import org.hdhmc.saki.domain.model.PlaybackProgressState
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.RepeatModeSetting
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SongLyrics
import java.io.File
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
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
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp)
            .pointerInput(track != null) {
                if (track == null) return@pointerInput
                val distanceThresholdPx = 72.dp.toPx()
                val velocityThresholdDpPerSecond = 300f
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
                        val upwardVelocityDpPerSecond = if (elapsedSeconds > 0f) {
                            (upwardDistance / elapsedSeconds).toDp().value
                        } else {
                            0f
                        }
                        if (elapsedSeconds > 0f &&
                            upwardVelocityDpPerSecond >= velocityThresholdDpPerSecond
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
            AnimatedVisibility(visible = track != null) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(100),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = Pair(
                        track?.queueArtworkModel(currentServer),
                        track?.title,
                    ),
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "capsule-artwork",
                ) { (model, title) ->
                    ArtworkCard(
                        model = model,
                        contentDescription = title,
                        modifier = Modifier.size(46.dp),
                        cornerRadiusDp = 14,
                        requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
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
    playbackProgress: PlaybackProgressState,
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
    var showDetails by remember(track.songId) { mutableStateOf(false) }
    var showMenu by remember(track.songId) { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showEndpointStatus by remember { mutableStateOf(false) }

    // Preload adjacent artwork into Coil and palette caches.
    val context = LocalContext.current
    val queue = playbackState.queue
    val currentIdx = playbackState.currentIndex
    val prevSongId = queue.getOrNull(currentIdx - 1)?.songId
    val nextSongId = queue.getOrNull(currentIdx + 1)?.songId
    LaunchedEffect(visible, track.songId, prevSongId, nextSongId, currentServer) {
        if (!visible) return@LaunchedEffect
        val adjacentIndices = listOfNotNull(
            if (currentIdx > 0) currentIdx - 1 else null,
            if (currentIdx < queue.lastIndex) currentIdx + 1 else null,
        ).distinct()
        for (i in adjacentIndices) {
            val item = queue[i]
            val server = item.serverId?.let { serversById[it] }
            val model = item.queueArtworkModel(server) ?: continue
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(FULL_COVER_ART_SIZE_PX)
                .build()
            context.imageLoader.enqueue(request)
            prewarmArtworkPresentation(context.applicationContext, model)
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
        val artwork = rememberArtworkPresentation(
            fallbackModel = track.queueArtworkModel(currentServer),
        )
        val colorScheme = MaterialTheme.colorScheme
        val targetDominant = artwork.dominantColor ?: colorScheme.primary
        val targetAccent = artwork.accentColor ?: colorScheme.tertiary
        val dominant by animateColorAsState(
            targetValue = targetDominant,
            animationSpec = tween(
                durationMillis = ARTWORK_COLOR_TRANSITION_MS,
                easing = FastOutSlowInEasing,
            ),
            label = "NowPlayingDominantColor",
        )
        val accent by animateColorAsState(
            targetValue = targetAccent,
            animationSpec = tween(
                durationMillis = ARTWORK_COLOR_TRANSITION_MS,
                easing = FastOutSlowInEasing,
            ),
            label = "NowPlayingAccentColor",
        )
        val background = remember(dominant, accent, colorScheme) {
            Brush.verticalGradient(
                listOf(
                    dominant.copy(alpha = 0.50f).compositeOver(colorScheme.background),
                    accent.copy(alpha = 0.35f).compositeOver(colorScheme.surface),
                    dominant.copy(alpha = 0.12f).compositeOver(colorScheme.background),
                ),
            )
        }
        val isDark = colorScheme.background.luminance() < 0.5f
        val playButtonColor = remember(dominant, isDark) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominant.toArgb(), hsv)
            hsv[1] = hsv[1].coerceAtMost(0.35f)
            hsv[2] = if (isDark) 0.30f else 0.88f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        val onPlayButtonColor = if (isDark) Color.White else Color.Black
        val sliderActiveColor = remember(dominant, isDark) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominant.toArgb(), hsv)
            hsv[1] = hsv[1].coerceIn(0.30f, 0.55f)
            hsv[2] = if (isDark) 0.70f else 0.45f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        val sliderInactiveColor = sliderActiveColor.copy(alpha = 0.25f)
        var sliderValue by remember(track.songId) {
            mutableFloatStateOf(playbackProgress.positionMs.toFloat())
        }
        var isDragging by remember(track.songId) { mutableStateOf(false) }

        LaunchedEffect(playbackProgress.positionMs, track.songId) {
            if (!isDragging) {
                sliderValue = playbackProgress.positionMs.toFloat()
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(Unit) { detectTapGestures { /* consume all taps */ } },
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
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingArtworkPagerHost(
                            queue = playbackState.queue,
                            currentIndex = playbackState.currentIndex,
                            currentTrack = track,
                            serversById = serversById,
                            modifier = Modifier.fillMaxSize(),
                            onArtworkClick = {
                                if (showLyrics) showLyrics = false
                                else if (hasLyrics) showLyrics = true
                            },
                            onUserSelectQueueItem = onSkipToQueueItem,
                        )
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
                                        positionMs = playbackProgress.positionMs,
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
                        Row(
                            modifier = Modifier.offset(x = (-12).dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                        Box(modifier = Modifier.offset(x = 12.dp)) {
                            PressScaleIconButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.player_more),
                                onClick = { showMenu = true },
                                compact = true,
                            )
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
                    val duration = playbackProgress.durationMs.coerceAtLeast(1L).toFloat()
                    val bufferFraction = if (playbackProgress.durationMs > 0) {
                        (playbackProgress.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                    } else 0f
                    val isCachedTrack = track.isCached || playbackState.isStreamCached
                    val sliderColors = if (isCachedTrack) {
                        SliderDefaults.colors(
                            thumbColor = sliderActiveColor,
                            activeTrackColor = sliderActiveColor,
                            inactiveTrackColor = sliderInactiveColor,
                        )
                    } else {
                        SliderDefaults.colors(
                            thumbColor = sliderActiveColor,
                            activeTrackColor = sliderActiveColor,
                            inactiveTrackColor = Color.Transparent,
                        )
                    }
                    val bufferColor = sliderActiveColor.copy(alpha = 0.3f)
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
                            Text(text = formatDuration(playbackProgress.durationMs), color = MaterialTheme.colorScheme.onBackground)
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
                                color = playButtonColor,
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
                                        tint = onPlayButtonColor,
                                    )
                                    Text(
                                        text = if (playbackState.isPlaying) {
                                            stringResource(R.string.player_pause)
                                        } else {
                                            stringResource(R.string.player_play)
                                        },
                                        modifier = Modifier.padding(start = 10.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = onPlayButtonColor,
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
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(if (compact) 48.dp else 56.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label)
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
    PressScaleIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        compact = compact,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (active) 1f else 0.38f),
        role = Role.Switch,
        semanticStateDescription = "$contentDescription: ${if (active) onText else offText}",
    )
}

@Composable
private fun PressScaleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    tint: Color? = null,
    role: Role = Role.Button,
    semanticStateDescription: String? = null,
) {
    val iconTint = tint ?: MaterialTheme.colorScheme.onBackground
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "PressScaleIconButtonPressScale",
    )

    Box(
        modifier = Modifier
            .size(if (compact) 48.dp else 56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .then(
                if (semanticStateDescription != null) {
                    Modifier.semantics {
                        stateDescription = semanticStateDescription
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = if (compact) Modifier.size(24.dp) else Modifier,
        )
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
                requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
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

private fun PlaybackQueueItem.artworkIdentityKey(): String {
    return listOf(
        mediaId,
        serverId?.toString().orEmpty(),
        coverArtId.orEmpty(),
        coverArtPath.orEmpty(),
        artworkUri.orEmpty(),
    ).joinToString("|")
}

private data class ArtworkPresentation(
    val dominantColor: Color? = null,
    val accentColor: Color? = null,
) {
    val hasColors: Boolean
        get() = dominantColor != null || accentColor != null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingArtworkPagerHost(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int,
    currentTrack: PlaybackQueueItem,
    serversById: Map<Long, ServerConfig>,
    modifier: Modifier = Modifier,
    onArtworkClick: () -> Unit,
    onUserSelectQueueItem: (Int) -> Unit,
) {
    val targetPage = currentIndex.coerceAtLeast(0)
    val queueIdentity = remember(queue) { queue.map { it.artworkIdentityKey() } }
    val latestOnArtworkClick by rememberUpdatedState(onArtworkClick)
    val latestOnUserSelectQueueItem by rememberUpdatedState(onUserSelectQueueItem)
    var stableQueue by remember { mutableStateOf(queue) }
    val artworkPagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { stableQueue.size.coerceAtLeast(1) },
    )

    var lastTrackId by remember { mutableStateOf(currentTrack.songId) }
    // Programmatic sync keeps pager state aligned but never drives playback.
    // Any other settled page change comes from the user's pager gesture.
    // A separate cover transition handles button/notification skip animation.
    var programmaticPagerSync by remember { mutableStateOf(false) }
    var currentArtworkKeyPage by remember { mutableStateOf(targetPage) }
    var pinnedCurrentArtworkPage by remember { mutableStateOf<Int?>(null) }
    var artworkTransitionSequence by remember { mutableStateOf(0) }
    var programmaticArtworkTransition by remember { mutableStateOf<ProgrammaticArtworkTransition?>(null) }
    var programmaticArtworkTransitionCoversPager by remember { mutableStateOf(false) }
    val isArtworkPagerDragged by artworkPagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isArtworkPagerDragged, programmaticPagerSync) {
        if (isArtworkPagerDragged && !programmaticPagerSync) {
            programmaticArtworkTransition = null
            programmaticArtworkTransitionCoversPager = false
        }
    }

    // Stabilize artwork during deferred queue expansion:
    // keep the current artwork keyed to the visible page until the pager can
    // move after any page-count change caused by queue item insertion. If
    // the user starts swiping during this handoff, do not force it back.
    LaunchedEffect(targetPage, currentTrack.songId, queueIdentity) {
        if (currentTrack.songId == lastTrackId && artworkPagerState.currentPage != targetPage) {
            val startPage = artworkPagerState.currentPage
            pinnedCurrentArtworkPage = startPage
            currentArtworkKeyPage = startPage
            try {
                stableQueue = queue
                withFrameNanos { }
                while (artworkPagerState.isScrollInProgress) {
                    withFrameNanos { }
                }
                val userMovedPager =
                    artworkPagerState.currentPage != startPage ||
                    artworkPagerState.settledPage != startPage
                if (!userMovedPager && artworkPagerState.currentPage != targetPage) {
                    programmaticPagerSync = true
                    try {
                        artworkPagerState.scrollToPage(targetPage)
                        withFrameNanos { }
                    } finally {
                        programmaticPagerSync = false
                    }
                }
                currentArtworkKeyPage = if (userMovedPager) targetPage else artworkPagerState.currentPage
                pinnedCurrentArtworkPage = null
                withFrameNanos { }
            } finally {
                pinnedCurrentArtworkPage = null
            }
        } else {
            stableQueue = queue
            currentArtworkKeyPage = targetPage
        }
        if (currentTrack.songId != lastTrackId && artworkPagerState.currentPage != targetPage) {
            val startPage = artworkPagerState.currentPage
            val direction = (targetPage - startPage).coerceIn(-1, 1)
            val fromItem = programmaticArtworkTransition?.to ?: stableQueue.getOrNull(startPage)
            val toItem = queue.getOrNull(targetPage) ?: currentTrack
            if (direction != 0) {
                artworkTransitionSequence += 1
                programmaticArtworkTransition = ProgrammaticArtworkTransition(
                    sequence = artworkTransitionSequence,
                    from = fromItem,
                    to = toItem,
                    direction = direction,
                )
                programmaticArtworkTransitionCoversPager = true
            }
            programmaticPagerSync = true
            try {
                artworkPagerState.scrollToPage(targetPage)
                withFrameNanos { }
            } finally {
                programmaticPagerSync = false
            }
        }
        lastTrackId = currentTrack.songId
    }

    val currentPlaybackIndex by rememberUpdatedState(currentIndex)
    val currentQueueSize by rememberUpdatedState(queue.size)
    LaunchedEffect(artworkPagerState) {
        snapshotFlow { artworkPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (programmaticPagerSync) return@collect
                if (page != currentPlaybackIndex && page in 0 until currentQueueSize) {
                    latestOnUserSelectQueueItem(page)
                }
            }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = artworkPagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (programmaticArtworkTransitionCoversPager) 0f else 1f
                }
                .clip(RoundedCornerShape(34.dp)),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            key = { page ->
                if (page == currentArtworkKeyPage) {
                    "current-${currentTrack.mediaId}"
                } else {
                    stableQueue.getOrNull(page)?.let { "queue-${it.mediaId}-$page" }
                        ?: "empty-$page"
                }
            },
        ) { page ->
            val queueItem = stableQueue.getOrNull(page)
            val artworkItem = if (page == pinnedCurrentArtworkPage) {
                currentTrack
            } else {
                queueItem
            }
            NowPlayingArtworkFrame(
                item = artworkItem,
                serversById = serversById,
                modifier = Modifier.fillMaxSize(),
                onClick = { latestOnArtworkClick() },
            )
        }
        programmaticArtworkTransition?.let { transition ->
            ProgrammaticArtworkTransitionOverlay(
                transition = transition,
                serversById = serversById,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(34.dp)),
                onRevealPager = {
                    if (programmaticArtworkTransition?.sequence == transition.sequence) {
                        programmaticArtworkTransitionCoversPager = false
                    }
                },
                onFinished = {
                    if (programmaticArtworkTransition?.sequence == transition.sequence) {
                        programmaticArtworkTransition = null
                        programmaticArtworkTransitionCoversPager = false
                    }
                },
            )
        }
    }
}

private data class ProgrammaticArtworkTransition(
    val sequence: Int,
    val from: PlaybackQueueItem?,
    val to: PlaybackQueueItem,
    val direction: Int,
)

@Composable
private fun ProgrammaticArtworkTransitionOverlay(
    transition: ProgrammaticArtworkTransition,
    serversById: Map<Long, ServerConfig>,
    modifier: Modifier = Modifier,
    onRevealPager: () -> Unit,
    onFinished: () -> Unit,
) {
    val latestOnRevealPager by rememberUpdatedState(onRevealPager)
    val latestOnFinished by rememberUpdatedState(onFinished)
    var started by remember(transition.sequence) { mutableStateOf(false) }
    var slideFinished by remember(transition.sequence) { mutableStateOf(false) }
    var fadeStarted by remember(transition.sequence) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = PROGRAMMATIC_ARTWORK_TRANSITION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "ProgrammaticArtworkTransitionProgress",
        finishedListener = { if (it == 1f) slideFinished = true },
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (fadeStarted) 0f else 1f,
        animationSpec = tween(
            durationMillis = PROGRAMMATIC_ARTWORK_FADE_OUT_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "ProgrammaticArtworkTransitionAlpha",
        finishedListener = { if (it == 0f) latestOnFinished() },
    )

    LaunchedEffect(transition.sequence) {
        started = true
    }
    LaunchedEffect(transition.sequence, slideFinished) {
        if (slideFinished) {
            withFrameNanos { }
            latestOnRevealPager()
            delay(PROGRAMMATIC_ARTWORK_REVEAL_HOLD_MS.toLong())
            fadeStarted = true
        }
    }

    BoxWithConstraints(
        modifier = modifier.graphicsLayer { alpha = overlayAlpha },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val cardTravelPx = with(density) {
            minOf(maxWidth.toPx(), maxHeight.toPx()) + PROGRAMMATIC_ARTWORK_PAGE_SPACING_DP.dp.toPx()
        }
        val direction = transition.direction.coerceIn(-1, 1)
        transition.from?.let { item ->
            NowPlayingArtworkFrame(
                item = item,
                serversById = serversById,
                modifier = Modifier.fillMaxSize(),
                contentModifier = Modifier.graphicsLayer {
                    scaleX = 1f - progress * 0.03f
                    scaleY = 1f - progress * 0.03f
                    translationX = -direction * progress * cardTravelPx
                },
            )
        }
        NowPlayingArtworkFrame(
            item = transition.to,
            serversById = serversById,
            modifier = Modifier.fillMaxSize(),
            contentModifier = Modifier.graphicsLayer {
                scaleX = 0.97f + progress * 0.03f
                scaleY = 0.97f + progress * 0.03f
                translationX = direction * (1f - progress) * cardTravelPx
            },
        )
    }
}

@Composable
private fun NowPlayingArtworkFrame(
    item: PlaybackQueueItem?,
    serversById: Map<Long, ServerConfig>,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val clickModifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
        ArtworkCard(
            model = item?.queueArtworkModel(item.serverId?.let { serversById[it] }),
            contentDescription = item?.title,
            modifier = contentModifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(34.dp))
                .then(clickModifier),
            cornerRadiusDp = 34,
        )
    }
}

private const val ARTWORK_PRESENTATION_CACHE_ENTRIES = 64
private const val ARTWORK_COLOR_TRANSITION_MS = 520
private const val PROGRAMMATIC_ARTWORK_TRANSITION_MS = 420
private const val PROGRAMMATIC_ARTWORK_REVEAL_HOLD_MS = 80
private const val PROGRAMMATIC_ARTWORK_FADE_OUT_MS = 120
private const val PROGRAMMATIC_ARTWORK_PAGE_SPACING_DP = 16
private val artworkPresentationCache = LruCache<String, ArtworkPresentation>(ARTWORK_PRESENTATION_CACHE_ENTRIES)

@Composable
private fun rememberArtworkPresentation(
    fallbackModel: Any?,
): ArtworkPresentation {
    val context = LocalContext.current.applicationContext
    var presentation by remember { mutableStateOf(ArtworkPresentation()) }
    LaunchedEffect(fallbackModel) {
        presentation = loadArtworkPresentation(context, fallbackModel)
    }
    return presentation
}

private suspend fun loadArtworkPresentation(
    context: android.content.Context,
    model: Any?,
): ArtworkPresentation {
    val key = model?.artworkPresentationCacheKey() ?: return ArtworkPresentation()
    artworkPresentationCache.get(key)?.let { return it }
    val presentation = decodeArtworkPresentation(context, model)
    if (presentation.hasColors) {
        artworkPresentationCache.put(key, presentation)
    }
    return presentation
}

private suspend fun prewarmArtworkPresentation(
    context: android.content.Context,
    model: Any?,
) {
    loadArtworkPresentation(context, model)
}

private fun Any.artworkPresentationCacheKey(): String {
    return when (this) {
        is File -> "file:$absolutePath"
        else -> "model:${this}"
    }
}

private suspend fun decodeArtworkPresentation(
    context: android.content.Context,
    model: Any,
): ArtworkPresentation = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(PALETTE_COVER_ART_SIZE_PX)
            .allowHardware(false)
            .build()
        val image = context.imageLoader.execute(request).image
            ?: return@withContext ArtworkPresentation()
        val bitmap = image.toBitmap()

        val palette = Palette.from(bitmap).clearFilters().generate()
        ArtworkPresentation(
            dominantColor = palette.getDominantColor(0).takeIf { it != 0 }?.let(::Color),
            accentColor = palette.getVibrantColor(0).takeIf { it != 0 }?.let(::Color),
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        ArtworkPresentation()
    }
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
