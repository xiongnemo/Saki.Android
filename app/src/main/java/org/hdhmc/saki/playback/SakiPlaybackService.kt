package org.hdhmc.saki.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import org.hdhmc.saki.MainActivity
import org.hdhmc.saki.R
import org.hdhmc.saki.data.remote.HTTP_USER_AGENT
import org.hdhmc.saki.domain.model.LyricLine
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshotSource
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshotSourceType
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.LocalPlayQueueRepository
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@AndroidEntryPoint
@UnstableApi
class SakiPlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_SET_SHUFFLE_ORDER = "saki.action.SET_SHUFFLE_ORDER"
        const val ACTION_TOGGLE_REPEAT = "saki.action.TOGGLE_REPEAT"
        const val ACTION_TOGGLE_SHUFFLE = "saki.action.TOGGLE_SHUFFLE"
        const val EXTRA_SHUFFLE_SEED = "saki.extra.SHUFFLE_SEED"
        const val EXTRA_SHUFFLE_ANCHOR = "saki.extra.SHUFFLE_ANCHOR"
        const val EXTRA_SHUFFLE_COUNT = "saki.extra.SHUFFLE_COUNT"
    }

    private var pendingShuffleOrder: Triple<Long, Int, Int>? = null // seed, anchor, count
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var subsonicRepository: SubsonicRepository

    @Inject
    lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    @Inject
    lateinit var localPlayQueueRepository: LocalPlayQueueRepository

    @Inject
    lateinit var streamCache: SimpleCache

    @Inject
    lateinit var lyricsHolder: LyricsHolder

    @Inject
    lateinit var networkTypeProvider: org.hdhmc.saki.data.remote.NetworkTypeProvider

    @Inject
    lateinit var streamCacheRepository: org.hdhmc.saki.domain.repository.StreamCacheRepository

    @Inject
    lateinit var endpointSelector: org.hdhmc.saki.data.remote.EndpointSelector

    private val serviceScope = CoroutineScope(SupervisorJob())
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var originalMediaTitle: CharSequence? = null
    private var originalMediaId: String? = null
    private var soundBalancingMode = SoundBalancingMode.OFF
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val upstreamDataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(HTTP_USER_AGENT),
        )
        val cacheFactory = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            resolveStreamDataSpec(dataSpec)
        }

        val initialPrefs = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            playbackPreferencesRepository.getPreferences()
        }
        cachedPlaybackPrefs = initialPrefs

        val maxBufferMs = when (initialPrefs.bufferStrategy) {
            org.hdhmc.saki.domain.model.BufferStrategy.AGGRESSIVE -> Int.MAX_VALUE
            org.hdhmc.saki.domain.model.BufferStrategy.CUSTOM ->
                initialPrefs.customBufferSeconds * 1_000
            else -> DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minOf(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, maxBufferMs),
                maxBufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                preloadConfiguration =
                    ExoPlayer.PreloadConfiguration(10 * C.MICROS_PER_SECOND)
                addListener(PlaybackRecoveryListener())
                addListener(PlayQueueSaveListener())
                addListener(NotificationMediaButtonListener())
                addListener(object : Player.Listener {
                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        val pending = pendingShuffleOrder ?: return
                        val (seed, anchor, count) = pending
                        if (mediaItemCount == count) {
                            setShuffleOrder(SakiShuffleOrder(count, seed, anchor))
                            pendingShuffleOrder = null
                        }
                    }
                })
            }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(SakiMediaSessionCallback())
            .setBitmapLoader(CoilBitmapLoader(this))
            .build()
        syncMediaButtonPreferences()

        playerScope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.soundBalancingMode }
                .distinctUntilChanged()
                .collect { mode ->
                    soundBalancingMode = mode
                    val audioSessionId = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
                    syncSoundBalancingEffect(audioSessionId)
                }
        }

        // Keep playback prefs in memory for the non-suspend ResolvingDataSource resolver
        playerScope.launch {
            playbackPreferencesRepository.observePreferences().collect { cachedPlaybackPrefs = it }
        }

        playerScope.launch {
            combine(
                playbackPreferencesRepository.observePreferences()
                    .map { it.bluetoothLyricsEnabled }
                    .distinctUntilChanged(),
                lyricsHolder.lyrics,
            ) { enabled, lyrics -> if (enabled) lyrics else null }
                .collectLatest { lyrics ->
                    if (lyrics == null || !lyrics.synced) {
                        restoreOriginalTitle()
                        return@collectLatest
                    }
                    var lastLyricText: String? = null
                    try {
                        while (true) {
                            val activePlayer = player ?: break
                            if (!activePlayer.isPlaying) {
                                delay(500)
                                continue
                            }
                            mediaSession ?: break
                            val positionMs = activePlayer.currentPosition
                            val lines = lyrics.lines
                            val index = lines.binarySearchLastBefore(positionMs)
                            val text = if (index >= 0) lines[index].text.takeIf { it.isNotBlank() } else null
                            if (text != null && text != lastLyricText) {
                                lastLyricText = text
                                val item = activePlayer.currentMediaItem ?: break
                                if (originalMediaTitle == null) {
                                    originalMediaTitle = item.mediaMetadata.title
                                    originalMediaId = item.mediaId
                                }
                                val updated = item.buildUpon()
                                    .setMediaMetadata(
                                        item.mediaMetadata.buildUpon()
                                            .setTitle(text)
                                            .build(),
                                    )
                                    .build()
                                activePlayer.replaceMediaItem(activePlayer.currentMediaItemIndex, updated)
                            }
                            delay(500)
                        }
                    } finally {
                        restoreOriginalTitle()
                    }
                }
        }
    }

    private fun syncMediaButtonPreferences() {
        val activePlayer = player ?: return
        mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences(activePlayer))
    }

    private fun buildMediaButtonPreferences(activePlayer: Player): List<CommandButton> {
        val hasQueue = activePlayer.mediaItemCount > 0
        val repeatName = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> getString(R.string.player_repeat_one)
            Player.REPEAT_MODE_ALL -> getString(R.string.player_repeat_all)
            else -> getString(R.string.player_repeat_off)
        }
        val repeatIconRes = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notification_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notification_repeat_on
            else -> R.drawable.ic_notification_repeat
        }
        val shuffleName = getString(
            if (activePlayer.shuffleModeEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off,
        )
        val shuffleIconRes = if (activePlayer.shuffleModeEnabled) {
            R.drawable.ic_notification_shuffle_on
        } else {
            R.drawable.ic_notification_shuffle
        }

        return listOf(
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(repeatName)
                .setCustomIconResId(repeatIconRes)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(hasQueue)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(shuffleName)
                .setCustomIconResId(shuffleIconRes)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(activePlayer.mediaItemCount > 1)
                .build(),
        )
    }

    private fun cycleNotificationRepeatMode() {
        val activePlayer = player ?: return
        activePlayer.repeatMode = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncMediaButtonPreferences()
    }

    private fun toggleNotificationShuffle(): ListenableFuture<SessionResult> {
        val future = SettableFuture.create<SessionResult>()
        val activePlayer = player as? ExoPlayer
        if (activePlayer == null) {
            future.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            return future
        }
        playerScope.launch {
            runCatching {
                val count = activePlayer.mediaItemCount
                if (count <= 1) {
                    playbackPreferencesRepository.clearShuffleState()
                    activePlayer.shuffleModeEnabled = false
                    pendingShuffleOrder = null
                    syncMediaButtonPreferences()
                    return@runCatching
                }

                if (activePlayer.shuffleModeEnabled) {
                    playbackPreferencesRepository.clearShuffleState()
                    activePlayer.shuffleModeEnabled = false
                    pendingShuffleOrder = null
                } else {
                    val seed = System.nanoTime()
                    val anchor = activePlayer.currentMediaItemIndex.coerceIn(0, count - 1)
                    playbackPreferencesRepository.updateShuffleState(seed, anchor)
                    activePlayer.setShuffleOrder(SakiShuffleOrder(count, seed, anchor))
                    activePlayer.shuffleModeEnabled = true
                    pendingShuffleOrder = null
                }
                syncMediaButtonPreferences()
            }.onSuccess {
                future.set(SessionResult(SessionResult.RESULT_SUCCESS))
            }.onFailure { throwable ->
                future.setException(throwable)
            }
        }
        return future
    }

    /** Latest playback preferences, kept in memory to avoid blocking reads in the resolver. */
    @Volatile
    private var cachedPlaybackPrefs: org.hdhmc.saki.domain.model.PlaybackPreferences? = null

    /**
     * Resolves placeholder `saki://stream` URIs to real Subsonic stream URLs at the moment
     * ExoPlayer actually opens the data source. This ensures the quality and endpoint are
     * determined by the current network state, not the queue build time.
     */
    private fun resolveStreamDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != "saki" || uri.host != "stream") return dataSpec

        val serverId = uri.getQueryParameter("serverId")?.toLongOrNull()
            ?: throw IOException("Missing serverId in stream placeholder URI")
        val songId = uri.getQueryParameter("songId")
            ?: throw IOException("Missing songId in stream placeholder URI")

        // Use cached prefs to avoid blocking; fall back to blocking read if not yet available
        val prefs = cachedPlaybackPrefs ?: runBlocking { playbackPreferencesRepository.getPreferences() }
        val requestedQuality = if (prefs.adaptiveQualityEnabled) {
            when (networkTypeProvider.networkType.value) {
                org.hdhmc.saki.data.remote.NetworkType.WIFI -> prefs.wifiStreamQuality
                org.hdhmc.saki.data.remote.NetworkType.MOBILE -> prefs.mobileStreamQuality
            }
        } else {
            val maxBitRate = uri.getQueryParameter("maxBitRate")?.toIntOrNull()
            StreamQuality.entries.find { it.maxBitRate == maxBitRate } ?: StreamQuality.ORIGINAL
        }
        val preferLocalCache = shouldPreferLocalStreamCache(serverId)
        val cacheLookupQuality = if (preferLocalCache) StreamQuality.entries.last() else requestedQuality
        val cachedQualityKey = streamCacheRepository.findCachedQualityKey(serverId, songId, cacheLookupQuality)
        val cachedQuality = cachedQualityKey?.let { key -> StreamQuality.fromStorageKey(key) }
        val cachedResourceKey = cachedQuality?.let { quality ->
            streamCacheRepository.buildCacheKey(serverId, songId, quality)
        }
        if (preferLocalCache && cachedResourceKey != null) {
            return dataSpec.buildUpon()
                .setUri(cachedStreamUri(cachedResourceKey))
                .setKey(cachedResourceKey)
                .build()
        }

        val quality = cachedQuality ?: requestedQuality
        val format = uri.getQueryParameter("format")
        if (!prefs.adaptiveQualityEnabled && cachedResourceKey == null && format != null && format != requestedQuality.format) {
            val streamRequest = runBlocking {
                subsonicRepository.buildStreamRequest(serverId, songId, requestedQuality.maxBitRate, format)
            }
            if (streamRequest.candidates.isEmpty()) {
                throw IOException("No stream candidates for song $songId on server $serverId")
            }
            val candidate = streamRequest.candidates.first()
            endpointSelector.recordSuccess(serverId, candidate.endpoint)
            val cacheKey = streamCacheRepository.buildCacheKey(serverId, songId, requestedQuality)
            return dataSpec.buildUpon()
                .setUri(candidate.url)
                .setKey(cacheKey)
                .build()
        }

        val streamRequest = runBlocking {
            subsonicRepository.buildStreamRequest(serverId, songId, quality.maxBitRate, quality.format)
        }
        if (streamRequest.candidates.isEmpty()) {
            throw IOException("No stream candidates for song $songId on server $serverId")
        }

        val candidate = streamRequest.candidates.first()
        endpointSelector.recordSuccess(serverId, candidate.endpoint)
        val realUrl = candidate.url
        val cacheKey = streamCacheRepository.buildCacheKey(serverId, songId, quality)

        return dataSpec.buildUpon()
            .setUri(realUrl)
            .setKey(cachedResourceKey ?: cacheKey)
            .build()
    }

    private fun shouldPreferLocalStreamCache(serverId: Long): Boolean {
        return endpointSelector.isOfflineDegraded(serverId)
    }

    private fun cachedStreamUri(cacheKey: String): Uri {
        return Uri.Builder()
            .scheme("saki-cache")
            .authority("stream")
            .appendQueryParameter("key", cacheKey)
            .build()
    }

    override fun onGetSession(
        controllerInfo: ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlayQueue(immediate = true)
        val activePlayer = player ?: return super.onTaskRemoved(rootIntent)
        if (!activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        savePlayQueue(immediate = true)
        releaseSoundBalancingEffect()

        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        playerScope.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private var savePlayQueueJob: kotlinx.coroutines.Job? = null

    private fun savePlayQueue(immediate: Boolean = false) {
        val activePlayer = player ?: return
        val itemCount = activePlayer.mediaItemCount
        if (itemCount == 0) return
        val request = activePlayer.currentMediaItem?.toPlaybackRequestOrNull() ?: return
        val queueRequests = (0 until itemCount)
            .mapNotNull { i -> activePlayer.getMediaItemAt(i).toPlaybackRequestOrNull() }
            .filter { itemRequest -> itemRequest.serverId == request.serverId }
        val songIds = queueRequests.map(PlaybackRequest::songId)
        if (songIds.isEmpty()) return
        val positionMs = activePlayer.currentPosition
        val serverId = request.serverId
        val currentSongId = request.songId
        val snapshot = LocalPlayQueueSnapshot(
            serverId = serverId,
            songs = queueRequests.map(PlaybackRequest::toSong),
            currentSongId = currentSongId,
            positionMs = positionMs,
            updatedAt = System.currentTimeMillis(),
            source = queueRequests.toSnapshotSource(request),
        )
        savePlayQueueJob?.cancel()
        if (immediate) {
            runBlocking {
                saveLocalPlayQueueSnapshot(snapshot)
            }
            savePlayQueueJob = serviceScope.launch {
                saveRemotePlayQueue(
                    serverId = serverId,
                    songIds = songIds,
                    currentSongId = currentSongId,
                    positionMs = positionMs,
                )
            }
            return
        }

        savePlayQueueJob = serviceScope.launch {
            delay(500)
            saveLocalPlayQueueSnapshot(snapshot)
            saveRemotePlayQueue(
                serverId = serverId,
                songIds = songIds,
                currentSongId = currentSongId,
                positionMs = positionMs,
            )
        }
    }

    private suspend fun saveLocalPlayQueueSnapshot(snapshot: LocalPlayQueueSnapshot) {
        try {
            localPlayQueueRepository.save(snapshot)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        }
    }

    private suspend fun saveRemotePlayQueue(
        serverId: Long,
        songIds: List<String>,
        currentSongId: String,
        positionMs: Long,
    ) {
        try {
            subsonicRepository.savePlayQueue(
                serverId = serverId,
                songIds = songIds,
                currentSongId = currentSongId,
                positionMs = positionMs,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        }
    }

    private fun List<PlaybackRequest>.toSnapshotSource(
        currentRequest: PlaybackRequest,
    ): LocalPlayQueueSnapshotSource? {
        if (currentRequest.queueSource != PLAYBACK_QUEUE_SOURCE_LIBRARY_SONGS) return null
        val currentIndex = currentRequest.libraryIndex ?: return null
        val libraryIndexes = mapNotNull { request ->
            request.libraryIndex.takeIf { request.queueSource == PLAYBACK_QUEUE_SOURCE_LIBRARY_SONGS }
        }
        if (libraryIndexes.size != size) return null
        return LocalPlayQueueSnapshotSource(
            type = LocalPlayQueueSnapshotSourceType.LIBRARY_SONGS,
            currentIndex = currentIndex,
            windowOffset = libraryIndexes.minOrNull() ?: currentIndex,
        )
    }

    private inner class SakiMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: ControllerInfo,
        ): ConnectionResult {
            val baseResult = ConnectionResult.AcceptedResultBuilder(session)
            val sessionCommandsBuilder = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
            if (controller.packageName == packageName || controller.isTrusted) {
                val sessionCommands = sessionCommandsBuilder
                    .add(SessionCommand(ACTION_SET_SHUFFLE_ORDER, Bundle.EMPTY))
                    .build()
                return baseResult
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            val filteredPlayerCommands = Player.Commands.Builder()
                .addAllCommands()
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build()

            return baseResult
                .setAvailableSessionCommands(sessionCommandsBuilder.build())
                .setAvailablePlayerCommands(filteredPlayerCommands)
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()

            serviceScope.launch {
                try {
                    future.set(
                        mediaItems.map { mediaItem ->
                            resolvePlayableItem(mediaItem)
                        },
                    )
                } catch (throwable: Throwable) {
                    future.setException(throwable)
                }
            }

            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SET_SHUFFLE_ORDER -> {
                    val seed = args.getLong(EXTRA_SHUFFLE_SEED)
                    val anchor = args.getInt(EXTRA_SHUFFLE_ANCHOR)
                    val count = args.getInt(EXTRA_SHUFFLE_COUNT)
                    val exoPlayer = player as? ExoPlayer
                    if (exoPlayer != null && count > 0 && count == exoPlayer.mediaItemCount) {
                        val order = SakiShuffleOrder(count, seed, anchor)
                        exoPlayer.setShuffleOrder(order)
                        pendingShuffleOrder = null
                    } else {
                        pendingShuffleOrder = Triple(seed, anchor, count)
                    }
                    return successSessionResult()
                }
                ACTION_TOGGLE_REPEAT -> {
                    cycleNotificationRepeatMode()
                    return successSessionResult()
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    return toggleNotificationShuffle()
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        private suspend fun resolvePlayableItem(
            mediaItem: MediaItem,
        ): MediaItem {
            if (mediaItem.localConfiguration != null) {
                return mediaItem
            }

            val request = requireNotNull(mediaItem.toPlaybackRequestOrNull()) {
                "Missing Subsonic playback request metadata for ${mediaItem.mediaId}"
            }

            // Build placeholder URI — real stream URL resolved at play time by ResolvingDataSource
            val placeholderUri = Uri.Builder()
                .scheme("saki")
                .authority("stream")
                .appendQueryParameter("serverId", request.serverId.toString())
                .appendQueryParameter("songId", request.songId)
                .apply {
                    request.maxBitRate?.let { appendQueryParameter("maxBitRate", it.toString()) }
                    request.format?.let { appendQueryParameter("format", it) }
                }
                .build()

            // Resolve artwork URL using canonical endpoint (first by order) so
            // CoverArtEndpointInterceptor can rewrite it to the current best endpoint at load time
            val resolvedArtworkUri = request.artworkUri ?: request.coverArtId?.let { coverArtId ->
                runCatching {
                    val canonical = endpointSelector.getCanonicalEndpoint(request.serverId)
                    val candidates = subsonicRepository.buildCoverArtRequest(request.serverId, coverArtId, 720).candidates
                    val canonicalUrl = canonical?.let { c ->
                        val host = c.baseUrl.trimEnd('/').substringAfter("://")
                        candidates.find { it.url.contains(host) }?.url
                    }
                    canonicalUrl ?: candidates.firstOrNull()?.url
                }.getOrNull()
            }
            val finalRequest = if (resolvedArtworkUri != null && request.artworkUri == null) {
                request.copy(artworkUri = resolvedArtworkUri)
            } else {
                request
            }

            return MediaItem.Builder()
                .setMediaId(finalRequest.songId)
                .setUri(placeholderUri)
                .setMimeType(finalRequest.mimeType)
                .setMediaMetadata(finalRequest.toMediaMetadata())
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(placeholderUri)
                        .setExtras(finalRequest.toBundle())
                        .build(),
                )
                .build()
        }
    }

    private inner class NotificationMediaButtonListener : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncMediaButtonPreferences()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncMediaButtonPreferences()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncMediaButtonPreferences()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncMediaButtonPreferences()
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            syncMediaButtonPreferences()
        }
    }

    private inner class PlaybackRecoveryListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            syncSoundBalancingEffect(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            val activePlayer = player ?: return
            if (!error.shouldRetryNextEndpoint()) {
                return
            }

            val currentIndex = activePlayer.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET) {
                return
            }

            // Invalidate the failed endpoint so the resolver picks a different one on retry
            val request = activePlayer.currentMediaItem?.toPlaybackRequestOrNull()
            if (request != null) {
                val activeEndpointId = endpointSelector.getActiveEndpointId(request.serverId)
                if (activeEndpointId != null) {
                    endpointSelector.invalidate(request.serverId, activeEndpointId)
                }
            }

            val resumePositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = activePlayer.playWhenReady
            activePlayer.prepare()
            activePlayer.seekTo(currentIndex, resumePositionMs)
            activePlayer.playWhenReady = wasPlaying
        }
    }

    private inner class PlayQueueSaveListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            savePlayQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) savePlayQueue()
        }
    }

    private fun restoreOriginalTitle() {
        val activePlayer = player ?: return
        val title = originalMediaTitle ?: return
        val savedId = originalMediaId
        originalMediaTitle = null
        originalMediaId = null
        val item = activePlayer.currentMediaItem ?: return
        if (item.mediaId != savedId || item.mediaMetadata.title == title) return
        val restored = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon().setTitle(title).build(),
            )
            .build()
        activePlayer.replaceMediaItem(activePlayer.currentMediaItemIndex, restored)
    }

    private fun List<LyricLine>.binarySearchLastBefore(positionMs: Long): Int {
        var low = 0
        var high = size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (this[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun syncSoundBalancingEffect(audioSessionId: Int) {
        val targetSessionId = audioSessionId.takeIf { it != C.AUDIO_SESSION_ID_UNSET && it != 0 }
        val targetGainMb = soundBalancingMode.targetGainMb
        if (targetGainMb == null || targetSessionId == null) {
            releaseSoundBalancingEffect()
            return
        }

        if (loudnessEnhancer == null || loudnessEnhancerSessionId != targetSessionId) {
            releaseSoundBalancingEffect()
            loudnessEnhancer = createLoudnessEnhancer(targetSessionId) ?: return
            loudnessEnhancerSessionId = targetSessionId
        }

        runCatching {
            loudnessEnhancer?.setTargetGain(targetGainMb)
            loudnessEnhancer?.enabled = true
        }.onFailure {
            releaseSoundBalancingEffect()
        }
    }

    @Synchronized
    private fun releaseSoundBalancingEffect() {
        runCatching {
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
        loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun createLoudnessEnhancer(audioSessionId: Int): LoudnessEnhancer? {
        return runCatching {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(soundBalancingMode.targetGainMb ?: 0)
                enabled = true
            }
        }.getOrNull()
    }
}

private fun PlaybackRequest.toSong(): Song {
    return Song(
        id = songId,
        parentId = null,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArtId,
        durationSeconds = durationMs?.div(1_000L)?.toInt(),
        track = track,
        discNumber = discNumber,
        year = null,
        genre = null,
        bitRate = sourceBitRate ?: bitRate,
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = mimeType,
        sizeBytes = null,
        path = null,
        created = null,
    )
}

private fun PlaybackException.shouldRetryNextEndpoint(): Boolean {
    return causeSequence()
        .filterIsInstance<IOException>()
        .any { exception ->
            exception is UnknownHostException ||
                exception is ConnectException ||
                exception is SocketTimeoutException ||
                exception is NoRouteToHostException
        }
}

private fun Throwable.causeSequence(): Sequence<Throwable> = sequence {
    var current: Throwable? = this@causeSequence
    while (current != null) {
        yield(current)
        current = current.cause
    }
}

private fun successSessionResult(): ListenableFuture<SessionResult> =
    SettableFuture.create<SessionResult>().apply {
        set(SessionResult(SessionResult.RESULT_SUCCESS))
    }
