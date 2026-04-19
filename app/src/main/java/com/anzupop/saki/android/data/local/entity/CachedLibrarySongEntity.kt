package com.anzupop.saki.android.data.local.entity

import androidx.room.Entity

@Entity(tableName = "cached_library_songs", primaryKeys = ["serverId", "songId"])
data class CachedLibrarySongEntity(
    val serverId: Long,
    val songId: String,
    val parentId: String?,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val durationSeconds: Int?,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val bitRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val path: String?,
    val created: String?,
)
