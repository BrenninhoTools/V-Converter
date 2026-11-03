package com.brenninhotools.vconverter.fpsplus

import com.brenninhotools.vconverter.core.fmt
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import kotlin.math.roundToInt

object FpsNames {
    val builtinCharacters = setOf(
        "Bf", "BfCar", "BfChristmas", "BfDark", "BfDead", "BfHoldingGf", "BfHoldingGfDead", "BfLil", "BfLilErect", "BfPixel", "BfPixelDead", "Dad",
        "Darnell", "DarnellBlazin", "Gf", "GfCar", "GfChristmas", "GfDark", "GfPixel", "GfTankmen", "GuyLil", "GuyLilErect", "Mom", "MomCar",
        "Monster", "MonsterChristmas", "Nene", "NeneChristmas", "NeneDark", "NeneNoSpeaker", "NenePixel", "NeneTankmen", "OtisSpeaker",
        "ParentsChristmas", "Pico", "PicoBlazin", "PicoChristmas", "PicoChristmasDead", "PicoCutscene", "PicoDark", "PicoDead", "PicoDeadExplode",
        "PicoHoldingNene", "PicoHoldingNeneDead", "PicoLil", "PicoPixel", "PicoPlayer", "PicoSpeaker", "PicoWeekend", "PicoWeekendDead", "Senpai",
        "SenpaiAngry", "Spirit", "Spooky", "SpookyDark", "Tankman", "TankmanBloody"
    )

    val builtinStageClasses = setOf(
        "Buddies", "BuddiesErect", "BuddiesPico", "EmptyStage", "Limo", "LimoErect", "Mall", "MallErect", "MallEvil", "Philly", "PhillyBlazin",
        "PhillyErect", "PhillyStreets", "PhillyStreetsErect", "School", "SchoolErect", "SchoolEvil", "SchoolEvilErect", "SpookyMansion",
        "SpookyMansionErect", "Stage", "StageErect", "Tank", "TankErect"
    )

    val psychStages = mapOf(
        "stage" to "Stage", "spooky" to "SpookyMansion", "philly" to "Philly", "limo" to "Limo", "mall" to "Mall", "mallEvil" to "MallEvil",
        "school" to "School", "schoolEvil" to "SchoolEvil", "tank" to "Tank", "phillyStreets" to "PhillyStreets", "phillyBlazin" to "PhillyBlazin"
    )

    private val characterAliases = mapOf("pico-playable" to "PicoPlayer", "bf-holding-gf-dead" to "BfHoldingGfDead", "tankman-bloody" to "TankmanBloody")

    fun pascal(id: String): String {
        val core = id.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }.ifEmpty { "Mod" }
        return if (core.first().isDigit()) "N$core" else core
    }

    fun builtinCharacter(id: String): String? = (characterAliases[id] ?: pascal(id)).takeIf { it in builtinCharacters }

    fun character(id: String): String = builtinCharacter(id) ?: pascal(id).let { if (it in builtinCharacters) it + "Mod" else it }

    fun stage(id: String, custom: Boolean): String {
        if (!custom) psychStages[id]?.let { return it }
        val name = pascal(id)
        return if (name in builtinStageClasses || name in builtinCharacters) name + "Mod" else name
    }

    fun songFolder(id: String): String = id.trim().lowercase().replace(' ', '-')
}

data class FpsChart(val chart: String, val events: List<JSONObject>, val warnings: List<String>, val song: String, val noteTypes: Set<String>, val customEvents: Set<String>)

object FpsEvents {
    fun hx(v: String) = v.replace(";", ",")

