package com.brenninhotools.vconverter.stage

import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class StageSource(val id: String, val stage: PsychStage, val displayName: String = "")

data class StageResult(val id: String, val json: String, val report: Report)

object PsychToVSliceStage {
    const val STAGE_VERSION = "1.0.0"
    private const val GIRLFRIEND_SCROLL = 0.95

    private val namedColors = mapOf(
        "white" to "FFFFFF", "black" to "000000", "gray" to "808080", "grey" to "808080",
        "red" to "FF0000", "green" to "008000", "lime" to "00FF00", "blue" to "0000FF",
        "yellow" to "FFFF00", "cyan" to "00FFFF", "magenta" to "FF00FF", "pink" to "FFC0CB",
        "purple" to "800080", "orange" to "FFA500", "brown" to "8B4513"
    )

    fun convert(source: StageSource): StageResult {
        val psych = source.stage
        val report = ReportBuilder()
        val props = JSONArray()
        val zIndex = mutableMapOf(
            CharacterSlot.GIRLFRIEND to 100,
            CharacterSlot.OPPONENT to 200,
            CharacterSlot.PLAYER to 300
        )
        val skipped = mutableListOf<String>()
        var converted = 0

        val objects = psych.objects
        if (objects == null) {
            report.warn("This stage has no \"objects\" list, so its sprites are built in a script (stages/${source.id}.lua or .hx). Only character positions, camera and zoom were converted; recreate the props in V-Slice.")
            if (psych.preload.isNotEmpty()) {
                report.section("Images the stage preloads", psych.preload.map { it.removePrefix("images/") })
            }
        } else {
            objects.forEachIndexed { index, obj ->
                val z = (index + 1) * 10
                when (obj.type) {
                    "gf", "gfGroup" -> zIndex[CharacterSlot.GIRLFRIEND] = z
                    "dad", "dadGroup" -> zIndex[CharacterSlot.OPPONENT] = z
                    "boyfriend", "boyfriendGroup" -> zIndex[CharacterSlot.PLAYER] = z
                    "square", "sprite", "animatedSprite" -> {
                        val label = obj.name.ifBlank { "object $index" }
                        if ((obj.filters and PsychStageParser.FILTER_HIGH_QUALITY) == 0) {
                            skipped += "$label (low quality only)"
                        } else {
                            if ((obj.filters and (PsychStageParser.FILTER_STORY_MODE or PsychStageParser.FILTER_FREEPLAY)) != 0) {
                                report.warn("\"$label\" is limited to story mode or freeplay in Psych; V-Slice always shows it.")
                            }
                            props.put(prop(obj, label, z, report))
                            converted++
                        }
                    }
                    else -> skipped += "${obj.name.ifBlank { "object $index" }} (unknown type \"${obj.type}\")"
                }
            }
        }

        val characters = JSONObject()
            .put("bf", character(CharacterSlot.PLAYER, psych.boyfriend, psych.cameraBoyfriend, zIndex.getValue(CharacterSlot.PLAYER), 1.0))
            .put("dad", character(CharacterSlot.OPPONENT, psych.opponent, psych.cameraOpponent, zIndex.getValue(CharacterSlot.OPPONENT), 1.0))
            .put("gf", character(CharacterSlot.GIRLFRIEND, psych.girlfriend, psych.cameraGirlfriend, zIndex.getValue(CharacterSlot.GIRLFRIEND), GIRLFRIEND_SCROLL))

        val root = JSONObject()
            .put("version", STAGE_VERSION)
            .put("name", source.displayName.ifBlank { PsychToVSliceCharacter.prettyName(source.id) })
            .put("cameraZoom", psych.defaultZoom.toJsonNumber())
            .put("directory", psych.directory.ifBlank { "shared" })
            .put("props", props)
            .put("characters", characters)

        report.stat("Props", converted)
        report.stat("Zoom", psych.defaultZoom.toJsonNumber())
        report.stat("Pixel", if (psych.isPixelStage) "Yes" else "No")

        if (skipped.isNotEmpty()) report.section("Skipped objects", skipped)
        if (psych.hideGirlfriend) {
            report.warn("Psych hides Girlfriend on this stage. In V-Slice, set the song's girlfriend character to none in its metadata.")
        }
        if (psych.cameraSpeed != 1.0) {
            report.warn("Camera speed ${psych.cameraSpeed} has no V-Slice stage equivalent and was ignored.")
        }
        if (psych.isPixelStage) {
            report.warn("This is a pixel stage. Use the \"pixel\" note style in the songs that use it.")
        }
        report.section(
            "Notes",
            listOf("Character positions were rebased to V-Slice feet anchors. Characters converted with V-Converter line up exactly; native V-Slice characters land close to their Psych spot.")
        )

        return StageResult(source.id, root.toString(2), report.build())
    }

