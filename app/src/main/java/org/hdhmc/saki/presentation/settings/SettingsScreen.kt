package org.hdhmc.saki.presentation.settings

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.domain.model.MAX_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.MIN_STREAM_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.BufferStrategy
import org.hdhmc.saki.domain.model.CUSTOM_BUFFER_STEP_SECONDS
import org.hdhmc.saki.domain.model.MAX_CUSTOM_BUFFER_SECONDS
import org.hdhmc.saki.domain.model.MAX_IMAGE_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.IMAGE_CACHE_SIZE_STEP_MB
import org.hdhmc.saki.domain.model.MIN_IMAGE_CACHE_SIZE_MB
import org.hdhmc.saki.domain.model.MIN_CUSTOM_BUFFER_SECONDS
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.STREAM_CACHE_SIZE_STEP_MB
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.presentation.SakiAppUiState
import org.hdhmc.saki.presentation.labelRes
import org.hdhmc.saki.presentation.library.ArtworkCard
import org.hdhmc.saki.presentation.library.resolveArtworkModel
import org.hdhmc.saki.presentation.rememberBrowseBackgroundBrush
import java.util.Locale
import kotlin.math.roundToInt

private val defaultAlbumFeedOptions = listOf(
    AlbumListType.NEWEST,
    AlbumListType.RECENT,
    AlbumListType.RANDOM,
    AlbumListType.HIGHEST,
    AlbumListType.FREQUENT,
    AlbumListType.ALPHABETICAL_BY_NAME,
    AlbumListType.ALPHABETICAL_BY_ARTIST,
    AlbumListType.STARRED,
)

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
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateDefaultBrowseTab: (DefaultBrowseTab) -> Unit,
    onUpdateDefaultAlbumFeed: (AlbumListType) -> Unit,
    onUpdateBluetoothLyrics: (Boolean) -> Unit,
    onUpdateBufferStrategy: (BufferStrategy) -> Unit,
    onUpdateCustomBufferSeconds: (Int) -> Unit,
    onExportConfig: (android.net.Uri) -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
    onPlayCachedSong: (CachedSong) -> Unit,
    onPlayCachedQueue: (List<CachedSong>, Int) -> Unit,
    onDeleteCachedSong: (String) -> Unit,
    onClearCachedSongs: () -> Unit,
    onUpdateDownloadQuality: (StreamQuality) -> Unit,
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
                        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall)
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    }
                    Text(
                        text = stringResource(R.string.settings_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_server_profiles_title),
                body = stringResource(R.string.settings_server_profiles_body),
                action = null,
            ) {
                if (uiState.servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_servers),
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
                FilledTonalButton(onClick = onManageServers, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.WifiTethering, contentDescription = null)
                    Text(stringResource(R.string.settings_open_server_manager), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            val prefs = uiState.playbackState.preferences
            SettingsSectionCard(
                title = stringResource(R.string.settings_stream_quality_title),
                body = stringResource(R.string.settings_stream_quality_body),
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
                                label = { Text(quality.localizedLabel()) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_adaptive_quality), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = prefs.adaptiveQualityEnabled,
                        onCheckedChange = onUpdateAdaptiveQuality,
                    )
                }
                if (prefs.adaptiveQualityEnabled) {
                    Text(stringResource(R.string.settings_wifi), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.wifiStreamQuality == quality,
                                onClick = { onUpdateWifiStreamQuality(quality) },
                                label = { Text(quality.localizedLabel()) },
                            )
                        }
                    }
                    Text(stringResource(R.string.settings_mobile), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StreamQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = prefs.mobileStreamQuality == quality,
                                onClick = { onUpdateMobileStreamQuality(quality) },
                                label = { Text(quality.localizedLabel()) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_sound_balancing_title),
                body = stringResource(R.string.settings_sound_balancing_body),
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
                            label = { Text(mode.localizedLabel()) },
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
                title = stringResource(R.string.settings_buffer_strategy_title),
                body = stringResource(R.string.settings_buffer_strategy_body),
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
                            label = { Text(strategy.localizedLabel()) },
                        )
                    }
                }
                when (prefs.bufferStrategy) {
                    BufferStrategy.NORMAL -> Text(
                        text = stringResource(R.string.settings_buffer_strategy_normal_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BufferStrategy.AGGRESSIVE -> Text(
                        text = stringResource(R.string.settings_buffer_strategy_aggressive_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BufferStrategy.CUSTOM -> {
                        Text(
                            text = stringResource(R.string.settings_buffer_ahead_seconds, bufferSliderValue.toBufferSeconds()),
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
                                text = stringResource(R.string.settings_seconds_short, MIN_CUSTOM_BUFFER_SECONDS),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.settings_seconds_short, MAX_CUSTOM_BUFFER_SECONDS),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_restart_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_text_size_title),
                body = stringResource(R.string.settings_text_size_body),
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
                            label = { Text(textScale.localizedLabel()) },
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
                title = stringResource(R.string.settings_language_title),
                body = stringResource(R.string.settings_language_body),
                action = null,
            ) {
                val currentLanguage = uiState.appPreferences.language
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LanguageChip(
                        label = stringResource(R.string.settings_language_system),
                        selected = currentLanguage == AppLanguage.SYSTEM,
                        coverage = null,
                        onClick = { onUpdateLanguage(AppLanguage.SYSTEM) },
                    )
                    LanguageChip(
                        label = stringResource(R.string.settings_language_english),
                        selected = currentLanguage == AppLanguage.ENGLISH,
                        coverage = null,
                        onClick = { onUpdateLanguage(AppLanguage.ENGLISH) },
                    )
                    LanguageChip(
                        label = stringResource(R.string.settings_language_chinese),
                        selected = currentLanguage == AppLanguage.CHINESE,
                        coverage = translationCoverage("zh"),
                        onClick = { onUpdateLanguage(AppLanguage.CHINESE) },
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_theme_title),
                body = stringResource(R.string.settings_theme_body),
                action = null,
            ) {
                val currentTheme = uiState.appPreferences.themeMode
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { onUpdateThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.settings_theme_system)) },
                    )
                    FilterChip(
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onUpdateThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.settings_theme_light)) },
                    )
                    FilterChip(
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onUpdateThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.settings_theme_dark)) },
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_default_browse_title),
                body = stringResource(R.string.settings_default_browse_body),
                action = null,
            ) {
                val defaultBrowseTab = uiState.appPreferences.defaultBrowseTab
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DefaultBrowseTab.entries.forEach { tab ->
                        FilterChip(
                            selected = defaultBrowseTab == tab,
                            onClick = { onUpdateDefaultBrowseTab(tab) },
                            label = { Text(stringResource(tab.labelRes())) },
                        )
                    }
                }
                if (defaultBrowseTab == DefaultBrowseTab.ALBUMS) {
                    Text(
                        text = stringResource(R.string.settings_default_album_feed),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        defaultAlbumFeedOptions.forEach { feed ->
                            FilterChip(
                                selected = uiState.appPreferences.defaultAlbumFeed == feed,
                                onClick = { onUpdateDefaultAlbumFeed(feed) },
                                label = { Text(stringResource(feed.labelRes())) },
                            )
                        }
                    }
                }
            }
        }

        item {
            val streamCacheCount = pluralStringResource(
                R.plurals.settings_stream_cached_track_count,
                storageSummary.streamCachedSongCount,
                storageSummary.streamCachedSongCount,
            )
            val streamCacheBody = if (selectedServer != null) {
                stringResource(R.string.settings_cache_count_on_server, streamCacheCount, selectedServer.name)
            } else {
                streamCacheCount
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_streaming_cache_title),
                body = streamCacheBody,
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
                        text = stringResource(
                            R.string.settings_cache_limit,
                            formatStorageSize(configuredStreamCacheSizeMb.toLong() * 1024L * 1024L),
                        ),
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
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text(stringResource(R.string.settings_clear_stream_cache), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.settings_cover_art_cache_title),
                body = stringResource(R.string.settings_cover_art_cache_body),
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
                        text = stringResource(
                            R.string.settings_cache_limit,
                            formatStorageSize(configuredImageCacheSizeMb.toLong() * 1024L * 1024L),
                        ),
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
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text(stringResource(R.string.settings_clear_cover_art_cache), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            val downloadCount = pluralStringResource(
                R.plurals.settings_download_count,
                storageSummary.downloadedSongCount,
                storageSummary.downloadedSongCount,
            )
            val downloadsBody = stringResource(
                R.string.settings_downloads_body,
                downloadCount,
                formatStorageSize(storageSummary.downloadedBytes),
            )
            val downloadsBodyWithServer = if (selectedServer != null) {
                stringResource(R.string.settings_cache_count_on_server, downloadsBody, selectedServer.name)
            } else {
                downloadsBody
            }
            SettingsSectionCard(
                title = stringResource(R.string.settings_downloads_title),
                body = downloadsBodyWithServer,
                action = null,
            ) {
                Text(
                    text = stringResource(R.string.settings_download_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val currentDownloadQuality = uiState.playbackState.preferences.downloadQuality
                    StreamQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = currentDownloadQuality == quality,
                            onClick = { onUpdateDownloadQuality(quality) },
                            label = { Text(quality.localizedLabel()) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visibleCachedSongs.isNotEmpty()) {
                        OutlinedButton(onClick = { onPlayCachedQueue(visibleCachedSongs, 0) }, shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text(stringResource(R.string.settings_play_all), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = onClearCachedSongs,
                        enabled = visibleCachedSongs.isNotEmpty(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Text(stringResource(R.string.settings_clear_all), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (visibleCachedSongs.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Text(
                            text = stringResource(R.string.settings_no_downloaded_tracks),
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
                title = stringResource(R.string.settings_experimental_title),
                body = stringResource(R.string.settings_experimental_body),
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
                        Text(stringResource(R.string.settings_bluetooth_lyrics_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_bluetooth_lyrics_body),
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
                title = stringResource(R.string.settings_backup_restore_title),
                body = stringResource(R.string.settings_backup_restore_body),
                action = null,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("saki-backup.json") }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.Upload, contentDescription = null)
                        Text(stringResource(R.string.settings_export), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text(stringResource(R.string.settings_import), modifier = Modifier.padding(start = 8.dp))
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
                text = "${server.username} • ${
                    pluralStringResource(
                        R.plurals.settings_endpoint_count,
                        server.endpoints.size,
                        server.endpoints.size,
                    )
                }",
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
    val qualityLabel = song.quality.localizedLabel()

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
                text = listOfNotNull(song.artist, song.album).joinToString(" • ").ifBlank { qualityLabel },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlayFromHere) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.settings_play_from_here))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.settings_remove_download))
        }
    }
}

@Composable
private fun StreamQuality.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun SoundBalancingMode.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun BufferStrategy.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun TextScale.localizedLabel(): String = stringResource(labelRes())

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    coverage: Int?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (coverage != null) {
                Text("$label (${stringResource(R.string.settings_language_coverage, coverage)})")
            } else {
                Text(label)
            }
        },
    )
}

@Composable
private fun translationCoverage(locale: String): Int {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(locale) {
        val res = context.resources
        val enConfig = android.content.res.Configuration(res.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        }
        val enRes = context.createConfigurationContext(enConfig).resources
        val localeConfig = android.content.res.Configuration(res.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(locale))
        }
        val localizedRes = context.createConfigurationContext(localeConfig).resources

        val fields = R.string::class.java.fields
        var total = 0
        var translated = 0
        for (field in fields) {
            val id = field.getInt(null)
            if (enRes.getString(id) != localizedRes.getString(id)) translated++
            total++
        }
        if (total > 0) (translated * 100) / total else 0
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
