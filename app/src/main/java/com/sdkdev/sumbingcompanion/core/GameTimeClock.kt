package com.sdkdev.sumbingcompanion.core

import android.os.SystemClock
import kotlin.math.ceil

data class GameTimeSnapshot(
    val synced: Boolean = false,
    val running: Boolean = false,
    val stage: Int = 0,
    val gameTime: String = "—",
    val nextGameTime: String = "—",
    val durationSeconds: Int = 0,
    val remainingSeconds: Int = 0
)

class GameTimeClock(
    private val elapsedClockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    companion object {
        val STAGES = listOf(
            "05:59" to 300,
            "06:59" to 300,
            "08:59" to 300,
            "10:29" to 300,
            "11:59" to 300,
            "13:29" to 300,
            "14:59" to 300,
            "16:29" to 300,
            "17:29" to 300,
            "18:29" to 120,
            "19:29" to 120,
            "20:59" to 120,
            "00:00" to 360,
            "01:59" to 120,
            "03:59" to 120
        )
    }

    private var synced = false
    private var running = false
    private var stage = 0
    private var gameTime = "—"
    private var nextGameTime = "—"
    private var durationSeconds = 0
    private var remainingAtAnchor = 0.0
    private var anchorElapsedMs = elapsedClockMs()

    fun applyServer(
        stage: Int,
        gameTime: String,
        nextGameTime: String,
        stageDurationMs: Double,
        remainingMs: Double,
        running: Boolean,
        halfRttMs: Double = 0.0
    ): GameTimeSnapshot {
        require(stage in 1..STAGES.size) { "Game Time stage di luar range" }
        require(gameTime.isNotBlank()) { "Game Time kosong" }
        require(stageDurationMs > 0.0) { "Durasi Game Time tidak valid" }
        require(remainingMs >= 0.0) { "Sisa Game Time tidak valid" }

        this.synced = true
        this.running = running
        this.stage = stage
        this.gameTime = gameTime
        this.nextGameTime = nextGameTime.ifBlank { STAGES[stage % STAGES.size].first }
        this.durationSeconds = (stageDurationMs / 1000.0).toInt().coerceAtLeast(1)
        this.remainingAtAnchor = (
            remainingMs - if (running) halfRttMs.coerceAtLeast(0.0) else 0.0
        ).coerceAtLeast(0.0) / 1000.0
        this.anchorElapsedMs = elapsedClockMs()

        return snapshot()
    }

    fun snapshot(): GameTimeSnapshot {
        if (!synced) return GameTimeSnapshot()

        val now = elapsedClockMs()
        val elapsed = if (running) {
            ((now - anchorElapsedMs).coerceAtLeast(0L)) / 1000.0
        } else 0.0

        var remaining = remainingAtAnchor - elapsed

        while (running && remaining <= 0.0) {
            val overshoot = -remaining
            val nextStage = stage % STAGES.size + 1
            val entry = STAGES[nextStage - 1]

            stage = nextStage
            gameTime = entry.first
            durationSeconds = entry.second
            nextGameTime = STAGES[nextStage % STAGES.size].first
            remainingAtAnchor = durationSeconds.toDouble()
            anchorElapsedMs = (now - overshoot * 1000.0).toLong()
            remaining = durationSeconds - overshoot
        }

        return GameTimeSnapshot(
            synced = true,
            running = running,
            stage = stage,
            gameTime = gameTime,
            nextGameTime = nextGameTime,
            durationSeconds = durationSeconds,
            remainingSeconds = ceil(remaining.coerceAtLeast(0.0)).toInt()
        )
    }
}
