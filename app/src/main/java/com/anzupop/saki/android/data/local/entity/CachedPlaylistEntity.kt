package com.anzupop.saki.android.data.local.entity

import androidx.room.Entity

@Entity(tableName = "cached_playlists", primaryKeys = ["serverId", "playlistId"])
data class CachedPlaylistEntity(
    val serverId: Long,
    val playlistId: String,
    val name: String,
    val owner: String?,
    val isPublic: Boolean?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val coverArtId: String?,
    val created: String?,
    val changed: String?,
)
