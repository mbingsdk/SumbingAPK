package com.sdkdev.sumbingcompanion.core

import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

enum class WeatherState { IDLE, WEATHER, COOLDOWN, POST_CD, RECOOLDOWN }

data class MonitorUiState(
    val state: WeatherState = WeatherState.IDLE,
    val lastCuaca: String = "siang",
    val countdown: Int = 0,
    val weatherDur: Int = 0,
    val cdTotal: Int = 1,
    val lastPassive: String = "siang",
    val prevWasRain: Boolean = false,
    val topConfidence: Pair<String, Float> = Pair("", 0f),
    val overlayAlpha: Float = 1.0f,
    val testPreview: Bitmap? = null,
    val isCalibrating: Boolean = false,
    val statusText: String = "Waiting for weather..."
)

class StateManager(
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
    private val elapsedClockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        const val WEATHER_CYCLE_SEC = 11 * 60 + 30
        const val POST_CD_SEC = 5
        const val RECD_SEC = WEATHER_CYCLE_SEC - POST_CD_SEC
        const val RECD_ENTRY_GRACE_SEC = 5
        const val RECD_BREAK_HI = 310
        const val RECD_BREAK_LO = 270
    }

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var stateAnchorElapsedMs: Long = 0L
    private var weatherCycleStartWallMs: Long = 0L
    private var weatherSegmentStartWallMs: Long = 0L
    private var expectedWeatherStartWallMs: Long = 0L
    private var syncedCountdownAnchorElapsedMs: Long = 0L
    private var syncedCountdownStart: Int = 0

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
        val current = _uiState.value
        val cuaca = weather.key

        if (cuaca == "kabut") {
            if (!current.prevWasRain) return
            if (current.state == WeatherState.WEATHER) continueAsKabut()
            return
        }

        when (current.state) {
            WeatherState.IDLE -> enterWeather(cuaca)
            WeatherState.POST_CD -> {
                val started = expectedWeatherStartWallMs.takeIf { it > 0L } ?: wallClockMs()
                enterWeather(cuaca, started)
            }
            WeatherState.RECOOLDOWN -> {
                val inEntryGrace = current.countdown >= RECD_SEC - RECD_ENTRY_GRACE_SEC
                val inBreakWindow = current.countdown in RECD_BREAK_LO..RECD_BREAK_HI
                if (inEntryGrace || inBreakWindow) {
                    val started = if (inEntryGrace && expectedWeatherStartWallMs > 0L) {
                        expectedWeatherStartWallMs
                    } else wallClockMs()
                    enterWeather(cuaca, started)
                }
            }
            WeatherState.WEATHER -> {
                if (cuaca != current.lastCuaca) enterWeather(cuaca)
            }
            WeatherState.COOLDOWN -> Unit
        }
    }

    private fun handleIdleWeather(cuaca: String) {
        val current = _uiState.value
        _uiState.value = current.copy(lastPassive = cuaca)
        if (current.state == WeatherState.WEATHER) enterCooldown()
    }

    fun updateTick() {
        val nowElapsed = elapsedClockMs()
        val current = _uiState.value

        when (current.state) {
            WeatherState.WEATHER -> {
                val cycleStart = weatherCycleStartWallMs.takeIf { it > 0L } ?: weatherSegmentStartWallMs
                val dur = if (cycleStart > 0L) {
                    ((wallClockMs() - cycleStart) / 1000L).coerceAtLeast(0L).toInt()
                } else 0
                if (dur != current.weatherDur) _uiState.value = current.copy(weatherDur = dur)
            }
            WeatherState.COOLDOWN -> {
                val elapsedSec = ((nowElapsed - syncedCountdownAnchorElapsedMs) / 1000L).coerceAtLeast(0L).toInt()
                val remaining = (syncedCountdownStart - elapsedSec).coerceAtLeast(0)
                if (remaining != current.countdown) _uiState.value = current.copy(countdown = remaining)
                if (remaining == 0) enterPostCd()
            }
            WeatherState.POST_CD, WeatherState.RECOOLDOWN -> {
                val elapsedSec = ((nowElapsed - stateAnchorElapsedMs) / 1000L).coerceAtLeast(0L).toInt()
                val remaining = (current.cdTotal - elapsedSec).coerceAtLeast(0)
                if (remaining != current.countdown) _uiState.value = current.copy(countdown = remaining)
                if (remaining == 0) {
                    if (current.state == WeatherState.POST_CD) enterRecooldown()
                    else enterPostCd()
                }
            }
            WeatherState.IDLE -> Unit
        }
    }

    fun syncCountdown(remainingSeconds: Int, durationSeconds: Int) {
        val duration = durationSeconds.coerceAtLeast(1)
        val remaining = remainingSeconds.coerceAtLeast(0).coerceAtMost(duration)
        val nowElapsed = elapsedClockMs()

        if (_uiState.value.state == WeatherState.WEATHER) {
            val elapsed = (duration - remaining).coerceAtLeast(0)
            weatherCycleStartWallMs = wallClockMs() - elapsed * 1000L
            _uiState.value = _uiState.value.copy(
                weatherDur = elapsed,
                cdTotal = duration,
                statusText = "Weather cycle synced • ${formatSeconds(remaining)} remaining"
            )
            return
        }

        weatherCycleStartWallMs = 0L
        weatherSegmentStartWallMs = 0L
        expectedWeatherStartWallMs = 0L
        syncedCountdownAnchorElapsedMs = nowElapsed
        syncedCountdownStart = remaining

        _uiState.value = _uiState.value.copy(
            state = WeatherState.COOLDOWN,
            countdown = remaining,
            cdTotal = duration,
            weatherDur = 0,
            statusText = "Synced • ${formatSeconds(remaining)} remaining"
        )

        if (remaining == 0) enterPostCd()
    }

    private fun enterWeather(cuaca: String, startedAtWallMs: Long = wallClockMs()) {
        weatherCycleStartWallMs = startedAtWallMs
        weatherSegmentStartWallMs = startedAtWallMs
        expectedWeatherStartWallMs = 0L
        stateAnchorElapsedMs = elapsedClockMs()
        val weather = WeatherClass.fromKey(cuaca)

        _uiState.value = _uiState.value.copy(
            state = WeatherState.WEATHER,
            lastCuaca = cuaca,
            countdown = 0,
            cdTotal = 1,
            weatherDur = 0,
            prevWasRain = weather.isRainGroup,
            statusText = "Active weather: ${weather.label}"
        )
    }

    private fun continueAsKabut() {
        weatherSegmentStartWallMs = wallClockMs()
        _uiState.value = _uiState.value.copy(
            lastCuaca = "kabut",
            weatherDur = ((wallClockMs() - weatherCycleStartWallMs) / 1000L).coerceAtLeast(0L).toInt(),
            prevWasRain = false,
            statusText = "Kabut • waiting for Day/Night"
        )
    }

    private fun enterCooldown() {
        val cycleStart = weatherCycleStartWallMs.takeIf { it > 0L } ?: weatherSegmentStartWallMs
        val elapsedWeather = if (cycleStart > 0L) {
            ((wallClockMs() - cycleStart).coerceAtLeast(0L) / 1000.0)
        } else 0.0
        val remaining = ceil(WEATHER_CYCLE_SEC - elapsedWeather).toInt().coerceAtLeast(0)

        weatherCycleStartWallMs = 0L
        weatherSegmentStartWallMs = 0L
        expectedWeatherStartWallMs = 0L
        syncedCountdownAnchorElapsedMs = elapsedClockMs()
        syncedCountdownStart = remaining

        _uiState.value = _uiState.value.copy(
            state = WeatherState.COOLDOWN,
            countdown = remaining,
            cdTotal = remaining.coerceAtLeast(1),
            statusText = "Cooldown • ${formatSeconds(remaining)} remaining in 11:30 cycle"
        )

        if (remaining == 0) enterPostCd()
    }

    private fun enterPostCd() {
        expectedWeatherStartWallMs = wallClockMs()
        stateAnchorElapsedMs = elapsedClockMs()
        _uiState.value = _uiState.value.copy(
            state = WeatherState.POST_CD,
            countdown = POST_CD_SEC,
            cdTotal = POST_CD_SEC,
            statusText = "5-second window • watching for early weather"
        )
    }

    private fun enterRecooldown() {
        stateAnchorElapsedMs = elapsedClockMs()
        _uiState.value = _uiState.value.copy(
            state = WeatherState.RECOOLDOWN,
            countdown = RECD_SEC,
            cdTotal = RECD_SEC,
            statusText = "Re-cooldown • break window 5:10–4:30"
        )
    }

    fun reset() {
        val current = _uiState.value
        _uiState.value = MonitorUiState(
            overlayAlpha = current.overlayAlpha,
            isCalibrating = current.isCalibrating
        )
        stateAnchorElapsedMs = 0L
        weatherCycleStartWallMs = 0L
        weatherSegmentStartWallMs = 0L
        expectedWeatherStartWallMs = 0L
        syncedCountdownAnchorElapsedMs = 0L
        syncedCountdownStart = 0
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

    fun setStatus(text: String) {
        _uiState.value = _uiState.value.copy(statusText = text)
    }

    private fun formatSeconds(sec: Int): String {
        val safe = sec.coerceAtLeast(0)
        return "%d:%02d".format(safe / 60, safe % 60)
    }
}
