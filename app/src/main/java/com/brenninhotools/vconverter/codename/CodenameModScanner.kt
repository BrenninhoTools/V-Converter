package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.SongPath
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.Placeholders

data class CodenameScannedSong(
    val folder: String,
    val id: String,
    val charts: Map<String, Map<String, String>>,
    val metas: Map<String, String>,
    val events: Map<String, String>,
    val audio: List<String>,
    val scripts: List<String>,
    val cutscenes: List<String>
) {
    val variants: List<String> get() = charts.keys.filter { it.isNotEmpty() }.sorted()
}

data class CodenameModScan(
    val root: String,
    val modpack: String?,
    val icon: String?,
    val songs: List<CodenameScannedSong>,
    val characters: Map<String, String>,
    val characterScripts: Map<String, String>,
    val stages: Map<String, String>,
    val stageScripts: Map<String, String>,
    val weeks: Map<String, String>,
    val weekList: String?,
    val weekCharacters: Map<String, String>,
    val events: Map<String, String>,
    val eventScripts: Map<String, String>,
    val noteScripts: Map<String, String>,
    val libraryScripts: Map<String, String>,
    val globalScripts: List<String>,
    val stateScripts: List<String>,
    val assets: List<String>,
    val ignored: List<String>,
    val placeholders: Map<String, String>
) {
    val isEmpty: Boolean get() = songs.isEmpty() && characters.isEmpty() && stages.isEmpty() && weeks.isEmpty() && assets.isEmpty()
    val scriptCount: Int get() = songs.sumOf { it.scripts.size } + characterScripts.size + stageScripts.size + eventScripts.size + noteScripts.size + globalScripts.size

    fun relative(path: String): String = path.removePrefix(root)
}

object CodenameModScanner {
    val assetFolders = setOf("images", "music", "sounds", "fonts", "videos", "shaders")
    private val markers = listOf("data/config/modpack.ini", "songs/", "data/characters/", "data/stages/", "data/weeks/", "images/", "data/events/", "data/notes/")
    private val scriptExtensions = setOf("hx", "hxs", "hsc", "hscript")
    private val audioExtensions = setOf("ogg", "mp3", "wav")

    fun looksLikeCodename(paths: List<String>): Boolean = paths.any { p ->
        val lower = p.lowercase()
        lower.endsWith("data/config/modpack.ini") ||
            (lower.contains("data/characters/") && lower.endsWith(".xml")) ||
            (lower.contains("data/stages/") && lower.endsWith(".xml")) ||
            Regex("(^|/)songs/[^/]+/charts/").containsMatchIn(lower)
    }

