package com.brenninhotools.vconverter.week

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONException
import com.brenninhotools.vconverter.json.JSONObject

data class PsychWeekSong(val name: String, val icon: String, val color: List<Int>)

data class PsychWeek(
    val songs: List<PsychWeekSong>,
    val weekCharacters: List<String>,
    val weekBackground: String,
    val storyName: String,
    val weekName: String,
    val weekBefore: String,
    val startUnlocked: Boolean,
    val hiddenUntilUnlocked: Boolean,
    val hideStoryMode: Boolean,
    val hideFreeplay: Boolean,
    val difficulties: List<String>
)

data class PsychMenuCharacter(
    val image: String,
    val scale: Double,
    val positionX: Double,
    val positionY: Double,
    val idleAnim: String,
    val confirmAnim: String,
    val flipX: Boolean,
    val antialiasing: Boolean
)

class PsychWeekException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PsychWeekParser {
    fun parse(raw: String): PsychWeek {
        val root = json(raw)
        val songs = root.value("songs") as? JSONArray
            ?: throw PsychWeekException("This JSON does not look like a Psych Engine week (no \"songs\" list).")

        val parsedSongs = (0 until songs.length()).mapNotNull { i ->
            val entry = songs.value(i) as? JSONArray ?: return@mapNotNull null
            val name = entry.value(0).asText()
            if (name.isBlank()) return@mapNotNull null
            val color = (entry.value(2) as? JSONArray)?.let { arr ->
                (0 until arr.length()).map { arr.value(it).asDouble(0.0).toInt().coerceIn(0, 255) }
            }?.takeIf { it.size >= 3 } ?: listOf(146, 113, 253)
            PsychWeekSong(name, entry.value(1).asText(), color)
        }

        val characters = (root.value("weekCharacters") as? JSONArray)?.let { arr ->
            (0 until arr.length()).map { arr.value(it).asText() }
        } ?: listOf("dad", "bf", "gf")

        val difficulties = root.value("difficulties").asText()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return PsychWeek(
            songs = parsedSongs,
            weekCharacters = characters,
            weekBackground = root.value("weekBackground").asText(),
            storyName = root.value("storyName").asText(),
            weekName = root.value("weekName").asText(),
            weekBefore = root.value("weekBefore").asText(),
            startUnlocked = root.value("startUnlocked").asBool(true),
            hiddenUntilUnlocked = root.value("hiddenUntilUnlocked").asBool(false),
            hideStoryMode = root.value("hideStoryMode").asBool(false),
            hideFreeplay = root.value("hideFreeplay").asBool(false),
            difficulties = difficulties
        )
    }

    fun parseMenuCharacter(raw: String): PsychMenuCharacter {
        val root = json(raw)
        if (root.value("idle_anim") == null || root.value("image") == null) {
            throw PsychWeekException("This JSON does not look like a Psych Engine menu character (missing \"image\" or \"idle_anim\").")
        }
        val position = root.value("position") as? JSONArray
        return PsychMenuCharacter(
            image = root.value("image").asText(),
            scale = root.value("scale").asDouble(1.0).takeIf { it > 0.0 } ?: 1.0,
            positionX = position?.value(0).asDouble(0.0),
            positionY = position?.value(1).asDouble(0.0),
            idleAnim = root.value("idle_anim").asText(),
            confirmAnim = root.value("confirm_anim").asText(),
            flipX = root.value("flipX").asBool(false),
            antialiasing = root.value("antialiasing").asBool(true)
        )
    }

    fun isMenuCharacter(raw: String): Boolean = runCatching { json(raw).has("idle_anim") }.getOrDefault(false)

    private fun json(raw: String): JSONObject = try {
        JSONObject(raw.trim().removePrefix("﻿"))
    } catch (e: JSONException) {
        throw PsychWeekException("The file is not valid JSON: ${e.message}", e)
    }
}
