package com.brenninhotools.vconverter.chart.preview

import com.brenninhotools.vconverter.convert.PsychChartNames
import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.io.OutputFile
import org.json.JSONArray
import org.json.JSONObject

data class ChartTweaks(
    val songName: String = "",
    val artist: String = "",
    val charter: String = "",
    val player: String = "",
    val opponent: String = "",
    val girlfriend: String = "",
    val stage: String = "",
    val noteStyle: String = "",
    val speeds: Map<String, Double> = emptyMap(),
    val keepUnknownEvents: Boolean = true
) {
    val isEmpty: Boolean get() = this == ChartTweaks(keepUnknownEvents = keepUnknownEvents)

    fun summary(): List<String> = buildList {
        if (songName.isNotBlank()) add("Song name → $songName")
        if (artist.isNotBlank()) add("Artist → $artist")
        if (charter.isNotBlank()) add("Charter → $charter")
        if (player.isNotBlank()) add("Player → $player")
        if (opponent.isNotBlank()) add("Opponent → $opponent")
        if (girlfriend.isNotBlank()) add("Girlfriend → $girlfriend")
        if (stage.isNotBlank()) add("Stage → $stage")
        if (noteStyle.isNotBlank()) add("Note style → $noteStyle")
        speeds.forEach { (difficulty, speed) -> add("Scroll speed ($difficulty) → ${speed.toJsonNumber()}") }
    }
}

enum class ChartField { SONG_NAME, ARTIST, CHARTER, CHARACTERS, STAGE, NOTE_STYLE, SPEED, KEEP_EVENTS }

object ChartTweaker {
    fun fields(source: ChartEngine, target: ChartEngine): Set<ChartField> = buildSet {
        add(ChartField.CHARACTERS)
        add(ChartField.STAGE)
        add(ChartField.SPEED)
        if (target != ChartEngine.PSYCH) add(ChartField.SONG_NAME)
        if (target == ChartEngine.VSLICE) {
            add(ChartField.ARTIST)
            add(ChartField.CHARTER)
            add(ChartField.NOTE_STYLE)
        }
        if (source == ChartEngine.PSYCH && target == ChartEngine.VSLICE) add(ChartField.KEEP_EVENTS)
    }

    fun apply(target: ChartEngine, files: List<OutputFile>, tweaks: ChartTweaks): List<OutputFile> {
        if (tweaks.isEmpty) return files
        return files.map { file ->
            if (file.bytes != null || !file.name.endsWith(".json", ignoreCase = true)) return@map file
            val root = runCatching { JSONObject(file.content) }.getOrNull() ?: return@map file
            val changed = when (target) {
                ChartEngine.VSLICE -> vslice(file, root, tweaks)
                ChartEngine.PSYCH -> psych(file, root, tweaks)
                ChartEngine.CODENAME -> codename(file, root, tweaks)
            }
            if (changed) OutputFile(file.directories, file.name, if (file.content.contains("\n")) root.toString(2) else root.toString(), null) else file
        }
    }

    private fun speedFor(tweaks: ChartTweaks, difficulty: String): Double? =
        tweaks.speeds.entries.firstOrNull { it.key.equals(difficulty, ignoreCase = true) }?.value

    private fun vslice(file: OutputFile, root: JSONObject, tweaks: ChartTweaks): Boolean {
        val play = root.optJSONObject("playData")
        if (play != null) {
            if (tweaks.songName.isNotBlank()) root.put("songName", tweaks.songName)
            if (tweaks.artist.isNotBlank()) root.put("artist", tweaks.artist)
            if (tweaks.charter.isNotBlank()) root.put("charter", tweaks.charter)
            val characters = play.optJSONObject("characters") ?: JSONObject().also { play.put("characters", it) }
            if (tweaks.player.isNotBlank()) characters.put("player", tweaks.player)
            if (tweaks.opponent.isNotBlank()) characters.put("opponent", tweaks.opponent)
            if (tweaks.girlfriend.isNotBlank()) characters.put("girlfriend", tweaks.girlfriend)
            if (tweaks.stage.isNotBlank()) play.put("stage", tweaks.stage)
            if (tweaks.noteStyle.isNotBlank()) play.put("noteStyle", tweaks.noteStyle)
            return true
        }
        val speeds = root.optJSONObject("scrollSpeed") ?: return false
        if (!root.has("notes")) return false
        var changed = false
        speeds.keys().asSequence().toList().forEach { difficulty ->
            speedFor(tweaks, difficulty)?.let {
                speeds.put(difficulty, it.toJsonNumber())
                changed = true
            }
        }
        return changed
    }

    private fun psych(file: OutputFile, root: JSONObject, tweaks: ChartTweaks): Boolean {
        if (file.name.substringBeforeLast('.').equals("events", ignoreCase = true)) return false
        val song = root.optJSONObject("song")?.takeIf { it.has("notes") } ?: root.takeIf { it.has("notes") && it.has("bpm") } ?: return false
        if (tweaks.player.isNotBlank()) song.put("player1", tweaks.player)
        if (tweaks.opponent.isNotBlank()) song.put("player2", tweaks.opponent)
        if (tweaks.girlfriend.isNotBlank()) {
            song.put("gfVersion", tweaks.girlfriend)
            if (song.has("player3")) song.put("player3", tweaks.girlfriend)
        }
        if (tweaks.stage.isNotBlank()) song.put("stage", tweaks.stage)
        val stem = file.name.substringBeforeLast('.')
        val difficulty = PsychChartNames.split(stem, file.directories.lastOrNull()).second
        speedFor(tweaks, difficulty)?.let { song.put("speed", it.toJsonNumber()) }
        return true
    }

    private fun codename(file: OutputFile, root: JSONObject, tweaks: ChartTweaks): Boolean {
        val stem = file.name.substringBeforeLast('.')
        if (stem.equals("meta", ignoreCase = true) || stem.lowercase().startsWith("meta-")) {
            if (tweaks.songName.isBlank()) return false
            root.put("displayName", tweaks.songName)
            return true
        }
        val lines = root.optJSONArray("strumLines") ?: return false
        if (tweaks.stage.isNotBlank()) root.put("stage", tweaks.stage)
        speedFor(tweaks, stem)?.let { root.put("scrollSpeed", it.toJsonNumber()) }
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val position = line.optString("position").lowercase()
            val replacement = when {
                position == "girlfriend" || position == "gf" -> tweaks.girlfriend
                line.optInt("type") == 1 -> tweaks.player
                line.optInt("type") == 0 -> tweaks.opponent
                else -> ""
            }
            if (replacement.isNotBlank()) line.put("characters", JSONArray().put(replacement))
        }
        return true
    }
}
