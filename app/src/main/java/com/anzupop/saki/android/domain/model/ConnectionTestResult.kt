package com.anzupop.saki.android.domain.model

data class ConnectionTestRequest(
    val endpointUrl: String,
    val username: String,
    val password: String,
    val clientName: String = DEFAULT_SUBSONIC_CLIENT,
    val apiVersion: String = DEFAULT_SUBSONIC_API_VERSION,
)

sealed interface ConnectionTestResult {
    val endpointUrl: String

    data class Success(
        override val endpointUrl: String,
        val serverVersion: String?,
    ) : ConnectionTestResult

    data class Failure(
        override val endpointUrl: String,
        val message: String,
    ) : ConnectionTestResult
}
