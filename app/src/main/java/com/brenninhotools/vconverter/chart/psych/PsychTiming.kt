package com.brenninhotools.vconverter.chart.psych

class PsychTiming(song: PsychSong) {
    data class TimeChange(val time: Double, val beat: Double, val bpm: Double)

    val sectionStarts: List<Double>
    val changes: List<TimeChange>
    val irregularSections: Int

    init {
        val starts = ArrayList<Double>(song.sections.size)
        val list = mutableListOf(TimeChange(0.0, 0.0, song.bpm))
        var bpm = song.bpm
        var time = 0.0
        var beat = 0.0
        var irregular = 0

        for (section in song.sections) {
            if (section.changeBpm && section.bpm > 0.0 && section.bpm != bpm) {
                bpm = section.bpm
                if (time == 0.0) list[0] = TimeChange(0.0, 0.0, bpm) else list += TimeChange(time, beat, bpm)
            }
            if (section.beats != 4.0) irregular++
            starts += time
            time += section.beats * 60000.0 / bpm
            beat += section.beats
        }

        sectionStarts = starts
        changes = list
        irregularSections = irregular
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
