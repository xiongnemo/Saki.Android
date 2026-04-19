package com.anzupop.saki.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anzupop.saki.android.data.local.entity.CachedAlbumEntity
import com.anzupop.saki.android.data.local.entity.CachedArtistEntity
import com.anzupop.saki.android.data.local.entity.CachedLibrarySongEntity
import com.anzupop.saki.android.data.local.entity.CachedPlaylistEntity

@Dao
interface LibraryCacheDao {

    @Query("SELECT * FROM cached_artists WHERE serverId = :serverId ORDER BY sectionName, name")
    suspend fun getArtists(serverId: Long): List<CachedArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<CachedArtistEntity>)

    @Query("DELETE FROM cached_artists WHERE serverId = :serverId")
    suspend fun clearArtists(serverId: Long)

    @Transaction
    suspend fun replaceArtists(serverId: Long, artists: List<CachedArtistEntity>) {
        clearArtists(serverId)
        insertArtists(artists)
    }

    @Query("SELECT * FROM cached_albums WHERE serverId = :serverId AND listType = :listType ORDER BY sortOrder")
    suspend fun getAlbums(serverId: Long, listType: String): List<CachedAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<CachedAlbumEntity>)

    @Query("DELETE FROM cached_albums WHERE serverId = :serverId AND listType = :listType")
    suspend fun clearAlbums(serverId: Long, listType: String)

    @Transaction
    suspend fun replaceAlbums(serverId: Long, listType: String, albums: List<CachedAlbumEntity>) {
        clearAlbums(serverId, listType)
        insertAlbums(albums)
    }

    @Query("SELECT * FROM cached_playlists WHERE serverId = :serverId ORDER BY name")
    suspend fun getPlaylists(serverId: Long): List<CachedPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<CachedPlaylistEntity>)

    @Query("DELETE FROM cached_playlists WHERE serverId = :serverId")
    suspend fun clearPlaylists(serverId: Long)

    @Transaction
    suspend fun replacePlaylists(serverId: Long, playlists: List<CachedPlaylistEntity>) {
        clearPlaylists(serverId)
        insertPlaylists(playlists)
    }

    @Query("SELECT * FROM cached_library_songs WHERE serverId = :serverId ORDER BY title COLLATE NOCASE")
    suspend fun getSongs(serverId: Long): List<CachedLibrarySongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<CachedLibrarySongEntity>)

    @Query("DELETE FROM cached_library_songs WHERE serverId = :serverId")
    suspend fun clearSongs(serverId: Long)

    @Transaction
    suspend fun replaceSongs(serverId: Long, songs: List<CachedLibrarySongEntity>) {
        clearSongs(serverId)
        insertSongs(songs)
    }
}
