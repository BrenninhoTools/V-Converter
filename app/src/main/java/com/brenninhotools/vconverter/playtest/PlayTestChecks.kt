package com.brenninhotools.vconverter.playtest

import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.LoadedCharacter
import com.brenninhotools.vconverter.editor.LoadedStage
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAssets
import com.brenninhotools.vconverter.core.fmt

enum class IssueLevel { ERROR, WARNING, INFO }

data class PlayIssue(val level: IssueLevel, val area: String, val message: String)

object PlayTestChecks {
    private fun time(ms: Double): String {
        val total = ms.coerceAtLeast(0.0).toLong()
        return fmt("%d:%02d.%03d", total / 60000, (total / 1000) % 60, total % 1000)
    }

    fun chart(document: ChartDocument, audio: List<EditorAudio>): List<PlayIssue> {
        val issues = ArrayList<PlayIssue>()
        val area = "Chart"
        if (document.difficulties.values.all { it.notes.isEmpty() }) issues += PlayIssue(IssueLevel.ERROR, area, "No difficulty has notes.")
        document.timing.forEach { t ->
            if (t.bpm <= 0.0 || t.bpm > 1000.0) issues += PlayIssue(IssueLevel.ERROR, area, "BPM ${t.bpm} at ${time(t.time)} is out of range.")
        }
        if (document.timing.firstOrNull()?.time?.let { it > 0.0 } == true) issues += PlayIssue(IssueLevel.WARNING, area, "The first BPM change starts at ${time(document.timing.first().time)}, not at 0.")
        document.difficulties.forEach { (name, chart) ->
            if (chart.notes.isEmpty()) {
                issues += PlayIssue(IssueLevel.WARNING, area, "\"$name\" has no notes.")
                return@forEach
            }
            if (chart.speed <= 0.0) issues += PlayIssue(IssueLevel.WARNING, area, "\"$name\" has a scroll speed of ${chart.speed}.")
            val byLane = chart.notes.groupBy { it.data }
            var stacked = 0
            var firstStack: Double? = null
            var overlaps = 0
            var firstOverlap: Double? = null
            byLane.values.forEach { lane ->
                val sorted = lane.sortedBy { it.time }
                sorted.zipWithNext().forEach { (a, b) ->
                    if (b.time - a.time < 2.0) {
                        stacked++
                        if (firstStack == null || a.time < firstStack!!) firstStack = a.time
                    } else if (a.length > 0.0 && a.time + a.length > b.time - 10.0) {
                        overlaps++
                        if (firstOverlap == null || a.time < firstOverlap!!) firstOverlap = a.time
                    }
                }
            }
            if (stacked > 0) issues += PlayIssue(IssueLevel.WARNING, area, "\"$name\": $stacked stacked note(s) on the same lane, first at ${time(firstStack ?: 0.0)}.")
            if (overlaps > 0) issues += PlayIssue(IssueLevel.WARNING, area, "\"$name\": $overlaps hold(s) run into the next note on their lane, first at ${time(firstOverlap ?: 0.0)}.")
            val early = chart.notes.count { it.time < 0.0 }
            if (early > 0) issues += PlayIssue(IssueLevel.WARNING, area, "\"$name\": $early note(s) before the song starts.")
            if (chart.notes.none { it.data < 4 }) issues += PlayIssue(IssueLevel.WARNING, area, "\"$name\" has no player notes.")
            if (chart.notes.none { it.data >= 4 }) issues += PlayIssue(IssueLevel.INFO, area, "\"$name\" has no opponent notes.")
        }
        if (document.events.none { it.kind == "FocusCamera" }) issues += PlayIssue(IssueLevel.INFO, area, "No FocusCamera events: the camera stays on the opponent.")
        val unknown = document.events.map { it.kind }.filter { it !in knownEvents }.distinct()
        if (unknown.isNotEmpty()) issues += PlayIssue(IssueLevel.INFO, area, "Events that need a script to do something: ${unknown.joinToString()}.")
        if (audio.isEmpty()) issues += PlayIssue(IssueLevel.WARNING, "Audio", "No audio found (songs/<id>/Inst.ogg): PlayTest runs on a silent clock.")
        else if (audio.none { it.isInstrumental }) issues += PlayIssue(IssueLevel.WARNING, "Audio", "No Inst file among ${audio.joinToString { it.name }}.")
        return issues
    }

