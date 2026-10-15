package com.brenninhotools.vconverter.week

import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.core.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class WeekSource(
    val id: String,
    val week: PsychWeek,
    val menuCharacters: Map<String, PsychMenuCharacter>
)

data class LevelResult(val id: String, val json: String, val songIds: List<String>, val report: Report)

object PsychToVSliceLevel {
    const val LEVEL_VERSION = "1.0.0"
    private const val SCREEN_WIDTH = 1280.0
    private const val SLOT_WIDTH = SCREEN_WIDTH * 0.25
    private const val PSYCH_SLOT_SHIFT = 150.0
    private const val PSYCH_Y = 70.0
    private const val DEFAULT_BACKGROUND = "#F9CF51"

    private val basePropDefinitions = mapOf(
        "dad" to """{"assetPath":"storymenu/props/dad","scale":1.0,"offsets":[100,60],"animations":[{"name":"idle","prefix":"idle0","frameRate":24}]}""",
        "bf" to """{"assetPath":"storymenu/props/bf","scale":1.0,"offsets":[150,80],"animations":[{"name":"idle","prefix":"idle0","frameRate":24},{"name":"confirm","prefix":"confirm0","frameRate":24}]}""",
        "gf" to """{"assetPath":"storymenu/props/gf","scale":1.0,"offsets":[200,80],"animations":[{"name":"danceLeft","prefix":"idle0","frameIndices":[30,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14]},{"name":"danceRight","prefix":"idle0","frameIndices":[15,16,17,18,19,20,21,22,23,24,25,26,27,28,29]}]}"""
    )

    fun convert(source: WeekSource): LevelResult {
        val week = source.week
        val report = ReportBuilder()
        val props = JSONArray()
        val missingCharacters = mutableListOf<String>()

        week.weekCharacters.take(3).forEachIndexed { slot, rawName ->
            val name = rawName.trim()
            if (name.isEmpty()) return@forEachIndexed
            val propIndex = props.length()
            val menu = source.menuCharacters[name]
            when {
                menu != null -> props.put(prop(menu, slot, propIndex))
                basePropDefinitions.containsKey(name) -> {
                    val base = JSONObject(basePropDefinitions.getValue(name))
                    if (propIndex != slot) {
                        val offsets = base.getJSONArray("offsets")
                        offsets.put(0, offsets.getDouble(0) + (slot - propIndex) * SLOT_WIDTH)
                    }
                    props.put(base)
                }
                else -> missingCharacters += name
            }
        }

        val songIds = week.songs.map { SongPath.format(it.name) }
        val background = week.weekBackground.trim().takeIf { it.isNotEmpty() }?.let { "menubackgrounds/menu_$it" } ?: DEFAULT_BACKGROUND

        val root = JSONObject()
            .put("version", LEVEL_VERSION)
            .put("name", week.storyName.ifBlank { week.weekName }.ifBlank { source.id })
            .put("titleAsset", "storymenu/${source.id}")
            .put("props", props)
            .put("background", background)
            .put("songs", JSONArray(songIds))
        if (week.hideStoryMode) root.put("visible", false)

        report.stat("Songs", songIds.size)
        report.stat("Props", props.length())

        if (missingCharacters.isNotEmpty()) {
            report.warn("No menu character data for ${missingCharacters.joinToString { "\"$it\"" }}. Add images/menucharacters/<name>.json to convert them; they were left out.")
        }
        if (week.difficulties.isNotEmpty()) {
            report.warn("Psych week difficulties (${week.difficulties.joinToString()}) are not stored in V-Slice levels; each song's metadata lists its difficulties.")
        }
        report.section(
            "Not carried over",
            listOfNotNull(
                week.weekBefore.takeIf { it.isNotBlank() && !week.startUnlocked }?.let { "Unlock after \"$it\" (V-Slice levels have no lock chain)" },
                if (week.hiddenUntilUnlocked) "Hidden until unlocked" else null,
                if (week.hideFreeplay) "Hide from freeplay (V-Slice lists every level's songs in freeplay)" else null,
                week.weekName.takeIf { it.isNotBlank() }?.let { "Week name \"$it\" (only the story name is shown in V-Slice)" }
            )
        )
        report.section(
            "Freeplay icons & colors (set these in the V-Slice freeplay instead)",
            week.songs.map { song ->
                String.format(Locale.US, "%s → icon %s, #%02X%02X%02X", SongPath.format(song.name), song.icon.ifBlank { "face" }, song.color[0], song.color[1], song.color[2])
            }
        )

        return LevelResult(source.id, root.toString(2), songIds, report.build())
    }

    private fun prop(menu: PsychMenuCharacter, slot: Int, propIndex: Int): JSONObject {
        val pixel = !menu.antialiasing
        val x = SLOT_WIDTH * (1 + slot) - PSYCH_SLOT_SHIFT - SLOT_WIDTH * propIndex - menu.positionX
        val y = PSYCH_Y - menu.positionY
        val animations = JSONArray().put(
            JSONObject()
                .put("name", "idle")
                .put("prefix", menu.idleAnim)
                .put("frameRate", 24)
                .put("looped", true)
        )
        if (menu.confirmAnim.isNotBlank() && menu.confirmAnim != menu.idleAnim) {
            animations.put(
                JSONObject()
                    .put("name", "confirm")
                    .put("prefix", menu.confirmAnim)
                    .put("frameRate", 24)
                    .put("looped", false)
            )
        }
        val entry = JSONObject()
            .put("assetPath", "menucharacters/${menu.image}")
            .put("scale", (if (pixel) menu.scale / 6.0 else menu.scale).toJsonNumber())
            .put("offsets", JSONArray(listOf(x.toJsonNumber(), y.toJsonNumber())))
            .put("danceEvery", 0)
            .put("startingAnimation", "idle")
            .put("animations", animations)
        if (pixel) entry.put("isPixel", true)
        if (menu.flipX) entry.put("flipX", true)
        return entry
    }
}
