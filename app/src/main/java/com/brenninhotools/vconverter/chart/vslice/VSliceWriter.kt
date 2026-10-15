package com.brenninhotools.vconverter.chart.vslice

import com.brenninhotools.vconverter.chart.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject

object VSliceWriter {
    const val METADATA_VERSION = "2.2.4"
    const val CHART_VERSION = "2.0.0"

    fun metadataJson(metadata: VSliceMetadata): String {
        val characters = JSONObject()
            .put("player", metadata.characters.player)
            .put("girlfriend", metadata.characters.girlfriend)
            .put("opponent", metadata.characters.opponent)
            .put("instrumental", metadata.characters.instrumental)
            .put("altInstrumentals", JSONArray())

        val ratings = JSONObject()
        metadata.difficulties.forEach { ratings.put(it, 0) }

        val playData = JSONObject()
            .put("songVariations", JSONArray())
            .put("difficulties", JSONArray(metadata.difficulties))
            .put("characters", characters)
            .put("stage", metadata.stage)
            .put("noteStyle", metadata.noteStyle)
            .put("ratings", ratings)
            .put("previewStart", metadata.previewStart)
            .put("previewEnd", metadata.previewEnd)

        val timeChanges = JSONArray()
        metadata.timeChanges.forEach { change ->
            timeChanges.put(
                JSONObject()
                    .put("t", change.time.toJsonNumber())
                    .put("b", change.beat.toJsonNumber())
                    .put("bpm", change.bpm.toJsonNumber())
                    .put("n", change.numerator)
                    .put("d", change.denominator)
                    .put("bt", JSONArray(listOf(4, 4, 4, 4)))
            )
        }

        val root = JSONObject()
            .put("version", METADATA_VERSION)
            .put("songName", metadata.songName)
            .put("artist", metadata.artist)
            .put("charter", metadata.charter)
            .put("divisions", 96)
            .put("looped", false)
            .put("playData", playData)
            .put("generatedBy", metadata.generatedBy)
            .put("timeFormat", "ms")
            .put("timeChanges", timeChanges)

        return root.toString(2)
    }

    fun chartJson(chart: VSliceChart): String {
        val scrollSpeed = JSONObject()
        chart.scrollSpeed.forEach { (difficulty, speed) -> scrollSpeed.put(difficulty, speed.toJsonNumber()) }

        val events = JSONArray()
        chart.events.forEach { event ->
            val value = JSONObject()
            event.value.forEach { (key, raw) ->
                value.put(key, if (raw is Double) raw.toJsonNumber() else raw)
            }
            events.put(
                JSONObject()
                    .put("t", event.time.toJsonNumber())
                    .put("e", event.kind)
                    .put("v", value)
            )
        }

        val notes = JSONObject()
        chart.notes.forEach { (difficulty, list) ->
            val array = JSONArray()
            list.forEach { note ->
                val entry = JSONObject()
                    .put("t", note.time.toJsonNumber())
                    .put("d", note.direction)
                if (note.length > 0.0) entry.put("l", note.length.toJsonNumber())
                if (note.kind.isNotEmpty()) entry.put("k", note.kind)
                array.put(entry)
            }
            notes.put(difficulty, array)
        }

        val root = JSONObject()
            .put("version", CHART_VERSION)
            .put("scrollSpeed", scrollSpeed)
            .put("events", events)
            .put("notes", notes)
            .put("generatedBy", chart.generatedBy)

        return root.toString(2)
    }
}
