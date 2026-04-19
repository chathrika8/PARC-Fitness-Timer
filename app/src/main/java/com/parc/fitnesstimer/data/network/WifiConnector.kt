package com.parc.fitnesstimer.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class WifiConnectResult {
    /** Network is available and process is bound to it. */
    data class Connected(val network: Network) : WifiConnectResult()
    /** Timed out or request rejected. */
    data class Failed(val reason: String) : WifiConnectResult()
    /** API < 29; user must connect manually. */
    object ManualRequired : WifiConnectResult()
}

/**
 * Handles WiFi network connection using [WifiNetworkSpecifier] (API 29+).
 *
 * On API 26-28, [WifiNetworkSpecifier] is unavailable; the function returns
 * [WifiConnectResult.ManualRequired] and the UI guides the user to connect
 * in the system WiFi settings.
 *
 * After a successful connection, [ConnectivityManager.bindProcessToNetwork]
 * is called so that all TCP traffic in this process routes over the AP
 * network — including the WebSocket to 192.168.4.1.
 */
@Singleton
class WifiConnector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Request connection to the given [ssid].
     *
     * Returns a cold [Flow] that emits exactly one [WifiConnectResult] and completes.
     * The caller is responsible for collecting in an appropriate coroutine scope.
     *
     * @param ssid    Target AP SSID (e.g. "GymTimer")
     * @param passphrase WPA2 passphrase; empty string = open network
     * @param timeoutMs  How long to wait for the OS to connect (default 30 s)
     */
    fun connectToAp(
        ssid: String,
        passphrase: String = "",
        timeoutMs: Int = 30_000
    ): Flow<WifiConnectResult> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            trySend(WifiConnectResult.ManualRequired)
            close()
            return@callbackFlow
        }

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply { if (passphrase.isNotEmpty()) setWpa2Passphrase(passphrase) }
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Bind the entire process to this network so ws:// uses this AP.
                connectivityManager.bindProcessToNetwork(network)
                trySend(WifiConnectResult.Connected(network))
                close()
            }

            override fun onUnavailable() {
                trySend(WifiConnectResult.Failed("Network unavailable or request declined"))
                close()
            }

            override fun onLost(network: Network) {
                // Unbind — the caller (ConnectionViewModel) handles reconnect.
                connectivityManager.bindProcessToNetwork(null)
            }
        }

        connectivityManager.requestNetwork(networkRequest, callback, timeoutMs)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Release network binding. Call when the app no longer needs the AP connection.
     */
    fun releaseNetworkBinding() {
        connectivityManager.bindProcessToNetwork(null)
    }

    /** Check whether a WiFi transport is currently active. */
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
