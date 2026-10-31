package com.brenninhotools.vconverter.reverse

import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.core.sortedMutableMap
import com.brenninhotools.vconverter.core.toSortedMutableMap
import com.brenninhotools.vconverter.core.roundLong

data class VSliceSongSource(val variation: String, val metadata: VMetadata, val chart: VChart)

data class PsychChartFile(val path: String, val json: String)

data class ReverseChartResult(val songId: String, val songName: String, val files: List<PsychChartFile>, val difficulties: List<String>, val report: Report)

object VSliceToPsychChart {
    private val reverseStages: Map<String, String> = PsychToVSliceChart.stageIds.entries.associate { (psych, vslice) -> vslice.lowercase() to psych }
    private val reverseKinds: Map<String, String> = mapOf(
        "alt" to "Alt Animation",
        "hey" to "Hey!",
        "hurt" to "Hurt Note",
        "noanim" to "No Animation",
        "gf-sing" to "GF Sing"
    )
    private val psychNames: Map<String, String> by lazy {
        com.brenninhotools.vconverter.script.PsychNativeLibrary.builtinEventNames.associateBy { com.brenninhotools.vconverter.chart.PsychEventIds.idFor(it) }
    }
    private val knownDifficulties = listOf("easy", "normal", "hard", "erect", "nightmare")

    fun formatToSongPath(name: String): String =
        name.replace(Regex("[~&;:<>#\\s]"), "-").replace(Regex("[.,'\"%?!]"), "").trim().lowercase()

    fun stageFor(stage: String): String = reverseStages[stage.lowercase()] ?: stage

    fun noteTypeFor(kind: String): String = when {
        kind.isEmpty() || kind == "normal" -> ""
        else -> reverseKinds[kind.lowercase()] ?: kind
    }

    fun songName(id: String, metadata: VMetadata?): String {
        val name = metadata?.songName?.trim().orEmpty()
        return if (name.isNotEmpty() && formatToSongPath(name) == id) name else id
    }

    fun songIdFor(songId: String, variation: String): String =
        formatToSongPath(if (variation.isEmpty() || variation == "default") songId else "$songId-$variation").ifEmpty { "song" }

    fun convertAll(songId: String, sources: List<VSliceSongSource>, stageZoom: Double? = null): List<ReverseChartResult> {
        require(sources.isNotEmpty()) { "Add the song's metadata and chart files." }
        return sources.groupBy { it.variation }.toSortedMutableMap().map { (variation, group) ->
            convert(songIdFor(songId, variation), group, stageZoom)
        }
    }

