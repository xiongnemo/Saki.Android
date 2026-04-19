package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.LibraryIndexes
import com.anzupop.saki.android.domain.model.PlaylistSummary

interface LibraryCacheRepository {
    suspend fun getArtists(serverId: Long): LibraryIndexes?
    suspend fun saveArtists(serverId: Long, indexes: LibraryIndexes)
    suspend fun getAlbums(serverId: Long, type: AlbumListType): List<AlbumSummary>
    suspend fun saveAlbums(serverId: Long, type: AlbumListType, albums: List<AlbumSummary>)
    suspend fun getPlaylists(serverId: Long): List<PlaylistSummary>
    suspend fun savePlaylists(serverId: Long, playlists: List<PlaylistSummary>)
}
