package com.brenninhotools.vconverter.chart.preview

import kotlin.math.max

enum class ChartEngine(val title: String) {
    PSYCH("Psych"),
    VSLICE("V-Slice"),
    CODENAME("Codename")
}

object ChartSide {
    const val OPPONENT = 0
    const val PLAYER = 1
    const val GIRLFRIEND = 2
}

data class PreviewNote(val time: Double, val side: Int, val direction: Int, val length: Double, val kind: String)

data class PreviewEvent(val time: Double, val name: String)

data class PreviewTiming(val time: Double, val bpm: Double, val numerator: Int)

data class PreviewFocus(val time: Double, val side: Int)

data class ChartPreview(
    val song: String,
    val variation: String,
    val difficulty: String,
    val notes: List<PreviewNote>,
    val events: List<PreviewEvent>,
    val timing: List<PreviewTiming>,
    val focus: List<PreviewFocus>,
    val speed: Double,
    val player: String,
    val opponent: String,
    val girlfriend: String,
    val stage: String,
    val offset: Double = 0.0,
    val duplicates: Int = 0
) {
    val length: Double = max(
        notes.maxOfOrNull { it.time + it.length } ?: 0.0,
        events.maxOfOrNull { it.time } ?: 0.0
    ) + 1.0

    val label: String get() = buildString {
        append(difficulty.replaceFirstChar { it.uppercase() })
        if (variation.isNotEmpty() && variation != "default") append(" · ").append(variation)
    }

    val songKey: String get() = song.lowercase().replace(Regex("[^a-z0-9]+"), "")

    val playerNotes: Int get() = notes.count { it.side == ChartSide.PLAYER }

    val opponentNotes: Int get() = notes.count { it.side == ChartSide.OPPONENT }

    val otherNotes: Int get() = notes.count { it.side == ChartSide.GIRLFRIEND }

    val holdNotes: Int get() = notes.count { it.length > 0.0 }

    val bpmRange: Pair<Double, Double> get() {
        val bpms = timing.map { it.bpm }.ifEmpty { listOf(100.0) }
        return bpms.min() to bpms.max()
    }

    val signatures: List<Int> get() = timing.map { it.numerator }.distinct()

    val peakNps: Double get() {
        val player = notes.filter { it.side == ChartSide.PLAYER }.map { it.time }.sorted()
        if (player.isEmpty()) return 0.0
        var best = 0
        var start = 0
        player.forEachIndexed { index, time ->
            while (time - player[start] >= 1000.0) start++
            best = max(best, index - start + 1)
        }
        return best.toDouble()
    }

    fun beatLines(): List<Pair<Double, Boolean>> {
        val result = mutableListOf<Pair<Double, Boolean>>()
        val changes = timing.ifEmpty { listOf(PreviewTiming(0.0, 100.0, 4)) }.sortedBy { it.time }
        changes.forEachIndexed { index, change ->
            val end = changes.getOrNull(index + 1)?.time ?: length
            val beatMs = 60000.0 / change.bpm.coerceAtLeast(1.0)
            var beat = 0
            var time = change.time
            var guard = 0
            while (time < end - 0.5 && guard++ < 20000) {
                result += time to (beat % change.numerator.coerceAtLeast(1) == 0)
                beat++
                time = change.time + beat * beatMs
            }
        }
        return result
    }

    fun matches(other: ChartPreview): Boolean =
        difficulty.equals(other.difficulty, ignoreCase = true) &&
            (songKey == other.songKey || songKey.startsWith(other.songKey) || other.songKey.startsWith(songKey))
}
