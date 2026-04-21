package com.anzupop.saki.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.DEFAULT_SUBSONIC_API_VERSION
import com.anzupop.saki.android.domain.model.DEFAULT_SUBSONIC_CLIENT
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import com.anzupop.saki.android.domain.repository.ServerConfigRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val EXCLUDED_SETTING_KEYS = setOf(
    "room_migration_done",
    DataStoreAppPreferencesRepository.KEY_ONBOARDING_COMPLETED.name,
)

@JsonClass(generateAdapter = true)
data class BackupData(
    val version: Int = 1,
    val servers: List<BackupServer>,
    val settings: Map<String, String> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class BackupServer(
    val name: String,
    val username: String,
    val password: String,
    val clientName: String? = null,
    val apiVersion: String? = null,
    val endpoints: List<BackupEndpoint>,
)

@JsonClass(generateAdapter = true)
data class BackupEndpoint(
    val label: String,
    val url: String,
    val isPrimary: Boolean? = null,
)

@Singleton
class ConfigBackupManager @Inject constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val dataStore: DataStore<Preferences>,
    private val moshi: Moshi,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun exportToJson(): String = withContext(ioDispatcher) {
        val servers = serverConfigRepository.observeServerConfigs().first()
        val prefs = dataStore.data.first()

        val backupServers = servers.map { server ->
            BackupServer(
                name = server.name,
                username = server.username,
                password = server.password,
                clientName = server.clientName,
                apiVersion = server.apiVersion,
                endpoints = server.endpoints
                    .sortedBy(ServerEndpoint::order)
                    .map { ep -> BackupEndpoint(label = ep.label, url = ep.baseUrl) },
            )
        }

        val settings = buildMap {
            prefs.asMap().forEach { (key, value) ->
                if (key.name !in EXCLUDED_SETTING_KEYS) {
                    put(key.name, value.toString())
                }
            }
        }

        val backup = BackupData(servers = backupServers, settings = settings)
        moshi.adapter(BackupData::class.java).indent("  ").toJson(backup)
    }

    suspend fun importFromJson(json: String): ImportResult = withContext(ioDispatcher) {
        val backup = try {
            moshi.adapter(BackupData::class.java).fromJson(json)
        } catch (_: Exception) {
            return@withContext ImportResult.InvalidFormat
        } ?: return@withContext ImportResult.InvalidFormat

        if (backup.version != 1) return@withContext ImportResult.UnsupportedVersion(backup.version)

        var serversImported = 0
        val existingServers = serverConfigRepository.observeServerConfigs().first()
        val seenKeys = existingServers.mapTo(mutableSetOf()) { it.name.trim() to it.username.trim() }

        for (backupServer in backup.servers) {
            val key = backupServer.name.trim() to backupServer.username.trim()
            if (key in seenKeys) continue
            seenKeys.add(key)

            val config = ServerConfig(
                name = backupServer.name.trim(),
                username = backupServer.username.trim(),
                password = backupServer.password,
                clientName = backupServer.clientName ?: DEFAULT_SUBSONIC_CLIENT,
                apiVersion = backupServer.apiVersion ?: DEFAULT_SUBSONIC_API_VERSION,
                endpoints = backupServer.endpoints.mapIndexed { index, ep ->
                    ServerEndpoint(label = ep.label, baseUrl = ep.url, order = index)
                },
            )
            serverConfigRepository.saveServerConfig(config)
            serversImported++
        }

        var settingsApplied = false
        if (backup.settings.isNotEmpty()) {
            dataStore.edit { ds ->
                backup.settings.forEach { (key, value) ->
                    when (key) {
                        DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB] = value.toIntOrNull() ?: return@forEach
                            settingsApplied = true
                        }
                        DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS] = value.toBooleanStrictOrNull() ?: return@forEach
                            settingsApplied = true
                        }
                        DataStorePlaybackPreferencesRepository.KEY_ADAPTIVE_QUALITY.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_ADAPTIVE_QUALITY] = value.toBooleanStrictOrNull() ?: return@forEach
                            settingsApplied = true
                        }
                        DataStoreAppPreferencesRepository.KEY_TEXT_SCALE.name -> {
                            ds[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE] = value; settingsApplied = true
                        }
                        DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY] = value; settingsApplied = true
                        }
                        DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE] = value; settingsApplied = true
                        }
                        DataStorePlaybackPreferencesRepository.KEY_WIFI_STREAM_QUALITY.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_WIFI_STREAM_QUALITY] = value; settingsApplied = true
                        }
                        DataStorePlaybackPreferencesRepository.KEY_MOBILE_STREAM_QUALITY.name -> {
                            ds[DataStorePlaybackPreferencesRepository.KEY_MOBILE_STREAM_QUALITY] = value; settingsApplied = true
                        }
                    }
                }
            }
        }

        ImportResult.Success(serversImported = serversImported, settingsRestored = settingsApplied)
    }
}

sealed interface ImportResult {
    data class Success(val serversImported: Int, val settingsRestored: Boolean) : ImportResult
    data object InvalidFormat : ImportResult
    data class UnsupportedVersion(val version: Int) : ImportResult
}
