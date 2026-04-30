package com.parc.fitnesstimer.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parc.fitnesstimer.data.model.Preset
import com.parc.fitnesstimer.data.repository.TimerRepository
import com.parc.fitnesstimer.domain.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PresetsUiState(
    val presets: List<Preset> = emptyList(),
    val isLoading: Boolean = false,
    val confirmDeleteSlot: Int? = null
)

@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val repository: TimerRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(PresetsUiState(isLoading = true))
    val uiState: StateFlow<PresetsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.presets.collect { list ->
                _ui.update { it.copy(presets = list, isLoading = false) }
            }
        }
        // Drop the loading spinner if we lose the socket so the screen doesn't
        // hang forever waiting for a presets list that will never arrive.
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                if (state == ConnectionState.DISCONNECTED) {
                    _ui.update { it.copy(isLoading = false) }
                }
            }
        }
        // Request the list from the device immediately
        refresh()
    }

    fun refresh() {
        if (repository.connectionState.value != ConnectionState.CONNECTED) return
        _ui.update { it.copy(isLoading = true) }
        repository.sendPresetsGet()
    }

    fun onLoadPreset(slot: Int) {
        repository.sendPresetLoad(slot)
    }

    fun onDeleteTapped(slot: Int) {
        _ui.update { it.copy(confirmDeleteSlot = slot) }
    }

    fun onDeleteConfirmed() {
        val slot = _ui.value.confirmDeleteSlot ?: return
        _ui.update { it.copy(confirmDeleteSlot = null) }
        repository.sendPresetDel(slot)
        refresh()
    }

    fun onDeleteDismissed() {
        _ui.update { it.copy(confirmDeleteSlot = null) }
    }
}
