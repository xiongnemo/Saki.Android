package org.hdhmc.saki.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.PlaybackQueueItem
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SubsonicStreamRequest
import java.io.File

private const val EXTRA_SERVER_ID = "saki.playback.server_id"
private const val EXTRA_SONG_ID = "saki.playback.song_id"
private const val EXTRA_TITLE = "saki.playback.title"
private const val EXTRA_ALBUM = "saki.playback.album"
private const val EXTRA_ALBUM_ID = "saki.playback.album_id"
private const val EXTRA_ARTIST = "saki.playback.artist"
private const val EXTRA_ARTIST_ID = "saki.playback.artist_id"
private const val EXTRA_DURATION_MS = "saki.playback.duration_ms"
private const val EXTRA_TRACK = "saki.playback.track"
private const val EXTRA_DISC_NUMBER = "saki.playback.disc_number"
private const val EXTRA_MIME_TYPE = "saki.playback.mime_type"
private const val EXTRA_COVER_ART_ID = "saki.playback.cover_art_id"
private const val EXTRA_COVER_ART_PATH = "saki.playback.cover_art_path"
private const val EXTRA_ARTWORK_URI = "saki.playback.artwork_uri"
private const val EXTRA_LOCAL_PATH = "saki.playback.local_path"
private const val EXTRA_QUALITY_LABEL = "saki.playback.quality_label"
private const val EXTRA_STREAM_CACHE_KEY = "saki.playback.stream_cache_key"
private const val EXTRA_IS_CACHED = "saki.playback.is_cached"
private const val EXTRA_MAX_BIT_RATE = "saki.playback.max_bit_rate"
private const val EXTRA_FORMAT = "saki.playback.format"
private const val EXTRA_STREAM_URLS = "saki.playback.stream_urls"
private const val EXTRA_STREAM_INDEX = "saki.playback.stream_index"
private const val EXTRA_SUFFIX = "saki.playback.suffix"
private const val EXTRA_BIT_RATE = "saki.playback.bit_rate"
private const val EXTRA_SOURCE_BIT_RATE = "saki.playback.source_bit_rate"
private const val EXTRA_SAMPLE_RATE = "saki.playback.sample_rate"
private const val EXTRA_QUEUE_SOURCE = "saki.playback.queue_source"
private const val EXTRA_LIBRARY_INDEX = "saki.playback.library_index"

internal const val PLAYBACK_QUEUE_SOURCE_LIBRARY_SONGS = "library_songs"

internal data class PlaybackRequest(
    val serverId: Long,
    val songId: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val durationMs: Long?,
    val track: Int?,
    val discNumber: Int?,
    val mimeType: String?,
    val coverArtId: String?,
    val coverArtPath: String?,
    val artworkUri: String?,
    val localPath: String?,
    val qualityLabel: String,
    val streamCacheKey: String?,
    val isCached: Boolean,
    val maxBitRate: Int?,
    val format: String?,
    val suffix: String?,
    val bitRate: Int?,
    val sourceBitRate: Int?,
    val sampleRate: Int?,
    val queueSource: String?,
    val libraryIndex: Int?,
)

internal fun estimatedPlaybackBitRateKbps(
    sourceBitRate: Int?,
    maxBitRate: Int?,
): Int? {
    val knownSourceBitRate = sourceBitRate?.takeIf { bitrate -> bitrate > 0 }
    val requestedMaxBitRate = maxBitRate?.takeIf { bitrate -> bitrate > 0 }
        ?: return knownSourceBitRate
    return knownSourceBitRate?.coerceAtMost(requestedMaxBitRate) ?: requestedMaxBitRate
}

