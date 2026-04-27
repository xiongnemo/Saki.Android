package org.hdhmc.saki.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.hdhmc.saki.data.remote.NetworkType
import org.hdhmc.saki.data.remote.NetworkTypeProvider
import org.hdhmc.saki.di.DefaultDispatcher
import org.hdhmc.saki.di.MainDispatcher
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.PlaybackRuntimeInfo
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.RepeatModeSetting
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.PlaybackManager
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.StreamCacheRepository
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
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
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val cachedSongRepository: CachedSongRepository,
    private val streamCacheRepository: StreamCacheRepository,
    private val networkTypeProvider: NetworkTypeProvider,
) : PlaybackManager {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val controllerMutex = Mutex()

    // Shuffle state: seed + anchor for deterministic rebuild, display order for UI
    private var shuffleSeed: Long = 0L
    private var shuffleAnchorIndex: Int = 0
    private var shuffleDisplayOrder: List<Int>? = null // player indices in shuffle display order
    private var restoringExternalShuffleState = false
    private var deferredQueueJob: Job? = null
    private var queueLoadGeneration: Long = 0L
    private var pendingDeferredShuffleEnabled: Boolean? = null
    private var pendingDeferredShuffleSeed: Long = 0L
    private var pendingDeferredShuffleAnchorIndex: Int = 0

    private val controllerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncExternalShuffleState(player)
            syncState(player)
        }
    }

    private var controller: MediaController? = null
    private val mutablePlaybackState = MutableStateFlow(PlaybackSessionState())
    @Volatile private var cacheReady = false

    override val playbackState: StateFlow<PlaybackSessionState> = mutablePlaybackState.asStateFlow()

    private fun effectiveQuality(): StreamQuality {
        val prefs = playbackState.value.preferences
        if (!prefs.adaptiveQualityEnabled) return prefs.streamQuality
        return when (networkTypeProvider.networkType.value) {
            NetworkType.WIFI -> prefs.wifiStreamQuality
            NetworkType.MOBILE -> prefs.mobileStreamQuality
        }
    }

    private fun resolveQualityForSong(
        serverId: Long,
        songId: String,
        preferred: StreamQuality = effectiveQuality(),
    ): StreamQuality {
        val cachedKey = streamCacheRepository.findCachedQualityKey(serverId, songId, preferred)
        return if (cachedKey != null) StreamQuality.fromStorageKey(cachedKey) else preferred
    }

    private fun Song.toPreferredMediaItem(
        serverId: Long,
        preferredQuality: StreamQuality,
        cachedSong: CachedSong?,
    ): MediaItem {
        if (cachedSong != null) {
            return cachedSong.toCachedMediaItem()
        }

        val quality = resolveQualityForSong(serverId, id, preferredQuality)
        return toPlaybackRequestMediaItem(
            serverId = serverId,
            qualityLabel = quality.label,
            streamCacheKey = streamCacheRepository.buildCacheKey(serverId, id, quality),
            artworkUri = null,
            maxBitRate = quality.maxBitRate,
            format = quality.format,
        )
    }

    private suspend fun PlaybackRequest.toPreferredMediaItemOrNull(): MediaItem? {
        val preferredQuality = effectiveQuality()
        val cachedSong = cachedSongRepository.getPlayableCachedSong(serverId, songId, preferredQuality)
        if (cachedSong != null) {
            return cachedSong.toCachedMediaItem()
        }

        val quality = resolveQualityForSong(serverId, songId, preferredQuality)
        if (quality.maxBitRate == maxBitRate && quality.format == format) {
            return null
        }

        return copy(
            qualityLabel = quality.label,
            streamCacheKey = streamCacheRepository.buildCacheKey(serverId, songId, quality),
            maxBitRate = quality.maxBitRate,
            format = quality.format,
        ).toLogicalMediaItem()
    }

    init {
        scope.launch {
            streamCacheRepository.observeCacheVersion().collect { if (it > 0L) cacheReady = true }
        }
        scope.launch {
            playbackPreferencesRepository.observePreferences().collect { preferences ->
                mutablePlaybackState.update { state ->
                    state.copy(preferences = preferences)
                }
            }
        }
        scope.launch {
            runCatching { ensureControllerConnected() }
            while (isActive) {
                controller?.let { activeController ->
                    syncExternalShuffleState(activeController)
                    syncState(activeController)
                }
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
        val safeStartIndex = startIndex.coerceIn(songs.indices)
        val startSong = songs[safeStartIndex]
        val preferredQuality = effectiveQuality()
        val startCachedSong = cachedSongRepository.getPlayableCachedSong(serverId, startSong.id, preferredQuality)
        val startMediaItem = startSong.toPreferredMediaItem(
            serverId = serverId,
            preferredQuality = preferredQuality,
            cachedSong = startCachedSong,
        )

        withController { activeController ->
            val generation = beginDeferredQueueLoad()
            val shouldRestoreShuffle = shuffleDisplayOrder != null || activeController.shuffleModeEnabled
            val deferredShuffleSeed = if (shouldRestoreShuffle) System.nanoTime() else 0L
            pendingDeferredShuffleEnabled = if (songs.size > 1) shouldRestoreShuffle else null
            pendingDeferredShuffleSeed = deferredShuffleSeed
            pendingDeferredShuffleAnchorIndex = safeStartIndex
            shuffleDisplayOrder = null
            activeController.shuffleModeEnabled = false
            if (shouldRestoreShuffle && songs.size > 1) {
                shuffleSeed = deferredShuffleSeed
                shuffleAnchorIndex = safeStartIndex
                persistShuffleState(shuffleSeed, shuffleAnchorIndex)
            } else {
                persistShuffleState()
            }
            activeController.setMediaItem(startMediaItem)
            activeController.prepare()
            activeController.play()
            syncState(activeController)
            if (songs.size > 1) {
                scheduleDeferredQueueLoad(
                    generation = generation,
                    serverId = serverId,
                    songs = songs.toList(),
                    startIndex = safeStartIndex,
                    preferredQuality = preferredQuality,
                    restoreShuffle = shouldRestoreShuffle,
                    shuffleSeedForQueue = deferredShuffleSeed,
                )
            }
        }
    }

    private fun beginDeferredQueueLoad(): Long {
        deferredQueueJob?.cancel()
        deferredQueueJob = null
        pendingDeferredShuffleEnabled = null
        pendingDeferredShuffleSeed = 0L
        pendingDeferredShuffleAnchorIndex = 0
        queueLoadGeneration += 1
        return queueLoadGeneration
    }

    private fun cancelDeferredQueueLoad(clearPendingShuffleState: Boolean = false) {
        val shouldClearShuffleState = clearPendingShuffleState && pendingDeferredShuffleEnabled != null
        beginDeferredQueueLoad()
        if (shouldClearShuffleState) {
            persistShuffleState()
        }
    }

    private fun scheduleDeferredQueueLoad(
        generation: Long,
        serverId: Long,
        songs: List<Song>,
        startIndex: Int,
        preferredQuality: StreamQuality,
        restoreShuffle: Boolean,
        shuffleSeedForQueue: Long,
    ) {
        val currentSongId = songs[startIndex].id
        deferredQueueJob = scope.launch {
            try {
                val deferredItems = buildDeferredQueueItems(
                    serverId = serverId,
                    songs = songs,
                    startIndex = startIndex,
                    preferredQuality = preferredQuality,
                )
                withController { activeController ->
                    if (generation != queueLoadGeneration) return@withController
                    if (activeController.mediaItemCount != 1) return@withController
                    if (activeController.currentMediaItem?.toPlaybackRequestOrNull()?.songId != currentSongId) {
                        return@withController
                    }

                    if (deferredItems.before.isNotEmpty()) {
                        activeController.addMediaItems(0, deferredItems.before)
                    }
                    if (deferredItems.after.isNotEmpty()) {
                        activeController.addMediaItems(deferredItems.after)
                    }
                    if (activeController.currentMediaItem?.toPlaybackRequestOrNull()?.songId != currentSongId) {
                        return@withController
                    }

                    val fullQueueSize = activeController.mediaItemCount
                    val currentIndex = activeController.currentMediaItemIndex
                    val enableShuffle = pendingDeferredShuffleEnabled ?: restoreShuffle
                    val pendingShuffleSeed = pendingDeferredShuffleSeed
                    pendingDeferredShuffleEnabled = null
                    pendingDeferredShuffleSeed = 0L
                    pendingDeferredShuffleAnchorIndex = 0
                    if (enableShuffle && fullQueueSize > 1 && currentIndex in 0 until fullQueueSize) {
                        shuffleSeed = pendingShuffleSeed.takeUnless { it == 0L }
                            ?: shuffleSeedForQueue.takeUnless { it == 0L }
                            ?: System.nanoTime()
                        shuffleAnchorIndex = currentIndex
                        sendShuffleOrderToService(activeController, fullQueueSize, shuffleSeed, shuffleAnchorIndex)
                        activeController.shuffleModeEnabled = true
                        shuffleDisplayOrder = SakiShuffleOrder(fullQueueSize, shuffleSeed, shuffleAnchorIndex).toDisplayOrder()
                    } else {
                        activeController.shuffleModeEnabled = false
                        shuffleDisplayOrder = null
                    }
                    persistShuffleState()
                    syncState(activeController)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Keep the already-started track playing if deferred queue expansion fails.
            } finally {
                // Only the active deferred load may clear pending state. Cancelled or stale loads
                // have a newer generation, and must leave the newer playback request alone.
                if (generation == queueLoadGeneration) {
                    val hadPendingShuffle = pendingDeferredShuffleEnabled != null
                    deferredQueueJob = null
                    pendingDeferredShuffleEnabled = null
                    pendingDeferredShuffleSeed = 0L
                    pendingDeferredShuffleAnchorIndex = 0
                    if (hadPendingShuffle) {
                        persistShuffleState()
                    }
                }
            }
        }
    }

    private suspend fun buildDeferredQueueItems(
        serverId: Long,
        songs: List<Song>,
        startIndex: Int,
        preferredQuality: StreamQuality,
    ): DeferredQueueItems = withContext(defaultDispatcher) {
        val cachedSongsById = cachedSongRepository.getPlayableCachedSongs(serverId, preferredQuality)
        val before = ArrayList<MediaItem>(startIndex)
        for (index in 0 until startIndex) {
            currentCoroutineContext().ensureActive()
            val song = songs[index]
            before += song.toPreferredMediaItem(
                serverId = serverId,
                preferredQuality = preferredQuality,
                cachedSong = cachedSongsById[song.id],
            )
        }

        val after = ArrayList<MediaItem>((songs.lastIndex - startIndex).coerceAtLeast(0))
        for (index in (startIndex + 1)..songs.lastIndex) {
            currentCoroutineContext().ensureActive()
            val song = songs[index]
            after += song.toPreferredMediaItem(
                serverId = serverId,
                preferredQuality = preferredQuality,
                cachedSong = cachedSongsById[song.id],
            )
        }
        DeferredQueueItems(before = before, after = after)
    }

    private data class DeferredQueueItems(
        val before: List<MediaItem>,
        val after: List<MediaItem>,
    )

    override suspend fun restoreQueue(
        serverId: Long,
        songs: List<Song>,
        startIndex: Int,
        positionMs: Long,
    ) {
        if (songs.isEmpty()) return
        cancelDeferredQueueLoad()
        shuffleDisplayOrder = null
        val preferredQuality = effectiveQuality()
        val cachedSongsById = cachedSongRepository.getPlayableCachedSongs(serverId, preferredQuality)
        val mediaItems = songs.map { song ->
            song.toPreferredMediaItem(
                serverId = serverId,
                preferredQuality = preferredQuality,
                cachedSong = cachedSongsById[song.id],
            )
        }

        withController { activeController ->
            val safeStartIndex = startIndex.coerceIn(mediaItems.indices)
            activeController.setMediaItems(mediaItems, safeStartIndex, positionMs)
            activeController.prepare()

            // Restore shuffle state if previously saved
            val saved = playbackPreferencesRepository.getShuffleState()
            if (saved != null && mediaItems.size > 1) {
                shuffleSeed = saved.first
                shuffleAnchorIndex = saved.second.coerceIn(0, mediaItems.size - 1)
                sendShuffleOrderToService(activeController, mediaItems.size, shuffleSeed, shuffleAnchorIndex)
                activeController.shuffleModeEnabled = true
                shuffleDisplayOrder = SakiShuffleOrder(mediaItems.size, shuffleSeed, shuffleAnchorIndex).toDisplayOrder()
            } else {
                activeController.shuffleModeEnabled = false
            }

            syncState(activeController)
        }
    }

    override suspend fun playCachedQueue(
        songs: List<CachedSong>,
        startIndex: Int,
    ) {
        require(songs.isNotEmpty()) { "Playback queue cannot be empty." }
        cancelDeferredQueueLoad()
        shuffleDisplayOrder = null
        persistShuffleState()
        val mediaItems = songs.map(CachedSong::toCachedMediaItem)

        withController { activeController ->
            val safeStartIndex = startIndex.coerceIn(mediaItems.indices)
            activeController.shuffleModeEnabled = false
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
        cancelDeferredQueueLoad(clearPendingShuffleState = true)
        clearShuffleIfActive()
        val preferredQuality = effectiveQuality()
        val cachedSongsById = cachedSongRepository.getPlayableCachedSongs(serverId, preferredQuality)
        val mediaItems = songs.map { song ->
            song.toPreferredMediaItem(
                serverId = serverId,
                preferredQuality = preferredQuality,
                cachedSong = cachedSongsById[song.id],
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
        cancelDeferredQueueLoad(clearPendingShuffleState = true)
        clearShuffleIfActive()
        val preferredQuality = effectiveQuality()
        val cachedSong = cachedSongRepository.getPlayableCachedSong(serverId, song.id, preferredQuality)
        val mediaItem = song.toPreferredMediaItem(
            serverId = serverId,
            preferredQuality = preferredQuality,
            cachedSong = cachedSong,
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
            // With native ShuffleOrder, seekToNext respects shuffle mode
            val nextIndex = activeController.currentMediaItemIndex + 1
            if (!activeController.shuffleModeEnabled && nextIndex < activeController.mediaItemCount) {
                val nextItem = activeController.getMediaItemAt(nextIndex)
                val request = nextItem.toPlaybackRequestOrNull()
                if (request != null && !request.isCached) {
                    request.toPreferredMediaItemOrNull()?.let { rebuilt ->
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

    private fun persistShuffleState() {
        scope.launch {
            if (shuffleDisplayOrder != null) {
                playbackPreferencesRepository.updateShuffleState(shuffleSeed, shuffleAnchorIndex)
            } else {
                playbackPreferencesRepository.clearShuffleState()
            }
        }
    }

    private fun persistShuffleState(seed: Long, anchorIndex: Int) {
        scope.launch {
            playbackPreferencesRepository.updateShuffleState(seed, anchorIndex)
        }
    }

    private suspend fun clearShuffleIfActive() {
        if (shuffleDisplayOrder == null) return
        shuffleDisplayOrder = null
        persistShuffleState()
        withController { it.shuffleModeEnabled = false }
    }

    private fun sendShuffleOrderToService(controller: MediaController, count: Int, seed: Long, anchor: Int) {
        val args = Bundle().apply {
            putLong(SakiPlaybackService.EXTRA_SHUFFLE_SEED, seed)
            putInt(SakiPlaybackService.EXTRA_SHUFFLE_ANCHOR, anchor)
            putInt(SakiPlaybackService.EXTRA_SHUFFLE_COUNT, count)
        }
        controller.sendCustomCommand(
            SessionCommand(SakiPlaybackService.ACTION_SET_SHUFFLE_ORDER, Bundle.EMPTY),
            args,
        )
    }

    /** Map display index (in shuffle order) to player index. */
    private fun displayToPlayer(displayIndex: Int): Int =
        shuffleDisplayOrder?.getOrNull(displayIndex) ?: displayIndex

    /** Map player index to display index (in shuffle order). */
    private fun playerToDisplay(playerIndex: Int): Int =
        shuffleDisplayOrder?.indexOf(playerIndex)?.takeIf { it >= 0 } ?: playerIndex

    override suspend fun toggleShuffle() {
        withController { activeController ->
            val count = activeController.mediaItemCount
            pendingDeferredShuffleEnabled?.let { pendingShuffle ->
                // While the deferred queue is still a single-item placeholder, record the user's
                // desired shuffle state and apply it after the full queue has been inserted.
                // If expansion has already inserted items, fall through to the normal path below.
                if (deferredQueueJob != null && count <= 1) {
                    val enableShuffle = !pendingShuffle
                    pendingDeferredShuffleEnabled = enableShuffle
                    if (enableShuffle) {
                        val seed = pendingDeferredShuffleSeed.takeUnless { it == 0L } ?: System.nanoTime()
                        pendingDeferredShuffleSeed = seed
                        shuffleSeed = seed
                        shuffleAnchorIndex = pendingDeferredShuffleAnchorIndex
                        persistShuffleState(seed, pendingDeferredShuffleAnchorIndex)
                    } else {
                        persistShuffleState()
                    }
                    syncState(activeController)
                    return@withController
                }
            }
            if (count <= 1) return@withController

            if (shuffleDisplayOrder == null) {
                // Shuffle ON
                shuffleSeed = System.nanoTime()
                shuffleAnchorIndex = activeController.currentMediaItemIndex
                sendShuffleOrderToService(activeController, count, shuffleSeed, shuffleAnchorIndex)
                activeController.shuffleModeEnabled = true
                shuffleDisplayOrder = SakiShuffleOrder(count, shuffleSeed, shuffleAnchorIndex).toDisplayOrder()
            } else {
                // Shuffle OFF
                shuffleDisplayOrder = null
                activeController.shuffleModeEnabled = false
            }

            persistShuffleState()
            syncState(activeController)
        }
    }

    override suspend fun skipToQueueItem(index: Int) {
        withController { activeController ->
            val playerIndex = displayToPlayer(index)
            if (playerIndex !in 0 until activeController.mediaItemCount) return@withController
            activeController.seekToDefaultPosition(playerIndex)
            activeController.play()
            syncState(activeController)
        }
    }

    override suspend fun removeQueueItem(index: Int) {
        cancelDeferredQueueLoad(clearPendingShuffleState = true)
        withController { activeController ->
            val playerIndex = displayToPlayer(index)
            if (playerIndex !in 0 until activeController.mediaItemCount) return@withController
            activeController.removeMediaItem(playerIndex)
            if (shuffleDisplayOrder != null) {
                val newCount = activeController.mediaItemCount
                if (newCount <= 1) {
                    shuffleDisplayOrder = null
                    activeController.shuffleModeEnabled = false
                    persistShuffleState()
                } else {
                    // Mirror ExoPlayer's cloneAndRemove: filter out removed index, shift down
                    shuffleDisplayOrder = shuffleDisplayOrder!!
                        .filter { it != playerIndex }
                        .map { if (it > playerIndex) it - 1 else it }
                    if (shuffleAnchorIndex == playerIndex) {
                        shuffleAnchorIndex = 0
                    } else if (shuffleAnchorIndex > playerIndex) {
                        shuffleAnchorIndex--
                    }
                }
            }
            syncState(activeController)
        }
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
        val playerIndex = player.currentMediaItemIndex
            .takeUnless { it == C.INDEX_UNSET }
            ?: -1

        // Expose queue in shuffled order if shuffle is active
        val displayOrder = shuffleDisplayOrder
        val (displayQueue, displayIndex) = if (displayOrder != null && queue.isNotEmpty()) {
            val shuffled = displayOrder.mapNotNull { queue.getOrNull(it) }
            shuffled to playerToDisplay(playerIndex)
        } else {
            queue to playerIndex
        }

        val currentRequest = player.currentMediaItem?.toPlaybackRequestOrNull()
        val cachedQualityKey = if (currentRequest != null && !currentRequest.isCached && cacheReady) {
            streamCacheRepository.findCachedQualityKey(
                currentRequest.serverId, currentRequest.songId, effectiveQuality(),
            )
        } else null
        val streamCached = cachedQualityKey != null

        mutablePlaybackState.update { state ->
            val currentDisplayItem = displayQueue.getOrNull(displayIndex)?.let { item ->
                if (item.isCached) return@let item
                if (state.preferences.adaptiveQualityEnabled) {
                    val label = if (streamCached) {
                        org.hdhmc.saki.domain.model.StreamQuality.fromStorageKey(cachedQualityKey!!).label
                    } else {
                        effectiveQuality().label
                    }
                    item.copy(qualityLabel = label)
                } else {
                    item
                }
            }
            state.copy(
                isConnected = true,
                isPlaying = player.isPlaying,
                currentItem = currentDisplayItem,
                currentIndex = displayIndex,
                queue = displayQueue,
                positionMs = player.currentPosition.coerceKnownTime(),
                durationMs = player.duration.coerceKnownTime().takeIf { it > 0 }
                    ?: player.currentMediaItem?.metadataDurationMs()
                    ?: 0L,
                bufferedPositionMs = player.bufferedPosition.coerceKnownTime(),
                isStreamCached = streamCached,
                repeatMode = player.repeatMode.toRepeatModeSetting(),
                shuffleEnabled = pendingDeferredShuffleEnabled ?: (shuffleDisplayOrder != null || player.shuffleModeEnabled),
                runtimeInfo = player.currentAudioRuntimeInfoOrNull(),
            )
        }
    }

    private fun syncExternalShuffleState(player: Player) {
        if (!player.shuffleModeEnabled) {
            restoringExternalShuffleState = false
            if (shuffleDisplayOrder != null) {
                shuffleDisplayOrder = null
                scope.launch { playbackPreferencesRepository.clearShuffleState() }
            }
            return
        }
        if (shuffleDisplayOrder != null || player.mediaItemCount <= 1 || restoringExternalShuffleState) return

        restoringExternalShuffleState = true
        scope.launch {
            try {
                val saved = readExternalShuffleState()
                val count = player.mediaItemCount
                if (!player.shuffleModeEnabled || count <= 1 || shuffleDisplayOrder != null) return@launch
                if (saved == null) return@launch
                shuffleSeed = saved.first
                shuffleAnchorIndex = saved.second.coerceIn(0, count - 1)
                shuffleDisplayOrder = SakiShuffleOrder(count, shuffleSeed, shuffleAnchorIndex).toDisplayOrder()
                syncState(player)
            } finally {
                restoringExternalShuffleState = false
            }
        }
    }

    private suspend fun readExternalShuffleState(): Pair<Long, Int>? {
        repeat(5) { attempt ->
            playbackPreferencesRepository.getShuffleState()?.let { return it }
            delay(100L * (attempt + 1))
        }
        return playbackPreferencesRepository.getShuffleState()
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

private fun PlaybackRequest.toLogicalMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(songId)
        .setMediaMetadata(toMediaMetadata())
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setExtras(toBundle())
                .build(),
        )
        .build()
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
