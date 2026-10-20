package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.reverse.PsychChartFile
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToLong

data class CodenamePsychOptions(
    val stageIds: Map<String, String> = emptyMap(),
    val eventDefinitions: Map<String, EventDefinition> = emptyMap(),
    val gameOverCharacter: String? = null
)

data class CodenamePsychSong(
    val songId: String,
    val songName: String,
    val files: List<PsychChartFile>,
    val difficulties: List<String>,
    val customEvents: Map<String, Int>,
    val noteTypes: Set<String>,
    val characters: Map<String, String>,
    val report: Report
)

object CodenameToPsychChart {
    val stageIds = mapOf(
        "stage" to "stage",
        "spooky" to "spooky",
        "philly" to "philly",
        "limo" to "limo",
        "mall" to "mall",
        "mall-evil" to "mallEvil",
        "school" to "school",
        "school-evil" to "schoolEvil",
        "tank" to "tank"
    )

    val noteTypes = mapOf(
        "alt anim note" to "Alt Animation",
        "alt animation" to "Alt Animation",
        "no anim note" to "No Animation",
        "no animation" to "No Animation",
        "hurt note" to "Hurt Note",
        "hurt" to "Hurt Note",
        "gf sing note" to "GF Sing",
        "gf sing" to "GF Sing",
        "hey note" to "Hey!",
        "hey!" to "Hey!"
    )

    val characterIds = mapOf("pico-speakers" to "pico-speaker")

    fun psychCharacter(id: String): String = characterIds[id.lowercase()] ?: id

    val builtinNoteTypes = setOf("Alt Animation", "No Animation", "Hurt Note", "GF Sing", "Hey!")

    private val difficultyOrder = listOf("easy", "normal", "hard", "erect", "nightmare")

    fun noteTypeFor(name: String): String = if (name.isBlank()) "" else noteTypes[name.trim().lowercase()] ?: name

    fun stageFor(stage: String, custom: Map<String, String> = emptyMap()): String = custom[stage] ?: stageIds[stage.lowercase()] ?: stage

    fun psychSongId(song: CodenameSong): String =
        SongPath.format(if (song.variation.isEmpty()) song.id else "${song.id}-${song.variation}").ifEmpty { "song" }

    const val HIDDEN_GF = "Hidden GF Sing"

    private enum class Mode { PLAYER, OPPONENT, GF, HIDDEN_GF, SPEAKER, NONE }

    private class Role(val index: Int, val name: String?, val laneBase: Int, val mode: Mode)

    private class Section(val start: Double, val end: Double, val beats: Double, val bpm: Double, val changeBpm: Boolean) {
        var mustHit = false
        var gf = false
        val notes = mutableListOf<JSONArray>()
        var playerNotes = 0
        var opponentNotes = 0
    }

    private class OutNote(val time: Double, val lane: Int, val length: Double, val type: String)

