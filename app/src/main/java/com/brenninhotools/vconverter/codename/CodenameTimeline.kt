package com.brenninhotools.vconverter.codename

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import com.brenninhotools.vconverter.core.sortedMutableSet

class CodenameTimeline(
    bpm: Double,
    beatsPerMeasure: Double = 4.0,
    stepsPerBeat: Int = 4,
    events: List<CodenameEvent> = emptyList()
) {
    data class Change(
        val songTime: Double,
        var stepTime: Double,
        var beatTime: Double,
        var measureTime: Double,
        val bpm: Double,
        var beatsPerMeasure: Double,
        var stepsPerBeat: Int,
        var continuous: Boolean = false,
        var endSongTime: Double = 0.0,
        var endStepTime: Double = 0.0,
        var previousBpm: Double = 0.0
    ) {
        val measureSteps: Double get() = beatsPerMeasure * stepsPerBeat
    }

    data class Span(val startStep: Int, val endStep: Int, val startTime: Double, val endTime: Double) {
        val steps: Int get() = endStep - startStep
        val beats: Double get() = steps / 4.0
        val bpm: Double get() = if (endTime > startTime) steps * 15000.0 / (endTime - startTime) else 0.0
    }

    val changes: List<Change>
    val ignored: List<CodenameEvent>

    init {
        val list = mutableListOf(Change(0.0, 0.0, 0.0, 0.0, bpm, beatsPerMeasure.takeIf { it > 0 } ?: 4.0, stepsPerBeat.coerceAtLeast(1)))
        val skipped = mutableListOf<CodenameEvent>()
        val relevant = events.filter { it.name in TIMING_EVENTS && it.params.isNotEmpty() }
            .sortedWith(compareBy<CodenameEvent> { it.time }.thenBy { if (it.name == "Time Signature Change") 0 else 1 })
        relevant.forEach { e ->
            val current = list.last()
            if (current.continuous && e.time < current.endSongTime - 1e-9) {
                skipped += e
                return@forEach
            }
            when (e.name) {
                "BPM Change" -> {
                    val target = number(e.params.getOrNull(0)) ?: return@forEach
                    if (target > 0 && target != current.bpm) list += map(current, e.time, target)
                }
                "Time Signature Change" -> {
                    val numerator = number(e.params.getOrNull(0)) ?: return@forEach
                    val denominator = number(e.params.getOrNull(1)) ?: 4.0
                    val asSteps = e.params.getOrNull(2) == true || e.params.getOrNull(2)?.toString() == "true"
                    var change = current
                    if (change.songTime != e.time) {
                        change = map(current, e.time, current.bpm)
                        list += change
                    }
                    change.beatsPerMeasure = numerator
                    change.stepsPerBeat = (if (asSteps) denominator.toInt() else floor(16.0 / denominator).toInt()).coerceAtLeast(1)
                    change.stepTime = floor(change.stepTime + .99998)
                    change.beatTime = floor(change.beatTime + .99998)
                    change.measureTime = floor(change.measureTime + .99998)
                }
                "Continuous BPM Change" -> {
                    val target = number(e.params.getOrNull(0)) ?: return@forEach
                    val steps = number(e.params.getOrNull(1)) ?: 4.0
                    if (target <= 0 || target == current.bpm) {
                        skipped += e
                        return@forEach
                    }
                    val previous = current.bpm
                    val change = map(current, e.time, target)
                    change.endSongTime = e.time + steps / (target - previous) * ln(target / previous) * 15000.0
                    change.endStepTime = change.stepTime + steps
                    change.continuous = true
                    change.previousBpm = previous
                    list += change
                }
            }
        }
        changes = list
        ignored = skipped
    }

    private fun map(current: Change, time: Double, bpm: Double): Change {
        val stepTime = (if (current.continuous) current.endStepTime else current.stepTime) +
            (time - if (current.continuous) current.endSongTime else current.songTime) / (15000.0 / current.bpm)
        val beatTime = current.beatTime + (stepTime - current.stepTime) / current.stepsPerBeat
        val measureTime = current.measureTime + (beatTime - current.beatTime) / current.beatsPerMeasure
        return Change(time, stepTime, beatTime, measureTime, bpm, current.beatsPerMeasure, current.stepsPerBeat)
    }

    val hasContinuous: Boolean get() = changes.any { it.continuous }

    val firstBpm: Double get() = changes.first().bpm

    private fun indexAtTime(time: Double): Int {
        var index = 0
        for (i in changes.indices) if (changes[i].songTime <= time + 1e-9) index = i
        return index
    }

    private fun indexAtStep(step: Double): Int {
        var index = 0
        for (i in changes.indices) if (changes[i].stepTime <= step + 1e-9) index = i
        return index
    }

    fun bpmAt(time: Double): Double {
        val index = indexAtTime(time)
        val c = changes[index]
        if (c.continuous && index > 0 && time < c.endSongTime) {
            if (time <= c.songTime) return c.previousBpm
            val ratio = (time - c.songTime) / (c.endSongTime - c.songTime)
            return c.previousBpm.pow(1 - ratio) * c.bpm.pow(ratio)
        }
        return c.bpm
    }

    fun stepAt(time: Double): Double {
        val index = indexAtTime(time)
        val c = changes[index]
        if (index == 0) return time / (15000.0 / c.bpm)
        if (c.continuous && time > c.songTime) {
            val prev = c.previousBpm
            return if (time > c.endSongTime) {
                c.stepTime + ((c.endSongTime - c.songTime) * (c.bpm - prev) / ln(c.bpm / prev) + (time - c.endSongTime) * c.bpm) / 15000.0
            } else {
                val current = bpmAt(time)
                if (abs(current - prev) < 1e-12) c.stepTime + (time - c.songTime) * prev / 15000.0
                else c.stepTime + (time - c.songTime) * (current - prev) / ln(current / prev) / 15000.0
            }
        }
        return c.stepTime + (time - c.songTime) / (15000.0 / c.bpm)
    }

    fun timeAt(step: Double): Double {
        val index = indexAtStep(step)
        val c = changes[index]
        if (!c.continuous || index == 0) return c.songTime + (step - c.stepTime) * (15000.0 / c.bpm)
        var low = c.songTime
        var high = maxOf(c.endSongTime, c.songTime + 1.0)
        while (stepAt(high) < step) high += (high - low).coerceAtLeast(1.0)
        repeat(80) {
            val mid = (low + high) / 2
            if (stepAt(mid) < step) low = mid else high = mid
        }
        return (low + high) / 2
    }

    fun beatAt(time: Double): Double {
        val step = stepAt(time)
        val c = changes[indexAtStep(step)]
        return c.beatTime + (step - c.stepTime) / c.stepsPerBeat
    }

    fun changeAtStep(step: Double): Change = changes[indexAtStep(step)]

    fun spans(endTime: Double): List<Span> {
        val endStep = ceil(stepAt(endTime.coerceAtLeast(0.0)) - 1e-6).toInt().coerceAtLeast(1)
        val breaks = sortedMutableSet(0)
        changes.forEachIndexed { i, c ->
            val start = c.stepTime
            val stop = changes.getOrNull(i + 1)?.stepTime ?: (endStep + c.measureSteps * 2)
            breaks += start.roundToInt()
            var m = ceil(c.measureTime - 1e-6)
            var s = c.stepTime + (m - c.measureTime) * c.measureSteps
            var guard = 0
            while (s < stop - 1e-6 && guard++ < 100000) {
                breaks += s.roundToInt()
                m += 1.0
                s = c.stepTime + (m - c.measureTime) * c.measureSteps
            }
            if (c.continuous) {
                var r = c.stepTime
                while (r < c.endStepTime - 1e-6) {
                    breaks += r.roundToInt()
                    r += 4.0
                }
                breaks += c.endStepTime.roundToInt()
            }
        }
        val lastMeasureEnd = run {
            val c = changeAtStep(endStep.toDouble())
            val m = ceil(c.measureTime + (endStep - c.stepTime) / c.measureSteps - 1e-6)
            (c.stepTime + (m - c.measureTime) * c.measureSteps).roundToInt().coerceAtLeast(endStep)
        }
        breaks += lastMeasureEnd
        val list = breaks.filter { it in 0..lastMeasureEnd }.sorted()
        return list.zipWithNext { a, b -> Span(a, b, timeAt(a.toDouble()), timeAt(b.toDouble())) }.filter { it.steps > 0 }
    }

    companion object {
        val TIMING_EVENTS = setOf("BPM Change", "Time Signature Change", "Continuous BPM Change")

        fun number(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}
