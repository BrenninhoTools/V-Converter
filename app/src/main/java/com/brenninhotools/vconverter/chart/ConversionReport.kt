package com.brenninhotools.vconverter.chart

import java.util.Locale
import java.util.TreeMap
import java.util.TreeSet

class ConversionReport {
    val noteCounts = LinkedHashMap<String, Int>()
    val duplicateNotes = LinkedHashMap<String, Int>()
    val unsupportedEvents = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
    val noteKinds = TreeSet<String>()
    val warnings = mutableListOf<String>()
    var cameraEvents = 0
    var convertedEvents = 0

    fun warn(message: String) {
        if (message !in warnings) warnings += message
    }

    fun skipEvent(name: String) {
        unsupportedEvents[name] = (unsupportedEvents[name] ?: 0) + 1
    }

    fun summary(): String = buildString {
        appendLine("Notes")
        noteCounts.forEach { (difficulty, count) ->
            val dupes = duplicateNotes[difficulty] ?: 0
            append("  • ").append(difficulty).append(": ").append(count)
            if (dupes > 0) append(" (").append(dupes).append(" stacked duplicates removed)")
            appendLine()
        }
        appendLine()
        appendLine("Events")
        appendLine("  • Camera focus events generated from sections: $cameraEvents")
        appendLine("  • Psych events converted: $convertedEvents")
        if (unsupportedEvents.isNotEmpty()) {
            appendLine("  • Skipped (no V-Slice equivalent yet):")
            unsupportedEvents.forEach { (name, count) -> appendLine("      - $name ×$count") }
        }
        if (noteKinds.isNotEmpty()) {
            appendLine()
            appendLine("Note kinds used")
            appendLine("  ${noteKinds.joinToString(", ")}")
            appendLine("  V-Slice only reacts to a note kind when a NoteKind script with that id exists in your mod.")
        }
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("Warnings")
            warnings.forEach { appendLine("  • $it") }
        }
    }.trimEnd()

    companion object {
        fun formatMs(value: Double): String = String.format(Locale.US, "%.0f ms", value)
    }
}
