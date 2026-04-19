package com.anzupop.saki.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkType { WIFI, MOBILE }

@Singleton
class NetworkTypeProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _networkType = MutableStateFlow(NetworkType.WIFI)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    init {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm != null) {
            // Set initial value
            _networkType.value = cm.resolveNetworkType()

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _networkType.value = caps.resolveType()
                }

                override fun onLost(network: Network) {
                    _networkType.value = cm.resolveNetworkType()
                }
            })
        }
    }
}

private fun ConnectivityManager.resolveNetworkType(): NetworkType {
    val caps = getNetworkCapabilities(activeNetwork) ?: return NetworkType.MOBILE
    return caps.resolveType()
}

private fun NetworkCapabilities.resolveType(): NetworkType = when {
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
    // VPN, cellular, and everything else → mobile
    else -> NetworkType.MOBILE
}
