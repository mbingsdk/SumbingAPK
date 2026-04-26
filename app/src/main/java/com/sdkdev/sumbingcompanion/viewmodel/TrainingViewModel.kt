package com.sdkdev.sumbingcompanion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdkdev.sumbingcompanion.core.AutoCaptureConfig
import com.sdkdev.sumbingcompanion.core.AutoCaptureEvent
import com.sdkdev.sumbingcompanion.core.WeatherState
import com.sdkdev.sumbingcompanion.data.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrainingViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {
    
    val autoCaptureConfig: StateFlow<AutoCaptureConfig> = settingsRepo.autoCaptureConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoCaptureConfig())

    private val _lastCaptureEvent = MutableSharedFlow<AutoCaptureEvent>()
    val lastCaptureEvent: SharedFlow<AutoCaptureEvent> = _lastCaptureEvent.asSharedFlow()

    fun toggleAutoCapture(enabled: Boolean) {
        viewModelScope.launch {
            val current = autoCaptureConfig.value
            settingsRepo.saveAutoCaptureConfig(current.copy(enabled = enabled))
        }
    }

    fun setAutoCaptureInterval(sec: Int) {
        viewModelScope.launch {
            val current = autoCaptureConfig.value
            settingsRepo.saveAutoCaptureConfig(current.copy(intervalSec = sec))
        }
    }

    fun setAutoCaptureMax(max: Int) {
        viewModelScope.launch {
            val current = autoCaptureConfig.value
            settingsRepo.saveAutoCaptureConfig(current.copy(maxPerClass = max))
        }
    }

    fun toggleCaptureState(state: WeatherState) {
        viewModelScope.launch {
            val current = autoCaptureConfig.value
            val newStates = current.captureOnStates.toMutableSet()
            if (newStates.contains(state)) {
                newStates.remove(state)
            } else {
                newStates.add(state)
            }
            settingsRepo.saveAutoCaptureConfig(current.copy(captureOnStates = newStates))
        }
    }

    fun onAutoCaptureEvent(event: AutoCaptureEvent) {
        viewModelScope.launch {
            _lastCaptureEvent.emit(event)
        }
    }
}
