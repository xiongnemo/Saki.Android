package com.anzupop.saki.android.domain.repository

import com.anzupop.saki.android.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerConfigRepository {
    fun observeServerConfigs(): Flow<List<ServerConfig>>

    suspend fun getServerConfig(serverId: Long): ServerConfig?

    suspend fun saveServerConfig(config: ServerConfig): Long

    suspend fun deleteServerConfig(serverId: Long)
}
