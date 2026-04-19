package com.anzupop.saki.android.domain.model

enum class StreamQuality(
    val storageKey: String,
    val label: String,
    val maxBitRate: Int?,
    val format: String?,
    val preferOriginalDownload: Boolean,
) {
    ORIGINAL(
        storageKey = "original",
        label = "Original",
        maxBitRate = 0,
        format = "raw",
        preferOriginalDownload = true,
    ),
    KBPS_320(
        storageKey = "320",
        label = "320 kbps",
        maxBitRate = 320,
        format = null,
        preferOriginalDownload = false,
    ),
    KBPS_256(
        storageKey = "256",
        label = "256 kbps",
        maxBitRate = 256,
        format = null,
        preferOriginalDownload = false,
    ),
    KBPS_192(
        storageKey = "192",
        label = "192 kbps",
        maxBitRate = 192,
        format = null,
        preferOriginalDownload = false,
    ),
    KBPS_160(
        storageKey = "160",
        label = "160 kbps",
        maxBitRate = 160,
        format = null,
        preferOriginalDownload = false,
    ),
    KBPS_128(
        storageKey = "128",
        label = "128 kbps",
        maxBitRate = 128,
        format = null,
        preferOriginalDownload = false,
    ),
    KBPS_96(
        storageKey = "96",
        label = "96 kbps",
        maxBitRate = 96,
        format = null,
        preferOriginalDownload = false,
    ),
    ;

    companion object {
        fun fromStorageKey(storageKey: String?): StreamQuality {
            return entries.firstOrNull { quality ->
                quality.storageKey == storageKey
            } ?: ORIGINAL
        }
    }
}

const val DEFAULT_STREAM_CACHE_SIZE_MB = 2_048
const val MIN_STREAM_CACHE_SIZE_MB = 256
const val MAX_STREAM_CACHE_SIZE_MB = 8_192
const val STREAM_CACHE_SIZE_STEP_MB = 256

enum class SoundBalancingMode(
    val storageKey: String,
    val label: String,
    val targetGainMb: Int?,
) {
    OFF(
        storageKey = "off",
        label = "Off",
        targetGainMb = null,
    ),
    LOW(
        storageKey = "low",
        label = "Low",
        targetGainMb = 150,
    ),
    MEDIUM(
        storageKey = "medium",
        label = "Medium",
        targetGainMb = 350,
    ),
    HIGH(
        storageKey = "high",
        label = "High",
        targetGainMb = 600,
    ),
    ;

    companion object {
        fun fromStorageKey(storageKey: String?): SoundBalancingMode {
            return entries.firstOrNull { mode ->
                mode.storageKey == storageKey
            } ?: OFF
        }
    }
}

data class PlaybackPreferences(
    val streamQuality: StreamQuality = StreamQuality.ORIGINAL,
    val adaptiveQualityEnabled: Boolean = false,
    val wifiStreamQuality: StreamQuality = StreamQuality.ORIGINAL,
    val mobileStreamQuality: StreamQuality = StreamQuality.KBPS_320,
    val soundBalancingMode: SoundBalancingMode = SoundBalancingMode.OFF,
    val streamCacheSizeMb: Int = DEFAULT_STREAM_CACHE_SIZE_MB,
    val bluetoothLyricsEnabled: Boolean = false,
) {
    val streamCacheSizeBytes: Long
        get() = streamCacheSizeMb.toLong() * 1024L * 1024L
}

enum class RepeatModeSetting {
    OFF,
    ONE,
    ALL,
    ;

    fun next(): RepeatModeSetting {
        return when (this) {
            OFF -> ALL
            ALL -> ONE
            ONE -> OFF
        }
    }
}

data class CachedSong(
    val cacheId: String,
    val serverId: Long,
    val songId: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val coverArtPath: String?,
    val localPath: String,
    val durationSeconds: Int?,
    val track: Int?,
    val discNumber: Int?,
    val suffix: String?,
    val contentType: String?,
    val quality: StreamQuality,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
)

data class CacheStorageSummary(
    val downloadedSongCount: Int = 0,
    val downloadedBytes: Long = 0,
    val streamCachedSongCount: Int = 0,
    val streamCacheBytes: Long = 0,
    val hasStreamingCache: Boolean = false,
)

data class StreamCacheSummary(
    val cachedSongIds: Set<String> = emptySet(),
    val bytes: Long = 0,
)

data class PlaybackQueueItem(
    val mediaId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val artistId: String?,
    val album: String?,
    val albumId: String?,
    val artworkUri: String?,
    val serverId: Long?,
    val coverArtId: String?,
    val coverArtPath: String?,
    val localPath: String?,
    val qualityLabel: String,
    val isCached: Boolean,
)

data class PlaybackRuntimeInfo(
    val sampleMimeType: String? = null,
    val containerMimeType: String? = null,
    val codecs: String? = null,
    val averageBitrate: Int? = null,
    val peakBitrate: Int? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val language: String? = null,
)

data class PlaybackSessionState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentItem: PlaybackQueueItem? = null,
    val currentIndex: Int = -1,
    val queue: List<PlaybackQueueItem> = emptyList(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val repeatMode: RepeatModeSetting = RepeatModeSetting.OFF,
    val shuffleEnabled: Boolean = false,
    val preferences: PlaybackPreferences = PlaybackPreferences(),
    val runtimeInfo: PlaybackRuntimeInfo? = null,
)
