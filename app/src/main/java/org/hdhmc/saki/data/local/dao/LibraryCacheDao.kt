package org.hdhmc.saki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.hdhmc.saki.data.local.entity.CachedAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedArtistEntity
import org.hdhmc.saki.data.local.entity.CachedLibrarySongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryCacheDao {

    @Query("SELECT * FROM cached_artists WHERE serverId = :serverId ORDER BY sectionName, name")
    suspend fun getArtists(serverId: Long): List<CachedArtistEntity>

    @Query("SELECT * FROM cached_artists WHERE serverId = :serverId AND artistId = :artistId LIMIT 1")
    suspend fun getArtistSummary(serverId: Long, artistId: String): CachedArtistEntity?

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

    @Query(
        """
        SELECT * FROM cached_albums
        WHERE serverId = :serverId AND albumId = :albumId
        ORDER BY
            CASE WHEN artist IS NOT NULL THEN 1 ELSE 0 END
                + CASE WHEN artistId IS NOT NULL THEN 1 ELSE 0 END
                + CASE WHEN coverArtId IS NOT NULL THEN 1 ELSE 0 END
                + CASE WHEN songCount IS NOT NULL THEN 1 ELSE 0 END
                + CASE WHEN durationSeconds IS NOT NULL THEN 1 ELSE 0 END
                + CASE WHEN year IS NOT NULL THEN 1 ELSE 0 END
                + CASE WHEN genre IS NOT NULL THEN 1 ELSE 0 END DESC,
            CASE listType
                WHEN 'newest' THEN 0
                WHEN 'recent' THEN 1
                WHEN 'highest' THEN 2
                WHEN 'frequent' THEN 3
                WHEN 'alphabeticalByName' THEN 4
                WHEN 'alphabeticalByArtist' THEN 5
                WHEN 'starred' THEN 6
                WHEN 'random' THEN 7
                ELSE 99
            END,
            sortOrder
        LIMIT 1
        """,
    )
    suspend fun getAlbumSummary(serverId: Long, albumId: String): CachedAlbumEntity?

    @Query("SELECT * FROM cached_albums WHERE serverId = :serverId AND artistId = :artistId ORDER BY year DESC, name COLLATE NOCASE")
    suspend fun getAlbumSummariesByArtistId(serverId: Long, artistId: String): List<CachedAlbumEntity>

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

    @Query("SELECT * FROM cached_artists WHERE serverId = :serverId AND name LIKE :query COLLATE NOCASE ORDER BY name COLLATE NOCASE LIMIT :limit")
    suspend fun searchArtists(serverId: Long, query: String, limit: Int): List<CachedArtistEntity>

    @Query(
        """
        SELECT * FROM cached_albums AS album
        WHERE album.serverId = :serverId
            AND (
                album.name LIKE :query COLLATE NOCASE
                OR album.artist LIKE :query COLLATE NOCASE
            )
            AND album.listType = (
                SELECT candidate.listType FROM cached_albums AS candidate
                WHERE candidate.serverId = album.serverId
                    AND candidate.albumId = album.albumId
                    AND (
                        candidate.name LIKE :query COLLATE NOCASE
                        OR candidate.artist LIKE :query COLLATE NOCASE
                    )
                ORDER BY
                    CASE WHEN candidate.artist IS NOT NULL THEN 1 ELSE 0 END
                        + CASE WHEN candidate.artistId IS NOT NULL THEN 1 ELSE 0 END
                        + CASE WHEN candidate.coverArtId IS NOT NULL THEN 1 ELSE 0 END
                        + CASE WHEN candidate.songCount IS NOT NULL THEN 1 ELSE 0 END
                        + CASE WHEN candidate.durationSeconds IS NOT NULL THEN 1 ELSE 0 END
                        + CASE WHEN candidate.year IS NOT NULL THEN 1 ELSE 0 END
                        + CASE WHEN candidate.genre IS NOT NULL THEN 1 ELSE 0 END DESC,
                    CASE candidate.listType
                        WHEN 'newest' THEN 0
                        WHEN 'recent' THEN 1
                        WHEN 'highest' THEN 2
                        WHEN 'frequent' THEN 3
                        WHEN 'alphabeticalByName' THEN 4
                        WHEN 'alphabeticalByArtist' THEN 5
                        WHEN 'starred' THEN 6
                        WHEN 'random' THEN 7
                        ELSE 99
                    END,
                    candidate.sortOrder,
                    candidate.listType
                LIMIT 1
            )
        ORDER BY album.name COLLATE NOCASE, album.albumId
        LIMIT :limit
        """,
    )
    suspend fun searchAlbums(serverId: Long, query: String, limit: Int): List<CachedAlbumEntity>

    @Query("SELECT * FROM cached_library_songs WHERE serverId = :serverId AND songId IN (:songIds)")
    suspend fun getLibrarySongs(serverId: Long, songIds: List<String>): List<CachedLibrarySongEntity>

    @Query("SELECT * FROM cached_library_songs WHERE serverId = :serverId AND albumId = :albumId ORDER BY CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, discNumber, CASE WHEN track IS NULL THEN 1 ELSE 0 END, track, title COLLATE NOCASE")
    suspend fun getLibrarySongsByAlbumId(serverId: Long, albumId: String): List<CachedLibrarySongEntity>

    @Query("SELECT * FROM cached_library_songs WHERE serverId = :serverId AND artistId = :artistId ORDER BY album COLLATE NOCASE, CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, discNumber, CASE WHEN track IS NULL THEN 1 ELSE 0 END, track, title COLLATE NOCASE")
    suspend fun getLibrarySongsByArtistId(serverId: Long, artistId: String): List<CachedLibrarySongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<CachedLibrarySongEntity>)

    @Query("DELETE FROM cached_library_songs WHERE serverId = :serverId")
    suspend fun clearSongs(serverId: Long)

    @Transaction
    suspend fun replaceSongs(serverId: Long, songs: List<CachedLibrarySongEntity>) {
        clearSongs(serverId)
        if (songs.isNotEmpty()) insertSongs(songs)
    }

    @Query("SELECT * FROM cached_artist_details WHERE serverId = :serverId AND artistId = :artistId LIMIT 1")
    suspend fun getArtistDetail(serverId: Long, artistId: String): CachedArtistDetailEntity?

    @Query("SELECT * FROM cached_artist_detail_albums WHERE serverId = :serverId AND artistId = :artistId ORDER BY sortOrder")
    suspend fun getArtistDetailAlbums(serverId: Long, artistId: String): List<CachedArtistDetailAlbumEntity>

    @Query("SELECT * FROM cached_artist_detail_songs WHERE serverId = :serverId AND artistId = :artistId ORDER BY sortOrder")
    suspend fun getArtistDetailSongs(serverId: Long, artistId: String): List<CachedArtistDetailSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistDetail(detail: CachedArtistDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistDetailAlbums(albums: List<CachedArtistDetailAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistDetailSongs(songs: List<CachedArtistDetailSongEntity>)

    @Query("DELETE FROM cached_artist_details WHERE serverId = :serverId AND artistId = :artistId")
    suspend fun clearArtistDetail(serverId: Long, artistId: String)

    @Query("DELETE FROM cached_artist_detail_albums WHERE serverId = :serverId AND artistId = :artistId")
    suspend fun clearArtistDetailAlbums(serverId: Long, artistId: String)

    @Query("DELETE FROM cached_artist_detail_songs WHERE serverId = :serverId AND artistId = :artistId")
    suspend fun clearArtistDetailSongs(serverId: Long, artistId: String)

    @Transaction
    suspend fun replaceArtistDetail(
        detail: CachedArtistDetailEntity,
        albums: List<CachedArtistDetailAlbumEntity>,
        topSongs: List<CachedArtistDetailSongEntity>,
        songMetadata: List<CachedSongMetadataEntity>,
    ) {
        clearArtistDetail(detail.serverId, detail.artistId)
        clearArtistDetailAlbums(detail.serverId, detail.artistId)
        clearArtistDetailSongs(detail.serverId, detail.artistId)
        insertArtistDetail(detail)
        if (albums.isNotEmpty()) insertArtistDetailAlbums(albums)
        if (songMetadata.isNotEmpty()) insertSongMetadata(songMetadata)
        if (topSongs.isNotEmpty()) insertArtistDetailSongs(topSongs)
    }

    @Query("SELECT * FROM cached_album_details WHERE serverId = :serverId AND albumId = :albumId LIMIT 1")
    suspend fun getAlbumDetail(serverId: Long, albumId: String): CachedAlbumDetailEntity?

    @Query("SELECT * FROM cached_album_detail_songs WHERE serverId = :serverId AND albumId = :albumId ORDER BY sortOrder")
    suspend fun getAlbumDetailSongs(serverId: Long, albumId: String): List<CachedAlbumDetailSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumDetail(detail: CachedAlbumDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumDetailSongs(songs: List<CachedAlbumDetailSongEntity>)

    @Query("DELETE FROM cached_album_details WHERE serverId = :serverId AND albumId = :albumId")
    suspend fun clearAlbumDetail(serverId: Long, albumId: String)

    @Query("DELETE FROM cached_album_detail_songs WHERE serverId = :serverId AND albumId = :albumId")
    suspend fun clearAlbumDetailSongs(serverId: Long, albumId: String)

    @Transaction
    suspend fun replaceAlbumDetail(
        detail: CachedAlbumDetailEntity,
        songs: List<CachedAlbumDetailSongEntity>,
        songMetadata: List<CachedSongMetadataEntity>,
    ) {
        clearAlbumDetail(detail.serverId, detail.albumId)
        clearAlbumDetailSongs(detail.serverId, detail.albumId)
        insertAlbumDetail(detail)
        if (songMetadata.isNotEmpty()) insertSongMetadata(songMetadata)
        if (songs.isNotEmpty()) insertAlbumDetailSongs(songs)
    }

    @Query("SELECT * FROM cached_playlist_details WHERE serverId = :serverId AND playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylistDetail(serverId: Long, playlistId: String): CachedPlaylistDetailEntity?

    @Query("SELECT playlistId FROM cached_playlist_details WHERE serverId = :serverId AND playlistId IN (:playlistIds)")
    suspend fun getCachedPlaylistDetailIds(serverId: Long, playlistIds: List<String>): List<String>

    @Query("SELECT * FROM cached_playlist_detail_songs WHERE serverId = :serverId AND playlistId = :playlistId ORDER BY sortOrder")
    suspend fun getPlaylistDetailSongs(serverId: Long, playlistId: String): List<CachedPlaylistDetailSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistDetail(detail: CachedPlaylistDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistDetailSongs(songs: List<CachedPlaylistDetailSongEntity>)

    @Query("DELETE FROM cached_playlist_details WHERE serverId = :serverId AND playlistId = :playlistId")
    suspend fun clearPlaylistDetail(serverId: Long, playlistId: String)

    @Query("DELETE FROM cached_playlist_detail_songs WHERE serverId = :serverId AND playlistId = :playlistId")
    suspend fun clearPlaylistDetailSongs(serverId: Long, playlistId: String)

    @Transaction
    suspend fun replacePlaylistDetail(
        detail: CachedPlaylistDetailEntity,
        songs: List<CachedPlaylistDetailSongEntity>,
        songMetadata: List<CachedSongMetadataEntity>,
    ) {
        clearPlaylistDetail(detail.serverId, detail.playlistId)
        clearPlaylistDetailSongs(detail.serverId, detail.playlistId)
        insertPlaylistDetail(detail)
        if (songMetadata.isNotEmpty()) insertSongMetadata(songMetadata)
        if (songs.isNotEmpty()) insertPlaylistDetailSongs(songs)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongMetadata(songs: List<CachedSongMetadataEntity>)

    @Query(
        """
        DELETE FROM cached_song_metadata
        WHERE serverId = :serverId
            AND cachedAt < :cachedAt
            AND NOT EXISTS (
                SELECT 1 FROM cached_artist_detail_songs
                WHERE cached_artist_detail_songs.serverId = cached_song_metadata.serverId
                    AND cached_artist_detail_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_album_detail_songs
                WHERE cached_album_detail_songs.serverId = cached_song_metadata.serverId
                    AND cached_album_detail_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_playlist_detail_songs
                WHERE cached_playlist_detail_songs.serverId = cached_song_metadata.serverId
                    AND cached_playlist_detail_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_songs
                WHERE cached_songs.serverId = cached_song_metadata.serverId
                    AND cached_songs.songId = cached_song_metadata.songId
            )
        """,
    )
    suspend fun pruneSongMetadataBefore(serverId: Long, cachedAt: Long)

    @Query(
        """
        DELETE FROM cached_song_metadata
        WHERE serverId = :serverId
            AND NOT EXISTS (
                SELECT 1 FROM cached_library_songs
                WHERE cached_library_songs.serverId = cached_song_metadata.serverId
                    AND cached_library_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_artist_detail_songs
                WHERE cached_artist_detail_songs.serverId = cached_song_metadata.serverId
                    AND cached_artist_detail_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_album_detail_songs
                WHERE cached_album_detail_songs.serverId = cached_song_metadata.serverId
                    AND cached_album_detail_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_playlist_detail_songs
                WHERE cached_playlist_detail_songs.serverId = cached_song_metadata.serverId
                    AND cached_playlist_detail_songs.songId = cached_song_metadata.songId
            )
            AND NOT EXISTS (
                SELECT 1 FROM cached_songs
                WHERE cached_songs.serverId = cached_song_metadata.serverId
                    AND cached_songs.songId = cached_song_metadata.songId
            )
        """,
    )
    suspend fun pruneUnreferencedSongMetadata(serverId: Long)

    @Query("SELECT * FROM cached_song_metadata WHERE serverId = :serverId AND songId IN (:songIds)")
    suspend fun getSongMetadata(serverId: Long, songIds: List<String>): List<CachedSongMetadataEntity>

    @Query("SELECT COUNT(*) FROM cached_song_metadata")
    fun observeSongMetadataInvalidations(): Flow<Int>

    @Query("SELECT songId, libraryOrder FROM cached_song_metadata WHERE serverId = :serverId AND songId IN (:songIds)")
    suspend fun getSongMetadataOrders(serverId: Long, songIds: List<String>): List<CachedSongMetadataOrder>

    @Query(
        """
        SELECT * FROM cached_song_metadata
        WHERE serverId = :serverId
        ORDER BY libraryOrder, title COLLATE NOCASE, songId
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getSongMetadataPage(serverId: Long, limit: Int, offset: Int): List<CachedSongMetadataEntity>

    @Query(
        """
        SELECT * FROM cached_song_metadata
        WHERE serverId = :serverId
            AND (
                title LIKE :query COLLATE NOCASE
                OR album LIKE :query COLLATE NOCASE
                OR artist LIKE :query COLLATE NOCASE
            )
        ORDER BY title COLLATE NOCASE, songId
        LIMIT :limit
        """,
    )
    suspend fun searchSongMetadata(serverId: Long, query: String, limit: Int): List<CachedSongMetadataEntity>

    @Query("SELECT * FROM cached_song_metadata WHERE serverId = :serverId AND albumId = :albumId ORDER BY CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, discNumber, CASE WHEN track IS NULL THEN 1 ELSE 0 END, track, title COLLATE NOCASE")
    suspend fun getSongMetadataByAlbumId(serverId: Long, albumId: String): List<CachedSongMetadataEntity>

    @Query("SELECT * FROM cached_song_metadata WHERE serverId = :serverId AND artistId = :artistId ORDER BY album COLLATE NOCASE, CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, discNumber, CASE WHEN track IS NULL THEN 1 ELSE 0 END, track, title COLLATE NOCASE")
    suspend fun getSongMetadataByArtistId(serverId: Long, artistId: String): List<CachedSongMetadataEntity>
}
