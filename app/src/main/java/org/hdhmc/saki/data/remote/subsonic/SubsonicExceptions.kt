package org.hdhmc.saki.data.remote.subsonic

import org.hdhmc.saki.domain.model.ServerEndpoint
import java.io.IOException

class NoSuchServerException(serverId: Long) : IllegalArgumentException(
    "No saved server configuration found for id=$serverId.",
)

class NoEndpointsConfiguredException(serverId: Long) : IllegalStateException(
    "Server id=$serverId does not have any endpoints configured.",
)

class SubsonicApiException(
    val endpoint: ServerEndpoint,
    val code: Int?,
    override val message: String,
) : IOException(message)

class SubsonicHttpException(
    val endpoint: ServerEndpoint,
    val statusCode: Int,
    override val message: String,
) : IOException(message)

class EndpointFallbackException(
    val failures: List<EndpointFailure>,
) : IOException(
    buildString {
        append("All configured endpoints failed.")
        if (failures.isNotEmpty()) {
            append(' ')
            append(
                failures.joinToString(separator = " | ") { failure ->
                    "${failure.endpoint.label}: ${failure.cause.javaClass.simpleName}: ${failure.cause.message}"
                },
            )
        }
    },
)

data class EndpointFailure(
    val endpoint: ServerEndpoint,
    val cause: IOException,
)
