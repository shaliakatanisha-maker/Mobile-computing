package com.mobilecomputing.networkmeter.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

class ConnectionStateTracker(
    private val context: Context,
    private val onConnectionChanged: (connectionType: String, isConnected: Boolean) -> Unit
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var activeType: String = "Disconnected"
    private var isConnected: Boolean = false
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun startTracking() {
        val cm = connectivityManager ?: return

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateConnectionState(network)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateConnectionState(network)
            }

            override fun onLost(network: Network) {
                activeType = "Disconnected"
                isConnected = false
                onConnectionChanged(activeType, isConnected)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback!!)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback!!)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initial check
        checkCurrentState()
    }

    fun stopTracking() {
        callback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
            callback = null
        }
    }

    fun getCurrentConnectionType(): String {
        checkCurrentState()
        return activeType
    }

    fun isCurrentlyConnected(): Boolean {
        checkCurrentState()
        return isConnected
    }

    private fun checkCurrentState() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork != null && activeNetwork.isConnected) {
            isConnected = true
            activeType = when (activeNetwork.type) {
                ConnectivityManager.TYPE_WIFI -> "Wi-Fi"
                ConnectivityManager.TYPE_MOBILE -> "Cellular"
                ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                else -> "Other"
            }
        } else {
            isConnected = false
            activeType = "Disconnected"
        }
    }

    private fun updateConnectionState(network: Network) {
        val cm = connectivityManager ?: return
        val caps = cm.getNetworkCapabilities(network) ?: return
        isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        activeType = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        onConnectionChanged(activeType, isConnected)
    }
}
