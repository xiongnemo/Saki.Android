package org.hdhmc.saki.presentation.library

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.Song

@Composable
fun ArtistDetailScreen(
    server: ServerConfig,
    artist: Artist,
    topSongs: List<Song>,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    onOpenAlbum: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
) {
    val albumCount = artist.albumCount
    LibraryDetailScaffold(
        title = artist.name,
        subtitle = if (albumCount != null) albumCountText(albumCount) else null,
        artwork = null,
    ) {
        when {
            isLoading && topSongs.isEmpty() -> item { LoadingStateCard(stringResource(R.string.library_loading_artist)) }
            error != null && topSongs.isEmpty() -> item { ErrorStateCard(error) }
            else -> {
                if (topSongs.isNotEmpty()) {
                    item {
                        SectionTitle(
                            stringResource(R.string.library_popular_songs),
                            stringResource(R.string.library_popular_songs_subtitle, artist.name),
                        )
                    }
                    itemsIndexed(topSongs, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song,
                            server = server,
                            cachedSong = cachedSongsBySongId[song.id],
                            isStreamCached = song.id in streamCachedSongIds,
                            isDownloading = song.id in downloadingSongIds,
                            onClick = { onPlaySongs(topSongs, index) },
                            onMore = { onShowActions(song) },
                        )
                    }
                }
                if (artist.albums.isNotEmpty()) {
                    item {
                        SectionTitle(
                            stringResource(R.string.library_albums),
                            stringResource(R.string.library_albums_subtitle_full_release),
                        )
                    }
                    item {
                        LazyRow {
                            items(artist.albums, key = { it.id }) { album ->
                                AlbumMiniCard(album = album, server = server, onOpenAlbum = onOpenAlbum)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    server: ServerConfig,
    album: Album,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
) {
    val songCount = album.songCount
    val subtitle = listOfNotNull(
        album.artist,
        album.year?.toString(),
        if (songCount != null) songCountText(songCount) else null,
    ).joinToString(" • ")
    LibraryDetailScaffold(
        title = album.name,
        subtitle = subtitle,
        artwork = resolveArtworkModel(server, album.coverArtId, null),
    ) {
        when {
            isLoading && album.songs.isEmpty() -> item { LoadingStateCard(stringResource(R.string.library_loading_album)) }
            error != null && album.songs.isEmpty() -> item { ErrorStateCard(error) }
            else -> {
                item {
                    SectionTitle(
                        title = stringResource(R.string.library_track_list),
                        subtitle = album.genre ?: stringResource(R.string.library_album_details),
                        actionLabel = stringResource(R.string.library_play_album),
                        onAction = { if (album.songs.isNotEmpty()) onPlaySongs(album.songs, 0) },
                    )
                }
                itemsIndexed(album.songs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        server = server,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        onClick = { onPlaySongs(album.songs, index) },
                        onMore = { onShowActions(song) },
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    server: ServerConfig,
    playlist: Playlist,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
) {
    val songCount = playlist.songCount
    val subtitle = listOfNotNull(
        playlist.owner,
        if (songCount != null) songCountText(songCount) else null,
    ).joinToString(" • ")
    LibraryDetailScaffold(
        title = playlist.name,
        subtitle = subtitle,
        artwork = resolveArtworkModel(server, playlist.coverArtId, null),
    ) {
        when {
            isLoading && playlist.songs.isEmpty() -> item { LoadingStateCard(stringResource(R.string.library_loading_playlist)) }
            error != null && playlist.songs.isEmpty() -> item { ErrorStateCard(error) }
            else -> {
                item {
                    SectionTitle(
                        title = stringResource(R.string.library_tracks),
                        subtitle = stringResource(R.string.library_playlist_sequence),
                        actionLabel = stringResource(R.string.library_play_playlist),
                        onAction = { if (playlist.songs.isNotEmpty()) onPlaySongs(playlist.songs, 0) },
                    )
                }
                itemsIndexed(playlist.songs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                    SongRow(
                        song = song,
                        server = server,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        onClick = { onPlaySongs(playlist.songs, index) },
                        onMore = { onShowActions(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDetailScaffold(
    title: String,
    subtitle: String?,
    artwork: Any?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
                if (artwork != null) {
                    ArtworkCard(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        cornerRadiusDp = 34,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = if (artwork != null) 14.dp else 0.dp),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun ArtistRow(artist: ArtistSummary, onOpenArtist: (String) -> Unit) {
    val albumCount = artist.albumCount
    RowCard(
        title = artist.name,
        subtitle = if (albumCount != null) albumCountText(albumCount) else null,
        artwork = null,
        onClick = { onOpenArtist(artist.id) },
    )
}

@Composable
fun PlaylistCard(playlist: PlaylistSummary, server: ServerConfig, onOpenPlaylist: (String) -> Unit) {
    val songCount = playlist.songCount
    val subtitle = listOfNotNull(
        playlist.owner,
        if (songCount != null) songCountText(songCount) else null,
    ).joinToString(" • ")
    RowCard(
        title = playlist.name,
        subtitle = subtitle,
        artwork = resolveArtworkModel(server, playlist.coverArtId, null),
        onClick = { onOpenPlaylist(playlist.id) },
    )
}

@Composable
fun AlbumRow(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    val songCount = album.songCount
    val subtitle = listOfNotNull(
        album.artist,
        album.year?.toString(),
        if (songCount != null) songCountText(songCount) else null,
    ).joinToString(" • ")
    RowCard(
        title = album.name,
        subtitle = subtitle,
        artwork = resolveArtworkModel(server, album.coverArtId, null),
        onClick = { onOpenAlbum(album.id) },
    )
}

@Composable
fun ArtistShortcutCard(artist: ArtistSummary, onOpenArtist: (String) -> Unit) {
    val albumCount = artist.albumCount
    Card(
        modifier = Modifier
            .width(190.dp)
            .padding(end = 12.dp)
            .clickable { onOpenArtist(artist.id) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = artist.name, style = MaterialTheme.typography.titleLarge)
            if (albumCount != null) {
                Text(
                    text = releaseCountText(albumCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AlbumCard(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    Card(
        onClick = { onOpenAlbum(album.id) },
        modifier = Modifier
            .padding(6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ArtworkCard(
                model = resolveArtworkModel(server, album.coverArtId, null),
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                cornerRadiusDp = 24,
            )
            Text(text = album.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(album.artist, album.year?.toString()).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumMiniCard(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .padding(end = 12.dp)
            .clickable { onOpenAlbum(album.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ArtworkCard(
                model = resolveArtworkModel(server, album.coverArtId, null),
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
                cornerRadiusDp = 18,
            )
            Text(text = album.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = album.year?.toString() ?: album.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    server: ServerConfig,
    cachedSong: CachedSong?,
    isStreamCached: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkCard(
            model = resolveArtworkModel(server, song.coverArtId, cachedSong),
            contentDescription = song.title,
            modifier = Modifier.size(60.dp),
            cornerRadiusDp = 18,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    cachedSong != null || isStreamCached -> Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = stringResource(R.string.library_available_offline),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    isDownloading -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = listOfNotNull(song.artist, song.album).joinToString(" • ")
                        .ifBlank { stringResource(R.string.library_unknown_artist_album) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.library_more_actions))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SongActionsSheet(
    song: Song,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleDownload: () -> Unit,
    onDetails: () -> Unit,
    onQueueSong: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = song.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SheetActionRow(
                icon = Icons.Rounded.SkipNext,
                title = stringResource(R.string.library_play_next),
                subtitle = stringResource(R.string.library_play_next_subtitle),
                onClick = onPlayNext,
            )
            SheetActionRow(
                icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.CloudDownload,
                title = when {
                    isDownloaded -> stringResource(R.string.settings_remove_download)
                    isDownloading -> stringResource(R.string.library_downloading)
                    else -> stringResource(R.string.library_download)
                },
                subtitle = if (isDownloaded) {
                    stringResource(R.string.library_delete_cached_copy)
                } else {
                    stringResource(R.string.library_save_offline)
                },
                enabled = !isDownloading,
                onClick = onToggleDownload,
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(R.string.library_add_to_queue),
                subtitle = stringResource(R.string.library_add_to_queue_subtitle),
                onClick = onQueueSong,
            )
            SheetActionRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.library_details),
                subtitle = stringResource(R.string.library_show_metadata),
                onClick = onDetails,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.common_close))
            }
        },
        title = { Text(song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(stringResource(R.string.detail_artist), song.artist)
                DetailLine(stringResource(R.string.detail_album), song.album)
                DetailLine(stringResource(R.string.detail_track), song.track?.toString())
                DetailLine(stringResource(R.string.detail_disc), song.discNumber?.toString())
                DetailLine(stringResource(R.string.detail_genre), song.genre)
                DetailLine(stringResource(R.string.detail_bitrate), song.bitRate?.let { "$it kbps" })
                DetailLine(stringResource(R.string.detail_duration), song.durationSeconds?.let(::formatDurationSeconds))
            }
        },
    )
}

@Composable
fun NoServerBrowseState(modifier: Modifier, onManageServers: () -> Unit, onImportBackup: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.browse_needs_server), style = MaterialTheme.typography.displaySmall)
                Text(
                    text = stringResource(R.string.browse_needs_server_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onManageServers, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.browse_add_server)) }
                    OutlinedButton(onClick = onImportBackup, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.browse_import_backup)) }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(actionLabel, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingStateCard(label: String) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text = label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorStateCard(message: String) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text = message,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun EmptyStateCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowCard(title: String, subtitle: String?, artwork: Any?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (artwork != null) {
                ArtworkCard(model = artwork, contentDescription = title, modifier = Modifier.size(72.dp), cornerRadiusDp = 22)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (artwork != null) 12.dp else 0.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    val unknown = stringResource(R.string.detail_unknown)
    Text(
        text = stringResource(
            R.string.detail_line_format,
            label,
            value.orEmpty().ifBlank { unknown },
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun albumCountText(count: Int): String =
    pluralStringResource(R.plurals.library_album_count, count, count)

@Composable
private fun songCountText(count: Int): String =
    pluralStringResource(R.plurals.library_song_count, count, count)

@Composable
private fun releaseCountText(count: Int): String =
    pluralStringResource(R.plurals.library_release_count, count, count)

private fun formatDurationSeconds(durationSeconds: Int): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