    fun tags(name: String, v1: String, v2: String, character: (String) -> String): List<String>? {
        val a = hx(v1).trim()
        val b = hx(v2).trim()
        fun who(text: String, fallback: String) = when (text.lowercase()) {
            "bf", "boyfriend", "0", "player" -> "bf"
            "gf", "girlfriend", "2" -> "gf"
            "dad", "opponent", "1" -> "dad"
            else -> fallback
        }
        return when (name) {
            "Hey!" -> when (a.lowercase()) {
                "bf", "boyfriend", "0" -> listOf("playAnim;bf;hey;true")
                "gf", "girlfriend", "1" -> listOf("playAnim;gf;cheer;true")
                else -> listOf("playAnim;bf;hey;true", "playAnim;gf;cheer;true")
            }
            "Play Animation" -> listOf("playAnim;${when (b.lowercase()) { "bf", "boyfriend", "1" -> "bf"; "gf", "girlfriend", "2" -> "gf"; else -> "dad" }};$a;true")
            "Set GF Speed" -> listOf("setBopFreq;gf;${a.toIntOrNull()?.coerceAtLeast(1) ?: 1};0")
            "Add Camera Zoom" -> {
                val game = a.toDoubleOrNull() ?: 0.015
                listOf("camBop;${fmt("%.2f", game / 0.015)}")
            }
            "Camera Follow Pos" -> if (a.isEmpty() && b.isEmpty()) emptyList() else listOf("camMove;${a.ifEmpty { "0" }};${b.ifEmpty { "0" }}")
            "Screen Shake" -> {
                val game = a.split(',').map { it.trim() }
                val intensity = game.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                val duration = game.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                if (intensity <= 0 || duration <= 0) emptyList() else listOf("camShake;$intensity;0.042;$duration")
            }
            "Change Character" -> listOf("changeCharacter;${who(a, "bf")};${character(b)};true")
            "Change Scroll Speed" -> listOf("scrollSpeedMultiplier;${a.ifEmpty { "1" }};${b.ifEmpty { "0" }}")
            "Play Sound" -> listOf("playSound;$a;${b.ifEmpty { "1" }}")
            "Alt Idle Animation" -> listOf("setAnimSet;${who(a, "dad")};${b.removePrefix("-").ifEmpty { "" }}")
            "Set Property", "Call Method", "Set Camera Bopping", "Set Camera Target", "Philly Glow", "Kill Henchmen", "Trigger BG Ghouls", "BG Freaks Expression", "Dadbattle Spotlight" -> null
            else -> listOf("$name;$a;$b")
        }
    }

    fun event(time: Double, tag: String, lane: Int = 0): JSONObject = JSONObject().put("time", time).put("lane", lane).put("tag", tag)
}

object FpsFormats {
    private fun parse(text: String): JSONObject = JSONObject(text.trim().removePrefix("﻿"))

    private fun songOf(root: JSONObject): JSONObject = root.optJSONObject("song")?.takeIf { it.opt("notes") is JSONArray } ?: root

    private val noteTypes = mapOf("Alt Animation" to "alt", "Hey!" to "hey", "No Animation" to "noAnim")

    fun noteTag(type: String): String = noteTypes[type] ?: type

