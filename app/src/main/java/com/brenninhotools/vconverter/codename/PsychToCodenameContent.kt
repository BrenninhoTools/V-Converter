package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.core.AnimateAtlas
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.stage.PsychStage
import com.brenninhotools.vconverter.stage.PsychStageObject
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychWeek
import kotlin.math.abs
import com.brenninhotools.vconverter.core.roundLong

object PsychToCodenameCharacter {
    fun designedForPlayer(id: String, character: PsychCharacter): Boolean = character.editorIsPlayer ?: run {
        val lower = id.lowercase()
        lower.startsWith("bf") || lower.startsWith("boyfriend") || lower.contains("-player") || lower.endsWith("playable")
    }

    fun defaultSlot(id: String, character: PsychCharacter): CharacterSlot = when {
        id.lowercase().startsWith("gf") -> CharacterSlot.GIRLFRIEND
        designedForPlayer(id, character) -> CharacterSlot.PLAYER
        else -> CharacterSlot.OPPONENT
    }

    fun spriteFor(image: String): Pair<String, String?> {
        val clean = image.trim().replace('\\', '/').removePrefix("images/").removeSuffix(".png")
        return if (clean.startsWith("characters/")) clean.removePrefix("characters/") to null
        else clean.substringAfterLast('/') to clean
    }

    fun convert(id: String, character: PsychCharacter, slot: CharacterSlot, atlas: AnimateAtlas? = null, gameOverCharacter: String? = null, atlasFiles: List<String> = emptyList()): CodenamePsychResult {
        val animate = atlas != null
        val report = ReportBuilder()
        val scale = character.scale.takeIf { it > 0.0 } ?: 1.0
        val player = designedForPlayer(id, character)
        val images = character.atlasImages.ifEmpty { listOf(character.image) }
        val (sprite, moved) = spriteFor(images.first())
        val copies = mutableListOf<PsychCopy>()
        if (moved != null) {
            listOf("png", "xml", "txt", "json").forEach { ext -> copies += PsychCopy("images/$moved.$ext", "images/characters/$sprite.$ext") }
            atlasFiles.forEach { name -> copies += PsychCopy("images/$moved/$name", "images/characters/$sprite/$name") }
            report.section("Spritesheet", listOf("images/$moved was copied to images/characters/$sprite (Codename loads characters from images/characters/)."))
        }
        val camX = when (slot) {
            CharacterSlot.PLAYER -> -character.cameraX
            CharacterSlot.OPPONENT -> character.cameraX
            CharacterSlot.GIRLFRIEND -> character.cameraX - 150.0
        }
        val camY = if (slot == CharacterSlot.GIRLFRIEND) character.cameraY + 100.0 else character.cameraY
        val xml = XmlBuilder("codename-engine-character")
        xml.open(
            "character",
            "x" to character.positionX.takeIf { it != 0.0 },
            "y" to character.positionY.takeIf { it != 0.0 },
            "sprite" to sprite.takeIf { it != id },
            "flipX" to if (character.flipX) "true" else null,
            "isPlayer" to if (player) "true" else null,
            "icon" to character.healthIcon.takeIf { it != id },
            "color" to CodenameWriters.hex(character.healthBarColor),
            "scale" to scale.takeIf { it != 1.0 },
            "holdTime" to (character.singDuration * 1.1).let { round(it) }.takeIf { abs(it - 4.0) > 1e-6 },
            "camx" to round(camX).takeIf { it != 0.0 },
            "camy" to round(camY).takeIf { it != 0.0 },
            "centercam" to if (animate) "true" else null,
            "antialiasing" to if (character.noAntialiasing) "false" else null,
            "gameOverChar" to gameOverCharacter?.takeIf { it.isNotBlank() && it != "bf-dead" }
        )
        images.drop(1).forEach { extra ->
            val path = extra.trim().removePrefix("images/").removeSuffix(".png")
            xml.leaf("spritesheet", "path" to path)
        }
        val missingSymbols = mutableListOf<String>()
        character.animations.forEach { anim ->
            val symbol = atlas?.findSymbol(anim.prefix)
            if (atlas != null && symbol == null) missingSymbols += "${anim.anim} (\"${anim.prefix}\")"
            val bounds = symbol?.bounds
            xml.leaf(
                "anim",
                "name" to anim.anim,
                "anim" to (symbol?.name ?: anim.prefix),
                "x" to round(anim.offsetX / scale - (bounds?.x ?: 0.0)),
                "y" to round(anim.offsetY / scale - (bounds?.y ?: 0.0)),
                "fps" to anim.fps,
                "loop" to anim.loop.toString(),
                "indices" to anim.indices.takeIf { it.isNotEmpty() }?.let { numberRange(it) }
            )
        }
        xml.close("character")
        report.stat("Animations", character.animations.size)
        report.stat("Side", if (player) "player" else if (slot == CharacterSlot.GIRLFRIEND) "girlfriend" else "opponent")
        val notes = mutableListOf<String>()
        notes += "Offsets were divided by the scale ($scale): Codename scales animation offsets with the sprite, Psych does not."
        if (player) notes += "isPlayer=\"true\": Codename mirrors the character (and swaps singLEFT/singRIGHT) when it is used as the opponent."
        if (character.vocalsFile.isNotBlank()) notes += "Vocals file \"${character.vocalsFile}\" becomes the strumline vocals suffix in the converted charts."
        if (animate) {
            notes += "Animate atlas: centercam=\"true\" keeps Psych's centered camera."
            notes += "Psych symbol names (prefix match) were resolved to the exact symbol names Codename's flixel-animate needs, and offsets account for each symbol's bounds."
        }
        if (missingSymbols.isNotEmpty()) report.warn("These animations have no matching symbol in Animation.json and will not play: ${missingSymbols.joinToString()}.")
        report.section("Notes", notes)
        if (slot == CharacterSlot.GIRLFRIEND && id.lowercase().startsWith("gf").not()) report.warn("Camera offsets were converted for the girlfriend slot.")
        return CodenamePsychResult(id, listOf(PsychFile("data/characters/$id.xml", xml.toString())), report.build(), copies)
    }

