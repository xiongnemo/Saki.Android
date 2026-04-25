package org.hdhmc.saki.data.remote.subsonic

import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistSection
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.LyricLine
import org.hdhmc.saki.domain.model.MusicFolder
import org.hdhmc.saki.domain.model.WordCue
import org.hdhmc.saki.domain.model.PingResult
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.SavedPlayQueue
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SongLyrics

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

internal fun SubsonicResponseDto.toSavedPlayQueue(): SavedPlayQueue {
    val payload = playQueue ?: return SavedPlayQueue(emptyList(), null, 0)
    return SavedPlayQueue(
        songs = payload.entry.map(SongDto::toSong),
        currentSongId = payload.current,
        positionMs = payload.position ?: 0,
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

private val INLINE_TS = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

private fun parseWordCues(lineStartMs: Long, value: String): List<WordCue>? {
    if (!value.contains(INLINE_TS)) return null
    val cues = mutableListOf<WordCue>()
    var lastEnd = 0
    var nextStartMs = lineStartMs
    for (match in INLINE_TS.findAll(value)) {
        val text = value.substring(lastEnd, match.range.first)
        if (text.isNotEmpty()) cues += WordCue(nextStartMs, text)
        val (mm, ss, frac) = match.destructured
        val ms = frac.padEnd(3, '0').toLong()
        nextStartMs = mm.toLong() * 60_000 + ss.toLong() * 1_000 + ms
        lastEnd = match.range.last + 1
    }
    val tail = value.substring(lastEnd)
    if (tail.isNotEmpty()) cues += WordCue(nextStartMs, tail)
    return cues.ifEmpty { null }
}

internal fun SubsonicResponseDto.toLyrics(): SongLyrics? {
    val candidates = lyricsList?.structuredLyrics ?: return null
    val best = candidates.firstOrNull { it.synced } ?: candidates.firstOrNull() ?: return null
    val offset = best.offset
    return SongLyrics(
        synced = best.synced,
        lines = best.line.map { dto ->
            val rawStart = dto.start ?: 0L
            val words = parseWordCues(rawStart, dto.value)
            LyricLine(
                startMs = (rawStart - offset).coerceAtLeast(0L),
                text = words?.joinToString("") { it.text } ?: dto.value,
                words = words?.map { it.copy(startMs = (it.startMs - offset).coerceAtLeast(0L)) },
            )
        },
    )
}