package com.anzupop.saki.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import com.anzupop.saki.android.domain.repository.ServerConfigRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

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
    val isPrimary: Boolean = false,
)

@Singleton
class ConfigBackupManager @Inject constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val dataStore: DataStore<Preferences>,
    private val moshi: Moshi,
) {
    suspend fun exportToJson(): String {
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
                    .sortedWith(compareByDescending<ServerEndpoint> { it.isPrimary }.thenBy { it.order })
                    .map { ep ->
                        BackupEndpoint(label = ep.label, url = ep.baseUrl, isPrimary = ep.isPrimary)
                    },
            )
        }

        val settings = buildMap {
            prefs.asMap().forEach { (key, value) ->
                // Skip migration flag and onboarding state
                if (key.name != "room_migration_done" && key.name != "onboarding_completed") {
                    put(key.name, value.toString())
                }
            }
        }

        val backup = BackupData(servers = backupServers, settings = settings)
        return moshi.adapter(BackupData::class.java).indent("  ").toJson(backup)
    }

    suspend fun importFromJson(json: String): ImportResult {
        val backup = try {
            moshi.adapter(BackupData::class.java).fromJson(json)
        } catch (_: Exception) {
            return ImportResult.InvalidFormat
        } ?: return ImportResult.InvalidFormat

        if (backup.version != 1) return ImportResult.UnsupportedVersion(backup.version)

        var serversImported = 0
        val existingServers = serverConfigRepository.observeServerConfigs().first()

        for (backupServer in backup.servers) {
            val isDuplicate = existingServers.any { existing ->
                existing.name == backupServer.name && existing.username == backupServer.username
            }
            if (isDuplicate) continue

            val config = ServerConfig(
                name = backupServer.name,
                username = backupServer.username,
                password = backupServer.password,
                clientName = backupServer.clientName ?: "Saki.Android",
                apiVersion = backupServer.apiVersion ?: "1.16.1",
                endpoints = backupServer.endpoints.mapIndexed { index, ep ->
                    ServerEndpoint(label = ep.label, baseUrl = ep.url, isPrimary = ep.isPrimary, order = index)
                },
            )
            serverConfigRepository.saveServerConfig(config)
            serversImported++
        }

        // Restore settings
        if (backup.settings.isNotEmpty()) {
            dataStore.edit { ds ->
                backup.settings.forEach { (key, value) ->
                    when {
                        key == DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB.name ->
                            ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_CACHE_SIZE_MB] = value.toIntOrNull() ?: return@forEach
                        key == DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS.name ->
                            ds[DataStorePlaybackPreferencesRepository.KEY_BLUETOOTH_LYRICS] = value.toBooleanStrictOrNull() ?: return@forEach
                        key == DataStoreAppPreferencesRepository.KEY_TEXT_SCALE.name ->
                            ds[DataStoreAppPreferencesRepository.KEY_TEXT_SCALE] = value
                        key == DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY.name ->
                            ds[DataStorePlaybackPreferencesRepository.KEY_STREAM_QUALITY] = value
                        key == DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE.name ->
                            ds[DataStorePlaybackPreferencesRepository.KEY_SOUND_BALANCING_MODE] = value
                    }
                }
            }
        }

        return ImportResult.Success(serversImported = serversImported, settingsRestored = backup.settings.isNotEmpty())
    }
}

sealed interface ImportResult {
    data class Success(val serversImported: Int, val settingsRestored: Boolean) : ImportResult
    data object InvalidFormat : ImportResult
    data class UnsupportedVersion(val version: Int) : ImportResult
}
