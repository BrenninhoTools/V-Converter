package com.brenninhotools.vconverter.chart.psych

import kotlin.math.abs
import com.brenninhotools.vconverter.core.roundLong

class PsychTiming(song: PsychSong) {
    data class TimeChange(val time: Double, val beat: Double, val bpm: Double, val numerator: Int = 4)

    val sectionStarts: List<Double>
    val changes: List<TimeChange>
    val irregularSections: Int
    val fractionalSections: Int
    val endTime: Double

    init {
        val starts = ArrayList<Double>(song.sections.size)
        val list = mutableListOf(TimeChange(0.0, 0.0, song.bpm))
        var bpm = song.bpm
        var numerator = 4
        var time = 0.0
        var beat = 0.0
        var irregular = 0
        var fractional = 0

        for (section in song.sections) {
            val newBpm = if (section.changeBpm && section.bpm > 0.0) section.bpm else bpm
            val whole = (section.beats).roundLong().toInt()
            val integral = abs(section.beats - whole) < 1e-6 && whole in 1..64
            val newNumerator = if (integral) whole else numerator
            if (section.beats != 4.0) irregular++
            if (!integral) fractional++
            if (newBpm != bpm || newNumerator != numerator) {
                bpm = newBpm
                numerator = newNumerator
                val change = TimeChange(time, beat, bpm, numerator)
                if (time == 0.0) list[0] = change else list += change
            }
            starts += time
            time += section.beats * 60000.0 / bpm
            beat += section.beats
        }

        sectionStarts = starts
        changes = list
        irregularSections = irregular
        fractionalSections = fractional
        endTime = time
    }

    fun bpmAt(time: Double): Double = (changes.lastOrNull { it.time <= time + 0.001 } ?: changes.first()).bpm

    fun stepMsAt(time: Double): Double = 15000.0 / bpmAt(time)

    fun sectionIndexAt(time: Double): Int {
        if (sectionStarts.isEmpty()) return -1
        var low = 0
        var high = sectionStarts.lastIndex
        var found = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (sectionStarts[mid] <= time + 0.001) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}
