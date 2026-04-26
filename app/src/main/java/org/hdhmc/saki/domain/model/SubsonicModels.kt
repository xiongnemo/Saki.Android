package org.hdhmc.saki.domain.model

data class SubsonicCallResult<T>(
    val endpoint: ServerEndpoint,
    val data: T,
)

data class PingResult(
    val apiVersion: String,
    val serverType: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean?,
)

data class MusicFolder(
    val id: String,
    val name: String,
)

data class LibraryIndexes(
    val lastModified: Long?,
    val ignoredArticles: String?,
    val shortcuts: List<ArtistSummary>,
    val sections: List<ArtistSection>,
)

data class ArtistSection(
    val name: String,
    val artists: List<ArtistSummary>,
)

data class ArtistSummary(
    val id: String,
    val name: String,
    val albumCount: Int?,
    val coverArtId: String?,
    val artistImageUrl: String?,
)

data class Artist(
    val id: String,
    val name: String,
    val coverArtId: String?,
    val artistImageUrl: String?,
    val albumCount: Int?,
    val albums: List<AlbumSummary>,
)

enum class AlbumListType(val apiValue: String) {
    RANDOM("random"),
    NEWEST("newest"),
    HIGHEST("highest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    ALPHABETICAL_BY_NAME("alphabeticalByName"),
    ALPHABETICAL_BY_ARTIST("alphabeticalByArtist"),
    STARRED("starred"),
    BY_YEAR("byYear"),
    BY_GENRE("byGenre");

    companion object {
        val defaultBrowseFeeds = listOf(
            NEWEST,
            RECENT,
            RANDOM,
            HIGHEST,
            FREQUENT,
            ALPHABETICAL_BY_NAME,
            ALPHABETICAL_BY_ARTIST,
            STARRED,
        )

        fun fromApiValue(apiValue: String?): AlbumListType? {
            return entries.firstOrNull { it.apiValue == apiValue }
        }
    }
}

data class AlbumSummary(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val year: Int?,
    val genre: String?,
    val created: String?,
)

data class Album(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val year: Int?,
    val genre: String?,
    val created: String?,
    val songs: List<Song>,
)

data class Song(
    val id: String,
    val parentId: String?,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val coverArtId: String?,
    val durationSeconds: Int?,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val bitRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val path: String?,
    val created: String?,
)

data class PlaylistSummary(
    val id: String,
    val name: String,
    val owner: String?,
    val isPublic: Boolean?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val coverArtId: String?,
    val created: String?,
    val changed: String?,
)

data class Playlist(
    val id: String,
    val name: String,
    val owner: String?,
    val isPublic: Boolean?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val coverArtId: String?,
    val created: String?,
    val changed: String?,
    val songs: List<Song>,
)

data class SearchResults(
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val songs: List<Song> = emptyList(),
)

data class SavedPlayQueue(
    val songs: List<Song>,
    val currentSongId: String?,
    val positionMs: Long,
)

data class AuthenticatedUrlCandidate(
    val endpoint: ServerEndpoint,
    val url: String,
)

typealias SubsonicStreamCandidate = AuthenticatedUrlCandidate

data class SubsonicStreamRequest(
    val songId: String,
    val candidates: List<AuthenticatedUrlCandidate>,
)

data class SubsonicDownloadRequest(
    val songId: String,
    val candidates: List<AuthenticatedUrlCandidate>,
)

data class SubsonicCoverArtRequest(
    val coverArtId: String,
    val candidates: List<AuthenticatedUrlCandidate>,
)

data class WordCue(
    val startMs: Long,
    val text: String,
)

data class LyricLine(
    val startMs: Long,
    val text: String,
    val words: List<WordCue>? = null,
)

data class SongLyrics(
    val synced: Boolean,
    val lines: List<LyricLine>,
)
