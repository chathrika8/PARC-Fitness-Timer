package com.parc.fitnesstimer.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parc.fitnesstimer.data.model.DisplayDigits
import com.parc.fitnesstimer.data.model.TimerStateDto
import com.parc.fitnesstimer.data.repository.TimerRepository
import com.parc.fitnesstimer.domain.ConnectionState
import com.parc.fitnesstimer.domain.TimerMode
import com.parc.fitnesstimer.domain.TimerRunState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUiState(
    // ── From server ───────────────────────────────────────────────────────────
    val serverState: TimerStateDto = TimerStateDto(),
    // ── Locally-managed config (before Apply is tapped) ───────────────────────
    val configMode: TimerMode = TimerMode.AMRAP,
    val configWorkSecs: Int = 720,
    val configRestSecs: Int = 60,
    val configRounds: Int = 8,
    // ── UI interaction flags ──────────────────────────────────────────────────
    val showModeConfirmDialog: Boolean = false,
    val pendingMode: TimerMode? = null,
    val showPresetNameDialog: Boolean = false,
    val snackbarMessage: String? = null
) {
    val runState: TimerRunState get() = serverState.runState
    val currentMode: TimerMode get() = serverState.timerMode
    val currentPhase get() = serverState.timerPhase
    val isRunning: Boolean get() = runState == TimerRunState.RUNNING
    val isPaused: Boolean get() = runState == TimerRunState.PAUSED
    val isIdle: Boolean get() = runState == TimerRunState.IDLE
    val isDone: Boolean get() = runState == TimerRunState.DONE
    val isPreStart: Boolean get() = runState == TimerRunState.PRE_START
    val showRoundButton: Boolean get() = currentMode.hasManualRound && (isRunning || isPaused)
    val showPhaseBar: Boolean get() = (currentMode == TimerMode.TABATA ||
            currentMode == TimerMode.INTERVAL) && isRunning
}

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val repository: TimerRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _ui = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _ui.asStateFlow()

    init {
        // Mirror server state into UI state
        viewModelScope.launch {
            repository.timerState.collect { serverState ->
                _ui.update { it.copy(serverState = serverState) }
            }
        }
    }

    // ── Display digits (derived) ──────────────────────────────────────────────

    /** colonVisible is passed in from the screen (client-side blink). */
    fun computeDigits(colonVisible: Boolean): DisplayDigits {
        val s = _ui.value.serverState
        val blink = when (s.runState) {
            TimerRunState.IDLE, TimerRunState.PAUSED -> colonVisible // client blink
            else -> s.colon                                          // server-driven
        }
        return DisplayDigits.from(s, blink)
    }

    // ── Transport commands ────────────────────────────────────────────────────

    fun onStartPause() {
        val state = _ui.value.runState
        when {
            state == TimerRunState.IDLE || state == TimerRunState.DONE -> repository.sendStart()
            state == TimerRunState.RUNNING || state == TimerRunState.TRANSITION -> repository.sendPause()
            state == TimerRunState.PAUSED -> repository.sendStart()
            state == TimerRunState.PRE_START -> repository.sendPause()
        }
    }

    fun onReset() = repository.sendReset()
    fun onRoundInc() = repository.sendRinc()

    // ── Mode selection ────────────────────────────────────────────────────────

    fun onModeChipTapped(mode: TimerMode) {
        if (_ui.value.isRunning) {
            // Ask for confirmation before switching during an active workout
            _ui.update { it.copy(showModeConfirmDialog = true, pendingMode = mode) }
        } else {
            applyModeSwitch(mode)
        }
    }

    fun onModeConfirmAccepted() {
        val mode = _ui.value.pendingMode ?: return
        _ui.update { it.copy(showModeConfirmDialog = false, pendingMode = null) }
        applyModeSwitch(mode)
    }

    fun onModeConfirmDismissed() {
        _ui.update { it.copy(showModeConfirmDialog = false, pendingMode = null) }
    }

    private fun applyModeSwitch(mode: TimerMode) {
        val work   = mode.defaultWorkSecs
        val rest   = mode.defaultRestSecs
        val rounds = mode.defaultRounds
        _ui.update { it.copy(configMode = mode, configWorkSecs = work,
            configRestSecs = rest, configRounds = rounds) }
        repository.sendConfig(mode.value, work, rest, rounds)
    }

    // ── Config spinners ───────────────────────────────────────────────────────

    fun onWorkSecsChange(v: Int)  = _ui.update { it.copy(configWorkSecs = v) }
    fun onRestSecsChange(v: Int)  = _ui.update { it.copy(configRestSecs = v) }
    fun onRoundsChange(v: Int)    = _ui.update { it.copy(configRounds = v) }

    fun onApplyConfig() {
        val s = _ui.value
        repository.sendConfig(s.configMode.value, s.configWorkSecs,
            s.configRestSecs, s.configRounds)
    }

    // ── Preset save dialog ────────────────────────────────────────────────────

    fun onSavePresetTapped()           = _ui.update { it.copy(showPresetNameDialog = true) }
    fun onPresetNameDialogDismissed()  = _ui.update { it.copy(showPresetNameDialog = false) }

    fun onPresetSaveConfirmed(name: String, slot: Int = 0) {
        _ui.update { it.copy(showPresetNameDialog = false) }
        repository.sendPresetSave(slot, name)
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    fun onSnackbarShown() = _ui.update { it.copy(snackbarMessage = null) }
}