    fun convert(song: CodenameSong, options: CodenamePsychOptions = CodenamePsychOptions()): CodenamePsychSong {
        require(song.charts.isNotEmpty()) { "Add at least one Codename chart." }
        val id = psychSongId(song)
        val report = ReportBuilder()
        val files = mutableListOf<PsychChartFile>()
        val customEvents = sortedMapOf<String, Int>(String.CASE_INSENSITIVE_ORDER)
        val usedTypes = sortedSetOf<String>()
        val difficulties = song.charts.keys.sortedWith(compareBy<String> { difficultyOrder.indexOf(it.lowercase()).let { i -> if (i < 0) 99 else i } }.thenBy { it })
        var characters: Map<String, String> = emptyMap()
        val displayName = song.meta.displayName.trim()
        val name = if (displayName.isNotEmpty() && SongPath.format(displayName) == id) displayName else id

        difficulties.forEach { difficulty ->
            val chart = song.charts.getValue(difficulty)
            val meta = song.meta.merge(chart.meta)
            val events = (chart.events + song.globalEvents).sortedBy { it.time }
            val timeline = CodenameTimeline(meta.bpm, meta.beatsPerMeasure, meta.stepsPerBeat, events)
            val roles = roles(chart)
            val player = roles.firstOrNull { it.name == "bf" }?.let { chart.strumLines[it.index] }
            val opponent = roles.firstOrNull { it.name == "dad" }?.let { chart.strumLines[it.index] }
            val girlfriend = roles.firstOrNull { it.name == "gf" }?.let { chart.strumLines[it.index] }
            val opponentIsGf = opponent?.isGirlfriend == true
            val altState = altTimeline(events)
            val notes = mutableListOf<OutNote>()
            val seen = HashSet<Triple<Int, Long, String>>()
            var duplicates = 0
            var wrapped = 0
            var typedGf = 0
            val dropped = sortedMapOf<String, Int>()

            val speaker = mutableListOf<Pair<Double, Int>>()
            roles.forEach { role ->
                val line = chart.strumLines[role.index]
                if (role.mode == Mode.NONE) {
                    if (line.notes.isNotEmpty()) dropped[line.characters.joinToString().ifEmpty { "strumline ${role.index}" }] = line.notes.size
                    return@forEach
                }
                if (role.mode == Mode.SPEAKER) {
                    line.notes.forEach { speaker += it.time to Math.floorMod(it.id, 4) }
                    return@forEach
                }
                if (line.keyCount != 4) report.warn("$difficulty: strumline ${role.index} has ${line.keyCount} keys; Psych charts have 4, so lanes were wrapped.")
                line.notes.forEach { note ->
                    if (note.id >= 4 || note.id < 0) wrapped++
                    var type = noteTypeFor(chart.noteTypeName(note.type))
                    when (role.mode) {
                        Mode.GF -> if (type.isEmpty()) type = "GF Sing" else typedGf++
                        Mode.HIDDEN_GF -> if (type.isEmpty()) type = HIDDEN_GF else typedGf++
                        else -> Unit
                    }
                    if (type.isEmpty() && altState.singAt(role.index, note.time)) type = "Alt Animation"
                    val lane = role.laneBase + Math.floorMod(note.id, 4)
                    if (!seen.add(Triple(lane, (note.time * 10).roundToLong(), type))) {
                        duplicates++
                        return@forEach
                    }
                    if (type.isNotEmpty()) usedTypes += type
                    notes += OutNote(note.time, lane, note.length, type)
                }
            }
            if (speaker.isNotEmpty() && files.none { it.path.endsWith("/picospeaker.json") }) {
                files += picoSpeaker(id, name, timeline, speaker)
                report.section("Pico speaker", listOf("The speaker strumline became data/$id/picospeaker.json, which Psych's pico-speaker character reads for its shooting animations."))
            }
            if (roles.any { it.mode == Mode.HIDDEN_GF } && notes.any { it.type == HIDDEN_GF }) {
                report.section("$difficulty · girlfriend notes", listOf("Notes of the hidden girlfriend strumline use the \"$HIDDEN_GF\" note type (custom_notetypes/$HIDDEN_GF.hx): invisible, and only the girlfriend sings them."))
            }
            notes.sortBy { it.time }

            val lastTime = maxOf(notes.maxOfOrNull { it.time + it.length } ?: 0.0, events.maxOfOrNull { it.time } ?: 0.0) + 1.0
            var previousBpm = timeline.firstBpm
            val sections = timeline.spans(lastTime).mapIndexed { i, span ->
                val bpm = span.bpm.takeIf { it > 0 } ?: previousBpm
                val change = i > 0 && abs(bpm - previousBpm) > 1e-6
                previousBpm = bpm
                Section(span.startTime, span.endTime, span.beats, bpm, change)
            }.ifEmpty { listOf(Section(0.0, 60000.0 / meta.bpm * 4, 4.0, meta.bpm, false)) }
            notes.forEach { note ->
                val section = sections[sectionIndex(sections, note.time)]
                val entry = JSONArray().put(note.time.toJsonNumber()).put(note.lane).put(note.length.toJsonNumber())
                if (note.type.isNotEmpty()) entry.put(note.type)
                section.notes += entry
                if (note.lane < 4) section.playerNotes++ else section.opponentNotes++
            }

            val psychEvents = sortedMapOf<Double, MutableList<List<String>>>()
            fun add(time: Double, eventName: String, v1: String, v2: String) {
                psychEvents.getOrPut(time) { mutableListOf() } += listOf(eventName, v1, v2)
            }
            fun custom(time: Double, eventName: String, values: List<Any?>, count: Int) {
                val (v1, v2) = CodenameToPsychEvents.encode(values, count)
                add(time, eventName, v1, v2)
                customEvents[eventName] = maxOf(customEvents[eventName] ?: 0, count)
            }
            fun roleName(index: Any?): String? {
                val i = CodenameTimeline.number(index)?.toInt() ?: return null
                return roles.firstOrNull { it.index == i }?.name
            }

            val camera = events.filter { it.name == "Camera Movement" || it.name == "Camera Position" }
            var pointer = 0
            var focus = "dad"
            var forced = false
            var midSection = 0
            var lastMustHit = false
            var snapped = 0
            var offsets = 0
            fun applyCamera(event: CodenameEvent, section: Section?, atStart: Boolean) {
                when (event.name) {
                    "Camera Position" -> {
                        val isOffset = event.params.getOrNull(6) == true
                        if (isOffset || event.params.getOrNull(2) == false) custom(event.time, "Camera Position", event.params, 7)
                        else add(event.time, "Camera Follow Pos", CodenameToPsychEvents.valueText(event.params.getOrNull(0) ?: 0), CodenameToPsychEvents.valueText(event.params.getOrNull(1) ?: 0))
                        forced = true
                    }
                    else -> {
                        val target = roleName(event.params.getOrNull(0)) ?: return
                        if ((CodenameTimeline.number(event.params.getOrNull(5)) ?: 0.0) != 0.0 || (CodenameTimeline.number(event.params.getOrNull(6)) ?: 0.0) != 0.0) offsets++
                        val snap = event.params.getOrNull(1) == false
                        if (snap) snapped++
                        val needsEvent = !atStart || forced || snap
                        focus = target
                        if (needsEvent) {
                            if (!atStart) midSection++
                            custom(event.time, "Camera Movement", listOf(target, if (snap) "snap" else ""), 2)
                            forced = target == "gf"
                        }
                        section?.let { s -> if (atStart) s.gf = false }
                    }
                }
            }
            sections.forEach { section ->
                val startEvents = mutableListOf<CodenameEvent>()
                while (pointer < camera.size && camera[pointer].time <= section.start + 1.0) startEvents += camera[pointer++]
                startEvents.forEach { applyCamera(it, section, true) }
                when (focus) {
                    "gf" -> when {
                        section.playerNotes == 0 -> {
                            section.mustHit = true
                            section.gf = true
                        }
                        section.opponentNotes == 0 -> {
                            section.mustHit = false
                            section.gf = true
                        }
                        else -> {
                            section.mustHit = lastMustHit
                            if (!forced) {
                                custom(section.start, "Camera Movement", listOf("gf", ""), 2)
                                forced = true
                            }
                        }
                    }
                    else -> section.mustHit = focus == "bf"
                }
                lastMustHit = section.mustHit
                while (pointer < camera.size && camera[pointer].time < section.end - 1.0) applyCamera(camera[pointer++], null, false)
            }

            var speed = chart.scrollSpeed.takeIf { it > 0 } ?: 1.0
            val baseSpeed = speed
            val idleAlt = HashMap<Int, Boolean>()
            val unknown = sortedMapOf<String, Int>()
            var easedScroll = 0
            events.forEach { event ->
                val p = event.params
                when (event.name) {
                    "Camera Movement", "Camera Position", "BPM Change", "Time Signature Change", "Continuous BPM Change" -> Unit
                    "Add Camera Zoom" -> {
                        val amount = CodenameToPsychEvents.valueText(p.getOrNull(0) ?: 0.05)
                        if (p.getOrNull(1) == "camHUD") add(event.time, "Add Camera Zoom", "0", amount) else add(event.time, "Add Camera Zoom", amount, "0")
                    }
                    "Play Animation" -> {
                        val target = roleName(p.getOrNull(0))
                        if (target == null) unknown["Play Animation (extra strumline)"] = (unknown["Play Animation (extra strumline)"] ?: 0) + 1
                        else add(event.time, "Play Animation", CodenameToPsychEvents.valueText(p.getOrNull(1)), target)
                    }
                    "Scroll Speed Change" -> {
                        val value = CodenameTimeline.number(p.getOrNull(1)) ?: 1.0
                        val target = if (p.getOrNull(5) == true) speed * value else value
                        val tween = p.getOrNull(0) != false
                        val steps = CodenameTimeline.number(p.getOrNull(2)) ?: 4.0
                        val seconds = if (tween) (timeline.timeAt(timeline.stepAt(event.time) + steps) - event.time) / 1000.0 else 0.0
                        val ease = p.getOrNull(3)?.toString() ?: "linear"
                        if (tween && ease != "linear") easedScroll++
                        speed = target
                        add(event.time, "Change Scroll Speed", CodenameToPsychEvents.number(target / baseSpeed), CodenameToPsychEvents.number(seconds))
                    }
                    "Alt Animation Toggle" -> {
                        val strum = CodenameTimeline.number(p.getOrNull(2))?.toInt() ?: 0
                        val idle = p.getOrNull(1) != false
                        if (idleAlt[strum] != idle) {
                            val target = roleName(strum)
                            if (target != null && (idle || idleAlt.containsKey(strum))) add(event.time, "Alt Idle Animation", target, if (idle) "-alt" else "")
                            idleAlt[strum] = idle
                        }
                    }
                    "Camera Zoom" -> {
                        val tween = p.getOrNull(0) != false
                        val classic = p.getOrNull(4)?.toString() == "CLASSIC"
                        val direct = (p.getOrNull(6)?.toString() ?: "direct") == "direct"
                        val hud = p.getOrNull(2) == "camHUD"
                        val multiplicative = p.getOrNull(7) == true
                        if (!hud && direct && !multiplicative && (!tween || classic)) {
                            add(event.time, "Set Property", "defaultCamZoom", CodenameToPsychEvents.valueText(p.getOrNull(1) ?: 1))
                        } else custom(event.time, "Camera Zoom", p, 8)
                    }
                    "Camera Bop", "Camera Modulo Change", "Camera Flash", "HScript Call" -> custom(event.time, event.name, p, CodenameToPsychEvents.paramCounts.getValue(event.name))
                    else -> {
                        val definition = options.eventDefinitions[event.name]
                        val count = maxOf(definition?.params?.size ?: 0, p.size)
                        custom(event.time, event.name, p, count)
                        if (definition == null) unknown[event.name] = (unknown[event.name] ?: 0) + 1
                    }
                }
            }

            val eventsJson = JSONArray()
            psychEvents.forEach { (time, list) ->
                eventsJson.put(JSONArray().put(time.toJsonNumber()).put(JSONArray(list.map { JSONArray(it) })))
            }
            val playerChar = psychCharacter(player?.characters?.firstOrNull() ?: "bf")
            val opponentChar = psychCharacter(opponent?.characters?.firstOrNull() ?: "dad")
            val gfChar = psychCharacter((if (opponentIsGf) opponent?.characters?.firstOrNull() else girlfriend?.characters?.firstOrNull()) ?: "gf")
            characters = mapOf("player" to playerChar, "opponent" to opponentChar, "girlfriend" to gfChar)
            val root = JSONObject()
                .put("song", name)
                .put("notes", JSONArray(sections.map { s ->
                    JSONObject()
                        .put("sectionNotes", JSONArray(s.notes))
                        .put("sectionBeats", s.beats.toJsonNumber())
                        .put("mustHitSection", s.mustHit)
                        .put("gfSection", s.gf)
                        .put("altAnim", false)
                        .put("bpm", precise(s.bpm))
                        .put("changeBPM", s.changeBpm)
                }))
                .put("events", eventsJson)
                .put("bpm", precise(sections.first().bpm))
                .put("needsVoices", meta.needsVoices)
                .put("speed", baseSpeed.toJsonNumber())
                .put("offset", 0)
                .put("player1", playerChar)
                .put("player2", opponentChar)
                .put("gfVersion", gfChar)
                .put("stage", stageFor(chart.stage, options.stageIds))
                .put("format", "psych_v1")
            options.gameOverCharacter?.takeIf { it.isNotBlank() && it != "bf-dead" }?.let { root.put("gameOverChar", it) }
            val suffix = if (difficulty.equals("normal", ignoreCase = true)) "" else "-" + SongPath.format(difficulty)
            files += PsychChartFile("data/$id/$id$suffix.json", root.toString())
            report.stat(difficulty.replaceFirstChar { it.uppercase() }, notes.size)

            if (dropped.isNotEmpty()) report.warn("$difficulty: Psych has only the player and opponent strumlines, so notes of extra strumlines were left out (${dropped.entries.joinToString { "${it.key}: ${it.value}" }}).")
            if (duplicates > 0) report.warn("$difficulty: $duplicates stacked note(s) on the same lane and time were merged.")
            if (wrapped > 0) report.warn("$difficulty: $wrapped note(s) used lanes above 4 and were wrapped.")
            if (typedGf > 0) report.warn("$difficulty: $typedGf girlfriend note(s) already had a note type, so they kept it and the opponent sings them.")
            if (midSection > 0) report.section("$difficulty · camera", listOf("$midSection camera move(s) happen in the middle of a section; they became \"Camera Movement\" events (custom_events/Camera Movement.hx)."))
            if (snapped > 0) report.warn("$difficulty: $snapped instant camera move(s) (no tween) snap through the \"Camera Movement\" event only when it is mid-section; section moves always glide in Psych.")
            if (offsets > 0) report.warn("$difficulty: Camera Movement offsets (Offset X/Y) have no Psych equivalent and were ignored.")
            if (easedScroll > 0) report.warn("$difficulty: $easedScroll scroll speed tween(s) use eases; Psych's Change Scroll Speed is always linear.")
            if (timeline.ignored.isNotEmpty()) report.warn("$difficulty: ${timeline.ignored.size} timing event(s) overlap a continuous BPM change and were ignored, like Codename does.")
            if (timeline.changes.any { it.stepsPerBeat != 4 }) report.warn("$difficulty: a time signature changes the steps per beat; Psych always has 4 steps per beat, so sections keep the measure length in steps.")
            if (timeline.hasContinuous) report.section("$difficulty · timing", listOf("Continuous BPM changes became one Psych section per beat with the matching BPM, so notes stay in sync."))
            unknown.forEach { (event, n) -> report.section("Events without a definition", listOf("$event ×$n (values passed as text)")) }
        }

        report.section("Difficulties", difficulties.map { d -> "data/$id/$id${if (d.equals("normal", true)) "" else "-" + SongPath.format(d)}.json" })
        if (difficulties.any { it.lowercase() !in listOf("easy", "normal", "hard") }) {
            report.warn("Psych lists only Easy, Normal and Hard unless the week sets \"difficulties\"; the Weeks converter adds the rest.")
        }
        if (customEvents.isNotEmpty()) report.section("Custom events used", customEvents.keys.map { "custom_events/$it.hx" })
        if (name != displayName && displayName.isNotBlank()) report.warn("Psych finds songs by folder, so \"$displayName\" is named \"$name\" in the chart.")
        return CodenamePsychSong(id, name, files, difficulties.map { it.lowercase() }, customEvents, usedTypes, characters, report.build())
    }

