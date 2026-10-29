package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.core.json.value
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

data class EditorNote(val time: Double, val data: Int, val length: Double = 0.0, val kind: String = "") {
    val isPlayer: Boolean get() = data in 0..3
    val direction: Int get() = data.mod(4)
    val column: Int get() = if (isPlayer) direction + 4 else direction
}

data class EditorEvent(val time: Double, val kind: String, val value: Any?) {
    fun field(key: String): Any? = (value as? JSONObject)?.opt(key)
    val summary: String get() = when (val v = value) {
        is JSONObject -> v.keys().asSequence().joinToString(", ") { "$it=${v.opt(it)}" }
        null -> ""
        else -> v.toString()
    }
}

data class EditorTiming(val time: Double, val bpm: Double, val numerator: Int = 4)

data class DifficultyChart(val speed: Double, val notes: List<EditorNote>)

data class EditorMeta(
    val songName: String = "",
    val artist: String = "",
    val charter: String = "",
    val player: String = "bf",
    val opponent: String = "dad",
    val girlfriend: String = "gf",
    val stage: String = "mainStage",
    val noteStyle: String = "funkin",
    val offset: Double = 0.0,
    val previewStart: Int = 0,
    val previewEnd: Int = 15000
)

data class ChartDocument(
    val id: String,
    val variation: String = "",
    val meta: EditorMeta = EditorMeta(),
    val timing: List<EditorTiming> = listOf(EditorTiming(0.0, 120.0, 4)),
    val difficulties: Map<String, DifficultyChart> = linkedMapOf("normal" to DifficultyChart(1.0, emptyList())),
    val events: List<EditorEvent> = emptyList(),
    val rawMetadata: String? = null
) {
    val difficultyNames: List<String> get() = difficulties.keys.toList()

    val conductor: Conductor by lazy { Conductor(timing) }

    val length: Double get() {
        val notes = difficulties.values.flatMap { it.notes }.maxOfOrNull { it.time + it.length } ?: 0.0
        val lastEvent = events.maxOfOrNull { it.time } ?: 0.0
        return maxOf(notes, lastEvent)
    }

    fun notes(difficulty: String): List<EditorNote> = difficulties[difficulty]?.notes ?: emptyList()

    fun noteAt(difficulty: String, time: Double, data: Int, toleranceMs: Double): EditorNote? =
        notes(difficulty).filter { it.data == data && time >= it.time - toleranceMs && time <= it.time + maxOf(it.length, 0.0) + toleranceMs }
            .minByOrNull { abs(it.time - time) }

    private fun withNotes(difficulty: String, change: (List<EditorNote>) -> List<EditorNote>): ChartDocument {
        val chart = difficulties[difficulty] ?: return this
        val updated = LinkedHashMap(difficulties)
        updated[difficulty] = chart.copy(notes = change(chart.notes).sortedWith(compareBy({ it.time }, { it.data })))
        return copy(difficulties = updated)
    }

    fun addNote(difficulty: String, note: EditorNote): ChartDocument =
        withNotes(difficulty) { list -> list.filterNot { same(it, note) } + note }

    fun removeNote(difficulty: String, note: EditorNote): ChartDocument =
        withNotes(difficulty) { list -> list.filterNot { same(it, note) } }

    fun replaceNote(difficulty: String, old: EditorNote, new: EditorNote): ChartDocument =
        withNotes(difficulty) { list -> list.filterNot { same(it, old) || same(it, new) } + new }

    fun mirrorNotes(difficulty: String): ChartDocument =
        withNotes(difficulty) { list -> list.map { it.copy(data = if (it.isPlayer) it.data + 4 else it.data - 4) } }

    fun clearNotes(difficulty: String): ChartDocument = withNotes(difficulty) { emptyList() }

    private fun same(a: EditorNote, b: EditorNote): Boolean = a.data == b.data && abs(a.time - b.time) < 0.5

    fun addEvent(event: EditorEvent): ChartDocument = copy(events = (events + event).sortedBy { it.time })

    fun removeEvent(event: EditorEvent): ChartDocument = copy(events = events.filterNot { it === event || (it.kind == event.kind && abs(it.time - event.time) < 0.5 && it.summary == event.summary) })

    fun replaceEvent(old: EditorEvent, new: EditorEvent): ChartDocument = removeEvent(old).addEvent(new)

    fun eventsNear(time: Double, toleranceMs: Double): List<EditorEvent> = events.filter { abs(it.time - time) <= toleranceMs }

    fun withTiming(list: List<EditorTiming>): ChartDocument {
        val cleaned = list.filter { it.bpm > 0.0 && it.numerator > 0 }.sortedBy { it.time }.distinctBy { it.time }
        val fixed = if (cleaned.isEmpty() || cleaned.first().time > 0.0) listOf(EditorTiming(0.0, cleaned.firstOrNull()?.bpm ?: 120.0, cleaned.firstOrNull()?.numerator ?: 4)) + cleaned.filter { it.time > 0.0 } else cleaned
        return copy(timing = fixed)
    }

    fun addDifficulty(name: String, copyFrom: String? = null): ChartDocument {
        val clean = name.trim().lowercase().replace(Regex("\\s+"), "-")
        require(clean.isNotEmpty()) { "Enter a difficulty name." }
        require(clean !in difficulties) { "\"$clean\" already exists." }
        val source = copyFrom?.let { difficulties[it] }
        val updated = LinkedHashMap(difficulties)
        updated[clean] = DifficultyChart(source?.speed ?: difficulties.values.firstOrNull()?.speed ?: 1.0, source?.notes ?: emptyList())
        return copy(difficulties = updated)
    }

    fun removeDifficulty(name: String): ChartDocument {
        require(difficulties.size > 1) { "A song needs at least one difficulty." }
        val updated = LinkedHashMap(difficulties)
        updated.remove(name)
        return copy(difficulties = updated)
    }

    fun withSpeed(difficulty: String, speed: Double): ChartDocument {
        val chart = difficulties[difficulty] ?: return this
        val updated = LinkedHashMap(difficulties)
        updated[difficulty] = chart.copy(speed = speed)
        return copy(difficulties = updated)
    }

    fun metadataJson(): String {
        val root = rawMetadata?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val play = root.optJSONObject("playData") ?: JSONObject().also { root.put("playData", it) }
        val characters = play.optJSONObject("characters") ?: JSONObject().also { play.put("characters", it) }
        root.put("version", root.optString("version").ifEmpty { VSliceWriter.METADATA_VERSION })
        root.put("songName", meta.songName.ifBlank { id })
        root.put("artist", meta.artist.ifBlank { "Unknown" })
        root.put("charter", meta.charter.ifBlank { "Unknown" })
        if (!root.has("divisions")) root.put("divisions", 96)
        if (!root.has("looped")) root.put("looped", false)
        root.put("timeFormat", "ms")
        root.put("generatedBy", GENERATED_BY)
        characters.put("player", meta.player)
        characters.put("girlfriend", meta.girlfriend)
        characters.put("opponent", meta.opponent)
        if (!characters.has("instrumental")) characters.put("instrumental", "")
        if (!characters.has("altInstrumentals")) characters.put("altInstrumentals", JSONArray())
        play.put("difficulties", JSONArray(difficultyNames))
        if (!play.has("songVariations")) play.put("songVariations", JSONArray())
        play.put("stage", meta.stage)
        play.put("noteStyle", meta.noteStyle)
        val ratings = play.optJSONObject("ratings") ?: JSONObject()
        difficultyNames.forEach { if (!ratings.has(it)) ratings.put(it, 0) }
        play.put("ratings", ratings)
        play.put("previewStart", meta.previewStart)
        play.put("previewEnd", meta.previewEnd)
        val changes = JSONArray()
        var beat = 0.0
        timing.forEachIndexed { index, change ->
            if (index > 0) {
                val previous = timing[index - 1]
                beat += (change.time - previous.time) * previous.bpm / 60000.0
            }
            changes.put(
                JSONObject()
                    .put("t", change.time.toJsonNumber())
                    .put("b", (Math.round(beat * 10000.0) / 10000.0).toJsonNumber())
                    .put("bpm", change.bpm.toJsonNumber())
                    .put("n", change.numerator)
                    .put("d", 4)
                    .put("bt", JSONArray(listOf(4, 4, 4, 4)))
            )
        }
        root.put("timeChanges", changes)
        if (meta.offset != 0.0) {
            val offsets = root.optJSONObject("offsets") ?: JSONObject().put("altInstrumentals", JSONObject()).put("vocals", JSONObject()).put("altVocals", JSONObject())
            offsets.put("instrumental", meta.offset.toJsonNumber())
            root.put("offsets", offsets)
        } else {
            root.optJSONObject("offsets")?.put("instrumental", 0)
        }
        return root.toString(2)
    }

    fun chartJson(): String {
        val speeds = JSONObject()
        difficulties.forEach { (name, chart) -> speeds.put(name, chart.speed.toJsonNumber()) }
        val eventArray = JSONArray()
        events.forEach { event ->
            eventArray.put(JSONObject().put("t", event.time.toJsonNumber()).put("e", event.kind).put("v", event.value ?: JSONObject()))
        }
        val notes = JSONObject()
        difficulties.forEach { (name, chart) ->
            val array = JSONArray()
            chart.notes.forEach { note ->
                val entry = JSONObject().put("t", note.time.toJsonNumber()).put("d", note.data)
                if (note.length > 0.0) entry.put("l", note.length.toJsonNumber())
                if (note.kind.isNotEmpty()) entry.put("k", note.kind)
                array.put(entry)
            }
            notes.put(name, array)
        }
        return JSONObject()
            .put("version", VSliceWriter.CHART_VERSION)
            .put("scrollSpeed", speeds)
            .put("events", eventArray)
            .put("notes", notes)
            .put("generatedBy", GENERATED_BY)
            .toString(2)
    }

    fun fileNames(): Pair<String, String> {
        val suffix = if (variation.isEmpty() || variation == "default") "" else "-$variation"
        return "data/songs/$id/$id-metadata$suffix.json" to "data/songs/$id/$id-chart$suffix.json"
    }

    companion object {
        const val GENERATED_BY = "V-Converter Chart Editor"

        fun blank(id: String, name: String, bpm: Double, difficulties: List<String>): ChartDocument = ChartDocument(
            id = id,
            meta = EditorMeta(songName = name),
            timing = listOf(EditorTiming(0.0, bpm, 4)),
            difficulties = LinkedHashMap(difficulties.associateWith { DifficultyChart(1.0, emptyList()) }),
            events = listOf(EditorEvent(0.0, "FocusCamera", JSONObject().put("char", 1)))
        )

        fun load(id: String, variation: String, metadata: String, chart: String): ChartDocument {
            val meta = JSONObject(metadata.trim().removePrefix("﻿"))
            val root = JSONObject(chart.trim().removePrefix("﻿"))
            val play = meta.optJSONObject("playData") ?: JSONObject()
            val characters = play.optJSONObject("characters") ?: JSONObject()
            val timing = (meta.value("timeChanges") as? JSONArray)?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
                    EditorTiming(it.value("t").asDouble(0.0), it.value("bpm").asDouble(100.0), it.value("n").asDouble(4.0).toInt().coerceAtLeast(1))
                }
            }.orEmpty().sortedBy { it.time }.ifEmpty { listOf(EditorTiming(0.0, 100.0, 4)) }
            val speeds = root.optJSONObject("scrollSpeed") ?: JSONObject()
            val notesRoot = root.optJSONObject("notes") ?: JSONObject()
            val listed = (play.value("difficulties") as? JSONArray)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val names = (listed + notesRoot.keys().asSequence().toList()).filter { it.isNotBlank() }.distinct()
            val difficulties = LinkedHashMap<String, DifficultyChart>()
            names.forEach { name ->
                val array = notesRoot.optJSONArray(name) ?: JSONArray()
                val notes = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
                    EditorNote(it.value("t").asDouble(0.0), it.value("d").asDouble(0.0).toInt().mod(8), it.value("l").asDouble(0.0).coerceAtLeast(0.0), it.value("k").asText())
                }.sortedWith(compareBy({ it.time }, { it.data }))
                val speed = speeds.value(name).asDouble(Double.NaN).takeIf { !it.isNaN() } ?: speeds.value("default").asDouble(1.0)
                difficulties[name] = DifficultyChart(speed, notes)
            }
            if (difficulties.isEmpty()) difficulties["normal"] = DifficultyChart(1.0, emptyList())
            val eventArray = root.optJSONArray("events") ?: JSONArray()
            val events = (0 until eventArray.length()).mapNotNull { eventArray.optJSONObject(it) }.map {
                EditorEvent(it.value("t").asDouble(0.0), it.value("e").asText(), it.opt("v"))
            }.sortedBy { it.time }
            val offsets = meta.optJSONObject("offsets")
            return ChartDocument(
                id = id,
                variation = variation,
                meta = EditorMeta(
                    songName = meta.optString("songName"),
                    artist = meta.optString("artist"),
                    charter = meta.optString("charter"),
                    player = characters.optString("player", "bf"),
                    opponent = characters.optString("opponent", "dad"),
                    girlfriend = characters.optString("girlfriend", "gf"),
                    stage = play.optString("stage", "mainStage"),
                    noteStyle = play.optString("noteStyle", "funkin"),
                    offset = offsets?.value("instrumental").asDouble(0.0),
                    previewStart = play.optInt("previewStart", 0),
                    previewEnd = play.optInt("previewEnd", 15000)
                ),
                timing = timing,
                difficulties = difficulties,
                events = events,
                rawMetadata = metadata
            )
        }
    }
}

