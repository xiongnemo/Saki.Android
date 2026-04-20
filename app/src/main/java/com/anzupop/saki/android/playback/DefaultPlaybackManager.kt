package com.anzupop.saki.android.playback

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.anzupop.saki.android.data.remote.NetworkType
import com.anzupop.saki.android.data.remote.NetworkTypeProvider
import com.anzupop.saki.android.di.MainDispatcher
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.PlaybackRuntimeInfo
import com.anzupop.saki.android.domain.model.PlaybackSessionState
import com.anzupop.saki.android.domain.model.RepeatModeSetting
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.domain.model.StreamQuality
import com.anzupop.saki.android.domain.repository.PlaybackManager
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import com.anzupop.saki.android.domain.repository.StreamCacheRepository
import com.anzupop.saki.android.domain.repository.SubsonicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
@UnstableApi
class DefaultPlaybackManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val streamCacheRepository: StreamCacheRepository,
    private val subsonicRepository: SubsonicRepository,
    private val networkTypeProvider: NetworkTypeProvider,
) : PlaybackManager {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val controllerMutex = Mutex()
    private val controllerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
        }
    }

    private var controller: MediaController? = null
    private val mutablePlaybackState = MutableStateFlow(PlaybackSessionState())

    override val playbackState: StateFlow<PlaybackSessionState> = mutablePlaybackState.asStateFlow()

    private fun effectiveQuality(): StreamQuality {
        val prefs = playbackState.value.preferences
        if (!prefs.adaptiveQualityEnabled) return prefs.streamQuality
        return when (networkTypeProvider.networkType.value) {
            NetworkType.WIFI -> prefs.wifiStreamQuality
            NetworkType.MOBILE -> prefs.mobileStreamQuality
        }
    }

    private fun resolveQualityForSong(serverId: Long, songId: String): StreamQuality {
        val preferred = effectiveQuality()
        val cachedKey = streamCacheRepository.findCachedQualityKey(serverId, songId, preferred)
        return if (cachedKey != null) StreamQuality.fromStorageKey(cachedKey) else preferred
    }

    private suspend fun rebuildUpcomingItems() {
        withController { activeController ->
            val current = activeController.currentMediaItemIndex
            if (current == C.INDEX_UNSET) return@withController
            val end = (current + 11).coerceAtMost(activeController.mediaItemCount)
            for (i in (current + 1) until end) {
                val item = activeController.getMediaItemAt(i)
                val request = item.toPlaybackRequestOrNull() ?: continue
                if (request.isCached) continue
                val quality = resolveQualityForSong(request.serverId, request.songId)
                if (quality.maxBitRate == request.maxBitRate) continue
                val rebuilt = request.copy(
                    qualityLabel = quality.label,
                    streamCacheKey = streamCacheRepository.buildCacheKey(request.serverId, request.songId, quality),
                    maxBitRate = quality.maxBitRate,
                    format = quality.format,
                ).let { updated ->
                    MediaItem.Builder()
                        .setMediaId(updated.songId)
                        .setMediaMetadata(updated.toMediaMetadata())
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setExtras(updated.toBundle())
                                .build(),
                        )
                        .build()
                }
                activeController.replaceMediaItem(i, rebuilt)
            }
        }
    }

    init {
        scope.launch {
            playbackPreferencesRepository.observePreferences().collect { preferences ->
                mutablePlaybackState.update { state ->
                    state.copy(preferences = preferences)
                }
            }
        }
        scope.launch {
            networkTypeProvider.networkType.collect { _ ->
                if (playbackState.value.preferences.adaptiveQualityEnabled) {
                    rebuildUpcomingItems()
                }
            }
        }
        scope.launch {
            runCatching { ensureControllerConnected() }
            while (isActive) {
                controller?.let(::syncState)
                delay(if (mutablePlaybackState.value.isPlaying) 500L else 1_000L)
            }
        }
    }

    override suspend fun playSong(
        serverId: Long,
        song: Song,
    ) {
        playQueue(
            serverId = serverId,
            songs = listOf(song),
            startIndex = 0,
        )
    }

    override suspend fun playCachedSong(song: CachedSong) {
        playCachedQueue(
            songs = listOf(song),
            startIndex = 0,
        )
    }

    override suspend fun playQueue(
        serverId: Long,
        songs: List<Song>,
        startIndex: Int,
    ) {
        require(songs.isNotEmpty()) { "Playback queue cannot be empty." }
        val mediaItems = songs.map { song ->
            val quality = resolveQualityForSong(serverId, song.id)
            song.toPlaybackRequestMediaItem(
                serverId = serverId,
                qualityLabel = quality.label,
                streamCacheKey = streamCacheRepository.buildCacheKey(serverId, song.id, quality),
                artworkUri = buildArtworkUri(serverId, song.coverArtId),
                maxBitRate = quality.maxBitRate,
                format = quality.format,
            )
        }

        withController { activeController ->
            val safeStartIndex = startIndex.coerceIn(mediaItems.indices)
            activeController.setMediaItems(mediaItems, safeStartIndex, C.TIME_UNSET)
            activeController.prepare()
            activeController.play()
            syncState(activeController)
        }
    }

    override suspend fun restoreQueue(
        serverId: Long,
        songs: List<Song>,
        startIndex: Int,
        positionMs: Long,
    ) {
        if (songs.isEmpty()) return
        val mediaItems = songs.map { song ->
            val quality = resolveQualityForSong(serverId, song.id)
            song.toPlaybackRequestMediaItem(
                serverId = serverId,
                qualityLabel = quality.label,
                streamCacheKey = streamCacheRepository.buildCacheKey(serverId, song.id, quality),
                artworkUri = buildArtworkUri(serverId, song.coverArtId),
                maxBitRate = quality.maxBitRate,
                format = quality.format,
            )
        }

        withController { activeController ->
            val safeStartIndex = startIndex.coerceIn(mediaItems.indices)
            activeController.setMediaItems(mediaItems, safeStartIndex, positionMs)
            activeController.prepare()
            syncState(activeController)
        }
    }

    override suspend fun playCachedQueue(
        songs: List<CachedSong>,
        startIndex: Int,
    ) {
        require(songs.isNotEmpty()) { "Playback queue cannot be empty." }
        val mediaItems = songs.map(CachedSong::toCachedMediaItem)

        withController { activeController ->
            val safeStartIndex = startIndex.coerceIn(mediaItems.indices)
            activeController.setMediaItems(mediaItems, safeStartIndex, C.TIME_UNSET)
            activeController.prepare()
            activeController.play()
            syncState(activeController)
        }
    }

    override suspend fun addToQueue(
        serverId: Long,
        songs: List<Song>,
    ) {
        if (songs.isEmpty()) return
        val mediaItems = songs.map { song ->
            val quality = resolveQualityForSong(serverId, song.id)
            song.toPlaybackRequestMediaItem(
                serverId = serverId,
                qualityLabel = quality.label,
                streamCacheKey = streamCacheRepository.buildCacheKey(serverId, song.id, quality),
                artworkUri = buildArtworkUri(serverId, song.coverArtId),
                maxBitRate = quality.maxBitRate,
                format = quality.format,
            )
        }

        withController { activeController ->
            activeController.addMediaItems(mediaItems)
            syncState(activeController)
        }
    }

    override suspend fun playNext(
        serverId: Long,
        song: Song,
    ) {
        val quality = resolveQualityForSong(serverId, song.id)
        val mediaItem = song.toPlaybackRequestMediaItem(
            serverId = serverId,
            qualityLabel = quality.label,
            streamCacheKey = streamCacheRepository.buildCacheKey(serverId, song.id, quality),
            artworkUri = buildArtworkUri(serverId, song.coverArtId),
            maxBitRate = quality.maxBitRate,
            format = quality.format,
        )

        withController { activeController ->
            val insertIndex = if (activeController.currentMediaItemIndex == C.INDEX_UNSET) {
                0
            } else {
                activeController.currentMediaItemIndex + 1
            }
            activeController.addMediaItem(insertIndex, mediaItem)
            syncState(activeController)
        }
    }

    override suspend fun pause() {
        withController { activeController ->
            activeController.pause()
            syncState(activeController)
        }
    }

    override suspend fun resume() {
        withController { activeController ->
            activeController.play()
            syncState(activeController)
        }
    }

    override suspend fun skipToNext() {
        withController { activeController ->
            val nextIndex = activeController.currentMediaItemIndex + 1
            if (nextIndex < activeController.mediaItemCount) {
                val nextItem = activeController.getMediaItemAt(nextIndex)
                val request = nextItem.toPlaybackRequestOrNull()
                if (request != null && !request.isCached) {
                    val quality = resolveQualityForSong(request.serverId, request.songId)
                    if (quality.maxBitRate != request.maxBitRate) {
                        val rebuilt = request.copy(
                            qualityLabel = quality.label,
                            streamCacheKey = streamCacheRepository.buildCacheKey(request.serverId, request.songId, quality),
                            maxBitRate = quality.maxBitRate,
                            format = quality.format,
                        ).let { updated ->
                            MediaItem.Builder()
                                .setMediaId(updated.songId)
                                .setMediaMetadata(updated.toMediaMetadata())
                                .setRequestMetadata(
                                    MediaItem.RequestMetadata.Builder()
                                        .setExtras(updated.toBundle())
                                        .build(),
                                )
                                .build()
                        }
                        activeController.replaceMediaItem(nextIndex, rebuilt)
                    }
                }
            }
            activeController.seekToNext()
            syncState(activeController)
        }
    }

    override suspend fun skipToPrevious() {
        withController { activeController ->
            activeController.seekToPrevious()
            syncState(activeController)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withController { activeController ->
            activeController.seekTo(positionMs.coerceAtLeast(0L))
            syncState(activeController)
        }
    }

    override suspend fun cycleRepeatMode() {
        withController { activeController ->
            activeController.repeatMode = when (activeController.repeatMode.toRepeatModeSetting().next()) {
                RepeatModeSetting.OFF -> Player.REPEAT_MODE_OFF
                RepeatModeSetting.ONE -> Player.REPEAT_MODE_ONE
                RepeatModeSetting.ALL -> Player.REPEAT_MODE_ALL
            }
            syncState(activeController)
        }
    }

    override suspend fun toggleShuffle() {
        withController { activeController ->
            activeController.shuffleModeEnabled = !activeController.shuffleModeEnabled
            syncState(activeController)
        }
    }

    override suspend fun skipToQueueItem(index: Int) {
        withController { activeController ->
            if (index !in 0 until activeController.mediaItemCount) return@withController
            activeController.seekToDefaultPosition(index)
            activeController.play()
            syncState(activeController)
        }
    }

    override suspend fun removeQueueItem(index: Int) {
        withController { activeController ->
            if (index !in 0 until activeController.mediaItemCount) return@withController
            activeController.removeMediaItem(index)
            syncState(activeController)
        }
    }

    private suspend fun buildArtworkUri(
        serverId: Long,
        coverArtId: String?,
    ): String? {
        if (coverArtId.isNullOrBlank()) return null
        return runCatching {
            subsonicRepository.buildCoverArtRequest(
                serverId = serverId,
                coverArtId = coverArtId,
                size = 720,
            ).candidates.firstOrNull()?.url
        }.getOrNull()
    }

    private suspend fun <T> withController(
        block: suspend (MediaController) -> T,
    ): T {
        val activeController = ensureControllerConnected()
        return withContext(mainDispatcher) {
            block(activeController)
        }
    }

    private suspend fun ensureControllerConnected(): MediaController {
        controller?.let { return it }

        return controllerMutex.withLock {
            controller?.let { return@withLock it }
            withContext(mainDispatcher) {
                val sessionToken = SessionToken(
                    appContext,
                    ComponentName(appContext, SakiPlaybackService::class.java),
                )
                val controllerFuture = MediaController.Builder(appContext, sessionToken)
                    .setApplicationLooper(Looper.getMainLooper())
                    .buildAsync()
                val connectedController = controllerFuture.await(appContext)
                connectedController.addListener(controllerListener)
                controller = connectedController
                syncState(connectedController)
                connectedController
            }
        }
    }

    private fun syncState(player: Player) {
        val queue = (0 until player.mediaItemCount)
            .map(player::getMediaItemAt)
            .mapNotNull(MediaItem::toQueueItemOrNull)
        val currentIndex = player.currentMediaItemIndex
            .takeUnless { it == C.INDEX_UNSET }
            ?: -1

        val currentRequest = player.currentMediaItem?.toPlaybackRequestOrNull()
        val streamCached = currentRequest != null && !currentRequest.isCached &&
            player.bufferedPosition.coerceKnownTime() >= (player.duration.coerceKnownTime() - 1000L) &&
            player.duration.coerceKnownTime() > 0L

        mutablePlaybackState.update { state ->
            state.copy(
                isConnected = true,
                isPlaying = player.isPlaying,
                currentItem = queue.getOrNull(currentIndex),
                currentIndex = currentIndex,
                queue = queue,
                positionMs = player.currentPosition.coerceKnownTime(),
                durationMs = player.duration.coerceKnownTime().takeIf { it > 0 }
                    ?: player.currentMediaItem?.metadataDurationMs()
                    ?: 0L,
                bufferedPositionMs = player.bufferedPosition.coerceKnownTime(),
                isStreamCached = streamCached,
                repeatMode = player.repeatMode.toRepeatModeSetting(),
                shuffleEnabled = player.shuffleModeEnabled,
                runtimeInfo = player.currentAudioRuntimeInfoOrNull(),
            )
        }
    }
}

