package com.brenninhotools.vconverter.reverse

import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.readText
import org.json.JSONArray
import org.json.JSONObject

data class VSliceSongFiles(val id: String, val metadata: Map<String, String>, val charts: Map<String, String>, val audio: List<String>)

data class VSliceModScan(
    val root: String,
    val meta: String?,
    val icon: String?,
    val songs: List<VSliceSongFiles>,
    val characters: List<String>,
    val stages: List<String>,
    val levels: List<String>,
    val scripts: List<String>,
    val assets: List<String>,
    val ignored: List<String>,
    val placeholders: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean get() = songs.isEmpty() && characters.isEmpty() && stages.isEmpty() && levels.isEmpty() && scripts.isEmpty()

    fun relative(path: String): String = path.removePrefix(root)
}

object VSliceModScanner {
    private val assetFolders = setOf("images", "music", "sounds", "fonts", "videos", "shaders")

    fun looksLikeVSlice(files: ModFiles): Boolean = files.paths.any {
        it.endsWith("_polymod_meta.json") || Regex("(^|/)data/songs/[^/]+/[^/]+-metadata(-[^/]+)?\\.json$").containsMatchIn(it) || it.endsWith(".hxc")
    }

    fun scan(files: ModFiles): VSliceModScan {
        val paths = files.paths
        val metaPath = paths.filter { it.endsWith("_polymod_meta.json") }.minByOrNull { it.count { c -> c == '/' } }
        val root = metaPath?.removeSuffix("_polymod_meta.json")
            ?: paths.firstNotNullOfOrNull { p -> Regex("^(.*?)(data/(songs|characters|stages|levels)/)").find(p)?.groupValues?.get(1) }
            ?: ""
        val placeholders = com.brenninhotools.vconverter.mod.Placeholders.find(paths, root)
        val placeholderFiles = placeholders.values.toSet()
        val inRoot = paths.filter { it.startsWith(root) && it !in placeholderFiles }
        val songs = LinkedHashMap<String, Triple<MutableMap<String, String>, MutableMap<String, String>, MutableList<String>>>()
        val characters = mutableListOf<String>()
        val stages = mutableListOf<String>()
        val levels = mutableListOf<String>()
        val scripts = mutableListOf<String>()
        val assets = mutableListOf<String>()
        val ignored = mutableListOf<String>()
        var icon: String? = null
        inRoot.forEach { full ->
            val rel = full.removePrefix(root)
            val parts = rel.split('/')
            when {
                rel == "_polymod_meta.json" -> Unit
                rel == "_polymod_icon.png" -> icon = full
                parts.size == 4 && parts[0] == "data" && parts[1] == "songs" && rel.endsWith(".json") -> {
                    val id = parts[2]
                    val file = parts[3].removeSuffix(".json")
                    val entry = songs.getOrPut(id) { Triple(mutableMapOf(), mutableMapOf(), mutableListOf()) }
                    val meta = Regex("^${Regex.escape(id)}-metadata(?:-(.+))?$").find(file)
                    val chart = Regex("^${Regex.escape(id)}-chart(?:-(.+))?$").find(file)
                    when {
                        meta != null -> entry.first[meta.groupValues[1]] = full
                        chart != null -> entry.second[chart.groupValues[1]] = full
                        else -> ignored += rel
                    }
                }
                parts.size == 3 && parts[0] == "data" && parts[1] == "characters" && rel.endsWith(".json") -> characters += full
                parts.size == 3 && parts[0] == "data" && parts[1] == "stages" && rel.endsWith(".json") -> stages += full
                parts.size == 3 && parts[0] == "data" && parts[1] == "levels" && rel.endsWith(".json") -> levels += full
                parts.size >= 3 && parts[0] == "songs" && rel.substringAfterLast('.').lowercase() in setOf("ogg", "mp3", "wav") -> {
                    songs.getOrPut(parts[1]) { Triple(mutableMapOf(), mutableMapOf(), mutableListOf()) }.third += full
                }
                rel.endsWith(".hxc") -> scripts += full
                parts.size > 1 && parts[0] in assetFolders -> assets += full
                else -> ignored += rel
            }
        }
        return VSliceModScan(
            root = root,
            meta = metaPath,
            icon = icon,
            songs = songs.filter { it.value.first.isNotEmpty() || it.value.second.isNotEmpty() }.map { (id, v) -> VSliceSongFiles(id, v.first, v.second, v.third) },
            characters = characters,
            stages = stages,
            levels = levels,
            scripts = scripts,
            assets = assets,
            ignored = ignored,
            placeholders = placeholders
        )
    }

