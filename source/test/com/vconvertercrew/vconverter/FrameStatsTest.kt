package com.vconvertercrew.vconverter

import com.vconvertercrew.vconverter.core.FrameStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {
    @Test
    fun measuresFpsFrameTimesAndLows() {
        val stats = FrameStats()
        var now = 1_000_000_000L
        stats.frame(now)
        repeat(120) {
            now += if (it == 60) 50_000_000L else 16_666_667L
            stats.frame(now)
        }
        assertTrue(stats.fps in 50..60)
        assertEquals(60, stats.refresh)
        assertEquals(50.0, stats.worstMs, 0.01)
        assertEquals(20, stats.lowFps)
        assertTrue(stats.averageMs in 16.6..17.2)
        assertEquals(16_666_667L, stats.recent(0))
        assertEquals(120, stats.samples)
    }
}
