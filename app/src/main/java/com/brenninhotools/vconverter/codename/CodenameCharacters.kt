package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.character.CharacterResult
import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychAnimation
import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import org.json.JSONObject

data class CodenameAnimation(
    val name: String,
    val prefix: String,
    val fps: Double,
    val loop: Boolean,
    val indices: List<Int>,
    val x: Double,
    val y: Double,
    val label: Boolean
)

data class CodenameCharacter(
    val id: String,
    val sprite: String,
    val extraSheets: List<String>,
    val x: Double,
    val y: Double,
    val cameraX: Double,
    val cameraY: Double,
    val scale: Double,
    val flipX: Boolean,
    val playerOffsets: Boolean,
    val antialiasing: Boolean,
    val icon: String,
    val color: String,
    val holdTime: Double,
    val interval: Int?,
    val centerCamera: Boolean,
    val gameOverCharacter: String?,
    val animations: List<CodenameAnimation>
) {
    val images: List<String> get() = (listOf("characters/$sprite") + extraSheets).distinct()
}

object CodenameCharacterParser {
    fun isCharacterXml(raw: String): Boolean = runCatching { XmlNode.parse(raw).name.equals("character", ignoreCase = true) }.getOrDefault(false)

    fun parse(id: String, raw: String): CodenameCharacter {
        val root = XmlNode.parse(raw)
        if (!root.name.equals("character", ignoreCase = true)) {
            throw IllegalArgumentException("This XML is not a Codename Engine character (root is <${root.name}>).")
        }
        val defaultFps = root.double("defFps") ?: 24.0
        val anims = root.elements("anim").mapNotNull { node ->
            val name = node.attr("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CodenameAnimation(
                name = name,
                prefix = node.attr("anim") ?: "",
                fps = node.double("fps")?.takeIf { it > 0.0 } ?: defaultFps,
                loop = node.bool("loop") ?: false,
                indices = node.attr("indices")?.let { XmlNode.numberRange(it) } ?: emptyList(),
                x = node.double("x") ?: 0.0,
                y = node.double("y") ?: 0.0,
                label = node.bool("label") ?: false
            )
        }
        val sheets = root.elements("spritesheet", "sheet").mapNotNull { (it.attr("path") ?: it.text).trim().takeIf { p -> p.isNotEmpty() } }
        return CodenameCharacter(
            id = id,
            sprite = root.attr("sprite")?.takeIf { it.isNotBlank() } ?: id,
            extraSheets = sheets,
            x = root.double("x") ?: 0.0,
            y = root.double("y") ?: 0.0,
            cameraX = root.double("camx") ?: 0.0,
            cameraY = root.double("camy") ?: 0.0,
            scale = root.double("scale")?.takeIf { it > 0.0 } ?: 1.0,
            flipX = root.bool("flipX") ?: false,
            playerOffsets = root.bool("isPlayer") ?: false,
            antialiasing = root.bool("antialiasing") ?: true,
            icon = root.attr("icon")?.takeIf { it.isNotBlank() } ?: id,
            color = root.attr("color") ?: "",
            holdTime = root.double("holdTime") ?: 4.0,
            interval = root.int("interval"),
            centerCamera = root.bool("centercam") ?: true,
            gameOverCharacter = root.attr("gameOverChar")?.takeIf { it.isNotBlank() },
            animations = anims
        )
    }
}

object CodenameToVSliceCharacter {
    private val swaps = mapOf("singLEFT" to "singRIGHT", "singRIGHT" to "singLEFT")

    fun defaultSlot(character: CodenameCharacter): CharacterSlot = when {
        character.playerOffsets -> CharacterSlot.PLAYER
        character.id.lowercase().startsWith("gf") -> CharacterSlot.GIRLFRIEND
        else -> CharacterSlot.OPPONENT
    }

