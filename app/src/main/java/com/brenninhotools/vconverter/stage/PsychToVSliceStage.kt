package com.brenninhotools.vconverter.stage

import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.SparrowFrame
import com.brenninhotools.vconverter.core.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class StageSource(
    val id: String,
    val stage: PsychStage,
    val displayName: String = "",
    val atlas: (String) -> SparrowAtlas? = { null }
)

data class StageResult(val id: String, val json: String, val report: Report)

object PsychToVSliceStage {
    const val STAGE_VERSION = "1.0.0"
    private const val GIRLFRIEND_SCROLL = 0.95

    val libraries = setOf("shared", "week1", "week2", "week3", "week4", "week5", "week6", "week7", "weekend1", "tutorial")

    val baseStages = mapOf(
        "stage" to "mainStage",
        "spooky" to "spookyMansion",
        "philly" to "phillyTrain",
        "limo" to "limoRide",
        "mall" to "mallXmas",
        "mallevil" to "mallEvil",
        "school" to "school",
        "schoolevil" to "schoolEvil",
        "tank" to "tankmanBattlefield",
        "phillystreets" to "phillyStreets",
        "phillyblazin" to "phillyBlazin"
    )

    private val namedColors = mapOf(
        "white" to "FFFFFF", "black" to "000000", "gray" to "808080", "grey" to "808080",
        "red" to "FF0000", "green" to "008000", "lime" to "00FF00", "blue" to "0000FF",
        "yellow" to "FFFF00", "cyan" to "00FFFF", "magenta" to "FF00FF", "pink" to "FFC0CB",
        "purple" to "800080", "orange" to "FFA500", "brown" to "8B4513", "transparent" to "000000"
    )

    private val blends = setOf("add", "alpha", "darken", "difference", "erase", "hardlight", "invert", "layer", "lighten", "multiply", "normal", "overlay", "screen", "shader", "subtract")

    private class Notes {
        val lines = mutableListOf<String>()
        var exactOffsets = 0
        var approximateOffsets = 0
        var renamedBops = 0
    }

    fun library(directory: String): String {
        val clean = directory.trim().trim('/').lowercase()
        return if (clean in libraries) clean else "shared"
    }

