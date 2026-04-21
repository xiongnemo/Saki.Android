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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    private val forcedEndpoints = ConcurrentHashMap<Long, Long>()
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
        forcedEndpoints.remove(serverId)
        lastProbeResults.remove(serverId)
    }

    private var reprobeJob: kotlinx.coroutines.Job? = null

    private fun onNetworkChanged() {
        forcedEndpoints.clear()
        reprobeJob?.cancel()
        reprobeJob = scope.launch {
            val delays = longArrayOf(0, 3_000, 6_000, 12_000)
            for (d in delays) {
                if (d > 0) delay(d)
                serverConfigs.forEach { (serverId, server) -> probe(serverId, server) }
            }
        }
    }

    /**
     * Probe endpoints: returns as soon as the first reachable endpoint is found.
     * Remaining probes continue in background to find the optimal endpoint.
     * Cancels any previous in-flight probe for the same server.
     */
    private val activeProbeJobs = ConcurrentHashMap<Long, Job>()

    suspend fun probe(serverId: Long, server: ServerConfig): ServerEndpoint? {
        activeProbeJobs.remove(serverId)?.cancel()
        serverConfigs[serverId] = server
        val endpoints = server.endpoints
        if (endpoints.isEmpty()) return null
        if (endpoints.size == 1) {
            val ep = endpoints.first()
            val latency = pingEndpoint(ep, server)
            lastProbeResults[serverId] = listOf(EndpointProbeResult(ep, latency, latency != null))
            if (forcedEndpoints[serverId] == null) {
                if (latency != null) {
                    bestEndpoints[serverId] = ep.id
                } else {
                    bestEndpoints.remove(serverId)
                }
            }
            _probeVersion.update { it + 1 }
            return if (latency != null) ep else null
        }

        val probeResults = ConcurrentHashMap<Long, Long>()
        // Initialize all endpoints as unreachable
        lastProbeResults[serverId] = endpoints.map { EndpointProbeResult(it, null, false) }

        val firstReachable = CompletableDeferred<ServerEndpoint?>()

        val jobs = endpoints.map { endpoint ->
            scope.launch {
                val latency = pingEndpoint(endpoint, server)
                if (latency != null) {
                    probeResults[endpoint.id] = latency
                    if (forcedEndpoints[serverId] == null) {
                        val currentBestId = bestEndpoints[serverId]
                        val currentBestLatency = currentBestId?.let { probeResults[it] }
                        if (currentBestLatency == null || latency < currentBestLatency) {
                            bestEndpoints[serverId] = endpoint.id
                        }
                    }
                }
                // Update this endpoint's result incrementally
                lastProbeResults[serverId] = endpoints.map { ep ->
                    val lat = probeResults[ep.id]
                    EndpointProbeResult(ep, lat, lat != null)
                }
                _probeVersion.update { it + 1 }
                if (latency != null) firstReachable.complete(endpoint)
            }
        }

        // Single background coroutine: wait for all jobs, then finalize
        val parentJob = scope.launch {
            jobs.forEach { it.join() }
            firstReachable.complete(null) // all failed
            if (probeResults.isEmpty() && forcedEndpoints[serverId] == null) {
                bestEndpoints.remove(serverId)
            }
            _probeVersion.update { it + 1 }
        }
        activeProbeJobs[serverId] = parentJob

        return firstReachable.await()
    }

    fun sortedEndpoints(serverId: Long, endpoints: List<ServerEndpoint>): List<ServerEndpoint> {
        val bestId = bestEndpoints[serverId] ?: return endpoints
        val best = endpoints.find { it.id == bestId } ?: return endpoints
        return listOf(best) + endpoints.filter { it.id != bestId }
    }

    fun invalidate(serverId: Long, failedEndpointId: Long) {
        forcedEndpoints.remove(serverId, failedEndpointId)
        if (bestEndpoints.remove(serverId, failedEndpointId)) {
            _probeVersion.update { it + 1 }
        }
    }

    fun getActiveEndpointId(serverId: Long): Long? = bestEndpoints[serverId]

    fun getLastProbeResults(serverId: Long): List<EndpointProbeResult> =
        lastProbeResults[serverId] ?: emptyList()

    fun forceEndpoint(serverId: Long, endpointId: Long) {
        forcedEndpoints[serverId] = endpointId
        bestEndpoints[serverId] = endpointId
        _probeVersion.update { it + 1 }
    }

    fun clearForce(serverId: Long) {
        forcedEndpoints.remove(serverId)
        bestEndpoints.remove(serverId)
        _probeVersion.update { it + 1 }
    }

    fun isForced(serverId: Long): Boolean = forcedEndpoints.containsKey(serverId)

    fun findServerByHostPort(host: String, port: Int): Long? {
        var matchedServerId: Long? = null
        for ((serverId, config) in serverConfigs) {
            for (ep in config.endpoints) {
                val url = ep.baseUrl.trimEnd('/').toHttpUrlOrNull() ?: continue
                if (url.host == host && url.port == port) {
                    if (matchedServerId == null) {
                        matchedServerId = serverId
                    } else if (matchedServerId != serverId) {
                        return null // ambiguous
                    }
                }
            }
        }
        return matchedServerId
    }

    /** Returns the first endpoint by order for the given server (used as canonical base for Coil URLs). */
    fun getCanonicalEndpoint(serverId: Long): ServerEndpoint? {
        return serverConfigs[serverId]?.endpoints
            ?.sortedBy(ServerEndpoint::order)
            ?.firstOrNull()
    }

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
