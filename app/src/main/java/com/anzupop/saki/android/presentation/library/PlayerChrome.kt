package com.anzupop.saki.android.presentation.library

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.anzupop.saki.android.presentation.EndpointProbeInfo
import com.anzupop.saki.android.domain.model.PlaybackQueueItem
import com.anzupop.saki.android.domain.model.PlaybackSessionState
import com.anzupop.saki.android.domain.model.RepeatModeSetting
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.SongLyrics
import java.io.File
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingCapsule(
    track: PlaybackQueueItem?,
    isPlaying: Boolean,
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

    Card(
        onClick = onExpand,
        enabled = track != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = Pair(track?.queueArtworkModel(), track?.title),
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
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.let { listOfNotNull(it.artist, it.album).joinToString(" • ") }
                        ?: "Start playback from Browse",
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

@OptIn(ExperimentalFoundationApi::class)
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
    activeEndpointLabel: String? = null,
    activeEndpointId: Long? = null,
    endpointProbeResults: List<EndpointProbeInfo> = emptyList(),
    isProbing: Boolean = false,
    onReprobeEndpoints: () -> Unit = {},
    lyrics: SongLyrics? = null,
) {
    val artwork = rememberArtworkPresentation(
        fallbackModel = track.queueArtworkModel(),
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
    LaunchedEffect(track.songId, prevSongId, nextSongId) {
        val adjacentIndices = listOfNotNull(
            if (currentIdx > 0) currentIdx - 1 else null,
            if (currentIdx < queue.lastIndex) currentIdx + 1 else null,
        )
        for (i in adjacentIndices) {
            val model = queue[i].queueArtworkModel() ?: continue
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

        LaunchedEffect(playbackState.positionMs, track.songId) {
            sliderValue = playbackState.positionMs.toFloat()
        }

        // Artwork pager state synced with playback queue
        val artworkPagerState = rememberPagerState(
            initialPage = playbackState.currentIndex.coerceAtLeast(0),
            pageCount = { playbackState.queue.size.coerceAtLeast(1) },
        )
        // Sync pager when track changes externally (button skip, queue tap)
        LaunchedEffect(playbackState.currentIndex) {
            val target = playbackState.currentIndex.coerceAtLeast(0)
            if (artworkPagerState.currentPage != target) {
                artworkPagerState.animateScrollToPage(target)
            }
        }
        // When user swipes pager, trigger skip
        val currentPlaybackIndex by rememberUpdatedState(playbackState.currentIndex)
        val currentQueueSize by rememberUpdatedState(playbackState.queue.size)
        LaunchedEffect(artworkPagerState) {
            snapshotFlow { artworkPagerState.settledPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (page != currentPlaybackIndex && page in 0 until currentQueueSize) {
                        onSkipToQueueItem(page)
                    }
                }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .safeDrawingPadding()
                .imePadding(),
        ) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
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
            val verticalSpacing = if (shortScreen) 12.dp else 16.dp
            val artworkTargetSize = when {
                maxHeight < 620.dp -> 220.dp
                maxHeight < 700.dp -> 248.dp
                maxHeight < 820.dp -> 296.dp
                else -> 368.dp
            }
            val artworkSize = artworkTargetSize.coerceAtMost(maxWidth - (horizontalPadding * 2))
            val queueRevealSpacing = when {
                maxHeight < 700.dp -> maxHeight * 0.34f
                maxHeight < 820.dp -> maxHeight * 0.40f
                else -> maxHeight * 0.44f
            }
            val showQueueAffordance by remember(listState, playbackState.queue.size) {
                derivedStateOf {
                    playbackState.queue.size > 1 &&
                        listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset < 32
                }
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    state = listState,
                    contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Now Playing",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                            ) {
                                Text(
                                    text = when {
                                        track.isCached -> "Offline"
                                        playbackState.isStreamCached -> "Cached"
                                        else -> "Streaming"
                                    } + " • ${track.qualityLabel}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                    item {
                        val hasLyrics = lyrics != null && lyrics.lines.isNotEmpty()
                        Box(
                            modifier = Modifier
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
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(34.dp)),
                                pageSpacing = 16.dp,
                                beyondViewportPageCount = 1,
                            ) { page ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val queueItem = playbackState.queue.getOrNull(page)
                                    ArtworkCard(
                                        model = queueItem?.queueArtworkModel(),
                                        contentDescription = queueItem?.title,
                                        modifier = Modifier.size(artworkSize),
                                        cornerRadiusDp = 34,
                                    )
                                }
                            }
                            // Lyrics overlay on artwork
                            AnimatedVisibility(
                                visible = showLyrics && hasLyrics,
                                enter = fadeIn(tween(250)),
                                exit = fadeOut(tween(250)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(artworkSize)
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
                                            contentDescription = "Close lyrics",
                                            tint = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
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
                    }
                    item {
                        MetadataLinkRow(
                            track = track,
                            textStyle = metadataStyle,
                            canOpenArtist = canOpenArtist,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                    item {
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
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { onSeekTo(sliderValue.roundToLong()) },
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
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = formatDuration(sliderValue.roundToLong()), color = MaterialTheme.colorScheme.onBackground)
                            Text(text = formatDuration(playbackState.durationMs), color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerActionButton(Icons.Rounded.SkipPrevious, "Previous", onSkipToPrevious, compactControls)
                            Spacer(Modifier.width(14.dp))
                            Surface(
                                modifier = Modifier.size(
                                    width = if (compactControls) 132.dp else 148.dp,
                                    height = if (compactControls) 64.dp else 72.dp,
                                ),
                                shape = RoundedCornerShape(30.dp),
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
                                        text = if (playbackState.isPlaying) "Pause" else "Play",
                                        modifier = Modifier.padding(start = 10.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            PlayerActionButton(Icons.Rounded.SkipNext, "Next", onSkipToNext, compactControls)
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TogglePill(
                                label = when (playbackState.repeatMode) {
                                    RepeatModeSetting.OFF -> "Repeat off"
                                    RepeatModeSetting.ONE -> "Repeat one"
                                    RepeatModeSetting.ALL -> "Repeat all"
                                },
                                icon = if (playbackState.repeatMode == RepeatModeSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                active = playbackState.repeatMode != RepeatModeSetting.OFF,
                                onClick = onCycleRepeatMode,
                                compact = compactControls,
                            )
                            TogglePill(
                                label = if (playbackState.shuffleEnabled) "Shuffle on" else "Shuffle off",
                                icon = Icons.Rounded.Shuffle,
                                active = playbackState.shuffleEnabled,
                                onClick = onToggleShuffle,
                                compact = compactControls,
                            )
                            Box {
                                PlayerActionButton(Icons.Rounded.MoreVert, "More", { showMenu = true }, compactControls)
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Song details") },
                                        onClick = {
                                            showMenu = false
                                            showDetails = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(activeEndpointLabel ?: "No endpoint") },
                                        onClick = {
                                            showMenu = false
                                            showEndpointStatus = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (playbackState.queue.size > 1) {
                        item {
                            Spacer(modifier = Modifier.heightIn(min = queueRevealSpacing))
                        }
                        item {
                            Text(text = "Queue", style = MaterialTheme.typography.headlineSmall)
                        }
                        itemsIndexed(playbackState.queue) { index, item ->
                            QueueRow(
                                item = item,
                                isCurrent = index == playbackState.currentIndex,
                                onClick = { onSkipToQueueItem(index) },
                                onRemove = { onRemoveQueueItem(index) },
                            )
                        }
                    }
                }

                if (showQueueAffordance) {
                    QueuePullAffordance(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(9)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close")
                }
            },
            title = { Text(track.title) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { DetailLine("Artist", track.artist) }
                    item { DetailLine("Album", track.album) }
                    item { DetailLine("Quality", track.qualityLabel) }
                    item { DetailLine("Server", currentServer?.name) }
                    item { DetailLine("Mode", if (track.isCached) "Offline" else "Streaming") }
                    item { DetailLine("MIME type", playbackState.runtimeInfo?.sampleMimeType) }
                    item { DetailLine("Container", playbackState.runtimeInfo?.containerMimeType) }
                    item { DetailLine("Codec", playbackState.runtimeInfo?.codecs) }
                    item { DetailLine("Average bitrate", playbackState.runtimeInfo?.averageBitrate?.let(::formatBitrate)) }
                    item { DetailLine("Peak bitrate", playbackState.runtimeInfo?.peakBitrate?.let(::formatBitrate)) }
                    item { DetailLine("Sample rate", playbackState.runtimeInfo?.sampleRate?.let(::formatSampleRate)) }
                    item { DetailLine("Channels", playbackState.runtimeInfo?.channelCount?.toString()) }
                    item { DetailLine("Language", playbackState.runtimeInfo?.language) }
                    item { DetailLine("Cover art ID", track.coverArtId) }
                    item { DetailLine("Local file", track.localPath) }
                }
            },
        )
    }

    if (showEndpointStatus) {
        AlertDialog(
            onDismissRequest = { showEndpointStatus = false },
            confirmButton = {
                TextButton(onClick = { showEndpointStatus = false }) {
                    Text("Close")
                }
            },
            title = { Text("Endpoint Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (endpointProbeResults.isEmpty()) {
                        Text("No probe results yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        endpointProbeResults.forEach { result ->
                            val isActive = result.id == activeEndpointId
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
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
                                            text = result.label + if (isActive) " ✓" else "",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = result.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = if (result.reachable) "${result.latencyMs} ms" else "Unreachable",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (result.reachable) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error
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
                    ) {
                        Text(if (isProbing) "Probing…" else "Re-test endpoints")
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
private fun TogglePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        },
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (compact) 12.dp else 14.dp,
                    vertical = if (compact) 10.dp else 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
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
private fun BoxScope.QueuePullAffordance(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = "Show queue",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueRow(
    item: PlaybackQueueItem,
    isCurrent: Boolean,
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
                model = item.queueArtworkModel(),
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
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Text(
        text = "$label: ${value.orEmpty().ifBlank { "Unknown" }}",
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun PlaybackQueueItem.queueArtworkModel(): Any? {
    return when {
        !coverArtPath.isNullOrBlank() -> File(coverArtPath)
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