package com.brenninhotools.vconverter.reverse

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ReverseResult(
    val path: String,
    val json: String,
    val report: Report,
    val extra: List<Pair<String, String>> = emptyList(),
    val copies: List<Pair<String, String>> = emptyList()
)

object VSliceToPsychCharacter {
    private val defaultColors = mapOf(
        CharacterSlot.PLAYER to listOf(49, 176, 209),
        CharacterSlot.OPPONENT to listOf(175, 102, 206),
        CharacterSlot.GIRLFRIEND to listOf(165, 0, 77)
    )

    fun defaultSlot(id: String): CharacterSlot = when {
        id.lowercase().startsWith("gf") -> CharacterSlot.GIRLFRIEND
        id.lowercase().startsWith("bf") || id.lowercase().startsWith("pico-playable") -> CharacterSlot.PLAYER
        else -> CharacterSlot.OPPONENT
    }

    fun atlasKey(path: String): String = path.trim().replace('\\', '/').substringAfterLast('/').substringBeforeLast(".png").lowercase()

    fun convert(id: String, character: VCharacter, slot: CharacterSlot, atlases: Map<String, SparrowAtlas>): ReverseResult {
        val report = ReportBuilder()
        val s = character.scale
        val images = (listOf(character.assetPath) + character.animations.map { it.assetPath }).filter { it.isNotBlank() }.distinct()
        val main = images.firstOrNull() ?: "characters/$id"
        val idle = character.animations.firstOrNull { it.name == "danceLeft" }
            ?: character.animations.firstOrNull { it.name == character.startingAnimation }
            ?: character.animations.firstOrNull { it.name == "idle" }
            ?: character.animations.firstOrNull()
        val firstFrame = atlases[atlasKey(main)]?.firstFrame
        val idleFrame = idle?.let { anim -> images.firstNotNullOfOrNull { atlases[atlasKey(it)]?.firstFrameOf(anim.prefix, anim.indices) } }
        val exact = firstFrame != null && idleFrame != null
        val fallbackWidth = slot.stageAnchorX * 2.0
        val fallbackHeight = slot.stageAnchorY * 2.0 / (1.0 + s)
        val fw0 = firstFrame?.sourceWidth ?: idleFrame?.sourceWidth ?: fallbackWidth
        val fh0 = firstFrame?.sourceHeight ?: idleFrame?.sourceHeight ?: fallbackHeight
        val fwi = idleFrame?.sourceWidth ?: fw0
        val fhi = idleFrame?.sourceHeight ?: fh0
        val idleX = idle?.offsetX ?: 0.0
        val idleY = idle?.offsetY ?: 0.0

        val posX = slot.stageAnchorX - s * fwi / 2.0 - (1.0 - s) * fw0 / 2.0 - s * idleX + character.offsetX
        val posY = slot.stageAnchorY - s * fhi - (1.0 - s) * fh0 / 2.0 - s * idleY + character.offsetY
        val camX = slot.cameraSign * (character.cameraX - posX + slot.stageAnchorX - s * fw0 / 2.0)
        val camY = character.cameraY - posY + slot.stageAnchorY - s * fh0 / 2.0 - s * fhi / 2.0

        val animations = JSONArray()
        character.animations.forEach { anim ->
            animations.put(
                JSONObject()
                    .put("offsets", JSONArray(listOf(((anim.offsetX - idleX) * s).toJsonNumber(), ((anim.offsetY - idleY) * s).toJsonNumber())))
                    .put("loop", anim.looped)
                    .put("fps", anim.frameRate)
                    .put("anim", anim.name)
                    .put("indices", JSONArray(anim.indices))
                    .put("name", anim.prefix)
            )
        }
        val root = JSONObject()
            .put("animations", animations)
            .put("image", images.joinToString(","))
            .put("position", JSONArray(listOf(posX.toJsonNumber(), posY.toJsonNumber())))
            .put("healthicon", character.healthIcon.ifEmpty { id })
            .put("flip_x", character.flipX)
            .put("healthbar_colors", JSONArray(defaultColors.getValue(slot)))
            .put("camera_position", JSONArray(listOf(camX.toJsonNumber(), camY.toJsonNumber())))
            .put("sing_duration", (character.singTime / 1.1).toJsonNumber())
            .put("scale", s.toJsonNumber())
            .put("no_antialiasing", character.isPixel)
            .put("vocals_file", "")
            .put("_editor_isPlayer", slot == CharacterSlot.PLAYER)

        report.stat("Animations", character.animations.size)
        report.stat("Slot", slot.label)
        report.stat("Accuracy", if (exact) "Exact" else "Approximate")
        if (!exact) report.warn("Add the spritesheet XML for an exact position and camera; without it they are approximate.")
        if (character.renderType !in setOf("sparrow", "multisparrow")) {
            report.warn("Render type \"${character.renderType}\" is not a Sparrow sheet; Psych 1.0 loads texture atlases from the same image field, check the animations in the Character Editor.")
        }
        if (character.animations.any { it.flipX }) report.warn("Per-animation flipX has no Psych equivalent.")
        report.section(
            "Notes",
            listOf(
                "Health bar color set to the default ${slot.label.lowercase(Locale.US)} color; change it in Psych's Character Editor.",
                "Positions were rebased for the ${slot.label.lowercase(Locale.US)} slot."
            )
        )
        return ReverseResult("characters/$id.json", root.toString(2), report.build())
    }
}

