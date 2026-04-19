package com.anzupop.saki.android.data.repository

import com.anzupop.saki.android.data.local.dao.LibraryCacheDao
import com.anzupop.saki.android.data.local.entity.CachedAlbumEntity
import com.anzupop.saki.android.data.local.entity.CachedArtistEntity
import com.anzupop.saki.android.data.local.entity.CachedLibrarySongEntity
import com.anzupop.saki.android.data.local.entity.CachedPlaylistEntity
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.AlbumListType
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.ArtistSection
import com.anzupop.saki.android.domain.model.ArtistSummary
import com.anzupop.saki.android.domain.model.LibraryIndexes
import com.anzupop.saki.android.domain.model.PlaylistSummary
import com.anzupop.saki.android.domain.model.Song
import com.anzupop.saki.android.domain.repository.LibraryCacheRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryCacheRepository @Inject constructor(
    private val dao: LibraryCacheDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LibraryCacheRepository {

    override suspend fun getArtists(serverId: Long): LibraryIndexes? = withContext(ioDispatcher) {
        val entities = dao.getArtists(serverId)
        if (entities.isEmpty()) return@withContext null
        // groupBy on an ordered list preserves insertion order (LinkedHashMap)
        val sections = entities.groupBy(CachedArtistEntity::sectionName).map { (name, artists) ->
            ArtistSection(
                name = name,
                artists = artists.map { it.toDomain() },
            )
        }
        LibraryIndexes(
            lastModified = null,
            ignoredArticles = null,
            shortcuts = emptyList(),
            sections = sections,
        )
    }

    override suspend fun saveArtists(serverId: Long, indexes: LibraryIndexes) = withContext(ioDispatcher) {
        val entities = indexes.sections.flatMap { section ->
            section.artists.map { artist ->
                CachedArtistEntity(
                    serverId = serverId,
                    artistId = artist.id,
                    name = artist.name,
                    sectionName = section.name,
                    albumCount = artist.albumCount,
                    coverArtId = artist.coverArtId,
                    artistImageUrl = artist.artistImageUrl,
                )
            }
        }
        dao.replaceArtists(serverId, entities)
    }

    override suspend fun getAlbums(serverId: Long, type: AlbumListType): List<AlbumSummary> = withContext(ioDispatcher) {
        dao.getAlbums(serverId, type.apiValue).map { it.toDomain() }
    }

    override suspend fun saveAlbums(serverId: Long, type: AlbumListType, albums: List<AlbumSummary>) = withContext(ioDispatcher) {
        val entities = albums.mapIndexed { index, album ->
            CachedAlbumEntity(
                serverId = serverId,
                albumId = album.id,
                listType = type.apiValue,
                name = album.name,
                artist = album.artist,
                artistId = album.artistId,
                coverArtId = album.coverArtId,
                songCount = album.songCount,
                durationSeconds = album.durationSeconds,
                year = album.year,
                genre = album.genre,
                created = album.created,
                sortOrder = index,
            )
        }
        dao.replaceAlbums(serverId, type.apiValue, entities)
    }

    override suspend fun getPlaylists(serverId: Long): List<PlaylistSummary> = withContext(ioDispatcher) {
        dao.getPlaylists(serverId).map { it.toDomain() }
    }

    override suspend fun savePlaylists(serverId: Long, playlists: List<PlaylistSummary>) = withContext(ioDispatcher) {
        val entities = playlists.map { playlist ->
            CachedPlaylistEntity(
                serverId = serverId,
                playlistId = playlist.id,
                name = playlist.name,
                owner = playlist.owner,
                isPublic = playlist.isPublic,
                songCount = playlist.songCount,
                durationSeconds = playlist.durationSeconds,
                coverArtId = playlist.coverArtId,
                created = playlist.created,
                changed = playlist.changed,
            )
        }
        dao.replacePlaylists(serverId, entities)
    }

    override suspend fun getSongs(serverId: Long): List<Song> = withContext(ioDispatcher) {
        dao.getSongs(serverId).map { it.toDomain() }
    }

    override suspend fun saveSongs(serverId: Long, songs: List<Song>) = withContext(ioDispatcher) {
        val entities = songs.map { song ->
            CachedLibrarySongEntity(
                serverId = serverId,
                songId = song.id,
                parentId = song.parentId,
                title = song.title,
                album = song.album,
                albumId = song.albumId,
                artist = song.artist,
                artistId = song.artistId,
                coverArtId = song.coverArtId,
                durationSeconds = song.durationSeconds,
                track = song.track,
                discNumber = song.discNumber,
                year = song.year,
                genre = song.genre,
                bitRate = song.bitRate,
                suffix = song.suffix,
                contentType = song.contentType,
                sizeBytes = song.sizeBytes,
                path = song.path,
                created = song.created,
            )
        }
        dao.replaceSongs(serverId, entities)
    }

    private fun CachedArtistEntity.toDomain() = ArtistSummary(
        id = artistId,
        name = name,
        albumCount = albumCount,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
    )

    private fun CachedAlbumEntity.toDomain() = AlbumSummary(
        id = albumId,
        name = name,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
    )

    private fun CachedPlaylistEntity.toDomain() = PlaylistSummary(
        id = playlistId,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        created = created,
        changed = changed,
    )

    private fun CachedLibrarySongEntity.toDomain() = Song(
        id = songId,
        parentId = parentId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArtId,
        durationSeconds = durationSeconds,
        track = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
    )
}
