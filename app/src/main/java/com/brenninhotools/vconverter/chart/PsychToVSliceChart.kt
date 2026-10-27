package com.brenninhotools.vconverter.chart

import com.brenninhotools.vconverter.chart.psych.PsychEvent
import com.brenninhotools.vconverter.chart.psych.PsychSong
import com.brenninhotools.vconverter.chart.psych.PsychTiming
import com.brenninhotools.vconverter.chart.vslice.VSliceChart
import com.brenninhotools.vconverter.chart.vslice.VSliceCharacters
import com.brenninhotools.vconverter.chart.vslice.VSliceEvent
import com.brenninhotools.vconverter.chart.vslice.VSliceMetadata
import com.brenninhotools.vconverter.chart.vslice.VSliceNote
import com.brenninhotools.vconverter.chart.vslice.VSliceTimeChange
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import kotlin.math.roundToLong

data class ChartSource(val difficulty: String, val song: PsychSong)

data class ConversionOptions(
    val songName: String = "",
    val artist: String = "",
    val charter: String = "",
    val extraEvents: List<PsychEvent> = emptyList(),
    val customStageIds: Set<String> = emptySet(),
    val keepPsychEvents: Boolean = true,
    val playerVocals: List<String>? = null,
    val opponentVocals: List<String>? = null,
    val splitVariations: Boolean = true,
    val generatedBy: String = "V-Converter (Psych Engine to V-Slice)"
)

data class VariationChart(
    val variation: String,
    val metadata: VSliceMetadata,
    val chart: VSliceChart
)

data class ConversionResult(
    val metadata: VSliceMetadata,
    val chart: VSliceChart,
    val report: ConversionReport,
    val variations: List<VariationChart> = emptyList()
) {
    fun files(id: String): List<Pair<String, String>> {
        val list = mutableListOf(
            "$id-metadata.json" to VSliceWriter.metadataJson(metadata),
            "$id-chart.json" to VSliceWriter.chartJson(chart)
        )
        variations.forEach { variation ->
            list += "$id-metadata-${variation.variation}.json" to VSliceWriter.metadataJson(variation.metadata)
            list += "$id-chart-${variation.variation}.json" to VSliceWriter.chartJson(variation.chart)
        }
        return list
    }
}

object PsychToVSliceChart {
    val difficultyOrder = listOf("easy", "normal", "hard", "erect", "nightmare")

    private const val FOCUS_PLAYER = 0
    private const val FOCUS_OPPONENT = 1
    private const val FOCUS_GIRLFRIEND = 2

    val stageIds = mapOf(
        "stage" to "mainStage",
        "spooky" to "spookyMansion",
        "philly" to "phillyTrain",
        "limo" to "limoRide",
        "mall" to "mallXmas",
        "mallevil" to "mallEvil",
        "school" to "school",
        "schoolevil" to "schoolEvil",
        "tank" to "tankmanBattlefield"
    )

    val noteKinds = mapOf(
        "alt animation" to "alt",
        "hey!" to "hey",
        "hurt note" to "hurt",
        "no animation" to "noanim",
        "gf sing" to "gf-sing"
    )

