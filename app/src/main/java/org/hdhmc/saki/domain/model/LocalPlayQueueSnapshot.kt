package org.hdhmc.saki.domain.model

data class LocalPlayQueueSnapshot(
    val serverId: Long,
    val songs: List<Song>,
    val currentSongId: String?,
    val positionMs: Long,
    val updatedAt: Long,
)
