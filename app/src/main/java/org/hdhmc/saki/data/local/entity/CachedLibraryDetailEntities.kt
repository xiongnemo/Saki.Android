package org.hdhmc.saki.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_song_metadata",
    primaryKeys = ["serverId", "songId"],
    indices = [
        Index(value = ["serverId", "albumId"]),
        Index(value = ["serverId", "artistId"]),
        Index(value = ["serverId", "libraryOrder", "title", "songId"]),
        Index(value = ["serverId", "title", "songId"]),
    ],
)
data class CachedSongMetadataEntity(
    val serverId: Long,
    val songId: String,
    val parentId: String?,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
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
    val sampleRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val path: String?,
    val created: String?,
    val cachedAt: Long,
    @ColumnInfo(defaultValue = "2147483647")
    val libraryOrder: Int,
)

data class CachedSongMetadataOrder(
    val songId: String,
    val libraryOrder: Int,
)

@Entity(tableName = "cached_artist_details", primaryKeys = ["serverId", "artistId"])
data class CachedArtistDetailEntity(
    val serverId: Long,
    val artistId: String,
    val name: String,
    val coverArtId: String?,
    val artistImageUrl: String?,
    val albumCount: Int?,
    val cachedAt: Long,
    val isComplete: Boolean,
)

@Entity(
    tableName = "cached_artist_detail_albums",
    primaryKeys = ["serverId", "artistId", "albumId"],
    indices = [Index(value = ["serverId", "artistId", "sortOrder"])],
)
data class CachedArtistDetailAlbumEntity(
    val serverId: Long,
    val artistId: String,
    val albumId: String,
    val name: String,
    val artist: String?,
    val albumArtistId: String?,
    val coverArtId: String?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val year: Int?,
    val genre: String?,
    val created: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "cached_artist_detail_songs",
    primaryKeys = ["serverId", "artistId", "sortOrder"],
    indices = [
        Index(value = ["serverId", "artistId"]),
        Index(value = ["serverId", "songId"]),
    ],
)
data class CachedArtistDetailSongEntity(
    val serverId: Long,
    val artistId: String,
    val songId: String,
    val sortOrder: Int,
)

@Entity(tableName = "cached_album_details", primaryKeys = ["serverId", "albumId"])
data class CachedAlbumDetailEntity(
    val serverId: Long,
    val albumId: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val year: Int?,
    val genre: String?,
    val created: String?,
    val cachedAt: Long,
    val isComplete: Boolean,
)

@Entity(
    tableName = "cached_album_detail_songs",
    primaryKeys = ["serverId", "albumId", "sortOrder"],
    indices = [
        Index(value = ["serverId", "albumId"]),
        Index(value = ["serverId", "songId"]),
    ],
)
data class CachedAlbumDetailSongEntity(
    val serverId: Long,
    val albumId: String,
    val songId: String,
    val sortOrder: Int,
)

@Entity(tableName = "cached_playlist_details", primaryKeys = ["serverId", "playlistId"])
data class CachedPlaylistDetailEntity(
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
    val cachedAt: Long,
    val isComplete: Boolean,
)

@Entity(
    tableName = "cached_playlist_detail_songs",
    primaryKeys = ["serverId", "playlistId", "sortOrder"],
    indices = [
        Index(value = ["serverId", "playlistId"]),
        Index(value = ["serverId", "songId"]),
    ],
)
data class CachedPlaylistDetailSongEntity(
    val serverId: Long,
    val playlistId: String,
    val songId: String,
    val sortOrder: Int,
)
