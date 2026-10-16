package com.brenninhotools.vconverter.generator

import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import org.json.JSONArray
import org.json.JSONObject

enum class MenuProp(val label: String) {
    NONE("None"),
    BASE("Base game"),
    CUSTOM("Custom")
}

data class LevelPropSpec(
    val mode: MenuProp,
    val assetPath: String = "",
    val idlePrefix: String = "idle",
    val confirmPrefix: String = "",
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val image: PickedAsset? = null,
    val xml: PickedAsset? = null
)

data class LevelSpec(
    val id: String,
    val name: String,
    val songs: List<String>,
    val background: String = "#F9CF51",
    val visible: Boolean = true,
    val opponent: LevelPropSpec = LevelPropSpec(MenuProp.BASE),
    val boyfriend: LevelPropSpec = LevelPropSpec(MenuProp.BASE),
    val girlfriend: LevelPropSpec = LevelPropSpec(MenuProp.BASE),
    val title: PickedAsset? = null
)

object LevelGenerator {
    private val baseKeys = listOf("dad", "bf", "gf")

    fun generate(spec: LevelSpec): Generated {
        val id = Ids.of(spec.id)
        require(id.isNotEmpty()) { "Enter a level id." }
        val songs = spec.songs.map(Ids::of).filter { it.isNotEmpty() }.distinct()
        require(songs.isNotEmpty()) { "List at least one song id." }
        val files = mutableListOf<GenFile>()
        val report = ReportBuilder()
        val props = JSONArray()

        listOf(spec.opponent, spec.boyfriend, spec.girlfriend).forEachIndexed { index, prop ->
            when (prop.mode) {
                MenuProp.NONE -> Unit
                MenuProp.BASE -> props.put(JSONObject(PsychToVSliceLevel.basePropDefinitions.getValue(baseKeys[index])))
                MenuProp.CUSTOM -> {
                    val asset = prop.assetPath.trim().removePrefix("images/").substringBeforeLast(".png")
                        .ifEmpty { "storymenu/props/" + (prop.image?.stem ?: "$id-${baseKeys[index]}") }
                    val animations = JSONArray().put(
                        JSONObject().put("name", "idle").put("prefix", prop.idlePrefix.ifBlank { "idle" }).put("frameRate", 24).put("looped", true)
                    )
                    if (prop.confirmPrefix.isNotBlank()) {
                        animations.put(JSONObject().put("name", "confirm").put("prefix", prop.confirmPrefix).put("frameRate", 24).put("looped", false))
                    }
                    props.put(
                        JSONObject()
                            .put("assetPath", asset)
                            .put("scale", prop.scale.toJsonNumber())
                            .put("offsets", JSONArray(listOf(prop.offsetX.toJsonNumber(), prop.offsetY.toJsonNumber())))
                            .put("danceEvery", 0)
                            .put("startingAnimation", "idle")
                            .put("animations", animations)
                    )
                    prop.image?.let { files += GenFile("images/$asset.png", bytes = it.bytes) }
                    prop.xml?.let { files += GenFile("images/$asset.xml", bytes = it.bytes) }
                    if (prop.image == null) report.section("Menu prop to add", listOf("images/$asset.png", "images/$asset.xml"))
                }
            }
        }

        val titleAsset = "storymenu/titles/$id"
        val root = JSONObject()
            .put("version", PsychToVSliceLevel.LEVEL_VERSION)
            .put("name", spec.name.trim().ifEmpty { Ids.pretty(id) }.uppercase())
            .put("titleAsset", titleAsset)
            .put("props", props)
            .put("background", Ids.color(spec.background, "#F9CF51"))
            .put("songs", JSONArray(songs))
        if (!spec.visible) root.put("visible", false)
        files.add(0, GenFile("data/levels/$id.json", root.toString(2)))
        spec.title?.let {
            require(it.extension == "png") { "The title image must be a .png file." }
            files += GenFile("images/$titleAsset.png", bytes = it.bytes)
        }
        if (props.length() < 3) report.warn("Fewer than 3 menu props: check their offsets in the story menu.")
        if (spec.title == null) report.section("Title image to add", listOf("images/$titleAsset.png"))
        report.stat("Songs", songs.size)
        report.stat("Files", files.size)
        report.section("Songs", songs.map { "data/songs/$it/$it-metadata.json" })
        return Generated(files, report.build())
    }
}