    fun numberRange(list: List<Int>): String {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < list.size) {
            var j = i
            while (j + 1 < list.size && list[j + 1] == list[j] + 1) j++
            parts += if (j - i >= 2) "${list[i]}..${list[j]}" else (i..j).joinToString(",") { list[it].toString() }
            i = j + 1
        }
        return parts.joinToString(",")
    }

    fun round(value: Double): Double = (value * 100.0).roundLong() / 100.0
}

object PsychToCodenameStage {
    private val markers = mapOf("gf" to "girlfriend", "gfGroup" to "girlfriend", "dad" to "dad", "dadGroup" to "dad", "boyfriend" to "boyfriend", "boyfriendGroup" to "boyfriend")

    fun convert(id: String, stage: PsychStage, luaNotes: List<String> = emptyList()): CodenamePsychResult {
        val report = ReportBuilder()
        val xml = XmlBuilder("codename-engine-stage")
        xml.open("stage", "zoom" to stage.defaultZoom, "name" to id)
        val pixel = stage.isPixelStage || stage.stageUI.equals("pixel", ignoreCase = true)
        if (pixel) xml.leaf("use-extension", "script" to "pixel")
        val objects = stage.objects?.takeIf { list -> list.isNotEmpty() } ?: emptyList()
        val withMarkers = if (objects.any { it.type in markers }) objects else objects + listOf("gfGroup", "dadGroup", "boyfriendGroup").map { marker(it) }
        val written = mutableSetOf<String>()
        var lowOnly = 0
        var highOnly = 0
        var otherFilters = 0
        withMarkers.forEach { obj ->
            markers[obj.type]?.let { slot ->
                if (slot in written) return@forEach
                written += slot
                when (slot) {
                    "girlfriend" -> xml.leaf("girlfriend", "x" to stage.girlfriend.x, "y" to stage.girlfriend.y, "camxoffset" to stage.cameraGirlfriend.x.takeIf { it != 0.0 }, "camyoffset" to stage.cameraGirlfriend.y.takeIf { it != 0.0 })
                    "dad" -> xml.leaf("dad", "x" to stage.opponent.x, "y" to stage.opponent.y, "camxoffset" to stage.cameraOpponent.x.takeIf { it != 0.0 }, "camyoffset" to stage.cameraOpponent.y.takeIf { it != 0.0 })
                    else -> xml.leaf("boyfriend", "x" to stage.boyfriend.x, "y" to stage.boyfriend.y, "camxoffset" to stage.cameraBoyfriend.x.takeIf { it != 0.0 }, "camyoffset" to stage.cameraBoyfriend.y.takeIf { it != 0.0 })
                }
                return@forEach
            }
            if (obj.type !in setOf("sprite", "animatedSprite", "square")) return@forEach
            val low = obj.filters and PsychStageParser.FILTER_LOW_QUALITY != 0
            val high = obj.filters and PsychStageParser.FILTER_HIGH_QUALITY != 0
            if (obj.filters and (PsychStageParser.FILTER_STORY_MODE or PsychStageParser.FILTER_FREEPLAY) != 0) otherFilters++
            val group = when {
                low && !high -> "low-memory"
                high && !low -> "high-memory"
                else -> null
            }
            if (group == "low-memory") lowOnly++
            if (group == "high-memory") highOnly++
            if (group != null) xml.open(group)
            writeObject(xml, obj)
            if (group != null) xml.close(group)
        }
        xml.close("stage")

        val hooks = mutableListOf<String>()
        if (stage.hideGirlfriend) hooks += "if (gf != null) gf.visible = false;"
        if (stage.cameraSpeed != 1.0) hooks += "FlxG.camera.followLerp = 0.04 * ${CodenameToPsychEvents.number(stage.cameraSpeed)};"
        val notes = mutableListOf<String>()
        if (pixel) notes += "Pixel stage: the base game's pixel extension (data/scripts/pixel.hx) gives it pixel notes and UI."
        if (stage.hideGirlfriend) notes += "hide_girlfriend became gf.visible = false in the stage script."
        if (stage.cameraSpeed != 1.0) notes += "camera_speed became FlxG.camera.followLerp in the stage script."
        if (lowOnly > 0 || highOnly > 0) notes += "Quality filters became <low-memory> ($lowOnly) and <high-memory> ($highOnly) groups."
        if (otherFilters > 0) report.warn("$otherFilters object(s) only show in story mode or freeplay; Codename stages cannot filter by mode, so they always show.")
        if (stage.preload.isNotEmpty()) notes += "The preload list was dropped (Codename caches stage sprites on its own)."
        notes += luaNotes
        report.stat("Objects", objects.count { it.type in setOf("sprite", "animatedSprite", "square") })
        report.section("Notes", notes)
        return CodenamePsychResult(id, listOf(PsychFile("data/stages/$id.xml", xml.toString())), report.build(),
            hooks = if (hooks.isEmpty()) PsychHooks() else PsychHooks(callbacks = mapOf("postCreate" to hooks)))
    }

