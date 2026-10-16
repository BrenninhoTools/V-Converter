package com.brenninhotools.vconverter.generator

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import org.json.JSONArray
import org.json.JSONObject

data class PropSpec(
    val name: String,
    val image: PickedAsset? = null,
    val assetPath: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val scrollX: Double = 1.0,
    val scrollY: Double = 1.0,
    val scale: Double = 1.0,
    val front: Boolean = false
)

data class StageSpec(
    val id: String,
    val name: String = "",
    val cameraZoom: Double = 1.0,
    val isPixel: Boolean = false,
    val props: List<PropSpec> = emptyList(),
    val backgroundColor: String = ""
)

object StageGenerator {
    private val psychDefaults = mapOf(
        CharacterSlot.PLAYER to (770.0 to 100.0),
        CharacterSlot.OPPONENT to (100.0 to 100.0),
        CharacterSlot.GIRLFRIEND to (400.0 to 130.0)
    )

    fun generate(spec: StageSpec): Generated {
        val id = Ids.of(spec.id).ifEmpty { "" }
        require(id.isNotEmpty()) { "Enter a stage id." }
        require(spec.cameraZoom > 0) { "Camera zoom must be above 0." }
        val files = mutableListOf<GenFile>()
        val props = JSONArray()
        val usedNames = HashSet<String>()
        var backZ = 10
        var frontZ = 400

        if (spec.backgroundColor.isNotBlank()) {
            props.put(
                JSONObject()
                    .put("name", "background")
                    .put("zIndex", 0)
                    .put("position", JSONArray(listOf(-1000, -600)))
                    .put("assetPath", Ids.color(spec.backgroundColor, "#000000"))
                    .put("scale", JSONArray(listOf(4000, 2400)))
                    .put("scroll", JSONArray(listOf(0, 0)))
            )
            usedNames += "background"
        }

        spec.props.forEach { prop ->
            var name = Ids.of(prop.name).ifEmpty { prop.image?.stem?.let(Ids::of) ?: "prop" }
            var n = 2
            val base = name
            while (!usedNames.add(name)) name = base + "-" + n++
            val asset = prop.assetPath.trim().removePrefix("images/").substringBeforeLast(".png")
                .ifEmpty { "stages/$id/" + (prop.image?.stem ?: name) }
            val entry = JSONObject()
                .put("name", name)
                .put("zIndex", if (prop.front) frontZ else backZ)
                .put("position", JSONArray(listOf(prop.x.toJsonNumber(), prop.y.toJsonNumber())))
                .put("assetPath", asset)
                .put("scale", JSONArray(listOf(prop.scale.toJsonNumber(), prop.scale.toJsonNumber())))
            if (prop.scrollX != 1.0 || prop.scrollY != 1.0) entry.put("scroll", JSONArray(listOf(prop.scrollX.toJsonNumber(), prop.scrollY.toJsonNumber())))
            if (spec.isPixel) entry.put("isPixel", true)
            props.put(entry)
            if (prop.front) frontZ += 10 else backZ += 10
            prop.image?.let {
                require(it.extension == "png") { "${it.name}: stage images must be .png files." }
                files += GenFile("images/$asset.png", bytes = it.bytes)
            }
        }

        fun character(slot: CharacterSlot, z: Int, scroll: Double): JSONObject {
            val (px, py) = psychDefaults.getValue(slot)
            val entry = JSONObject()
                .put("zIndex", z)
                .put("position", JSONArray(listOf((px + slot.stageAnchorX).toJsonNumber(), (py + slot.stageAnchorY).toJsonNumber())))
                .put("cameraOffsets", JSONArray(listOf(slot.cameraBaseX.toJsonNumber(), slot.cameraBaseY.toJsonNumber())))
            if (scroll != 1.0) entry.put("scroll", JSONArray(listOf(scroll, scroll)))
            return entry
        }
        val characters = JSONObject()
            .put("bf", character(CharacterSlot.PLAYER, 300, 1.0))
            .put("dad", character(CharacterSlot.OPPONENT, 200, 1.0))
            .put("gf", character(CharacterSlot.GIRLFRIEND, 100, 0.95))
        val root = JSONObject()
            .put("version", PsychToVSliceStage.STAGE_VERSION)
            .put("name", spec.name.trim().ifEmpty { Ids.pretty(id) })
            .put("cameraZoom", spec.cameraZoom.toJsonNumber())
            .put("directory", "shared")
            .put("props", props)
            .put("characters", characters)
        files.add(0, GenFile("data/stages/$id.json", root.toString(2)))

        val report = ReportBuilder()
        report.stat("Props", props.length())
        report.stat("Files", files.size)
        val missing = spec.props.filter { it.image == null }.map { "images/" + it.assetPath.ifBlank { "stages/$id/${Ids.of(it.name)}" } + ".png" }
        if (missing.isNotEmpty()) report.section("Images to add", missing)
        report.section(
            "Next steps",
            listOf(
                "Use this stage in a song with the id \"$id\".",
                "Fine-tune positions in V-Slice's Stage Editor."
            )
        )
        return Generated(files, report.build())
    }
}
