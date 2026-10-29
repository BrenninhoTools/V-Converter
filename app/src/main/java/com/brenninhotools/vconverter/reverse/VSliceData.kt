package com.brenninhotools.vconverter.reverse

import com.brenninhotools.vconverter.core.json.asBool
import com.brenninhotools.vconverter.core.json.asDouble
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class VSliceFormatException(message: String) : Exception(message)

data class VTimeChange(val time: Double, val bpm: Double, val numerator: Int)

data class VMetadata(
    val songName: String,
    val artist: String,
    val charter: String,
    val difficulties: List<String>,
    val variations: List<String>,
    val player: String,
    val opponent: String,
    val girlfriend: String,
    val playerVocals: List<String>?,
    val opponentVocals: List<String>?,
    val stage: String,
    val noteStyle: String,
    val timeChanges: List<VTimeChange>,
    val offsetInstrumental: Double
)

data class VNote(val time: Double, val data: Int, val length: Double, val kind: String)

data class VEvent(val time: Double, val kind: String, val value: Any?)

data class VChart(val scrollSpeed: Map<String, Double>, val events: List<VEvent>, val notes: Map<String, List<VNote>>)

data class VAnimation(
    val name: String,
    val prefix: String,
    val assetPath: String,
    val frameRate: Int,
    val looped: Boolean,
    val indices: List<Int>,
    val offsetX: Double,
    val offsetY: Double,
    val flipX: Boolean,
    val animType: String = ""
)

data class VCharacter(
    val name: String,
    val renderType: String,
    val assetPath: String,
    val scale: Double,
    val isPixel: Boolean,
    val healthIcon: String,
    val healthIconPixel: Boolean,
    val offsetX: Double,
    val offsetY: Double,
    val cameraX: Double,
    val cameraY: Double,
    val singTime: Double,
    val danceEvery: Double,
    val flipX: Boolean,
    val startingAnimation: String,
    val animations: List<VAnimation>
)

data class VProp(
    val name: String,
    val assetPath: String,
    val x: Double,
    val y: Double,
    val zIndex: Int,
    val scaleX: Double,
    val scaleY: Double,
    val scrollX: Double,
    val scrollY: Double,
    val alpha: Double,
    val angle: Double,
    val color: String,
    val isPixel: Boolean,
    val flipX: Boolean,
    val flipY: Boolean,
    val animType: String,
    val animations: List<VAnimation>,
    val startingAnimation: String,
    val blend: String = "",
    val danceEvery: Double = 0.0
)

data class VStageCharacter(
    val x: Double,
    val y: Double,
    val zIndex: Int,
    val cameraX: Double,
    val cameraY: Double,
    val scroll: Double,
    val scrollY: Double = scroll,
    val scale: Double = 1.0,
    val alpha: Double = 1.0,
    val angle: Double = 0.0
)

data class VStage(
    val name: String,
    val cameraZoom: Double,
    val directory: String,
    val props: List<VProp>,
    val boyfriend: VStageCharacter,
    val dad: VStageCharacter,
    val girlfriend: VStageCharacter
)

data class VLevelProp(val assetPath: String, val scale: Double, val offsetX: Double, val offsetY: Double, val isPixel: Boolean, val flipX: Boolean, val animations: List<VAnimation>)

data class VLevel(
    val name: String,
    val titleAsset: String,
    val songs: List<String>,
    val background: String,
    val visible: Boolean,
    val props: List<VLevelProp>
)

object VSliceParser {
    private fun json(raw: String, what: String): JSONObject = try {
        JSONObject(raw.trim().removePrefix("﻿"))
    } catch (e: JSONException) {
        throw VSliceFormatException("The $what is not valid JSON: ${e.message}")
    }

    private fun strings(raw: Any?): List<String> {
        val array = raw as? JSONArray ?: return emptyList()
        return (0 until array.length()).map { array.value(it).asText() }.filter { it.isNotEmpty() }
    }

    private fun numbers(raw: Any?): List<Double> {
        val array = raw as? JSONArray ?: return emptyList()
        return (0 until array.length()).map { array.value(it).asDouble(0.0) }
    }

