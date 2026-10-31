package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.vslice.VSliceChart
import com.brenninhotools.vconverter.chart.vslice.VSliceCharacters
import com.brenninhotools.vconverter.chart.vslice.VSliceEvent
import com.brenninhotools.vconverter.chart.vslice.VSliceMetadata
import com.brenninhotools.vconverter.chart.vslice.VSliceNote
import com.brenninhotools.vconverter.chart.vslice.VSliceTimeChange
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import kotlin.math.roundToLong
import com.brenninhotools.vconverter.core.sortedMutableMap
import com.brenninhotools.vconverter.core.sortedMutableSet
import com.brenninhotools.vconverter.core.CaseInsensitiveOrder
import com.brenninhotools.vconverter.core.putIfMissing

data class CodenameSong(
    val id: String,
    val meta: CodenameMeta,
    val charts: Map<String, CodenameChartData>,
    val globalEvents: List<CodenameEvent> = emptyList(),
    val variation: String = ""
)

data class CodenameChartOptions(
    val customStageIds: Set<String> = emptySet(),
    val eventDefinitions: Map<String, EventDefinition> = emptyMap(),
    val playerVocals: List<String>? = null,
    val opponentVocals: List<String>? = null,
    val instrumental: String = "",
    val songVariations: List<String> = emptyList(),
    val generatedBy: String = "V-Converter (Codename Engine to V-Slice)"
)

data class CodenameChartResult(
    val metadata: VSliceMetadata,
    val chart: VSliceChart,
    val report: Report,
    val keptEvents: Map<String, Int>,
    val noteKinds: Set<String>,
    val noteTypeNames: Map<String, String>
)

object CodenameToVSliceChart {
    val stageIds = mapOf(
        "stage" to "mainStage",
        "spooky" to "spookyMansion",
        "philly" to "phillyTrain",
        "limo" to "limoRide",
        "mall" to "mallXmas",
        "mall-evil" to "mallEvil",
        "school" to "school",
        "school-evil" to "schoolEvil",
        "tank" to "tankmanBattlefield"
    )

    private val noteKinds = mapOf(
        "alt anim note" to "alt",
        "alt animation" to "alt",
        "no anim note" to "noanim",
        "no animation" to "noanim",
        "hurt note" to "hurt",
        "hey note" to "hey"
    )

    fun noteKindFor(name: String): String = noteKinds[name.trim().lowercase()] ?: PsychToVSliceChart.noteKindFor(name)

