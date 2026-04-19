package com.anzupop.saki.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.anzupop.saki.android.data.remote.subsonic.SubsonicAuth
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.ServerConfig
import com.anzupop.saki.android.domain.model.ServerEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Singleton
class EndpointSelector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val bestEndpoints = ConcurrentHashMap<Long, Long>()
    private val lastProbeResults = ConcurrentHashMap<Long, List<EndpointProbeResult>>()
    private val serverConfigs = ConcurrentHashMap<Long, ServerConfig>()

    private val _probeVersion = MutableStateFlow(0L)
    val probeVersion: StateFlow<Long> = _probeVersion.asStateFlow()

    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(1500, TimeUnit.MILLISECONDS)
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()
    }

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
            override fun onAvailable(network: Network) = onNetworkChanged()
            override fun onLost(network: Network) = onNetworkChanged()
        })
        networkCallbackRegistered = true
    }

    fun registerServer(server: ServerConfig) {
        serverConfigs[server.id] = server
    }

    fun unregisterServer(serverId: Long) {
        serverConfigs.remove(serverId)
        bestEndpoints.remove(serverId)
        lastProbeResults.remove(serverId)
    }

    private var reprobeJob: kotlinx.coroutines.Job? = null

    private fun onNetworkChanged() {
        reprobeJob?.cancel()
        reprobeJob = scope.launch {
            val delays = longArrayOf(0, 3_000, 6_000, 12_000)
            for (d in delays) {
                if (d > 0) delay(d)
                serverConfigs.forEach { (serverId, server) -> probe(serverId, server) }
            }
        }
    }

    suspend fun probe(serverId: Long, server: ServerConfig): ServerEndpoint? {
        serverConfigs[serverId] = server
        val endpoints = server.endpoints
        if (endpoints.isEmpty()) return null
        if (endpoints.size == 1) {
            val ep = endpoints.first()
            val latency = pingEndpoint(ep, server)
            lastProbeResults[serverId] = listOf(EndpointProbeResult(ep, latency, latency != null))
            if (latency != null) {
                bestEndpoints[serverId] = ep.id
            } else {
                bestEndpoints.remove(serverId)
            }
            _probeVersion.update { it + 1 }
            return if (latency != null) ep else null
        }

        val probeResults = ConcurrentHashMap<Long, Long?>()
        kotlinx.coroutines.coroutineScope {
            endpoints.map { endpoint ->
                async {
                    val latency = pingEndpoint(endpoint, server)
                    probeResults[endpoint.id] = latency
                    if (latency != null) {
                        val currentBestId = bestEndpoints[serverId]
                        val currentBestLatency = currentBestId?.let { probeResults[it] }
                        if (currentBestLatency == null || latency < currentBestLatency) {
                            bestEndpoints[serverId] = endpoint.id
                            _probeVersion.update { it + 1 }
                        }
                    }
                }
            }.forEach { it.await() }
        }

        lastProbeResults[serverId] = endpoints.map { ep ->
            val lat = probeResults[ep.id]
            EndpointProbeResult(ep, lat, lat != null)
        }
        if (probeResults.values.all { it == null }) {
            bestEndpoints.remove(serverId)
        }
        _probeVersion.update { it + 1 }
        return endpoints.find { it.id == bestEndpoints[serverId] }
    }

    fun sortedEndpoints(serverId: Long, endpoints: List<ServerEndpoint>): List<ServerEndpoint> {
        val bestId = bestEndpoints[serverId] ?: return endpoints
        val best = endpoints.find { it.id == bestId } ?: return endpoints
        return listOf(best) + endpoints.filter { it.id != bestId }
    }

    fun invalidate(serverId: Long, failedEndpointId: Long) {
        if (bestEndpoints.remove(serverId, failedEndpointId)) {
            _probeVersion.update { it + 1 }
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

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        val start = System.nanoTime()
        return suspendCancellableCoroutine { cont ->
            val call = probeClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    val ms = (System.nanoTime() - start) / 1_000_000
                    if (cont.isActive) cont.resume(if (response.isSuccessful) ms else null)
                }
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }
}
