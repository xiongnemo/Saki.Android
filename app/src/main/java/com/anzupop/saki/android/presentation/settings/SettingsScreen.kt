package com.anzupop.saki.android.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anzupop.saki.android.domain.model.CachedSong
import com.anzupop.saki.android.domain.model.MAX_STREAM_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.MIN_STREAM_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.BufferStrategy
import com.anzupop.saki.android.domain.model.CUSTOM_BUFFER_STEP_SECONDS
import com.anzupop.saki.android.domain.model.MAX_CUSTOM_BUFFER_SECONDS
import com.anzupop.saki.android.domain.model.MAX_IMAGE_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.IMAGE_CACHE_SIZE_STEP_MB
import com.anzupop.saki.android.domain.model.MIN_IMAGE_CACHE_SIZE_MB
import com.anzupop.saki.android.domain.model.MIN_CUSTOM_BUFFER_SECONDS
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.SoundBalancingMode
import com.anzupop.saki.android.domain.model.STREAM_CACHE_SIZE_STEP_MB
import com.anzupop.saki.android.domain.model.StreamQuality
import com.anzupop.saki.android.domain.model.TextScale
import com.anzupop.saki.android.presentation.SakiAppUiState
import com.anzupop.saki.android.presentation.library.ArtworkCard
import com.anzupop.saki.android.presentation.library.resolveArtworkModel
import com.anzupop.saki.android.presentation.rememberBrowseBackgroundBrush
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SakiAppUiState,
    contentPadding: PaddingValues,
    onManageServers: () -> Unit,
    onSelectServer: (Long) -> Unit,
    onUpdateStreamQuality: (StreamQuality) -> Unit,
    onUpdateAdaptiveQuality: (Boolean) -> Unit,
    onUpdateWifiStreamQuality: (StreamQuality) -> Unit,
    onUpdateMobileStreamQuality: (StreamQuality) -> Unit,
    onUpdateSoundBalancing: (SoundBalancingMode) -> Unit,
    onUpdateStreamCacheSizeMb: (Int) -> Unit,
    onClearStreamCache: () -> Unit,
    onUpdateImageCacheSizeMb: (Int) -> Unit,
    onClearImageCache: () -> Unit,
    onUpdateTextScale: (TextScale) -> Unit,
    onReplayOnboarding: () -> Unit,
    onUpdateBluetoothLyrics: (Boolean) -> Unit,
    onUpdateBufferStrategy: (BufferStrategy) -> Unit,
    onUpdateCustomBufferSeconds: (Int) -> Unit,
    onExportConfig: (android.net.Uri) -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
    onPlayCachedSong: (CachedSong) -> Unit,
    onPlayCachedQueue: (List<CachedSong>, Int) -> Unit,
    onDeleteCachedSong: (String) -> Unit,
    onClearCachedSongs: () -> Unit,
) {
    val background = rememberBrowseBackgroundBrush()
    val selectedServer = uiState.servers.firstOrNull { it.id == uiState.selectedServerId }
    val visibleCachedSongs = uiState.cachedSongs.filter { song ->
        uiState.selectedServerId == null || song.serverId == uiState.selectedServerId
    }
    val storageSummary = uiState.cacheStorageSummary
    val configuredStreamCacheSizeMb = uiState.playbackState.preferences.streamCacheSizeMb
    var streamCacheSliderValue by remember(configuredStreamCacheSizeMb) {
        mutableFloatStateOf(configuredStreamCacheSizeMb.toFloat())
    }
    val configuredImageCacheSizeMb = uiState.playbackState.preferences.imageCacheSizeMb
    var imageCacheSliderValue by remember(configuredImageCacheSizeMb) {
        mutableFloatStateOf(configuredImageCacheSizeMb.toFloat())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
            ) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Settings", style = MaterialTheme.typography.displaySmall)
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    }
                    Text(
                        text = "Manage servers, text size, streaming quality, sound balancing, offline downloads, and onboarding.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Server profiles",
                body = "Pick the active server and open the full profile editor when you need it.",
                action = null,
            ) {
                if (uiState.servers.isEmpty()) {
                    Text(
                        text = "No servers configured yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.servers.forEach { server ->
                        ServerRow(
                            server = server,
                            selected = server.id == uiState.selectedServerId,
                            onClick = { onSelectServer(server.id) },
                        )
                    }
                }
                FilledTonalButton(onClick = onManageServers) {
                    Icon(Icons.Rounded.WifiTethering, contentDescription = null)
                    Text("Open server manager", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            val prefs = uiState.playbackState.preferences
            SettingsSectionCard(
                title = "Stream quality",
                body = "Limit streaming bitrate or keep the original source file when possible.",
                action = null,
            ) {
                if (!prefs.adaptiveQualityEnabled) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.streamQuality == quality,
                                onClick = { onUpdateStreamQuality(quality) },
                                label = { Text(quality.label) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("Adaptive quality", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = prefs.adaptiveQualityEnabled,
                        onCheckedChange = onUpdateAdaptiveQuality,
                    )
                }
                if (prefs.adaptiveQualityEnabled) {
                    Text("WiFi", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.wifiStreamQuality == quality,
                                onClick = { onUpdateWifiStreamQuality(quality) },
                                label = { Text(quality.label) },
                            )
                        }
                    }
                    Text("Mobile", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.mobileStreamQuality == quality,
                                onClick = { onUpdateMobileStreamQuality(quality) },
                                label = { Text(quality.label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Sound balancing",
                body = "Reduces perceived volume jumps across tracks using Android's audio session effects. Device support and results vary.",
                action = null,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoundBalancingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.playbackState.preferences.soundBalancingMode == mode,
                            onClick = { onUpdateSoundBalancing(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        item {
            val prefs = uiState.playbackState.preferences
            val configuredSeconds = prefs.customBufferSeconds
            var bufferSliderValue by remember(configuredSeconds) {
                mutableFloatStateOf(configuredSeconds.toFloat())
            }
            SettingsSectionCard(
                title = "Buffer strategy",
                body = "Control how much of the current track is buffered ahead during playback.",
                action = null,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BufferStrategy.entries.forEach { strategy ->
                        FilterChip(
                            selected = prefs.bufferStrategy == strategy,
                            onClick = { onUpdateBufferStrategy(strategy) },
                            label = { Text(strategy.label) },
                        )
                    }
                }
                when (prefs.bufferStrategy) {
                    BufferStrategy.NORMAL -> Text(
                        text = "Default buffering (~50 s ahead). Balances memory use and playback reliability.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BufferStrategy.AGGRESSIVE -> Text(
                        text = "Buffers the entire current track. Best on stable WiFi for instant seeking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BufferStrategy.CUSTOM -> {
                        Text(
                            text = "Buffer ${bufferSliderValue.toBufferSeconds()} s ahead",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Slider(
                            value = bufferSliderValue,
                            onValueChange = { bufferSliderValue = it },
                            valueRange = MIN_CUSTOM_BUFFER_SECONDS.toFloat()..MAX_CUSTOM_BUFFER_SECONDS.toFloat(),
                            steps = ((MAX_CUSTOM_BUFFER_SECONDS - MIN_CUSTOM_BUFFER_SECONDS) / CUSTOM_BUFFER_STEP_SECONDS) - 1,
                            onValueChangeFinished = {
                                val newSeconds = bufferSliderValue.toBufferSeconds()
                                bufferSliderValue = newSeconds.toFloat()
                                if (newSeconds != configuredSeconds) {
                                    onUpdateCustomBufferSeconds(newSeconds)
                                }
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${MIN_CUSTOM_BUFFER_SECONDS} s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${MAX_CUSTOM_BUFFER_SECONDS} s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = "Changes take effect after restarting the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSectionCard(
                title = "Text size",
                body = "Scale the app typography without changing the rest of the system UI.",
                action = null,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextScale.entries.forEach { textScale ->
                        FilterChip(
                            selected = uiState.textScale == textScale,
                            onClick = { onUpdateTextScale(textScale) },
                            label = { Text(textScale.label) },
                            leadingIcon = {
                                Icon(Icons.Rounded.TextFields, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Streaming cache",
                body = buildString {
                    append("${storageSummary.streamCachedSongCount} stream-cached track")
                    if (storageSummary.streamCachedSongCount != 1) append("s")
                    if (selectedServer != null) append(" on ${selectedServer.name}")
                },
                action = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(storageSummary.streamCacheBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "/ ${formatStorageSize(configuredStreamCacheSizeMb.toLong() * 1024L * 1024L)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        val limit = configuredStreamCacheSizeMb.toLong() * 1024L * 1024L
                        if (limit <= 0L) 0f
                        else (storageSummary.streamCacheBytes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = streamCacheSliderValue,
                    onValueChange = { streamCacheSliderValue = it },
                    valueRange = MIN_STREAM_CACHE_SIZE_MB.toFloat()..MAX_STREAM_CACHE_SIZE_MB.toFloat(),
                    steps = STREAM_CACHE_SLIDER_STEPS,
                    onValueChangeFinished = {
                        val newSizeMb = streamCacheSliderValue.toStreamCacheSizeMb()
                        streamCacheSliderValue = newSizeMb.toFloat()
                        if (newSizeMb != configuredStreamCacheSizeMb) {
                            onUpdateStreamCacheSizeMb(newSizeMb)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(MIN_STREAM_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatStorageSize(MAX_STREAM_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onClearStreamCache,
                    enabled = storageSummary.streamCacheBytes > 0L,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text("Clear stream cache", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Cover art cache",
                body = "Album artwork cached across all servers.",
                action = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(storageSummary.imageCacheBytes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "/ ${formatStorageSize(configuredImageCacheSizeMb.toLong() * 1024L * 1024L)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        val limit = configuredImageCacheSizeMb.toLong() * 1024L * 1024L
                        if (limit <= 0L) 0f
                        else (storageSummary.imageCacheBytes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = imageCacheSliderValue,
                    onValueChange = { imageCacheSliderValue = it },
                    valueRange = MIN_IMAGE_CACHE_SIZE_MB.toFloat()..MAX_IMAGE_CACHE_SIZE_MB.toFloat(),
                    steps = ((MAX_IMAGE_CACHE_SIZE_MB - MIN_IMAGE_CACHE_SIZE_MB) / IMAGE_CACHE_SIZE_STEP_MB) - 1,
                    onValueChangeFinished = {
                        val newSizeMb = imageCacheSliderValue.toImageCacheSizeMb()
                        imageCacheSliderValue = newSizeMb.toFloat()
                        if (newSizeMb != configuredImageCacheSizeMb) {
                            onUpdateImageCacheSizeMb(newSizeMb)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatStorageSize(MIN_IMAGE_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatStorageSize(MAX_IMAGE_CACHE_SIZE_MB.toLong() * 1024L * 1024L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onClearImageCache,
                    enabled = storageSummary.imageCacheBytes > 0L,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text("Clear cover art cache", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Downloads",
                body = buildString {
                    append("${storageSummary.downloadedSongCount} download")
                    if (storageSummary.downloadedSongCount != 1) append("s")
                    append(" • ${formatStorageSize(storageSummary.downloadedBytes)}")
                    if (selectedServer != null) append(" on ${selectedServer.name}")
                },
                action = null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visibleCachedSongs.isNotEmpty()) {
                        OutlinedButton(onClick = { onPlayCachedQueue(visibleCachedSongs, 0) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("Play all", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = onClearCachedSongs,
                        enabled = visibleCachedSongs.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Text("Clear all", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (visibleCachedSongs.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Text(
                            text = "No downloaded tracks yet.",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    visibleCachedSongs.forEachIndexed { index, song ->
                        CachedSongRow(
                            song = song,
                            server = selectedServer ?: uiState.servers.firstOrNull { it.id == song.serverId },
                            onPlay = { onPlayCachedSong(song) },
                            onDelete = { onDeleteCachedSong(song.cacheId) },
                            onPlayFromHere = { onPlayCachedQueue(visibleCachedSongs, index) },
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Experimental",
                body = "Features that may not work on all devices.",
                action = null,
            ) {
                val checked = uiState.playbackState.preferences.bluetoothLyricsEnabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = onUpdateBluetoothLyrics,
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bluetooth / notification lyrics", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show current lyric line in media notification, lock screen, and Bluetooth devices (car stereos, headphones).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = null,
                    )
                }
            }
        }

        item {
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> if (uri != null) onExportConfig(uri) }
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) onImportConfig(uri) }
            SettingsSectionCard(
                title = "Backup & Restore",
                body = "Export or import server configuration and settings. Warning: exported files contain server passwords in plaintext.",
                action = null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("saki-backup.json") }) {
                        Icon(Icons.Rounded.Upload, contentDescription = null)
                        Text("Export", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text("Import", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "Onboarding",
                body = "Run the first-time setup flow again if you want to revisit the intro and setup steps.",
                action = null,
            ) {
                Row {
                    Button(onClick = onReplayOnboarding) {
                        Icon(Icons.Rounded.Storage, contentDescription = null)
                        Text("Run onboarding again", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    body: String,
    action: (@Composable (() -> Unit))?,
    content: @Composable () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerConfig,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = server.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${server.username} • ${server.endpoints.size} endpoint${if (server.endpoints.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CachedSongRow(
    song: CachedSong,
    server: ServerConfig?,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPlayFromHere: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onPlay),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkCard(
            model = resolveArtworkModel(server, song.coverArtId, song),
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
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • ").ifBlank { song.quality.label },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlayFromHere) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play from here")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove download")
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return if (value >= 100 || unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

private const val STREAM_CACHE_SLIDER_STEPS =
    ((MAX_STREAM_CACHE_SIZE_MB - MIN_STREAM_CACHE_SIZE_MB) / STREAM_CACHE_SIZE_STEP_MB) - 1

private fun Float.toStreamCacheSizeMb(): Int {
    val stepsFromMin = ((this - MIN_STREAM_CACHE_SIZE_MB) / STREAM_CACHE_SIZE_STEP_MB).roundToInt()
    return (MIN_STREAM_CACHE_SIZE_MB + (stepsFromMin * STREAM_CACHE_SIZE_STEP_MB))
        .coerceIn(MIN_STREAM_CACHE_SIZE_MB, MAX_STREAM_CACHE_SIZE_MB)
}

private fun Float.toBufferSeconds(): Int {
    val stepsFromMin = ((this - MIN_CUSTOM_BUFFER_SECONDS) / CUSTOM_BUFFER_STEP_SECONDS).roundToInt()
    return (MIN_CUSTOM_BUFFER_SECONDS + (stepsFromMin * CUSTOM_BUFFER_STEP_SECONDS))
        .coerceIn(MIN_CUSTOM_BUFFER_SECONDS, MAX_CUSTOM_BUFFER_SECONDS)
}

private fun Float.toImageCacheSizeMb(): Int {
    val stepsFromMin = ((this - MIN_IMAGE_CACHE_SIZE_MB) / IMAGE_CACHE_SIZE_STEP_MB).roundToInt()
    return (MIN_IMAGE_CACHE_SIZE_MB + (stepsFromMin * IMAGE_CACHE_SIZE_STEP_MB))
        .coerceIn(MIN_IMAGE_CACHE_SIZE_MB, MAX_IMAGE_CACHE_SIZE_MB)
}
