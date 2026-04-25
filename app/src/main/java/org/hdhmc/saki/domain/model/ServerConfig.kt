package org.hdhmc.saki.domain.model

const val DEFAULT_SUBSONIC_CLIENT = "Saki.Android"
const val DEFAULT_SUBSONIC_API_VERSION = "1.16.1"

data class ServerConfig(
    val id: Long = 0,
    val name: String,
    val username: String,
    val password: String,
    val clientName: String = DEFAULT_SUBSONIC_CLIENT,
    val apiVersion: String = DEFAULT_SUBSONIC_API_VERSION,
    val endpoints: List<ServerEndpoint>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class ServerEndpoint(
    val id: Long = 0,
    val label: String,
    val baseUrl: String,
    val order: Int = 0,
)
