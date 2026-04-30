package org.hdhmc.saki.domain.model

data class LocalPlayQueueSnapshot(
    val serverId: Long,
    val songs: List<Song>,
    val currentSongId: String?,
    val positionMs: Long,
    val updatedAt: Long,
    val source: LocalPlayQueueSnapshotSource? = null,
)

data class LocalPlayQueueSnapshotSource(
    val type: LocalPlayQueueSnapshotSourceType,
    val currentIndex: Int,
    val windowOffset: Int,
)

enum class LocalPlayQueueSnapshotSourceType {
    LIBRARY_SONGS,
}
