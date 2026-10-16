package com.brenninhotools.vconverter.mod

import com.brenninhotools.vconverter.character.CharacterSource
import com.brenninhotools.vconverter.character.PsychCharacter
import com.brenninhotools.vconverter.character.PsychCharacterParser
import com.brenninhotools.vconverter.character.PsychToVSliceCharacter
import com.brenninhotools.vconverter.chart.ChartSource
import com.brenninhotools.vconverter.chart.ConversionOptions
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.chart.psych.PsychChartParser
import com.brenninhotools.vconverter.chart.psych.PsychSong
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.core.json.asText
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptSource
import com.brenninhotools.vconverter.core.json.value
import com.brenninhotools.vconverter.stage.PsychStageParser
import com.brenninhotools.vconverter.stage.PsychToVSliceStage
import com.brenninhotools.vconverter.stage.StageSource
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource
import org.json.JSONArray
import org.json.JSONObject

data class ModInfo(
    val title: String,
    val description: String,
    val author: String,
    val version: String = "1.0.0"
)

data class ModProgress(val done: Int, val total: Int, val label: String)

data class ModConversionResult(val filesWritten: Int, val report: Report)

class ModConverter(
    private val files: ModFiles,
    private val scan: ModScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    private val report = ReportBuilder()
    private val pathIndex: Map<String, String> = files.paths.associateBy { it.lowercase() }
    private var written = 0
    private var keptPsychEvents = 0
    private val keptEventNames = sortedSetOf<String>()
    private var done = 0
    private val total = scan.songs.size + scan.characters.size + scan.stages.size + scan.weeks.size + scan.assets.size + scan.scripts.size + 2

    fun convert(info: ModInfo): ModConversionResult {
        val characters = parseCharacters()
        val charts = parseCharts()
        val stageIds = convertStages()
        convertSongs(charts, characters, stageIds)
        convertCharacters(characters, charts)
        convertWeeks(charts.keys)
        convertScripts(info, charts)
        copyAssets()
        writeMeta(info)

        report.stat("Songs", charts.size)
        report.stat("Characters", characters.size)
        report.stat("Stages", stageIds.size)
        report.stat("Weeks", scan.weeks.size)
        report.stat("Assets", scan.assets.size)
        report.stat("Files", written)


        if (scan.ignored.isNotEmpty()) {
            report.section("Other files skipped", limited(scan.ignored.map(scan::relative)))
        }
        if (scan.otherRoots.isNotEmpty()) {
            report.warn("The archive has more than one mod. Only \"${scan.root.ifEmpty { "/" }}\" was converted; also found: ${scan.otherRoots.joinToString()}.")
        }
        if (scan.weekList != null) {
            report.warn("Psych's weekList.txt order is not used by V-Slice; levels are listed in their own order.")
        }

        return ModConversionResult(written, report.build())
    }

    private fun parseCharacters(): Map<String, PsychCharacter> {
        val result = LinkedHashMap<String, PsychCharacter>()
        scan.characters.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            try {
                result[id] = PsychCharacterParser.parse(files.readText(path))
            } catch (e: Exception) {
                report.warn("Character \"$id\" could not be read: ${e.message}")
            }
        }
        return result
    }

    private fun parseCharts(): Map<String, Pair<List<ChartSource>, PsychSong>> {
        val result = LinkedHashMap<String, Pair<List<ChartSource>, PsychSong>>()
        scan.songs.forEach { song ->
            val sources = song.charts.mapNotNull { (difficulty, path) ->
                try {
                    ChartSource(difficulty, PsychChartParser.parse(files.readText(path)))
                } catch (e: Exception) {
                    report.warn("Chart ${scan.relative(path)} could not be read: ${e.message}")
                    null
                }
            }
            if (sources.isNotEmpty()) {
                val main = sources.firstOrNull { it.difficulty == "normal" } ?: sources.first()
                result[song.id] = sources to main.song
            }
        }
        return result
    }

    private fun convertStages(): Set<String> {
        val ids = LinkedHashSet<String>()
        scan.stages.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            step("Stage $id")
            try {
                val result = PsychToVSliceStage.convert(StageSource(id, PsychStageParser.parse(files.readText(path))))
                writer.writeText("data/stages/$id.json", result.json)
                written++
                ids += id
                result.report.warnings.forEach { report.warn("Stage $id: $it") }
            } catch (e: Exception) {
                report.warn("Stage \"$id\" was not converted: ${e.message}")
            }
        }
        return ids
    }

    private fun convertSongs(
        charts: Map<String, Pair<List<ChartSource>, PsychSong>>,
        characters: Map<String, PsychCharacter>,
        stageIds: Set<String>
    ) {
        scan.songs.forEach { scanned ->
            step("Song ${scanned.id}")
            val (sources, main) = charts[scanned.id] ?: return@forEach
            try {
                val audioNames = scanned.audio.map { it.substringAfterLast('/').substringBeforeLast('.').lowercase() }.toSet()
                val playerKey = characters[main.player1]?.vocalsFile?.takeIf { it.isNotBlank() } ?: "Player"
                val opponentKey = characters[main.player2]?.vocalsFile?.takeIf { it.isNotBlank() } ?: "Opponent"
                val extraEvents = scanned.events?.let { path ->
                    runCatching { PsychChartParser.parseEventsOnly(files.readText(path)) }
                        .onFailure { report.warn("Events for ${scanned.id} could not be read: ${it.message}") }
                        .getOrNull()
                } ?: emptyList()
                val conversion = PsychToVSliceChart.convert(
                    sources,
                    ConversionOptions(
                        songName = main.name,
                        extraEvents = extraEvents,
                        customStageIds = stageIds,
                        playerVocals = listOf(playerKey).takeIf { "voices-${playerKey.lowercase()}" in audioNames },
                        opponentVocals = listOf(opponentKey).takeIf { "voices-${opponentKey.lowercase()}" in audioNames }
                    )
                )
                writer.writeText("data/songs/${scanned.id}/${scanned.id}-metadata.json", VSliceWriter.metadataJson(conversion.metadata))
                writer.writeText("data/songs/${scanned.id}/${scanned.id}-chart.json", VSliceWriter.chartJson(conversion.chart))
                written += 2
                keptPsychEvents += conversion.report.keptPsychEvents
                keptEventNames += conversion.report.unsupportedEvents.keys
                conversion.report.warnings.forEach { report.warn("Song ${scanned.id}: $it") }
                if (conversion.report.unsupportedEvents.isNotEmpty()) {
                    report.section(
                        "Song ${scanned.id} · Psych events kept for scripts",
                        conversion.report.unsupportedEvents.map { (name, count) -> "$name ×$count" }
                    )
                }
                if (scanned.audio.none { it.substringAfterLast('/').startsWith("Inst", ignoreCase = true) }) {
                    report.warn("Song ${scanned.id}: no Inst audio found in songs/${scanned.id}/.")
                }
            } catch (e: Exception) {
                report.warn("Song \"${scanned.id}\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertCharacters(
        characters: Map<String, PsychCharacter>,
        charts: Map<String, Pair<List<ChartSource>, PsychSong>>
    ) {
        val usage = HashMap<String, MutableMap<CharacterSlot, Int>>()
        fun count(id: String, slot: CharacterSlot) {
            val slots = usage.getOrPut(id) { mutableMapOf() }
            slots[slot] = (slots[slot] ?: 0) + 1
        }
        charts.values.forEach { (_, song) ->
            count(song.player1, CharacterSlot.PLAYER)
            count(song.player2, CharacterSlot.OPPONENT)
            count(song.girlfriend, CharacterSlot.GIRLFRIEND)
        }

        var approximate = 0
        characters.forEach { (id, character) ->
            step("Character $id")
            try {
                val slot = usage[id]?.maxByOrNull { it.value }?.key ?: PsychToVSliceCharacter.defaultSlot(id, character)
                val atlases = character.atlasImages.ifEmpty { listOf(character.image) }.mapNotNull { image ->
                    val path = pathIndex["${scan.root}images/$image.xml".lowercase()] ?: return@mapNotNull null
                    PsychToVSliceCharacter.atlasKey(image) to SparrowAtlas.parse(files.readText(path))
                }.toMap()
                val result = PsychToVSliceCharacter.convert(CharacterSource(id, character, slot, atlases))
                writer.writeText("data/characters/$id.json", result.json)
                written++
                if (result.report.stats.any { it.label == "Accuracy" && it.value != "Exact" }) approximate++
                result.report.warnings
                    .filterNot { it.startsWith("Spritesheet XML not provided") }
                    .forEach { report.warn("Character $id: $it") }
            } catch (e: Exception) {
                report.warn("Character \"$id\" was not converted: ${e.message}")
            }
        }
        if (approximate > 0) {
            report.warn("$approximate character(s) use an Animate atlas or a missing spritesheet XML, so their positions are approximate.")
        }
    }

    private fun convertWeeks(songIds: Set<String>) {
        val menu = LinkedHashMap<String, PsychMenuCharacter>()
        scan.menuCharacters.forEach { path ->
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            runCatching { PsychWeekParser.parseMenuCharacter(files.readText(path)) }
                .onSuccess { menu[name] = it }
        }
        scan.weeks.forEach { path ->
            val id = path.substringAfterLast('/').substringBeforeLast('.')
            step("Week $id")
            try {
                val result = PsychToVSliceLevel.convert(WeekSource(id, PsychWeekParser.parse(files.readText(path)), menu))
                writer.writeText("data/levels/$id.json", result.json)
                written++
                result.report.warnings.forEach { report.warn("Week $id: $it") }
                val missing = result.songIds.filter { it !in songIds }
                if (missing.isNotEmpty()) {
                    report.warn("Week $id lists songs with no chart in this mod: ${missing.joinToString()}.")
                }
            } catch (e: Exception) {
                report.warn("Week \"$id\" was not converted: ${e.message}")
            }
        }
    }

    private fun convertScripts(info: ModInfo, charts: Map<String, Pair<List<ChartSource>, PsychSong>>) {
        val scriptFiles = scan.scripts.filter { it.substringAfterLast('.').lowercase() in setOf("lua", "hx", "hxs", "hscript") }
        val noteTypeNames = linkedSetOf<String>()
        charts.values.forEach { (sources, _) ->
            sources.forEach { source -> source.song.sections.forEach { section -> section.notes.forEach { if (it.type.isNotBlank()) noteTypeNames += it.type } } }
        }
        scriptFiles.filter { "/custom_notetypes/" in "/" + scan.relative(it).lowercase() }.forEach { noteTypeNames += it.substringAfterLast('/').substringBeforeLast('.') }
        val keptEvents = keptPsychEvents > 0
        if (scriptFiles.isEmpty() && !keptEvents) return
        val slug = ScriptConverter.slug(info.title.ifBlank { "Mod" })
        val sources = scriptFiles.mapNotNull { path ->
            runCatching { ScriptSource(scan.relative(path), files.readText(path)) }.getOrNull()
        }
        sources.forEach { step("Script ${it.path.substringAfterLast('/')}") }
        val bundle = ScriptConverter.convert(slug, sources, ScriptConverter.noteTypeMap(noteTypeNames))
        val outputs = bundle.files + ScriptConverter.eventFiles(keptEventNames)
        outputs.forEach { file ->
            writer.writeText(file.path, file.content)
            written++
        }
        report.stat("Scripts", bundle.converted)
        bundle.report.sections.forEach { report.section("Script · ${it.title}", it.lines) }
        bundle.report.warnings.forEach { report.warn(it) }
        val others = scan.scripts.size - scriptFiles.size
        if (others > 0) report.warn("$others other script files (not .lua or .hx) were skipped.")
    }

    private fun copyAssets() {
        scan.assets.forEach { path ->
            val target = scan.relative(path)
            step(target)
            try {
                writer.copy(target) { files.open(path) }
                written++
            } catch (e: Exception) {
                report.warn("Could not copy $target: ${e.message}")
            }
        }
        scan.packIcon?.let { icon ->
            runCatching {
                writer.copy("_polymod_icon.png") { files.open(icon) }
                written++
            }
        }
    }

    private fun writeMeta(info: ModInfo) {
        step("_polymod_meta.json")
        val contributors = JSONArray()
        if (info.author.isNotBlank()) contributors.put(JSONObject().put("name", info.author))
        val meta = JSONObject()
            .put("title", info.title.ifBlank { "Converted Mod" })
            .put("description", info.description)
            .put("contributors", contributors)
            .put("homepage", "")
            .put("api_version", API_VERSION)
            .put("mod_version", info.version)
            .put("license", "")
        writer.writeText("_polymod_meta.json", meta.toString(2))
        written++
        done = total
        onProgress(ModProgress(total, total, "Done"))
    }

    private fun step(label: String) {
        done++
        onProgress(ModProgress(done.coerceAtMost(total), total, label))
    }

    private fun limited(lines: List<String>, max: Int = 40): List<String> =
        if (lines.size <= max) lines else lines.take(max) + "…and ${lines.size - max} more"

    companion object {
        const val API_VERSION = "0.8.0"

        fun readPack(files: ModFiles, scan: ModScan): ModInfo? {
            val path = scan.pack ?: return null
            return runCatching {
                val json = JSONObject(files.readText(path).trim().removePrefix("﻿"))
                ModInfo(
                    title = json.value("name").asText(),
                    description = json.value("description").asText(),
                    author = ""
                )
            }.getOrNull()
        }
    }
}
