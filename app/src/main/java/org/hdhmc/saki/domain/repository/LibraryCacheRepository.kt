package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.CachedArtistDetail
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.Song

interface LibraryCacheRepository {
    suspend fun getArtists(serverId: Long): LibraryIndexes?
    suspend fun saveArtists(serverId: Long, indexes: LibraryIndexes)
    suspend fun getAlbums(serverId: Long, type: AlbumListType): List<AlbumSummary>
    suspend fun saveAlbums(serverId: Long, type: AlbumListType, albums: List<AlbumSummary>)
    suspend fun getPlaylists(serverId: Long): List<PlaylistSummary>
    suspend fun savePlaylists(serverId: Long, playlists: List<PlaylistSummary>)
    suspend fun getSongs(serverId: Long): List<Song>
    suspend fun saveSongs(serverId: Long, songs: List<Song>)
    suspend fun getArtistDetail(serverId: Long, artistId: String): CachedArtistDetail?
    suspend fun saveArtistDetail(serverId: Long, detail: CachedArtistDetail)
    suspend fun getAlbumDetail(serverId: Long, albumId: String): Album?
    suspend fun saveAlbumDetail(serverId: Long, album: Album)
    suspend fun getPlaylistDetail(serverId: Long, playlistId: String): Playlist?
    suspend fun savePlaylistDetail(serverId: Long, playlist: Playlist)
}
