package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class CodenameNote(val time: Double, val id: Int, val type: Int, val length: Double)

data class CodenameStrumLine(
    val characters: List<String>,
    val type: Int,
    val position: String,
    val notes: List<CodenameNote>,
    val visible: Boolean,
    val vocalsSuffix: String,
    val keyCount: Int
) {
    val isPlayer: Boolean get() = type == PLAYER
    val isOpponent: Boolean get() = type == OPPONENT
    val isGirlfriend: Boolean get() = position.equals("girlfriend", ignoreCase = true) || position.equals("gf", ignoreCase = true)

    companion object {
        const val OPPONENT = 0
        const val PLAYER = 1
        const val ADDITIONAL = 2
    }
}

data class CodenameEvent(val name: String, val time: Double, val params: List<Any?>)

data class CodenameMeta(
    val name: String,
    val displayName: String,
    val bpm: Double,
    val beatsPerMeasure: Double,
    val stepsPerBeat: Int,
    val difficulties: List<String>,
    val variants: List<String>,
    val icon: String,
    val color: String,
    val instSuffix: String,
    val vocalsSuffix: String,
    val needsVoices: Boolean,
    val artist: String,
    val charter: String
) {
    fun merge(raw: JSONObject?): CodenameMeta {
        if (raw == null) return this
        val parsed = CodenameChartParser.parseMeta(raw.toString(), name)
        return copy(
            displayName = if (raw.has("displayName")) parsed.displayName else displayName,
            bpm = if (raw.has("bpm")) parsed.bpm else bpm,
            beatsPerMeasure = if (raw.has("beatsPerMeasure")) parsed.beatsPerMeasure else beatsPerMeasure,
            stepsPerBeat = if (raw.has("stepsPerBeat")) parsed.stepsPerBeat else stepsPerBeat,
            icon = if (raw.has("icon")) parsed.icon else icon,
            color = if (raw.has("color")) parsed.color else color,
            instSuffix = if (raw.has("instSuffix")) parsed.instSuffix else instSuffix,
            vocalsSuffix = if (raw.has("vocalsSuffix")) parsed.vocalsSuffix else vocalsSuffix,
            needsVoices = if (raw.has("needsVoices")) parsed.needsVoices else needsVoices,
            artist = parsed.artist.ifBlank { artist },
            charter = parsed.charter.ifBlank { charter }
        )
    }

    companion object {
        fun default(name: String) = CodenameMeta(
            name = name,
            displayName = name,
            bpm = 100.0,
            beatsPerMeasure = 4.0,
            stepsPerBeat = 4,
            difficulties = emptyList(),
            variants = emptyList(),
            icon = "face",
            color = "",
            instSuffix = "",
            vocalsSuffix = "",
            needsVoices = true,
            artist = "",
            charter = ""
        )
    }
}

data class CodenameChartData(
    val strumLines: List<CodenameStrumLine>,
    val events: List<CodenameEvent>,
    val noteTypes: List<String>,
    val scrollSpeed: Double,
    val stage: String,
    val meta: JSONObject?
) {
    fun noteTypeName(type: Int): String = if (type <= 0) "" else noteTypes.getOrNull(type - 1) ?: ""
}

class CodenameChartException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CodenameChartParser {
    fun isCodenameChart(raw: String): Boolean = runCatching {
        val root = JSONObject(raw.trim().removePrefix("﻿"))
        root.value("codenameChart").asBool(false) || root.value("strumLines") is JSONArray
    }.getOrDefault(false)

