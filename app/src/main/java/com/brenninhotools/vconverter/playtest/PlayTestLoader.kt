package com.brenninhotools.vconverter.playtest

import com.brenninhotools.vconverter.codename.CodenameModConverter
import com.brenninhotools.vconverter.codename.CodenameModScanner
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.editor.CharacterLoader
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.LoadedCharacter
import com.brenninhotools.vconverter.editor.LoadedStage
import com.brenninhotools.vconverter.editor.SongLoader
import com.brenninhotools.vconverter.editor.StageLoader
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.reverse.VSliceParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

class MemoryModFiles(private val files: Map<String, ByteArray>) : ModFiles {
    override val paths: List<String> = files.keys.sorted()
    override fun open(path: String): InputStream = ByteArrayInputStream(files[path] ?: throw IllegalArgumentException("Missing $path"))
    override fun size(path: String): Long = files[path]?.size?.toLong() ?: 0L
}

class MemoryModWriter : ModWriter {
    val files = LinkedHashMap<String, ByteArray>()
    override fun writeText(path: String, text: String) {
        files[path] = text.toByteArray(Charsets.UTF_8)
    }

    override fun copy(path: String, source: () -> InputStream) {
        files[path] = source().use { it.readBytes() }
    }
}

enum class CharacterRole(val label: String, val key: String) { PLAYER("Player", "player"), OPPONENT("Opponent", "opponent"), GIRLFRIEND("Girlfriend", "girlfriend") }

data class PlayTestSongRef(val id: String, val variation: String, val name: String, val difficulties: List<String>) {
    val label: String get() = name.ifBlank { id } + if (variation.isNotEmpty()) " ($variation)" else ""
}

class PlayTestMod(
    val engine: String,
    val files: Map<String, ByteArray>,
    val songs: List<PlayTestSongRef>,
    val conversion: Report? = null,
    val loose: PlayTestSong? = null
) {
    private val index: Map<String, String> = files.keys.associateBy { it.lowercase(Locale.ROOT) }

    fun find(path: String): Pair<String, ByteArray>? {
        val key = index[path.lowercase(Locale.ROOT)] ?: return null
        return key to files.getValue(key)
    }

    fun under(folder: String): List<Pair<String, ByteArray>> {
        val prefix = folder.lowercase(Locale.ROOT).trimEnd('/') + "/"
        return files.entries.filter { it.key.lowercase(Locale.ROOT).startsWith(prefix) }.map { it.key to it.value }
    }
}

class PlayTestSong(
    val document: ChartDocument,
    val audio: List<EditorAudio>,
    val characters: Map<CharacterRole, LoadedCharacter>,
    val characterIds: Map<CharacterRole, String>,
    val stage: LoadedStage?,
    val stageId: String,
    val issues: List<PlayIssue>
)

object PlayTestLoader {
    private val metadataName = Regex("(^|/)data/songs/([^/]+)/([^/]+)-metadata(?:-([^/]+))?\\.json$", RegexOption.IGNORE_CASE)

