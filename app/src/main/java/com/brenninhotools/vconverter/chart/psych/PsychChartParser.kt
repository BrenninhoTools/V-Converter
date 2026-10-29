package com.brenninhotools.vconverter.chart.psych

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PsychChartException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PsychChartParser {
    const val DEFAULT_BPM = 100.0

    private val legacyNoteTypes = listOf("", "Alt Animation", "Hey!", "Hurt Note", "GF Sing", "No Animation")

    fun parse(raw: String): PsychSong {
        val root = try {
            JSONObject(raw.trim().removePrefix("﻿"))
        } catch (e: JSONException) {
            throw PsychChartException("The file is not valid JSON: ${e.message}", e)
        }
        val song = (root.value("song") as? JSONObject) ?: root
        if (song.value("notes") !is JSONArray && song.value("events") !is JSONArray) {
            throw PsychChartException("This JSON does not look like a Psych Engine chart (no \"notes\" or \"events\" found).")
        }

        val format = song.value("format").asText().ifBlank { "unknown" }
        val absoluteLanes = format.startsWith("psych_v1")
        val events = mutableListOf<PsychEvent>()
        val sections = mutableListOf<PsychSection>()
        var invalidNotes = 0
        var wrappedLanes = 0

        val sectionArray = song.value("notes") as? JSONArray ?: JSONArray()
        for (i in 0 until sectionArray.length()) {
            val section = sectionArray.value(i) as? JSONObject ?: continue
            val mustHit = section.value("mustHitSection").asBool(true)
            val notes = mutableListOf<PsychNote>()
            val noteArray = section.value("sectionNotes") as? JSONArray ?: JSONArray()

            for (j in 0 until noteArray.length()) {
                val note = noteArray.value(j) as? JSONArray
                if (note == null) {
                    invalidNotes++
                    continue
                }
                val time = note.value(0).asDouble(Double.NaN)
                val data = note.value(1).asDouble(Double.NaN)
                if (time.isNaN() || data.isNaN()) {
                    invalidNotes++
                    continue
                }
                val lane = data.toInt()
                if (lane < 0) {
                    events += PsychEvent(time, note.value(2).asText(), note.value(3).asText(), note.value(4).asText())
                    continue
                }
                var resolved = lane
                if (resolved > 7) {
                    resolved %= 8
                    wrappedLanes++
                }
                if (!absoluteLanes) {
                    val playerHits = if (resolved < 4) mustHit else !mustHit
                    resolved = resolved % 4 + if (playerHits) 0 else 4
                }
                val length = note.value(2).asDouble(0.0).coerceAtLeast(0.0)
                notes += PsychNote(time, resolved, length, noteType(note.value(3)))
            }

            sections += PsychSection(
                beats = sectionBeats(section),
                mustHit = mustHit,
                gfSection = section.value("gfSection").asBool(false),
                altAnim = section.value("altAnim").asBool(false),
                changeBpm = section.value("changeBPM").asBool(false),
                bpm = section.value("bpm").asDouble(0.0),
                notes = notes
            )
        }

        events += parseEvents(song.value("events") as? JSONArray)

        val bpm = song.value("bpm").asDouble(DEFAULT_BPM).takeIf { it > 0.0 } ?: DEFAULT_BPM
        val girlfriend = song.value("gfVersion").asText().ifBlank { song.value("player3").asText() }.ifBlank { "gf" }

        return PsychSong(
            name = song.value("song").asText(),
            format = format,
            bpm = bpm,
            speed = song.value("speed").asDouble(1.0).takeIf { it > 0.0 } ?: 1.0,
            offset = song.value("offset").asDouble(0.0),
            needsVoices = song.value("needsVoices").asBool(true),
            player1 = song.value("player1").asText().ifBlank { "bf" },
            player2 = song.value("player2").asText().ifBlank { "dad" },
            girlfriend = girlfriend,
            stage = song.value("stage").asText().ifBlank { "stage" },
            arrowSkin = song.value("arrowSkin").asText(),
            splashSkin = song.value("splashSkin").asText(),
            sections = sections,
            events = events.sortedBy { it.time },
            invalidNotes = invalidNotes,
            wrappedLanes = wrappedLanes
        )
    }

    fun parseEventsOnly(raw: String): List<PsychEvent> = parse(raw).events

    private fun sectionBeats(section: JSONObject): Double {
        val beats = section.value("sectionBeats").asDouble(Double.NaN)
        if (!beats.isNaN() && beats > 0.0) return beats
        val steps = section.value("lengthInSteps").asDouble(Double.NaN)
        if (!steps.isNaN() && steps > 0.0) return steps / 4.0
        return 4.0
    }

    private fun noteType(raw: Any?): String = when (raw) {
        null -> ""
        is String -> raw
        is Boolean -> if (raw) "Alt Animation" else ""
        is Number -> legacyNoteTypes.getOrElse(raw.toInt()) { "" }
        else -> ""
    }

    private fun parseEvents(array: JSONArray?): List<PsychEvent> {
        if (array == null) return emptyList()
        val result = mutableListOf<PsychEvent>()
        for (i in 0 until array.length()) {
            val entry = array.value(i) as? JSONArray ?: continue
            val time = entry.value(0).asDouble(Double.NaN)
            if (time.isNaN()) continue
            when (val payload = entry.value(1)) {
                is JSONArray -> for (j in 0 until payload.length()) {
                    val sub = payload.value(j) as? JSONArray ?: continue
                    val name = sub.value(0).asText()
                    if (name.isNotBlank()) result += PsychEvent(time, name, sub.value(1).asText(), sub.value(2).asText())
                }
                is String -> if (payload.isNotBlank()) {
                    result += PsychEvent(time, payload, entry.value(2).asText(), entry.value(3).asText())
                }
            }
        }
        return result
    }
}