    fun convert(sources: List<ChartSource>, options: ConversionOptions = ConversionOptions()): ConversionResult {
        require(sources.isNotEmpty()) { "Select at least one Psych Engine chart." }

        val normalized = sources.map { it.copy(difficulty = normalizeDifficulty(it.difficulty)) }
        normalized.groupBy { it.difficulty }.forEach { (difficulty, group) ->
            require(difficulty.isNotEmpty()) { "Every chart needs a difficulty name." }
            require(group.size == 1) { "The difficulty \"$difficulty\" is assigned to more than one chart." }
        }

        val ordered = normalized.sortedWith(compareBy({ difficultyRank(it.difficulty) }, { it.difficulty }))
        val main = ordered.firstOrNull { it.difficulty == "normal" } ?: ordered.first()
        val report = ConversionReport()

        val groups = if (options.splitVariations) {
            ordered.groupBy { signature(it.song) }.values.sortedBy { group -> if (main in group) 0 else 1 }
        } else {
            listOf(ordered)
        }
        val names = variationNames(groups.drop(1))
        if (!options.splitVariations) checkConsistency(main, ordered, report)
        ordered.forEach { source -> checkSource(source, report) }

        val built = groups.mapIndexed { index, group ->
            val lead = if (index == 0) main else group.first()
            buildGroup(lead, group, options, report, if (index == 0) "" else names[index - 1])
        }
        val variationIds = names
        val mainResult = built.first()
        val mainMetadata = mainResult.first.copy(songVariations = variationIds)
        val variations = built.drop(1).mapIndexed { i, (metadata, chart) -> VariationChart(variationIds[i], metadata, chart) }
        variations.forEach { variation ->
            val diffs = variation.metadata.difficulties.joinToString(", ")
            report.warn("$diffs ${if (variation.metadata.difficulties.size == 1) "has" else "have"} different BPM, time signatures, events, characters or stage than \"${main.difficulty}\", so ${if (variation.metadata.difficulties.size == 1) "it was" else "they were"} kept as the \"${variation.variation}\" variation. V-Slice plays a variation with Voices files ending in -${variation.variation} (Voices-<character>-${variation.variation}.ogg).")
            report.variations[variation.variation] = variation.metadata.difficulties
        }

        return ConversionResult(mainMetadata, mainResult.second, report, variations)
    }

    private data class Signature(val changes: List<PsychTiming.TimeChange>, val events: Set<PsychEvent>, val player: String, val opponent: String, val girlfriend: String, val stage: String)

    private fun signature(song: PsychSong) = Signature(PsychTiming(song).changes, song.events.toSet(), song.player1, song.player2, song.girlfriend, song.stage)

    private fun variationNames(groups: List<List<ChartSource>>): List<String> {
        val used = mutableSetOf("default", "")
        return groups.map { group ->
            val difficulties = group.map { it.difficulty }
            val base = when {
                difficulties.any { it == "erect" || it == "nightmare" } -> "erect"
                else -> slug(difficulties.first()).ifEmpty { "alt" }
            }
            var name = base
            var n = 2
            while (!used.add(name)) name = "$base${n++}"
            name
        }
    }

    private fun buildGroup(lead: ChartSource, group: List<ChartSource>, options: ConversionOptions, report: ConversionReport, variation: String): Pair<VSliceMetadata, VSliceChart> {
        val song = lead.song
        val timing = PsychTiming(song)
        val notes = LinkedHashMap<String, List<VSliceNote>>()
        val scrollSpeed = LinkedHashMap<String, Double>()
        group.forEach { source ->
            notes[source.difficulty] = convertNotes(source, report)
            scrollSpeed[source.difficulty] = source.song.speed
        }
        val countEvents = variation.isEmpty()
        val events = (cameraEvents(song, timing, report, countEvents) + psychEvents(song.events + options.extraEvents, song, timing, report, options.keepPsychEvents, countEvents))
            .sortedBy { it.time }

        val metadata = VSliceMetadata(
            songName = options.songName.ifBlank { song.name }.ifBlank { "Unknown" },
            artist = options.artist.ifBlank { "Unknown" },
            charter = options.charter.ifBlank { "Unknown" },
            difficulties = group.map { it.difficulty },
            characters = VSliceCharacters(
                player = song.player1,
                girlfriend = song.girlfriend,
                opponent = song.player2,
                playerVocals = options.playerVocals,
                opponentVocals = options.opponentVocals
            ),
            stage = convertStage(song.stage, options.customStageIds, report),
            noteStyle = noteStyle(song),
            timeChanges = timing.changes.map { VSliceTimeChange(it.time, it.beat, it.bpm, it.numerator) },
            offset = song.offset,
            generatedBy = options.generatedBy
        )
        val chart = VSliceChart(
            scrollSpeed = scrollSpeed,
            events = events,
            notes = notes,
            generatedBy = options.generatedBy
        )
        if (timing.fractionalSections > 0) {
            report.warn("${timing.fractionalSections} sections${if (variation.isEmpty()) "" else " in the $variation variation"} have a fractional length (like 3.5 beats). Note timing is kept, but V-Slice measures stay in whole beats, so the camera bop can drift after them.")
        }
        if (song.offset != 0.0) {
            report.offsetApplied = song.offset
        }
        return metadata to chart
    }