    fun convert(source: StageSource): StageResult {
        val psych = source.stage
        val report = ReportBuilder()
        val notes = Notes()
        val props = JSONArray()
        val skipped = mutableListOf<String>()
        var converted = 0
        val usedNames = HashSet<String>()

        val objects = psych.objects
        val markerZ = HashMap<CharacterSlot, Int>()
        var top = 0
        if (objects == null || objects.isEmpty()) {
            if (objects == null) {
                val base = baseStages[source.id.lowercase()]
                report.warn(
                    "This stage has no \"objects\" list, so its sprites are built in code (a hardcoded Psych stage or stages/${source.id}.lua/.hx). Character positions, cameras and zoom were converted; the props must be recreated in V-Slice." +
                        if (base != null) " V-Slice already has this stage as \"$base\"." else ""
                )
                if (psych.preload.isNotEmpty()) report.section("Images the stage preloads", psych.preload.map { it.removePrefix("images/") })
            }
        } else {
            objects.forEachIndexed { index, obj ->
                val z = (index + 1) * 10
                top = z
                when (obj.type) {
                    "gf", "gfGroup" -> markerZ.putIfAbsent(CharacterSlot.GIRLFRIEND, z)
                    "dad", "dadGroup" -> markerZ.putIfAbsent(CharacterSlot.OPPONENT, z)
                    "boyfriend", "boyfriendGroup" -> markerZ.putIfAbsent(CharacterSlot.PLAYER, z)
                    "square", "sprite", "animatedSprite" -> {
                        val label = uniqueName(obj.name.ifBlank { "object$index" }, usedNames)
                        when {
                            obj.filters and (PsychStageParser.FILTER_LOW_QUALITY or PsychStageParser.FILTER_HIGH_QUALITY) == 0 -> skipped += "$label (hidden in every quality setting)"
                            obj.filters and PsychStageParser.FILTER_HIGH_QUALITY == 0 -> skipped += "$label (low quality only)"
                            else -> {
                                if (obj.filters and (PsychStageParser.FILTER_STORY_MODE or PsychStageParser.FILTER_FREEPLAY) != 0) {
                                    report.warn("\"$label\" only shows in story mode or freeplay in Psych; V-Slice always shows it.")
                                }
                                props.put(prop(obj, label, z, source, report, notes))
                                converted++
                            }
                        }
                    }
                    else -> skipped += "${obj.name.ifBlank { "object$index" }} (unknown type \"${obj.type}\")"
                }
            }
        }
        val missing = listOf(CharacterSlot.GIRLFRIEND, CharacterSlot.OPPONENT, CharacterSlot.PLAYER).filter { it !in markerZ }
        missing.forEachIndexed { i, slot -> markerZ[slot] = top + 10 * (i + 1) }
        if (objects != null && objects.isNotEmpty() && missing.size == 3) {
            notes.lines += "The objects list has no character layers, so the characters were placed above every prop (Psych would not show them at all)."
        } else if (objects != null && objects.isNotEmpty() && missing.isNotEmpty()) {
            report.warn("The objects list has no layer for ${missing.joinToString { it.label.lowercase() }}; Psych would not show ${if (missing.size == 1) "that character" else "those characters"}, V-Slice puts ${if (missing.size == 1) "it" else "them"} on top.")
        }

        val characters = JSONObject()
            .put("bf", character(CharacterSlot.PLAYER, psych.boyfriend, psych.cameraBoyfriend, markerZ.getValue(CharacterSlot.PLAYER), 1.0, 1.0))
            .put("dad", character(CharacterSlot.OPPONENT, psych.opponent, psych.cameraOpponent, markerZ.getValue(CharacterSlot.OPPONENT), 1.0, 1.0))
            .put("gf", character(CharacterSlot.GIRLFRIEND, psych.girlfriend, psych.cameraGirlfriend, markerZ.getValue(CharacterSlot.GIRLFRIEND), GIRLFRIEND_SCROLL, if (psych.hideGirlfriend) 0.0 else 1.0))

        val directory = library(psych.directory)
        val root = JSONObject()
            .put("version", STAGE_VERSION)
            .put("name", source.displayName.ifBlank { PsychToVSliceCharacter.prettyName(source.id) })
            .put("cameraZoom", psych.defaultZoom.toJsonNumber())
            .put("directory", directory)
            .put("props", props)
            .put("characters", characters)

        report.stat("Props", converted)
        report.stat("Zoom", psych.defaultZoom.toJsonNumber())
        report.stat("Pixel", if (psych.isPixelStage) "Yes" else "No")

        if (skipped.isNotEmpty()) report.section("Skipped objects", skipped)
        if (psych.directory.isNotBlank() && directory != psych.directory.trim().lowercase()) {
            notes.lines += "The folder \"${psych.directory}\" is not a V-Slice asset library (V-Slice stops with \"Missing library\" on unknown ones), so the stage uses \"shared\". Images in the mod's images/ folder load from there."
        }
        if (psych.hideGirlfriend) notes.lines += "hide_girlfriend became an invisible girlfriend (alpha 0); you can also set the song's girlfriend to none in its metadata."
        if (psych.cameraSpeed != 1.0) report.warn("Camera speed ${psych.cameraSpeed} has no V-Slice stage setting; use a FocusCamera event with a duration, or a stage script, to change it.")
        if (psych.isPixelStage) notes.lines += "Pixel stage: give the songs that use it the \"pixel\" note style in their metadata."
        if (notes.exactOffsets > 0) notes.lines += "${notes.exactOffsets} animated prop(s) are scaled; their animation offsets were recalculated from the spritesheet frames so they draw exactly where Psych draws them."
        if (notes.approximateOffsets > 0) report.warn("${notes.approximateOffsets} scaled animated prop(s) have animation offsets but no spritesheet XML was given; offsets were divided by the scale and may be a few pixels off. Add the images' .xml files for an exact result.")
        if (notes.renamedBops > 0) notes.lines += "${notes.renamedBops} beat-bopping prop(s) had their bop animation renamed to \"idle\", the name V-Slice props dance with."
        notes.lines += "Character positions use V-Slice feet anchors. Characters converted with V-Converter land on the exact Psych spot; native V-Slice characters land close to it."
        notes.lines += "Layers keep Psych's order through zIndex (props and characters every 10)."
        report.section("Notes", notes.lines)

        return StageResult(source.id, root.toString(2), report.build())
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        var candidate = name
        var n = 2
        while (!used.add(candidate)) candidate = "$name$n".also { n++ }
        return candidate
    }

