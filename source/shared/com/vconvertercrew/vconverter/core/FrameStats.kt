package com.vconvertercrew.vconverter.core

import kotlin.math.max
import kotlin.math.roundToInt

class FrameStats(private val capacity: Int = 240, private val windowNanos: Long = 500_000_000L) {
    private val durations = LongArray(capacity)
    private var head = 0
    private var count = 0
    private var last = 0L
    private var windowStart = 0L
    private var windowFrames = 0

    var fps: Int = 0
        private set
    var refresh: Int = 0
        private set

    fun frame(now: Long): Boolean {
        if (last == 0L) {
            last = now
            windowStart = now
            return false
        }
        val duration = now - last
        last = now
        if (duration <= 0L) return false
        durations[head] = duration
        head = (head + 1) % capacity
        if (count < capacity) count++
        windowFrames++
        val elapsed = now - windowStart
        if (elapsed >= windowNanos) {
            fps = (windowFrames * 1_000_000_000.0 / elapsed).roundToInt()
            refresh = max(refresh, ((fps + 5) / 10) * 10).coerceAtMost(500)
            windowFrames = 0
            windowStart = now
            return true
        }
        return false
    }

    val samples: Int get() = count

    fun recent(index: Int): Long = if (index < count) durations[(head - 1 - index + capacity) % capacity] else 0L

    val averageMs: Double get() = if (count == 0) 0.0 else (0 until count).sumOf { durations[it] } / count / 1_000_000.0

    val worstMs: Double get() = if (count == 0) 0.0 else (0 until count).maxOf { durations[it] } / 1_000_000.0

    val lowFps: Int
        get() {
            if (count == 0) return 0
            val sorted = (0 until count).map { durations[it] }.sortedDescending()
            val slowest = sorted.take(max(1, count / 100))
            val average = slowest.average()
            return if (average <= 0.0) 0 else (1_000_000_000.0 / average).roundToInt()
        }

    fun reset() {
        head = 0
        count = 0
        last = 0L
        windowFrames = 0
        fps = 0
    }
}