    fun parseChart(raw: String): CodenameChartData {
        val root = json(raw)
        val strums = root.value("strumLines") as? JSONArray
            ?: throw CodenameChartException("This JSON is not a Codename Engine chart (no \"strumLines\").")
        val strumLines = (0 until strums.length()).mapNotNull { index ->
            val line = strums.value(index) as? JSONObject ?: return@mapNotNull null
            val notesArray = line.value("notes") as? JSONArray
            val notes = if (notesArray == null) emptyList() else (0 until notesArray.length()).mapNotNull { n ->
                val note = notesArray.value(n) as? JSONObject ?: return@mapNotNull null
                val time = note.value("time").asDouble(Double.NaN)
                if (time.isNaN()) return@mapNotNull null
                CodenameNote(
                    time = time,
                    id = note.value("id").asDouble(0.0).toInt(),
                    type = note.value("type").asDouble(0.0).toInt(),
                    length = note.value("sLen").asDouble(0.0).coerceAtLeast(0.0)
                )
            }
            CodenameStrumLine(
                characters = strings(line.value("characters")),
                type = line.value("type").asDouble(0.0).toInt(),
                position = line.value("position").asText().ifBlank { if (line.value("type").asDouble(0.0).toInt() == 1) "boyfriend" else "dad" },
                notes = notes,
                visible = line.value("visible").asBool(true),
                vocalsSuffix = line.value("vocalsSuffix").asText(),
                keyCount = line.value("keyCount").asDouble(4.0).toInt().coerceAtLeast(1)
            )
        }
        return CodenameChartData(
            strumLines = strumLines,
            events = parseEventArray(root.value("events")),
            noteTypes = strings(root.value("noteTypes")),
            scrollSpeed = root.value("scrollSpeed").asDouble(1.0),
            stage = root.value("stage").asText().ifBlank { "stage" },
            meta = root.value("meta") as? JSONObject
        )
    }

    fun parseEvents(raw: String): List<CodenameEvent> = parseEventArray(json(raw).value("events"))

    fun parseMeta(raw: String, name: String): CodenameMeta {
        val root = json(raw)
        val custom = root.value("customValues") as? JSONObject
        val base = CodenameMeta.default(name)
        return base.copy(
            displayName = root.value("displayName").asText().ifBlank { name },
            bpm = root.value("bpm").asDouble(base.bpm).takeIf { it > 0.0 } ?: base.bpm,
            beatsPerMeasure = root.value("beatsPerMeasure").asDouble(4.0),
            stepsPerBeat = root.value("stepsPerBeat").asDouble(4.0).toInt(),
            difficulties = strings(root.value("difficulties")),
            variants = strings(root.value("variants")),
            icon = root.value("icon").asText().ifBlank { base.icon },
            color = colorText(root.value("color")),
            instSuffix = root.value("instSuffix").asText(),
            vocalsSuffix = root.value("vocalsSuffix").asText(),
            needsVoices = root.value("needsVoices").asBool(true),
            artist = custom?.value("artist").asText().ifBlank { root.value("artist").asText() },
            charter = custom?.value("charter").asText().ifBlank { root.value("charter").asText() }
        )
    }

    private fun colorText(raw: Any?): String = when (raw) {
        null -> ""
        is Number -> String.format("#%06X", raw.toLong() and 0xFFFFFF)
        else -> raw.toString()
    }

    private fun parseEventArray(raw: Any?): List<CodenameEvent> {
        val array = raw as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val event = array.value(i) as? JSONObject ?: return@mapNotNull null
            var name = event.value("name").asText()
            if (name.isBlank()) {
                name = when (event.value("type").asDouble(0.0).toInt()) {
                    -1 -> "HScript Call"
                    1 -> "Camera Movement"
                    2 -> "BPM Change"
                    3 -> "Alt Animation Toggle"
                    else -> ""
                }
            }
            if (name.isBlank()) return@mapNotNull null
            val params = (event.value("params") as? JSONArray)?.let { arr -> (0 until arr.length()).map { arr.value(it) } } ?: emptyList()
            CodenameEvent(name, event.value("time").asDouble(0.0), params)
        }
    }

    private fun strings(raw: Any?): List<String> {
        val array = raw as? JSONArray ?: return emptyList()
        return (0 until array.length()).map { array.value(it).asText() }.filter { it.isNotBlank() }
    }

    private fun json(raw: String): JSONObject = try {
        JSONObject(raw.trim().removePrefix("﻿"))
    } catch (e: JSONException) {
        throw CodenameChartException("The file is not valid JSON: ${e.message}", e)
    }
}