    fun normalizeDifficulty(raw: String): String = raw.trim().lowercase().replace(Regex("\\s+"), "-")

    private fun difficultyRank(difficulty: String): Int =
        difficultyOrder.indexOf(difficulty).takeIf { it >= 0 } ?: difficultyOrder.size

    private fun checkConsistency(main: ChartSource, all: List<ChartSource>, report: ConversionReport) {
        val song = main.song
        val timing = PsychTiming(song)
        all.filter { it !== main }.forEach { other ->
            if (PsychTiming(other.song).changes != timing.changes) {
                report.warn("\"${other.difficulty}\" has different BPM changes than \"${main.difficulty}\". V-Slice shares one timing map per song, so the \"${main.difficulty}\" timing was used.")
            }
            if (other.song.events.toSet() != song.events.toSet()) {
                report.warn("\"${other.difficulty}\" has different events than \"${main.difficulty}\". V-Slice shares events between difficulties, so only the \"${main.difficulty}\" events were kept.")
            }
            if (other.song.player1 != song.player1 || other.song.player2 != song.player2 || other.song.girlfriend != song.girlfriend || other.song.stage != song.stage) {
                report.warn("\"${other.difficulty}\" uses different characters or stage than \"${main.difficulty}\". The \"${main.difficulty}\" setup was used.")
            }
        }
    }

    private fun checkSource(source: ChartSource, report: ConversionReport) {
        if (source.song.invalidNotes > 0) {
            report.warn("\"${source.difficulty}\": ${source.song.invalidNotes} malformed notes were ignored.")
        }
        if (source.song.wrappedLanes > 0) {
            report.warn("\"${source.difficulty}\": ${source.song.wrappedLanes} notes used lanes above 7 and were wrapped into the 8 standard lanes.")
        }
    }

    private fun convertNotes(source: ChartSource, report: ConversionReport): List<VSliceNote> {
        val seen = HashSet<Pair<Long, Int>>()
        val result = mutableListOf<VSliceNote>()
        var duplicates = 0

        source.song.sections.forEach { section ->
            section.notes.forEach { note ->
                val key = (note.time * 1000.0).roundToLong() to note.lane
                if (!seen.add(key)) {
                    duplicates++
                    return@forEach
                }
                val kind = noteKind(note.type, !note.isPlayer, section.altAnim)
                if (kind.isNotEmpty()) report.noteKinds += kind
                result += VSliceNote(note.time, note.lane, note.length, kind)
            }
        }

        result.sortWith(compareBy({ it.time }, { it.direction }))
        report.noteCounts[source.difficulty] = result.size
        if (duplicates > 0) report.duplicateNotes[source.difficulty] = duplicates
        return result
    }

    private fun noteKind(type: String, opponent: Boolean, altSection: Boolean): String {
        if (type.isBlank()) return if (altSection && opponent) "alt" else ""
        return noteKindFor(type)
    }

    fun noteKindFor(type: String): String = noteKinds[type.trim().lowercase()] ?: slug(type)

    private fun slug(text: String): String =
        text.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun sectionFocus(song: PsychSong, index: Int): Int {
        val section = song.sections.getOrNull(index) ?: return FOCUS_OPPONENT
        return when {
            section.gfSection -> FOCUS_GIRLFRIEND
            section.mustHit -> FOCUS_PLAYER
            else -> FOCUS_OPPONENT
        }
    }

    private fun cameraEvents(song: PsychSong, timing: PsychTiming, report: ConversionReport, count: Boolean): List<VSliceEvent> {
        val result = mutableListOf<VSliceEvent>()
        var last: Int? = null
        song.sections.indices.forEach { index ->
            val focus = sectionFocus(song, index)
            if (focus != last) {
                last = focus
                result += VSliceEvent(timing.sectionStarts[index], "FocusCamera", mapOf("char" to focus))
            }
        }
        if (count) report.cameraEvents = result.size
        return result
    }

