package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.stage.PsychPoint
import com.brenninhotools.vconverter.stage.PsychStage
import com.brenninhotools.vconverter.stage.PsychStageAnimation
import com.brenninhotools.vconverter.stage.PsychStageObject
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageResult
import com.brenninhotools.vconverter.stage.StageSource
import org.json.JSONArray
import org.json.JSONObject

data class ImageInfo(val width: Double, val height: Double, val animated: Boolean)

data class CodenameCharacterSpot(
    val slot: String,
    val x: Double,
    val y: Double,
    val scroll: PsychPoint,
    val scale: Double,
    val alpha: Double,
    val angle: Double,
    val cameraX: Double,
    val cameraY: Double,
    val flip: Boolean?
)

data class CodenameBeatSprite(val name: String, val interval: Int, val offset: Int, val anims: List<String>, val onCountdown: Boolean)

data class CodenameProperty(val sprite: String, val path: String, val type: String, val value: String)

data class CodenameStage(
    val id: String,
    val name: String,
    val zoom: Double,
    val folder: String,
    val objects: List<PsychStageObject>,
    val spots: Map<String, CodenameCharacterSpot>,
    val spriteNames: List<String>,
    val extensions: List<String>,
    val notes: List<String>,
    val warnings: List<String>,
    val beats: List<CodenameBeatSprite> = emptyList(),
    val properties: List<CodenameProperty> = emptyList(),
    val blends: Map<String, String> = emptyMap(),
    val zoomFactors: Map<String, Double> = emptyMap(),
    val startCamera: Pair<Double?, Double?>? = null,
    val ratings: Pair<Double?, Double?>? = null,
    val extraCharacters: Map<String, CodenameCharacterSpot> = emptyMap()
)

object CodenameStageParser {
    private val defaults = mapOf(
        "bf" to CodenameCharacterSpot("bf", 770.0, 100.0, PsychPoint(1.0, 1.0), 1.0, 1.0, 0.0, 0.0, 0.0, true),
        "gf" to CodenameCharacterSpot("gf", 400.0, 130.0, PsychPoint(0.95, 0.95), 1.0, 1.0, 0.0, 0.0, 0.0, false),
        "dad" to CodenameCharacterSpot("dad", 100.0, 100.0, PsychPoint(1.0, 1.0), 1.0, 1.0, 0.0, 0.0, 0.0, false)
    )

    fun isStageXml(raw: String): Boolean = runCatching { XmlNode.parse(raw).name.equals("stage", ignoreCase = true) }.getOrDefault(false)