object VSliceToPsychStage {
    fun convert(id: String, stage: VStage): ReverseResult {
        val report = ReportBuilder()
        val objects = mutableListOf<Pair<Int, JSONObject>>()
        val markers = listOf(
            stage.girlfriend.zIndex to "gfGroup",
            stage.dad.zIndex to "dadGroup",
            stage.boyfriend.zIndex to "boyfriendGroup"
        )
        var pixel = false
        stage.props.forEach { prop ->
            val obj = JSONObject()
            val solid = prop.assetPath.startsWith("#")
            val animated = prop.animations.isNotEmpty()
            obj.put("type", if (solid) "square" else if (animated) "animatedSprite" else "sprite")
            obj.put("name", prop.name)
            obj.put("x", prop.x.toJsonNumber())
            obj.put("y", prop.y.toJsonNumber())
            obj.put("scale", JSONArray(listOf(prop.scaleX.toJsonNumber(), prop.scaleY.toJsonNumber())))
            obj.put("scroll", JSONArray(listOf(prop.scrollX.toJsonNumber(), prop.scrollY.toJsonNumber())))
            obj.put("alpha", prop.alpha.toJsonNumber())
            obj.put("angle", prop.angle.toJsonNumber())
            obj.put("color", if (solid) prop.assetPath.removePrefix("#").uppercase() else prop.color.removePrefix("#").ifEmpty { "FFFFFF" }.uppercase())
            obj.put("filters", 3)
            if (!solid) {
                obj.put("image", prop.assetPath)
                obj.put("flipX", prop.flipX)
                obj.put("flipY", prop.flipY)
                obj.put("antialiasing", !prop.isPixel)
                if (prop.isPixel) pixel = true
                if (animated) {
                    val anims = JSONArray()
                    prop.animations.forEach { anim ->
                        anims.put(
                            JSONObject()
                                .put("anim", anim.name)
                                .put("name", anim.prefix)
                                .put("fps", anim.frameRate)
                                .put("loop", anim.looped)
                                .put("indices", JSONArray(anim.indices))
                                .put("offsets", JSONArray(listOf((anim.offsetX * prop.scaleX).toJsonNumber(), (anim.offsetY * prop.scaleY).toJsonNumber())))
                        )
                    }
                    obj.put("animations", anims)
                    obj.put("firstAnimation", prop.startingAnimation.ifEmpty { prop.animations.first().name })
                    if (prop.animType != "sparrow") report.warn("\"${prop.name}\" uses ${prop.animType} frames; Psych reads the image as an atlas automatically, check it in the Stage Editor.")
                }
            }
            objects += prop.zIndex * 10 to obj
        }
        markers.forEach { (z, type) -> objects += z * 10 + 5 to JSONObject().put("type", type) }
        val ordered = JSONArray(objects.sortedBy { it.first }.map { it.second })

        fun pos(c: VStageCharacter, slot: CharacterSlot) = JSONArray(listOf((c.x - slot.stageAnchorX).toJsonNumber(), (c.y - slot.stageAnchorY).toJsonNumber()))
        fun cam(c: VStageCharacter, slot: CharacterSlot) = JSONArray(listOf((c.cameraX - slot.cameraBaseX).toJsonNumber(), (c.cameraY - slot.cameraBaseY).toJsonNumber()))
        val root = JSONObject()
            .put("directory", "")
            .put("defaultZoom", stage.cameraZoom.toJsonNumber())
            .put("isPixelStage", pixel)
            .put("stageUI", if (pixel) "pixel" else "normal")
            .put("boyfriend", pos(stage.boyfriend, CharacterSlot.PLAYER))
            .put("girlfriend", pos(stage.girlfriend, CharacterSlot.GIRLFRIEND))
            .put("opponent", pos(stage.dad, CharacterSlot.OPPONENT))
            .put("hide_girlfriend", false)
            .put("camera_boyfriend", cam(stage.boyfriend, CharacterSlot.PLAYER))
            .put("camera_opponent", cam(stage.dad, CharacterSlot.OPPONENT))
            .put("camera_girlfriend", cam(stage.girlfriend, CharacterSlot.GIRLFRIEND))
            .put("camera_speed", 1)
            .put("objects", ordered)
        report.stat("Objects", stage.props.size)
        report.stat("Zoom", stage.cameraZoom.toJsonNumber().toString())
        if (stage.directory.isNotBlank() && stage.directory != "shared") report.warn("V-Slice asset library \"${stage.directory}\" has no Psych equivalent; images are read from the mod's images folder.")
        if (listOf(stage.boyfriend, stage.dad, stage.girlfriend).any { it.scroll != 1.0 && it !== stage.girlfriend }) report.warn("Character scroll factors other than 1 are not stored in Psych stage files.")
        report.section("Notes", listOf("Props, layer order, scroll, scale, alpha, angle, color, flip and animations were carried over. Edit it further in Psych's Stage Editor."))
        return ReverseResult("stages/$id.json", root.toString(2), report.build())
    }
}