    fun convert(songId: String, sources: List<VSliceSongSource>, stageZoom: Double? = null): ReverseChartResult {
        require(sources.isNotEmpty()) { "Add the song's metadata and chart files." }
        val id = formatToSongPath(songId).ifEmpty { "song" }
        val report = ReportBuilder()
        val files = mutableListOf<PsychChartFile>()
        val difficulties = mutableListOf<String>()
        val skipped = sortedMutableMap<String, Int>()
        val mainMeta = sources.firstOrNull { it.variation.isEmpty() }?.metadata ?: sources.first().metadata
        val name = songName(id, mainMeta)
        var totalNotes = 0
        var lostFocus = 0

        sources.sortedBy { it.variation }.forEach { source ->
            val meta = source.metadata
            val sections = buildSections(meta.timeChanges, source.chart, meta)
            val focus = source.chart.events.filter { it.kind == "FocusCamera" }
            val diffs = meta.difficulties.ifEmpty { source.chart.notes.keys.toList() }
            diffs.forEach { difficulty ->
                val notes = source.chart.notes[difficulty] ?: emptyList()
                val speed = source.chart.scrollSpeed[difficulty] ?: source.chart.scrollSpeed["default"] ?: source.chart.scrollSpeed.values.firstOrNull() ?: 1.0
                val built = sections.map { it.copy(notes = mutableListOf()) }
                notes.forEach { note ->
                    val index = sectionIndex(built, note.time)
                    val entry = JSONArray().put(note.time.toJsonNumber()).put(note.data.coerceIn(0, 7)).put(note.length.toJsonNumber())
                    val type = noteTypeFor(note.kind)
                    if (type.isNotEmpty()) entry.put(type)
                    built[index].notes += entry
                }
                lostFocus = applyFocus(built, focus)
                val events = JSONArray()
                psychEvents(source.chart.events, meta, speed, stageZoom, skipped).forEach { (time, list) ->
                    val group = JSONArray()
                    list.forEach { group.put(JSONArray(it)) }
                    events.put(JSONArray().put(time.toJsonNumber()).put(group))
                }
                val root = JSONObject()
                    .put("song", name)
                    .put("notes", JSONArray(built.map { it.toJson() }))
                    .put("events", events)
                    .put("bpm", meta.timeChanges.first().bpm.toJsonNumber())
                    .put("needsVoices", true)
                    .put("speed", speed.toJsonNumber())
                    .put("offset", meta.offsetInstrumental.toJsonNumber())
                    .put("player1", meta.player)
                    .put("player2", meta.opponent)
                    .put("gfVersion", meta.girlfriend.ifEmpty { "gf" })
                    .put("stage", stageFor(meta.stage))
                    .put("format", "psych_v1")
                if (meta.noteStyle.equals("pixel", ignoreCase = true)) root.put("arrowSkin", "pixelUI/NOTE_assets").put("splashSkin", "pixelUI/noteSplashes")
                val suffix = if (difficulty.equals("normal", ignoreCase = true)) "" else "-" + difficulty.lowercase()
                files += PsychChartFile("data/$id/$id$suffix.json", root.toString())
                difficulties += difficulty.lowercase()
                totalNotes += notes.size
                report.stat(difficulty.replaceFirstChar { it.uppercase() }, notes.size)
            }
        }

        report.section("Difficulties", difficulties.map { d -> "data/$id/$id${if (d == "normal") "" else "-$d"}.json" })
        if (difficulties.any { it !in listOf("easy", "normal", "hard") }) {
            report.warn("Psych only lists Easy/Normal/Hard by default. Add the extra difficulties to the week's \"difficulties\" field (the Weeks converter does this).")
        }
        if (lostFocus > 0) report.warn("$lostFocus camera focus changes happen in the middle of a section; Psych moves the camera once per section, so they snap to the section they fall in.")
        if (skipped.isNotEmpty()) report.section("Events with no Psych equivalent (kept under their V-Slice names for scripts)", skipped.map { (k, v) -> "$k ×$v" })
        if (mainMeta.offsetInstrumental != 0.0) report.section("Audio offset", listOf("The V-Slice instrumental offset (${mainMeta.offsetInstrumental.toJsonNumber()} ms) became the Psych chart offset."))
        if (mainMeta.noteStyle.equals("pixel", ignoreCase = true)) report.warn("Pixel note style: the charts use Psych's pixelUI skins; make sure the stage is a pixel stage (isPixelStage).")
        report.section("Audio", listOf("songs/$id/Inst.ogg", "songs/$id/Voices.ogg or Voices-Player.ogg + Voices-Opponent.ogg"))
        if (name != mainMeta.songName && mainMeta.songName.isNotBlank()) {
            report.warn("Psych finds songs by name, so \"${mainMeta.songName}\" is saved as \"$name\" to match the folder.")
        }
        return ReverseChartResult(id, name, files, difficulties.distinct(), report.build())
    }

