package com.sdkdev.sumbingcompanion.core

import androidx.compose.ui.graphics.Color

enum class WeatherClass(
    val key: String,
    val displayName: String,
    val label: String,         // bahasa Indonesia
    val color: Color,
    val iconRes: Int?,
    val isActive: Boolean,
    val isRainGroup: Boolean,
) {
    SIANG(
        "siang", "Clear Day", "Siang Cerah",
        Color(0xFFFFD75B), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_siang, isActive = false, isRainGroup = false
    ),
    MALAM(
        "malam", "Night", "Malam",
        Color(0xFFB5A0FF), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_malam, isActive = false, isRainGroup = false
    ),
    ANGIN(
        "angin", "Wind", "Angin",
        Color(0xFF5BC8FF), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_angin, isActive = true, isRainGroup = false
    ),
    HUJAN(
        "hujan", "Rain", "Hujan",
        Color(0xFF88BBFF), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_hujan, isActive = true, isRainGroup = true
    ),
    HUJAN_PETIR(
        "hujan_petir", "Thunderstorm", "Hujan Petir",
        Color(0xFFFFCC44), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_petir, isActive = true, isRainGroup = true
    ),
    BADAI(
        "badai", "Storm", "Badai",
        Color(0xFFFF8844), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_badai, isActive = true, isRainGroup = false
    ),
    KABUT(
        "kabut", "Fog", "Kabut",
        Color(0xFFAAAAAA), com.sdkdev.sumbingcompanion.R.drawable.ic_weather_kabut, isActive = true, isRainGroup = false
    ),
    UNKNOWN(
        "?", "Unknown", "Tidak Diketahui",
        Color(0xFF7D8590), null, isActive = false, isRainGroup = false
    );

    companion object {
        fun fromString(key: String): WeatherClass = fromKey(key)

        fun fromKey(key: String): WeatherClass =
            entries.find { it.key == key } ?: UNKNOWN

        fun fromIndex(index: Int): WeatherClass = when (index) {
            0 -> ANGIN
            1 -> BADAI
            2 -> HUJAN
            3 -> HUJAN_PETIR
            4 -> KABUT
            5 -> MALAM
            6 -> SIANG
            else -> UNKNOWN
        }
    }
}