    private fun psychEvents(events: List<PsychEvent>, song: PsychSong, timing: PsychTiming, report: ConversionReport, keep: Boolean, count: Boolean): List<VSliceEvent> {
        val result = mutableListOf<VSliceEvent>()
        events.distinct().sortedBy { it.time }.forEach { event ->
            val converted = convertEvent(event, song, timing)
            if (converted == null) {
                if (count || event.name !in report.unsupportedEvents) report.skipEvent(event.name.ifBlank { "(unnamed)" })
                if (keep && event.name.isNotBlank()) {
                    if (count) report.keptPsychEvents++
                    result += VSliceEvent(event.time, PsychEventIds.idFor(event.name), linkedMapOf("value1" to event.value1, "value2" to event.value2))
                }
            } else {
                if (count) report.convertedEvents++
                result += converted
            }
        }
        return result
    }

    private fun convertEvent(event: PsychEvent, song: PsychSong, timing: PsychTiming): List<VSliceEvent>? {
        val v1 = event.value1.trim()
        val v2 = event.value2.trim()
        return when (event.name.trim().lowercase()) {
            "hey!" -> {
                val targets = when (v1.lowercase()) {
                    "bf", "boyfriend", "0" -> listOf("bf")
                    "gf", "girlfriend", "1" -> listOf("gf")
                    else -> listOf("bf", "gf")
                }
                targets.map { playAnimation(event.time, it, "hey") }
            }
            "play animation" -> {
                if (v1.isEmpty()) return null
                listOf(playAnimation(event.time, animationTarget(v2), v1))
            }
            "camera follow pos" -> {
                val x = v1.toDoubleOrNull()
                val y = v2.toDoubleOrNull()
                if (x == null && y == null) {
                    val focus = sectionFocus(song, timing.sectionIndexAt(event.time))
                    listOf(VSliceEvent(event.time, "FocusCamera", mapOf("char" to focus)))
                } else {
                    listOf(VSliceEvent(event.time, "FocusCamera", mapOf("char" to -1, "x" to (x ?: 0.0), "y" to (y ?: 0.0))))
                }
            }
            "change scroll speed" -> {
                val multiplier = v1.toDoubleOrNull() ?: 1.0
                val seconds = v2.toDoubleOrNull() ?: 0.0
                val value = LinkedHashMap<String, Any>()
                value["scroll"] = multiplier
                if (seconds <= 0.0) {
                    value["ease"] = "INSTANT"
                } else {
                    value["duration"] = seconds * 1000.0 / timing.stepMsAt(event.time)
                    value["ease"] = "linear"
                    value["easeDir"] = ""
                }
                value["absolute"] = false
                listOf(VSliceEvent(event.time, "ScrollSpeed", value))
            }
            else -> null
        }
    }

    private fun animationTarget(raw: String): String = when (raw.lowercase()) {
        "bf", "boyfriend" -> "bf"
        "gf", "girlfriend" -> "gf"
        else -> when (raw.toDoubleOrNull()?.let { Math.round(it) }) {
            1L -> "bf"
            2L -> "gf"
            else -> "dad"
        }
    }

    private fun playAnimation(time: Double, target: String, anim: String) =
        VSliceEvent(time, "PlayAnimation", linkedMapOf("target" to target, "anim" to anim, "force" to true))

    private fun convertStage(stage: String, customStageIds: Set<String>, report: ConversionReport): String {
        if (stage in customStageIds) return stage
        val mapped = stageIds[stage.lowercase()]
        if (mapped != null) return mapped
        report.warn("Stage \"$stage\" has no known V-Slice equivalent and was kept as-is. Make sure your mod has a V-Slice stage with this id.")
        return stage
    }

    private fun noteStyle(song: PsychSong): String {
        val pixelStage = song.stage.lowercase() in setOf("school", "schoolevil")
        val pixelSkin = song.arrowSkin.lowercase().contains("pixel")
        return if (pixelStage || pixelSkin) "pixel" else "funkin"
    }
}
