package com.brenninhotools.vconverter.generator

import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject

data class AnimSpec(
    val name: String,
    val prefix: String,
    val frameRate: Int = 24,
    val looped: Boolean = false,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0
)

data class CharacterSpec(
    val id: String,
    val name: String = "",
    val assetPath: String = "",
    val scale: Double = 1.0,
    val isPixel: Boolean = false,
    val flipX: Boolean = false,
    val singTime: Double = 8.0,
    val healthIcon: String = "",
    val cameraX: Double = 0.0,
    val cameraY: Double = 0.0,
    val animations: List<AnimSpec>,
    val image: PickedAsset? = null,
    val xml: PickedAsset? = null
)

object CharacterGenerator {
    private val directions = listOf("left", "down", "up", "right")
    private val numberSuffix = Regex("\\d+$")

    fun prefixes(atlas: SparrowAtlas): List<String> =
        atlas.frames.map { it.name.replace(numberSuffix, "") }.distinct()

    fun guess(prefix: String): String? {
        val lower = prefix.lowercase()
        val miss = "miss" in lower
        directions.forEach { dir ->
            if (Regex("(^|[^a-z])$dir([^a-z]|$)").containsMatchIn(lower) || lower.endsWith(dir) || "note$dir" in lower.replace(" ", "")) {
                if ("dance" in lower || "idle" in lower && dir in listOf("left", "right")) {
                    return if (dir == "left") "danceLeft" else if (dir == "right") "danceRight" else null
                }
                val base = "sing" + dir.uppercase()
                return if (miss) base + "miss" else base
            }
        }
        return when {
            "idle" in lower || "dance" in lower -> "idle"
            "hey" in lower || "cheer" in lower -> "hey"
            "scared" in lower || "fear" in lower -> "scared"
            "death" in lower || "dies" in lower || "dead" in lower -> "firstDeath"
            else -> null
        }
    }

    fun guessAll(prefixes: List<String>): List<AnimSpec> {
        val used = HashSet<String>()
        return prefixes.map { prefix ->
            val name = guess(prefix)?.takeIf { used.add(it) } ?: ""
            AnimSpec(name, prefix, 24, name == "idle" || name.startsWith("dance"))
        }
    }

    fun generate(spec: CharacterSpec): Generated {
        val id = Ids.of(spec.id)
        require(id.isNotEmpty()) { "Enter a character id." }
        val animations = spec.animations.filter { it.name.isNotBlank() && it.prefix.isNotBlank() }
        require(animations.isNotEmpty()) { "Give at least one animation a name (for example idle)." }
        val duplicate = animations.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }?.key
        require(duplicate == null) { "The animation \"$duplicate\" is used more than once." }

        val imageStem = spec.image?.stem ?: spec.xml?.stem
        val assetPath = spec.assetPath.trim().removePrefix("images/").substringBeforeLast(".png")
            .ifEmpty { "characters/" + (imageStem ?: id) }
        val names = animations.map { it.name }.toSet()
        val dances = "danceLeft" in names && "danceRight" in names

        val array = JSONArray()
        animations.forEach { anim ->
            val entry = JSONObject()
                .put("name", anim.name)
                .put("prefix", anim.prefix)
                .put("frameRate", anim.frameRate)
                .put("looped", anim.looped)
            if (anim.offsetX != 0.0 || anim.offsetY != 0.0) {
                entry.put("offsets", JSONArray(listOf(anim.offsetX.toJsonNumber(), anim.offsetY.toJsonNumber())))
            }
            array.put(entry)
        }
        val root = JSONObject()
            .put("version", PsychToVSliceCharacter.CHARACTER_VERSION)
            .put("name", spec.name.trim().ifEmpty { Ids.pretty(id) })
            .put("renderType", "sparrow")
            .put("assetPath", assetPath)
            .put("scale", (if (spec.isPixel && spec.scale == 1.0) 6.0 else spec.scale).toJsonNumber())
            .put("isPixel", spec.isPixel)
            .put("healthIcon", JSONObject().put("id", spec.healthIcon.trim().ifEmpty { id }).put("isPixel", spec.isPixel))
            .put("offsets", JSONArray(listOf(0, 0)))
            .put("cameraOffsets", JSONArray(listOf(spec.cameraX.toJsonNumber(), spec.cameraY.toJsonNumber())))
            .put("singTime", spec.singTime.toJsonNumber())
            .put("danceEvery", if (dances) 1 else 2)
            .put("flipX", spec.flipX)
            .put("startingAnimation", if (dances) "danceRight" else if ("idle" in names) "idle" else animations.first().name)
            .put("animations", array)

        val files = mutableListOf(GenFile("data/characters/$id.json", root.toString(2)))
        spec.image?.let {
            require(it.extension == "png") { "The spritesheet image must be a .png file." }
            files += GenFile("images/$assetPath.png", bytes = it.bytes)
        }
        spec.xml?.let { files += GenFile("images/$assetPath.xml", bytes = it.bytes) }

        val report = ReportBuilder()
        report.stat("Animations", animations.size)
        report.stat("Files", files.size)
        val required = listOf("idle", "singLEFT", "singDOWN", "singUP", "singRIGHT")
        val missing = required.filter { it !in names && !(it == "idle" && dances) }
        if (missing.isNotEmpty()) report.warn("Missing common animations: ${missing.joinToString()}.")
        if (spec.image == null) report.section("Spritesheet to add", listOf("images/$assetPath.png", "images/$assetPath.xml"))
        report.section("Health icon", listOf("images/icons/icon-${spec.healthIcon.trim().ifEmpty { id }}.png (300×150, two frames)"))
        return Generated(files, report.build())
    }
}
