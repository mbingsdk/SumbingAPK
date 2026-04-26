package com.sdkdev.sumbingcompanion.core

import android.content.Context
import android.graphics.Bitmap
import com.sdkdev.sumbingcompanion.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AutoCaptureManager(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val dataCollectionManager: DataCollectionManager,
    private val detectionEngine: DetectionEngine
) {
    private val _events = MutableSharedFlow<AutoCaptureEvent>()
    val events: SharedFlow<AutoCaptureEvent> = _events

    private var captureJob: Job? = null

    suspend fun start(stateFlow: StateFlow<MonitorUiState>, getLatestBitmap: () -> Bitmap?) {
        stop()
        
        val config = settingsRepo.autoCaptureConfigFlow.first()
        if (!config.enabled) return

        captureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val currentConfig = settingsRepo.autoCaptureConfigFlow.first()
                if (!currentConfig.enabled) break

                val uiState = stateFlow.value
                val label = when (uiState.state) {
                    WeatherState.WEATHER -> uiState.lastCuaca
                    WeatherState.IDLE -> uiState.lastPassive
                    else -> null
                }

                if (label != null && currentConfig.captureOnStates.contains(uiState.state)) {
                    val currentCount = dataCollectionManager.getCountForClass(label)
                    if (currentCount < currentConfig.maxPerClass) {
                        val bitmap = getLatestBitmap()
                        if (bitmap != null) {
                            val region = settingsRepo.getRegion()
                            
                            // Use cropRegion instead of cropAndResize to get full quality
                            val processed = detectionEngine.cropRegion(bitmap, region)
                            val path = dataCollectionManager.saveCapture(processed, label, fullFrame = false)

                            if (path != null) {
                                _events.emit(AutoCaptureEvent(label, path, currentCount + 1))
                            }
                        }
                    }
                }
                
                delay(currentConfig.intervalSec * 1000L)
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
    }
}
