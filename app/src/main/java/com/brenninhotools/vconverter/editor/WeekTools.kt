package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.codename.CodenameToVSliceLevel
import com.brenninhotools.vconverter.codename.CodenameWeekParser
import com.brenninhotools.vconverter.codename.PsychToCodenameWeek
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.reverse.LevelSongInfo
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.reverse.VSliceToPsychLevel
import com.brenninhotools.vconverter.week.PsychMenuCharacter
import com.brenninhotools.vconverter.week.PsychToVSliceLevel
import com.brenninhotools.vconverter.week.PsychWeekParser
import com.brenninhotools.vconverter.week.WeekSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LoadedWeek(val document: WeekDocument, val assets: Map<String, StageAsset>, val source: String)

object WeekLoader {
    private fun stem(path: String) = path.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')

    private fun isVSliceLevel(text: String): Boolean = runCatching {
        val root = JSONObject(text.trim().removePrefix("﻿"))
        val songs = root.optJSONArray("songs") ?: return false
        (root.has("titleAsset") || root.has("props")) && (songs.length() == 0 || songs.opt(0) is String)
    }.getOrDefault(false)

    private fun isPsychWeek(text: String): Boolean = runCatching {
        val root = JSONObject(text.trim().removePrefix("﻿"))
        val songs = root.optJSONArray("songs") ?: return false
        root.has("weekCharacters") || (songs.length() > 0 && songs.opt(0) is JSONArray)
    }.getOrDefault(false)

    fun load(files: List<Pair<String, ByteArray>>): LoadedWeek {
        val assets = StageAssets.collect(files)
        val texts = files.filter { it.first.substringAfterLast('.').lowercase() in setOf("json", "xml", "hxc") }.map { it.first to it.second.toString(Charsets.UTF_8) }
        val jsons = texts.filter { it.first.endsWith(".json", true) }
        val xmls = texts.filter { it.first.endsWith(".xml", true) }

        jsons.firstOrNull { isVSliceLevel(it.second) }?.let { (path, text) ->
            val id = Ids.of(stem(path)).ifEmpty { "week" }
            var document = WeekDocument.fromVSlice(id, text)
            val metadata = jsons.mapNotNull { (_, t) -> runCatching { VSliceParser.metadata(t) }.getOrNull() }
                .associateBy { SongPath.format(it.songName) }
            val metaFiles = jsons.mapNotNull { (p, t) ->
                val songId = stem(p).substringBefore("-metadata").takeIf { stem(p).contains("-metadata") } ?: return@mapNotNull null
                runCatching { songId to VSliceParser.metadata(t) }.getOrNull()
            }.toMap()
            val difficulties = linkedSetOf<String>()
            document = document.copy(songs = document.songs.map { song ->
                val meta = metaFiles[song.id] ?: metadata[song.id]
                meta?.difficulties?.let { difficulties += it }
                if (meta == null) song else song.copy(name = meta.songName, icon = meta.opponent)
            })
            if (difficulties.isNotEmpty()) document = document.copy(difficulties = difficulties.toList())
            texts.filter { it.first.endsWith(".hxc", true) && Regex("super\\(\\s*[\"']${Regex.escape(id)}[\"']").containsMatchIn(it.second) && it.second.contains("extends Level") }
                .firstOrNull()?.second?.let { script ->
                    val before = Regex("hasBeatenLevel\\(\\s*[\"']([^\"']+)[\"']").find(script)?.groupValues?.get(1)
                    document = document.copy(
                        weekBefore = before ?: "",
                        startUnlocked = before == null,
                        hiddenUntilUnlocked = before != null && script.contains("isVisible"),
                        hideFreeplay = Regex("return\\s*\\[\\s*]\\s*;").findAll(script).any() && !script.contains("isUnlocked() ? songs")
                    )
                }
            return LoadedWeek(document, assets, "V-Slice")
        }

        jsons.firstOrNull { isPsychWeek(it.second) }?.let { (path, text) ->
            val id = Ids.of(stem(path)).ifEmpty { "week" }
            val week = PsychWeekParser.parse(text)
            val menus = jsons.filter { PsychWeekParser.isMenuCharacter(it.second) }
                .mapNotNull { (p, t) -> runCatching { stem(p) to PsychWeekParser.parseMenuCharacter(t) }.getOrNull() }.toMap()
            val level = PsychToVSliceLevel.convert(WeekSource(id, week, menus))
            val base = WeekDocument.fromVSlice(id, level.json).copy(rawJson = null)
            val document = base.copy(
                weekName = week.weekName,
                songs = week.songs.map { WeekSong(SongPath.format(it.name), it.name, it.icon, WeekDocument.hex(it.color)) },
                difficulties = week.difficulties.map { it.lowercase(Locale.US) }.ifEmpty { WeekDocument.DEFAULT_DIFFICULTIES },
                visible = !week.hideStoryMode,
                hideFreeplay = week.hideFreeplay,
                weekBefore = week.weekBefore,
                startUnlocked = week.startUnlocked,
                hiddenUntilUnlocked = week.hiddenUntilUnlocked
            )
            return LoadedWeek(document, assets, "Psych Engine")
        }

        xmls.firstOrNull { CodenameWeekParser.isWeekXml(it.second) }?.let { (path, text) ->
            val id = Ids.of(stem(path)).ifEmpty { "week" }
            val week = CodenameWeekParser.parseWeek(id, text)
            val characters = xmls.filter { CodenameWeekParser.isMenuCharacterXml(it.second) }
                .mapNotNull { (p, t) -> runCatching { CodenameWeekParser.parseMenuCharacter(stem(p), t) }.getOrNull() }
                .associateBy { it.name }
            val level = CodenameToVSliceLevel.convert(week, characters)
            val base = WeekDocument.fromVSlice(id, level.json).copy(rawJson = null)
            val document = base.copy(
                songs = week.songs.map { WeekSong(SongPath.format(it.name), it.displayName ?: it.name) },
                difficulties = week.difficulties.map { it.lowercase(Locale.US) },
                hideFreeplay = week.songs.isNotEmpty() && week.songs.all { it.hidden }
            )
            return LoadedWeek(document, assets, "Codename Engine")
        }

        throw IllegalArgumentException("No week found. Pick a V-Slice level .json, a Psych week .json or a Codename week .xml, with its images.")
    }
}

