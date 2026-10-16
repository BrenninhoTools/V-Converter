package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.week.LevelResult
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import org.json.JSONArray
import org.json.JSONObject

data class CodenameWeekSong(val name: String, val hidden: Boolean, val displayName: String?, val variation: String?)

data class CodenameWeek(
    val id: String,
    val name: String,
    val sprite: String,
    val characters: List<String?>,
    val songs: List<CodenameWeekSong>,
    val difficulties: List<String>,
    val background: String
)

data class CodenameMenuAnimation(val name: String, val prefix: String, val fps: Double, val loop: Boolean, val indices: List<Int>, val x: Double, val y: Double, val type: String)

data class CodenameMenuCharacter(
    val name: String,
    val sprite: String,
    val scale: Double,
    val x: Double,
    val y: Double,
    val flipX: Boolean,
    val antialiasing: Boolean,
    val interval: Int,
    val animations: List<CodenameMenuAnimation>
)

object CodenameWeekParser {
    fun isWeekXml(raw: String): Boolean = runCatching { XmlNode.parse(raw).name.equals("week", ignoreCase = true) }.getOrDefault(false)

    fun isMenuCharacterXml(raw: String): Boolean = runCatching { XmlNode.parse(raw).name.equals("char", ignoreCase = true) }.getOrDefault(false)

    fun parseWeek(id: String, raw: String): CodenameWeek {
        val root = XmlNode.parse(raw)
        if (!root.name.equals("week", ignoreCase = true)) throw IllegalArgumentException("This XML is not a Codename Engine week (root is <${root.name}>).")
        val songs = root.elements("song").mapNotNull { node ->
            val name = node.text.trim()
            if (name.isEmpty()) null else CodenameWeekSong(name, node.bool("hide") == true, node.attr("displayName"), node.attr("variation"))
        }
        val characters = (root.attr("chars") ?: "").split(',').map { it.trim().takeUnless { c -> c.isEmpty() || c == "none" || c == "null" } }
        return CodenameWeek(
            id = id,
            name = root.attr("name") ?: id,
            sprite = root.attr("sprite")?.takeIf { it.isNotBlank() } ?: id,
            characters = if (root.has("chars")) characters else listOf(null, null, null),
            songs = songs,
            difficulties = root.elements("difficulty").mapNotNull { it.attr("name") }.ifEmpty { listOf("easy", "normal", "hard") },
            background = root.attr("bgColor") ?: ""
        )
    }

    fun parseMenuCharacter(name: String, raw: String): CodenameMenuCharacter {
        val root = XmlNode.parse(raw)
        val charName = root.attr("name")?.takeIf { it.isNotBlank() } ?: name
        return CodenameMenuCharacter(
            name = charName,
            sprite = root.attr("sprite")?.takeIf { it.isNotBlank() } ?: "menus/storymenu/characters/$charName",
            scale = root.double("scale")?.takeIf { it > 0.0 } ?: 1.0,
            x = root.double("x") ?: 0.0,
            y = root.double("y") ?: 0.0,
            flipX = root.bool("flipX") ?: false,
            antialiasing = root.bool("antialiasing") ?: true,
            interval = (root.int("beatInterval") ?: root.int("interval") ?: 2).coerceAtLeast(1),
            animations = root.elements("anim").mapNotNull { a ->
                val animName = a.attr("name") ?: return@mapNotNull null
                CodenameMenuAnimation(
                    name = animName,
                    prefix = a.attr("anim") ?: "",
                    fps = a.double("fps") ?: 24.0,
                    loop = a.bool("loop") ?: false,
                    indices = a.attr("indices")?.let { XmlNode.numberRange(it) } ?: emptyList(),
                    x = a.double("x") ?: 0.0,
                    y = a.double("y") ?: 0.0,
                    type = (a.attr("type") ?: "beat").lowercase()
                )
            }
        )
    }
}

object CodenameToVSliceLevel {
    private const val SLOT_WIDTH = 1280.0 * 0.25
    private const val SLOT_SHIFT = 150.0
    private const val MENU_Y = 70.0

