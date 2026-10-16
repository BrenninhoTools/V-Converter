package com.brenninhotools.vconverter.stage

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class PsychPoint(val x: Double, val y: Double)

data class PsychStageAnimation(
    val anim: String,
    val prefix: String,
    val fps: Int,
    val loop: Boolean,
    val indices: List<Int>,
    val offsetX: Double,
    val offsetY: Double
)

data class PsychStageObject(
    val type: String,
    val name: String,
    val image: String,
    val x: Double,
    val y: Double,
    val scale: PsychPoint,
    val scroll: PsychPoint,
    val alpha: Double,
    val angle: Double,
    val color: String,
    val antialiasing: Boolean,
    val flipX: Boolean,
    val flipY: Boolean,
    val filters: Int,
    val animations: List<PsychStageAnimation>,
    val firstAnimation: String,
    val blend: String = ""
)

data class PsychStage(
    val directory: String,
    val defaultZoom: Double,
    val isPixelStage: Boolean,
    val stageUI: String,
    val boyfriend: PsychPoint,
    val girlfriend: PsychPoint,
    val opponent: PsychPoint,
    val hideGirlfriend: Boolean,
    val cameraBoyfriend: PsychPoint,
    val cameraOpponent: PsychPoint,
    val cameraGirlfriend: PsychPoint,
    val cameraSpeed: Double,
    val preload: List<String>,
    val objects: List<PsychStageObject>?
)

class PsychStageException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PsychStageParser {
    const val FILTER_LOW_QUALITY = 1
    const val FILTER_HIGH_QUALITY = 2
    const val FILTER_STORY_MODE = 4
    const val FILTER_FREEPLAY = 8

    fun parse(raw: String): PsychStage {
        val root = try {
            JSONObject(raw.trim().removePrefix("﻿"))
        } catch (e: JSONException) {
            throw PsychStageException("The file is not valid JSON: ${e.message}", e)
        }
        if (root.value("boyfriend") !is JSONArray && root.value("defaultZoom") == null && root.value("objects") !is JSONArray) {
            throw PsychStageException("This JSON does not look like a Psych Engine stage (no \"boyfriend\", \"defaultZoom\" or \"objects\").")
        }

        val objects = (root.value("objects") as? JSONArray)?.let { array ->
            (0 until array.length()).mapNotNull { i -> (array.value(i) as? JSONObject)?.let(::parseObject) }
        }
        val preload = (root.value("preload") as? JSONObject)?.keys()?.asSequence()?.toList() ?: emptyList()
        val ui = root.value("stageUI").asText()

        return PsychStage(
            directory = root.value("directory").asText(),
            defaultZoom = root.value("defaultZoom").asDouble(0.9),
            isPixelStage = root.value("isPixelStage").asBool(false) || ui.equals("pixel", ignoreCase = true),
            stageUI = ui,
            boyfriend = point(root.value("boyfriend"), 770.0, 100.0),
            girlfriend = point(root.value("girlfriend"), 400.0, 130.0),
            opponent = point(root.value("opponent"), 100.0, 100.0),
            hideGirlfriend = root.value("hide_girlfriend").asBool(false),
            cameraBoyfriend = point(root.value("camera_boyfriend"), 0.0, 0.0),
            cameraOpponent = point(root.value("camera_opponent"), 0.0, 0.0),
            cameraGirlfriend = point(root.value("camera_girlfriend"), 0.0, 0.0),
            cameraSpeed = root.value("camera_speed").asDouble(1.0),
            preload = preload,
            objects = objects
        )
    }

    private fun point(raw: Any?, defaultX: Double, defaultY: Double): PsychPoint {
        val array = raw as? JSONArray ?: return PsychPoint(defaultX, defaultY)
        return PsychPoint(array.value(0).asDouble(defaultX), array.value(1).asDouble(defaultY))
    }

    private fun parseObject(obj: JSONObject): PsychStageObject {
        val animations = (obj.value("animations") as? JSONArray)?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val anim = array.value(i) as? JSONObject ?: return@mapNotNull null
                val offsets = anim.value("offsets") as? JSONArray
                val indices = (anim.value("indices") as? JSONArray)?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.value(it).asDouble(Double.NaN).takeUnless { v -> v.isNaN() }?.toInt() }
                } ?: emptyList()
                PsychStageAnimation(
                    anim = anim.value("anim").asText(),
                    prefix = anim.value("name").asText(),
                    fps = anim.value("fps").asDouble(24.0).toInt(),
                    loop = anim.value("loop").asBool(false),
                    indices = indices,
                    offsetX = offsets?.value(0).asDouble(0.0),
                    offsetY = offsets?.value(1).asDouble(0.0)
                )
            }.filter { it.anim.isNotBlank() }
        } ?: emptyList()

        return PsychStageObject(
            type = obj.value("type").asText(),
            name = obj.value("name").asText(),
            image = obj.value("image").asText(),
            x = obj.value("x").asDouble(0.0),
            y = obj.value("y").asDouble(0.0),
            scale = point(obj.value("scale"), 1.0, 1.0),
            scroll = point(obj.value("scroll"), 1.0, 1.0),
            alpha = obj.value("alpha").asDouble(1.0),
            angle = obj.value("angle").asDouble(0.0),
            color = obj.value("color").asText().ifBlank { "FFFFFF" },
            antialiasing = obj.value("antialiasing").asBool(true),
            flipX = obj.value("flipX").asBool(false),
            flipY = obj.value("flipY").asBool(false),
            filters = obj.value("filters").asDouble((FILTER_LOW_QUALITY or FILTER_HIGH_QUALITY).toDouble()).toInt(),
            animations = animations,
            firstAnimation = obj.value("firstAnimation").asText()
        )
    }
}