object WeekExport {
    private fun imagePath(raw: String) = raw.trim().replace('\\', '/').substringAfter(':').removePrefix("images/").removeSuffix(".png")

    private fun capitalize(value: String) = value.replaceFirstChar { it.titlecase(Locale.US) }

    fun build(document: WeekDocument, target: ExportTarget, assets: Map<String, StageAsset>): ConvertedOutput {
        require(document.id.isNotBlank()) { "Enter a week id." }
        require(document.songs.isNotEmpty()) { "Add at least one song." }
        val duplicate = document.songs.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }?.key
        require(duplicate == null) { "The song \"$duplicate\" is listed twice." }
        val report = ReportBuilder()
        val files = ArrayList<OutputFile>()
        val missing = ArrayList<String>()
        val written = HashSet<String>()

        fun image(path: String, to: String = path) {
            val clean = imagePath(path)
            if (clean.isEmpty() || !written.add(to.lowercase())) return
            val asset = StageAssets.find(assets, clean)
            if (asset == null) {
                missing += "images/$to.png"
                return
            }
            files += OutputFile.of("images/$to.png", null, asset.png)
            asset.xml?.let { files += OutputFile.of("images/$to.xml", it, null) }
        }

        val vslice = document.toVSlice()
        when (target) {
            ExportTarget.VSLICE -> {
                files += OutputFile.of("data/levels/${document.id}.json", vslice, null)
                if (document.isLocked || document.hideFreeplay) {
                    val className = PsychToVSliceLevel.levelClass(document.id)
                    files += OutputFile.of("scripts/levels/$className.hxc", PsychToVSliceLevel.levelScript(className, document.id, document.toPsychWeek(), document.isLocked), null)
                    report.section(
                        "Level script (scripts/levels/$className.hxc)",
                        listOfNotNull(
                            if (document.isLocked) "Unlocks after beating \"${document.weekBefore.trim()}\"" else null,
                            if (document.isLocked && document.hiddenUntilUnlocked) "Hidden from the story menu until unlocked" else null,
                            if (document.isLocked) "Its songs stay out of freeplay while it is locked" else null,
                            if (document.hideFreeplay) "Its songs are hidden from freeplay but still play in story mode" else null
                        )
                    )
                }
                image(document.title)
                if (!document.backgroundIsColor) image(document.background)
                WeekSlot.entries.mapNotNull { document.prop(it) }.filterNot { it.isBase }.forEach { image(it.assetPath) }
                if (document.difficulties != WeekDocument.DEFAULT_DIFFICULTIES) report.section("Difficulties", listOf("V-Slice levels have no difficulty list; each song's metadata lists ${document.difficulties.joinToString()}."))
            }
            ExportTarget.PSYCH, ExportTarget.CODENAME -> {
                val psych = psych(document, vslice, report)
                if (target == ExportTarget.PSYCH) {
                    psych.texts.forEach { (path, text) -> files += OutputFile.of(path, text, null) }
                    psych.copies.forEach { (from, to) -> if (from.endsWith(".png", true)) image(from.removeSuffix(".png"), imagePath(to)) }
                } else {
                    val week = PsychWeekParser.parse(psych.texts.first().second)
                    val result = PsychToCodenameWeek.convert(document.id, week)
                    result.files.forEach { files += OutputFile.of(it.path, it.text, null) }
                    result.report.warnings.forEach(report::warn)
                    result.report.sections.forEach { report.section(it.title, it.lines) }
                    psych.texts.drop(1).forEach { (path, text) ->
                        val name = path.substringAfterLast('/').substringBeforeLast('.')
                        val menu: PsychMenuCharacter = PsychWeekParser.parseMenuCharacter(text)
                        PsychToCodenameWeek.menuCharacter(name, menu).files.forEach { files += OutputFile.of(it.path, it.text, null) }
                    }
                    val psychToSource = psych.copies.associate { (from, to) -> to.lowercase() to from }
                    psych.copies.filter { it.second.startsWith("images/menucharacters/") && it.first.endsWith(".png", true) }
                        .forEach { (from, to) -> image(from.removeSuffix(".png"), imagePath(to)) }
                    result.copies.forEach { copy ->
                        val source = psychToSource[copy.from.lowercase()] ?: return@forEach
                        image(source.removeSuffix(".png"), imagePath(copy.to))
                    }
                }
            }
        }
        report.stat("Songs", document.songs.size)
        report.stat("Menu characters", WeekSlot.entries.count { document.prop(it) != null })
        report.stat("Files", files.size)
        if (missing.isNotEmpty()) report.section("Images to add", missing.distinct())
        return ConvertedOutput(files, report.build())
    }

    private class PsychWeekFiles(val texts: List<Pair<String, String>>, val copies: List<Pair<String, String>>)

    private fun psych(document: WeekDocument, vslice: String, report: ReportBuilder): PsychWeekFiles {
        val songs = document.songs.associate { it.id to LevelSongInfo(it.displayName, it.icon.ifBlank { "face" }, document.difficulties) }
        val result = VSliceToPsychLevel.convert(document.id, VSliceParser.level(vslice), songs)
        result.report.warnings.filterNot { it.contains("backgrounds are images") || it.startsWith("No metadata") }.forEach(report::warn)
        val root = JSONObject(result.json)
        val songArray = JSONArray()
        document.songs.forEach { song -> songArray.put(JSONArray().put(song.displayName).put(song.icon.ifBlank { "face" }).put(JSONArray(WeekDocument.rgb(song.color)))) }
        val copies = result.copies.toMutableList()
        val background = when {
            document.backgroundIsColor -> {
                if (!document.background.trim().equals(WeekDocument.DEFAULT_BACKGROUND, true)) report.warn("Psych week backgrounds are images; the color ${document.background} becomes Psych's default yellow.")
                ""
            }
            else -> {
                val name = imagePath(document.background).substringAfterLast('/').removePrefix("menu_")
                copies += "images/${imagePath(document.background)}.png" to "images/menubackgrounds/menu_$name.png"
                name
            }
        }
        root.put("songs", songArray)
            .put("weekBackground", background)
            .put("weekName", document.weekName.ifBlank { document.storyName })
            .put("weekBefore", document.weekBefore.trim())
            .put("startUnlocked", document.startUnlocked || document.weekBefore.isBlank())
            .put("hiddenUntilUnlocked", document.hiddenUntilUnlocked)
            .put("hideFreeplay", document.hideFreeplay)
            .put("difficulties", if (document.difficulties == WeekDocument.DEFAULT_DIFFICULTIES) "" else document.difficulties.joinToString(",") { capitalize(it) })
        val texts = listOf(result.path to root.toString(2)) + result.extra
        return PsychWeekFiles(texts, copies)
    }
}
