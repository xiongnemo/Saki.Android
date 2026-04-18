package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.Album
import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.Artist
import com.anzupop.saki.android.domain.model.LibraryIndexes
import com.anzupop.saki.android.domain.model.MusicFolder
import com.anzupop.saki.android.domain.model.PingResult
import com.anzupop.saki.android.domain.model.Playlist
import com.anzupop.saki.android.domain.model.PlaylistSummary
import com.anzupop.saki.android.domain.model.SearchResults
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.domain.model.SongLyrics
import com.anzupop.saki.android.domain.model.SubsonicCallResult
import com.anzupop.saki.android.domain.model.SubsonicCoverArtRequest
import com.anzupop.saki.android.domain.model.SubsonicDownloadRequest
import com.anzupop.saki.android.domain.model.SubsonicStreamRequest

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
}