    fun parse(id: String, raw: String, includeLowMemory: Boolean = false, images: (String) -> ImageInfo? = { null }): CodenameStage {
        val root = XmlNode.parse(raw)
        if (!root.name.equals("stage", ignoreCase = true)) {
            throw IllegalArgumentException("This XML is not a Codename Engine stage (root is <${root.name}>).")
        }
        val folder = root.attr("folder")?.trim()?.let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" } ?: ""
        val notes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val nodes = mutableListOf<XmlNode>()
        val memory = HashMap<XmlNode, Int>()
        var lowMemory = 0
        root.children.forEach { node ->
            when (node.name.lowercase()) {
                "high-memory" -> node.children.forEach {
                    nodes += it
                    memory[it] = PsychStageParser.FILTER_HIGH_QUALITY
                }
                "low-memory" -> if (includeLowMemory) node.children.forEach {
                    nodes += it
                    memory[it] = PsychStageParser.FILTER_LOW_QUALITY
                } else lowMemory += node.children.size
                else -> nodes += node
            }
        }
        if (lowMemory > 0) notes += "$lowMemory low-memory-only node(s) were left out (V-Slice always loads the full stage)."
        val beats = mutableListOf<CodenameBeatSprite>()
        val properties = mutableListOf<CodenameProperty>()
        val blends = LinkedHashMap<String, String>()
        val zoomFactors = LinkedHashMap<String, Double>()
        var ratings: Pair<Double?, Double?>? = null
        val extraSpots = LinkedHashMap<String, CodenameCharacterSpot>()

        val objects = mutableListOf<PsychStageObject>()
        val spots = LinkedHashMap<String, CodenameCharacterSpot>()
        val spriteNames = mutableListOf<String>()
        val extraCharacters = mutableListOf<String>()
        val extensions = mutableListOf<String>()
        nodes.forEach { node ->
            when (node.name.lowercase()) {
                "sprite", "spr", "sparrow" -> {
                    val name = node.attr("name") ?: return@forEach
                    spriteNames += name
                    val obj = sprite(node, name, folder, images, warnings)
                    objects += memory[node]?.let { obj.copy(filters = it) } ?: obj
                    beatSprite(node, name)?.let { beats += it }
                    node.attr("blend")?.takeIf { it.isNotBlank() && it.lowercase() != "normal" }?.let { blends[name] = it.lowercase() }
                    node.double("zoomfactor")?.takeIf { it != 1.0 }?.let { zoomFactors[name] = it }
                    properties += properties(node, name)
                }
                "box", "solid" -> {
                    val name = node.attr("name") ?: return@forEach
                    val width = node.double("width") ?: return@forEach
                    val height = node.double("height") ?: return@forEach
                    spriteNames += name
                    properties += properties(node, name)
                    objects += PsychStageObject(
                        type = "square",
                        name = name,
                        image = "",
                        x = node.double("x") ?: 0.0,
                        y = node.double("y") ?: 0.0,
                        scale = PsychPoint(width * (node.double("scalex") ?: node.double("scale") ?: 1.0), height * (node.double("scaley") ?: node.double("scale") ?: 1.0)),
                        scroll = scroll(node),
                        alpha = property(node, "alpha")?.toDoubleOrNull() ?: node.double("alpha") ?: 1.0,
                        angle = node.double("angle") ?: 0.0,
                        color = node.attr("color") ?: "FFFFFF",
                        antialiasing = true,
                        flipX = false,
                        flipY = false,
                        filters = memory[node] ?: (PsychStageParser.FILTER_LOW_QUALITY or PsychStageParser.FILTER_HIGH_QUALITY),
                        animations = emptyList(),
                        firstAnimation = ""
                    )
                }
                "boyfriend", "bf", "player" -> spots["bf"] = spot(node, "bf")
                "girlfriend", "gf" -> spots["gf"] = spot(node, "gf")
                "dad", "opponent" -> spots["dad"] = spot(node, "dad")
                "character", "char" -> node.attr("name")?.let {
                    extraCharacters += it
                    extraSpots[it] = spot(node, "dad").copy(slot = it)
                }
                "ratings", "combo" -> {
                    ratings = node.double("x") to node.double("y")
                    notes += "The ratings position (x ${node.attr("x") ?: "default"}, y ${node.attr("y") ?: "default"}) is not part of V-Slice stages."
                }
                "use-extension", "extension", "ext" -> node.attr("script")?.let { extensions += (node.attr("folder") ?: "data/scripts/").let { f -> if (f.endsWith("/")) f else "$f/" } + it }
            }
        }
        if (extraCharacters.isNotEmpty()) warnings += "Extra character slots (${extraCharacters.joinToString()}) have no V-Slice stage equivalent; spawn them from a script."
        listOf("bf", "gf", "dad").forEach { key -> if (!spots.containsKey(key)) spots[key] = defaults.getValue(key) }

        val ordered = mutableListOf<PsychStageObject>()
        var objectIndex = 0
        nodes.forEach { node ->
            when (node.name.lowercase()) {
                "sprite", "spr", "sparrow", "box", "solid" -> if (node.has("name") && objectIndex < objects.size && objects[objectIndex].name == node.attr("name")) ordered += objects[objectIndex++]
                "boyfriend", "bf", "player" -> ordered += marker("boyfriendGroup")
                "girlfriend", "gf" -> ordered += marker("gfGroup")
                "dad", "opponent" -> ordered += marker("dadGroup")
            }
        }
        while (objectIndex < objects.size) ordered += objects[objectIndex++]
        listOf("gfGroup", "dadGroup", "boyfriendGroup").forEach { type -> if (ordered.none { it.type == type }) ordered += marker(type) }

        if (root.has("startCamPosX") || root.has("startCamPosY")) {
            notes += "Start camera position (${root.attr("startCamPosX") ?: "-"}, ${root.attr("startCamPosY") ?: "-"}) is not stored in V-Slice stages; the camera starts on the first focus event."
        }
        return CodenameStage(
            id = id,
            name = root.attr("name")?.takeIf { it.isNotBlank() } ?: id,
            zoom = root.double("zoom") ?: 1.05,
            folder = folder,
            objects = ordered,
            spots = spots,
            spriteNames = spriteNames,
            extensions = extensions,
            notes = notes,
            warnings = warnings,
            beats = beats,
            properties = properties,
            blends = blends,
            zoomFactors = zoomFactors,
            startCamera = if (root.has("startCamPosX") || root.has("startCamPosY")) root.double("startCamPosX") to root.double("startCamPosY") else null,
            ratings = ratings,
            extraCharacters = extraSpots
        )
    }

    private fun properties(node: XmlNode, name: String): List<CodenameProperty> =
        node.elements("property").mapNotNull { p ->
            val path = p.attr("name") ?: return@mapNotNull null
            val type = p.attr("type") ?: return@mapNotNull null
            val value = p.attr("value") ?: return@mapNotNull null
            if (path == "alpha") null else CodenameProperty(name, path, type.lowercase(), value)
        }

