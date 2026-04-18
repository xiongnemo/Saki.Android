package com.anzupop.saki.android.presentation.library

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anzupop.saki.android.domain.model.Album
import com.anzupop.saki.android.domain.model.AlbumSummary
import com.anzupop.saki.android.domain.model.Artist
import com.anzupop.saki.android.domain.model.ArtistSummary
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.Playlist
import com.anzupop.saki.android.domain.model.PlaylistSummary
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.Song

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
    LibraryDetailScaffold(
        title = artist.name,
        subtitle = artist.albumCount?.let { "$it ${if (it == 1) "album" else "albums"}" } ?: "Artist",
        artwork = null,
    ) {
        when {
            isLoading && topSongs.isEmpty() -> item { LoadingStateCard("Loading artist") }
            error != null && topSongs.isEmpty() -> item { ErrorStateCard(error) }
            else -> {
                if (topSongs.isNotEmpty()) {
                    item { SectionTitle("Popular songs", "Quick picks for ${artist.name}") }
                    items(topSongs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            server = server,
                            cachedSong = cachedSongsBySongId[song.id],
                            isStreamCached = song.id in streamCachedSongIds,
                            isDownloading = song.id in downloadingSongIds,
                            onClick = { onPlaySongs(topSongs, topSongs.indexOf(song)) },
                            onMore = { onShowActions(song) },
                        )
                    }
                }
                if (artist.albums.isNotEmpty()) {
                    item { SectionTitle("Albums", "Tap into the full release page") }
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
    LibraryDetailScaffold(
        title = album.name,
        subtitle = listOfNotNull(album.artist, album.year?.toString(), album.songCount?.let { "$it ${if (it == 1) "song" else "songs"}" }).joinToString(" • "),
        artwork = resolveArtworkModel(server, album.coverArtId, null),
    ) {
        when {
            isLoading && album.songs.isEmpty() -> item { LoadingStateCard("Loading album") }
            error != null && album.songs.isEmpty() -> item { ErrorStateCard(error) }
            else -> {
                item {
                    SectionTitle(
                        title = "Track list",
                        subtitle = album.genre ?: "Album details",
                        actionLabel = "Play album",
                        onAction = { if (album.songs.isNotEmpty()) onPlaySongs(album.songs, 0) },
                    )
                }
                items(album.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        server = server,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        onClick = { onPlaySongs(album.songs, album.songs.indexOf(song)) },
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
    LibraryDetailScaffold(
        title = playlist.name,
        subtitle = listOfNotNull(playlist.owner, playlist.songCount?.let { "$it ${if (it == 1) "song" else "songs"}" }).joinToString(" • "),
        artwork = resolveArtworkModel(server, playlist.coverArtId, null),
    ) {
        when {
            isLoading && playlist.songs.isEmpty() -> item { LoadingStateCard("Loading playlist") }
            error != null && playlist.songs.isEmpty() -> item { ErrorStateCard(error) }
            else -> {
                item {
                    SectionTitle(
                        title = "Tracks",
                        subtitle = "Playlist sequence",
                        actionLabel = "Play playlist",
                        onAction = { if (playlist.songs.isNotEmpty()) onPlaySongs(playlist.songs, 0) },
                    )
                }
                items(playlist.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        server = server,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        onClick = { onPlaySongs(playlist.songs, playlist.songs.indexOf(song)) },
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
    subtitle: String,
    artwork: Any?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Card(
                modifier = Modifier.padding(top = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                    Text(text = title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp))
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
    RowCard(
        title = artist.name,
        subtitle = artist.albumCount?.let { "$it ${if (it == 1) "album" else "albums"}" } ?: "Artist",
        artwork = null,
        onClick = { onOpenArtist(artist.id) },
    )
}

@Composable
fun PlaylistCard(playlist: PlaylistSummary, server: ServerConfig, onOpenPlaylist: (String) -> Unit) {
    RowCard(
        title = playlist.name,
        subtitle = listOfNotNull(playlist.owner, playlist.songCount?.let { "$it ${if (it == 1) "song" else "songs"}" }).joinToString(" • "),
        artwork = resolveArtworkModel(server, playlist.coverArtId, null),
        onClick = { onOpenPlaylist(playlist.id) },
    )
}

@Composable
fun AlbumRow(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    RowCard(
        title = album.name,
        subtitle = listOfNotNull(album.artist, album.year?.toString(), album.songCount?.let { "$it ${if (it == 1) "song" else "songs"}" }).joinToString(" • "),
        artwork = resolveArtworkModel(server, album.coverArtId, null),
        onClick = { onOpenAlbum(album.id) },
    )
}

@Composable
fun ArtistShortcutCard(artist: ArtistSummary, onOpenArtist: (String) -> Unit) {
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
            Text(
                text = artist.albumCount?.let { "$it releases" } ?: "Open artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AlbumCard(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    Card(
        modifier = Modifier
            .padding(6.dp)
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
                        contentDescription = "Available offline",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    isDownloading -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = listOfNotNull(song.artist, song.album).joinToString(" • ").ifBlank { "Unknown artist • Unknown album" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
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
            SheetActionRow(icon = Icons.Rounded.SkipNext, title = "Play next", subtitle = "Insert this after the current song", onClick = onPlayNext)
            SheetActionRow(
                icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.CloudDownload,
                title = if (isDownloaded) "Remove download" else if (isDownloading) "Downloading..." else "Download",
                subtitle = if (isDownloaded) "Delete the cached copy" else "Save this for offline playback",
                enabled = !isDownloading,
                onClick = onToggleDownload,
            )
            SheetActionRow(icon = Icons.AutoMirrored.Rounded.QueueMusic, title = "Add to queue", subtitle = "Append this song to the queue", onClick = onQueueSong)
            SheetActionRow(icon = Icons.Rounded.Info, title = "Details", subtitle = "Show metadata", onClick = onDetails)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text(song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Artist", song.artist)
                DetailLine("Album", song.album)
                DetailLine("Track", song.track?.toString())
                DetailLine("Disc", song.discNumber?.toString())
                DetailLine("Genre", song.genre)
                DetailLine("Bitrate", song.bitRate?.let { "$it kbps" })
                DetailLine("Duration", song.durationSeconds?.let(::formatDurationSeconds))
            }
        },
    )
}

@Composable
fun NoServerBrowseState(modifier: Modifier, onManageServers: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Browse needs a server profile", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = "Add a Subsonic server first, then swipe through artists, albums, playlists, and songs.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onManageServers) { Text("Add server") }
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
                Button(onClick = onAction) {
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
private fun RowCard(title: String, subtitle: String, artwork: Any?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
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
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Text(text = "$label: ${value.orEmpty().ifBlank { "Unknown" }}", style = MaterialTheme.typography.bodyLarge)
}

private fun formatDurationSeconds(durationSeconds: Int): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
