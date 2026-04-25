package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerConfigRepository {
    fun observeServerConfigs(): Flow<List<ServerConfig>>

    suspend fun getServerConfig(serverId: Long): ServerConfig?

    suspend fun saveServerConfig(config: ServerConfig): Long

    suspend fun deleteServerConfig(serverId: Long)
}