    fun scan(files: ModFiles): CodenameModScan {
        val root = findRoot(files.paths)
        val placeholders = Placeholders.find(files.paths, root)
        val placeholderFiles = placeholders.values.toSet()
        val inside = files.paths.filter { it.startsWith(root) && it !in placeholderFiles }

        val songCharts = LinkedHashMap<String, MutableMap<String, MutableMap<String, String>>>()
        val songMetas = HashMap<String, MutableMap<String, String>>()
        val songEvents = HashMap<String, MutableMap<String, String>>()
        val songAudio = HashMap<String, MutableList<String>>()
        val songScripts = HashMap<String, MutableList<String>>()
        val songCutscenes = HashMap<String, MutableList<String>>()
        val characters = sortedMapOf<String, String>()
        val characterScripts = sortedMapOf<String, String>()
        val stages = sortedMapOf<String, String>()
        val stageScripts = sortedMapOf<String, String>()
        val weeks = sortedMapOf<String, String>()
        val weekCharacters = sortedMapOf<String, String>()
        val events = sortedMapOf<String, String>()
        val eventScripts = sortedMapOf<String, String>()
        val noteScripts = sortedMapOf<String, String>()
        val libraryScripts = sortedMapOf<String, String>()
        val globalScripts = mutableListOf<String>()
        val stateScripts = mutableListOf<String>()
        val assets = mutableListOf<String>()
        val ignored = mutableListOf<String>()
        var modpack: String? = null
        var icon: String? = null
        var weekList: String? = null

        inside.forEach { full ->
            val rel = full.removePrefix(root)
            val parts = rel.split('/')
            val lower = parts.map { it.lowercase() }
            val fileName = parts.last()
            val stem = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val top = lower.first()
            when {
                rel.equals("data/config/modpack.ini", ignoreCase = true) -> modpack = full
                parts.size == 1 && ext == "png" && stem.lowercase().startsWith("icon") -> {
                    if (icon == null || stem.equals("icon", ignoreCase = true)) icon = full
                }
                top == "songs" && parts.size == 2 && ext in scriptExtensions -> globalScripts += full
                top == "songs" && parts.size >= 3 -> {
                    val folder = parts[1]
                    val sub = lower.getOrNull(2)
                    when {
                        sub == "charts" && parts.size == 4 && ext == "json" ->
                            songCharts.getOrPut(folder) { linkedMapOf() }.getOrPut("") { linkedMapOf() }[stem] = full
                        sub == "charts" && parts.size == 5 && ext == "json" ->
                            songCharts.getOrPut(folder) { linkedMapOf() }.getOrPut(parts[3]) { linkedMapOf() }[stem] = full
                        parts.size == 3 && ext == "json" && stem.lowercase() == "meta" -> songMetas.getOrPut(folder) { linkedMapOf() }[""] = full
                        parts.size == 3 && ext == "json" && stem.lowercase().startsWith("meta-") -> songMetas.getOrPut(folder) { linkedMapOf() }[stem.substring(5)] = full
                        parts.size == 3 && ext == "json" && stem.lowercase() == "events" -> songEvents.getOrPut(folder) { linkedMapOf() }[""] = full
                        parts.size == 3 && ext == "json" && stem.lowercase().startsWith("events-") -> songEvents.getOrPut(folder) { linkedMapOf() }[stem.substring(7)] = full
                        sub == "song" && ext in audioExtensions -> songAudio.getOrPut(folder) { mutableListOf() } += full
                        sub == "scripts" && ext in scriptExtensions -> songScripts.getOrPut(folder) { mutableListOf() } += full
                        parts.size == 3 && ext in scriptExtensions -> songCutscenes.getOrPut(folder) { mutableListOf() } += full
                        else -> ignored += full
                    }
                }
                top == "data" && parts.size == 3 && lower[1] == "characters" && ext == "xml" -> characters[stem] = full
                top == "data" && parts.size == 3 && lower[1] == "characters" && ext in scriptExtensions -> characterScripts[stem] = full
                top == "data" && parts.size == 3 && lower[1] == "stages" && ext == "xml" -> stages[stem] = full
                top == "data" && parts.size == 3 && lower[1] == "stages" && ext in scriptExtensions -> stageScripts[stem] = full
                top == "data" && parts.size == 4 && lower[1] == "weeks" && lower[2] == "weeks" && ext == "xml" -> weeks[stem] = full
                top == "data" && parts.size == 4 && lower[1] == "weeks" && lower[2] == "characters" && ext == "xml" -> weekCharacters[stem] = full
                top == "data" && parts.size == 3 && lower[1] == "weeks" && fileName.equals("weeks.txt", ignoreCase = true) -> weekList = full
                top == "data" && parts.size == 3 && lower[1] == "events" && ext == "json" -> events[stem] = full
                top == "data" && parts.size == 3 && lower[1] == "events" && ext in scriptExtensions -> eventScripts[stem] = full
                top == "data" && parts.size == 3 && lower[1] == "notes" && ext in scriptExtensions -> noteScripts[stem] = full
                top == "data" && lower.getOrNull(1) == "scripts" && ext in scriptExtensions -> libraryScripts["data/" + parts.drop(1).joinToString("/").substringBeforeLast('.')] = full
                top == "data" && (lower.getOrNull(1) == "globals" || lower.getOrNull(1) == "global") && ext in scriptExtensions -> globalScripts += full
                top == "data" && lower.getOrNull(1) == "states" && ext in scriptExtensions -> stateScripts += full
                top in assetFolders -> assets += full
                else -> ignored += full
            }
        }

        val songs = songCharts.map { (folder, variants) ->
            CodenameScannedSong(
                folder = folder,
                id = SongPath.format(folder),
                charts = variants,
                metas = songMetas[folder] ?: emptyMap(),
                events = songEvents[folder] ?: emptyMap(),
                audio = songAudio[folder] ?: emptyList(),
                scripts = songScripts[folder] ?: emptyList(),
                cutscenes = songCutscenes[folder] ?: emptyList()
            )
        }.sortedBy { it.id }

        return CodenameModScan(
            root = root,
            modpack = modpack,
            icon = icon,
            songs = songs,
            characters = characters,
            characterScripts = characterScripts,
            stages = stages,
            stageScripts = stageScripts,
            weeks = weeks,
            weekList = weekList,
            weekCharacters = weekCharacters,
            events = events,
            eventScripts = eventScripts,
            noteScripts = noteScripts,
            libraryScripts = libraryScripts,
            globalScripts = globalScripts,
            stateScripts = stateScripts,
            assets = assets,
            ignored = ignored,
            placeholders = placeholders
        )
    }

    private fun findRoot(paths: List<String>): String {
        val scores = HashMap<String, Int>()
        paths.forEach { path ->
            val parts = path.split('/')
            for (depth in 0 until parts.size) {
                val prefix = if (depth == 0) "" else parts.take(depth).joinToString("/", postfix = "/")
                val rest = path.removePrefix(prefix).lowercase()
                markers.forEachIndexed { index, marker ->
                    val hit = if (marker.endsWith("/")) rest.startsWith(marker) else rest == marker
                    if (hit) scores[prefix] = (scores[prefix] ?: 0) or (1 shl index)
                }
            }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { Integer.bitCount(it.value) }.thenBy { it.key.length })
            .firstOrNull()?.key ?: ""
    }
}