    private fun character(slot: CharacterSlot, position: PsychPoint, camera: PsychPoint, z: Int, scroll: Double, alpha: Double): JSONObject {
        val entry = JSONObject()
            .put("zIndex", z)
            .put("position", JSONArray(listOf((position.x + slot.stageAnchorX).toJsonNumber(), (position.y + slot.stageAnchorY).toJsonNumber())))
            .put("cameraOffsets", JSONArray(listOf((camera.x + slot.cameraBaseX).toJsonNumber(), (camera.y + slot.cameraBaseY).toJsonNumber())))
        if (scroll != 1.0) entry.put("scroll", JSONArray(listOf(scroll, scroll)))
        if (alpha != 1.0) entry.put("alpha", alpha.toJsonNumber())
        return entry
    }

    private fun prop(obj: PsychStageObject, label: String, z: Int, source: StageSource, report: ReportBuilder, notes: Notes): JSONObject {
        val entry = JSONObject()
            .put("name", label)
            .put("zIndex", z)
            .put("position", JSONArray(listOf(obj.x.toJsonNumber(), obj.y.toJsonNumber())))

        if (obj.type == "square") {
            entry.put("assetPath", "#" + hexColor(obj.color, label, report))
            entry.put("scale", JSONArray(listOf(obj.scale.x.roundToInt().coerceAtLeast(1), obj.scale.y.roundToInt().coerceAtLeast(1))))
        } else {
            val image = obj.image.trim().replace('\\', '/').removePrefix("images/").removeSuffix(".png")
            entry.put("assetPath", image)
            entry.put("scale", JSONArray(listOf(obj.scale.x.toJsonNumber(), obj.scale.y.toJsonNumber())))
            val color = hexColor(obj.color, label, report)
            if (color != "FFFFFF") entry.put("color", "#$color")
            if (!obj.antialiasing) entry.put("isPixel", true)
            if (obj.flipX) entry.put("flipX", true)
            if (obj.flipY) entry.put("flipY", true)
        }

        if (obj.scroll.x != 1.0 || obj.scroll.y != 1.0) entry.put("scroll", JSONArray(listOf(obj.scroll.x.toJsonNumber(), obj.scroll.y.toJsonNumber())))
        if (obj.alpha != 1.0) entry.put("alpha", obj.alpha.coerceIn(0.0, 1.0).toJsonNumber())
        val blend = obj.blend.trim().lowercase()
        if (blend.isNotEmpty() && blend != "normal") {
            if (blend in blends) entry.put("blend", blend) else report.warn("\"$label\" uses the blend mode \"${obj.blend}\", which V-Slice does not know; it draws normally.")
        }
        if (obj.angle != 0.0) entry.put("angle", obj.angle.toJsonNumber())

        if (obj.type == "animatedSprite") animate(obj, label, entry, source, report, notes)
        return entry
    }

