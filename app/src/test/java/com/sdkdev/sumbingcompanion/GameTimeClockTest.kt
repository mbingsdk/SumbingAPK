package com.sdkdev.sumbingcompanion

import com.sdkdev.sumbingcompanion.core.GameTimeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTimeClockTest {
    @Test
    fun stageCountdownRunsLocallyAndJumps() {
        var elapsed = 10_000L
        val clock = GameTimeClock(elapsedClockMs = { elapsed })

        val initial = clock.applyServer(
            stage = 9,
            gameTime = "17:29",
            nextGameTime = "18:29",
            stageDurationMs = 300_000.0,
            remainingMs = 1_000.0,
            running = true
        )

        assertEquals(9, initial.stage)
        assertEquals("17:29", initial.gameTime)

        elapsed += 2_000L
        val next = clock.snapshot()

        assertEquals(10, next.stage)
        assertEquals("18:29", next.gameTime)
        assertEquals("19:29", next.nextGameTime)
        assertEquals(120, next.durationSeconds)
    }

    @Test
    fun scheduleMatchesDesktopContract() {
        assertEquals(15, GameTimeClock.STAGES.size)
        assertEquals("05:59" to 300, GameTimeClock.STAGES[0])
        assertEquals("17:29" to 300, GameTimeClock.STAGES[8])
        assertEquals("18:29" to 120, GameTimeClock.STAGES[9])
        assertEquals("00:00" to 360, GameTimeClock.STAGES[12])
        assertEquals("03:59" to 120, GameTimeClock.STAGES[14])
        assertTrue(GameTimeClock.STAGES.all { it.second > 0 })
    }
}
