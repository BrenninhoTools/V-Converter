package com.brenninhotools.vconverter.reverse

import com.brenninhotools.vconverter.core.AnimateAtlas
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.json.toJsonNumber
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ReverseResult(
    val path: String,
    val json: String,
    val report: Report,
    val extra: List<Pair<String, String>> = emptyList(),
    val copies: List<Pair<String, String>> = emptyList()
)

object VSliceToPsychCharacter {
    private val defaultColors = mapOf(
        CharacterSlot.PLAYER to listOf(49, 176, 209),
        CharacterSlot.OPPONENT to listOf(175, 102, 206),
        CharacterSlot.GIRLFRIEND to listOf(165, 0, 77)
    )

    fun defaultSlot(id: String): CharacterSlot = when {
        id.lowercase().startsWith("gf") -> CharacterSlot.GIRLFRIEND
        id.lowercase().startsWith("bf") || id.lowercase().startsWith("pico-playable") -> CharacterSlot.PLAYER
        else -> CharacterSlot.OPPONENT
    }

    fun atlasKey(path: String): String = path.trim().replace('\\', '/').substringAfterLast('/').substringBeforeLast(".png").lowercase()

    fun convert(id: String, character: VCharacter, slot: CharacterSlot, atlases: Map<String, SparrowAtlas>, animate: AnimateAtlas? = null): ReverseResult {
        if (animate != null && character.renderType.equals("animateatlas", ignoreCase = true)) return convertAtlas(id, character, slot, animate)
        val report = ReportBuilder()
        val s = character.scale
        val images = (listOf(character.assetPath) + character.animations.map { it.assetPath }).filter { it.isNotBlank() }.distinct()
        val main = images.firstOrNull() ?: "characters/$id"
        val idle = character.animations.firstOrNull { it.name == "danceLeft" }
            ?: character.animations.firstOrNull { it.name == character.startingAnimation }
            ?: character.animations.firstOrNull { it.name == "idle" }
            ?: character.animations.firstOrNull()
        val firstFrame = atlases[atlasKey(main)]?.firstFrame
        val idleFrame = idle?.let { anim -> images.firstNotNullOfOrNull { atlases[atlasKey(it)]?.firstFrameOf(anim.prefix, anim.indices) } }
        val exact = firstFrame != null && idleFrame != null
        val fallbackWidth = slot.stageAnchorX * 2.0
        val fallbackHeight = slot.stageAnchorY * 2.0 / (1.0 + s)
        val fw0 = firstFrame?.sourceWidth ?: idleFrame?.sourceWidth ?: fallbackWidth
        val fh0 = firstFrame?.sourceHeight ?: idleFrame?.sourceHeight ?: fallbackHeight
        val fwi = idleFrame?.sourceWidth ?: fw0
        val fhi = idleFrame?.sourceHeight ?: fh0
        val idleX = idle?.offsetX ?: 0.0
        val idleY = idle?.offsetY ?: 0.0

        val posX = slot.stageAnchorX - s * fwi / 2.0 - (1.0 - s) * fw0 / 2.0 - s * idleX + character.offsetX
        val posY = slot.stageAnchorY - s * fhi - (1.0 - s) * fh0 / 2.0 - s * idleY + character.offsetY
        val camX = slot.cameraSign * (character.cameraX - posX + slot.stageAnchorX - s * fw0 / 2.0)
        val camY = character.cameraY - posY + slot.stageAnchorY - s * fh0 / 2.0 - s * fhi / 2.0

        val animations = JSONArray()
        character.animations.forEach { anim ->
            animations.put(
                JSONObject()
                    .put("offsets", JSONArray(listOf(((anim.offsetX - idleX) * s).toJsonNumber(), ((anim.offsetY - idleY) * s).toJsonNumber())))
                    .put("loop", anim.looped)
                    .put("fps", anim.frameRate)
                    .put("anim", if (anim.name.endsWith("-hold")) anim.name.removeSuffix("-hold") + "-loop" else anim.name)
                    .put("indices", JSONArray(anim.indices))
                    .put("name", anim.prefix)
            )
        }
        val root = JSONObject()
            .put("animations", animations)
            .put("image", images.joinToString(","))
            .put("position", JSONArray(listOf(posX.toJsonNumber(), posY.toJsonNumber())))
            .put("healthicon", character.healthIcon.ifEmpty { id })
            .put("flip_x", character.flipX)
            .put("healthbar_colors", JSONArray(defaultColors.getValue(slot)))
            .put("camera_position", JSONArray(listOf(camX.toJsonNumber(), camY.toJsonNumber())))
            .put("sing_duration", (character.singTime / 1.1).toJsonNumber())
            .put("scale", s.toJsonNumber())
            .put("no_antialiasing", character.isPixel)
            .put("vocals_file", "")
            .put("_editor_isPlayer", slot == CharacterSlot.PLAYER)

        report.stat("Animations", character.animations.size)
        report.stat("Slot", slot.label)
        report.stat("Accuracy", if (exact) "Exact" else "Approximate")
        if (!exact) report.warn("Add the spritesheet XML for an exact position and camera; without it they are approximate.")
        if (character.renderType !in setOf("sparrow", "multisparrow")) {
            report.warn("Render type \"${character.renderType}\" is not a Sparrow sheet; Psych 1.0 loads texture atlases from the same image field, check the animations in the Character Editor.")
        }
        if (character.animations.any { it.flipX }) report.warn("Per-animation flipX has no Psych equivalent.")
        report.section(
            "Notes",
            listOf(
                "Health bar color set to the default ${slot.label.lowercase(Locale.US)} color; change it in Psych's Character Editor.",
                "Positions were rebased for the ${slot.label.lowercase(Locale.US)} slot."
            )
        )
        return ReverseResult("characters/$id.json", root.toString(2), report.build())
    }

