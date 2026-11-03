package com.brenninhotools.vconverter.nmv

import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.stage.PsychStageParser

data class NmvText(val text: String, val warnings: List<String> = emptyList())

object NmvFormats {
    fun sanitize(name: String): String = name.trim().lowercase().replace(' ', '-')

    private fun parse(text: String): JSONObject = JSONObject(text.trim().removePrefix("﻿"))

    private fun songOf(root: JSONObject): JSONObject = root.optJSONObject("song") ?: root

    fun isChart(text: String): Boolean = runCatching {
        val song = songOf(parse(text))
        song.opt("notes") is JSONArray
    }.getOrDefault(false)

    fun chartToPsych(text: String): NmvText {
        val root = parse(text)
        val song = songOf(root)
        val warnings = mutableListOf<String>()
        val keys = song.optInt("keys", 4)
        val lanes = song.optInt("lanes", 2)
        val format = song.optString("format")
        if (format.startsWith("nmv")) {
            var dropped = 0
            val sections = song.optJSONArray("notes") ?: JSONArray()
            for (s in 0 until sections.length()) {
                val section = sections.optJSONObject(s) ?: continue
                val notes = section.optJSONArray("sectionNotes") ?: continue
                val kept = JSONArray()
                for (n in 0 until notes.length()) {
                    val note = notes.optJSONArray(n) ?: continue
                    val data = note.optDouble(1, 0.0).toInt()
                    if (data < 0) {
                        kept.put(note)
                        continue
                    }
                    val lane = data / keys
                    val key = data % keys
                    if (lane >= 2 || key >= 4) {
                        dropped++
                        continue
                    }
                    note.put(1, lane * 4 + key)
                    kept.put(note)
                }
                section.put("sectionNotes", kept)
            }
            song.put("format", "psych_v1")
            if (dropped > 0) warnings += "$dropped notes on extra lanes or keys were dropped (Psych has 2 lanes of 4 keys; this chart has $lanes lanes of $keys keys)."
        } else if (keys != 4) {
            warnings += "The chart uses $keys keys; Psych only plays 4."
        }
        val skins = song.optJSONArray("arrowSkins")
        if (skins != null && (0 until skins.length()).any { skins.optString(it).let { s -> s.isNotEmpty() && s != "default" } }) {
            warnings += "NightmareVision noteskins (${(0 until skins.length()).joinToString(", ") { skins.optString(it) }}) have no Psych equivalent; the default arrows are used."
        }
        listOf("keys", "lanes", "arrowSkins", "trackSwap").forEach { song.remove(it) }
        if (!song.has("player3") && song.has("gfVersion")) song.put("player3", song.optString("gfVersion"))
        return NmvText(JSONObject().put("song", song).toString(2), warnings)
    }

    fun chartToNmv(text: String, folder: String? = null): NmvText {
        val root = parse(text)
        val song = songOf(root)
        val warnings = mutableListOf<String>()
        val format = song.optString("format")
        when {
            format.contains("psych_v1") -> song.put("format", "nmv2")
            format.startsWith("nmv") -> Unit
            else -> song.remove("format")
        }
        if (!song.has("keys")) song.put("keys", 4)
        if (!song.has("lanes")) song.put("lanes", 2)
        if (!song.has("gfVersion") && song.has("player3")) song.put("gfVersion", song.optString("player3"))
        song.remove("player3")
        val skin = song.optString("arrowSkin")
        if (skin.isNotEmpty() && !skin.equals("NOTE_assets", true)) warnings += "Psych arrow skin '$skin' is an image; NightmareVision uses noteskins (data/noteskins), so the default noteskin is used."
        song.remove("arrowSkin")
        song.remove("splashSkin")
        if (folder != null && sanitize(song.optString("song")) != folder) song.put("song", folder)
        return NmvText(JSONObject().put("song", song).toString(2), warnings)
    }