    private fun marker(type: String) = PsychStageObject(type, type, "", 0.0, 0.0, com.brenninhotools.vconverter.stage.PsychPoint(1.0, 1.0),
        com.brenninhotools.vconverter.stage.PsychPoint(1.0, 1.0), 1.0, 0.0, "FFFFFF", true, false, false, 3, emptyList(), "")

    private fun scrollAttrs(obj: PsychStageObject): Array<Pair<String, Any?>> =
        if (obj.scroll.x == obj.scroll.y) arrayOf("scroll" to obj.scroll.x.takeIf { it != 1.0 })
        else arrayOf("scrollx" to obj.scroll.x, "scrolly" to obj.scroll.y)

    private fun color(raw: String): String? {
        val hex = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X").let { if (it.length == 8) it.substring(2) else it }.uppercase()
        return if (hex.length == 6 && hex != "FFFFFF") "#$hex" else null
    }

    private fun writeObject(xml: XmlBuilder, obj: PsychStageObject) {
        if (obj.type == "square") {
            xml.leaf(
                "box",
                "name" to obj.name,
                "x" to obj.x,
                "y" to obj.y,
                "width" to (obj.scale.x).roundLong().toInt().coerceAtLeast(1),
                "height" to (obj.scale.y).roundLong().toInt().coerceAtLeast(1),
                "color" to (color(obj.color) ?: "#FFFFFF"),
                *scrollAttrs(obj),
                "alpha" to obj.alpha.takeIf { it != 1.0 },
                "angle" to obj.angle.takeIf { it != 0.0 }
            )
            return
        }
        val scaled = obj.scale.x != 1.0 || obj.scale.y != 1.0
        val scaleAttrs: Array<Pair<String, Any?>> = when {
            !scaled -> emptyArray()
            obj.scale.x == obj.scale.y -> arrayOf("scale" to obj.scale.x, "updateHitbox" to "true")
            else -> arrayOf("scalex" to obj.scale.x, "scaley" to obj.scale.y, "updateHitbox" to "true")
        }
        val attrs = arrayOf(
            "name" to obj.name,
            "x" to obj.x,
            "y" to obj.y,
            "sprite" to obj.image.removePrefix("images/").removeSuffix(".png"),
            *scrollAttrs(obj),
            *scaleAttrs,
            "alpha" to obj.alpha.takeIf { it != 1.0 },
            "angle" to obj.angle.takeIf { it != 0.0 },
            "color" to color(obj.color),
            "antialiasing" to if (!obj.antialiasing) "false" else null,
            "flipX" to if (obj.flipX) "true" else null,
            "flipY" to if (obj.flipY) "true" else null,
            "blend" to obj.blend.takeIf { it.isNotBlank() && it != "normal" }
        )
        if (obj.type != "animatedSprite" || obj.animations.isEmpty()) {
            xml.leaf("sprite", *attrs)
            return
        }
        val first = obj.firstAnimation.ifEmpty { obj.animations.first().anim }
        val bops = obj.danceEvery > 0.0
        val alternate = bops && obj.animations.any { it.anim == "danceLeft" } && obj.animations.any { it.anim == "danceRight" } && first in setOf("danceLeft", "danceRight")
        val beatAnims = when {
            !bops -> emptySet()
            alternate -> setOf("danceLeft", "danceRight")
            else -> setOf(first)
        }
        if (bops) xml.open("sprite", *attrs, "type" to "beat", "beatInterval" to (obj.danceEvery).roundLong().toInt().coerceAtLeast(1))
        else xml.open("sprite", *attrs, "type" to "loop")
        val ordered = if (bops) obj.animations.sortedBy { if (it.anim == "danceLeft") 0 else if (it.anim in beatAnims) 1 else 2 }
        else obj.animations.filter { it.anim != first } + obj.animations.filter { it.anim == first }
        ordered.forEach { anim ->
            xml.leaf(
                "anim",
                "name" to anim.anim,
                "type" to if (bops && anim.anim !in beatAnims) "none" else null,
                "anim" to anim.prefix.takeIf { it.isNotEmpty() },
                "fps" to anim.fps,
                "loop" to anim.loop.toString(),
                "indices" to anim.indices.takeIf { it.isNotEmpty() }?.let { PsychToCodenameCharacter.numberRange(it) },
                "x" to (anim.offsetX / obj.scale.x).let { PsychToCodenameCharacter.round(it) }.takeIf { it != 0.0 },
                "y" to (anim.offsetY / obj.scale.y).let { PsychToCodenameCharacter.round(it) }.takeIf { it != 0.0 }
            )
        }
        xml.close("sprite")
    }
}