    fun exactSymbol(atlas: AnimateAtlas, name: String): String =
        if (atlas.symbols.keys.any { it != name && it.startsWith(name) }) "$name\\" else name

    private fun convertAtlas(id: String, character: VCharacter, slot: CharacterSlot, atlas: AnimateAtlas): ReverseResult {
        val report = ReportBuilder()
        val s = character.scale
        val idle = character.animations.firstOrNull { it.name == "danceLeft" }
            ?: character.animations.firstOrNull { it.name == character.startingAnimation }
            ?: character.animations.firstOrNull { it.name == "idle" }
            ?: character.animations.firstOrNull()
        val first = atlas.firstSprite
        val w0 = first?.first ?: slot.stageAnchorX
        val h0 = first?.second ?: slot.stageAnchorY
        val missing = mutableListOf<String>()

        data class Resolved(val psychName: String, val indices: List<Int>, val boundsX: Double, val boundsY: Double)

        fun resolve(anim: VAnimation): Resolved? {
            val label = if (anim.animType.equals("symbol", ignoreCase = true)) null else atlas.findLabel(anim.prefix)
            if (label != null) {
                val frames = if (anim.indices.isNotEmpty()) anim.indices.map { label.start + it } else (label.start until label.start + label.duration).toList()
                return Resolved(exactSymbol(atlas, atlas.mainSymbol), frames, atlas.mainBounds.x, atlas.mainBounds.y)
            }
            val symbol = atlas.symbols[anim.prefix] ?: atlas.findSymbol(anim.prefix) ?: return null
            return Resolved(exactSymbol(atlas, symbol.name), anim.indices, symbol.bounds.x, symbol.bounds.y)
        }

        val idleResolved = idle?.let(::resolve)
        val idleX = (idle?.offsetX ?: 0.0) - character.offsetX + (idleResolved?.boundsX ?: 0.0)
        val idleY = (idle?.offsetY ?: 0.0) - character.offsetY + (idleResolved?.boundsY ?: 0.0)
        val posX = slot.stageAnchorX - s * w0 / 2.0 - s * idleX + character.offsetX
        val posY = slot.stageAnchorY - s * h0 - s * idleY + character.offsetY
        val camX = slot.cameraSign * (character.cameraX - posX + slot.stageAnchorX)
        val camY = character.cameraY - posY + slot.stageAnchorY - s * h0 / 2.0

        val animations = JSONArray()
        character.animations.forEach { anim ->
            val resolved = resolve(anim)
            if (resolved == null) missing += "${anim.name} (\"${anim.prefix}\")"
            val ox = anim.offsetX - character.offsetX + (resolved?.boundsX ?: 0.0)
            val oy = anim.offsetY - character.offsetY + (resolved?.boundsY ?: 0.0)
            animations.put(
                JSONObject()
                    .put("offsets", JSONArray(listOf(((ox - idleX) * s).toJsonNumber(), ((oy - idleY) * s).toJsonNumber())))
                    .put("loop", anim.looped)
                    .put("fps", anim.frameRate)
                    .put("anim", if (anim.name.endsWith("-hold")) anim.name.removeSuffix("-hold") + "-loop" else anim.name)
                    .put("indices", JSONArray(resolved?.indices ?: anim.indices))
                    .put("name", resolved?.psychName ?: anim.prefix)
            )
        }
        val root = JSONObject()
            .put("animations", animations)
            .put("image", character.assetPath)
            .put("position", JSONArray(listOf(posX.toJsonNumber(), posY.toJsonNumber())))
            .put("healthicon", character.healthIcon.ifEmpty { id })
            .put("flip_x", character.flipX)
            .put("healthbar_colors", JSONArray(defaultColors.getValue(slot)))
            .put("camera_position", JSONArray(listOf(camX.toJsonNumber(), camY.toJsonNumber())))
            .put("sing_duration", (character.singTime / 1.1).toJsonNumber())
            .put("scale", s.toJsonNumber())
            .put("no_antialiasing", character.isPixel)
            .put("vocals_file", "")
            .put("_editor_isPlayer", slot == CharacterSlot.PLAYER)

        report.stat("Animations", character.animations.size)
        report.stat("Render", "Animate atlas")
        report.stat("Slot", slot.label)
        report.stat("Accuracy", if (first != null && missing.isEmpty()) "Exact" else "Approximate")
        if (missing.isNotEmpty()) report.warn("These animations match no symbol or frame label in Animation.json: ${missing.joinToString()}.")
        if (first == null) report.warn("No spritemap JSON was found next to Animation.json, so the position is approximate.")
        report.section(
            "Animate atlas",
            listOf(
                "Frame label animations became indices of the main symbol (Psych only plays symbols); symbol names end with \"\\\" where Psych's prefix match would pick the wrong symbol.",
                "Offsets include each symbol's bounds, so the atlas lands where V-Slice draws it.",
                "Psych loads the atlas from images/${character.assetPath}/ (Animation.json + spritemap)."
            )
        )
        report.section("Notes", listOf("Health bar color set to the default ${slot.label.lowercase(Locale.US)} color; change it in Psych's Character Editor."))
        return ReverseResult("characters/$id.json", root.toString(2), report.build())
    }
}

