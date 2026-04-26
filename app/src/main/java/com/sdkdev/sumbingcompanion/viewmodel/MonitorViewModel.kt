package com.sdkdev.sumbingcompanion.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdkdev.sumbingcompanion.core.MonitorUiState
import com.sdkdev.sumbingcompanion.core.StateManager
import com.sdkdev.sumbingcompanion.core.WeatherClass
import com.sdkdev.sumbingcompanion.core.WeatherState
import com.sdkdev.sumbingcompanion.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {
    private val stateManager = StateManager()

    val uiState: StateFlow<MonitorUiState> = stateManager.uiState

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var tickJob: Job? = null

    init {
        // Collect alpha settings
        viewModelScope.launch {
            settingsRepo.overlayAlpha.collect { alpha ->
                stateManager.updateAlpha(alpha)
            }
        }
        
        // Collect calibration state
        viewModelScope.launch {
            settingsRepo.isCalibrating.collect { isCalibrating ->
                stateManager.setCalibrating(isCalibrating)
            }
        }
    }

    fun toggleMonitoring() {
        _isMonitoring.value = !_isMonitoring.value
        if (_isMonitoring.value) {
            startTickLoop()
        } else {
            stopTickLoop()
            stateManager.reset()
        }
    }

    fun resetToIdle() {
        stateManager.reset()
    }

    fun setCalibrating(calibrating: Boolean) {
        settingsRepo.setCalibrating(calibrating)
    }

    fun setOverlayAlpha(alpha: Float) {
        viewModelScope.launch {
            settingsRepo.saveOverlayAlpha(alpha)
        }
    }

    fun onTestDetection(weather: WeatherClass, confidence: Float, preview: Bitmap? = null) {
        stateManager.updateTestResult(weather, confidence, preview)
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                stateManager.updateTick()
                delay(200)
            }
        }
    }

    private fun stopTickLoop() {
        tickJob?.cancel()
        tickJob = null
    }

    fun onDetection(weather: WeatherClass, confidence: Float) {
        if (_isMonitoring.value) {
            stateManager.handleDetection(weather, confidence)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTickLoop()
    }
}