object PsychToCodenameWeek {
    private val defaultDifficulties = listOf("easy", "normal", "hard")

    fun convert(id: String, week: PsychWeek): CodenamePsychResult {
        val report = ReportBuilder()
        val xml = XmlBuilder("codename-engine-week")
        val chars = week.weekCharacters.take(3).map { it.ifBlank { "none" } }
        xml.open("week", "name" to week.storyName.ifBlank { week.weekName.ifBlank { id } }, "chars" to chars.joinToString(","), "sprite" to id)
        week.songs.forEach { song ->
            val folder = SongPath.format(song.name)
            if (folder.isEmpty() || song.name.lowercase() == folder) xml.textNode("song", song.name)
            else xml.textNode("song", folder, "displayName" to song.name)
        }
        val difficulties = week.difficulties.map { SongPath.format(it) }.filter { it.isNotEmpty() }
        if (difficulties.isNotEmpty() && difficulties != defaultDifficulties) difficulties.forEach { xml.leaf("difficulty", "name" to it) }
        xml.close("week")
        val copies = listOf(PsychCopy("images/storymenu/$id.png", "images/menus/storymenu/weeks/$id.png"))
        report.stat("Songs", week.songs.size)
        val notes = mutableListOf("Week image: images/storymenu/$id.png → images/menus/storymenu/weeks/$id.png")
        if (week.weekBackground.isNotBlank()) notes += "The story menu background \"${week.weekBackground}\" has no Codename equivalent."
        if (!week.startUnlocked || week.hiddenUntilUnlocked || week.weekBefore.isNotBlank()) report.warn("Week locking (weekBefore/startUnlocked) has no Codename equivalent; the week is always unlocked.")
        if (week.hideFreeplay) notes += "Hidden from freeplay: its songs are left out of data/config/freeplaySonglist.txt."
        report.section("Notes", notes)
        val files = if (week.hideStoryMode) emptyList() else listOf(PsychFile("data/weeks/weeks/$id.xml", xml.toString()))
        if (week.hideStoryMode) report.section("Story mode", listOf("Hidden from story mode, so no week XML was written; its songs stay in freeplay."))
        return CodenamePsychResult(id, files, report.build(), copies)
    }

    fun menuCharacter(id: String, menu: PsychMenuCharacter): CodenamePsychResult {
        val xml = XmlBuilder("codename-engine-week-character")
        xml.open(
            "char",
            "sprite" to "menucharacters/${menu.image}",
            "scale" to menu.scale.takeIf { it != 1.0 },
            "x" to menu.positionX,
            "y" to menu.positionY,
            "flipX" to if (menu.flipX) "true" else null,
            "antialiasing" to if (!menu.antialiasing) "false" else null
        )
        xml.leaf("anim", "name" to "idle", "anim" to menu.idleAnim, "fps" to 24, "loop" to "true", "type" to "loop")
        if (menu.confirmAnim.isNotBlank() && menu.confirmAnim != menu.idleAnim) xml.leaf("anim", "name" to "confirm", "anim" to menu.confirmAnim, "fps" to 24, "loop" to "false", "type" to "none")
        xml.close("char")
        val report = ReportBuilder()
        report.section("Notes", listOf("The idle animation loops like in Psych (type=\"loop\")."))
        return CodenamePsychResult(id, listOf(PsychFile("data/weeks/characters/$id.xml", xml.toString())), report.build())
    }
}
