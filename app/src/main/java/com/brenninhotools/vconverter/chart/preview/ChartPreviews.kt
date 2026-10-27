package com.brenninhotools.vconverter.chart.preview

import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychTiming
import com.brenninhotools.vconverter.codename.CodenameChartParser
import com.brenninhotools.vconverter.codename.CodenameMeta
import com.brenninhotools.vconverter.codename.CodenameStrumLine
import com.brenninhotools.vconverter.codename.CodenameTimeline
import com.brenninhotools.vconverter.convert.PsychChartNames
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.reverse.VSliceParser
import org.json.JSONObject

object ChartPreviews {
    private val difficultyOrder = listOf("easy", "normal", "hard", "erect", "nightmare")

    fun read(engine: ChartEngine, files: List<Pair<String, String>>): List<ChartPreview> {
        val previews = when (engine) {
            ChartEngine.PSYCH -> psych(files)
            ChartEngine.VSLICE -> vslice(files)
            ChartEngine.CODENAME -> codename(files)
        }
        return previews.sortedWith(
            compareBy<ChartPreview>({ it.song }, { it.variation }, { difficultyOrder.indexOf(it.difficulty).let { i -> if (i < 0) difficultyOrder.size else i } }, { it.difficulty })
        )
    }

    private fun parts(path: String) = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }

    private fun psych(files: List<Pair<String, String>>): List<ChartPreview> {
        val eventsByFolder = HashMap<String, MutableList<PreviewEvent>>()
        val charts = mutableListOf<Triple<String, String, String>>()
        files.forEach { (path, text) ->
            val segments = parts(path)
            val stem = segments.lastOrNull()?.substringBeforeLast('.') ?: return@forEach
            val folder = segments.dropLast(1).lastOrNull().orEmpty().lowercase()
            if (stem.equals("events", ignoreCase = true)) {
                runCatching { PsychChartParser.parseEventsOnly(text) }.getOrNull()?.let { list ->
                    eventsByFolder.getOrPut(folder) { mutableListOf() } += list.map { PreviewEvent(it.time, it.name) }
                }
            } else {
                charts += Triple(folder, stem, text)
            }
        }
        return charts.mapNotNull { (folder, stem, text) ->
            val song = runCatching { PsychChartParser.parse(text) }.getOrNull() ?: return@mapNotNull null
            if (song.sections.isEmpty()) return@mapNotNull null
            val (id, difficulty) = PsychChartNames.split(stem, folder.ifEmpty { null })
            val timing = PsychTiming(song)
            val seen = HashSet<Pair<Long, Int>>()
            var duplicates = 0
            val notes = song.sections.flatMap { section ->
                section.notes.mapNotNull { note ->
                    if (!seen.add(Math.round(note.time * 1000.0) to note.lane)) {
                        duplicates++
                        return@mapNotNull null
                    }
                    PreviewNote(note.time, if (note.isPlayer) ChartSide.PLAYER else ChartSide.OPPONENT, note.direction, note.length, note.type)
                }
            }
            val focus = song.sections.mapIndexed { index, section ->
                PreviewFocus(
                    timing.sectionStarts[index],
                    when {
                        section.gfSection -> ChartSide.GIRLFRIEND
                        section.mustHit -> ChartSide.PLAYER
                        else -> ChartSide.OPPONENT
                    }
                )
            }
            val extra = eventsByFolder[folder].orEmpty()
            ChartPreview(
                song = id.replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                variation = "",
                difficulty = difficulty,
                notes = notes.sortedBy { it.time },
                events = (song.events.map { PreviewEvent(it.time, it.name) } + extra).distinct().sortedBy { it.time },
                timing = timing.changes.map { PreviewTiming(it.time, it.bpm, it.numerator) },
                focus = compact(focus),
                speed = song.speed,
                player = song.player1,
                opponent = song.player2,
                girlfriend = song.girlfriend,
                stage = song.stage,
                offset = song.offset,
                duplicates = duplicates
            )
        }
    }

    private val vsliceName = Regex("^(.+)-(metadata|chart)(?:-(.+))?$")

    private fun vslice(files: List<Pair<String, String>>): List<ChartPreview> {
        val metas = HashMap<Pair<String, String>, String>()
        val charts = mutableListOf<Triple<String, String, String>>()
        files.forEach { (path, text) ->
            val stem = parts(path).lastOrNull()?.substringBeforeLast('.') ?: return@forEach
            val match = vsliceName.find(stem) ?: return@forEach
            val song = match.groupValues[1].lowercase()
            val variation = match.groupValues[3].lowercase()
            if (match.groupValues[2] == "metadata") metas[song to variation] = text else charts += Triple(song, variation, text)
        }
        return charts.flatMap { (song, variation, text) ->
            val chart = runCatching { VSliceParser.chart(text) }.getOrNull() ?: return@flatMap emptyList()
            val meta = metas[song to variation]?.let { raw -> runCatching { VSliceParser.metadata(raw) }.getOrNull() }
            val timing = meta?.timeChanges?.map { PreviewTiming(it.time, it.bpm, it.numerator) } ?: listOf(PreviewTiming(0.0, 100.0, 4))
            val focus = chart.events.filter { it.kind == "FocusCamera" }.mapNotNull { event ->
                val raw = when (val v = event.value) {
                    is JSONObject -> v.value("char").asDouble(-1.0).toInt()
                    is Number -> v.toInt()
                    else -> v?.toString()?.toIntOrNull() ?: -1
                }
                when (raw) {
                    0 -> PreviewFocus(event.time, ChartSide.PLAYER)
                    1 -> PreviewFocus(event.time, ChartSide.OPPONENT)
                    2 -> PreviewFocus(event.time, ChartSide.GIRLFRIEND)
                    else -> null
                }
            }
            val events = chart.events.filter { it.kind != "FocusCamera" }.map { PreviewEvent(it.time, it.kind) }
            chart.notes.map { (difficulty, notes) ->
                ChartPreview(
                    song = song,
                    variation = variation,
                    difficulty = difficulty,
                    notes = notes.map { note ->
                        PreviewNote(note.time, if (note.data in 0..3) ChartSide.PLAYER else ChartSide.OPPONENT, note.data.mod(4), note.length, note.kind)
                    },
                    events = events,
                    timing = timing,
                    focus = compact(focus),
                    speed = chart.scrollSpeed[difficulty] ?: chart.scrollSpeed["default"] ?: chart.scrollSpeed.values.firstOrNull() ?: 1.0,
                    player = meta?.player.orEmpty(),
                    opponent = meta?.opponent.orEmpty(),
                    girlfriend = meta?.girlfriend.orEmpty(),
                    stage = meta?.stage.orEmpty(),
                    offset = meta?.offsetInstrumental ?: 0.0
                )
            }
        }
    }

    private fun codename(files: List<Pair<String, String>>): List<ChartPreview> {
        val metas = HashMap<String, CodenameMeta>()
        val charts = mutableListOf<Triple<String, Pair<String, String>, String>>()
        files.forEach { (path, text) ->
            val segments = parts(path)
            val name = segments.lastOrNull() ?: return@forEach
            val stem = name.substringBeforeLast('.')
            val chartsIndex = segments.indexOfLast { it.equals("charts", ignoreCase = true) }
            when {
                chartsIndex >= 0 && chartsIndex < segments.size - 1 -> {
                    val song = segments.getOrNull(chartsIndex - 1)?.lowercase() ?: "song"
                    val variant = if (segments.size - chartsIndex == 3) segments[chartsIndex + 1].lowercase() else ""
                    charts += Triple(song, variant to stem.lowercase(), text)
                }
                stem.equals("meta", ignoreCase = true) || stem.lowercase().startsWith("meta-") -> {
                    val song = segments.dropLast(1).lastOrNull()?.lowercase() ?: "song"
                    val variant = if (stem.contains('-')) stem.substringAfter('-').lowercase() else ""
                    runCatching { CodenameChartParser.parseMeta(text, song) }.getOrNull()?.let { metas["$song|$variant"] = it }
                }
                CodenameChartParser.isCodenameChart(text) -> charts += Triple(segments.dropLast(1).lastOrNull()?.lowercase() ?: "song", "" to stem.lowercase(), text)
            }
        }
        return charts.mapNotNull { (song, key, text) ->
            val (variant, difficulty) = key
            val chart = runCatching { CodenameChartParser.parseChart(text) }.getOrNull() ?: return@mapNotNull null
            val meta = (metas["$song|$variant"] ?: metas["$song|"] ?: CodenameMeta.default(song)).merge(chart.meta)
            val timeline = CodenameTimeline(meta.bpm, meta.beatsPerMeasure, meta.stepsPerBeat, chart.events)
            val sides = chart.strumLines.map { line -> side(line) }
            val notes = chart.strumLines.flatMapIndexed { index, line ->
                line.notes.map { note -> PreviewNote(note.time, sides[index], note.id.mod(line.keyCount.coerceAtLeast(1)).mod(4), note.length, chart.noteTypeName(note.type)) }
            }.sortedBy { it.time }
            val focus = chart.events.filter { it.name == "Camera Movement" }.mapNotNull { event ->
                val index = (event.params.getOrNull(0) as? Number)?.toInt() ?: event.params.getOrNull(0)?.toString()?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
                sides.getOrNull(index)?.let { PreviewFocus(event.time, it) }
            }
            val skip = setOf("Camera Movement", "BPM Change", "Time Signature Change", "Continuous BPM Change")
            fun first(predicate: (CodenameStrumLine) -> Boolean) = chart.strumLines.firstOrNull(predicate)?.characters?.firstOrNull().orEmpty()
            ChartPreview(
                song = song,
                variation = variant,
                difficulty = difficulty,
                notes = notes,
                events = chart.events.filter { it.name !in skip }.map { PreviewEvent(it.time, it.name) },
                timing = timing(timeline.changes.map { PreviewTiming(it.songTime, it.bpm, it.beatsPerMeasure.toInt().coerceAtLeast(1)) }),
                focus = compact(focus),
                speed = chart.scrollSpeed,
                player = first { it.isPlayer },
                opponent = first { it.isOpponent },
                girlfriend = first { it.isGirlfriend && !it.isPlayer && !it.isOpponent }.ifEmpty { first { it.isGirlfriend } },
                stage = chart.stage
            )
        }
    }

    private fun side(line: CodenameStrumLine): Int = when {
        line.isPlayer -> ChartSide.PLAYER
        line.isOpponent -> ChartSide.OPPONENT
        else -> ChartSide.GIRLFRIEND
    }

    private fun timing(list: List<PreviewTiming>): List<PreviewTiming> {
        val result = mutableListOf<PreviewTiming>()
        list.sortedBy { it.time }.forEach { change ->
            val last = result.lastOrNull()
            when {
                last != null && Math.abs(last.time - change.time) < 0.5 -> result[result.lastIndex] = change.copy(time = last.time)
                last != null && last.bpm == change.bpm && last.numerator == change.numerator -> Unit
                else -> result += change
            }
        }
        return result
    }

    private fun compact(focus: List<PreviewFocus>): List<PreviewFocus> {
        val result = mutableListOf<PreviewFocus>()
        focus.sortedBy { it.time }.forEach { if (result.lastOrNull()?.side != it.side) result += it }
        return result
    }
}
