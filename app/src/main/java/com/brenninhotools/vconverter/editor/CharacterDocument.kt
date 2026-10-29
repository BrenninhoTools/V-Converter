package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.generator.CharacterGenerator
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.reverse.VSliceParser
import org.json.JSONArray
import org.json.JSONObject

data class CharacterAnim(
    val name: String,
    val prefix: String,
    val frameRate: Int = 24,
    val looped: Boolean = false,
    val indices: List<Int> = emptyList(),
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val flipX: Boolean = false
)

data class CharacterDocument(
    val id: String,
    val name: String = "",
    val assetPath: String = "characters/$id",
    val scale: Double = 1.0,
    val isPixel: Boolean = false,
    val flipX: Boolean = false,
    val healthIcon: String = id,
    val healthIconPixel: Boolean = false,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val cameraX: Double = 0.0,
    val cameraY: Double = 0.0,
    val singTime: Double = 8.0,
    val danceEvery: Double = 2.0,
    val startingAnimation: String = "idle",
    val slot: CharacterSlot = CharacterSlot.OPPONENT,
    val animations: List<CharacterAnim> = emptyList(),
    val rawJson: String? = null
) {
    fun anim(name: String): CharacterAnim? = animations.firstOrNull { it.name == name }

    fun replaceAnim(index: Int, anim: CharacterAnim): CharacterDocument =
        copy(animations = animations.mapIndexed { i, a -> if (i == index) anim else a })

    fun addAnim(anim: CharacterAnim): CharacterDocument = copy(animations = animations + anim)

    fun removeAnim(index: Int): CharacterDocument = copy(animations = animations.filterIndexed { i, _ -> i != index })

    fun moveAnim(index: Int, by: Int): CharacterDocument {
        val target = index + by
        if (index !in animations.indices || target !in animations.indices) return this
        val list = animations.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        return copy(animations = list)
    }

    fun toJson(): String {
        val root = rawJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val names = animations.map { it.name }.toSet()
        val array = JSONArray()
        animations.filter { it.name.isNotBlank() && it.prefix.isNotBlank() }.forEach { anim ->
            val entry = JSONObject()
                .put("name", anim.name)
                .put("prefix", anim.prefix)
                .put("frameRate", anim.frameRate)
                .put("looped", anim.looped)
            if (anim.indices.isNotEmpty()) entry.put("frameIndices", JSONArray(anim.indices))
            if (anim.flipX) entry.put("flipX", true)
            entry.put("offsets", JSONArray(listOf(anim.offsetX.toJsonNumber(), anim.offsetY.toJsonNumber())))
            array.put(entry)
        }
        val icon = (root.optJSONObject("healthIcon") ?: JSONObject())
            .put("id", healthIcon.trim().ifEmpty { id })
            .put("isPixel", healthIconPixel)
        root.put("version", root.optString("version").ifEmpty { PsychToVSliceCharacter.CHARACTER_VERSION })
            .put("name", name.trim().ifEmpty { Ids.pretty(id) })
            .put("renderType", root.optString("renderType").ifEmpty { "sparrow" })
            .put("assetPath", assetPath.trim())
            .put("scale", scale.toJsonNumber())
            .put("isPixel", isPixel)
            .put("healthIcon", icon)
            .put("offsets", JSONArray(listOf(offsetX.toJsonNumber(), offsetY.toJsonNumber())))
            .put("cameraOffsets", JSONArray(listOf(cameraX.toJsonNumber(), cameraY.toJsonNumber())))
            .put("singTime", singTime.toJsonNumber())
            .put("danceEvery", danceEvery.toJsonNumber())
            .put("flipX", flipX)
            .put("startingAnimation", startingAnimation.takeIf { it in names } ?: animations.firstOrNull()?.name ?: "idle")
            .put("animations", array)
        return root.toString(2)
    }

    companion object {
        fun fromVSlice(id: String, json: String, slot: CharacterSlot? = null): CharacterDocument {
            val c = VSliceParser.character(json)
            return CharacterDocument(
                id = id,
                name = c.name,
                assetPath = c.assetPath,
                scale = c.scale,
                isPixel = c.isPixel,
                flipX = c.flipX,
                healthIcon = c.healthIcon.ifEmpty { id },
                healthIconPixel = c.healthIconPixel,
                offsetX = c.offsetX,
                offsetY = c.offsetY,
                cameraX = c.cameraX,
                cameraY = c.cameraY,
                singTime = c.singTime,
                danceEvery = c.danceEvery,
                startingAnimation = c.startingAnimation,
                slot = slot ?: defaultSlot(id),
                animations = c.animations.map { CharacterAnim(it.name, it.prefix, it.frameRate, it.looped, it.indices, it.offsetX, it.offsetY, it.flipX) },
                rawJson = json
            )
        }

        fun fromAtlas(id: String, atlas: SpriteAtlas, assetPath: String): CharacterDocument {
            val anims = CharacterGenerator.guessAll(atlas.prefixes()).filter { it.name.isNotEmpty() }
                .map { CharacterAnim(it.name, it.prefix, it.frameRate, it.looped) }
            val names = anims.map { it.name }.toSet()
            val dances = "danceLeft" in names && "danceRight" in names
            return CharacterDocument(
                id = id,
                name = Ids.pretty(id),
                assetPath = assetPath,
                danceEvery = if (dances) 1.0 else 2.0,
                startingAnimation = if (dances) "danceRight" else "idle",
                slot = defaultSlot(id),
                animations = anims
            )
        }

        fun defaultSlot(id: String): CharacterSlot = when {
            id.lowercase().startsWith("gf") -> CharacterSlot.GIRLFRIEND
            id.lowercase().startsWith("bf") || id.lowercase().contains("playable") -> CharacterSlot.PLAYER
            else -> CharacterSlot.OPPONENT
        }
    }
}

