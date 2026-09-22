package com.killindodo.dodo_rf.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class WifiBindState {
    object Idle : WifiBindState()
    object Requesting : WifiBindState()
    data class Bound(val network: Network, val ssid: String? = null) : WifiBindState()
    data class Failed(val reason: String) : WifiBindState()
}

class DodoWifiBinder(private val context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _bindState = MutableStateFlow<WifiBindState>(WifiBindState.Idle)
    val bindState: StateFlow<WifiBindState> = _bindState

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    init {
        // Attempt to bind to existing Wi-Fi immediately if available
        bindToCurrentWifi()
    }

    /**
     * Finds active Wi-Fi interface and binds entire process socket routing to it.
     * Prevents Android from dropping packets or routing over Cellular when AP has no internet.
     */
    fun bindToCurrentWifi(): Boolean {
        try {
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val success = cm.bindProcessToNetwork(network)
                    if (success) {
                        Log.i(TAG, "Successfully bound process to active Wi-Fi: $network")
                        _bindState.value = WifiBindState.Bound(network)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed binding to current Wi-Fi", e)
        }
        return false
    }

    /**
     * Request connection to Dodo-RF SoftAP using WifiNetworkSpecifier (Android 10+)
     */
    fun connectAndBindSoftAp(
        ssid: String = "Dodo-RF-Replicator",
        passphrase: String = "esp32rf2026"
    ) {
        releaseBinding()
        _bindState.value = WifiBindState.Requesting

        val builder = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()
            builder.setNetworkSpecifier(specifier)
        }

        val request = builder.build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val bound = cm.bindProcessToNetwork(network)
                Log.i(TAG, "Network available. Process bound: $bound")
                _bindState.value = WifiBindState.Bound(network, ssid)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "Network lost: $network")
                cm.bindProcessToNetwork(null)
                _bindState.value = WifiBindState.Idle
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.e(TAG, "Network request timed out or cancelled by user")
                _bindState.value = WifiBindState.Failed("Connection cancelled or timed out")
            }
        }

        activeCallback = callback
        try {
            cm.requestNetwork(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting network", e)
            _bindState.value = WifiBindState.Failed(e.message ?: "Unknown error")
        }
    }

    fun releaseBinding() {
        activeCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (ignored: Exception) {}
            activeCallback = null
        }
        cm.bindProcessToNetwork(null)
        _bindState.value = WifiBindState.Idle
    }

    companion object {
        private const val TAG = "DodoWifiBinder"
    }
}