internal fun Song.toPlaybackRequestMediaItem(
    serverId: Long,
    qualityLabel: String,
    streamCacheKey: String,
    artworkUri: String? = null,
    maxBitRate: Int? = null,
    format: String? = null,
    queueSource: String? = null,
    libraryIndex: Int? = null,
): MediaItem {
    val request = PlaybackRequest(
        serverId = serverId,
        songId = id,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        durationMs = durationSeconds?.times(1_000L),
        track = track,
        discNumber = discNumber,
        mimeType = contentType,
        coverArtId = coverArtId,
        coverArtPath = null,
        artworkUri = artworkUri,
        localPath = null,
        qualityLabel = qualityLabel,
        streamCacheKey = streamCacheKey,
        isCached = false,
        maxBitRate = maxBitRate,
        format = format,
        suffix = suffix,
        bitRate = estimatedPlaybackBitRateKbps(bitRate, maxBitRate),
        sourceBitRate = bitRate,
        sampleRate = sampleRate,
        queueSource = queueSource,
        libraryIndex = libraryIndex,
    )

    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(request.toMediaMetadata())
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setExtras(request.toBundle())
                .build(),
        )
        .build()
}

internal fun CachedSong.toCachedMediaItem(
    queueSource: String? = null,
    libraryIndex: Int? = null,
): MediaItem {
    val localUri = Uri.fromFile(File(localPath))
    val request = PlaybackRequest(
        serverId = serverId,
        songId = songId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        durationMs = durationSeconds?.times(1_000L),
        track = track,
        discNumber = discNumber,
        mimeType = contentType,
        coverArtId = coverArtId,
        coverArtPath = coverArtPath,
        artworkUri = coverArtPath?.let { path -> Uri.fromFile(File(path)).toString() },
        localPath = localPath,
        qualityLabel = quality.label,
        streamCacheKey = null,
        isCached = true,
        maxBitRate = null,
        format = null,
        suffix = suffix,
        bitRate = bitRateKbps,
        sourceBitRate = bitRateKbps,
        sampleRate = sampleRate,
        queueSource = queueSource,
        libraryIndex = libraryIndex,
    )

    return MediaItem.Builder()
        .setMediaId(cacheId)
        .setUri(localUri)
        .setMimeType(contentType)
        .setMediaMetadata(request.toMediaMetadata())
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(localUri)
                .setExtras(request.toBundle())
                .build(),
        )
        .build()
}

internal fun MediaItem.metadataDurationMs(): Long? {
    val extras = requestMetadata.extras ?: return null
    return extras.getLong(EXTRA_DURATION_MS).takeIf { extras.containsKey(EXTRA_DURATION_MS) }
}

internal fun MediaItem.toPlaybackRequestOrNull(): PlaybackRequest? {
    val extras = requestMetadata.extras ?: return null
    val songId = extras.getString(EXTRA_SONG_ID).orEmpty().ifBlank { mediaId }
    if (songId.isBlank()) return null

    return PlaybackRequest(
        serverId = extras.getLong(EXTRA_SERVER_ID),
        songId = songId,
        title = extras.getString(EXTRA_TITLE).orEmpty(),
        album = extras.getString(EXTRA_ALBUM),
        albumId = extras.getString(EXTRA_ALBUM_ID),
        artist = extras.getString(EXTRA_ARTIST),
        artistId = extras.getString(EXTRA_ARTIST_ID),
        durationMs = extras.getLong(EXTRA_DURATION_MS).takeIf { extras.containsKey(EXTRA_DURATION_MS) },
        track = extras.getInt(EXTRA_TRACK).takeIf { extras.containsKey(EXTRA_TRACK) },
        discNumber = extras.getInt(EXTRA_DISC_NUMBER).takeIf { extras.containsKey(EXTRA_DISC_NUMBER) },
        mimeType = extras.getString(EXTRA_MIME_TYPE),
        coverArtId = extras.getString(EXTRA_COVER_ART_ID),
        coverArtPath = extras.getString(EXTRA_COVER_ART_PATH),
        artworkUri = extras.getString(EXTRA_ARTWORK_URI),
        localPath = extras.getString(EXTRA_LOCAL_PATH),
        qualityLabel = extras.getString(EXTRA_QUALITY_LABEL).orEmpty(),
        streamCacheKey = extras.getString(EXTRA_STREAM_CACHE_KEY),
        isCached = extras.getBoolean(EXTRA_IS_CACHED, false),
        maxBitRate = extras.getInt(EXTRA_MAX_BIT_RATE).takeIf { extras.containsKey(EXTRA_MAX_BIT_RATE) },
        format = extras.getString(EXTRA_FORMAT),
        suffix = extras.getString(EXTRA_SUFFIX),
        bitRate = extras.getInt(EXTRA_BIT_RATE).takeIf { extras.containsKey(EXTRA_BIT_RATE) },
        sourceBitRate = extras.getInt(EXTRA_SOURCE_BIT_RATE)
            .takeIf { extras.containsKey(EXTRA_SOURCE_BIT_RATE) },
        sampleRate = extras.getInt(EXTRA_SAMPLE_RATE).takeIf { extras.containsKey(EXTRA_SAMPLE_RATE) },
        queueSource = extras.getString(EXTRA_QUEUE_SOURCE),
        libraryIndex = extras.getInt(EXTRA_LIBRARY_INDEX).takeIf { extras.containsKey(EXTRA_LIBRARY_INDEX) },
    )
}