data class AtlasFrame(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val frameX: Int,
    val frameY: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotated: Boolean
)

class SpriteAtlas(val frames: List<AtlasFrame>, val imagePath: String = "") {
    private val numberSuffix = Regex("\\d+$")

    fun prefixes(): List<String> = frames.map { it.name.replace(numberSuffix, "") }.distinct()

    fun framesFor(prefix: String, indices: List<Int> = emptyList()): List<AtlasFrame> {
        if (prefix.isEmpty()) return emptyList()
        val matching = frames.filter { it.name.startsWith(prefix) }
            .sortedWith(compareBy({ number(it.name, prefix) ?: Int.MAX_VALUE }, { it.name }))
        if (indices.isEmpty()) return matching
        val byNumber = matching.associateBy { number(it.name, prefix) }
        return indices.mapNotNull { byNumber[it] ?: matching.getOrNull(it) }
    }

    private fun number(name: String, prefix: String): Int? = name.removePrefix(prefix).takeLastWhile { it.isDigit() }.toIntOrNull()

    companion object {
        private val subTexture = Regex("<SubTexture\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
        private val textureAtlas = Regex("<TextureAtlas\\b([^>]*)>", RegexOption.IGNORE_CASE)
        private val attribute = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")

        private fun attrs(text: String): Map<String, String> = attribute.findAll(text).associate { a ->
            a.groupValues[1].lowercase() to unescape(a.groupValues[3].ifEmpty { a.groupValues[4] })
        }

        fun parse(xml: String): SpriteAtlas {
            val imagePath = textureAtlas.find(xml)?.let { attrs(it.groupValues[1])["imagepath"] } ?: ""
            val frames = subTexture.findAll(xml).mapNotNull { match ->
                val a = attrs(match.groupValues[1])
                val name = a["name"] ?: return@mapNotNull null
                fun int(key: String) = a[key]?.toDoubleOrNull()?.toInt()
                val width = int("width") ?: return@mapNotNull null
                val height = int("height") ?: return@mapNotNull null
                val rotated = a["rotated"]?.lowercase() == "true"
                AtlasFrame(
                    name = name,
                    x = int("x") ?: 0,
                    y = int("y") ?: 0,
                    width = width,
                    height = height,
                    frameX = int("framex") ?: 0,
                    frameY = int("framey") ?: 0,
                    frameWidth = int("framewidth") ?: if (rotated) height else width,
                    frameHeight = int("frameheight") ?: if (rotated) width else height,
                    rotated = rotated
                )
            }.toList()
            return SpriteAtlas(frames, imagePath)
        }

        private fun unescape(text: String): String = text
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}
