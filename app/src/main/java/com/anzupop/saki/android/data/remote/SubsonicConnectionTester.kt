package com.anzupop.saki.android.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.ConnectionTestRequest
import com.anzupop.saki.android.domain.model.ConnectionTestResult
import com.anzupop.saki.android.domain.model.DEFAULT_SUBSONIC_API_VERSION
import com.anzupop.saki.android.domain.model.DEFAULT_SUBSONIC_CLIENT
import com.anzupop.saki.android.domain.repository.ServerConnectionTester
import java.io.IOException
import java.net.ConnectException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class SubsonicConnectionTester @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ServerConnectionTester {
    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun testConnection(request: ConnectionTestRequest): ConnectionTestResult = withContext(ioDispatcher) {
        val endpointUrl = request.endpointUrl.trim().trimEnd('/')
        val baseUrl = endpointUrl.toHttpUrlOrNull()
            ?: return@withContext ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "Enter a valid http:// or https:// URL.",
            )
        val networkSnapshot = createNetworkSnapshot()
        if (!networkSnapshot.hasInternetPermission) {
            return@withContext ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "The app does not have INTERNET permission.${networkSnapshot.debugSuffix}",
            )
        }

        val salt = UUID.randomUUID().toString().replace("-", "").take(8)
        val token = md5("${request.password}$salt")
        val pingUrl = baseUrl.newBuilder()
            .addPathSegments("rest/ping.view")
            .addQueryParameter("u", request.username.trim())
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", request.apiVersion.ifBlank { DEFAULT_SUBSONIC_API_VERSION })
            .addQueryParameter("c", request.clientName.ifBlank { DEFAULT_SUBSONIC_CLIENT })
            .addQueryParameter("f", "json")
            .build()

        val networkRequest = Request.Builder()
            .url(pingUrl)
            .get()
            .build()
        val requestClient = createRequestClient(networkSnapshot.activeNetwork)
        val resolvedAddresses = resolveHost(
            host = baseUrl.host,
            activeNetwork = networkSnapshot.activeNetwork,
            fallbackDns = requestClient.dns,
        )

        try {
            val startNanos = System.nanoTime()
            requestClient.newCall(networkRequest).execute().use { response ->
                val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
                if (!response.isSuccessful) {
                    return@withContext ConnectionTestResult.Failure(
                        endpointUrl = endpointUrl,
                        message = buildHttpFailureMessage(
                            host = baseUrl.host,
                            port = baseUrl.port,
                            responseCode = response.code,
                            resolvedAddresses = resolvedAddresses,
                            networkSnapshot = networkSnapshot,
                        ),
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext ConnectionTestResult.Failure(
                        endpointUrl = endpointUrl,
                        message = "The server returned an empty response.",
                    )
                val subsonicResponse = JSONObject(body).optJSONObject("subsonic-response")
                    ?: return@withContext ConnectionTestResult.Failure(
                        endpointUrl = endpointUrl,
                        message = "Unexpected response from the server.",
                    )

                if (subsonicResponse.optString("status") == "ok") {
                    return@withContext ConnectionTestResult.Success(
                        endpointUrl = endpointUrl,
                        serverVersion = subsonicResponse.optString("version").ifBlank { null },
                        latencyMs = latencyMs,
                    )
                }

                val errorMessage = subsonicResponse.optJSONObject("error")
                    ?.optString("message")
                    ?.ifBlank { null }
                    ?: "The server rejected the Subsonic credentials."

                ConnectionTestResult.Failure(
                    endpointUrl = endpointUrl,
                    message = errorMessage,
                )
            }
        } catch (exception: UnknownHostException) {
            ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "DNS lookup failed for ${baseUrl.host}: ${exception.message ?: "No address associated with hostname."}${resolvedAddresses.asDebugSuffix()}${networkSnapshot.debugSuffix}",
            )
        } catch (exception: ConnectException) {
            ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = buildConnectFailureMessage(
                    host = baseUrl.host,
                    port = baseUrl.port,
                    resolvedAddresses = resolvedAddresses,
                    details = exception.message,
                    networkSnapshot = networkSnapshot,
                ),
            )
        } catch (exception: SocketTimeoutException) {
            ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "Timed out while contacting ${baseUrl.host}:${baseUrl.port}.${resolvedAddresses.asDebugSuffix()}${networkSnapshot.debugSuffix}",
            )
        } catch (exception: SSLException) {
            ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "TLS handshake failed for ${baseUrl.host}:${baseUrl.port}: ${exception.message ?: "Unknown TLS error."}${networkSnapshot.debugSuffix}",
            )
        } catch (exception: IOException) {
            ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "${exception.javaClass.simpleName}: ${exception.message ?: "I/O failure."}${resolvedAddresses.asDebugSuffix()}${networkSnapshot.debugSuffix}",
            )
        } catch (_: IllegalArgumentException) {
            ConnectionTestResult.Failure(
                endpointUrl = endpointUrl,
                message = "Enter a valid http:// or https:// URL.",
            )
        }
    }

    private fun createRequestClient(activeNetwork: Network?): OkHttpClient {
        if (activeNetwork == null) return okHttpClient

        return okHttpClient.newBuilder()
            .socketFactory(activeNetwork.socketFactory)
            .dns(
                object : Dns {
                    override fun lookup(hostname: String) =
                    runCatching { activeNetwork.getAllByName(hostname).toList() }
                        .getOrElse { okHttpClient.dns.lookup(hostname) }
                },
            )
            .build()
    }

    private fun resolveHost(
        host: String,
        activeNetwork: Network?,
        fallbackDns: Dns,
    ): List<String> {
        return runCatching {
            val resolved = when {
                activeNetwork != null -> runCatching {
                    activeNetwork.getAllByName(host).toList()
                }.getOrElse {
                    fallbackDns.lookup(host)
                }

                else -> fallbackDns.lookup(host)
            }

            resolved.mapNotNull { address ->
                address.hostAddress
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun createNetworkSnapshot(): NetworkSnapshot {
        val manager = connectivityManager
        val activeNetwork = manager?.activeNetwork
        val capabilities = activeNetwork?.let(manager::getNetworkCapabilities)
        val linkProperties = activeNetwork?.let(manager::getLinkProperties)
        val hasInternetPermission = context.packageManager.checkPermission(
            Manifest.permission.INTERNET,
            context.packageName,
        ) == PackageManager.PERMISSION_GRANTED

        return NetworkSnapshot(
            hasInternetPermission = hasInternetPermission,
            activeNetwork = activeNetwork,
            capabilities = capabilities,
            linkProperties = linkProperties,
        )
    }
}

private fun md5(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
        .digest(input.toByteArray())

    return digest.joinToString(separator = "") { byte ->
        "%02x".format(Locale.US, byte)
    }
}

private fun buildHttpFailureMessage(
    host: String,
    port: Int,
    responseCode: Int,
    resolvedAddresses: List<String>,
    networkSnapshot: NetworkSnapshot,
): String {
    return "HTTP $responseCode from $host:$port.${resolvedAddresses.asDebugSuffix()}${networkSnapshot.debugSuffix}"
}

private fun buildConnectFailureMessage(
    host: String,
    port: Int,
    resolvedAddresses: List<String>,
    details: String?,
    networkSnapshot: NetworkSnapshot,
): String {
    val detailSuffix = details?.takeIf(String::isNotBlank)?.let { " $it." } ?: ""
    return "TCP connection to $host:$port was refused.$detailSuffix${resolvedAddresses.asDebugSuffix()}${networkSnapshot.debugSuffix}"
}

private fun List<String>.asDebugSuffix(): String {
    if (isEmpty()) return ""
    return " Resolved addresses: ${joinToString()}."
}

private data class NetworkSnapshot(
    val hasInternetPermission: Boolean,
    val activeNetwork: Network?,
    val capabilities: NetworkCapabilities?,
    val linkProperties: LinkProperties?,
) {
    val debugSuffix: String
        get() {
            val parts = mutableListOf<String>()
            if (activeNetwork == null) {
                parts += "No active Android network."
            }
            val transports = buildList {
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("WIFI")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("CELLULAR")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("VPN")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ETHERNET")
            }
            if (transports.isNotEmpty()) {
                parts += "Active transports: ${transports.joinToString()}."
            }
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
                parts += "Network is validated."
            }
            linkProperties?.dnsServers
                ?.mapNotNull { it.hostAddress }
                ?.takeIf { it.isNotEmpty() }
                ?.let { dnsServers ->
                    parts += "DNS servers: ${dnsServers.joinToString()}."
                }

            if (parts.isEmpty()) return ""
            return " ${parts.joinToString(separator = " ")}"
        }
}