    fun convert(song: CodenameSong, options: CodenameChartOptions = CodenameChartOptions()): CodenameChartResult {
        require(song.charts.isNotEmpty()) { "Select at least one Codename Engine chart." }
        val report = ReportBuilder()
        val ordered = song.charts.entries
            .map { PsychToVSliceChart.normalizeDifficulty(it.key) to it.value }
            .sortedWith(compareBy({ rank(it.first) }, { it.first }))
        ordered.groupBy { it.first }.forEach { (difficulty, group) ->
            require(group.size == 1) { "The difficulty \"$difficulty\" is assigned to more than one chart." }
        }
        val mainEntry = ordered.firstOrNull { it.first == "normal" } ?: ordered.first()
        val main = mainEntry.second
        val meta = song.meta.merge(main.meta)

        val player = main.strumLines.firstOrNull { it.isPlayer }
        val opponent = main.strumLines.firstOrNull { it.isOpponent }
        val girlfriend = main.strumLines.firstOrNull { it.isGirlfriend && !it.isPlayer && !it.isOpponent }
        val playerId = player?.characters?.firstOrNull() ?: "bf"
        val opponentId = opponent?.characters?.firstOrNull() ?: "dad"
        val girlfriendId = when {
            opponent?.isGirlfriend == true -> ""
            girlfriend != null -> girlfriend.characters.firstOrNull() ?: ""
            else -> ""
        }

        ordered.filter { it.second !== main }.forEach { (difficulty, chart) ->
            val p = chart.strumLines.firstOrNull { it.isPlayer }?.characters?.firstOrNull()
            val o = chart.strumLines.firstOrNull { it.isOpponent }?.characters?.firstOrNull()
            if ((p != null && p != playerId) || (o != null && o != opponentId) || chart.stage != main.stage) {
                report.warn("\"$difficulty\" uses different characters or stage than \"${mainEntry.first}\". The \"${mainEntry.first}\" setup was used.")
            }
            if (chart.events.toSet() != main.events.toSet()) {
                report.warn("\"$difficulty\" has different events than \"${mainEntry.first}\". V-Slice shares events between difficulties, so only the \"${mainEntry.first}\" events were kept.")
            }
        }
        main.strumLines.filter { it.characters.size > 1 }.forEach {
            report.warn("A ${positionLabel(it)} strumline sings with ${it.characters.size} characters (${it.characters.joinToString()}); V-Slice uses only \"${it.characters.first()}\".")
        }

        val kinds = sortedMutableSet<String>()
        val kindNames = LinkedHashMap<String, String>()
        val notes = LinkedHashMap<String, List<VSliceNote>>()
        val scrollSpeed = LinkedHashMap<String, Double>()
        val counts = mutableListOf<String>()
        ordered.forEach { (difficulty, chart) ->
            val result = mutableListOf<VSliceNote>()
            val seen = HashSet<Pair<Long, Int>>()
            var duplicates = 0
            var additional = 0
            var wideKeys = 0
            val players = chart.strumLines.count { it.isPlayer }
            val opponents = chart.strumLines.count { it.isOpponent }
            chart.strumLines.forEach { line ->
                if (!line.isPlayer && !line.isOpponent) {
                    additional += line.notes.size
                    return@forEach
                }
                if (line.keyCount != 4) wideKeys += line.notes.size
                line.notes.forEach { note ->
                    val lane = ((note.id % 4) + 4) % 4 + if (line.isPlayer) 0 else 4
                    val key = (note.time * 1000.0).roundToLong() to lane
                    if (!seen.add(key)) {
                        duplicates++
                        return@forEach
                    }
                    val typeName = chart.noteTypeName(note.type)
                    val kind = if (typeName.isBlank() || typeName.equals("Default Note", ignoreCase = true)) "" else noteKindFor(typeName)
                    if (kind.isNotEmpty()) {
                        kinds += kind
                        kindNames.putIfMissing(kind, typeName)
                    }
                    result += VSliceNote(note.time, lane, note.length, kind)
                }
            }
            result.sortWith(compareBy({ it.time }, { it.direction }))
            notes[difficulty] = result
            scrollSpeed[difficulty] = chart.scrollSpeed
            counts += "$difficulty: ${result.size} notes" + if (duplicates > 0) " ($duplicates stacked duplicates removed)" else ""
            report.stat(difficulty.replaceFirstChar { it.uppercaseChar() }, result.size)
            if (additional > 0) report.warn("\"$difficulty\": $additional notes on extra (girlfriend/additional) strumlines were left out; V-Slice only has a player and an opponent strumline.")
            if (wideKeys > 0) report.warn("\"$difficulty\": $wideKeys notes come from strumlines with more than 4 keys and were folded into 4 lanes.")
            if (players > 1 || opponents > 1) report.warn("\"$difficulty\" has ${players} player and ${opponents} opponent strumlines; their notes were merged.")
        }

        val allEvents = (main.events + song.globalEvents).sortedBy { it.time }
        val timing = Timing(meta)
        allEvents.forEach { timing.add(it, report) }

        val kept = sortedMutableMap<String, Int>(CaseInsensitiveOrder)
        val events = mutableListOf<VSliceEvent>()
        var converted = 0
        allEvents.forEach { event ->
            when (event.name) {
                "BPM Change", "Time Signature Change", "Continuous BPM Change" -> converted++
                else -> {
                    val native = nativeEvent(event, main, timing)
                    if (native != null) {
                        events += native
                        converted++
                    } else {
                        val definition = CodenameEvents.definitionFor(event.name, options.eventDefinitions, event.params.size)
                        events += VSliceEvent(event.time, definition.id, CodenameEvents.valueFor(definition, event.params))
                        kept[event.name] = (kept[event.name] ?: 0) + 1
                    }
                }
            }
        }
        events.sortBy { it.time }
        report.stat("Events", events.size)
        report.section("Notes", counts)
        report.section("Events", listOf("$converted Codename events converted to V-Slice events or time changes"))
        if (kept.isNotEmpty()) report.section("Kept as V-Slice events (scripts handle them)", kept.map { (name, count) -> "$name ×$count → ${CodenameEvents.eventId(name)}" })
        if (kinds.isNotEmpty()) report.section("Note kinds", kinds.map { kind -> "${kindNames[kind]} → $kind" })

        val stage = when {
            main.stage in options.customStageIds -> main.stage
            stageIds.containsKey(main.stage.lowercase()) -> stageIds.getValue(main.stage.lowercase())
            else -> {
                report.warn("Stage \"${main.stage}\" has no known V-Slice equivalent and was kept as-is. Make sure your mod has a V-Slice stage with this id.")
                main.stage
            }
        }

        val metadata = VSliceMetadata(
            songName = meta.displayName.ifBlank { song.id },
            artist = meta.artist.ifBlank { "Unknown" },
            charter = meta.charter.ifBlank { "Unknown" },
            difficulties = ordered.map { it.first },
            characters = VSliceCharacters(
                player = playerId,
                girlfriend = girlfriendId,
                opponent = opponentId,
                instrumental = options.instrumental,
                playerVocals = options.playerVocals,
                opponentVocals = options.opponentVocals
            ),
            stage = stage,
            noteStyle = if (main.stage.lowercase().startsWith("school")) "pixel" else "funkin",
            timeChanges = timing.changes(),
            songVariations = options.songVariations,
            generatedBy = options.generatedBy
        )
        if (meta.color.isNotBlank()) report.section("Not carried over", listOf("Freeplay color ${meta.color} and icon \"${meta.icon}\" (V-Slice reads the icon from the character)"))
        return CodenameChartResult(
            metadata,
            VSliceChart(scrollSpeed, events, notes, options.generatedBy),
            report.build(),
            kept,
            kinds,
            kindNames
        )
    }