    private fun animate(obj: PsychStageObject, label: String, entry: JSONObject, source: StageSource, report: ReportBuilder, notes: Notes) {
        val atlas = source.atlas(entry.getString("assetPath"))
        var animations = obj.animations
        if (animations.isEmpty()) {
            val first = atlas?.firstFrame
            if (first == null) {
                report.warn("\"$label\" is an animated sprite without animations; add its spritesheet .xml so the first frame can be shown. It was kept as a plain image for now.")
                return
            }
            val prefix = first.name.trimEnd { it.isDigit() }
            animations = listOf(PsychStageAnimation("idle", prefix, 24, false, listOf(0), 0.0, 0.0))
            notes.lines += "\"$label\" had no animations; it now shows the first frame of its spritesheet like Psych does."
        }

        var start = obj.firstAnimation.takeIf { name -> animations.any { it.anim == name } } ?: animations.first().anim
        val bops = obj.danceEvery > 0
        if (bops && animations.none { it.anim == "idle" || it.anim == "danceLeft" }) {
            val renamed = start
            animations = animations.map { if (it.anim == renamed) it.copy(anim = "idle") else it }
            start = "idle"
            notes.renamedBops++
        }

        val sx = obj.scale.x
        val sy = obj.scale.y
        val scaled = sx != 1.0 || sy != 1.0
        val hasOffsets = animations.any { it.offsetX != 0.0 || it.offsetY != 0.0 }
        val first: SparrowFrame? = atlas?.firstFrame
        val startAnim = animations.first { it.anim == start }
        val startFrame: SparrowFrame? = atlas?.firstFrameOf(startAnim.prefix, startAnim.indices) ?: first
        val exact = obj.rawOffsets && scaled && first != null && startFrame != null
        if (obj.rawOffsets && scaled && (hasOffsets || startFrame != null && first != null && startFrame.sourceWidth != first.sourceWidth)) {
            if (exact) notes.exactOffsets++ else if (hasOffsets) notes.approximateOffsets++
        }
        val hitboxX = if (exact) 0.5 * first!!.sourceWidth * (1 - sx) else 0.0
        val hitboxY = if (exact) 0.5 * first!!.sourceHeight * (1 - sy) else 0.0
        val startHitboxX = if (exact) 0.5 * startFrame!!.sourceWidth * (1 - sx) else 0.0
        val startHitboxY = if (exact) 0.5 * startFrame!!.sourceHeight * (1 - sy) else 0.0
        val startShowsHitbox = obj.rawOffsets && scaled && !bops && !obj.startReplayed

        if (atlas != null) {
            val missingPrefixes = animations.filter { !atlas.hasPrefix(it.prefix) }.map { "${it.anim} (\"${it.prefix}\")" }
            if (missingPrefixes.isNotEmpty()) report.warn("\"$label\": the spritesheet has no frames for ${missingPrefixes.joinToString()}.")
        }

        val list = JSONArray()
        animations.forEach { anim ->
            val (ox, oy) = when {
                !obj.rawOffsets -> anim.offsetX / sx to anim.offsetY / sy
                anim.anim == start && startShowsHitbox -> (startHitboxX - hitboxX) / sx to (startHitboxY - hitboxY) / sy
                else -> (anim.offsetX - hitboxX) / sx to (anim.offsetY - hitboxY) / sy
            }
            val animEntry = JSONObject()
                .put("name", anim.anim)
                .put("frameRate", anim.fps)
                .put("looped", anim.loop)
            if (anim.prefix.isNotEmpty() || anim.indices.isEmpty()) animEntry.put("prefix", anim.prefix)
            if (anim.indices.isNotEmpty()) animEntry.put("frameIndices", JSONArray(anim.indices))
            if (round(ox) != 0.0 || round(oy) != 0.0) animEntry.put("offsets", JSONArray(listOf(round(ox).toJsonNumber(), round(oy).toJsonNumber())))
            list.put(animEntry)
        }
        entry.put("animType", if (animations.all { it.prefix.isEmpty() && it.indices.isNotEmpty() }) "packer" else "sparrow")
        entry.put("danceEvery", obj.danceEvery.toJsonNumber())
        entry.put("animations", list)
        if (!obj.paused) entry.put("startingAnimation", start)
    }

    private fun round(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private fun hexColor(raw: String, label: String, report: ReportBuilder): String {
        val text = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        val hex = Regex("^[0-9a-fA-F]+$")
        return when {
            text.isEmpty() -> "FFFFFF"
            text.length == 6 && hex.matches(text) -> text.uppercase()
            text.length == 8 && hex.matches(text) -> text.substring(2).uppercase()
            text.length == 3 && hex.matches(text) -> text.map { "$it$it" }.joinToString("").uppercase()
            namedColors.containsKey(text.lowercase()) -> namedColors.getValue(text.lowercase())
            else -> {
                report.warn("\"$label\" uses the color \"$raw\", which could not be read; white was used instead.")
                "FFFFFF"
            }
        }
    }
}
