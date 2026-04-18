package com.anzupop.saki.android.data.remote.subsonic

import com.anzupop.saki.android.domain.model.Album
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.Artist
import com.anzupop.saki.android.domain.model.ArtistSection
import com.anzupop.saki.android.domain.model.ArtistSummary
import com.anzupop.saki.android.domain.model.LibraryIndexes
import com.anzupop.saki.android.domain.model.MusicFolder
import com.anzupop.saki.android.domain.model.PingResult
import com.anzupop.saki.android.domain.model.Playlist
import com.anzupop.saki.android.domain.model.PlaylistSummary
import com.anzupop.saki.android.domain.model.SearchResults
import com.anzupop.saki.android.domain.model.Song

internal fun SubsonicResponseDto.requireSuccess(): SubsonicResponseDto {
    if (status == "ok") return this

    throw IllegalStateException(error?.message ?: "The Subsonic server returned an unknown API failure.")
}

internal fun SubsonicResponseDto.toPingResult(): PingResult {
    return PingResult(
        apiVersion = version,
        serverType = type,
        serverVersion = serverVersion,
        openSubsonic = openSubsonic,
    )
}

internal fun SubsonicResponseDto.toMusicFolders(): List<MusicFolder> {
    return musicFolders?.musicFolder.orEmpty().map { folder ->
        MusicFolder(
            id = folder.id,
            name = folder.name,
        )
    }
}

internal fun SubsonicResponseDto.toLibraryIndexes(): LibraryIndexes {
    val indexesPayload = indexes ?: return LibraryIndexes(
        lastModified = null,
        ignoredArticles = null,
        shortcuts = emptyList(),
        sections = emptyList(),
    )

    return LibraryIndexes(
        lastModified = indexesPayload.lastModified,
        ignoredArticles = indexesPayload.ignoredArticles,
        shortcuts = indexesPayload.shortcut.map(ArtistDto::toArtistSummary),
        sections = indexesPayload.index.mapNotNull { section ->
            val sectionName = section.name ?: return@mapNotNull null
            ArtistSection(
                name = sectionName,
                artists = section.artist.map(ArtistDto::toArtistSummary),
            )
        },
    )
}

internal fun SubsonicResponseDto.toAlbumSummaries(): List<AlbumSummary> {
    return albumList?.album.orEmpty().map(AlbumDto::toAlbumSummary)
}

internal fun SubsonicResponseDto.toArtist(): Artist {
    val artistPayload = requireNotNull(artist) {
        "The server did not return an artist payload."
    }

    return Artist(
        id = artistPayload.id,
        name = artistPayload.name,
        coverArtId = artistPayload.coverArt,
        artistImageUrl = artistPayload.artistImageUrl,
        albumCount = artistPayload.albumCount,
        albums = artistPayload.album.map(AlbumDto::toAlbumSummary),
    )
}

internal fun SubsonicResponseDto.toAlbum(): Album {
    val albumPayload = requireNotNull(album) {
        "The server did not return an album payload."
    }

    return Album(
        id = albumPayload.id,
        name = albumPayload.displayName,
        artist = albumPayload.artist,
        artistId = albumPayload.artistId,
        coverArtId = albumPayload.coverArt,
        songCount = albumPayload.songCount,
        durationSeconds = albumPayload.duration,
        year = albumPayload.year,
        genre = albumPayload.genre,
        created = albumPayload.created,
        songs = albumPayload.song.map(SongDto::toSong),
    )
}

internal fun SubsonicResponseDto.toSong(): Song {
    return requireNotNull(song) {
        "The server did not return a song payload."
    }.toSong()
}

internal fun SubsonicResponseDto.toPlaylistSummaries(): List<PlaylistSummary> {
    return playlists?.playlist.orEmpty().map(PlaylistDto::toPlaylistSummary)
}

internal fun SubsonicResponseDto.toPlaylist(): Playlist {
    val playlistPayload = requireNotNull(playlist) {
        "The server did not return a playlist payload."
    }

    return Playlist(
        id = playlistPayload.id,
        name = playlistPayload.name,
        owner = playlistPayload.owner,
        isPublic = playlistPayload.isPublic,
        songCount = playlistPayload.songCount,
        durationSeconds = playlistPayload.duration,
        coverArtId = playlistPayload.coverArt,
        created = playlistPayload.created,
        changed = playlistPayload.changed,
        songs = playlistPayload.entry.map(SongDto::toSong),
    )
}

internal fun SubsonicResponseDto.toSearchResults(): SearchResults {
    val payload = searchResult3 ?: return SearchResults()

    return SearchResults(
        artists = payload.artist.map(ArtistDto::toArtistSummary),
        albums = payload.album.map(AlbumDto::toAlbumSummary),
        songs = payload.song.map(SongDto::toSong),
    )
}

private fun ArtistDto.toArtistSummary(): ArtistSummary {
    return ArtistSummary(
        id = id,
        name = name,
        albumCount = albumCount,
        coverArtId = coverArt,
        artistImageUrl = artistImageUrl,
    )
}

private fun AlbumDto.toAlbumSummary(): AlbumSummary {
    return AlbumSummary(
        id = id,
        name = displayName,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArt,
        songCount = songCount,
        durationSeconds = duration,
        year = year,
        genre = genre,
        created = created,
    )
}

private fun SongDto.toSong(): Song {
    return Song(
        id = id,
        parentId = parent,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArt,
        durationSeconds = duration,
        track = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = size,
        path = path,
        created = created,
    )
}

private fun PlaylistDto.toPlaylistSummary(): PlaylistSummary {
    return PlaylistSummary(
        id = id,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = duration,
        coverArtId = coverArt,
        created = created,
        changed = changed,
    )
}

private val AlbumDto.displayName: String
    get() = name ?: title ?: "Unknown Album"
