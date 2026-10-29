package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VStageCharacter
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import org.json.JSONArray
import org.json.JSONObject

data class StageProp(
    val name: String,
    val assetPath: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val zIndex: Int = 0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val scrollX: Double = 1.0,
    val scrollY: Double = 1.0,
    val alpha: Double = 1.0,
    val angle: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val isPixel: Boolean = false,
    val prefix: String = "",
    val raw: String? = null
) {
    val isSolid: Boolean get() = assetPath.startsWith("#")
}

data class StageSpot(
    val x: Double,
    val y: Double,
    val zIndex: Int,
    val cameraX: Double,
    val cameraY: Double,
    val scroll: Double = 1.0,
    val scale: Double = 1.0,
    val alpha: Double = 1.0
)

enum class StageSlot(val key: String, val label: String) { GIRLFRIEND("gf", "Girlfriend"), DAD("dad", "Opponent"), BOYFRIEND("bf", "Player") }

data class StageDocument(
    val id: String,
    val name: String = "",
    val cameraZoom: Double = 1.0,
    val directory: String = "shared",
    val props: List<StageProp> = emptyList(),
    val spots: Map<StageSlot, StageSpot> = defaultSpots,
    val rawJson: String? = null
) {
    fun spot(slot: StageSlot): StageSpot = spots[slot] ?: defaultSpots.getValue(slot)

    fun withSpot(slot: StageSlot, spot: StageSpot): StageDocument = copy(spots = spots + (slot to spot))

    fun replaceProp(index: Int, prop: StageProp): StageDocument = copy(props = props.mapIndexed { i, p -> if (i == index) prop else p })

    fun addProp(prop: StageProp): StageDocument = copy(props = props + prop)

    fun removeProp(index: Int): StageDocument = copy(props = props.filterIndexed { i, _ -> i != index })

    fun uniqueName(base: String): String {
        val clean = Ids.of(base).ifEmpty { "prop" }
        if (props.none { it.name == clean }) return clean
        var n = 2
        while (props.any { it.name == "$clean-$n" }) n++
        return "$clean-$n"
    }

    fun toJson(): String {
        val root = rawJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val array = JSONArray()
        props.forEach { prop ->
            val entry = prop.raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
            entry.put("name", prop.name)
                .put("assetPath", prop.assetPath)
                .put("position", JSONArray(listOf(prop.x.toJsonNumber(), prop.y.toJsonNumber())))
                .put("zIndex", prop.zIndex)
                .put("scale", JSONArray(listOf(prop.scaleX.toJsonNumber(), prop.scaleY.toJsonNumber())))
            if (prop.scrollX != 1.0 || prop.scrollY != 1.0 || entry.has("scroll")) entry.put("scroll", JSONArray(listOf(prop.scrollX.toJsonNumber(), prop.scrollY.toJsonNumber())))
            if (prop.alpha != 1.0 || entry.has("alpha")) entry.put("alpha", prop.alpha.toJsonNumber())
            if (prop.angle != 0.0 || entry.has("angle")) entry.put("angle", prop.angle.toJsonNumber())
            if (prop.flipX || entry.has("flipX")) entry.put("flipX", prop.flipX)
            if (prop.flipY || entry.has("flipY")) entry.put("flipY", prop.flipY)
            if (prop.isPixel || entry.has("isPixel")) entry.put("isPixel", prop.isPixel)
            if (prop.prefix.isNotEmpty() && !entry.has("animations")) {
                entry.put("animType", "sparrow")
                entry.put("animations", JSONArray().put(JSONObject().put("name", "idle").put("prefix", prop.prefix).put("frameRate", 24).put("looped", true)))
                entry.put("startingAnimation", "idle")
            }
            array.put(entry)
        }
        val characters = root.optJSONObject("characters") ?: JSONObject()
        StageSlot.entries.forEach { slot ->
            val spot = spot(slot)
            val entry = characters.optJSONObject(slot.key) ?: JSONObject()
            entry.put("zIndex", spot.zIndex)
                .put("position", JSONArray(listOf(spot.x.toJsonNumber(), spot.y.toJsonNumber())))
                .put("cameraOffsets", JSONArray(listOf(spot.cameraX.toJsonNumber(), spot.cameraY.toJsonNumber())))
            if (spot.scroll != 1.0 || entry.has("scroll")) entry.put("scroll", JSONArray(listOf(spot.scroll.toJsonNumber(), spot.scroll.toJsonNumber())))
            if (spot.scale != 1.0 || entry.has("scale")) entry.put("scale", spot.scale.toJsonNumber())
            if (spot.alpha != 1.0 || entry.has("alpha")) entry.put("alpha", spot.alpha.toJsonNumber())
            characters.put(slot.key, entry)
        }
        root.put("version", root.optString("version").ifEmpty { PsychToVSliceStage.STAGE_VERSION })
            .put("name", name.trim().ifEmpty { Ids.pretty(id) })
            .put("cameraZoom", cameraZoom.toJsonNumber())
            .put("directory", directory.ifBlank { "shared" })
            .put("props", array)
            .put("characters", characters)
        return root.toString(2)
    }

    companion object {
        val defaultSpots: Map<StageSlot, StageSpot> = mapOf(
            StageSlot.GIRLFRIEND to StageSpot(751.5, 787.0, 100, 0.0, 0.0, 0.95),
            StageSlot.DAD to StageSpot(335.0, 885.0, 200, 150.0, -100.0),
            StageSlot.BOYFRIEND to StageSpot(989.5, 885.0, 300, -100.0, -100.0)
        )

        private fun spot(c: VStageCharacter) = StageSpot(c.x, c.y, c.zIndex, c.cameraX, c.cameraY, c.scroll, c.scale, c.alpha)

        fun fromVSlice(id: String, json: String): StageDocument {
            val stage = VSliceParser.stage(json)
            val rawProps = runCatching { JSONObject(json).optJSONArray("props") }.getOrNull()
            return StageDocument(
                id = id,
                name = stage.name,
                cameraZoom = stage.cameraZoom,
                directory = stage.directory.ifBlank { "shared" },
                props = stage.props.mapIndexed { i, p ->
                    StageProp(
                        name = p.name, assetPath = p.assetPath, x = p.x, y = p.y, zIndex = p.zIndex,
                        scaleX = p.scaleX, scaleY = p.scaleY, scrollX = p.scrollX, scrollY = p.scrollY,
                        alpha = p.alpha, angle = p.angle, flipX = p.flipX, flipY = p.flipY, isPixel = p.isPixel,
                        prefix = p.animations.firstOrNull { it.name == p.startingAnimation }?.prefix ?: p.animations.firstOrNull()?.prefix ?: "",
                        raw = rawProps?.optJSONObject(i)?.toString()
                    )
                },
                spots = mapOf(StageSlot.BOYFRIEND to spot(stage.boyfriend), StageSlot.DAD to spot(stage.dad), StageSlot.GIRLFRIEND to spot(stage.girlfriend)),
                rawJson = json
            )
        }
    }
}