    private data class Section(
        val start: Double,
        val beats: Double,
        val bpm: Double,
        val changeBpm: Boolean,
        var mustHit: Boolean = false,
        var gf: Boolean = false,
        val notes: MutableList<JSONArray> = mutableListOf()
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
                .put("sectionNotes", JSONArray(notes))
                .put("sectionBeats", beats.toJsonNumber())
                .put("mustHitSection", mustHit)
                .put("gfSection", gf)
                .put("altAnim", false)
                .put("bpm", bpm.toJsonNumber())
                .put("changeBPM", changeBpm)
            return obj
        }
    }

    private fun buildSections(changes: List<VTimeChange>, chart: VChart, meta: VMetadata): List<Section> {
        val lastNote = chart.notes.values.flatten().maxOfOrNull { it.time + it.length } ?: 0.0
        val lastEvent = chart.events.maxOfOrNull { it.time } ?: 0.0
        val end = maxOf(lastNote, lastEvent) + 1.0
        val sorted = changes.sortedBy { it.time }
        val result = mutableListOf<Section>()
        var time = 0.0
        var changeIndex = 0
        var current = sorted.first()
        var previousBpm = current.bpm
        while (changeIndex + 1 < sorted.size && sorted[changeIndex + 1].time <= 0.0) current = sorted[++changeIndex]
        var guard = 0
        while ((time < end || result.isEmpty()) && guard++ < 100000) {
            val beatMs = 60000.0 / current.bpm
            val full = current.numerator.toDouble()
            val next = sorted.getOrNull(changeIndex + 1)
            var length = full * beatMs
            if (next != null && next.time < time + length - 0.001) length = next.time - time
            val beats = (length / beatMs * 1000.0).roundLong() / 1000.0
            result += Section(time, if (beats <= 0) full else beats, current.bpm, result.isNotEmpty() && current.bpm != previousBpm)
            previousBpm = current.bpm
            time += length
            if (next != null && next.time <= time + 0.001) {
                changeIndex++
                current = next
            }
        }
        if (result.isEmpty()) result += Section(0.0, 4.0, meta.timeChanges.first().bpm, false)
        return result
    }

    private fun sectionIndex(sections: List<Section>, time: Double): Int {
        var lo = 0
        var hi = sections.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (sections[mid].start <= time + 0.5) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun focusChar(event: VEvent): Int? {
        val v = event.value
        return when (v) {
            is JSONObject -> v.value("char").asDouble(-99.0).toInt().takeIf { it != -99 }
            is Number -> v.toInt()
            else -> v?.toString()?.toIntOrNull()
        }
    }

    private fun applyFocus(sections: List<Section>, focus: List<VEvent>): Int {
        var state = 1
        var pointer = 0
        var lost = 0
        sections.forEachIndexed { index, section ->
            val sectionEnd = sections.getOrNull(index + 1)?.start ?: Double.MAX_VALUE
            while (pointer < focus.size && focus[pointer].time < sectionEnd - 0.5) {
                val char = focusChar(focus[pointer])
                if (char != null && char >= 0) {
                    if (focus[pointer].time > section.start + 1.0) lost++
                    state = char
                }
                pointer++
            }
            section.mustHit = state == 0
            section.gf = state == 2
        }
        return lost
    }

    private fun field(value: Any?, key: String): Any? = (value as? JSONObject)?.value(key)

    private fun stepMs(meta: VMetadata, time: Double): Double {
        val change = meta.timeChanges.lastOrNull { it.time <= time } ?: meta.timeChanges.first()
        return 15000.0 / change.bpm
    }

    private fun target(raw: String): String = when (raw.lowercase()) {
        "bf", "boyfriend", "player", "0" -> "BF"
        "gf", "girlfriend", "2" -> "GF"
        else -> "Dad"
    }

    private fun psychEvents(events: List<VEvent>, meta: VMetadata, speed: Double, stageZoom: Double?, skipped: MutableMap<String, Int>): List<Pair<Double, List<List<String>>>> {
        val grouped = LinkedHashMap<Double, MutableList<List<String>>>()
        fun add(time: Double, name: String, v1: String, v2: String) {
            grouped.getOrPut(time) { mutableListOf() } += listOf(name, v1, v2)
        }
        fun skip(kind: String) {
            skipped[kind] = (skipped[kind] ?: 0) + 1
        }
        events.forEach { event ->
            val v = event.value
            when (event.kind) {
                "FocusCamera" -> {
                    if (focusChar(event) == -1) {
                        add(event.time, "Camera Follow Pos", field(v, "x").asText(), field(v, "y").asText())
                    }
                }
                "PlayAnimation" -> {
                    val anim = field(v, "anim").asText()
                    val who = target(field(v, "target").asText())
                    if (anim.equals("hey", ignoreCase = true) || anim.equals("cheer", ignoreCase = true)) add(event.time, "Hey!", if (who == "GF") "GF" else "BF", "0.6")
                    else if (anim.isNotEmpty()) add(event.time, "Play Animation", anim, who)
                }
                "ScrollSpeed" -> {
                    val scroll = field(v, "scroll").asDouble(1.0)
                    val absolute = field(v, "absolute").asText() == "true"
                    val multiplier = if (absolute && speed > 0) scroll / speed else scroll
                    val instant = field(v, "ease").asText().equals("INSTANT", ignoreCase = true)
                    val seconds = if (instant) 0.0 else field(v, "duration").asDouble(4.0) * stepMs(meta, event.time) / 1000.0
                    add(event.time, "Change Scroll Speed", (multiplier).toJsonNumber().toString(), seconds.toJsonNumber().toString())
                }
                "ZoomCamera" -> {
                    val zoom = field(v, "zoom").asDouble(1.0)
                    val mode = field(v, "mode").asText()
                    val value = if (mode == "direct") zoom else stageZoom?.let { zoom * it }
                    if (value != null) add(event.time, "Set Property", "defaultCamZoom", value.toJsonNumber().toString()) else skip("ZoomCamera (stage mode)")
                }
                "SetCameraBop" -> add(event.time, "Set Property", "camZoomingMult", field(v, "intensity").asDouble(1.0).toJsonNumber().toString())
                "PsychEvent" -> add(event.time, field(v, "name").asText(), field(v, "value1").asText(), field(v, "value2").asText())
                else -> {
                    val obj = v as? JSONObject
                    if (obj != null && (obj.has("value1") || obj.has("value2"))) {
                        add(event.time, psychNames[event.kind] ?: event.kind, obj.value("value1").asText(), obj.value("value2").asText())
                    } else {
                        add(event.time, event.kind, when (v) {
                            null -> ""
                            is JSONObject -> v.toString()
                            else -> v.toString()
                        }, "")
                        skip(event.kind)
                    }
                }
            }
        }
        return grouped.entries.sortedBy { it.key }.map { it.key to it.value.toList() }
    }
}
