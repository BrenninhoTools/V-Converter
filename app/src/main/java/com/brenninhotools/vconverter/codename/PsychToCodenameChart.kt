package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.psych.PsychEvent
import com.brenninhotools.vconverter.chart.psych.PsychSong
import com.brenninhotools.vconverter.chart.psych.PsychTiming
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import kotlin.math.roundToLong

data class PsychCodenameChartOptions(
    val songFolder: String = "",
    val customStageIds: Set<String> = emptySet(),
    val characterIcons: Map<String, String> = emptyMap(),
    val characterColors: Map<String, List<Int>> = emptyMap(),
    val songColor: List<Int>? = null,
    val songIcon: String? = null,
    val playerVocals: String? = null,
    val opponentVocals: String? = null,
    val customEventNames: Set<String> = emptySet()
)

data class PsychCodenameChartResult(
    val folder: String,
    val charts: Map<String, String>,
    val meta: String,
    val events: Map<String, Int>,
    val noteTypes: Set<String>,
    val characters: Map<String, String>,
    val report: Report
)

object PsychToCodenameChart {
    val stageIds = mapOf(
        "stage" to "stage",
        "spooky" to "spooky",
        "philly" to "philly",
        "limo" to "limo",
        "mall" to "mall",
        "mallevil" to "mall-evil",
        "school" to "school",
        "schoolevil" to "school-evil",
        "tank" to "tank"
    )

    val characterIds = CodenameToPsychChart.characterIds.entries.associate { (k, v) -> v to k }

    val noteTypes = mapOf(
        "alt animation" to "Alt Anim Note",
        "no animation" to "No Anim Note"
    )

    val scriptedEvents = linkedSetOf("Hey!", "Set GF Speed", "Screen Shake", "Change Character", "Set Property", "Play Sound", "Alt Idle Animation")

    val stageEvents = setOf("Dadbattle Spotlight", "Philly Glow", "Kill Henchmen", "BG Freaks Expression", "Trigger BG Ghouls")

    val scriptedNoteTypes = setOf("Hey!", "Hurt Note", "GF Sing")

    private val difficultyOrder = listOf("easy", "normal", "hard", "erect", "nightmare")

    fun codenameCharacter(id: String): String = characterIds[id.lowercase()] ?: id

    fun stageFor(stage: String, custom: Set<String>): String = if (stage in custom) stage else stageIds[stage.lowercase()] ?: stage

    fun noteTypeFor(name: String): String = noteTypes[name.trim().lowercase()] ?: name.trim()

    private class Out(val time: Double, val id: Int, val length: Double, val type: String)