object VSliceToPsychStage {
    private const val PSYCH_GF_SCROLL = 0.95

    private fun lua(text: String) = "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"

    private fun num(value: Double): String = value.toJsonNumber().toString()

    fun convert(id: String, stage: VStage, atlas: (String) -> SparrowAtlas? = { null }): ReverseResult {
        val report = ReportBuilder()
        val objects = mutableListOf<Pair<Int, JSONObject>>()
        val beats = mutableListOf<String>()
        val hideGf = stage.girlfriend.alpha <= 0.0
        val markers = listOfNotNull(
            if (hideGf) null else stage.girlfriend.zIndex to "gfGroup",
            stage.dad.zIndex to "dadGroup",
            stage.boyfriend.zIndex to "boyfriendGroup"
        )
        val setup = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var pixel = false
        var bops = 0
        var fixedOffsets = 0
        val used = HashSet<String>()
        stage.props.forEachIndexed { index, prop ->
            var name = prop.name.ifBlank { "prop$index" }
            var n = 2
            while (!used.add(name)) name = "${prop.name.ifBlank { "prop$index" }}${n++}"
            val obj = JSONObject()
            val solid = prop.assetPath.startsWith("#")
            val animated = prop.animations.isNotEmpty() && !solid
            obj.put("type", if (solid) "square" else if (animated) "animatedSprite" else "sprite")
            obj.put("name", name)
            obj.put("x", prop.x.toJsonNumber())
            obj.put("y", prop.y.toJsonNumber())
            obj.put("scale", JSONArray(listOf(prop.scaleX.toJsonNumber(), prop.scaleY.toJsonNumber())))
            obj.put("scroll", JSONArray(listOf(prop.scrollX.toJsonNumber(), prop.scrollY.toJsonNumber())))
            obj.put("alpha", prop.alpha.toJsonNumber())
            obj.put("angle", prop.angle.toJsonNumber())
            obj.put("color", colorHex(if (solid) prop.assetPath else prop.color))
            obj.put("filters", 3)
            if (!solid) {
                obj.put("image", prop.assetPath)
                obj.put("flipX", prop.flipX)
                obj.put("flipY", prop.flipY)
                obj.put("antialiasing", !prop.isPixel)
                if (prop.isPixel) pixel = true
            } else {
                obj.put("antialiasing", false)
            }
            if (prop.blend.isNotBlank() && !prop.blend.equals("normal", true)) setup += "setBlendMode(${lua(name)}, ${lua(prop.blend.lowercase())})"
            if (animated) {
                val scaled = prop.scaleX != 1.0 || prop.scaleY != 1.0
                val start = prop.startingAnimation.takeIf { s -> prop.animations.any { it.name == s } }
                val first = if (scaled) atlas(prop.assetPath)?.firstFrame else null
                val hitboxX = if (first != null) 0.5 * first.sourceWidth * (1 - prop.scaleX) else 0.0
                val hitboxY = if (first != null) 0.5 * first.sourceHeight * (1 - prop.scaleY) else 0.0
                val anims = JSONArray()
                val packer = prop.animType == "packer"
                prop.animations.forEach { anim ->
                    if (packer) {
                        setup += "addAnimation(${lua(name)}, ${lua(anim.name)}, {${anim.indices.joinToString(", ")}}, ${anim.frameRate}, ${anim.looped})"
                        setup += "addOffset(${lua(name)}, ${lua(anim.name)}, ${num(round(hitboxX + anim.offsetX * prop.scaleX))}, ${num(round(hitboxY + anim.offsetY * prop.scaleY))})"
                        return@forEach
                    }
                    anims.put(
                        JSONObject()
                            .put("anim", if (anim.name.endsWith("-hold")) anim.name.removeSuffix("-hold") + "-loop" else anim.name)
                            .put("name", anim.prefix)
                            .put("fps", anim.frameRate)
                            .put("loop", anim.looped)
                            .put("indices", JSONArray(anim.indices))
                            .put("offsets", JSONArray(listOf(round(hitboxX + anim.offsetX * prop.scaleX).toJsonNumber(), round(hitboxY + anim.offsetY * prop.scaleY).toJsonNumber())))
                    )
                }
                obj.put("animations", anims)
                obj.put("firstAnimation", start ?: prop.animations.first().name)
                if (start == null && prop.danceEvery <= 0.0) setup += "setProperty(${lua("$name.animation.paused")}, true)"
                if (packer) {
                    setup += "playAnim(${lua(name)}, ${lua(start ?: prop.animations.first().name)}, true)"
                    notes += "\"$name\" uses a Packer (.txt) spritesheet: its frame-index animations are added by stages/$id.lua, since Psych stage files only take prefix animations."
                } else if (prop.animType == "animateatlas") {
                    report.warn("\"$name\" is an Adobe Animate texture atlas; Psych stage objects cannot load those, so it needs a FlxAnimate sprite made in a script.")
                } else if (prop.animType != "sparrow") {
                    report.warn("\"$name\" uses ${prop.animType} frames; check it in Psych's Stage Editor.")
                }
                if (scaled && !packer) {
                    fixedOffsets++
                    if (first == null) {
                        setup += "local ${luaVar(name, "x")}, ${luaVar(name, "y")} = getProperty(${lua("$name.offset.x")}), getProperty(${lua("$name.offset.y")})"
                        prop.animations.forEach { anim ->
                            setup += "addOffset(${lua(name)}, ${lua(anim.name)}, ${luaVar(name, "x")} + ${num(anim.offsetX * prop.scaleX)}, ${luaVar(name, "y")} + ${num(anim.offsetY * prop.scaleY)})"
                        }
                    }
                    if (start != null) setup += "playAnim(${lua(name)}, ${lua(start)}, true)"
                }
                if (prop.danceEvery > 0.0) {
                    val integral = prop.danceEvery == Math.floor(prop.danceEvery)
                    val alternate = prop.animations.any { it.name == "danceLeft" } && prop.animations.any { it.name == "danceRight" }
                    if (alternate && integral && prop.danceEvery == 1.0) {
                        beats += "if curBeat % 2 == 0 then"
                        beats += "\tplayAnim(${lua(name)}, 'danceLeft', false)"
                        beats += "else"
                        beats += "\tplayAnim(${lua(name)}, 'danceRight', false)"
                        beats += "end"
                        bops++
                    } else if (alternate) {
                        val every = num(prop.danceEvery * 4.0)
                        steps += "if curStep % $every == 0 then"
                        steps += "\tdanced[${lua(name)}] = not danced[${lua(name)}]"
                        steps += "\tplayAnim(${lua(name)}, danced[${lua(name)}] and 'danceLeft' or 'danceRight', false)"
                        steps += "end"
                        bops++
                    } else if (prop.animations.any { it.name == "idle" }) {
                        if (integral) beats += "if curBeat % ${num(prop.danceEvery)} == 0 then playAnim(${lua(name)}, 'idle', false) end"
                        else steps += "if curStep % ${num(prop.danceEvery * 4.0)} == 0 then playAnim(${lua(name)}, 'idle', false) end"
                        bops++
                    } else {
                        report.warn("\"$name\" dances every ${num(prop.danceEvery)} beat(s) in V-Slice but has no idle or danceLeft/danceRight animation, so it does not bop there either.")
                    }
                }
            }
            objects += prop.zIndex * 10 to obj
        }
        markers.forEachIndexed { i, (z, type) -> objects += z * 10 + 5 + i to JSONObject().put("type", type) }
        val ordered = JSONArray(objects.sortedBy { it.first }.map { it.second })

        fun characterSetup(c: VStageCharacter, tag: String, defaultScroll: Double) {
            if (c.scroll != defaultScroll || c.scrollY != defaultScroll) {
                setup += "setProperty(${lua("$tag.scrollFactor.x")}, ${num(c.scroll)})"
                setup += "setProperty(${lua("$tag.scrollFactor.y")}, ${num(c.scrollY)})"
            }
            if (c.alpha != 1.0) setup += "setProperty(${lua("$tag.alpha")}, ${num(c.alpha)})"
            if (c.angle != 0.0) setup += "setProperty(${lua("$tag.angle")}, ${num(c.angle)})"
            if (c.scale != 1.0) report.warn("The $tag is scaled by ${num(c.scale)} on this stage; Psych stages cannot scale characters, so scale the character JSON instead.")
        }
        characterSetup(stage.boyfriend, "boyfriend", 1.0)
        characterSetup(stage.dad, "dad", 1.0)
        if (!hideGf) characterSetup(stage.girlfriend, "gf", PSYCH_GF_SCROLL)

        fun pos(c: VStageCharacter, slot: CharacterSlot) = JSONArray(listOf((c.x - slot.stageAnchorX).toJsonNumber(), (c.y - slot.stageAnchorY).toJsonNumber()))
        fun cam(c: VStageCharacter, slot: CharacterSlot) = JSONArray(listOf((c.cameraX - slot.cameraBaseX).toJsonNumber(), (c.cameraY - slot.cameraBaseY).toJsonNumber()))
        val root = JSONObject()
            .put("directory", "")
            .put("defaultZoom", stage.cameraZoom.toJsonNumber())
            .put("isPixelStage", pixel)
            .put("stageUI", if (pixel) "pixel" else "normal")
            .put("boyfriend", pos(stage.boyfriend, CharacterSlot.PLAYER))
            .put("girlfriend", pos(stage.girlfriend, CharacterSlot.GIRLFRIEND))
            .put("opponent", pos(stage.dad, CharacterSlot.OPPONENT))
            .put("hide_girlfriend", hideGf)
            .put("camera_boyfriend", cam(stage.boyfriend, CharacterSlot.PLAYER))
            .put("camera_opponent", cam(stage.dad, CharacterSlot.OPPONENT))
            .put("camera_girlfriend", cam(stage.girlfriend, CharacterSlot.GIRLFRIEND))
            .put("camera_speed", 1)
            .put("objects", ordered)

        val extra = mutableListOf<Pair<String, String>>()
        if (setup.isNotEmpty() || steps.isNotEmpty() || beats.isNotEmpty()) {
            val script = StringBuilder()
            fun block(header: String, lines: List<String>) {
                if (lines.isEmpty()) return
                if (script.isNotEmpty()) script.append('\n')
                script.append(header).append('\n')
                lines.forEach { script.append('\t').append(it).append('\n') }
                script.append("end\n")
            }
            if (steps.any { it.contains("danced[") }) script.append("local danced = {}\n")
            block("function onCreatePost()", setup)
            block("function onBeatHit()", beats)
            block("function onStepHit()", steps)
            extra += "stages/$id.lua" to script.toString()
            val parts = mutableListOf<String>()
            if (bops > 0) parts += "$bops prop(s) bop on the beat like in V-Slice"
            if (fixedOffsets > 0) parts += "$fixedOffsets scaled animated prop(s) get V-Slice's offset math"
            if (setup.any { it.startsWith("setBlendMode") }) parts += "blend modes are applied"
            if (setup.any { it.contains("scrollFactor") || it.contains(".alpha") || it.contains(".angle") }) parts += "character scroll, alpha and angle match the V-Slice stage"
            notes += "stages/$id.lua was generated: " + parts.joinToString("; ") + "."
        }
        report.stat("Objects", stage.props.size)
        report.stat("Zoom", stage.cameraZoom.toJsonNumber().toString())
        if (stage.directory.isNotBlank() && stage.directory != "shared") notes += "V-Slice asset library \"${stage.directory}\": Psych reads the images from the mod's images folder, so copy them there if they came from that library."
        if (hideGf) notes += "The girlfriend is invisible on this stage, so hide_girlfriend is on."
        notes += "Props, layer order, scroll, scale, alpha, angle, color, flip and animations were carried over; tweak it further in Psych's Stage Editor."
        report.section("Notes", notes)
        return ReverseResult("stages/$id.json", root.toString(2), report.build(), extra)
    }

