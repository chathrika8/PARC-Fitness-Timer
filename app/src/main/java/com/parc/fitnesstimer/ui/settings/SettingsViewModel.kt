package com.parc.fitnesstimer.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parc.fitnesstimer.data.model.BuzzConfig
import com.parc.fitnesstimer.data.model.DeviceSettings
import com.parc.fitnesstimer.data.model.OtaEvent
import com.parc.fitnesstimer.data.repository.TimerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import javax.inject.Inject

sealed class OtaUiState {
    object Idle : OtaUiState()
    data class FileSelected(val name: String, val size: Int) : OtaUiState()
    object WaitingForReady : OtaUiState()
    data class Uploading(val progress: Float, val written: Int, val total: Int) : OtaUiState()
    object Done : OtaUiState()
    data class Error(val message: String) : OtaUiState()
}

data class SettingsUiState(
    val dmap: List<Int> = listOf(0, 1, 2, 3, 4, 5),
    val buzzConfig: BuzzConfig = BuzzConfig(),
    val isLoading: Boolean = true,
    
    // WiFi / Conn
    val ssidInput: String = "GymTimer",
    val passInput: String = "",
    val showPass: Boolean = false,
    val connMode: Int = 0,
    val btNameInput: String = "GymTimer",
    val btPinInput: String = "123456",
    
    // Display
    val colonMode: Int = 0,
    val c321Mode: Int = 0,
    
    // OTA
    val otaState: OtaUiState = OtaUiState.Idle,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TimerRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _ui.asStateFlow()

    // Holds raw file bytes while OTA is in progress
    private var otaBytes: ByteArray? = null
    private var otaJob: Job? = null

    init {
        // Collect device settings from repository
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _ui.update { it.copy(
                    dmap       = settings.dmap.toMutableList(),
                    buzzConfig = settings.bz,
                    connMode   = settings.conn.mode,
                    btNameInput = settings.conn.btName,
                    btPinInput  = settings.conn.btPin,
                    colonMode   = settings.disp.colon,
                    c321Mode    = settings.disp.c321,
                    isLoading  = false
                )}
            }
        }
        // Collect OTA events 
        viewModelScope.launch {
            repository.otaEvents.collect { event ->
                handleOtaEvent(event)
            }
        }
        // Request current settings on first load
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(isLoading = true) }
        repository.sendSettingsGet()
    }

    // ── Digit map reorder ─────────────────────────────────────────────────────

    /** Swap the physical positions of two digits. */
    fun swapDigits(from: Int, to: Int) {
        if (from == to) return
        val current = _ui.value.dmap.toMutableList()
        val tmp = current[from]
        current[from] = current[to]
        current[to] = tmp
        _ui.update { it.copy(dmap = current) }
    }

    fun saveDmap() = repository.sendDmapSave(_ui.value.dmap)

    // ── Sound settings ────────────────────────────────────────────────────────

    fun onVolumeChanged(vol: Int) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.copy(vol = vol)) }

    fun onPip321Changed(on: Boolean) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.withPip321(on)) }

    fun onGoToneChanged(on: Boolean) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.withGoTone(on)) }

    fun onPhaseTransitionChanged(on: Boolean) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.withPhaseTransition(on)) }

    fun onWorkoutDoneChanged(on: Boolean) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.withWorkoutDone(on)) }

    fun onLast10sChanged(on: Boolean) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.withLast10s(on)) }

    fun onPauseClickChanged(on: Boolean) =
        _ui.update { it.copy(buzzConfig = it.buzzConfig.withPauseClick(on)) }

    fun saveSound() {
        val bz = _ui.value.buzzConfig
        repository.sendSoundSave(bz.vol, bz.ev, bz.modes)
    }

    // ── WiFi settings ─────────────────────────────────────────────────────────

    fun onSsidChanged(v: String)  = _ui.update { it.copy(ssidInput = v) }
    fun onPassChanged(v: String)  = _ui.update { it.copy(passInput = v) }
    fun onShowPassToggled()       = _ui.update { it.copy(showPass = !it.showPass) }
    fun onConnModeChanged(v: Int) = _ui.update { it.copy(connMode = v) }
    fun onBtNameChanged(v: String) = _ui.update { it.copy(btNameInput = v) }
    fun onBtPinChanged(v: String) = _ui.update { it.copy(btPinInput = v) }

    fun onApplyWifi() {
        val ui = _ui.value
        repository.sendWifiSet(ui.ssidInput, ui.passInput)
    }

    fun onApplyConnSet() {
        val ui = _ui.value
        repository.sendConnSet(ui.connMode, ui.btNameInput, ui.btPinInput)
    }

    // ── Display Settings ──────────────────────────────────────────────────────
    fun onColonModeChanged(v: Int) = _ui.update { it.copy(colonMode = v) }
    fun onC321ModeChanged(v: Int) = _ui.update { it.copy(c321Mode = v) }

    fun saveDisplaySettings() {
        val ui = _ui.value
        repository.sendDisplaySave(ui.colonMode, ui.c321Mode)
    }

    fun onRestartDevice() = repository.sendRestart()

    // ── OTA update ────────────────────────────────────────────────────────────

    fun onFileSelected(uri: Uri, name: String, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                if (bytes == null || bytes.isEmpty()) {
                    _ui.update { it.copy(otaState = OtaUiState.Error("Cannot read file")) }
                    return@launch
                }
                otaBytes = bytes
                _ui.update { it.copy(
                    selectedFileUri  = uri,
                    selectedFileName = name,
                    otaState = OtaUiState.FileSelected(name, bytes.size)
                )}
            } catch (e: Exception) {
                _ui.update { it.copy(otaState = OtaUiState.Error(e.message ?: "Read error")) }
            }
        }
    }

    fun startOtaUpload() {
        val bytes = otaBytes ?: run {
            _ui.update { it.copy(otaState = OtaUiState.Error("No file selected")) }
            return
        }

        otaJob?.cancel()
        _ui.update { it.copy(otaState = OtaUiState.WaitingForReady) }

        // sendOtaBegin triggers the server to respond with ota_ready.
        // The response is collected in the otaEvents collector above.
        repository.sendOtaBegin(bytes.size)

        // Streaming starts when OtaEvent.Ready arrives (handled in handleOtaEvent)
    }

    private fun handleOtaEvent(event: OtaEvent) {
        when (event) {
            OtaEvent.Ready -> {
                // Begin streaming binary chunks
                val bytes = otaBytes ?: return
                otaJob = viewModelScope.launch(Dispatchers.IO) {
                    var offset = 0
                    while (offset < bytes.size) {
                        val chunkSize = minOf(4096, bytes.size - offset)
                        // ByteString.of(bytes, offset, count) creates a binary WS frame
                        val chunk = bytes.toByteString(offset, chunkSize)
                        repository.sendOtaChunk(chunk)
                        offset += chunkSize
                    }
                }
            }
            is OtaEvent.Progress -> {
                _ui.update { it.copy(otaState = OtaUiState.Uploading(
                    progress = event.written.toFloat() / (otaBytes?.size?.coerceAtLeast(1) ?: 1),
                    written  = event.written,
                    total    = event.total
                ))}
            }
            OtaEvent.Done -> {
                otaBytes = null
                _ui.update { it.copy(otaState = OtaUiState.Done) }
            }
            is OtaEvent.Error -> {
                _ui.update { it.copy(otaState = OtaUiState.Error(event.message)) }
            }
            OtaEvent.Restarting -> {
                // Device will restart; OTA done state handles UI
            }
        }
    }

    fun resetOtaState() = _ui.update { it.copy(otaState = OtaUiState.Idle, selectedFileName = "") }
}