    fun convert(
        character: CodenameCharacter,
        slot: CharacterSlot,
        atlases: Map<String, SparrowAtlas>,
        gameOver: CodenameCharacter? = null
    ): CharacterResult {
        val notes = ReportBuilder()
        val mirrored = (slot == CharacterSlot.PLAYER) != character.playerOffsets && slot != CharacterSlot.GIRLFRIEND
        val atlas = atlases[PsychToVSliceCharacter.atlasKey(character.images.first())]
        val baseWidth = atlas?.firstFrame?.sourceWidth

        fun psychAnim(anim: CodenameAnimation, extraX: Double = 0.0, extraY: Double = 0.0, scale: Double = character.scale): PsychAnimation {
            var offsetX = anim.x * scale + extraX
            val offsetY = anim.y * scale + extraY
            var name = anim.name
            if (mirrored) {
                val frame = atlas?.firstFrameOf(anim.prefix, anim.indices)
                offsetX = if (frame != null && baseWidth != null) (frame.sourceWidth - baseWidth) * scale - offsetX else -offsetX
                swaps.entries.firstOrNull { name.startsWith(it.key) }?.let { (from, to) -> name = to + name.removePrefix(from) }
            }
            return PsychAnimation(name, anim.prefix, anim.fps.toInt().coerceAtLeast(1), anim.loop, anim.indices, offsetX, offsetY)
        }

        val animations = character.animations.map { psychAnim(it) }.toMutableList()
        val images = character.images.toMutableList()
        if (gameOver != null) {
            if (gameOver.scale != character.scale) {
                notes.warn("Game over character \"${gameOver.id}\" has a different scale; its animations were not merged.")
            } else {
                val dx = (if (mirrored) -character.x else character.x) - gameOver.x
                val dy = character.y - gameOver.y
                gameOver.animations.filter { anim -> animations.none { it.anim == anim.name } }.forEach { anim ->
                    animations += psychAnim(anim, dx, dy, gameOver.scale)
                }
                gameOver.images.forEach { if (it !in images) images += it }
                notes.section("Game over", listOf("Animations of \"${gameOver.id}\" (${gameOver.animations.joinToString { it.name }}) were merged in; V-Slice plays the death animations on the player character itself."))
            }
        }

        val positionX = if (mirrored) -character.x else character.x
        var camX = character.cameraX
        var camY = character.cameraY
        if (!character.centerCamera) {
            val frame = atlas?.firstFrame
            if (frame != null) {
                camX -= frame.sourceWidth * character.scale / 2.0
                camY -= frame.sourceHeight * character.scale / 2.0
            } else {
                notes.warn("centercam=\"false\" moves the camera to the character's corner in Codename; without the spritesheet XML the camera offset is approximate.")
            }
        }
        val psychCamX = when {
            slot == CharacterSlot.PLAYER -> positionX - character.x - camX
            else -> character.x + camX - positionX
        }

        val psych = PsychCharacter(
            image = images.joinToString(","),
            scale = character.scale,
            positionX = positionX,
            positionY = character.y,
            cameraX = psychCamX,
            cameraY = camY,
            flipX = character.flipX,
            noAntialiasing = !character.antialiasing,
            healthIcon = character.icon,
            healthBarColor = rgb(character.color),
            singDuration = character.holdTime,
            vocalsFile = "",
            editorIsPlayer = character.playerOffsets,
            animations = animations
        )
        val result = PsychToVSliceCharacter.convert(CharacterSource(character.id, psych, slot, atlases))
        val json = JSONObject(result.json)
        character.interval?.let { json.put("danceEvery", it.coerceAtLeast(1)) }
        if (mirrored) {
            notes.warn("This character was made for the ${if (character.playerOffsets) "player" else "opponent"} side and is used on the other one; Codename mirrors it at runtime, so singLEFT/singRIGHT were swapped and the offsets mirrored.")
        }
        if (character.animations.any { it.label }) {
            notes.warn("Some animations use Animate frame labels; V-Slice needs symbol names, so check them in the character JSON.")
        }
        val report = result.report.let { base ->
            val extra = notes.build()
            base.copy(
                warnings = base.warnings.filterNot { it.startsWith("Psych plays") } + extra.warnings,
                sections = base.sections.filterNot { it.title == "Notes" } + extra.sections
            )
        }
        return CharacterResult(character.id, json.toString(2), report)
    }

    private fun rgb(color: String): List<Int> {
        val hex = color.trim().removePrefix("#").removePrefix("0x").removePrefix("0X").let { if (it.length == 8) it.substring(2) else it }
        if (hex.length != 6) return listOf(161, 161, 161)
        val value = hex.toIntOrNull(16) ?: return listOf(161, 161, 161)
        return listOf((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
    }
}
