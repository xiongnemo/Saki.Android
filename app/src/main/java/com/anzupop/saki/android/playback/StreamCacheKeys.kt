package com.anzupop.saki.android.playback

import com.anzupop.saki.android.domain.model.StreamQuality

private const val STREAM_CACHE_KEY_PREFIX = "saki.stream.v1"

data class StreamCacheResourceKey(
    val serverId: Long,
    val songId: String,
    val qualityKey: String,
)

fun buildStreamCacheKey(
    serverId: Long,
    songId: String,
    quality: StreamQuality,
): String {
    return listOf(
        STREAM_CACHE_KEY_PREFIX,
        serverId.toString(),
        songId,
        quality.storageKey,
    ).joinToString("|")
}

fun parseStreamCacheKey(key: String): StreamCacheResourceKey? {
    val parts = key.split('|')
    if (parts.size != 4 || parts.first() != STREAM_CACHE_KEY_PREFIX) {
        return null
    }

    return StreamCacheResourceKey(
        serverId = parts[1].toLongOrNull() ?: return null,
        songId = parts[2].ifBlank { return null },
        qualityKey = parts[3].ifBlank { return null },
    )
}
