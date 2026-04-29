package org.hdhmc.saki.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_songs",
    indices = [
        Index(value = ["serverId", "songId"], unique = true),
    ],
)
data class CachedSongEntity(
    @PrimaryKey
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
    val bitRate: Int?,
    val sampleRate: Int?,
    val qualityKey: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
)
