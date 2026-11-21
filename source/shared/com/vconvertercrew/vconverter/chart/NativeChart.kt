package com.vconvertercrew.vconverter.chart

import com.vconvertercrew.vconverter.chart.vslice.VSliceChart
import com.vconvertercrew.vconverter.chart.vslice.VSliceCharacters
import com.vconvertercrew.vconverter.chart.vslice.VSliceEvent
import com.vconvertercrew.vconverter.chart.vslice.VSliceMetadata
import com.vconvertercrew.vconverter.chart.vslice.VSliceNote
import com.vconvertercrew.vconverter.chart.vslice.VSliceTimeChange
import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.script.NativeData

internal object NativeChart {
    private val intKeys = setOf("char", "rate")

    fun convert(sources: List<ChartSource>, options: ConversionOptions): ConversionResult? {
        if (sources.any { it.song.raw.isBlank() } || options.noteStyle == "") return null
        val charts = JSONArray()
        sources.forEach { charts.put(JSONObject().put("difficulty", it.difficulty).put("text", it.song.raw)) }
        val extra = JSONArray()
        options.extraEvents.forEach { extra.put(JSONObject().put("time", it.time).put("name", it.name).put("value1", it.value1).put("value2", it.value2)) }
        val opts = JSONObject()
            .put("songName", options.songName)
            .put("artist", options.artist)
            .put("charter", options.charter)
            .put("extraEvents", extra)
            .put("customStageIds", JSONArray(options.customStageIds.toList()))
            .put("keepPsychEvents", options.keepPsychEvents)
            .put("splitVariations", options.splitVariations)
            .put("noteStyle", options.noteStyle ?: "")
            .put("pixelStages", JSONArray(options.pixelStages.toList()))
            .put("generatedBy", options.generatedBy)
        options.playerVocals?.let { opts.put("playerVocals", JSONArray(it)) }
        options.opponentVocals?.let { opts.put("opponentVocals", JSONArray(it)) }
        val result = NativeData.convert("chart", JSONObject().put("charts", charts).put("options", opts)) ?: return null
        return runCatching { read(result) }.getOrNull()
    }

    private fun read(result: JSONObject): ConversionResult {
        val main = result.getJSONObject("main")
        val variations = result.optJSONArray("variations")?.let { array ->
            (0 until array.length()).map { i ->
                val v = array.getJSONObject(i)
                VariationChart(v.getString("variation"), metadata(v.getJSONObject("metadata")), chart(v.getJSONObject("chart"), v.getJSONObject("metadata").getString("generatedBy")))
            }
        }.orEmpty()
        return ConversionResult(metadata(main.getJSONObject("metadata")), chart(main.getJSONObject("chart"), main.getJSONObject("metadata").getString("generatedBy")), report(result.getJSONObject("report")), variations)
    }

    private fun strings(array: JSONArray?): List<String> = NativeData.strings(array)

    private fun metadata(json: JSONObject): VSliceMetadata {
        val chars = json.getJSONObject("characters")
        val changes = json.getJSONArray("timeChanges")
        return VSliceMetadata(
            songName = json.getString("songName"),
            artist = json.getString("artist"),
            charter = json.getString("charter"),
            difficulties = strings(json.optJSONArray("difficulties")),
            characters = VSliceCharacters(
                player = chars.getString("player"),
                girlfriend = chars.getString("girlfriend"),
                opponent = chars.getString("opponent"),
                playerVocals = chars.optJSONArray("playerVocals")?.let { strings(it) },
                opponentVocals = chars.optJSONArray("opponentVocals")?.let { strings(it) }
            ),
            stage = json.getString("stage"),
            noteStyle = json.getString("noteStyle"),
            timeChanges = (0 until changes.length()).map { i ->
                val c = changes.getJSONObject(i)
                VSliceTimeChange(c.getDouble("time"), c.getDouble("beat"), c.getDouble("bpm"), c.getInt("numerator"))
            },
            songVariations = strings(json.optJSONArray("songVariations")),
            offset = json.optDouble("offset", 0.0),
            generatedBy = json.getString("generatedBy")
        )
    }

    private fun chart(json: JSONObject, generatedBy: String): VSliceChart {
        val speeds = LinkedHashMap<String, Double>()
        val speedJson = json.getJSONObject("scrollSpeed")
        speedJson.keys().forEach { speeds[it] = speedJson.getDouble(it) }
        val events = json.getJSONArray("events").let { array ->
            (0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                val raw = e.getJSONObject("value")
                val value = LinkedHashMap<String, Any>()
                raw.keys().forEach { key ->
                    val item = raw.get(key)
                    value[key] = if (item is Number) (if (key in intKeys) item.toInt() else item.toDouble()) else item
                }
                VSliceEvent(e.getDouble("time"), e.getString("kind"), value)
            }
        }
        val notesJson = json.getJSONObject("notes")
        val notes = LinkedHashMap<String, List<VSliceNote>>()
        notesJson.keys().forEach { difficulty ->
            val array = notesJson.getJSONArray(difficulty)
            notes[difficulty] = (0 until array.length()).map { i ->
                val n = array.getJSONObject(i)
                VSliceNote(n.getDouble("time"), n.getInt("direction"), n.getDouble("length"), n.optString("kind"))
            }
        }
        return VSliceChart(speeds, events, notes, generatedBy)
    }

    private fun report(json: JSONObject): ConversionReport {
        val report = ConversionReport()
        fun pairs(key: String, into: MutableMap<String, Int>) {
            val array = json.optJSONArray(key) ?: return
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                into[o.getString("name")] = o.getInt("count")
            }
        }
        pairs("noteCounts", report.noteCounts)
        pairs("duplicateNotes", report.duplicateNotes)
        pairs("unsupportedEvents", report.unsupportedEvents)
        report.noteKinds += strings(json.optJSONArray("noteKinds"))
        strings(json.optJSONArray("warnings")).forEach(report::warn)
        report.cameraEvents = json.optInt("cameraEvents")
        report.convertedEvents = json.optInt("convertedEvents")
        report.keptPsychEvents = json.optInt("keptPsychEvents")
        report.offsetApplied = json.optDouble("offsetApplied", 0.0)
        json.optJSONArray("variations")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                report.variations[o.getString("name")] = strings(o.optJSONArray("difficulties"))
            }
        }
        return report
    }
}
