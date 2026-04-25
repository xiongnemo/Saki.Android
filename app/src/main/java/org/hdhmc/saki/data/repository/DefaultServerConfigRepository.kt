package org.hdhmc.saki.data.repository

import org.hdhmc.saki.data.local.dao.ServerConfigDao
import org.hdhmc.saki.data.local.entity.ServerEndpointEntity
import org.hdhmc.saki.data.local.entity.ServerEntity
import org.hdhmc.saki.data.local.model.ServerWithEndpoints
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.DEFAULT_SUBSONIC_API_VERSION
import org.hdhmc.saki.domain.model.DEFAULT_SUBSONIC_CLIENT
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.ServerEndpoint
import org.hdhmc.saki.domain.repository.ServerConfigRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DefaultServerConfigRepository @Inject constructor(
    private val serverConfigDao: ServerConfigDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ServerConfigRepository {
    override fun observeServerConfigs(): Flow<List<ServerConfig>> {
        return serverConfigDao.observeServers()
            .map { servers -> servers.map(ServerWithEndpoints::toDomain) }
    }

    override suspend fun getServerConfig(serverId: Long): ServerConfig? = withContext(ioDispatcher) {
        serverConfigDao.getServerWithEndpoints(serverId)?.toDomain()
    }

    override suspend fun saveServerConfig(config: ServerConfig): Long = withContext(ioDispatcher) {
        val existing = if (config.id == 0L) {
            null
        } else {
            serverConfigDao.getServerWithEndpoints(config.id)?.server
        }
        val now = System.currentTimeMillis()
        val normalizedEndpoints = normalizeEndpoints(config.endpoints)
        val persistedServer = ServerEntity(
            id = config.id,
            name = config.name.trim(),
            username = config.username.trim(),
            password = config.password,
            clientName = config.clientName.ifBlank { DEFAULT_SUBSONIC_CLIENT }.trim(),
            apiVersion = config.apiVersion.ifBlank { DEFAULT_SUBSONIC_API_VERSION }.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        serverConfigDao.upsertServerWithEndpoints(
            server = persistedServer,
            endpoints = normalizedEndpoints.map { endpoint ->
                ServerEndpointEntity(
                    id = endpoint.id,
                    serverId = persistedServer.id,
                    label = endpoint.label,
                    baseUrl = endpoint.baseUrl,
                    displayOrder = endpoint.order,
                )
            },
        )
    }

    override suspend fun deleteServerConfig(serverId: Long): Unit = withContext(ioDispatcher) {
        serverConfigDao.deleteServerAndEndpoints(serverId)
    }

    private fun normalizeEndpoints(endpoints: List<ServerEndpoint>): List<ServerEndpoint> {
        return endpoints.mapIndexed { index, endpoint ->
            endpoint.copy(
                label = endpoint.label.ifBlank { "Endpoint ${index + 1}" }.trim(),
                baseUrl = endpoint.baseUrl.trim().trimEnd('/'),
                order = index,
            )
        }
    }
}

private fun ServerWithEndpoints.toDomain(): ServerConfig {
    return ServerConfig(
        id = server.id,
        name = server.name,
        username = server.username,
        password = server.password,
        clientName = server.clientName,
        apiVersion = server.apiVersion,
        endpoints = endpoints
            .sortedBy(ServerEndpointEntity::displayOrder)
            .map { endpoint ->
                ServerEndpoint(
                    id = endpoint.id,
                    label = endpoint.label,
                    baseUrl = endpoint.baseUrl,
                    order = endpoint.displayOrder,
                )
            },
        createdAt = server.createdAt,
        updatedAt = server.updatedAt,
    )
}
