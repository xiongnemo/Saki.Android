package org.hdhmc.saki.data.remote.subsonic

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubsonicEnvelopeDto(
    @field:Json(name = "subsonic-response")
    val response: SubsonicResponseDto,
)

@JsonClass(generateAdapter = true)
data class SubsonicResponseDto(
    val status: String,
    val version: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicErrorDto? = null,
    val musicFolders: MusicFoldersDto? = null,
    val indexes: IndexesDto? = null,
    val artist: ArtistDetailDto? = null,
    val albumList: AlbumListDto? = null,
    val album: AlbumDto? = null,
    val song: SongDto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val lyricsList: LyricsListDto? = null,
    val playQueue: PlayQueueDto? = null,
)

@JsonClass(generateAdapter = true)
data class SubsonicErrorDto(
    val code: Int? = null,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class MusicFoldersDto(
    val musicFolder: List<MusicFolderDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MusicFolderDto(
    val id: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class IndexesDto(
    val lastModified: Long? = null,
    val ignoredArticles: String? = null,
    val shortcut: List<ArtistDto> = emptyList(),
    val index: List<ArtistIndexDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArtistIndexDto(
    val name: String? = null,
    val artist: List<ArtistDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class ArtistDetailDto(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val album: List<AlbumDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlbumListDto(
    val album: List<AlbumDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlbumDto(
    val id: String,
    val name: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val created: String? = null,
    val song: List<SongDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SongDto(
    val id: String,
    val parent: String? = null,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val size: Long? = null,
    val path: String? = null,
    val created: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlaylistsDto(
    val playlist: List<PlaylistDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaylistDto(
    val id: String,
    val name: String,
    val owner: String? = null,
    @field:Json(name = "public")
    val isPublic: Boolean? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val created: String? = null,
    val changed: String? = null,
    val entry: List<SongDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LyricsListDto(
    val structuredLyrics: List<StructuredLyricsDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class StructuredLyricsDto(
    val lang: String? = null,
    val synced: Boolean = false,
    val offset: Long = 0,
    val line: List<LyricLineDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LyricLineDto(
    val start: Long? = null,
    val value: String = "",
)

@JsonClass(generateAdapter = true)
data class PlayQueueDto(
    val entry: List<SongDto> = emptyList(),
    val current: String? = null,
    val position: Long? = null,
    val username: String? = null,
    val changed: String? = null,
    val changedBy: String? = null,
)