package org.hdhmc.saki.data.repository

import org.hdhmc.saki.data.local.dao.LibraryCacheDao
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedArtistEntity
import org.hdhmc.saki.data.local.entity.CachedLibrarySongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataEntity
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistSection
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.CachedArtistDetail
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.repository.LibraryCacheRepository
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
                sampleRate = song.sampleRate,
                suffix = song.suffix,
                contentType = song.contentType,
                sizeBytes = song.sizeBytes,
                path = song.path,
                created = song.created,
            )
        }
        dao.replaceSongs(serverId, entities)
    }

    override suspend fun getArtistDetail(
        serverId: Long,
        artistId: String,
    ): CachedArtistDetail? = withContext(ioDispatcher) {
        val detail = dao.getArtistDetail(serverId, artistId) ?: return@withContext null
        val albums = dao.getArtistDetailAlbums(serverId, artistId).map { it.toDomain() }
        val topSongRefs = dao.getArtistDetailSongs(serverId, artistId)
        CachedArtistDetail(
            artist = detail.toDomain(albums),
            topSongs = resolveSongs(serverId, topSongRefs.map { it.songId }),
        )
    }

    override suspend fun saveArtistDetail(
        serverId: Long,
        detail: CachedArtistDetail,
    ): Unit = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        dao.replaceArtistDetail(
            detail = detail.artist.toEntity(serverId, cachedAt),
            albums = detail.artist.albums.mapIndexed { index, album ->
                album.toArtistDetailAlbumEntity(serverId, detail.artist.id, index)
            },
            topSongs = detail.topSongs.mapIndexed { index, song ->
                CachedArtistDetailSongEntity(
                    serverId = serverId,
                    artistId = detail.artist.id,
                    songId = song.id,
                    sortOrder = index,
                )
            },
            songMetadata = detail.topSongs.map { song -> song.toMetadataEntity(serverId, cachedAt) },
        )
    }

    override suspend fun getAlbumDetail(
        serverId: Long,
        albumId: String,
    ): Album? = withContext(ioDispatcher) {
        val detail = dao.getAlbumDetail(serverId, albumId) ?: return@withContext null
        val songRefs = dao.getAlbumDetailSongs(serverId, albumId)
        detail.toDomain(resolveSongs(serverId, songRefs.map { it.songId }))
    }

    override suspend fun saveAlbumDetail(
        serverId: Long,
        album: Album,
    ): Unit = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        dao.replaceAlbumDetail(
            detail = album.toEntity(serverId, cachedAt),
            songs = album.songs.mapIndexed { index, song ->
                CachedAlbumDetailSongEntity(
                    serverId = serverId,
                    albumId = album.id,
                    songId = song.id,
                    sortOrder = index,
                )
            },
            songMetadata = album.songs.map { song -> song.toMetadataEntity(serverId, cachedAt) },
        )
    }

    override suspend fun getPlaylistDetail(
        serverId: Long,
        playlistId: String,
    ): Playlist? = withContext(ioDispatcher) {
        val detail = dao.getPlaylistDetail(serverId, playlistId) ?: return@withContext null
        val songRefs = dao.getPlaylistDetailSongs(serverId, playlistId)
        detail.toDomain(resolveSongs(serverId, songRefs.map { it.songId }))
    }

    override suspend fun savePlaylistDetail(
        serverId: Long,
        playlist: Playlist,
    ): Unit = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        dao.replacePlaylistDetail(
            detail = playlist.toEntity(serverId, cachedAt),
            songs = playlist.songs.mapIndexed { index, song ->
                CachedPlaylistDetailSongEntity(
                    serverId = serverId,
                    playlistId = playlist.id,
                    songId = song.id,
                    sortOrder = index,
                )
            },
            songMetadata = playlist.songs.map { song -> song.toMetadataEntity(serverId, cachedAt) },
        )
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
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
    )

    private suspend fun resolveSongs(serverId: Long, songIds: List<String>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        val songsById = songIds.distinct()
            .chunked(SONG_METADATA_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> dao.getSongMetadata(serverId, chunk) }
            .associateBy(CachedSongMetadataEntity::songId)
        return songIds.mapNotNull { songId -> songsById[songId]?.toDomain() }
    }

    private fun CachedArtistDetailEntity.toDomain(albums: List<AlbumSummary>) = Artist(
        id = artistId,
        name = name,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
        albumCount = albumCount,
        albums = albums,
    )

    private fun Artist.toEntity(serverId: Long, cachedAt: Long) = CachedArtistDetailEntity(
        serverId = serverId,
        artistId = id,
        name = name,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
        albumCount = albumCount,
        cachedAt = cachedAt,
        isComplete = true,
    )

    private fun AlbumSummary.toArtistDetailAlbumEntity(
        serverId: Long,
        detailArtistId: String,
        sortOrder: Int,
    ) = CachedArtistDetailAlbumEntity(
        serverId = serverId,
        artistId = detailArtistId,
        albumId = id,
        name = name,
        artist = artist,
        albumArtistId = artistId,
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
        sortOrder = sortOrder,
    )

    private fun CachedArtistDetailAlbumEntity.toDomain() = AlbumSummary(
        id = albumId,
        name = name,
        artist = artist,
        artistId = albumArtistId,
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
    )

    private fun Album.toEntity(serverId: Long, cachedAt: Long) = CachedAlbumDetailEntity(
        serverId = serverId,
        albumId = id,
        name = name,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
        cachedAt = cachedAt,
        isComplete = true,
    )

    private fun CachedAlbumDetailEntity.toDomain(songs: List<Song>) = Album(
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
        songs = songs,
    )

    private fun Playlist.toEntity(serverId: Long, cachedAt: Long) = CachedPlaylistDetailEntity(
        serverId = serverId,
        playlistId = id,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        created = created,
        changed = changed,
        cachedAt = cachedAt,
        isComplete = true,
    )

    private fun CachedPlaylistDetailEntity.toDomain(songs: List<Song>) = Playlist(
        id = playlistId,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        created = created,
        changed = changed,
        songs = songs,
    )

    private fun Song.toMetadataEntity(serverId: Long, cachedAt: Long) = CachedSongMetadataEntity(
        serverId = serverId,
        songId = id,
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
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
        cachedAt = cachedAt,
    )

    private fun CachedSongMetadataEntity.toDomain() = Song(
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
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
    )

    private companion object {
        const val SONG_METADATA_QUERY_CHUNK_SIZE = 500
    }
}
