package com.brenninhotools.vconverter.character

import com.brenninhotools.vconverter.core.AnimateAtlas
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.SparrowFrame
import com.brenninhotools.vconverter.core.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class CharacterSource(
    val id: String,
    val character: PsychCharacter,
    val slot: CharacterSlot,
    val atlases: Map<String, SparrowAtlas>,
    val displayName: String = "",
    val animate: AnimateAtlas? = null
)

data class CharacterResult(
    val id: String,
    val json: String,
    val report: Report
)

object PsychToVSliceCharacter {
    const val CHARACTER_VERSION = "1.0.0"

    fun defaultSlot(id: String, character: PsychCharacter): CharacterSlot = when {
        character.editorIsPlayer == true -> CharacterSlot.PLAYER
        id.lowercase().startsWith("gf") -> CharacterSlot.GIRLFRIEND
        id.lowercase().startsWith("bf") -> CharacterSlot.PLAYER
        else -> CharacterSlot.OPPONENT
    }

    fun atlasKey(imagePath: String): String =
        imagePath.trim().replace('\\', '/').substringAfterLast('/').substringBeforeLast(".png").lowercase()

    fun convert(source: CharacterSource): CharacterResult {
        source.animate?.let { return convertAtlas(source, it) }
        val psych = source.character
        val slot = source.slot
        val report = ReportBuilder()
        val scale = psych.scale
        val images = psych.atlasImages.ifEmpty { listOf(psych.image) }
        val mainImage = images.first()
        val atlasByImage = images.associateWith { source.atlases[atlasKey(it)] }
        val missingAtlases = images.filter { atlasByImage[it] == null }

        val idleAnim = when {
            psych.hasDanceLeftRight -> psych.animations.first { it.anim == "danceLeft" }
            else -> psych.animations.firstOrNull { it.anim == "idle" } ?: psych.animations.firstOrNull()
        }

        val firstFrame: SparrowFrame? = atlasByImage[mainImage]?.firstFrame
        val idleFrame: SparrowFrame? = idleAnim?.let { anim ->
            images.firstNotNullOfOrNull { image -> atlasByImage[image]?.firstFrameOf(anim.prefix, anim.indices) }
        }

        val exact = firstFrame != null && idleFrame != null
        val fallbackWidth = slot.stageAnchorX * 2.0
        val fallbackHeight = slot.stageAnchorY * 2.0 / (1.0 + scale)
        val fw0 = firstFrame?.sourceWidth ?: idleFrame?.sourceWidth ?: fallbackWidth
        val fh0 = firstFrame?.sourceHeight ?: idleFrame?.sourceHeight ?: fallbackHeight
        val fwi = idleFrame?.sourceWidth ?: fw0
        val fhi = idleFrame?.sourceHeight ?: fh0

        val baseX = slot.stageAnchorX - psych.positionX - scale * fwi / 2.0 - (1.0 - scale) * fw0 / 2.0
        val baseY = slot.stageAnchorY - psych.positionY - scale * fhi - (1.0 - scale) * fh0 / 2.0

        val cameraX = psych.positionX - slot.stageAnchorX + scale * fw0 / 2.0 + slot.cameraSign * psych.cameraX
        val cameraY = psych.positionY - slot.stageAnchorY + scale * fh0 / 2.0 + scale * fhi / 2.0 + psych.cameraY

        val multiAtlas = images.size > 1
        val missingPrefixes = mutableListOf<String>()
        val animations = JSONArray()
        psych.animations.forEach { anim ->
            val entry = JSONObject()
                .put("name", vsliceName(anim.anim))
                .put("prefix", anim.prefix)
            if (multiAtlas) {
                val owner = images.firstOrNull { atlasByImage[it]?.hasPrefix(anim.prefix) == true }
                if (owner != null && owner != mainImage) entry.put("assetPath", owner)
            }
            if (missingAtlases.isEmpty() && images.none { atlasByImage[it]?.hasPrefix(anim.prefix) == true }) {
                missingPrefixes += "${anim.anim} (\"${anim.prefix}\")"
            }
            if (anim.indices.isNotEmpty()) entry.put("frameIndices", JSONArray(anim.indices))
            entry.put("frameRate", anim.fps)
            entry.put("looped", anim.loop)
            entry.put(
                "offsets",
                JSONArray(
                    listOf(
                        ((anim.offsetX + baseX) / scale).toJsonNumber(),
                        ((anim.offsetY + baseY) / scale).toJsonNumber()
                    )
                )
            )
            animations.put(entry)
        }

        val healthIcon = JSONObject()
            .put("id", psych.healthIcon)
            .put("isPixel", false)

        val name = source.displayName.ifBlank { prettyName(source.id) }
        val root = JSONObject()
            .put("version", CHARACTER_VERSION)
            .put("name", name)
            .put("renderType", if (multiAtlas) "multisparrow" else "sparrow")
            .put("assetPath", mainImage)
            .put("scale", scale.toJsonNumber())
            .put("isPixel", psych.noAntialiasing)
            .put("healthIcon", healthIcon)
            .put("offsets", JSONArray(listOf(0, 0)))
            .put("cameraOffsets", JSONArray(listOf(cameraX.toJsonNumber(), cameraY.toJsonNumber())))
            .put("singTime", (psych.singDuration * 1.1).toJsonNumber())
            .put("danceEvery", if (psych.hasDanceLeftRight) 1 else 2)
            .put("flipX", psych.flipX)
            .put("startingAnimation", if (psych.hasDanceLeftRight) "danceRight" else (idleAnim?.anim ?: "idle"))
            .put("animations", animations)

        report.stat("Animations", psych.animations.size)
        report.stat("Render", if (multiAtlas) "Multi-Sparrow" else "Sparrow")
        report.stat("Slot", slot.label)
        report.stat("Accuracy", if (exact) "Exact" else "Approximate")

        if (missingAtlases.isNotEmpty()) {
            report.warn("Spritesheet XML not provided for ${missingAtlases.joinToString { "\"$it\"" }}. Positions and camera are approximate; add the XML for an exact conversion.")
        } else if (idleFrame == null) {
            report.warn("The idle animation (\"${idleAnim?.prefix ?: "idle"}\") was not found in the spritesheet, so positions are approximate.")
        }
        if (missingPrefixes.isNotEmpty()) {
            report.warn("These animations have no matching frames in the spritesheet and will not play: ${missingPrefixes.joinToString()}.")
        }
        if (psych.animations.none { it.anim == "idle" } && !psych.hasDanceLeftRight) {
            report.warn("No \"idle\" or \"danceLeft\"/\"danceRight\" animation found; V-Slice needs one to idle.")
        }
        val loopAnims = psych.animations.filter { it.anim.endsWith("-loop") }.map { it.anim }
        if (loopAnims.isNotEmpty()) {
            report.section("Loop animations", listOf("Psych's \"<anim>-loop\" animations became V-Slice's \"<anim>-hold\", which V-Slice plays the same way after the animation ends: ${loopAnims.joinToString()}."))
        }
        val color = psych.healthBarColor
        report.section(
            "Not carried over",
            listOfNotNull(
                "Health bar color ${hex(color)} (V-Slice uses fixed health bar colors)",
                psych.vocalsFile.takeIf { it.isNotBlank() }?.let { "Vocals suffix \"$it\" (V-Slice names vocals after the character id)" },
                if (slot != CharacterSlot.PLAYER && psych.editorIsPlayer == true) "Psych editor marked this as a player character" else null
            )
        )
        report.section(
            "Notes",
            listOf(
                "Offsets were rebased for the ${slot.label.lowercase(Locale.US)} slot of a converted stage. Using the character in another slot can shift it by a few pixels."
            )
        )

