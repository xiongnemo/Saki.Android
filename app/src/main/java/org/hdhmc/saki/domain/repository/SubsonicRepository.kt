package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.MusicFolder
import org.hdhmc.saki.domain.model.PingResult
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.SavedPlayQueue
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.domain.model.SubsonicCallResult
import org.hdhmc.saki.domain.model.SubsonicCoverArtRequest
import org.hdhmc.saki.domain.model.SubsonicDownloadRequest
import org.hdhmc.saki.domain.model.SubsonicStreamRequest

interface SubsonicRepository {
    suspend fun ping(serverId: Long): SubsonicCallResult<PingResult>

    suspend fun getMusicFolders(serverId: Long): SubsonicCallResult<List<MusicFolder>>

    suspend fun getIndexes(
        serverId: Long,
        musicFolderId: String? = null,
        ifModifiedSince: Long? = null,
    ): SubsonicCallResult<LibraryIndexes>

    suspend fun getAlbumList(
        serverId: Long,
        type: AlbumListType,
        size: Int = 20,
        offset: Int = 0,
        fromYear: Int? = null,
        toYear: Int? = null,
        genre: String? = null,
        musicFolderId: String? = null,
    ): SubsonicCallResult<List<AlbumSummary>>

    suspend fun getArtist(
        serverId: Long,
        artistId: String,
    ): SubsonicCallResult<Artist>

    suspend fun getAlbum(
        serverId: Long,
        albumId: String,
    ): SubsonicCallResult<Album>

    suspend fun getSong(
        serverId: Long,
        songId: String,
    ): SubsonicCallResult<Song>

    suspend fun getPlaylists(
        serverId: Long,
        username: String? = null,
    ): SubsonicCallResult<List<PlaylistSummary>>

    suspend fun getPlaylist(
        serverId: Long,
        playlistId: String,
    ): SubsonicCallResult<Playlist>

    suspend fun search(
        serverId: Long,
        query: String,
        artistCount: Int = 12,
        artistOffset: Int = 0,
        albumCount: Int = 12,
        albumOffset: Int = 0,
        songCount: Int = 24,
        songOffset: Int = 0,
        musicFolderId: String? = null,
    ): SubsonicCallResult<SearchResults>

    suspend fun buildStreamRequest(
        serverId: Long,
        songId: String,
        maxBitRate: Int? = null,
        format: String? = null,
    ): SubsonicStreamRequest

    suspend fun buildDownloadRequest(
        serverId: Long,
        songId: String,
    ): SubsonicDownloadRequest

    suspend fun buildCoverArtRequest(
        serverId: Long,
        coverArtId: String,
        size: Int? = null,
    ): SubsonicCoverArtRequest

    suspend fun getLyrics(
        serverId: Long,
        songId: String,
    ): SubsonicCallResult<SongLyrics?>

    suspend fun getPlayQueue(serverId: Long): SubsonicCallResult<SavedPlayQueue>

    suspend fun savePlayQueue(
        serverId: Long,
        songIds: List<String>,
        currentSongId: String?,
        positionMs: Long,
    ): SubsonicCallResult<Unit>
}