    private fun objects(raw: Any?): List<JSONObject> {
        val array = raw as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.value(it) as? JSONObject }
    }

    private fun pair(raw: Any?, fx: Double, fy: Double): Pair<Double, Double> = when (raw) {
        is JSONArray -> raw.value(0).asDouble(fx) to raw.value(1).asDouble(raw.value(0).asDouble(fy))
        is Number -> raw.toDouble() to raw.toDouble()
        else -> fx to fy
    }

    fun isMetadata(raw: String): Boolean = runCatching { json(raw, "file").has("playData") }.getOrDefault(false)

    fun isChart(raw: String): Boolean = runCatching { json(raw, "file").let { it.has("notes") && it.has("scrollSpeed") } }.getOrDefault(false)

    fun metadata(raw: String): VMetadata {
        val root = json(raw, "metadata")
        val play = root.value("playData") as? JSONObject ?: throw VSliceFormatException("This is not a V-Slice song metadata file (no \"playData\").")
        val characters = play.value("characters") as? JSONObject ?: JSONObject()
        val changes = objects(root.value("timeChanges")).map {
            VTimeChange(it.value("t").asDouble(0.0), it.value("bpm").asDouble(100.0), it.value("n").asDouble(4.0).toInt().coerceAtLeast(1))
        }.sortedBy { it.time }.ifEmpty { listOf(VTimeChange(0.0, 100.0, 4)) }
        val offsets = root.value("offsets") as? JSONObject
        return VMetadata(
            songName = root.value("songName").asText(),
            artist = root.value("artist").asText(),
            charter = root.value("charter").asText(),
            difficulties = strings(play.value("difficulties")),
            variations = strings(play.value("songVariations")),
            player = characters.value("player").asText().ifEmpty { "bf" },
            opponent = characters.value("opponent").asText().ifEmpty { "dad" },
            girlfriend = characters.value("girlfriend").asText(),
            playerVocals = (characters.value("playerVocals") as? JSONArray)?.let(::strings),
            opponentVocals = (characters.value("opponentVocals") as? JSONArray)?.let(::strings),
            stage = play.value("stage").asText().ifEmpty { "mainStage" },
            noteStyle = play.value("noteStyle").asText().ifEmpty { "funkin" },
            timeChanges = changes,
            offsetInstrumental = offsets?.value("instrumental").asDouble(0.0)
        )
    }

    fun chart(raw: String): VChart {
        val root = json(raw, "chart")
        val speeds = root.value("scrollSpeed") as? JSONObject ?: throw VSliceFormatException("This is not a V-Slice chart file (no \"scrollSpeed\").")
        val notesRoot = root.value("notes") as? JSONObject ?: JSONObject()
        val notes = notesRoot.keys().asSequence().associateWith { difficulty ->
            objects(notesRoot.value(difficulty)).map {
                VNote(it.value("t").asDouble(0.0), it.value("d").asDouble(0.0).toInt(), it.value("l").asDouble(0.0), it.value("k").asText())
            }.sortedBy { it.time }
        }
        val events = objects(root.value("events")).map { VEvent(it.value("t").asDouble(0.0), it.value("e").asText(), it.value("v")) }.sortedBy { it.time }
        return VChart(speeds.keys().asSequence().associateWith { speeds.value(it).asDouble(1.0) }, events, notes)
    }

    private fun animation(obj: JSONObject): VAnimation {
        val (ox, oy) = pair(obj.value("offsets"), 0.0, 0.0)
        return VAnimation(
            name = obj.value("name").asText(),
            prefix = obj.value("prefix").asText(),
            assetPath = obj.value("assetPath").asText(),
            frameRate = obj.value("frameRate").asDouble(24.0).toInt(),
            looped = obj.value("looped").asBool(false),
            indices = numbers(obj.value("frameIndices")).map { it.toInt() },
            offsetX = ox,
            offsetY = oy,
            flipX = obj.value("flipX").asBool(false),
            animType = obj.value("animType").asText()
        )
    }

    fun character(raw: String): VCharacter {
        val root = json(raw, "character")
        if (!root.has("animations") || !root.has("assetPath") && !root.has("renderType")) {
            throw VSliceFormatException("This JSON does not look like a V-Slice character (no \"animations\"/\"assetPath\").")
        }
        val icon = root.value("healthIcon") as? JSONObject
        val (ox, oy) = pair(root.value("offsets"), 0.0, 0.0)
        val (cx, cy) = pair(root.value("cameraOffsets"), 0.0, 0.0)
        return VCharacter(
            name = root.value("name").asText(),
            renderType = root.value("renderType").asText().ifEmpty { "sparrow" },
            assetPath = root.value("assetPath").asText(),
            scale = root.value("scale").asDouble(1.0),
            isPixel = root.value("isPixel").asBool(false),
            healthIcon = icon?.value("id").asText() ?: "",
            healthIconPixel = icon?.value("isPixel").asBool(false) ?: false,
            offsetX = ox,
            offsetY = oy,
            cameraX = cx,
            cameraY = cy,
            singTime = root.value("singTime").asDouble(8.0),
            danceEvery = root.value("danceEvery").asDouble(1.0),
            flipX = root.value("flipX").asBool(false),
            startingAnimation = root.value("startingAnimation").asText(),
            animations = objects(root.value("animations")).map(::animation)
        )
    }

    private fun stageCharacter(raw: Any?, fx: Double, fy: Double, fz: Int, fcx: Double, fcy: Double): VStageCharacter {
        val obj = raw as? JSONObject ?: return VStageCharacter(fx, fy, fz, fcx, fcy, 1.0)
        val (x, y) = pair(obj.value("position"), fx, fy)
        val (cx, cy) = pair(obj.value("cameraOffsets"), fcx, fcy)
        val (sx, sy) = pair(obj.value("scroll"), 1.0, 1.0)
        return VStageCharacter(
            x, y, obj.value("zIndex").asDouble(fz.toDouble()).toInt(), cx, cy, sx, sy,
            scale = obj.value("scale").asDouble(1.0),
            alpha = obj.value("alpha").asDouble(1.0),
            angle = obj.value("angle").asDouble(0.0)
        )
    }

    fun stage(raw: String): VStage {
        val root = json(raw, "stage")
        val chars = root.value("characters") as? JSONObject ?: throw VSliceFormatException("This JSON does not look like a V-Slice stage (no \"characters\").")
        val props = objects(root.value("props")).mapIndexed { index, obj ->
            val (x, y) = pair(obj.value("position"), 0.0, 0.0)
            val (sx, sy) = pair(obj.value("scale"), 1.0, 1.0)
            val (rx, ry) = pair(obj.value("scroll"), 1.0, 1.0)
            VProp(
                name = obj.value("name").asText().ifEmpty { "prop$index" },
                assetPath = obj.value("assetPath").asText(),
                x = x,
                y = y,
                zIndex = obj.value("zIndex").asDouble(0.0).toInt(),
                scaleX = sx,
                scaleY = sy,
                scrollX = rx,
                scrollY = ry,
                alpha = obj.value("alpha").asDouble(1.0),
                angle = obj.value("angle").asDouble(0.0),
                color = obj.value("color").asText(),
                isPixel = obj.value("isPixel").asBool(false),
                flipX = obj.value("flipX").asBool(false),
                flipY = obj.value("flipY").asBool(false),
                animType = obj.value("animType").asText().ifEmpty { "sparrow" },
                animations = objects(obj.value("animations")).map(::animation),
                startingAnimation = obj.value("startingAnimation").asText(),
                blend = obj.value("blend").asText(),
                danceEvery = obj.value("danceEvery").asDouble(0.0)
            )
        }
        return VStage(
            name = root.value("name").asText(),
            cameraZoom = root.value("cameraZoom").asDouble(1.0),
            directory = root.value("directory").asText(),
            props = props,
            boyfriend = stageCharacter(chars.value("bf"), 989.5, 885.0, 300, -100.0, -100.0),
            dad = stageCharacter(chars.value("dad"), 335.0, 885.0, 200, 150.0, -100.0),
            girlfriend = stageCharacter(chars.value("gf"), 751.5, 787.0, 100, 0.0, 0.0)
        )
    }

    fun level(raw: String): VLevel {
        val root = json(raw, "level")
        if (!root.has("songs")) throw VSliceFormatException("This JSON does not look like a V-Slice level (no \"songs\").")
        return VLevel(
            name = root.value("name").asText(),
            titleAsset = root.value("titleAsset").asText(),
            songs = strings(root.value("songs")),
            background = root.value("background").asText().ifEmpty { "#F9CF51" },
            visible = root.value("visible").asBool(true),
            props = objects(root.value("props")).map { obj ->
                val (ox, oy) = pair(obj.value("offsets"), 0.0, 0.0)
                VLevelProp(
                    assetPath = obj.value("assetPath").asText(),
                    scale = obj.value("scale").asDouble(1.0),
                    offsetX = ox,
                    offsetY = oy,
                    isPixel = obj.value("isPixel").asBool(false),
                    flipX = obj.value("flipX").asBool(false),
                    animations = objects(obj.value("animations")).map(::animation)
                )
            }
        )
    }
}
