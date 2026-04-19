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
            _ui.update { it.copy(manualIp = manualIp, ssid = savedSsid) }
        }
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _ui.update { it.copy(wsConnected = state == ConnectionState.CONNECTED) }
            }
        }
    }

    fun onManualIpChanged(ip: String) = _ui.update { it.copy(manualIp = ip) }
    fun onSsidChanged(ssid: String)   = _ui.update { it.copy(ssid = ssid) }

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
        _ui.update { it.copy(wifiState = WifiUiState.Connecting) }
        viewModelScope.launch {
            prefs.saveLastIp(ip)
            prefs.saveManualIp(ip)
        }
        repository.connect(ip)
        // wsConnected will flip to true via connectionState collector → triggers nav
    }

    fun resetState() = _ui.update { it.copy(wifiState = WifiUiState.Idle) }
}