    private val knownEvents = setOf("FocusCamera", "PlayAnimation", "SetCameraBop", "ZoomCamera", "ScrollSpeed", "SetHealthIcon")

    private fun missingFrames(atlas: SpriteAtlas?, prefix: String, indices: List<Int>): Boolean = atlas != null && atlas.framesFor(prefix, indices).isEmpty()

    fun character(role: CharacterRole, id: String, loaded: LoadedCharacter?): List<PlayIssue> {
        val area = "${role.label} \"$id\""
        if (loaded == null) return listOf(PlayIssue(IssueLevel.WARNING, area, "No data/characters/$id.json (or it can't be read); a placeholder is drawn."))
        val issues = ArrayList<PlayIssue>()
        val doc = loaded.document
        val atlas = loaded.xml?.let { SpriteAtlas.parse(it) }?.takeIf { it.frames.isNotEmpty() }
        if (loaded.image == null || atlas == null) issues += PlayIssue(IssueLevel.WARNING, area, "Spritesheet images/${doc.assetPath}.png/.xml not found; a placeholder is drawn.")
        val names = doc.animations.map { it.name }.toSet()
        val dances = "danceLeft" in names && "danceRight" in names
        if ("idle" !in names && !dances) issues += PlayIssue(IssueLevel.WARNING, area, "No idle (or danceLeft/danceRight) animation.")
        if (role != CharacterRole.GIRLFRIEND) {
            val sings = listOf("singLEFT", "singDOWN", "singUP", "singRIGHT").filter { it !in names }
            if (sings.isNotEmpty()) issues += PlayIssue(IssueLevel.WARNING, area, "Missing ${sings.joinToString()}.")
        }
        if (role == CharacterRole.PLAYER) {
            val misses = listOf("singLEFTmiss", "singDOWNmiss", "singUPmiss", "singRIGHTmiss").filter { it !in names }
            if (misses.size == 4) issues += PlayIssue(IssueLevel.INFO, area, "No miss animations; misses keep the sing pose.")
        }
        val broken = doc.animations.filter { missingFrames(atlas, it.prefix, it.indices) }.map { it.name }
        if (broken.isNotEmpty()) issues += PlayIssue(IssueLevel.ERROR, area, "No frames in the spritesheet for ${broken.joinToString()}.")
        if (loaded.source != "V-Slice") issues += PlayIssue(IssueLevel.INFO, area, "Read as ${loaded.source}.")
        return issues
    }

    fun stage(id: String, loaded: LoadedStage?): List<PlayIssue> {
        val area = "Stage \"$id\""
        if (loaded == null) return listOf(PlayIssue(IssueLevel.WARNING, area, "No data/stages/$id.json (or it can't be read); characters are placed at the default spots."))
        val issues = ArrayList<PlayIssue>()
        val props = loaded.document.props.filterNot { it.isSolid }
        val missing = props.filter { StageAssets.find(loaded.assets, it.assetPath) == null }.map { it.name }
        if (missing.isNotEmpty()) issues += PlayIssue(IssueLevel.WARNING, area, "${missing.size} prop(s) without an image: ${missing.take(12).joinToString()}${if (missing.size > 12) "…" else ""}.")
        val broken = props.filter { p ->
            p.prefix.isNotEmpty() && missingFrames(StageAssets.find(loaded.assets, p.assetPath)?.xml?.let { SpriteAtlas.parse(it) }, p.prefix, emptyList())
        }.map { it.name }
        if (broken.isNotEmpty()) issues += PlayIssue(IssueLevel.ERROR, area, "No frames for the animation of ${broken.joinToString()}.")
        return issues
    }

    fun conversion(report: Report?): List<PlayIssue> =
        report?.warnings?.take(40)?.map { PlayIssue(IssueLevel.INFO, "Converter", it) } ?: emptyList()
}