    private fun rank(difficulty: String): Int = PsychToVSliceChart.difficultyOrder.indexOf(difficulty).takeIf { it >= 0 } ?: PsychToVSliceChart.difficultyOrder.size

    private fun positionLabel(line: CodenameStrumLine) = when {
        line.isPlayer -> "player"
        line.isOpponent -> "opponent"
        else -> "extra"
    }

    private fun focusFor(chart: CodenameChartData, index: Int): Int? {
        val line = chart.strumLines.getOrNull(index) ?: return null
        return when {
            line.isGirlfriend -> 2
            line.isPlayer -> 0
            line.isOpponent -> 1
            else -> 2
        }
    }

    private fun characterTarget(chart: CodenameChartData, index: Int): String {
        val line = chart.strumLines.getOrNull(index)
        return when {
            line == null -> if (index == 1) "bf" else "dad"
            line.isGirlfriend -> "gf"
            line.isPlayer -> "bf"
            line.isOpponent -> "dad"
            else -> "gf"
        }
    }

    private fun easeOf(name: Any?, dir: Any?, tween: Any?): Map<String, Any> {
        if (tween != null && !tween.asBool(true)) return mapOf("ease" to "INSTANT")
        val ease = name?.asText()?.ifBlank { null } ?: return emptyMap()
        if (ease == "CLASSIC") return mapOf("ease" to "CLASSIC")
        if (ease == "linear") return mapOf("ease" to "linear")
        return mapOf("ease" to ease, "easeDir" to (dir?.asText()?.ifBlank { null } ?: "In"))
    }

