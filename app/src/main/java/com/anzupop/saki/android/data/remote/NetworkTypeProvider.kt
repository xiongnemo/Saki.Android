package com.anzupop.saki.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
            _networkType.value = cm.resolveNetworkType()

            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _networkType.value = caps.resolveType()
                }

                override fun onLost(network: Network) {
                    _networkType.value = NetworkType.MOBILE
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
    else -> NetworkType.MOBILE
}
