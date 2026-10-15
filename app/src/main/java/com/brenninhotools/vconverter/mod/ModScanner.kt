package com.brenninhotools.vconverter.mod

data class ScannedSong(
    val id: String,
    val charts: Map<String, String>,
    val events: String?,
    val audio: List<String>
)

data class ModScan(
    val root: String,
    val otherRoots: List<String>,
    val pack: String?,
    val packIcon: String?,
    val songs: List<ScannedSong>,
    val characters: List<String>,
    val stages: List<String>,
    val weeks: List<String>,
    val weekList: String?,
    val menuCharacters: List<String>,
    val assets: List<String>,
    val scripts: List<String>,
    val ignored: List<String>
) {
    val isEmpty: Boolean get() = songs.isEmpty() && characters.isEmpty() && stages.isEmpty() && weeks.isEmpty() && assets.isEmpty() && scripts.isEmpty()

    fun relative(path: String): String = path.removePrefix(root)
}

object ModScanner {
    val assetFolders = setOf("images", "songs", "music", "sounds", "fonts", "videos", "shaders")
    private val markers = listOf("pack.json", "data/", "weeks/", "songs/", "characters/", "stages/", "images/")
    private val scriptFolders = setOf("scripts", "custom_events", "custom_notetypes")
    private val audioExtensions = setOf("ogg", "mp3", "wav")

    fun scan(files: ModFiles): ModScan {
        val roots = findRoots(files.paths)
        val root = roots.firstOrNull() ?: ""
        val inside = files.paths.filter { it.startsWith(root) }

        val charts = LinkedHashMap<String, MutableMap<String, String>>()
        val events = HashMap<String, String>()
        val audio = HashMap<String, MutableList<String>>()
        val characters = mutableListOf<String>()
        val stages = mutableListOf<String>()
        val weeks = mutableListOf<String>()
        val menuCharacters = mutableListOf<String>()
        val assets = mutableListOf<String>()
        val scripts = mutableListOf<String>()
        val ignored = mutableListOf<String>()
        var pack: String? = null
        var packIcon: String? = null
        var weekList: String? = null

        inside.forEach { full ->
            val rel = full.removePrefix(root)
            val parts = rel.split('/')
            val top = parts.first().lowercase()
            val fileName = parts.last()
            val ext = fileName.substringAfterLast('.', "").lowercase()

            when {
                rel.equals("pack.json", ignoreCase = true) -> pack = full
                rel.equals("pack.png", ignoreCase = true) -> packIcon = full
                ext == "lua" || ext == "hx" || ext == "hxs" || ext == "hscript" || top in scriptFolders -> scripts += full
                top == "data" && parts.size == 3 && ext == "json" -> {
                    val folder = parts[1]
                    val stem = fileName.substringBeforeLast('.')
                    when {
                        stem.equals("events", ignoreCase = true) -> events[folder] = full
                        stem.equals(folder, ignoreCase = true) -> charts.getOrPut(folder) { linkedMapOf() }["normal"] = full
                        stem.lowercase().startsWith(folder.lowercase() + "-") ->
                            charts.getOrPut(folder) { linkedMapOf() }[stem.substring(folder.length + 1).lowercase()] = full
                        else -> ignored += full
                    }
                }
                top == "characters" && parts.size == 2 && ext == "json" -> characters += full
                top == "stages" && parts.size == 2 && ext == "json" -> stages += full
                top == "weeks" && parts.size == 2 && ext == "json" -> weeks += full
                top == "weeks" && fileName.equals("weekList.txt", ignoreCase = true) -> weekList = full
                top in assetFolders -> {
                    assets += full
                    if (top == "images" && parts.size == 3 && parts[1].equals("menucharacters", ignoreCase = true) && ext == "json") {
                        menuCharacters += full
                    }
                    if (top == "songs" && parts.size == 3 && ext in audioExtensions) {
                        audio.getOrPut(parts[1].lowercase()) { mutableListOf() } += full
                    }
                }
                else -> ignored += full
            }
        }

        val songs = charts.map { (folder, diffs) ->
            ScannedSong(
                id = folder.lowercase(),
                charts = diffs,
                events = events[folder],
                audio = audio[folder.lowercase()] ?: emptyList()
            )
        }.sortedBy { it.id }

        return ModScan(
            root = root,
            otherRoots = roots.drop(1),
            pack = pack,
            packIcon = packIcon,
            songs = songs,
            characters = characters.sorted(),
            stages = stages.sorted(),
            weeks = weeks.sorted(),
            weekList = weekList,
            menuCharacters = menuCharacters.sorted(),
            assets = assets,
            scripts = scripts,
            ignored = ignored
        )
    }

    private fun findRoots(paths: List<String>): List<String> {
        val scores = HashMap<String, Int>()
        paths.forEach { path ->
            val parts = path.split('/')
            for (depth in 0 until parts.size) {
                val prefix = if (depth == 0) "" else parts.take(depth).joinToString("/", postfix = "/")
                val rest = path.removePrefix(prefix)
                markers.forEachIndexed { index, marker ->
                    val hit = if (marker.endsWith("/")) rest.lowercase().startsWith(marker) else rest.equals(marker, ignoreCase = true)
                    if (hit) scores[prefix] = (scores[prefix] ?: 0) or (1 shl index)
                }
            }
        }
        return scores.entries
            .filter { Integer.bitCount(it.value) >= 1 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { Integer.bitCount(it.value) }.thenBy { it.key.length })
            .map { it.key }
            .let { ranked ->
                val best = ranked.firstOrNull() ?: return emptyList()
                listOf(best) + ranked.drop(1).filter { other ->
                    !other.startsWith(best) && !best.startsWith(other) && Integer.bitCount(scores.getValue(other)) >= 2
                }
            }
    }
}
