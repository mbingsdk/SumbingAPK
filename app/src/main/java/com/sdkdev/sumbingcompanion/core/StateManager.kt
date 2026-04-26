package com.sdkdev.sumbingcompanion.core

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WeatherState { IDLE, WEATHER, COOLDOWN, POST_CD, RECOOLDOWN }

data class MonitorUiState(
    val state: WeatherState = WeatherState.IDLE,
    val lastCuaca: String = "siang",
    val countdown: Int = 0,         // detik tersisa (cooldown/post/recd)
    val weatherDur: Int = 0,        // detik durasi cuaca aktif
    val cdTotal: Int = 1,           // untuk progress bar
    val lastPassive: String = "siang",   // siang atau malam terakhir
    val prevWasRain: Boolean = false,
    val topConfidence: Pair<String, Float> = Pair("", 0f),
    val overlayAlpha: Float = 1.0f,
    val testPreview: Bitmap? = null,
    val isCalibrating: Boolean = false
)

class StateManager {
    companion object {
        private const val EXTRA_TIME = 2
        private const val COOLDOWN_SEC = (8 * 60) + EXTRA_TIME // 482
        private const val POST_CD_SEC = 5
        private const val RECD_SEC = COOLDOWN_SEC - POST_CD_SEC // 477
        private const val RECD_BREAK_HI = 310
        private const val RECD_BREAK_LO = 270
    }

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var stateStartTime: Long = 0
    private var weatherStartTime: Long = 0

    fun handleDetection(weather: WeatherClass, confidence: Float) {
        val key = weather.key
        _uiState.value = _uiState.value.copy(topConfidence = key to confidence)
        
        if (weather == WeatherClass.UNKNOWN) return

        if (weather.isActive || key == "kabut") {
            handleActiveWeather(weather)
        } else {
            handleIdleWeather(key)
        }
    }

    private fun handleActiveWeather(weather: WeatherClass) {
        val currentState = _uiState.value
        val cuaca = weather.key
        
        // Kabut Special Case
        if (cuaca == "kabut") {
            if (!currentState.prevWasRain) return
            if (currentState.state == WeatherState.WEATHER) {
                _uiState.value = currentState.copy(lastCuaca = "kabut")
            }
            return
        }

        when (currentState.state) {
            WeatherState.IDLE, WeatherState.POST_CD -> {
                enterWeather(cuaca)
            }
            WeatherState.RECOOLDOWN -> {
                if (currentState.countdown in RECD_BREAK_LO..RECD_BREAK_HI) {
                    enterWeather(cuaca)
                }
            }
            WeatherState.WEATHER -> {
                if (cuaca != currentState.lastCuaca) {
                    _uiState.value = currentState.copy(
                        lastCuaca = cuaca,
                        prevWasRain = weather.isRainGroup
                    )
                }
            }
            WeatherState.COOLDOWN -> { /* Unbreakable, ignore */ }
        }
    }

    private fun handleIdleWeather(cuaca: String) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(lastPassive = cuaca)
        
        if (currentState.state == WeatherState.WEATHER) {
            enterCooldown()
        }
    }

    fun updateTick() {
        val now = System.currentTimeMillis()
        val currentState = _uiState.value
        
        when (currentState.state) {
            WeatherState.WEATHER -> {
                val dur = ((now - weatherStartTime) / 1000).toInt()
                _uiState.value = currentState.copy(weatherDur = dur)
            }
            WeatherState.COOLDOWN, WeatherState.POST_CD, WeatherState.RECOOLDOWN -> {
                val elapsed = ((now - stateStartTime) / 1000).toInt()
                val total = currentState.cdTotal
                val remaining = (total - elapsed).coerceAtLeast(0)
                
                if (remaining != currentState.countdown) {
                    _uiState.value = currentState.copy(countdown = remaining)
                }
                
                if (remaining == 0) {
                    checkTransitions()
                }
            }
            else -> {}
        }
    }

    private fun checkTransitions() {
        val currentState = _uiState.value
        when (currentState.state) {
            WeatherState.COOLDOWN -> enterPostCd()
            WeatherState.POST_CD -> enterRecooldown()
            WeatherState.RECOOLDOWN -> {
                _uiState.value = currentState.copy(
                    state = WeatherState.IDLE,
                    countdown = 0,
                    prevWasRain = false
                )
            }
            else -> {}
        }
    }

    private fun enterWeather(cuaca: String) {
        val now = System.currentTimeMillis()
        weatherStartTime = now
        stateStartTime = now
        val weather = WeatherClass.fromKey(cuaca)
        _uiState.value = _uiState.value.copy(
            state = WeatherState.WEATHER,
            lastCuaca = cuaca,
            countdown = 0,
            weatherDur = 0,
            prevWasRain = weather.isRainGroup
        )
    }

    private fun enterCooldown() {
        stateStartTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            state = WeatherState.COOLDOWN,
            countdown = COOLDOWN_SEC,
            cdTotal = COOLDOWN_SEC
        )
    }

    private fun enterPostCd() {
        stateStartTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            state = WeatherState.POST_CD,
            countdown = POST_CD_SEC,
            cdTotal = POST_CD_SEC
        )
    }

    private fun enterRecooldown() {
        stateStartTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            state = WeatherState.RECOOLDOWN,
            countdown = RECD_SEC,
            cdTotal = RECD_SEC
        )
    }

    fun reset() {
        _uiState.value = MonitorUiState()
        stateStartTime = 0
        weatherStartTime = 0
    }

    fun updateTestResult(weather: WeatherClass, confidence: Float, preview: Bitmap? = null) {
        _uiState.value = _uiState.value.copy(
            topConfidence = weather.key to confidence,
            testPreview = preview
        )
    }

    fun updateAlpha(alpha: Float) {
        _uiState.value = _uiState.value.copy(overlayAlpha = alpha)
    }

    fun setCalibrating(calibrating: Boolean) {
        _uiState.value = _uiState.value.copy(isCalibrating = calibrating)
    }
}