class Conductor(changes: List<EditorTiming>) {
    private val list = changes.sortedBy { it.time }.ifEmpty { listOf(EditorTiming(0.0, 100.0, 4)) }
    private val beats: DoubleArray = DoubleArray(list.size).also { array ->
        for (i in 1 until list.size) array[i] = array[i - 1] + (list[i].time - list[i - 1].time) * list[i - 1].bpm / 60000.0
    }

    private fun indexAtTime(ms: Double): Int {
        var index = 0
        for (i in list.indices) if (list[i].time <= ms + 1e-6) index = i
        return index
    }

    private fun indexAtBeat(beat: Double): Int {
        var index = 0
        for (i in list.indices) if (beats[i] <= beat + 1e-9) index = i
        return index
    }

    fun change(ms: Double): EditorTiming = list[indexAtTime(ms)]

    fun beatAt(ms: Double): Double {
        val i = indexAtTime(ms)
        return beats[i] + (ms - list[i].time) * list[i].bpm / 60000.0
    }

    fun timeAt(beat: Double): Double {
        val i = indexAtBeat(beat)
        return list[i].time + (beat - beats[i]) * 60000.0 / list[i].bpm
    }

    fun stepMs(ms: Double): Double = 15000.0 / change(ms).bpm

    fun snap(ms: Double, division: Int): Double {
        val step = 4.0 / division.coerceAtLeast(1)
        val beat = beatAt(ms)
        val snapped = Math.round(beat / step) * step
        return (timeAt(snapped) * 1000.0).roundToLong() / 1000.0
    }

