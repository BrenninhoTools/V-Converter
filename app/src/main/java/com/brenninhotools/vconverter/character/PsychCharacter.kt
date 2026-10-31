package com.brenninhotools.vconverter.character

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONException
import com.brenninhotools.vconverter.json.JSONObject

data class PsychAnimation(
    val anim: String,
    val prefix: String,
    val fps: Int,
    val loop: Boolean,
    val indices: List<Int>,
    val offsetX: Double,
    val offsetY: Double
)

data class PsychCharacter(
    val image: String,
    val scale: Double,
    val positionX: Double,
    val positionY: Double,
    val cameraX: Double,
    val cameraY: Double,
    val flipX: Boolean,
    val noAntialiasing: Boolean,
    val healthIcon: String,
    val healthBarColor: List<Int>,
    val singDuration: Double,
    val vocalsFile: String,
    val editorIsPlayer: Boolean?,
    val animations: List<PsychAnimation>
) {
    val atlasImages: List<String> get() = image.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val hasDanceLeftRight: Boolean get() = animations.any { it.anim == "danceLeft" } && animations.any { it.anim == "danceRight" }
}

class PsychCharacterException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PsychCharacterParser {
    fun parse(raw: String): PsychCharacter {
        val root = try {
            JSONObject(raw.trim().removePrefix("﻿"))
        } catch (e: JSONException) {
            throw PsychCharacterException("The file is not valid JSON: ${e.message}", e)
        }
        val animArray = root.value("animations") as? JSONArray
        if (animArray == null || root.value("image") == null) {
            throw PsychCharacterException("This JSON does not look like a Psych Engine character (missing \"animations\" or \"image\").")
        }

        val animations = (0 until animArray.length()).mapNotNull { i ->
            val anim = animArray.value(i) as? JSONObject ?: return@mapNotNull null
            val name = anim.value("anim").asText()
            if (name.isBlank()) return@mapNotNull null
            val offsets = anim.value("offsets") as? JSONArray
            val indices = (anim.value("indices") as? JSONArray)?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.value(it).asDouble(Double.NaN).takeUnless { v -> v.isNaN() }?.toInt() }
            } ?: emptyList()
            PsychAnimation(
                anim = name,
                prefix = anim.value("name").asText(),
                fps = anim.value("fps").asDouble(24.0).toInt(),
                loop = anim.value("loop").asBool(false),
                indices = indices,
                offsetX = offsets?.value(0).asDouble(0.0),
                offsetY = offsets?.value(1).asDouble(0.0)
            )
        }

        val position = root.value("position") as? JSONArray
        val camera = root.value("camera_position") as? JSONArray
        val colors = (root.value("healthbar_colors") as? JSONArray)?.let { arr ->
            (0 until arr.length()).map { arr.value(it).asDouble(0.0).toInt().coerceIn(0, 255) }
        }?.takeIf { it.size >= 3 } ?: listOf(161, 161, 161)

        return PsychCharacter(
            image = root.value("image").asText(),
            scale = root.value("scale").asDouble(1.0).takeIf { it > 0.0 } ?: 1.0,
            positionX = position?.value(0).asDouble(0.0),
            positionY = position?.value(1).asDouble(0.0),
            cameraX = camera?.value(0).asDouble(0.0),
            cameraY = camera?.value(1).asDouble(0.0),
            flipX = root.value("flip_x").asBool(false),
            noAntialiasing = root.value("no_antialiasing").asBool(false),
            healthIcon = root.value("healthicon").asText().ifBlank { "face" },
            healthBarColor = colors,
            singDuration = root.value("sing_duration").asDouble(4.0),
            vocalsFile = root.value("vocals_file").asText(),
            editorIsPlayer = root.value("_editor_isPlayer")?.asBool(false),
            animations = animations
        )
    }
}
