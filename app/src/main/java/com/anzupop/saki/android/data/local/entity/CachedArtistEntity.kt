package com.anzupop.saki.android.data.local.entity

import androidx.room.Entity

@Entity(tableName = "cached_artists", primaryKeys = ["serverId", "artistId"])
data class CachedArtistEntity(
    val serverId: Long,
    val artistId: String,
    val name: String,
    val sectionName: String,
    val albumCount: Int?,
    val coverArtId: String?,
    val artistImageUrl: String?,
)