    fun floorSnap(ms: Double, division: Int): Double {
        val step = 4.0 / division.coerceAtLeast(1)
        val snapped = floor(beatAt(ms) / step + 1e-6) * step
        return (timeAt(snapped) * 1000.0).roundToLong() / 1000.0
    }

    fun gridLines(from: Double, to: Double, division: Int): List<Pair<Double, Int>> {
        val result = mutableListOf<Pair<Double, Int>>()
        val step = 4.0 / division.coerceAtLeast(1)
        var beat = floor(beatAt(maxOf(0.0, from)) / step) * step
        var guard = 0
        while (guard++ < 20000) {
            val time = timeAt(beat)
            if (time > to) break
            if (time >= from - 1.0) {
                val change = change(time)
                val beatInChange = beat - beats[list.indexOf(change)]
                val numerator = change.numerator.coerceAtLeast(1)
                val kind = when {
                    abs(beatInChange / numerator - Math.round(beatInChange / numerator)) < 1e-6 -> 2
                    abs(beat - Math.round(beat)) < 1e-6 -> 1
                    else -> 0
                }
                result += time to kind
            }
            beat += step
        }
        return result
    }

    fun measureAt(ms: Double): Int {
        val i = indexAtTime(ms)
        var measure = 0.0
        for (k in 1..i) measure += (beats[k] - beats[k - 1]) / list[k - 1].numerator
        return floor(measure + (beatAt(ms) - beats[i]) / list[i].numerator + 1e-6).toInt()
    }
}