private suspend fun ListenableFuture<MediaController>.await(
    appContext: Context,
): MediaController = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (exception: ExecutionException) {
                continuation.resumeWithException(exception.cause ?: exception)
            } catch (exception: CancellationException) {
                continuation.resumeWithException(exception)
            } catch (exception: Exception) {
                continuation.resumeWithException(exception)
            }
        },
        ContextCompat.getMainExecutor(appContext),
    )
    continuation.invokeOnCancellation {
        cancel(false)
    }
}

private fun Long.coerceKnownTime(): Long {
    return if (this == C.TIME_UNSET || this < 0L) 0L else this
}

private fun Int.toRepeatModeSetting(): RepeatModeSetting {
    return when (this) {
        Player.REPEAT_MODE_ONE -> RepeatModeSetting.ONE
        Player.REPEAT_MODE_ALL -> RepeatModeSetting.ALL
        else -> RepeatModeSetting.OFF
    }
}

@UnstableApi
private fun Player.currentAudioRuntimeInfoOrNull(): PlaybackRuntimeInfo? {
    val selectedAudioGroup = currentTracks.groups.firstOrNull { group ->
        group.type == C.TRACK_TYPE_AUDIO && group.isSelected
    } ?: return null

    val selectedFormat = (0 until selectedAudioGroup.length)
        .firstNotNullOfOrNull { index ->
            selectedAudioGroup.getTrackFormat(index).takeIf {
                selectedAudioGroup.isTrackSelected(index)
            }
        } ?: return null

    return PlaybackRuntimeInfo(
        sampleMimeType = selectedFormat.sampleMimeType,
        containerMimeType = selectedFormat.containerMimeType,
        codecs = selectedFormat.codecs,
        averageBitrate = selectedFormat.averageBitrate.takeIfPositive(),
        peakBitrate = selectedFormat.peakBitrate.takeIfPositive(),
        sampleRate = selectedFormat.sampleRate.takeIfPositive(),
        channelCount = selectedFormat.channelCount.takeIfPositive(),
        language = selectedFormat.language,
    )
}

private fun Int.takeIfPositive(): Int? = takeIf { it > 0 }