    private fun clean(files: List<Pair<String, ByteArray>>): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        files.forEach { (path, bytes) ->
            val p = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")
            if (p.isNotEmpty() && !p.startsWith("__MACOSX/")) out[p] = bytes
        }
        return out
    }

    private fun songsOf(files: Map<String, ByteArray>): List<PlayTestSongRef> = files.keys.mapNotNull { path ->
        val m = metadataName.find(path) ?: return@mapNotNull null
        if (!m.groupValues[2].equals(m.groupValues[3], ignoreCase = true)) return@mapNotNull null
        val meta = runCatching { VSliceParser.metadata(files.getValue(path).toString(Charsets.UTF_8)) }.getOrNull()
        PlayTestSongRef(m.groupValues[2], m.groupValues[4], meta?.songName ?: m.groupValues[2], meta?.difficulties ?: emptyList())
    }.distinctBy { it.id.lowercase(Locale.ROOT) to it.variation.lowercase(Locale.ROOT) }
        .sortedWith(compareBy({ it.label.lowercase(Locale.ROOT) }, { it.variation }))

    fun open(raw: List<Pair<String, ByteArray>>): PlayTestMod {
        val all = clean(raw)
        require(all.isNotEmpty()) { "Nothing to test: pick a mod folder, a .zip or chart files." }
        val vsliceRoot = all.keys.firstNotNullOfOrNull { p -> metadataName.find(p)?.let { p.substring(0, it.range.first + it.groupValues[1].length) } }
        if (vsliceRoot != null) {
            val files = all.filterKeys { it.startsWith(vsliceRoot) }.mapKeys { it.key.removePrefix(vsliceRoot) }
            return PlayTestMod("V-Slice", files, songsOf(files))
        }
        val paths = all.keys.toList()
        if (CodenameModScanner.looksLikeCodename(paths)) {
            val converted = runCatching {
                val source = MemoryModFiles(all)
                val writer = MemoryModWriter()
                val result = CodenameModConverter(source, CodenameModScanner.scan(source), writer).convert(ModInfo("PlayTest", "", ""))
                writer.files to result.report
            }.getOrNull()
            if (converted != null && songsOf(converted.first).isNotEmpty()) {
                return PlayTestMod("Codename Engine, converted to V-Slice", converted.first, songsOf(converted.first), converted.second)
            }
        }
        val psych = runCatching {
            val source = MemoryModFiles(all)
            val scan = ModScanner.scan(source)
            if (scan.songs.isEmpty()) null else {
                val writer = MemoryModWriter()
                val result = ModConverter(source, scan, writer).convert(ModInfo("PlayTest", "", ""))
                writer.files to result.report
            }
        }.getOrNull()
        if (psych != null && songsOf(psych.first).isNotEmpty()) {
            return PlayTestMod("Psych Engine, converted to V-Slice", psych.first, songsOf(psych.first), psych.second)
        }
        val loaded = SongLoader.load(all.entries.map { it.key to it.value })
        val song = PlayTestSong(loaded.document, loaded.audio, emptyMap(), emptyMap(), null, loaded.document.meta.stage, PlayTestChecks.chart(loaded.document, loaded.audio))
        val ref = PlayTestSongRef(loaded.document.id, loaded.document.variation, loaded.document.meta.songName, loaded.document.difficultyNames)
        return PlayTestMod("${loaded.source} chart", all, listOf(ref), loose = song)
    }

    private fun imageFiles(mod: PlayTestMod, assetPath: String): List<Pair<String, ByteArray>> {
        val path = assetPath.trim().replace('\\', '/').substringAfter(':').removePrefix("images/").removeSuffix(".png")
        if (path.isEmpty() || path.startsWith("#")) return emptyList()
        return listOfNotNull(mod.find("images/$path.png"), mod.find("images/$path.xml"))
    }

    fun character(mod: PlayTestMod, id: String): LoadedCharacter? {
        val json = mod.find("data/characters/$id.json") ?: return null
        val parsed = runCatching { VSliceParser.character(json.second.toString(Charsets.UTF_8)) }.getOrNull() ?: return null
        val images = imageFiles(mod, parsed.assetPath)
        return runCatching { CharacterLoader.load(listOf(json) + images) }.getOrNull()
    }

    fun stage(mod: PlayTestMod, id: String): LoadedStage? {
        val json = mod.find("data/stages/$id.json") ?: return null
        val parsed = runCatching { VSliceParser.stage(json.second.toString(Charsets.UTF_8)) }.getOrNull() ?: return null
        val images = parsed.props.flatMap { imageFiles(mod, it.assetPath) }.distinctBy { it.first }
        return runCatching { StageLoader.load(listOf(json) + images) }.getOrNull()
    }

    fun song(mod: PlayTestMod, ref: PlayTestSongRef): PlayTestSong {
        mod.loose?.let { return it }
        val suffix = if (ref.variation.isEmpty()) "" else "-${ref.variation}"
        val meta = mod.find("data/songs/${ref.id}/${ref.id}-metadata$suffix.json") ?: throw IllegalArgumentException("No metadata for ${ref.label}.")
        val chart = mod.find("data/songs/${ref.id}/${ref.id}-chart$suffix.json") ?: throw IllegalArgumentException("No chart file for ${ref.label} (data/songs/${ref.id}/${ref.id}-chart$suffix.json).")
        val audio = mod.under("songs/${ref.id}").filter { it.first.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("ogg", "mp3", "wav") }
        val loaded = SongLoader.load(listOf(meta, chart) + audio)
        val document = loaded.document
        val ids = mapOf(
            CharacterRole.PLAYER to document.meta.player,
            CharacterRole.OPPONENT to document.meta.opponent,
            CharacterRole.GIRLFRIEND to document.meta.girlfriend
        ).filterValues { it.isNotBlank() && it != "none" }
        val characters = ids.mapNotNull { (role, id) -> character(mod, id)?.let { role to it } }.toMap()
        val stageId = document.meta.stage
        val stage = stage(mod, stageId)
        val issues = PlayTestChecks.chart(document, loaded.audio) +
            ids.flatMap { (role, id) -> PlayTestChecks.character(role, id, characters[role]) } +
            PlayTestChecks.stage(stageId, stage) +
            PlayTestChecks.conversion(mod.conversion)
        return PlayTestSong(document, loaded.audio, characters, ids, stage, stageId, issues)
    }

    fun fromEditor(document: ChartDocument, audio: List<EditorAudio>): PlayTestSong =
        PlayTestSong(document, audio, emptyMap(), emptyMap(), null, document.meta.stage, PlayTestChecks.chart(document, audio))
}