data class LevelSongInfo(val name: String, val icon: String, val difficulties: List<String>)

object VSliceToPsychLevel {
    private const val SLOT_WIDTH = 320.0
    private const val PSYCH_SLOT_SHIFT = 150.0
    private const val PSYCH_Y = 70.0
    private val baseProps = mapOf("storymenu/props/dad" to "dad", "storymenu/props/bf" to "bf", "storymenu/props/gf" to "gf")

    fun convert(id: String, level: VLevel, songs: Map<String, LevelSongInfo>): ReverseResult {
        val report = ReportBuilder()
        val extra = mutableListOf<Pair<String, String>>()
        val copies = mutableListOf<Pair<String, String>>()
        val characters = mutableListOf("", "", "")
        level.props.take(3).forEachIndexed { index, prop ->
            val base = baseProps[prop.assetPath.lowercase()]
            val slot = when (base) {
                "dad" -> 0
                "bf" -> 1
                "gf" -> 2
                else -> index
            }
            if (base != null) {
                characters[slot] = base
                return@forEachIndexed
            }
            if (prop.assetPath.isBlank()) return@forEachIndexed
            val image = prop.assetPath.substringAfterLast('/')
            val name = image.lowercase().replace(Regex("[^a-z0-9_-]+"), "-")
            val idle = prop.animations.firstOrNull { it.name == "idle" } ?: prop.animations.firstOrNull()
            val confirm = prop.animations.firstOrNull { it.name == "confirm" }
            val x = SLOT_WIDTH * (1 + slot) - PSYCH_SLOT_SHIFT - SLOT_WIDTH * index - prop.offsetX
            val y = PSYCH_Y - prop.offsetY
            val menu = JSONObject()
                .put("image", image)
                .put("scale", (if (prop.isPixel) prop.scale * 6.0 else prop.scale).toJsonNumber())
                .put("position", JSONArray(listOf(x.toJsonNumber(), y.toJsonNumber())))
                .put("idle_anim", idle?.prefix ?: "idle")
                .put("confirm_anim", confirm?.prefix ?: idle?.prefix ?: "idle")
                .put("flipX", prop.flipX)
                .put("antialiasing", !prop.isPixel)
            extra += "images/menucharacters/$name.json" to menu.toString(2)
            if (!prop.assetPath.startsWith("menucharacters/")) {
                copies += "images/${prop.assetPath}.png" to "images/menucharacters/$image.png"
                copies += "images/${prop.assetPath}.xml" to "images/menucharacters/$image.xml"
            }
            characters[slot] = name
        }
        val songArray = JSONArray()
        val allDiffs = linkedSetOf<String>()
        level.songs.forEach { songId ->
            val info = songs[songId]
            songArray.put(JSONArray().put(info?.name ?: songId).put(info?.icon ?: "face").put(JSONArray(listOf(146, 113, 253))))
            info?.difficulties?.let { allDiffs += it }
            if (info == null) report.warn("No metadata for \"$songId\"; its freeplay icon is \"face\". Add the song's metadata file for its name and icon.")
        }
        val diffs = allDiffs.toList()
        val custom = diffs.isNotEmpty() && diffs.toSet() != setOf("easy", "normal", "hard")
        val root = JSONObject()
            .put("songs", songArray)
            .put("weekCharacters", JSONArray(characters))
            .put("weekBackground", "stage")
            .put("weekBefore", "")
            .put("storyName", level.name)
            .put("weekName", level.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) })
            .put("startUnlocked", true)
            .put("hiddenUntilUnlocked", false)
            .put("hideStoryMode", !level.visible)
            .put("hideFreeplay", false)
            .put("difficulties", if (custom) diffs.joinToString(",") { it.replaceFirstChar { c -> c.titlecase(Locale.US) } } else "")
        report.stat("Songs", level.songs.size)
        report.stat("Menu characters", characters.count { it.isNotEmpty() })
        report.section("Title image", listOf("V-Slice: images/${level.titleAsset}.png", "Psych: images/storymenu/$id.png"))
        if (copies.isNotEmpty()) report.section("Menu character images to copy", copies.filter { it.first.endsWith(".png") }.map { "${it.first} → ${it.second}" })
        copies += "images/${level.titleAsset}.png" to "images/storymenu/$id.png"
        if (!level.background.equals("#F9CF51", ignoreCase = true)) report.warn("Psych week backgrounds are images (images/menubackgrounds/menu_<name>.png); the color ${level.background} was not carried over.")
        return ReverseResult("weeks/$id.json", root.toString(2), report.build(), extra, copies)
    }
}