    private fun character(slot: CharacterSlot, position: PsychPoint, camera: PsychPoint, z: Int, scroll: Double): JSONObject {
        val entry = JSONObject()
            .put("zIndex", z)
            .put("position", JSONArray(listOf((position.x + slot.stageAnchorX).toJsonNumber(), (position.y + slot.stageAnchorY).toJsonNumber())))
            .put("cameraOffsets", JSONArray(listOf((camera.x + slot.cameraBaseX).toJsonNumber(), (camera.y + slot.cameraBaseY).toJsonNumber())))
        if (scroll != 1.0) entry.put("scroll", JSONArray(listOf(scroll, scroll)))
        return entry
    }

    private fun prop(obj: PsychStageObject, label: String, z: Int, report: ReportBuilder): JSONObject {
        val entry = JSONObject()
            .put("name", label)
            .put("zIndex", z)
            .put("position", JSONArray(listOf(obj.x.toJsonNumber(), obj.y.toJsonNumber())))

        if (obj.type == "square") {
            entry.put("assetPath", "#" + hexColor(obj.color, label, report))
            entry.put("scale", JSONArray(listOf(obj.scale.x.roundToInt().coerceAtLeast(1), obj.scale.y.roundToInt().coerceAtLeast(1))))
        } else {
            entry.put("assetPath", obj.image)
            entry.put("scale", JSONArray(listOf(obj.scale.x.toJsonNumber(), obj.scale.y.toJsonNumber())))
            val color = hexColor(obj.color, label, report)
            if (color != "FFFFFF") entry.put("color", "#$color")
            if (!obj.antialiasing) entry.put("isPixel", true)
            if (obj.flipX) entry.put("flipX", true)
            if (obj.flipY) entry.put("flipY", true)
        }

        if (obj.scroll.x != 1.0 || obj.scroll.y != 1.0) {
            entry.put("scroll", JSONArray(listOf(obj.scroll.x.toJsonNumber(), obj.scroll.y.toJsonNumber())))
        }
        if (obj.alpha != 1.0) entry.put("alpha", obj.alpha.toJsonNumber())
        if (obj.angle != 0.0) entry.put("angle", obj.angle.toJsonNumber())

        if (obj.type == "animatedSprite" && obj.animations.isNotEmpty()) {
            val scaled = obj.scale.x != 1.0 || obj.scale.y != 1.0
            if (scaled && obj.animations.any { it.offsetX != 0.0 || it.offsetY != 0.0 }) {
                report.warn("\"$label\" is scaled and has animation offsets; they were divided by the scale and may need a small manual tweak.")
            }
            val animations = JSONArray()
            obj.animations.forEach { anim ->
                val animEntry = JSONObject()
                    .put("name", anim.anim)
                    .put("prefix", anim.prefix)
                    .put("frameRate", anim.fps)
                    .put("looped", anim.loop)
                if (anim.indices.isNotEmpty()) animEntry.put("frameIndices", JSONArray(anim.indices))
                if (anim.offsetX != 0.0 || anim.offsetY != 0.0) {
                    animEntry.put("offsets", JSONArray(listOf((anim.offsetX / obj.scale.x).toJsonNumber(), (anim.offsetY / obj.scale.y).toJsonNumber())))
                }
                animations.put(animEntry)
            }
            entry.put("animType", "sparrow")
            entry.put("danceEvery", 0)
            entry.put("animations", animations)
            entry.put("startingAnimation", obj.firstAnimation.ifBlank { obj.animations.first().anim })
        }
        return entry
    }

    private fun hexColor(raw: String, label: String, report: ReportBuilder): String {
        val text = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        val hex = Regex("^[0-9a-fA-F]+$")
        return when {
            text.length == 6 && hex.matches(text) -> text.uppercase()
            text.length == 8 && hex.matches(text) -> text.substring(2).uppercase()
            namedColors.containsKey(text.lowercase()) -> namedColors.getValue(text.lowercase())
            else -> {
                report.warn("\"$label\" uses the color \"$raw\", which could not be read; white was used instead.")
                "FFFFFF"
            }
        }
    }
}