    fun convert(week: CodenameWeek, menuCharacters: Map<String, CodenameMenuCharacter>, songId: (String) -> String = { SongPath.format(it) }): LevelResult {
        val report = ReportBuilder()
        val props = JSONArray()
        val missing = mutableListOf<String>()
        week.characters.take(3).forEachIndexed { slot, name ->
            if (name == null) return@forEachIndexed
            val index = props.length()
            val menu = menuCharacters[name]
            when {
                menu != null -> props.put(prop(menu, slot, index))
                PsychToVSliceLevel.basePropDefinitions.containsKey(name) -> {
                    val base = JSONObject(PsychToVSliceLevel.basePropDefinitions.getValue(name))
                    if (index != slot) {
                        val offsets = base.getJSONArray("offsets")
                        offsets.put(0, offsets.getDouble(0) + (slot - index) * SLOT_WIDTH)
                    }
                    props.put(base)
                }
                else -> missing += name
            }
        }
        val ids = week.songs.map { songId(it.name) }
        val background = week.background.trim().let { raw ->
            val hex = raw.removePrefix("#").removePrefix("0x").removePrefix("0X").let { if (it.length == 8) it.substring(2) else it }
            if (hex.length == 6 && hex.all { it.isLetterOrDigit() }) "#" + hex.uppercase() else "#F9CF51"
        }
        val root = JSONObject()
            .put("version", PsychToVSliceLevel.LEVEL_VERSION)
            .put("name", week.name)
            .put("titleAsset", "menus/storymenu/weeks/${week.sprite}")
            .put("props", props)
            .put("background", background)
            .put("songs", JSONArray(ids))
        report.stat("Songs", ids.size)
        report.stat("Props", props.length())
        if (missing.isNotEmpty()) report.warn("No week character XML for ${missing.joinToString { "\"$it\"" }} (data/weeks/characters/<name>.xml); they were left out.")
        val hidden = week.songs.filter { it.hidden }.map { songId(it.name) }
        report.section(
            "Not carried over",
            listOfNotNull(
                if (hidden.isNotEmpty()) "Hidden from freeplay: ${hidden.joinToString()} (V-Slice lists every level song in freeplay)" else null,
                week.difficulties.takeIf { it != listOf("easy", "normal", "hard") }?.let { "Week difficulties ${it.joinToString()} (each V-Slice song lists its own)" },
                week.songs.mapNotNull { s -> s.variation?.let { "${songId(s.name)} plays its \"$it\" variation in Codename" } }.takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
        )
        return LevelResult(week.id, root.toString(2), ids, report.build())
    }

    private fun prop(menu: CodenameMenuCharacter, slot: Int, index: Int): JSONObject {
        val x = SLOT_WIDTH * (1 + slot) - SLOT_SHIFT - SLOT_WIDTH * index - menu.x
        val y = MENU_Y - menu.y
        val animations = JSONArray()
        val anims = menu.animations.ifEmpty { listOf(CodenameMenuAnimation("idle", "", 24.0, false, emptyList(), 0.0, 0.0, "beat")) }
        anims.forEach { anim ->
            val entry = JSONObject()
                .put("name", anim.name)
                .put("prefix", anim.prefix)
                .put("frameRate", anim.fps.toInt().coerceAtLeast(1))
                .put("looped", anim.loop)
            if (anim.indices.isNotEmpty()) entry.put("frameIndices", JSONArray(anim.indices))
            if (anim.x != 0.0 || anim.y != 0.0) entry.put("offsets", JSONArray(listOf(anim.x.toJsonNumber(), anim.y.toJsonNumber())))
            animations.put(entry)
        }
        val bops = anims.any { it.type == "beat" || it.type == "onbeat" }
        val entry = JSONObject()
            .put("assetPath", menu.sprite)
            .put("scale", menu.scale.toJsonNumber())
            .put("offsets", JSONArray(listOf(x.toJsonNumber(), y.toJsonNumber())))
            .put("danceEvery", if (bops) menu.interval else 0)
            .put("startingAnimation", anims.firstOrNull { it.name == "idle" }?.name ?: anims.first().name)
            .put("animations", animations)
        if (!menu.antialiasing) entry.put("isPixel", true)
        if (menu.flipX) entry.put("flipX", true)
        return entry
    }
}