    fun chart(text: String, folder: String, character: (String) -> String, stage: (String) -> String): FpsChart {
        val song = songOf(parse(text))
        val warnings = mutableListOf<String>()
        val format = song.optString("format")
        val absolute = format.contains("psych_v1") || format.startsWith("nmv")
        val bpm0 = song.optDouble("bpm", 100.0)
        var bpm = bpm0
        val bpms = JSONArray().put(JSONObject().put("time", 0).put("bpm", bpm0))
        val notes = JSONArray()
        val events = mutableListOf<JSONObject>()
        val types = linkedSetOf<String>()
        val custom = linkedSetOf<String>()
        var time = 0.0
        var lastFocus: String? = null
        var gfNotes = 0
        val sections = song.optJSONArray("notes") ?: JSONArray()
        fun addEvent(t: Double, name: String, v1: String, v2: String) {
            val tags = FpsEvents.tags(name, v1, v2, character)
            if (tags == null) {
                warnings += "Event \"$name\" only works in Psych's hardcoded stages or scripts and was left out."
                return
            }
            if (FpsEvents.tags(name, v1, v2) { it }?.firstOrNull()?.startsWith("$name;") == true) custom += name
            tags.forEach { events += FpsEvents.event(t, it, 1) }
        }
        for (s in 0 until sections.length()) {
            val section = sections.optJSONObject(s) ?: continue
            if (section.optBoolean("changeBPM", false) && section.optDouble("bpm", 0.0) > 0 && section.optDouble("bpm", 0.0) != bpm) {
                bpm = section.optDouble("bpm", bpm)
                bpms.put(JSONObject().put("time", time).put("bpm", bpm))
            }
            val mustHit = section.optBoolean("mustHitSection", true)
            val gfSection = section.optBoolean("gfSection", false)
            val focus = if (gfSection && !mustHit) "camFocusGf" else if (mustHit) "camFocusBf" else "camFocusDad"
            if (focus != lastFocus) {
                events += FpsEvents.event(time, focus, 0)
                lastFocus = focus
            }
            val step = 60000.0 / bpm / 4
            val list = section.optJSONArray("sectionNotes") ?: JSONArray()
            for (n in 0 until list.length()) {
                val note = list.optJSONArray(n) ?: continue
                val data = note.optDouble(1, 0.0).toInt()
                val t = note.optDouble(0, 0.0)
                if (data < 0) {
                    addEvent(t, note.optString(2), note.optString(3), note.optString(4))
                    continue
                }
                val player = if (absolute) data < 4 else (data < 4) == mustHit
                val type = (note.opt(3) as? String) ?: ""
                var tag = noteTag(type)
                if (tag.isEmpty() && section.optBoolean("altAnim", false) && !player) tag = "alt"
                if (type == "GF Sing" || (gfSection && !player)) gfNotes++
                if (type.isNotEmpty() && type !in noteTypes) types += type
                notes.put(JSONObject().put("time", t).put("direction", data % 4).put("length", (note.optDouble(2, 0.0) / step).roundToInt()).put("tag", if (type == "GF Sing") "" else tag).put("player", player))
            }
            val beats = when {
                section.has("sectionBeats") -> section.optDouble("sectionBeats", 4.0)
                section.has("lengthInSteps") -> section.optDouble("lengthInSteps", 16.0) / 4
                else -> 4.0
            }
            time += 60000.0 / bpm * beats
        }
        val psychEvents = song.optJSONArray("events")
        if (psychEvents != null) for (e in 0 until psychEvents.length()) {
            val entry = psychEvents.optJSONArray(e) ?: continue
            val t = entry.optDouble(0, 0.0)
            val inner = entry.optJSONArray(1) ?: continue
            for (k in 0 until inner.length()) {
                val ev = inner.optJSONArray(k) ?: continue
                addEvent(t, ev.optString(0), ev.optString(1), ev.optString(2))
            }
        }
        if (gfNotes > 0) warnings += "$gfNotes notes sung by GF (GF Sing / gfSection) are sung by the opponent in FPS Plus."
        val name = song.optString("song").ifBlank { folder }
        val meta = JSONObject()
        meta.put("format", "fpsplus_1")
        meta.put("song", folder)
        meta.put("player", character(song.optString("player1").ifBlank { "bf" }))
        meta.put("opponent", character(song.optString("player2").ifBlank { "dad" }))
        meta.put("speaker", character(song.optString("gfVersion").ifBlank { song.optString("player3").ifBlank { "gf" } }))
        meta.put("stage", stage(song.optString("stage").ifBlank { "stage" }))
        meta.put("scroll", song.optDouble("speed", 1.0))
        meta.put("bpm", bpms)
        return FpsChart(JSONObject().put("meta", meta).put("notes", notes).toString(2), events, warnings, name, types, custom)
    }

    fun eventsJson(text: String, character: (String) -> String): Pair<List<JSONObject>, Set<String>> {
        val song = songOf(parse(text))
        val out = mutableListOf<JSONObject>()
        val custom = linkedSetOf<String>()
        fun add(t: Double, name: String, v1: String, v2: String) {
            FpsEvents.tags(name, v1, v2, character)?.forEach { out += FpsEvents.event(t, it, 1) }
            if (FpsEvents.tags(name, v1, v2) { it }?.firstOrNull()?.startsWith("$name;") == true) custom += name
        }
        song.optJSONArray("events")?.let { list ->
            for (e in 0 until list.length()) {
                val entry = list.optJSONArray(e) ?: continue
                val inner = entry.optJSONArray(1) ?: continue
                for (k in 0 until inner.length()) inner.optJSONArray(k)?.let { add(entry.optDouble(0, 0.0), it.optString(0), it.optString(1), it.optString(2)) }
            }
        }
        song.optJSONArray("notes")?.let { sections ->
            for (s in 0 until sections.length()) {
                val notes = sections.optJSONObject(s)?.optJSONArray("sectionNotes") ?: continue
                for (n in 0 until notes.length()) {
                    val note = notes.optJSONArray(n) ?: continue
                    if (note.optDouble(1, 0.0) < 0) add(note.optDouble(0, 0.0), note.optString(2), note.optString(3), note.optString(4))
                }
            }
        }
        return out to custom
    }