    fun readInfo(files: ModFiles, scan: VSliceModScan): ModInfo? {
        val path = scan.meta ?: return null
        return runCatching {
            val json = JSONObject(files.readText(path).trim().removePrefix("﻿"))
            val contributors = json.value("contributors") as? JSONArray
            val author = contributors?.let { arr -> (0 until arr.length()).mapNotNull { (arr.value(it) as? JSONObject)?.value("name").asText().takeIf { n -> n.isNotEmpty() } } }?.joinToString(", ") ?: ""
            ModInfo(json.value("title").asText(), json.value("description").asText(), author, json.value("mod_version").asText().ifEmpty { "1.0.0" })
        }.getOrNull()
    }
}

class VSliceModConverter(
    private val files: ModFiles,
    private val scan: VSliceModScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    private val report = ReportBuilder()
    private val index: Map<String, String> = files.paths.associateBy { it.lowercase() }
    private var written = 0
    private var done = 0
    private val total = scan.songs.size + scan.characters.size + scan.stages.size + scan.levels.size + scan.scripts.size + scan.assets.size + 2

    private val atlasCache = HashMap<String, SparrowAtlas?>()

    private fun stageAtlas(image: String): SparrowAtlas? = atlasCache.getOrPut(image.lowercase()) {
        val xml = index["${scan.root}images/$image.xml".lowercase()] ?: return@getOrPut null
        runCatching { SparrowAtlas.parse(files.readText(xml)) }.getOrNull()?.takeIf { it.frames.isNotEmpty() }
    }

    private fun step(label: String) {
        done++
        onProgress(ModProgress(done.coerceAtMost(total), total, label))
    }

    private fun write(path: String, text: String) {
        writer.writeText(path, text)
        written++
    }

    private fun copy(from: String, to: String): Boolean {
        val source = index[from.lowercase()] ?: return false
        writer.copy(to) { files.open(source) }
        written++
        return true
    }

    fun convert(info: ModInfo): ModConversionResult {
        val metadata = readMetadata()
        val stageZooms = convertStages()
        val icons = convertCharacters(metadata)
        val songInfo = convertSongs(metadata, stageZooms, icons)
        convertLevels(songInfo)
        convertScripts()
        copyAssets()
        writePack(info)
        report.stat("Songs", scan.songs.size)
        report.stat("Characters", scan.characters.size)
        report.stat("Stages", scan.stages.size)
        report.stat("Weeks", scan.levels.size)
        report.stat("Scripts", scan.scripts.size)
        report.stat("Files", written)
        if (scan.placeholders.isNotEmpty()) report.section("Empty placeholder folders removed", scan.placeholders.map { (dir, file) -> "$dir/ (only ${file.substringAfterLast('/')})" })
        if (scan.ignored.isNotEmpty()) report.section("Other files skipped", scan.ignored.take(40) + if (scan.ignored.size > 40) listOf("…and ${scan.ignored.size - 40} more") else emptyList())
        return ModConversionResult(written, report.build())
    }

    private fun readMetadata(): Map<String, Map<String, VMetadata>> = scan.songs.associate { song ->
        song.id to song.metadata.mapNotNull { (variation, path) ->
            runCatching { variation to VSliceParser.metadata(files.readText(path)) }
                .onFailure { report.warn("Metadata ${scan.relative(path)} could not be read: ${it.message}") }
                .getOrNull()
        }.toMap()
    }

    private fun convertStages(): Map<String, Double> {
        val zooms = HashMap<String, Double>()
        scan.stages.forEach { path ->
            val id = path.substringAfterLast('/').removeSuffix(".json")
            step("Stage $id")
            try {
                val stage = VSliceParser.stage(files.readText(path))
                zooms[id] = stage.cameraZoom
                val result = VSliceToPsychStage.convert(id, stage, ::stageAtlas)
                write(result.path, result.json)
                result.extra.forEach { (path, text) -> write(path, text) }
                result.report.warnings.forEach { report.warn("Stage $id: $it") }
                result.report.sections.filter { it.title == "Notes" }.forEach { section -> section.lines.filter { it.startsWith("stages/") }.takeIf { it.isNotEmpty() }?.let { report.section("Stage $id", it) } }
            } catch (e: Exception) {
                report.warn("Stage \"$id\" was not converted: ${e.message}")
            }
        }
        return zooms
    }

    private fun convertCharacters(metadata: Map<String, Map<String, VMetadata>>): Map<String, String> {
        val usage = HashMap<String, MutableMap<CharacterSlot, Int>>()
        metadata.values.flatMap { it.values }.forEach { meta ->
            fun count(id: String, slot: CharacterSlot) {
                if (id.isEmpty()) return
                val map = usage.getOrPut(id) { mutableMapOf() }
                map[slot] = (map[slot] ?: 0) + 1
            }
            count(meta.player, CharacterSlot.PLAYER)
            count(meta.opponent, CharacterSlot.OPPONENT)
            count(meta.girlfriend, CharacterSlot.GIRLFRIEND)
        }
        val icons = HashMap<String, String>()
        scan.characters.forEach { path ->
            val id = path.substringAfterLast('/').removeSuffix(".json")
            step("Character $id")
            try {
                val character = VSliceParser.character(files.readText(path))
                val slot = usage[id]?.maxByOrNull { it.value }?.key ?: VSliceToPsychCharacter.defaultSlot(id)
                val atlases = HashMap<String, SparrowAtlas>()
                (listOf(character.assetPath) + character.animations.map { it.assetPath }).filter { it.isNotBlank() }.distinct().forEach { asset ->
                    val xml = index["${scan.root}images/$asset.xml".lowercase()]
                    if (xml != null) runCatching { atlases[VSliceToPsychCharacter.atlasKey(asset)] = SparrowAtlas.parse(files.readText(xml)) }
                }
                val result = VSliceToPsychCharacter.convert(id, character, slot, atlases)
                write(result.path, result.json)
                icons[id] = character.healthIcon.ifEmpty { id }
                result.report.warnings.forEach { report.warn("Character $id: $it") }
            } catch (e: Exception) {
                report.warn("Character \"$id\" was not converted: ${e.message}")
            }
        }
        return icons
    }

    private fun convertSongs(metadata: Map<String, Map<String, VMetadata>>, zooms: Map<String, Double>, icons: Map<String, String>): Map<String, Map<String, LevelSongInfo>> {
        val result = HashMap<String, MutableMap<String, LevelSongInfo>>()
        scan.songs.forEach { song ->
            step("Song ${song.id}")
            val metas = metadata[song.id] ?: emptyMap()
            val sources = song.charts.mapNotNull { (variation, path) ->
                val meta = metas[variation] ?: return@mapNotNull null.also { report.warn("Song ${song.id}: chart ${scan.relative(path)} has no matching metadata file.") }
                runCatching { VSliceSongSource(variation, meta, VSliceParser.chart(files.readText(path))) }
                    .onFailure { report.warn("Chart ${scan.relative(path)} could not be read: ${it.message}") }
                    .getOrNull()
            }
            if (sources.isEmpty()) return@forEach
            sources.groupBy { it.variation }.forEach { (variation, group) ->
                val meta = group.first().metadata
                val psychId = VSliceToPsychChart.songIdFor(song.id, variation)
                val converted = VSliceToPsychChart.convert(psychId, group, zooms[meta.stage])
                converted.files.forEach { write(it.path, it.json) }
                converted.report.warnings.forEach { report.warn("Song $psychId: $it") }
                result.getOrPut(song.id) { mutableMapOf() }[variation] =
                    LevelSongInfo(converted.songName, icons[meta.opponent] ?: meta.opponent, converted.difficulties)
                copyAudio(song, variation, psychId, meta)
            }
        }
        return result
    }

    private fun copyAudio(song: VSliceSongFiles, variation: String, psychId: String, meta: VMetadata) {
        val suffix = if (variation.isEmpty()) "" else "-$variation"
        val byName = song.audio.associateBy { it.substringAfterLast('/').substringBeforeLast('.').lowercase() }
        fun find(name: String): String? = byName[(name + suffix).lowercase()] ?: if (suffix.isNotEmpty()) null else byName[name.lowercase()]
        fun ext(path: String) = path.substringAfterLast('.').lowercase()
        val inst = find("Inst") ?: byName["inst"]
        if (inst != null) {
            writer.copy("songs/$psychId/Inst.${ext(inst)}") { files.open(inst) }
            written++
        } else {
            report.warn("Song $psychId: no Inst audio found.")
        }
        val player = (meta.playerVocals ?: listOf(meta.player)).firstNotNullOfOrNull { find("Voices-$it") }
        val opponent = (meta.opponentVocals ?: listOf(meta.opponent)).firstNotNullOfOrNull { find("Voices-$it") }
        if (player != null) {
            writer.copy("songs/$psychId/Voices-Player.${ext(player)}") { files.open(player) }
            written++
        }
        if (opponent != null) {
            writer.copy("songs/$psychId/Voices-Opponent.${ext(opponent)}") { files.open(opponent) }
            written++
        }
        if (player == null && opponent == null) {
            find("Voices")?.let { voices ->
                writer.copy("songs/$psychId/Voices.${ext(voices)}") { files.open(voices) }
                written++
            }
        }
    }

    private fun convertLevels(songs: Map<String, Map<String, LevelSongInfo>>) {
        scan.levels.forEach { path ->
            val id = path.substringAfterLast('/').removeSuffix(".json")
            step("Week $id")
            try {
                val level = VSliceParser.level(files.readText(path))
                val base = level.songs.associateWith { songs[it]?.get("") }.filterValues { it != null }.mapValues { it.value!! }
                val result = VSliceToPsychLevel.convert(id, level, base)
                write(result.path, result.json)
                result.extra.forEach { (p, text) -> write(p, text) }
                result.copies.forEach { (from, to) -> copy(scan.root + from, to) }
                result.report.warnings.forEach { report.warn("Week $id: $it") }
                val variations = level.songs.flatMap { songs[it]?.keys ?: emptySet() }.filter { it.isNotEmpty() }.distinct()
                variations.forEach { variation ->
                    val ids = level.songs.filter { songs[it]?.containsKey(variation) == true }
                    val infos = ids.associate { VSliceToPsychChart.songIdFor(it, variation) to songs.getValue(it).getValue(variation) }
                    val variant = level.copy(songs = infos.keys.toList(), visible = false)
                    val extra = VSliceToPsychLevel.convert("$id-$variation", variant, infos)
                    write(extra.path, extra.json)
                }
                if (variations.isNotEmpty()) report.section("Week $id · variations", variations.map { "weeks/$id-$it.json (freeplay only)" })
            } catch (e: Exception) {
                report.warn("Week \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertScripts() {
        val results = mutableListOf<Pair<String, HxcResult>>()
        val failures = mutableListOf<String>()
        scan.scripts.forEach { path ->
            step("Script ${path.substringAfterLast('/')}")
            try {
                val result = HxcToPsych.convert(path.substringAfterLast('/'), files.readText(path))
                result.files.forEach { write(it.path, it.content) }
                results += scan.relative(path) to result
            } catch (e: Exception) {
                failures += "${scan.relative(path)}: ${e.message}"
            }
        }
        if (results.isNotEmpty() || failures.isNotEmpty()) {
            val scriptReport = HxcToPsych.report(results, failures)
            scriptReport.sections.filter { it.title != "How it runs" }.forEach { report.section("Script · ${it.title}", it.lines) }
            scriptReport.warnings.forEach(report::warn)
        }
    }

    private fun copyAssets() {
        scan.assets.forEach { path ->
            val rel = scan.relative(path)
            writer.copy(rel) { files.open(path) }
            written++
            done++
        }
        onProgress(ModProgress(done.coerceAtMost(total), total, "Assets"))
    }

    private fun writePack(info: ModInfo) {
        val pack = JSONObject()
            .put("name", info.title.ifBlank { "Converted Mod" })
            .put("description", info.description)
            .put("restart", false)
            .put("runsGlobally", false)
            .put("color", JSONArray(listOf(170, 0, 255)))
            .put("iconFramerate", 10)
        write("pack.json", pack.toString(2))
        scan.icon?.let {
            writer.copy("pack.png") { files.open(it) }
            written++
        }
        onProgress(ModProgress(total, total, "Done"))
    }
}