    private fun beatSprite(node: XmlNode, name: String): CodenameBeatSprite? {
        val spriteType = (node.attr("type") ?: "loop").trim().lowercase()
        val animNodes = node.elements("anim")
        val anims = animNodes.filter { a ->
            val type = (a.attr("type") ?: spriteType).trim().lowercase()
            type == "beat" || type == "onbeat"
        }.mapNotNull { it.attr("name") }
        val list = if (animNodes.isEmpty() && (spriteType == "beat" || spriteType == "onbeat")) listOf("idle") else anims
        if (list.isEmpty()) return null
        val interval = (node.int("beatInterval") ?: node.int("interval") ?: 2).coerceAtLeast(1)
        return CodenameBeatSprite(name, interval, node.int("beatOffset") ?: 0, list, node.bool("playOnCountdown") ?: false)
    }

    private fun marker(type: String) = PsychStageObject(
        type = type, name = type, image = "", x = 0.0, y = 0.0, scale = PsychPoint(1.0, 1.0), scroll = PsychPoint(1.0, 1.0),
        alpha = 1.0, angle = 0.0, color = "FFFFFF", antialiasing = true, flipX = false, flipY = false, filters = 3,
        animations = emptyList(), firstAnimation = ""
    )

    private fun property(node: XmlNode, name: String): String? =
        node.elements("property").firstOrNull { it.attr("name") == name }?.attr("value")

    private fun scroll(node: XmlNode): PsychPoint {
        val both = node.double("scroll")
        return PsychPoint(node.double("scrollx") ?: both ?: 1.0, node.double("scrolly") ?: both ?: 1.0)
    }

    private fun spot(node: XmlNode, slot: String): CodenameCharacterSpot {
        val base = defaults.getValue(slot)
        val both = node.double("scroll")
        return CodenameCharacterSpot(
            slot = slot,
            x = node.double("x") ?: base.x,
            y = node.double("y") ?: base.y,
            scroll = PsychPoint(node.double("scrollx") ?: both ?: base.scroll.x, node.double("scrolly") ?: both ?: base.scroll.y),
            scale = node.double("scale") ?: 1.0,
            alpha = node.double("alpha") ?: 1.0,
            angle = node.double("angle") ?: 0.0,
            cameraX = node.double("camxoffset") ?: 0.0,
            cameraY = node.double("camyoffset") ?: 0.0,
            flip = if (node.has("flip") || node.has("flipX")) (node.bool("flip") == true || node.bool("flipX") == true) else null
        )
    }

    private fun sprite(node: XmlNode, name: String, folder: String, images: (String) -> ImageInfo?, warnings: MutableList<String>): PsychStageObject {
        val image = folder + (node.attr("sprite")?.takeIf { it.isNotBlank() } ?: name)
        val info = images(image)
        val type = (node.attr("type") ?: "loop").trim().lowercase()
        val both = node.double("scale")
        val scaleX = node.double("scalex") ?: both ?: 1.0
        val scaleY = node.double("scaley") ?: both ?: 1.0
        val updateHitbox = node.bool("updateHitbox") == true
        var x = node.double("x") ?: 0.0
        var y = node.double("y") ?: 0.0
        if (!updateHitbox && (scaleX != 1.0 || scaleY != 1.0)) {
            if (info != null) {
                x += info.width * (1.0 - scaleX) / 2.0
                y += info.height * (1.0 - scaleY) / 2.0
            } else {
                warnings += "\"$name\" is scaled without updateHitbox; Codename scales it from the center. Add the image so V-Converter can correct its position, or move it a bit by hand."
            }
        }
        val animNodes = node.elements("anim")
        val anims = animNodes.mapNotNull { a ->
            val animName = a.attr("name") ?: return@mapNotNull null
            PsychStageAnimation(
                anim = animName,
                prefix = a.attr("anim") ?: "",
                fps = (a.double("fps") ?: 24.0).toInt().coerceAtLeast(1),
                loop = a.bool("loop") ?: false,
                indices = a.attr("indices")?.let { XmlNode.numberRange(it) } ?: emptyList(),
                offsetX = (a.double("x") ?: 0.0) * scaleX,
                offsetY = (a.double("y") ?: 0.0) * scaleY
            )
        }.ifEmpty {
            if (info?.animated == true) listOf(PsychStageAnimation("idle", "", 24, type == "loop", emptyList(), 0.0, 0.0)) else emptyList()
        }
        if (node.double("zoomfactor")?.let { it != 1.0 } == true) warnings += "\"$name\" uses zoomfactor ${node.attr("zoomfactor")}, which V-Slice does not have; it now zooms with the camera."
        if (node.has("skewx") || node.has("skewy")) warnings += "\"$name\" is skewed; V-Slice props cannot skew."
        val beat = type == "beat" || type == "onbeat"
        val interval = (node.int("beatInterval") ?: node.int("interval") ?: 2).coerceAtLeast(1)
        if (beat && anims.isNotEmpty() && anims.none { it.anim == "idle" || it.anim == "danceLeft" }) {
            warnings += "\"$name\" bops on beat with \"${anims.first().anim}\"; V-Slice props bop with \"idle\" or \"danceLeft\"/\"danceRight\", so rename that animation."
        }
        val alpha = property(node, "alpha")?.toDoubleOrNull() ?: node.double("alpha") ?: 1.0
        return PsychStageObject(
            type = if (anims.isEmpty()) "sprite" else "animatedSprite",
            name = name,
            image = image,
            x = x,
            y = y,
            scale = PsychPoint(scaleX, scaleY),
            scroll = scroll(node),
            alpha = alpha,
            angle = node.double("angle") ?: 0.0,
            color = node.attr("color") ?: "FFFFFF",
            antialiasing = node.bool("antialiasing") ?: true,
            flipX = node.bool("flipX") ?: false,
            flipY = node.bool("flipY") ?: false,
            filters = PsychStageParser.FILTER_LOW_QUALITY or PsychStageParser.FILTER_HIGH_QUALITY,
            animations = anims,
            firstAnimation = when {
                anims.isEmpty() -> ""
                beat -> anims.firstOrNull { it.anim == "danceLeft" }?.anim ?: anims.first().anim
                type == "loop" -> anims.last().anim
                else -> anims.first().anim
            },
            blend = node.attr("blend")?.lowercase() ?: "",
            danceEvery = if (beat) interval else 0
        )
    }
}

