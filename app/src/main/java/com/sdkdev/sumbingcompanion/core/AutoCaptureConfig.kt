package com.sdkdev.sumbingcompanion.core

data class AutoCaptureConfig(
    val enabled: Boolean = false,
    val intervalSec: Int = 5,
    val maxPerClass: Int = 200,
    val captureOnStates: Set<WeatherState> = setOf(
        WeatherState.WEATHER,
        WeatherState.IDLE,
    ),
)

data class AutoCaptureEvent(
    val label: String,
    val filePath: String,
    val totalForClass: Int,
)
