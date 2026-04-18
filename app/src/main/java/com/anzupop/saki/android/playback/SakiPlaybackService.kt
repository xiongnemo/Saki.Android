package com.anzupop.saki.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import com.anzupop.saki.android.BuildConfig
import com.anzupop.saki.android.MainActivity
import com.anzupop.saki.android.domain.model.SoundBalancingMode
import com.anzupop.saki.android.domain.repository.PlaybackPreferencesRepository
import com.anzupop.saki.android.domain.repository.SubsonicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@AndroidEntryPoint
@UnstableApi
class SakiPlaybackService : MediaSessionService() {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var subsonicRepository: SubsonicRepository

    @Inject
    lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    @Inject
    lateinit var streamCache: SimpleCache

    private val serviceScope = CoroutineScope(SupervisorJob())
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
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
                .setUserAgent(BuildConfig.APPLICATION_ID),
        )
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(PlaybackRecoveryListener())
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
            .build()

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
    }

    override fun onGetSession(
        controllerInfo: ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        releaseSoundBalancingEffect()

        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        playerScope.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class SakiMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: ControllerInfo,
        ): ConnectionResult {
            val baseResult = ConnectionResult.AcceptedResultBuilder(session)
            if (controller.packageName == packageName || controller.isTrusted) {
                return baseResult.build()
            }

            val filteredPlayerCommands = Player.Commands.Builder()
                .addAllCommands()
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build()

            return baseResult
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

        private suspend fun resolvePlayableItem(
            mediaItem: MediaItem,
        ): MediaItem {
            if (mediaItem.localConfiguration != null) {
                return mediaItem
            }

            val request = requireNotNull(mediaItem.toPlaybackRequestOrNull()) {
                "Missing Subsonic playback request metadata for ${mediaItem.mediaId}"
            }
            val streamRequest = subsonicRepository.buildStreamRequest(
                serverId = request.serverId,
                songId = request.songId,
                maxBitRate = request.maxBitRate,
                format = request.format,
            )

            return request.toPlayableMediaItem(streamRequest)
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

            val replacement = activePlayer.currentMediaItem?.nextStreamCandidateOrNull() ?: return
            val resumePositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = activePlayer.playWhenReady

            activePlayer.replaceMediaItem(currentIndex, replacement)
            activePlayer.prepare()
            activePlayer.seekTo(currentIndex, resumePositionMs)
            activePlayer.playWhenReady = wasPlaying
        }
    }

    @Synchronized
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