    fun songMeta(chartText: String, folder: String): String {
        val song = runCatching { songOf(parse(chartText)) }.getOrNull()
        val name = song?.optString("song")?.takeIf { it.isNotBlank() } ?: folder
        val meta = JSONObject()
        meta.put("songName", folder)
        meta.put("displayName", name.split('-', ' ').joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }.takeIf { name == folder } ?: name)
        meta.put("composers", JSONArray())
        meta.put("charters", JSONArray())
        song?.optString("player2")?.takeIf { it.isNotBlank() }?.let { meta.put("freeplayIcon", it) }
        return meta.toString(2)
    }

    fun characterToPsych(text: String): NmvText {
        val root = parse(text)
        if (!root.has("healthbar_colors") && root.has("healthbar_colour")) {
            val c = root.optDouble("healthbar_colour", 0.0).toLong().toInt()
            root.put("healthbar_colors", JSONArray().put((c shr 16) and 0xFF).put((c shr 8) and 0xFF).put(c and 0xFF))
        }
        val warnings = mutableListOf<String>()
        if (root.optBoolean("ghosts_enabled", false)) warnings += "Sing ghosts are NightmareVision-only."
        listOf("healthbar_colour", "dance_every", "scalableOffsets", "_editor_isPlayer", "gameover_character", "gameover_intial_sound", "gameover_loop_sound", "gameover_confirm_sound", "vslice_sustains", "ghosts_enabled").forEach { root.remove(it) }
        if (!root.has("healthbar_colors")) root.put("healthbar_colors", JSONArray().put(161).put(161).put(161))
        return NmvText(root.toString(2), warnings)
    }

    fun characterToNmv(text: String): NmvText = NmvText(parse(text).toString(2))

    fun isCharacter(text: String): Boolean = runCatching { parse(text).opt("animations") is JSONArray }.getOrDefault(false)

    fun stageToPsych(text: String): NmvText {
        val root = parse(text)
        val warnings = mutableListOf<String>()
        val objects = root.optJSONArray("stageObjects")
        val chars = listOf(
            Triple("gf", root.optDouble("gfZIndex", 0.0), 0),
            Triple("dad", root.optDouble("dadZIndex", 0.0), 1),
            Triple("boyfriend", root.optDouble("bfZIndex", 0.0), 2)
        )
        if (objects != null) {
            data class Entry(val z: Double, val order: Int, val json: JSONObject)
            val entries = mutableListOf<Entry>()
            for (i in 0 until objects.length()) {
                val o = objects.optJSONObject(i) ?: continue
                entries += Entry(o.optDouble("zIndex", 0.0), i, objectToPsych(o, warnings))
            }
            chars.forEach { (type, z, n) -> entries += Entry(z, 100000 + n, JSONObject().put("type", type)) }
            val psych = JSONArray()
            entries.sortedWith(compareBy<Entry> { it.z }.thenBy { it.order }).forEach { psych.put(it.json) }
            root.put("objects", psych)
        }
        listOf("stageObjects", "dadZIndex", "gfZIndex", "bfZIndex").forEach { root.remove(it) }
        return NmvText(root.toString(2), warnings)
    }

    private fun objectToPsych(o: JSONObject, warnings: MutableList<String>): JSONObject {
        val id = o.optString("id")
        val asset = o.optString("asset")
        val animations = o.optJSONArray("animations") ?: JSONArray()
        val custom = o.optString("customInstance")
        if (custom.isNotEmpty()) warnings += "Stage object '$id' is a NightmareVision $custom; it became a plain sprite."
        if (asset.contains(',')) warnings += "Stage object '$id' uses several atlases; only ${asset.substringBefore(',')} is kept."
        val out = JSONObject()
        out.put("type", if (asset.isEmpty()) "square" else if (animations.length() > 0) "animatedSprite" else "sprite")
        out.put("name", id)
        if (asset.isNotEmpty()) out.put("image", asset.substringBefore(','))
        val pos = o.optJSONArray("position")
        out.put("x", pos?.optDouble(0, 0.0) ?: 0.0)
        out.put("y", pos?.optDouble(1, 0.0) ?: 0.0)
        out.put("scale", pair(o.optJSONArray("scale"), 1.0))
        out.put("scroll", pair(o.optJSONArray("scrollFactor"), 1.0))
        out.put("alpha", o.optDouble("alpha", 1.0))
        out.put("angle", o.optDouble("angle", 0.0))
        out.put("color", o.optString("colour").removePrefix("#").removePrefix("0x").ifEmpty { "FFFFFF" }.takeLast(6))
        out.put("antialiasing", o.optBoolean("antialiasing", true))
        out.put("flipX", o.optBoolean("flipX", false))
        out.put("flipY", o.optBoolean("flipY", false))
        out.put("filters", if (o.optBoolean("highQuality", false)) PsychStageParser.FILTER_HIGH_QUALITY else PsychStageParser.FILTER_LOW_QUALITY or PsychStageParser.FILTER_HIGH_QUALITY)
        if (o.has("blend")) out.put("blend", o.optString("blend"))
        if (animations.length() > 0) {
            out.put("animations", animations)
            out.put("firstAnimation", animations.optJSONObject(0)?.optString("anim") ?: "")
        }
        return out
    }

    private fun pair(array: JSONArray?, fallback: Double): JSONArray =
        JSONArray().put(array?.optDouble(0, fallback) ?: fallback).put(array?.optDouble(1, array.optDouble(0, fallback)) ?: fallback)

    fun stageToNmv(text: String): NmvText {
        val root = parse(text)
        val warnings = mutableListOf<String>()
        val objects = root.optJSONArray("objects")
        var z = 10
        var gf = -1
        var dad = -1
        var bf = -1
        if (objects != null) {
            val out = JSONArray()
            for (i in 0 until objects.length()) {
                val o = objects.optJSONObject(i) ?: continue
                when (o.optString("type")) {
                    "gf", "girlfriend", "gfGroup" -> gf = z
                    "dad", "opponent", "dadGroup" -> dad = z
                    "boyfriend", "bf", "boyfriendGroup" -> bf = z
                    else -> out.put(objectToNmv(o, z, warnings))
                }
                z += 10
            }
            root.put("stageObjects", out)
            root.remove("objects")
        }
        if (gf < 0) gf = maxOf(z, 100).also { z = it + 10 }
        if (dad < 0) dad = maxOf(z, 200).also { z = it + 10 }
        if (bf < 0) bf = maxOf(z, 300)
        root.put("gfZIndex", gf)
        root.put("dadZIndex", dad)
        root.put("bfZIndex", bf)
        root.remove("preload")
        return NmvText(root.toString(2), warnings)
    }

    private fun objectToNmv(o: JSONObject, z: Int, warnings: MutableList<String>): JSONObject {
        val out = JSONObject()
        val name = o.optString("name")
        out.put("id", name)
        val type = o.optString("type")
        if (type != "square") o.optString("image").takeIf { it.isNotEmpty() }?.let { out.put("asset", it) }
        out.put("position", JSONArray().put(o.optDouble("x", 0.0)).put(o.optDouble("y", 0.0)))
        o.optJSONArray("scale")?.let { out.put("scale", it) }
        o.optJSONArray("scroll")?.let { out.put("scrollFactor", it) }
        if (o.has("alpha")) out.put("alpha", o.optDouble("alpha", 1.0))
        if (o.has("angle")) out.put("angle", o.optDouble("angle", 0.0))
        val color = o.optString("color").removePrefix("#").removePrefix("0x")
        if (color.isNotEmpty() && !color.equals("FFFFFF", true)) out.put("colour", "#" + color.takeLast(6))
        if (o.has("flipX")) out.put("flipX", o.optBoolean("flipX", false))
        if (o.has("flipY")) out.put("flipY", o.optBoolean("flipY", false))
        if (o.has("antialiasing")) out.put("antialiasing", o.optBoolean("antialiasing", true))
        o.optString("blend").takeIf { it.isNotEmpty() && it != "normal" }?.let { out.put("blend", it) }
        out.put("zIndex", z)
        val filters = o.optInt("filters", PsychStageParser.FILTER_LOW_QUALITY or PsychStageParser.FILTER_HIGH_QUALITY)
        if (filters and PsychStageParser.FILTER_LOW_QUALITY == 0) out.put("highQuality", true)
        if (filters and PsychStageParser.FILTER_HIGH_QUALITY == 0) warnings += "Stage object '$name' only shows in low quality in Psych; NightmareVision always shows it."
        val animations = o.optJSONArray("animations")
        if (animations != null && animations.length() > 0) {
            val first = o.optString("firstAnimation")
            val sorted = JSONArray()
            (0 until animations.length()).mapNotNull { animations.optJSONObject(it) }.sortedBy { if (it.optString("anim") == first) 0 else 1 }.forEach { sorted.put(it) }
            out.put("animations", sorted)
        }
        return out
    }

    fun isStage(text: String): Boolean = runCatching {
        val root = parse(text)
        root.opt("boyfriend") is JSONArray || root.has("defaultZoom") || root.opt("objects") is JSONArray || root.opt("stageObjects") is JSONArray
    }.getOrDefault(false)

    fun metaToPack(text: String): String {
        val root = runCatching { parse(text) }.getOrDefault(JSONObject())
        val pack = JSONObject()
        pack.put("name", root.optString("name"))
        pack.put("description", root.optString("description"))
        pack.put("restart", false)
        pack.put("runsGlobally", root.optBoolean("global", false))
        return pack.toString(2)
    }

    fun packToMeta(text: String, icon: Boolean): String {
        val root = runCatching { parse(text) }.getOrDefault(JSONObject())
        val meta = JSONObject()
        meta.put("name", root.optString("name"))
        meta.put("description", root.optString("description"))
        meta.put("global", root.optBoolean("runsGlobally", false))
        if (icon) meta.put("iconFile", "pack")
        return meta.toString(2)
    }

    fun meta(title: String, description: String, icon: Boolean): String {
        val meta = JSONObject()
        meta.put("name", title)
        meta.put("description", description)
        meta.put("global", false)
        if (icon) meta.put("iconFile", "pack")
        return meta.toString(2)
    }
}