    private fun round(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private fun luaVar(name: String, axis: String): String = "o_" + name.replace(Regex("[^A-Za-z0-9_]"), "_") + "_" + axis

    private fun colorHex(raw: String): String {
        val text = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        return when {
            text.length == 8 && text.all { it.isLetterOrDigit() } -> text.substring(2).uppercase()
            text.length == 6 -> text.uppercase()
            else -> "FFFFFF"
        }
    }
}

data class LevelSongInfo(val name: String, val icon: String, val difficulties: List<String>)

object VSliceToPsychLevel {
    private const val SLOT_WIDTH = 320.0
    private const val PSYCH_SLOT_SHIFT = 150.0
    private const val PSYCH_Y = 70.0
    private val baseProps = mapOf("storymenu/props/dad" to "dad", "storymenu/props/bf" to "bf", "storymenu/props/gf" to "gf")

    fun convert(id: String, level: VLevel, songs: Map<String, LevelSongInfo>): ReverseResult {
        val report = ReportBuilder()
        val extra = mutableListOf<Pair<String, String>>()
        val copies = mutableListOf<Pair<String, String>>()
        val characters = mutableListOf("", "", "")
        level.props.take(3).forEachIndexed { index, prop ->
            val base = baseProps[prop.assetPath.lowercase()]
            val slot = when (base) {
                "dad" -> 0
                "bf" -> 1
                "gf" -> 2
                else -> index
            }
            if (base != null) {
                characters[slot] = base
                return@forEachIndexed
            }
            if (prop.assetPath.isBlank()) return@forEachIndexed
            val image = prop.assetPath.substringAfterLast('/')
            val name = image.lowercase().replace(Regex("[^a-z0-9_-]+"), "-")
            val idle = prop.animations.firstOrNull { it.name == "idle" } ?: prop.animations.firstOrNull()
            val confirm = prop.animations.firstOrNull { it.name == "confirm" }
            val x = SLOT_WIDTH * (1 + slot) - PSYCH_SLOT_SHIFT - SLOT_WIDTH * index - prop.offsetX
            val y = PSYCH_Y - prop.offsetY
            val menu = JSONObject()
                .put("image", image)
                .put("scale", (if (prop.isPixel) prop.scale * 6.0 else prop.scale).toJsonNumber())
                .put("position", JSONArray(listOf(x.toJsonNumber(), y.toJsonNumber())))
                .put("idle_anim", idle?.prefix ?: "idle")
                .put("confirm_anim", confirm?.prefix ?: idle?.prefix ?: "idle")
                .put("flipX", prop.flipX)
                .put("antialiasing", !prop.isPixel)
            extra += "images/menucharacters/$name.json" to menu.toString(2)
            if (!prop.assetPath.startsWith("menucharacters/")) {
                copies += "images/${prop.assetPath}.png" to "images/menucharacters/$image.png"
                copies += "images/${prop.assetPath}.xml" to "images/menucharacters/$image.xml"
            }
            characters[slot] = name
        }
        val songArray = JSONArray()
        val allDiffs = linkedSetOf<String>()
        level.songs.forEach { songId ->
            val info = songs[songId]
            songArray.put(JSONArray().put(info?.name ?: songId).put(info?.icon ?: "face").put(JSONArray(listOf(146, 113, 253))))
            info?.difficulties?.let { allDiffs += it }
            if (info == null) report.warn("No metadata for \"$songId\"; its freeplay icon is \"face\". Add the song's metadata file for its name and icon.")
        }
        val diffs = allDiffs.toList()
        val custom = diffs.isNotEmpty() && diffs.toSet() != setOf("easy", "normal", "hard")
        val root = JSONObject()
            .put("songs", songArray)
            .put("weekCharacters", JSONArray(characters))
            .put("weekBackground", "stage")
            .put("weekBefore", "")
            .put("storyName", level.name)
            .put("weekName", level.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) })
            .put("startUnlocked", true)
            .put("hiddenUntilUnlocked", false)
            .put("hideStoryMode", !level.visible)
            .put("hideFreeplay", false)
            .put("difficulties", if (custom) diffs.joinToString(",") { it.replaceFirstChar { c -> c.titlecase(Locale.US) } } else "")
        report.stat("Songs", level.songs.size)
        report.stat("Menu characters", characters.count { it.isNotEmpty() })
        report.section("Title image", listOf("V-Slice: images/${level.titleAsset}.png", "Psych: images/storymenu/$id.png"))
        if (copies.isNotEmpty()) report.section("Menu character images to copy", copies.filter { it.first.endsWith(".png") }.map { "${it.first} → ${it.second}" })
        copies += "images/${level.titleAsset}.png" to "images/storymenu/$id.png"
        if (!level.background.equals("#F9CF51", ignoreCase = true)) report.warn("Psych week backgrounds are images (images/menubackgrounds/menu_<name>.png); the color ${level.background} was not carried over.")
        return ReverseResult("weeks/$id.json", root.toString(2), report.build(), extra, copies)
    }
}