internal fun MediaItem.toQueueItemOrNull(): PlaybackQueueItem? {
    val extras = mediaMetadata.extras ?: requestMetadata.extras ?: return null
    val title = extras.getString(EXTRA_TITLE)
        ?: mediaMetadata.title?.toString()
        ?: return null

    val serverId = extras.getLong(EXTRA_SERVER_ID)
        .takeIf { extras.containsKey(EXTRA_SERVER_ID) }

    return PlaybackQueueItem(
        mediaId = mediaId.ifBlank { extras.getString(EXTRA_SONG_ID).orEmpty() },
        songId = extras.getString(EXTRA_SONG_ID).orEmpty().ifBlank { mediaId },
        title = title,
        artist = extras.getString(EXTRA_ARTIST) ?: mediaMetadata.artist?.toString(),
        artistId = extras.getString(EXTRA_ARTIST_ID),
        album = extras.getString(EXTRA_ALBUM) ?: mediaMetadata.albumTitle?.toString(),
        albumId = extras.getString(EXTRA_ALBUM_ID),
        artworkUri = extras.getString(EXTRA_ARTWORK_URI) ?: mediaMetadata.artworkUri?.toString(),
        serverId = serverId,
        coverArtId = extras.getString(EXTRA_COVER_ART_ID),
        coverArtPath = extras.getString(EXTRA_COVER_ART_PATH),
        localPath = extras.getString(EXTRA_LOCAL_PATH),
        qualityLabel = extras.getString(EXTRA_QUALITY_LABEL).orEmpty().ifBlank { "Original" },
        isCached = extras.getBoolean(EXTRA_IS_CACHED, false),
        suffix = extras.getString(EXTRA_SUFFIX),
        bitRateKbps = extras.getInt(EXTRA_BIT_RATE).takeIf { extras.containsKey(EXTRA_BIT_RATE) },
        sourceBitRateKbps = extras.getInt(EXTRA_SOURCE_BIT_RATE)
            .takeIf { extras.containsKey(EXTRA_SOURCE_BIT_RATE) },
        requestedMaxBitRateKbps = extras.getInt(EXTRA_MAX_BIT_RATE).takeIf { extras.containsKey(EXTRA_MAX_BIT_RATE) },
        sampleRate = extras.getInt(EXTRA_SAMPLE_RATE).takeIf { extras.containsKey(EXTRA_SAMPLE_RATE) },
        contentType = extras.getString(EXTRA_MIME_TYPE),
    )
}

