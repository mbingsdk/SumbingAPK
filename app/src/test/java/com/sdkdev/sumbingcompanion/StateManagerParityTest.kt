package com.sdkdev.sumbingcompanion

import com.sdkdev.sumbingcompanion.core.StateManager
import com.sdkdev.sumbingcompanion.core.WeatherClass
import com.sdkdev.sumbingcompanion.core.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateManagerParityTest {
    @Test
    fun cooldownUsesRemainderOfElevenTwentyNineCycle() {
        var wall = 1_000_000L
        var elapsed = 100_000L
        val manager = StateManager(
            wallClockMs = { wall },
            elapsedClockMs = { elapsed }
        )

        manager.handleDetection(WeatherClass.HUJAN, 0.9f)

        wall += 270_000L
        elapsed += 270_000L
        manager.handleDetection(WeatherClass.SIANG, 0.9f)

        assertEquals(WeatherState.COOLDOWN, manager.uiState.value.state)
        assertEquals(StateManager.WEATHER_CYCLE_SEC - 270, manager.uiState.value.countdown)
    }

    @Test
    fun syncDuringWeatherPreservesWeatherAndReanchorsCycle() {
        var wall = 1_000_000L
        var elapsed = 100_000L
        val manager = StateManager(
            wallClockMs = { wall },
            elapsedClockMs = { elapsed }
        )

        manager.handleDetection(WeatherClass.HUJAN, 0.9f)

        wall += 249_000L
        elapsed += 249_000L
        manager.syncCountdown(440, 689)

        assertEquals(WeatherState.WEATHER, manager.uiState.value.state)
        assertEquals("hujan", manager.uiState.value.lastCuaca)
        assertEquals(249, manager.uiState.value.weatherDur)

        wall += 21_000L
        elapsed += 21_000L
        manager.handleDetection(WeatherClass.SIANG, 0.9f)

        assertEquals(WeatherState.COOLDOWN, manager.uiState.value.state)
        assertEquals(419, manager.uiState.value.countdown)
    }

    @Test
    fun recooldownLoopsBackToExpectedWeatherWindow() {
        var wall = 1_000_000L
        var elapsed = 100_000L
        val manager = StateManager(
            wallClockMs = { wall },
            elapsedClockMs = { elapsed }
        )

        manager.syncCountdown(0, 689)
        assertEquals(WeatherState.POST_CD, manager.uiState.value.state)
        assertEquals(5, manager.uiState.value.countdown)

        wall += 5_000L
        elapsed += 5_000L
        manager.updateTick()

        assertEquals(WeatherState.RECOOLDOWN, manager.uiState.value.state)
        assertEquals(StateManager.RECD_SEC, manager.uiState.value.countdown)

        wall += StateManager.RECD_SEC * 1_000L
        elapsed += StateManager.RECD_SEC * 1_000L
        manager.updateTick()

        assertEquals(WeatherState.POST_CD, manager.uiState.value.state)
        assertEquals(5, manager.uiState.value.countdown)
    }
}
