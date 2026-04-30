package com.parc.fitnesstimer.ui.connection

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parc.fitnesstimer.data.network.WifiConnectResult
import com.parc.fitnesstimer.data.network.WifiConnector
import com.parc.fitnesstimer.data.prefs.AppPreferences
import com.parc.fitnesstimer.data.repository.TimerRepository
import com.parc.fitnesstimer.domain.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WifiUiState {
    object Idle : WifiUiState()
    object Scanning : WifiUiState()
    object Connecting : WifiUiState()
    object ManualRequired : WifiUiState()
    data class Error(val message: String) : WifiUiState()
    object Success : WifiUiState()
}

data class ConnectionUiState(
    val wifiState: WifiUiState = WifiUiState.Idle,
    val manualIp: String = AppPreferences.DEFAULT_IP,
    val ssid: String = AppPreferences.DEFAULT_SSID,
    val btDeviceName: String = "GymTimer",
    val selectedTransport: Int = 0, // 0 = WiFi, 1 = BT
    val wsConnected: Boolean = false
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: TimerRepository,
    private val wifiConnector: WifiConnector,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _ui = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val savedIp   = prefs.lastIp.first()
            val savedSsid = prefs.lastSsid.first()
            val manualIp  = prefs.manualIp.first().ifEmpty { savedIp.ifEmpty { AppPreferences.DEFAULT_IP } }
            val lastTransport = prefs.lastTransport.first()
            _ui.update { it.copy(manualIp = manualIp, ssid = savedSsid, selectedTransport = lastTransport) }

            // Auto-connect if WiFi was the last used transport AND we aren't
            // already connected (re-entering the screen shouldn't kick off a
            // duplicate connection attempt against a healthy socket).
            if (lastTransport == 0 &&
                repository.connectionState.value != ConnectionState.CONNECTED) {
                connectWebSocket()
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _ui.update { it.copy(wsConnected = state == ConnectionState.CONNECTED) }
                when (state) {
                    ConnectionState.CONNECTING,
                    ConnectionState.RECONNECTING -> {
                        _ui.update { it.copy(wifiState = WifiUiState.Connecting) }
                    }
                    ConnectionState.CONNECTED -> {
                        _ui.update { it.copy(wifiState = WifiUiState.Success) }
                    }
                    ConnectionState.DISCONNECTED -> {
                        // Only clear an in-flight Connecting indicator. Don't
                        // overwrite a terminal Error / ManualRequired message.
                        if (_ui.value.wifiState == WifiUiState.Connecting) {
                            _ui.update { it.copy(wifiState = WifiUiState.Idle) }
                        }
                    }
                }
            }
        }
    }

    fun onTransportSelected(transport: Int) {
        _ui.update { it.copy(selectedTransport = transport) }
        viewModelScope.launch { prefs.saveLastTransport(transport) }
    }

    fun onManualIpChanged(ip: String) = _ui.update { it.copy(manualIp = ip) }
    fun onSsidChanged(ssid: String)   = _ui.update { it.copy(ssid = ssid) }
    fun onBtDeviceNameChanged(name: String) = _ui.update { it.copy(btDeviceName = name) }

    /** Try connecting to the AP via WifiNetworkSpecifier then open WS. */
    fun onConnectWifiTapped() {
        viewModelScope.launch {
            _ui.update { it.copy(wifiState = WifiUiState.Connecting) }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                _ui.update { it.copy(wifiState = WifiUiState.ManualRequired) }
                return@launch
            }

            wifiConnector.connectToAp(
                ssid       = _ui.value.ssid,
                passphrase = ""
            ).collect { result ->
                when (result) {
                    is WifiConnectResult.Connected -> {
                        prefs.saveLastSsid(_ui.value.ssid)
                        connectWebSocket()
                    }
                    is WifiConnectResult.Failed -> {
                        _ui.update { it.copy(wifiState = WifiUiState.Error(result.reason)) }
                    }
                    WifiConnectResult.ManualRequired -> {
                        _ui.update { it.copy(wifiState = WifiUiState.ManualRequired) }
                    }
                }
            }
        }
    }

    /** Directly connect WebSocket using current IP (skips WiFi connection step). */
    fun onConnectIpTapped() {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        val ip = _ui.value.manualIp.trim().ifEmpty { AppPreferences.DEFAULT_IP }
        if (!isValidIpv4(ip)) {
            _ui.update { it.copy(wifiState = WifiUiState.Error(
                "\"$ip\" is not a valid IP address. Try 192.168.4.1."
            )) }
            return
        }
        _ui.update { it.copy(wifiState = WifiUiState.Connecting) }
        viewModelScope.launch {
            prefs.saveLastIp(ip)
            prefs.saveManualIp(ip)
        }
        repository.connectWifi(ip)
        // wsConnected will flip to true via connectionState collector → triggers nav
    }

    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return false
            n in 0..255 && part == n.toString()
        }
    }

    fun onConnectBluetoothTapped() {
        _ui.update { it.copy(wifiState = WifiUiState.Connecting) }
        repository.connectBluetooth(_ui.value.btDeviceName)
    }

    fun onBluetoothPermissionDenied() {
        _ui.update { it.copy(wifiState = WifiUiState.Error(
            "Bluetooth permission is required to connect to the timer."
        )) }
    }

    fun resetState() = _ui.update { it.copy(wifiState = WifiUiState.Idle) }
}