    private fun precise(value: Double): Number {
        val rounded = Math.round(value * 1_000_000.0) / 1_000_000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong() else rounded
    }

    private fun roles(chart: CodenameChartData): List<Role> {
        var player = false
        var opponent = false
        var girlfriend = false
        return chart.strumLines.mapIndexed { index, line ->
            val first = line.characters.firstOrNull()?.lowercase() ?: ""
            when {
                line.type == CodenameStrumLine.PLAYER && !player -> {
                    player = true
                    Role(index, "bf", 0, Mode.PLAYER)
                }
                line.type == CodenameStrumLine.OPPONENT && !opponent -> {
                    opponent = true
                    Role(index, "dad", 4, Mode.OPPONENT)
                }
                line.type == CodenameStrumLine.ADDITIONAL && !girlfriend && (line.isGirlfriend || first.startsWith("gf")) -> {
                    girlfriend = true
                    val mode = when {
                        first.startsWith("pico-speaker") -> Mode.SPEAKER
                        line.visible -> Mode.GF
                        else -> Mode.HIDDEN_GF
                    }
                    Role(index, "gf", 4, mode)
                }
                else -> Role(index, null, 0, Mode.NONE)
            }
        }
    }

    private fun picoSpeaker(id: String, name: String, timeline: CodenameTimeline, notes: List<Pair<Double, Int>>): PsychChartFile {
        val sorted = notes.sortedBy { it.first }
        val last = sorted.last().first + 1.0
        val spans = timeline.spans(last)
        val sections = spans.map { span -> span to JSONArray() }
        sorted.forEach { (time, lane) ->
            val index = sections.indexOfLast { it.first.startTime <= time + 0.5 }.coerceAtLeast(0)
            sections[index].second.put(JSONArray().put(time.toJsonNumber()).put(lane).put(0))
        }
        val root = JSONObject()
            .put("song", name)
            .put("bpm", precise(timeline.firstBpm))
            .put("needsVoices", true)
            .put("speed", 1)
            .put("notes", JSONArray(sections.map { (span, list) ->
                JSONObject().put("sectionNotes", list).put("sectionBeats", span.beats.toJsonNumber()).put("mustHitSection", true).put("bpm", precise(span.bpm)).put("changeBPM", false)
            }))
        return PsychChartFile("data/$id/picospeaker.json", root.toString())
    }

    private class AltTimeline(private val toggles: Map<Int, List<Pair<Double, Boolean>>>) {
        fun singAt(strum: Int, time: Double): Boolean = toggles[strum]?.lastOrNull { it.first <= time + 1.0 }?.second ?: false
    }

    private fun altTimeline(events: List<CodenameEvent>): AltTimeline {
        val map = HashMap<Int, MutableList<Pair<Double, Boolean>>>()
        events.filter { it.name == "Alt Animation Toggle" }.forEach { e ->
            val strum = CodenameTimeline.number(e.params.getOrNull(2))?.toInt() ?: 0
            map.getOrPut(strum) { mutableListOf() } += e.time to (e.params.getOrNull(0) == true || e.params.getOrNull(0)?.toString() == "true")
        }
        return AltTimeline(map)
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
}
