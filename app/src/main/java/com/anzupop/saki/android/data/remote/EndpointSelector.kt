package com.anzupop.saki.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.anzupop.saki.android.data.remote.subsonic.SubsonicAuth
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class EndpointSelector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // serverId -> best endpoint id
    private val bestEndpoints = ConcurrentHashMap<Long, Long>()

    // serverId -> last probe results
    private val lastProbeResults = ConcurrentHashMap<Long, List<EndpointProbeResult>>()

    // serverId -> server config (for auth + re-probing on network change)
    private val serverConfigs = ConcurrentHashMap<Long, ServerConfig>()

    // Incremented after each probe completes, so observers can react
    private val _probeVersion = MutableStateFlow(0L)
    val probeVersion: StateFlow<Long> = _probeVersion.asStateFlow()

    data class EndpointProbeResult(
        val endpoint: ServerEndpoint,
        val latencyMs: Long?,
        val reachable: Boolean,
    )

    private var networkCallbackRegistered = false

    fun start() {
        if (networkCallbackRegistered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkChanged()
            }

            override fun onLost(network: Network) {
                onNetworkChanged()
            }
        })
        networkCallbackRegistered = true
    }

    fun registerServer(server: ServerConfig) {
        serverConfigs[server.id] = server
    }

    private var reprobeJob: kotlinx.coroutines.Job? = null

    private fun onNetworkChanged() {
        reprobeJob?.cancel()
        reprobeJob = scope.launch {
            // Immediate probe, then re-probe with exponential backoff: 3s, 6s, 12s, then stop
            val delays = longArrayOf(0, 3_000, 6_000, 12_000)
            for (delay in delays) {
                if (delay > 0) kotlinx.coroutines.delay(delay)
                serverConfigs.forEach { (serverId, server) -> probe(serverId, server) }
            }
        }
    }

    suspend fun probe(serverId: Long, server: ServerConfig): ServerEndpoint? {
        val endpoints = server.endpoints
        if (endpoints.isEmpty()) return null
        if (endpoints.size == 1) {
            val ep = endpoints.first()
            val latency = pingEndpoint(ep, server)
            lastProbeResults[serverId] = listOf(EndpointProbeResult(ep, latency, latency != null))
            if (latency != null) bestEndpoints[serverId] = ep.id
            _probeVersion.value++
            return if (latency != null) ep else null
        }

        val authQuery = SubsonicAuth.baseQuery(server)
        val results = kotlinx.coroutines.coroutineScope {
            endpoints.map { endpoint ->
                async {
                    val latency = pingEndpoint(endpoint, server)
                    endpoint to latency
                }
            }.map { it.await() }
        }

        lastProbeResults[serverId] = results.map { (ep, lat) ->
            EndpointProbeResult(ep, lat, lat != null)
        }

        val best = results
            .filter { it.second != null }
            .minByOrNull { it.second!! }
            ?.first

        if (best != null) {
            bestEndpoints[serverId] = best.id
            Log.d("EndpointSelector", "Best endpoint for server $serverId: ${best.label} (${best.baseUrl})")
        } else {
            bestEndpoints.remove(serverId)
        }
        _probeVersion.value++
        return best
    }

    fun sortedEndpoints(serverId: Long, endpoints: List<ServerEndpoint>): List<ServerEndpoint> {
        val bestId = bestEndpoints[serverId] ?: return endpoints
        val best = endpoints.find { it.id == bestId } ?: return endpoints
        return listOf(best) + endpoints.filter { it.id != bestId }
    }

    fun invalidate(serverId: Long, failedEndpointId: Long) {
        if (bestEndpoints[serverId] == failedEndpointId) {
            bestEndpoints.remove(serverId)
        }
    }

    fun getActiveEndpointId(serverId: Long): Long? = bestEndpoints[serverId]

    fun getLastProbeResults(serverId: Long): List<EndpointProbeResult> =
        lastProbeResults[serverId] ?: emptyList()

    private suspend fun pingEndpoint(endpoint: ServerEndpoint, server: ServerConfig): Long? {
        val authQuery = SubsonicAuth.baseQuery(server)
        val urlBuilder = endpoint.baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("rest/ping.view")
            ?.addQueryParameter("f", "json") ?: return null
        authQuery.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build()

        val request = Request.Builder().url(url).get().build()
        return withContext(ioDispatcher) {
            withTimeoutOrNull(1_500) {
                val start = System.nanoTime()
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        val ms = (System.nanoTime() - start) / 1_000_000
                        if (response.isSuccessful) ms else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