    fun writeEvents(events: List<JSONObject>): String {
        val unique = LinkedHashMap<String, JSONObject>()
        events.sortedWith(compareBy<JSONObject> { it.optDouble("time", 0.0) }.thenBy { it.optInt("lane", 0) }).forEach {
            val key = it.optDouble("time", 0.0).toString() + "|" + it.optString("tag")
            if (key !in unique) unique[key] = it
        }
        val list = JSONArray()
        unique.values.forEach { list.put(it) }
        return JSONObject().put("events", list).put("meta", JSONObject().put("format", "fpsplus_1")).toString(2)
    }

    fun songMeta(name: String): String = JSONObject()
        .put("name", name)
        .put("artist", "")
        .put("album", "vol1")
        .put("difficulties", JSONArray().put(1).put(2).put(3))
        .put("compatibleInsts", JSONArray())
        .put("mixName", "Original")
        .toString(2)

    private fun str(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun num(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else fmt("%.3f", d).trimEnd('0').trimEnd('.')

    class CharacterOut(val className: String, val text: String, val warnings: List<String>)

    fun character(id: String, text: String, className: String, atlas: Boolean, deathClass: String?): CharacterOut {
        val root = parse(text)
        val warnings = mutableListOf<String>()
        val image = root.optString("image")
        val scale = root.optDouble("scale", 1.0).takeIf { it > 0 } ?: 1.0
        val lines = mutableListOf<String>()
        lines += "info.name = ${str(id)};"
        val images = image.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        when {
            atlas -> {
                lines += "info.spritePath = ${str(image)};"
                lines += "info.frameLoadType = setAtlas();"
                warnings += "$id is an Animate atlas: Psych plays symbols, FPS Plus plays frame labels, so check that the animations use labels with the same names."
            }
            images.size > 1 -> {
                lines += "info.spritePaths = [${images.joinToString(", ") { str(it) }}];"
                lines += "info.frameLoadType = setMultiSparrow();"
            }
            else -> {
                lines += "info.spritePath = ${str(image)};"
                lines += "info.frameLoadType = setSparrow();"
            }
        }
        lines += "info.iconName = ${str(root.optString("healthicon").ifBlank { "face" })};"
        val flip = root.optBoolean("flip_x", false)
        lines += "info.facesLeft = $flip;"
        if (root.optBoolean("no_antialiasing", false)) lines += "info.antialiasing = false;"
        root.optJSONArray("healthbar_colors")?.let { c ->
            val rgb = (c.optInt(0, 161) shl 16) or (c.optInt(1, 161) shl 8) or c.optInt(2, 161)
            lines += "info.healthColor = 0xFF" + fmt("%06X", rgb) + ";"
        }
        val cam = root.optJSONArray("camera_position")
        val cx = cam?.optDouble(0, 0.0) ?: 0.0
        val cy = cam?.optDouble(1, 0.0) ?: 0.0
        lines += "info.focusOffset.set(${num((if (flip) 100.0 else 150.0) + cx)}, ${num(-100.0 + cy)});"
        if (deathClass != null) lines += "info.deathCharacter = ${str(deathClass)};"
        val anims = root.optJSONArray("animations") ?: JSONArray()
        val names = (0 until anims.length()).mapNotNull { anims.optJSONObject(it)?.optString("anim") }.toSet()
        if ("idle" !in names && "danceLeft" in names && "danceRight" in names) lines += "info.idleSequence = [\"danceLeft\", \"danceRight\"];"
        lines += ""
        for (i in 0 until anims.length()) {
            val a = anims.optJSONObject(i) ?: continue
            val name = a.optString("anim")
            if (name.isBlank()) continue
            val prefix = a.optString("name")
            val offsets = a.optJSONArray("offsets")
            val ox = (offsets?.optDouble(0, 0.0) ?: 0.0) / scale
            val oy = (offsets?.optDouble(1, 0.0) ?: 0.0) / scale
            val fps = a.optDouble("fps", 24.0)
            val loop = if (a.optBoolean("loop", false)) "loop(true)" else "loop(false)"
            val indices = a.optJSONArray("indices")
            val offset = "offset(${num(ox)}, ${num(oy)})"
            lines += when {
                atlas -> "addByLabel(${str(name)}, $offset, ${str(prefix)}, ${num(fps)}, $loop);"
                indices != null && indices.length() > 0 -> "addByIndices(${str(name)}, $offset, ${str(prefix)}, [${(0 until indices.length()).joinToString(", ") { indices.optInt(it).toString() }}], \"\", ${num(fps)}, $loop);"
                else -> "addByPrefix(${str(name)}, $offset, ${str(prefix)}, ${num(fps)}, $loop);"
            }
        }
        lines += ""
        if (scale != 1.0) lines += "addExtraData(\"scale\", [${num(scale)}, ${num(scale)}]);"
        root.optDouble("sing_duration", 4.0).takeIf { it != 4.0 }?.let { lines += "addExtraData(\"stepsUntilRelease\", ${num(it)});" }
        val pos = root.optJSONArray("position")
        lines += "addExtraData(\"psychPosition\", [${num(pos?.optDouble(0, 0.0) ?: 0.0)}, ${num(pos?.optDouble(1, 0.0) ?: 0.0)}]);"
        val body = lines.dropLastWhile { it.isEmpty() }.joinToString("\n") { if (it.isEmpty()) "" else "\t\t$it" }
        val out = "class $className extends CharacterInfoBase\n{\n\n\tpublic function new(){\n\t\tsuper();\n\n$body\n\t}\n\n}\n"
        return CharacterOut(className, out, warnings)
    }

    class StageOut(val className: String, val constructorLines: List<String>, val createPrefix: List<String>, val warnings: List<String>, val imports: Set<String>)

    fun stage(id: String, text: String?, className: String): StageOut {
        val root = text?.let { runCatching { parse(it) }.getOrNull() } ?: JSONObject()
        val warnings = mutableListOf<String>()
        val c = mutableListOf<String>()
        c += "name = ${str(id)};"
        c += "startingZoom = ${num(root.optDouble("defaultZoom", 0.9))};"
        if (root.optBoolean("isPixelStage", false) || root.optString("stageUI").equals("pixel", true)) c += "uiType = \"Pixel\";"
        fun point(key: String, x: Double, y: Double): Pair<Double, Double> {
            val a = root.optJSONArray(key) ?: return x to y
            return a.optDouble(0, x) to a.optDouble(1, y)
        }
        val bf = point("boyfriend", 770.0, 100.0)
        val gf = point("girlfriend", 400.0, 130.0)
        val dad = point("opponent", 100.0, 100.0)
        if (bf != 770.0 to 100.0) c += "bfStart.set(bfStart.x + ${num(bf.first - 770.0)}, bfStart.y + ${num(bf.second - 100.0)});"
        if (dad != 100.0 to 100.0) c += "dadStart.set(dadStart.x + ${num(dad.first - 100.0)}, dadStart.y + ${num(dad.second - 100.0)});"
        if (gf != 400.0 to 130.0) c += "gfStart.set(gfStart.x + ${num(gf.first - 400.0)}, gfStart.y + ${num(gf.second - 130.0)});"
        val cb = point("camera_boyfriend", 0.0, 0.0)
        val co = point("camera_opponent", 0.0, 0.0)
        val cg = point("camera_girlfriend", 0.0, 0.0)
        if (cb != 0.0 to 0.0) c += "bfCameraOffset.set(${num(-cb.first)}, ${num(cb.second)});"
        if (co != 0.0 to 0.0) c += "dadCameraOffset.set(${num(co.first)}, ${num(co.second)});"
        if (cg != 0.0 to 0.0) c += "gfCameraOffset.set(${num(cg.first)}, ${num(cg.second)});"
        val objects = root.optJSONArray("objects")
        val imports = linkedSetOf<String>()
        if (objects != null && objects.length() > 0) {
            imports += "flixel.FlxSprite"
            val types = (0 until objects.length()).map { objects.optJSONObject(it)?.optString("type") ?: "" }
            val gfAt = types.indexOfFirst { it == "gf" || it == "gfGroup" }.let { if (it < 0) -1 else it }
            val charAt = types.indexOfFirst { it == "dad" || it == "boyfriend" || it == "dadGroup" || it == "boyfriendGroup" }
            val lastChar = types.indexOfLast { it == "dad" || it == "boyfriend" || it == "dadGroup" || it == "boyfriendGroup" || it == "gf" || it == "gfGroup" }
            var count = 0
            for (i in 0 until objects.length()) {
                val o = objects.optJSONObject(i) ?: continue
                val type = o.optString("type")
                if (type !in setOf("sprite", "animatedSprite", "square")) continue
                val v = "obj${count++}"
                c += ""
                c += "var $v:FlxSprite = new FlxSprite(${num(o.optDouble("x", 0.0))}, ${num(o.optDouble("y", 0.0))});"
                val image = o.optString("image")
                val anims = o.optJSONArray("animations") ?: JSONArray()
                when {
                    type == "square" -> c += "$v.makeGraphic(1, 1, 0xFFFFFFFF);"
                    type == "animatedSprite" || anims.length() > 0 -> {
                        c += "$v.frames = Paths.getSparrowAtlas(${str(image)});"
                        for (k in 0 until anims.length()) {
                            val a = anims.optJSONObject(k) ?: continue
                            val idx = a.optJSONArray("indices")
                            c += if (idx != null && idx.length() > 0) "$v.animation.addByIndices(${str(a.optString("anim"))}, ${str(a.optString("name"))}, [${(0 until idx.length()).joinToString(", ") { idx.optInt(it).toString() }}], \"\", ${num(a.optDouble("fps", 24.0))}, ${a.optBoolean("loop", false)});"
                            else "$v.animation.addByPrefix(${str(a.optString("anim"))}, ${str(a.optString("name"))}, ${num(a.optDouble("fps", 24.0))}, ${a.optBoolean("loop", false)});"
                        }
                        val first = o.optString("firstAnimation").ifBlank { anims.optJSONObject(0)?.optString("anim") ?: "" }
                        if (first.isNotEmpty()) c += "$v.animation.play(${str(first)});"
                    }
                    else -> c += "$v.loadGraphic(Paths.image(${str(image)}));"
                }
                val scale = o.optJSONArray("scale")
                val sx = scale?.optDouble(0, 1.0) ?: 1.0
                val sy = scale?.optDouble(1, sx) ?: sx
                if (sx != 1.0 || sy != 1.0) {
                    c += "$v.scale.set(${num(sx)}, ${num(sy)});"
                    c += "$v.updateHitbox();"
                }
                val scroll = o.optJSONArray("scroll")
                if (scroll != null) c += "$v.scrollFactor.set(${num(scroll.optDouble(0, 1.0))}, ${num(scroll.optDouble(1, 1.0))});"
                if (o.optDouble("alpha", 1.0) != 1.0) c += "$v.alpha = ${num(o.optDouble("alpha", 1.0))};"
                if (o.optDouble("angle", 0.0) != 0.0) c += "$v.angle = ${num(o.optDouble("angle", 0.0))};"
                val color = o.optString("color").removePrefix("#").removePrefix("0x")
                if (color.isNotEmpty() && !color.equals("FFFFFF", true) && Regex("^[0-9A-Fa-f]{6}$").matches(color)) c += "$v.color = 0xFF${color.uppercase()};"
                if (o.optBoolean("flipX", false)) c += "$v.flipX = true;"
                if (o.optBoolean("flipY", false)) c += "$v.flipY = true;"
                if (!o.optBoolean("antialiasing", true)) c += "$v.antialiasing = false;"
                o.optString("blend").takeIf { it.isNotBlank() && it != "normal" }?.let { c += "$v.blend = BlendMode.fromString(${str(it)});" }
                val layer = when {
                    lastChar < 0 || i < (if (gfAt >= 0) gfAt else charAt.takeIf { it >= 0 } ?: Int.MAX_VALUE) -> "addToBackground"
                    gfAt >= 0 && charAt >= 0 && i in gfAt..charAt -> "addToMiddle"
                    else -> "addToForeground"
                }
                c += "$layer($v);"
                if (o.optString("name").isNotBlank()) c += "playstate.arbitraryData.set(${str(o.optString("name"))}, $v);"
            }
        }
        val create = mutableListOf<String>()
        create += "psychPlace(boyfriend, ${num(bf.first)}, ${num(bf.second)});"
        create += "psychPlace(dad, ${num(dad.first)}, ${num(dad.second)});"
        create += "psychPlace(gf, ${num(gf.first)}, ${num(gf.second)});"
        if (root.optBoolean("hide_girlfriend", false)) create += "gf.visible = false;"
        return StageOut(className, c, create, warnings, imports)
    }

    val placeMethod = "function psychPlace(character:Character, x:Float, y:Float):Void\n{\n  if (character == null || !character.characterInfo.info.extraData.exists(\"psychPosition\")) return;\n  var p:Array<Float> = character.characterInfo.info.extraData.get(\"psychPosition\");\n  character.setPosition(x + p[0], y + p[1]);\n}"

    fun week(id: String, text: String, order: Int, character: (String) -> String): Pair<String, List<String>> {
        val root = parse(text)
        val out = JSONObject()
        val name = root.optString("storyName").ifBlank { root.optString("weekName").ifBlank { id } }
        out.put("name", name)
        out.put("id", id)
        out.put("sortOrder", 1000 + order)
        val songs = JSONArray()
        val icons = mutableListOf<String>()
        root.optJSONArray("songs")?.let { list ->
            for (i in 0 until list.length()) {
                val s = list.optJSONArray(i) ?: continue
                songs.put(FpsNames.songFolder(s.optString(0)))
                icons += s.optString(1)
            }
        }
        out.put("songs", songs)
        val chars = JSONArray()
        root.optJSONArray("weekCharacters")?.let { list -> for (i in 0 until list.length()) chars.put(list.optString(i)) }
        if (chars.length() > 0) out.put("characters", chars)
        val diffs = root.optString("difficulties").split(',').map { it.trim().lowercase() }.filter { it in setOf("easy", "normal", "hard") }
        if (diffs.isNotEmpty()) out.put("difficulties", JSONArray().also { a -> diffs.forEach { a.put(it) } })
        root.optJSONArray("weekColor")?.let { c -> out.put("color", "0x" + fmt("%06X", (c.optInt(0) shl 16) or (c.optInt(1) shl 8) or c.optInt(2))) }
        return out.toString(2) to icons
    }

    fun storyCharacterOffsets(text: String): String? {
        val root = runCatching { parse(text) }.getOrNull() ?: return null
        val pos = root.optJSONArray("position") ?: return null
        val x = pos.optDouble(0, 0.0)
        val y = pos.optDouble(1, 0.0)
        if (x == 0.0 && y == 0.0) return null
        val offsets = JSONArray()
        listOf("idle", "confirm").forEach { offsets.put(JSONObject().put("name", it).put("offset", JSONArray().put(-x).put(-y))) }
        return JSONObject().put("offsets", offsets).toString(2)
    }

    fun renameAtlasAnims(xml: String, idle: String, confirm: String): String {
        var out = xml
        if (idle.isNotBlank() && idle != "idle") out = Regex("name=\"${Regex.escape(idle)}").replace(out, "name=\"idle")
        if (confirm.isNotBlank() && confirm != "confirm") out = Regex("name=\"${Regex.escape(confirm)}").replace(out, "name=\"confirm")
        return out
    }

    fun modMeta(title: String, description: String, author: String, version: String): String {
        val uid = "converted." + title.lowercase().replace(Regex("[^a-z0-9]+"), "").ifEmpty { "mod" }
        val meta = JSONObject()
        meta.put("uid", uid)
        meta.put("title", title)
        meta.put("description", description)
        if (author.isNotBlank()) meta.put("contributors", JSONArray().put(JSONObject().put("name", author)))
        meta.put("api_version", "1.9.0")
        meta.put("mod_version", version.ifBlank { "1.0.0" })
        return meta.toString(2)
    }
}
