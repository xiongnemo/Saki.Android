package com.anzupop.saki.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.anzupop.saki.android.di.IoDispatcher
import com.anzupop.saki.android.domain.model.ServerEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    private var pendingProbes = ConcurrentHashMap<Long, List<ServerEndpoint>>()

    fun registerEndpoints(serverId: Long, endpoints: List<ServerEndpoint>) {
        pendingProbes[serverId] = endpoints
    }

    private fun onNetworkChanged() {
        pendingProbes.forEach { (serverId, endpoints) ->
            scope.launch { probe(serverId, endpoints) }
        }
    }

    suspend fun probe(serverId: Long, endpoints: List<ServerEndpoint>): ServerEndpoint? {
        if (endpoints.isEmpty()) return null
        if (endpoints.size == 1) {
            bestEndpoints[serverId] = endpoints.first().id
            return endpoints.first()
        }

        val results = kotlinx.coroutines.coroutineScope {
            endpoints.map { endpoint ->
                async {
                    val latency = pingEndpoint(endpoint)
                    endpoint to latency
                }
            }.map { it.await() }
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

    private suspend fun pingEndpoint(endpoint: ServerEndpoint): Long? {
        val url = endpoint.baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("rest/ping.view")
            ?.addQueryParameter("f", "json")
            ?.build() ?: return null

        val request = Request.Builder().url(url).get().build()
        return withTimeoutOrNull(3_000) {
            val start = System.nanoTime()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) (System.nanoTime() - start) / 1_000_000 else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