        return CharacterResult(source.id, root.toString(2), report.build())
    }

    private fun convertAtlas(source: CharacterSource, atlas: AnimateAtlas): CharacterResult {
        val psych = source.character
        val slot = source.slot
        val report = ReportBuilder()
        val scale = psych.scale
        val idleAnim = when {
            psych.hasDanceLeftRight -> psych.animations.first { it.anim == "danceLeft" }
            else -> psych.animations.firstOrNull { it.anim == "idle" } ?: psych.animations.firstOrNull()
        }
        val first = atlas.firstSprite
        val idleSymbol = idleAnim?.let { atlas.findSymbol(it.prefix) }
        val width = first?.first ?: idleSymbol?.bounds?.width ?: slot.stageAnchorX
        val height = first?.second ?: idleSymbol?.bounds?.height ?: slot.stageAnchorY
        val baseX = slot.stageAnchorX - psych.positionX - scale * width / 2.0
        val baseY = slot.stageAnchorY - psych.positionY - scale * height
        val cameraX = psych.positionX - slot.stageAnchorX + slot.cameraSign * psych.cameraX
        val cameraY = psych.positionY - slot.stageAnchorY + scale * height / 2.0 + psych.cameraY

        val missing = mutableListOf<String>()
        val animations = JSONArray()
        psych.animations.forEach { anim ->
            val symbol = atlas.findSymbol(anim.prefix)
            if (symbol == null) missing += "${anim.anim} (\"${anim.prefix}\")"
            val bounds = symbol?.bounds
            val entry = JSONObject()
                .put("name", vsliceName(anim.anim))
                .put("prefix", symbol?.name ?: anim.prefix)
                .put("animType", "symbol")
            if (anim.indices.isNotEmpty()) entry.put("frameIndices", JSONArray(anim.indices))
            entry.put("frameRate", anim.fps)
            entry.put("looped", anim.loop)
            entry.put(
                "offsets",
                JSONArray(
                    listOf(
                        ((anim.offsetX + baseX) / scale - (bounds?.x ?: 0.0)).toJsonNumber(),
                        ((anim.offsetY + baseY) / scale - (bounds?.y ?: 0.0)).toJsonNumber()
                    )
                )
            )
            animations.put(entry)
        }

        val root = JSONObject()
            .put("version", CHARACTER_VERSION)
            .put("name", source.displayName.ifBlank { prettyName(source.id) })
            .put("renderType", "animateatlas")
            .put("assetPath", psych.image.trim())
            .put("scale", scale.toJsonNumber())
            .put("isPixel", psych.noAntialiasing)
            .put("healthIcon", JSONObject().put("id", psych.healthIcon).put("isPixel", false))
            .put("offsets", JSONArray(listOf(0, 0)))
            .put("cameraOffsets", JSONArray(listOf(cameraX.toJsonNumber(), cameraY.toJsonNumber())))
            .put("singTime", (psych.singDuration * 1.1).toJsonNumber())
            .put("danceEvery", if (psych.hasDanceLeftRight) 1 else 2)
            .put("flipX", psych.flipX)
            .put("startingAnimation", if (psych.hasDanceLeftRight) "danceRight" else (idleAnim?.anim ?: "idle"))
            .put("applyStageMatrix", false)
            .put("animations", animations)

        report.stat("Animations", psych.animations.size)
        report.stat("Render", "Animate atlas")
        report.stat("Slot", slot.label)
        report.stat("Accuracy", if (first != null && missing.isEmpty()) "Exact" else "Approximate")
        if (missing.isNotEmpty()) report.warn("These animations have no matching symbol in Animation.json and will not play: ${missing.joinToString()}.")
        if (first == null) report.warn("No spritemap JSON was found next to Animation.json, so positions are approximate.")
        report.section(
            "Animate atlas",
            listOf(
                "Psych symbol names (prefix match) were resolved to the exact symbol names V-Slice needs (animType \"symbol\").",
                "Offsets account for each symbol's bounds, so the atlas lands where Psych draws it.",
                "Keep the atlas folder (Animation.json + spritemap) at images/${psych.image.trim()}/."
            )
        )
        val color = psych.healthBarColor
        report.section("Not carried over", listOf("Health bar color ${hex(color)} (V-Slice uses fixed health bar colors)"))
        return CharacterResult(source.id, root.toString(2), report.build())
    }

    fun vsliceName(name: String): String = if (name.endsWith("-loop")) name.removeSuffix("-loop") + "-hold" else name

    fun prettyName(id: String): String = id.split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.US) } }
        .ifBlank { id }

    private fun hex(color: List<Int>): String = String.format(Locale.US, "#%02X%02X%02X", color[0], color[1], color[2])
}
