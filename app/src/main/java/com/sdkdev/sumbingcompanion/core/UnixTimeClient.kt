package com.sdkdev.sumbingcompanion.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.roundToInt

data class ServerGameTime(
    val running: Boolean,
    val stage: Int,
    val gameTime: String,
    val nextGameTime: String,
    val stageDurationMs: Double,
    val remainingMs: Double
)

data class TimeSyncResult(
    val running: Boolean,
    val remainingSeconds: Int,
    val durationSeconds: Int,
    val cycle: String?,
    val rttMs: Int,
    val halfRttMs: Double,
    val gameTime: ServerGameTime?
)

object UnixTimeClient {
    const val DEFAULT_URL = "https://cd.unixteam.my.id/api/time"

    fun fetch(url: String = DEFAULT_URL): TimeSyncResult {
        val started = System.nanoTime()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Server HTTP $code")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val received = System.nanoTime()
            val json = JSONObject(body)

            val running = json.optBoolean("running", false)
            val remainingMs = json.optDouble("remaining_ms", Double.NaN)
            val durationMs = json.optDouble("duration_ms", Double.NaN)

            if (durationMs.isNaN() || remainingMs.isNaN() || durationMs <= 0 || remainingMs < 0) {
                throw IllegalArgumentException("Data countdown server tidak valid")
            }

            val rttMsDouble = (received - started) / 1_000_000.0
            val halfRttMs = (rttMsDouble / 2.0).coerceAtLeast(0.0)
            val adjustedMs = (remainingMs - halfRttMs).coerceAtLeast(0.0)

            val gameJson = json.optJSONObject("game_time")
            val gameTime = gameJson?.let {
                ServerGameTime(
                    running = it.optBoolean("running", false),
                    stage = it.getInt("stage"),
                    gameTime = it.getString("game_time"),
                    nextGameTime = it.optString("next_game_time", ""),
                    stageDurationMs = it.getDouble("stage_duration_ms"),
                    remainingMs = it.getDouble("remaining_ms")
                )
            }

            return TimeSyncResult(
                running = running,
                remainingSeconds = ceil(adjustedMs / 1000.0).toInt(),
                durationSeconds = (durationMs / 1000.0).roundToInt().coerceAtLeast(1),
                cycle = if (json.has("cycle") && !json.isNull("cycle")) json.get("cycle").toString() else null,
                rttMs = rttMsDouble.roundToInt(),
                halfRttMs = halfRttMs,
                gameTime = gameTime
            )
        } finally {
            connection.disconnect()
        }
    }
}