    fun convert(sources: List<ChartSource>, extraEvents: List<PsychEvent> = emptyList(), options: PsychCodenameChartOptions = PsychCodenameChartOptions()): PsychCodenameChartResult {
        require(sources.isNotEmpty()) { "Add at least one Psych chart." }
        val report = ReportBuilder()
        val ordered = sources.sortedWith(compareBy<ChartSource> { difficultyOrder.indexOf(it.difficulty.lowercase()).let { i -> if (i < 0) 99 else i } }.thenBy { it.difficulty })
        val main = ordered.firstOrNull { it.difficulty.equals("normal", true) }?.song ?: ordered.first().song
        val folder = options.songFolder.ifBlank { SongPath.format(main.name).ifEmpty { "song" } }
        val charts = LinkedHashMap<String, String>()
        val usedEvents = sortedMapOf<String, Int>(String.CASE_INSENSITIVE_ORDER)
        val usedTypes = sortedSetOf<String>()
        var characters: Map<String, String> = emptyMap()

        ordered.forEach { source ->
            val song = source.song
            val difficulty = SongPath.format(source.difficulty).ifEmpty { "normal" }
            val timing = PsychTiming(song)
            val player = codenameCharacter(song.player1)
            val opponent = codenameCharacter(song.player2)
            val gfName = codenameCharacter(song.girlfriend)
            val opponentIsGf = opponent.lowercase().startsWith("gf")
            val hasGf = !opponentIsGf && gfName.isNotBlank() && gfName != "none"
            characters = mapOf("player" to player, "opponent" to opponent, "girlfriend" to if (hasGf) gfName else "")

            val opponentNotes = mutableListOf<Out>()
            val playerNotes = mutableListOf<Out>()
            val gfNotes = mutableListOf<Out>()
            val seen = HashSet<Triple<Int, Long, String>>()
            var duplicates = 0
            var gfPlayer = 0
            song.sections.forEachIndexed { index, section ->
                section.notes.forEach { note ->
                    var type = note.type.trim()
                    val gfNote = (section.gfSection && note.isPlayer == section.mustHit) || type.equals("GF Sing", true)
                    if (section.altAnim && !note.isPlayer && type.isEmpty()) type = "Alt Animation"
                    if (!seen.add(Triple(note.lane, (note.time * 10).roundToLong(), type))) {
                        duplicates++
                        return@forEach
                    }
                    val mapped = noteTypeFor(type)
                    when {
                        gfNote && !note.isPlayer && hasGf -> gfNotes += Out(note.time, note.direction, note.length, if (type.equals("GF Sing", true)) "" else mapped)
                        gfNote && note.isPlayer -> {
                            gfPlayer++
                            playerNotes += Out(note.time, note.direction, note.length, if (type.isEmpty() || type.equals("GF Sing", true)) "GF Sing" else mapped)
                        }
                        note.isPlayer -> playerNotes += Out(note.time, note.direction, note.length, if (type.equals("GF Sing", true)) "" else mapped)
                        else -> opponentNotes += Out(note.time, note.direction, note.length, if (type.equals("GF Sing", true)) "" else mapped)
                    }
                }
            }
            val typeNames = (opponentNotes + playerNotes + gfNotes).map { it.type }.filter { it.isNotEmpty() }.distinct().sorted()
            usedTypes += typeNames
            fun typeIndex(name: String) = if (name.isEmpty()) 0 else typeNames.indexOf(name) + 1
            fun lineNotes(list: List<Out>) = list.map { CodenameNote(it.time, it.id, typeIndex(it.type), it.length) }

            val lines = mutableListOf(
                CodenameOutStrumLine(listOf(opponent), CodenameStrumLine.OPPONENT, if (opponentIsGf) "girlfriend" else "dad", lineNotes(opponentNotes), true, options.opponentVocals ?: ""),
                CodenameOutStrumLine(listOf(player), CodenameStrumLine.PLAYER, "boyfriend", lineNotes(playerNotes), true, options.playerVocals ?: "")
            )
            if (hasGf) lines += CodenameOutStrumLine(listOf(gfName), CodenameStrumLine.ADDITIONAL, "girlfriend", lineNotes(gfNotes), false)
            val gfIndex = if (hasGf) 2 else 0

            val events = mutableListOf<CodenameEvent>()
            val psychEvents = (song.events + extraEvents).sortedBy { it.time }
            val forcedIntervals = forcedCamera(psychEvents)
            val forcedPoints = psychEvents.filter { it.name == "Camera Follow Pos" }.mapNotNull { e ->
                val x = e.value1.trim().toDoubleOrNull()
                val y = e.value2.trim().toDoubleOrNull()
                if (x == null && y == null) null else Triple(e.time, x ?: 0.0, y ?: 0.0)
            }
            var lastFocus = -1
            var beats = 4.0
            var bpm = song.bpm
            song.sections.forEachIndexed { index, section ->
                val start = timing.sectionStarts.getOrElse(index) { 0.0 }
                if (index > 0 && section.changeBpm && section.bpm > 0 && section.bpm != bpm) {
                    bpm = section.bpm
                    events += CodenameEvent("BPM Change", start, listOf(bpm))
                } else if (index == 0 && section.changeBpm && section.bpm > 0) bpm = section.bpm
                if (section.beats != beats) {
                    beats = section.beats
                    events += CodenameEvent("Time Signature Change", start, listOf(beats, 4.0))
                }
                val focus = when {
                    section.gfSection && hasGf -> gfIndex
                    section.mustHit -> 1
                    else -> 0
                }
                if (focus != lastFocus) {
                    events += CodenameEvent("Camera Movement", start, listOf(focus))
                    lastFocus = focus
                    if (forcedIntervals.any { start >= it.first - 0.5 && start < it.second - 0.5 }) {
                        forcedPoints.lastOrNull { it.first <= start + 0.5 }?.let { point ->
                            events += CodenameEvent("Camera Position", start + 0.01, listOf(point.second, point.third, false, 4.0, "CLASSIC", "In", false))
                        }
                    }
                }
            }

            fun focusAt(time: Double): Int {
                val i = timing.sectionIndexAt(time).coerceAtLeast(0)
                val section = song.sections.getOrNull(i) ?: return 0
                return when {
                    section.gfSection && hasGf -> gfIndex
                    section.mustHit -> 1
                    else -> 0
                }
            }
            fun strumFor(target: String, numeric: Double?): Int {
                val t = target.trim().lowercase()
                return when {
                    t == "bf" || t == "boyfriend" || t == "player" -> 1
                    t == "gf" || t == "girlfriend" -> if (hasGf) gfIndex else 0
                    t == "dad" || t == "opponent" -> 0
                    numeric != null -> when (numeric.toInt()) {
                        1 -> 1
                        2 -> if (hasGf) gfIndex else 0
                        else -> 0
                    }
                    else -> 0
                }
            }
            fun used(name: String) {
                usedEvents[name] = (usedEvents[name] ?: 0) + 1
            }
            var unknown = 0
            psychEvents.forEach { e ->
                val v1 = e.value1
                val v2 = e.value2
                val f1 = v1.trim().toDoubleOrNull()
                val f2 = v2.trim().toDoubleOrNull()
                when (e.name) {
                    "Add Camera Zoom" -> {
                        val game = f1 ?: 0.015
                        val hud = f2 ?: 0.03
                        if (game != 0.0) events += CodenameEvent("Add Camera Zoom", e.time, listOf(game, "camGame"))
                        if (hud != 0.0) events += CodenameEvent("Add Camera Zoom", e.time, listOf(hud, "camHUD"))
                    }
                    "Play Animation" -> events += CodenameEvent("Play Animation", e.time, listOf(strumFor(v2, f2), v1.trim(), true, "NONE"))
                    "Camera Follow Pos" -> {
                        if (f1 == null && f2 == null) events += CodenameEvent("Camera Movement", e.time, listOf(focusAt(e.time)))
                        else events += CodenameEvent("Camera Position", e.time, listOf(f1 ?: 0.0, f2 ?: 0.0, true, 4.0, "CLASSIC", "In", false))
                    }
                    "Change Scroll Speed" -> {
                        val mult = f1 ?: 1.0
                        val seconds = f2 ?: 0.0
                        val steps = if (seconds > 0) seconds * 1000.0 / timing.stepMsAt(e.time) else 4.0
                        events += CodenameEvent("Scroll Speed Change", e.time, listOf(seconds > 0, song.speed * mult, round(steps), "linear", "In", false))
                    }
                    "Alt Idle Animation" -> {
                        val suffix = v2.trim()
                        if (suffix.isEmpty() || suffix == "-alt") events += CodenameEvent("Alt Animation Toggle", e.time, listOf(false, suffix.isNotEmpty(), strumFor(v1, f1)))
                        else {
                            events += CodenameEvent(e.name, e.time, listOf(v1, v2))
                            used(e.name)
                        }
                    }
                    else -> {
                        events += CodenameEvent(e.name, e.time, listOf(v1, v2))
                        used(e.name)
                        if (e.name !in scriptedEvents && e.name !in options.customEventNames && e.name !in stageEvents) unknown++
                    }
                }
            }

            charts[difficulty] = CodenameWriters.chart(lines, events, typeNames, song.speed, stageFor(song.stage, options.customStageIds))
            report.stat(difficulty.replaceFirstChar { it.uppercase() }, opponentNotes.size + playerNotes.size + gfNotes.size)
            if (duplicates > 0) report.warn("$difficulty: $duplicates stacked note(s) on the same lane and time were merged.")
            if (gfPlayer > 0) report.section("$difficulty · girlfriend notes", listOf("$gfPlayer player note(s) are sung by the girlfriend; they use the \"GF Sing\" note type (data/notes/GF Sing.hx)."))
            if (song.invalidNotes > 0) report.warn("$difficulty: ${song.invalidNotes} broken note(s) were skipped.")
            if (song.offset != 0.0) report.warn("$difficulty: the chart offset (${song.offset} ms) has no Codename equivalent; adjust it in the options instead.")
            if (unknown > 0) report.warn("$difficulty: $unknown event(s) are not built into Codename and have no script in this mod; they are kept so a script can handle them.")
        }

        val icon = options.songIcon ?: options.characterIcons[main.player2] ?: options.characterIcons[codenameCharacter(main.player2)] ?: codenameCharacter(main.player2)
        val color = options.songColor ?: options.characterColors[main.player2] ?: listOf(146, 113, 253)
        val meta = CodenameWriters.meta(
            CodenameOutMeta(
                displayName = main.name.ifBlank { folder },
                bpm = main.bpm,
                icon = icon,
                color = CodenameWriters.hex(color),
                difficulties = charts.keys.toList(),
                needsVoices = main.needsVoices
            )
        )
        report.section("Difficulties", charts.keys.map { "songs/$folder/charts/$it.json" })
        val kept = usedEvents.keys.filter { it !in scriptedEvents }
        if (usedEvents.isNotEmpty()) report.section("Events", usedEvents.map { (name, n) -> "$name ×$n" + when {
            name in scriptedEvents -> " (data/events/$name.hx)"
            name in stageEvents -> " (a base game stage event; nothing handles it in Codename unless a stage script does)"
            name in options.customEventNames -> " (converted from custom_events/)"
            else -> ""
        } })
        if (kept.any { it in stageEvents }) report.warn("Base game stage events (${kept.filter { it in stageEvents }.joinToString()}) only work in Psych's hardcoded stages.")
        if (main.arrowSkin.isNotBlank()) report.warn("The arrow skin \"${main.arrowSkin}\" is not carried over; Codename note skins are set with scripts (onNoteCreation).")
        return PsychCodenameChartResult(folder, charts, meta, usedEvents, usedTypes, characters, report.build())
    }

    private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun forcedCamera(events: List<PsychEvent>): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var start: Double? = null
        events.filter { it.name == "Camera Follow Pos" }.forEach { e ->
            val forced = e.value1.trim().toDoubleOrNull() != null || e.value2.trim().toDoubleOrNull() != null
            if (forced && start == null) start = e.time
            if (!forced && start != null) {
                result += start!! to e.time
                start = null
            }
        }
        start?.let { result += it to Double.MAX_VALUE }
        return result
    }
}