object CodenameToVSliceStage {
    fun convert(stage: CodenameStage): StageResult {
        val psych = PsychStage(
            directory = "shared",
            defaultZoom = stage.zoom,
            isPixelStage = stage.id.lowercase().startsWith("school"),
            stageUI = "",
            boyfriend = PsychPoint(stage.spots.getValue("bf").x, stage.spots.getValue("bf").y),
            girlfriend = PsychPoint(stage.spots.getValue("gf").x, stage.spots.getValue("gf").y),
            opponent = PsychPoint(stage.spots.getValue("dad").x, stage.spots.getValue("dad").y),
            hideGirlfriend = false,
            cameraBoyfriend = PsychPoint(stage.spots.getValue("bf").cameraX, stage.spots.getValue("bf").cameraY),
            cameraOpponent = PsychPoint(stage.spots.getValue("dad").cameraX, stage.spots.getValue("dad").cameraY),
            cameraGirlfriend = PsychPoint(stage.spots.getValue("gf").cameraX, stage.spots.getValue("gf").cameraY),
            cameraSpeed = 1.0,
            preload = emptyList(),
            objects = stage.objects
        )
        val base = PsychToVSliceStage.convert(StageSource(stage.id, psych, stage.name))
        val json = JSONObject(base.json)
        val characters = json.getJSONObject("characters")
        stage.spots.forEach { (key, spot) ->
            val entry = characters.optJSONObject(key) ?: return@forEach
            entry.remove("scroll")
            if (spot.scroll.x != 1.0 || spot.scroll.y != 1.0) entry.put("scroll", JSONArray(listOf(spot.scroll.x.toJsonNumber(), spot.scroll.y.toJsonNumber())))
            if (spot.scale != 1.0) entry.put("scale", spot.scale.toJsonNumber())
            if (spot.alpha != 1.0) entry.put("alpha", spot.alpha.toJsonNumber())
            if (spot.angle != 0.0) entry.put("angle", spot.angle.toJsonNumber())
        }
        val report = ReportBuilder()
        base.report.stats.forEach { report.stat(it.label, it.value) }
        base.report.sections.filterNot { it.title == "Notes" }.forEach { report.section(it.title, it.lines) }
        base.report.warnings.filterNot { it.contains("Psych hides") || it.contains("pixel stage") }.forEach(report::warn)
        stage.warnings.forEach(report::warn)
        val flips = stage.spots.values.filter { spot -> spot.flip != null && spot.flip != (spot.slot == "bf") }
        if (flips.isNotEmpty()) report.warn("Flipped character slots (${flips.joinToString { it.slot }}) are not supported by V-Slice stages; flip the character JSON instead.")
        report.section("Notes", stage.notes + "Character positions were rebased to V-Slice feet anchors; characters converted with V-Converter line up with their Codename spot.")
        return StageResult(stage.id, json.toString(2), report.build())
    }
}

