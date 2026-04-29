package org.hdhmc.saki.presentation.library

import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Song

internal fun Song.isOfflinePlayable(
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
): Boolean = id in cachedSongsBySongId || id in streamCachedSongIds

internal fun List<Song>.firstOfflinePlayableIndexOrNull(
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
): Int? {
    val index = indexOfFirst { song ->
        song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
    }
    return index.takeIf { it >= 0 }
}