    private fun nativeEvent(event: CodenameEvent, chart: CodenameChartData, timing: Timing): VSliceEvent? {
        val p = event.params
        return when (event.name) {
            "Camera Movement" -> {
                val target = p.getOrNull(0).asDouble(0.0).toInt()
                val focus = focusFor(chart, target) ?: return null
                val value = LinkedHashMap<String, Any>()
                value["char"] = focus
                val x = p.getOrNull(5).asDouble(0.0)
                val y = p.getOrNull(6).asDouble(0.0)
                if (x != 0.0) value["x"] = x
                if (y != 0.0) value["y"] = y
                val ease = easeOf(p.getOrNull(3), p.getOrNull(4), p.getOrNull(1))
                value.putAll(ease)
                if (ease["ease"] != null && ease["ease"] != "INSTANT" && ease["ease"] != "CLASSIC") value["duration"] = p.getOrNull(2).asDouble(4.0)
                VSliceEvent(event.time, "FocusCamera", value)
            }
            "Camera Position" -> {
                if (p.getOrNull(6).asBool(false)) return null
                val value = LinkedHashMap<String, Any>()
                value["char"] = -1
                value["x"] = p.getOrNull(0).asDouble(0.0)
                value["y"] = p.getOrNull(1).asDouble(0.0)
                val ease = easeOf(p.getOrNull(4), p.getOrNull(5), p.getOrNull(2))
                value.putAll(ease)
                if (ease["ease"] != null && ease["ease"] != "INSTANT" && ease["ease"] != "CLASSIC") value["duration"] = p.getOrNull(3).asDouble(4.0)
                VSliceEvent(event.time, "FocusCamera", value)
            }
            "Play Animation" -> {
                val anim = p.getOrNull(1).asText()
                if (anim.isBlank()) return null
                VSliceEvent(event.time, "PlayAnimation", linkedMapOf("target" to characterTarget(chart, p.getOrNull(0).asDouble(0.0).toInt()), "anim" to anim, "force" to p.getOrNull(2).asBool(true)))
            }
            "Scroll Speed Change" -> {
                val value = LinkedHashMap<String, Any>()
                value["scroll"] = p.getOrNull(1).asDouble(1.0)
                val tween = p.getOrNull(0).asBool(true)
                if (!tween) {
                    value["ease"] = "INSTANT"
                } else {
                    value["duration"] = p.getOrNull(2).asDouble(4.0)
                    val ease = p.getOrNull(3).asText().ifBlank { "linear" }
                    value["ease"] = ease
                    if (ease != "linear") value["easeDir"] = p.getOrNull(4).asText().ifBlank { "In" }
                }
                value["absolute"] = !p.getOrNull(5).asBool(false)
                VSliceEvent(event.time, "ScrollSpeed", value)
            }
            "Camera Modulo Change" -> {
                val every = p.getOrNull(2).asText().uppercase()
                val interval = p.getOrNull(0).asDouble(4.0)
                val rate = when (every) {
                    "MEASURE" -> interval * timing.beatsPerMeasure
                    "STEP" -> interval / 4.0
                    else -> interval
                }
                val value = linkedMapOf<String, Any>("rate" to rate, "intensity" to p.getOrNull(1).asDouble(1.0))
                val offset = p.getOrNull(3).asDouble(0.0)
                if (offset != 0.0) value["offset"] = offset
                VSliceEvent(event.time, "SetCameraBop", value)
            }
            "Camera Zoom" -> {
                if (p.getOrNull(2).asText() == "camHUD") return null
                if (p.getOrNull(7).asBool(false)) return null
                val value = LinkedHashMap<String, Any>()
                value["zoom"] = p.getOrNull(1).asDouble(1.0)
                val tween = p.getOrNull(0).asBool(true)
                val ease = p.getOrNull(4).asText().ifBlank { "linear" }
                if (!tween || ease == "CLASSIC") {
                    value["ease"] = "INSTANT"
                } else {
                    value["duration"] = p.getOrNull(3).asDouble(4.0)
                    value["ease"] = ease
                    if (ease != "linear") value["easeDir"] = p.getOrNull(5).asText().ifBlank { "In" }
                }
                value["mode"] = if (p.getOrNull(6).asText() == "stage") "stage" else "direct"
                VSliceEvent(event.time, "ZoomCamera", value)
            }
            else -> null
        }
    }

    private class Timing(meta: CodenameMeta) {
        private data class Change(val time: Double, val bpm: Double, val numerator: Int, val denominator: Int)

        private val list = mutableListOf(Change(0.0, meta.bpm, meta.beatsPerMeasure.toInt().coerceAtLeast(1), 4))
        val beatsPerMeasure: Double get() = list.first().numerator.toDouble()

        fun add(event: CodenameEvent, report: ReportBuilder) {
            val last = list.last()
            when (event.name) {
                "BPM Change" -> {
                    val bpm = event.params.getOrNull(0).asDouble(last.bpm)
                    if (bpm > 0.0) push(Change(event.time, bpm, last.numerator, last.denominator))
                }
                "Continuous BPM Change" -> {
                    val bpm = event.params.getOrNull(0).asDouble(last.bpm)
                    if (bpm > 0.0) {
                        push(Change(event.time, bpm, last.numerator, last.denominator))
                        report.warn("\"Continuous BPM Change\" at ${event.time.toLong()} ms became an instant BPM change; V-Slice has no gradual BPM changes.")
                    }
                }
                "Time Signature Change" -> {
                    val numerator = event.params.getOrNull(0).asDouble(4.0).toInt().coerceAtLeast(1)
                    val stepsMode = event.params.getOrNull(2).asBool(false)
                    val denominator = if (stepsMode) 4 else event.params.getOrNull(1).asDouble(4.0).toInt().coerceAtLeast(1)
                    push(Change(event.time, last.bpm, numerator, denominator))
                }
            }
        }

        private fun push(change: Change) {
            val last = list.last()
            if (change.time <= last.time + 0.0001) {
                list[list.lastIndex] = change.copy(time = last.time)
            } else if (change.bpm != last.bpm || change.numerator != last.numerator || change.denominator != last.denominator) {
                list += change
            }
        }

        fun changes(): List<VSliceTimeChange> {
            var beat = 0.0
            return list.mapIndexed { index, change ->
                if (index > 0) {
                    val previous = list[index - 1]
                    beat += (change.time - previous.time) / (60000.0 / previous.bpm)
                }
                VSliceTimeChange(change.time, beat, change.bpm, change.numerator, change.denominator)
            }
        }
    }
}
