package com.sdkdev.sumbingcompanion.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdkdev.sumbingcompanion.core.GameTimeClock
import com.sdkdev.sumbingcompanion.core.GameTimeSnapshot
import com.sdkdev.sumbingcompanion.core.MonitorUiState
import com.sdkdev.sumbingcompanion.core.StateManager
import com.sdkdev.sumbingcompanion.core.UnixTimeClient
import com.sdkdev.sumbingcompanion.core.WeatherClass
import com.sdkdev.sumbingcompanion.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitorViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {
    private val stateManager = StateManager()
    private val gameTimeClock = GameTimeClock()

    val uiState: StateFlow<MonitorUiState> = stateManager.uiState

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _gameTime = MutableStateFlow(GameTimeSnapshot())
    val gameTime: StateFlow<GameTimeSnapshot> = _gameTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var tickJob: Job? = null
    private var gameTimeJob: Job? = null
    private var lastObservedStage = 0

    init {
        viewModelScope.launch {
            settingsRepo.overlayAlpha.collect { alpha ->
                stateManager.updateAlpha(alpha)
            }
        }

        viewModelScope.launch {
            settingsRepo.isCalibrating.collect { isCalibrating ->
                stateManager.setCalibrating(isCalibrating)
            }
        }

        startTickLoop()
        startGameTimeLoop()
        refreshGameTime()
    }

    fun toggleMonitoring() {
        _isMonitoring.value = !_isMonitoring.value
        if (!_isMonitoring.value) {
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

    fun onDetection(weather: WeatherClass, confidence: Float) {
        if (_isMonitoring.value) {
            stateManager.handleDetection(weather, confidence)
        }
    }

    fun syncFromServer() {
        if (_isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            stateManager.setStatus("Syncing with UNIX Team timer...")

            try {
                val result = withContext(Dispatchers.IO) {
                    UnixTimeClient.fetch()
                }

                if (!result.running) {
                    error("Timer server sedang berhenti")
                }

                stateManager.syncCountdown(
                    remainingSeconds = result.remainingSeconds,
                    durationSeconds = result.durationSeconds
                )

                result.gameTime?.let { payload ->
                    _gameTime.value = gameTimeClock.applyServer(
                        stage = payload.stage,
                        gameTime = payload.gameTime,
                        nextGameTime = payload.nextGameTime,
                        stageDurationMs = payload.stageDurationMs,
                        remainingMs = payload.remainingMs,
                        running = payload.running,
                        halfRttMs = result.halfRttMs
                    )
                    lastObservedStage = _gameTime.value.stage
                }

                val cycleText = result.cycle?.let { " • cycle $it" } ?: ""
                stateManager.setStatus(
                    "Synced • \${formatSeconds(result.remainingSeconds)}$cycleText • \${result.rttMs}ms RTT"
                )
            } catch (e: Exception) {
                stateManager.setStatus(
                    "Sync failed: \${e.message ?: e::class.java.simpleName}"
                )
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun refreshGameTime() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    UnixTimeClient.fetch()
                }
                result.gameTime?.let { payload ->
                    _gameTime.value = gameTimeClock.applyServer(
                        stage = payload.stage,
                        gameTime = payload.gameTime,
                        nextGameTime = payload.nextGameTime,
                        stageDurationMs = payload.stageDurationMs,
                        remainingMs = payload.remainingMs,
                        running = payload.running,
                        halfRttMs = result.halfRttMs
                    )
                    lastObservedStage = _gameTime.value.stage
                }
            } catch (_: Exception) {
                // Keep the local stage projection and retry on the next boundary.
            }
        }
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

    private fun startGameTimeLoop() {
        gameTimeJob?.cancel()
        gameTimeJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = gameTimeClock.snapshot()
                _gameTime.value = snapshot

                if (snapshot.synced) {
                    if (lastObservedStage == 0) {
                        lastObservedStage = snapshot.stage
                    } else if (snapshot.stage != lastObservedStage) {
                        lastObservedStage = snapshot.stage
                        refreshGameTime()
                    }
                }

                delay(250)
            }
        }
    }

    private fun formatSeconds(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "%d:%02d".format(safe / 60, safe % 60)
    }

    override fun onCleared() {
        stopTickLoop()
        gameTimeJob?.cancel()
        gameTimeJob = null
        super.onCleared()
    }
}