@UnstableApi
internal fun PlaybackRequest.toPlayableMediaItem(
    streamRequest: SubsonicStreamRequest,
): MediaItem {
    require(streamRequest.candidates.isNotEmpty()) {
        "No playback candidates available for song $songId"
    }

    val playbackExtras = toBundle().apply {
        putStringArrayList(
            EXTRA_STREAM_URLS,
            ArrayList(streamRequest.candidates.map { it.url }),
        )
        putInt(EXTRA_STREAM_INDEX, 0)
    }

    return MediaItem.Builder()
        .setMediaId(songId)
        .setUri(streamRequest.candidates.first().url)
        .setMimeType(mimeType)
        .applyCustomCacheKey(streamCacheKey)
        .setMediaMetadata(toMediaMetadata())
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(Uri.parse(streamRequest.candidates.first().url))
                .setExtras(playbackExtras)
                .build(),
        )
        .build()
}

@UnstableApi
internal fun MediaItem.nextStreamCandidateOrNull(): MediaItem? {
    val extras = requestMetadata.extras ?: return null
    val streamUrls = extras.getStringArrayList(EXTRA_STREAM_URLS).orEmpty()
    val streamCacheKey = extras.getString(EXTRA_STREAM_CACHE_KEY)
    val currentIndex = extras.getInt(EXTRA_STREAM_INDEX, 0)
    val nextIndex = currentIndex + 1
    if (nextIndex !in streamUrls.indices) {
        return null
    }

    val nextExtras = Bundle(extras).apply {
        putInt(EXTRA_STREAM_INDEX, nextIndex)
    }
    val nextUrl = streamUrls[nextIndex]

    return buildUpon()
        .setUri(nextUrl)
        .applyCustomCacheKey(streamCacheKey)
        .setRequestMetadata(
            requestMetadata.buildUpon()
                .setMediaUri(Uri.parse(nextUrl))
                .setExtras(nextExtras)
                .build(),
        )
        .build()
}

internal fun PlaybackRequest.toBundle(): Bundle {
    return Bundle().apply {
        putLong(EXTRA_SERVER_ID, serverId)
        putString(EXTRA_SONG_ID, songId)
        putString(EXTRA_TITLE, title)
        putString(EXTRA_ALBUM, album)
        putString(EXTRA_ALBUM_ID, albumId)
        putString(EXTRA_ARTIST, artist)
        putString(EXTRA_ARTIST_ID, artistId)
        durationMs?.let { putLong(EXTRA_DURATION_MS, it) }
        track?.let { putInt(EXTRA_TRACK, it) }
        discNumber?.let { putInt(EXTRA_DISC_NUMBER, it) }
        putString(EXTRA_MIME_TYPE, mimeType)
        putString(EXTRA_COVER_ART_ID, coverArtId)
        putString(EXTRA_COVER_ART_PATH, coverArtPath)
        putString(EXTRA_ARTWORK_URI, artworkUri)
        putString(EXTRA_LOCAL_PATH, localPath)
        putString(EXTRA_QUALITY_LABEL, qualityLabel)
        putString(EXTRA_STREAM_CACHE_KEY, streamCacheKey)
        putBoolean(EXTRA_IS_CACHED, isCached)
        maxBitRate?.let { putInt(EXTRA_MAX_BIT_RATE, it) }
        putString(EXTRA_FORMAT, format)
        putString(EXTRA_SUFFIX, suffix)
        bitRate?.let { putInt(EXTRA_BIT_RATE, it) }
        sourceBitRate?.let { putInt(EXTRA_SOURCE_BIT_RATE, it) }
        sampleRate?.let { putInt(EXTRA_SAMPLE_RATE, it) }
        putString(EXTRA_QUEUE_SOURCE, queueSource)
        libraryIndex?.let { putInt(EXTRA_LIBRARY_INDEX, it) }
    }
}

@UnstableApi
private fun MediaItem.Builder.applyCustomCacheKey(
    cacheKey: String?,
): MediaItem.Builder {
    return if (cacheKey.isNullOrBlank()) {
        this
    } else {
        setCustomCacheKey(cacheKey)
    }
}

internal fun PlaybackRequest.toMediaMetadata(): MediaMetadata {
    return MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setTrackNumber(track)
        .setDiscNumber(discNumber)
        .setArtworkUri(artworkUri?.let(Uri::parse))
        .setIsPlayable(true)
        .setExtras(toBundle())
        .build()
}
