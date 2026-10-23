package com.brenninhotools.vconverter.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.brenninhotools.vconverter.codename.CodenameModConverter
import com.brenninhotools.vconverter.codename.CodenameModScanner
import com.brenninhotools.vconverter.codename.CodenameToPsychMod
import com.brenninhotools.vconverter.codename.PsychToCodenameMod
import com.brenninhotools.vconverter.convert.CodenameCharactersConverter
import com.brenninhotools.vconverter.convert.CodenameChartsConverter
import com.brenninhotools.vconverter.convert.CodenameScriptsConverter
import com.brenninhotools.vconverter.convert.CodenameStagesConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychCharactersConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychChartsConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychScriptsConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychStagesConverter
import com.brenninhotools.vconverter.convert.CodenameToPsychWeeksConverter
import com.brenninhotools.vconverter.convert.CodenameWeeksConverter
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.convert.PsychCharactersConverter
import com.brenninhotools.vconverter.convert.PsychChartsConverter
import com.brenninhotools.vconverter.convert.PsychScriptsConverter
import com.brenninhotools.vconverter.convert.PsychStagesConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameCharactersConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameScriptsConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameStagesConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameWeeksConverter
import com.brenninhotools.vconverter.convert.PsychWeeksConverter
import com.brenninhotools.vconverter.convert.ReverseCharactersConverter
import com.brenninhotools.vconverter.convert.ReverseChartsConverter
import com.brenninhotools.vconverter.convert.ReverseScriptsConverter
import com.brenninhotools.vconverter.convert.ReverseStagesConverter
import com.brenninhotools.vconverter.convert.ReverseWeeksConverter
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.io.Archives
import com.brenninhotools.vconverter.io.ExtractedArchive
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.mod.DirectoryModFiles
import com.brenninhotools.vconverter.mod.DirectoryModWriter
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.ZipModWriter
import com.brenninhotools.vconverter.reverse.VSliceModConverter
import com.brenninhotools.vconverter.reverse.VSliceModScanner
import java.io.File
import java.io.FileOutputStream

enum class Engine(val label: String, val title: String, val detail: String, val color: Color) {
    PSYCH("PSYCH", "Psych Engine", "Lua · HScript · JSON", Palette.Left),
    CODENAME("CODENAME", "Codename Engine", "XML · HScript", Palette.Orange),
    VSLICE("V-SLICE", "V-Slice", "Polymod · .hxc", Palette.Down)
}

enum class Direction(val from: Engine, val to: Engine) {
    PSYCH_TO_VSLICE(Engine.PSYCH, Engine.VSLICE),
    VSLICE_TO_PSYCH(Engine.VSLICE, Engine.PSYCH),
    CODENAME_TO_VSLICE(Engine.CODENAME, Engine.VSLICE),
    CODENAME_TO_PSYCH(Engine.CODENAME, Engine.PSYCH),
    PSYCH_TO_CODENAME(Engine.PSYCH, Engine.CODENAME);

    val label: String get() = "${from.title} to ${to.title}"

    fun swapped(): Direction? = entries.firstOrNull { it.from == to && it.to == from }

    fun retarget(engine: Engine): Direction = entries.firstOrNull { it.from == from && it.to == engine } ?: this

    companion object {
        fun from(engine: Engine): Direction = entries.first { it.from == engine }

        fun targets(engine: Engine): List<Engine> = entries.filter { it.from == engine }.map { it.to }
    }
}

enum class Tab(val label: String, val title: String, val accent: Color) {
    MOD("Mod", "Whole mod", Palette.Pink),
    CHARTS("Charts", "Charts", Palette.Down),
    CHARACTERS("Characters", "Characters", Palette.Left),
    STAGES("Stages", "Stages", Palette.Up),
    WEEKS("Weeks", "Weeks", Palette.Right),
    SCRIPTS("Scripts", "Scripts", Palette.Lavender)
}

sealed interface Status {
    val text: String

    data class Info(override val text: String) : Status
    data class Success(override val text: String) : Status
    data class Error(override val text: String) : Status
}

object Workspace {
    val cache: File by lazy { File(System.getProperty("java.io.tmpdir"), "v-converter").apply { mkdirs() } }

    fun converter(direction: Direction, tab: Tab): FileConverter = when (direction) {
        Direction.PSYCH_TO_VSLICE -> when (tab) {
            Tab.CHARTS -> PsychChartsConverter()
            Tab.CHARACTERS -> PsychCharactersConverter()
            Tab.STAGES -> PsychStagesConverter()
            Tab.WEEKS -> PsychWeeksConverter()
            else -> PsychScriptsConverter()
        }
        Direction.VSLICE_TO_PSYCH -> when (tab) {
            Tab.CHARTS -> ReverseChartsConverter()
            Tab.CHARACTERS -> ReverseCharactersConverter()
            Tab.STAGES -> ReverseStagesConverter()
            Tab.WEEKS -> ReverseWeeksConverter()
            else -> ReverseScriptsConverter()
        }
        Direction.CODENAME_TO_VSLICE -> when (tab) {
            Tab.CHARTS -> CodenameChartsConverter()
            Tab.CHARACTERS -> CodenameCharactersConverter()
            Tab.STAGES -> CodenameStagesConverter()
            Tab.WEEKS -> CodenameWeeksConverter()
            else -> CodenameScriptsConverter()
        }
        Direction.CODENAME_TO_PSYCH -> when (tab) {
            Tab.CHARTS -> CodenameToPsychChartsConverter()
            Tab.CHARACTERS -> CodenameToPsychCharactersConverter()
            Tab.STAGES -> CodenameToPsychStagesConverter()
            Tab.WEEKS -> CodenameToPsychWeeksConverter()
            else -> CodenameToPsychScriptsConverter()
        }
        Direction.PSYCH_TO_CODENAME -> when (tab) {
            Tab.CHARTS -> PsychToCodenameChartsConverter()
            Tab.CHARACTERS -> PsychToCodenameCharactersConverter()
            Tab.STAGES -> PsychToCodenameStagesConverter()
            Tab.WEEKS -> PsychToCodenameWeeksConverter()
            else -> PsychToCodenameScriptsConverter()
        }
    }

    fun hint(direction: Direction, tab: Tab): String = when (direction.from) {
        Engine.PSYCH -> when (tab) {
            Tab.MOD -> "Pick the mod folder inside Psych's mods directory, or a .zip / .rar / .7z with the mod."
            Tab.CHARTS -> "data/<song>/<song>-<difficulty>.json and events.json."
            Tab.CHARACTERS -> "characters/*.json" + if (direction.to == Engine.VSLICE) " with their spritesheet .xml for exact offsets." else "."
            Tab.STAGES -> "stages/*.json and stages/<stage>.lua when the stage builds its sprites in Lua."
            Tab.WEEKS -> "weeks/*.json and images/menucharacters/*.json."
            Tab.SCRIPTS -> "Lua and HScript files. Archives keep the folders, so scripts/, data/<song>/, stages/, custom_events/ and custom_notetypes/ keep their scope."
        }
        Engine.CODENAME -> when (tab) {
            Tab.MOD -> "Pick the mod folder inside Codename's mods directory, or a .zip / .rar / .7z with the mod."
            Tab.CHARTS -> "songs/<song>/charts/*.json with meta.json and events.json."
            Tab.CHARACTERS -> "data/characters/*.xml with their spritesheet .xml."
            Tab.STAGES -> "data/stages/*.xml (and the stage images for exact positions)."
            Tab.WEEKS -> "data/weeks/weeks/*.xml and data/weeks/characters/*.xml."
            Tab.SCRIPTS -> ".hx scripts, plus stage XMLs and data/events/*.json for context."
        }
        Engine.VSLICE -> when (tab) {
            Tab.MOD -> "Pick the mod folder inside V-Slice's mods directory, or a .zip / .rar / .7z with the mod."
            Tab.CHARTS -> "<song>-metadata.json and <song>-chart.json (and variations)."
            Tab.CHARACTERS -> "data/characters/*.json with their spritesheet .xml."
            Tab.STAGES -> "data/stages/*.json."
            Tab.WEEKS -> "data/levels/*.json plus the songs' -metadata.json files."
            Tab.SCRIPTS -> ".hxc scripts."
        }
    }

    fun writeZip(target: File, prefix: String, body: (ModWriter) -> Unit) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { stream ->
            ZipModWriter(stream, prefix).use { zip -> body(zip) }
        }
    }

    fun writeFolder(target: File, body: (ModWriter) -> Unit) {
        target.mkdirs()
        body(DirectoryModWriter(target))
    }
}

class LoadedMod(
    val name: String,
    val root: String,
    val stats: List<Pair<String, Int>>,
    val songs: List<String>,
    val info: ModInfo?,
    val files: ModFiles,
    private val archive: ExtractedArchive?,
    private val runner: (ModWriter, ModInfo, (ModProgress) -> Unit) -> ModConversionResult
) {
    fun run(writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit) = runner(writer, info, progress)

    fun release() {
        archive?.delete()
    }

    companion object {
        fun open(source: File, direction: Direction, progress: (ModProgress) -> Unit): LoadedMod {
            val archive: ExtractedArchive?
            val files: ModFiles
            if (source.isDirectory) {
                archive = null
                files = DirectoryModFiles(source)
            } else {
                val kind = Archives.kind(source) ?: throw IllegalArgumentException("${source.name} is not a ZIP, RAR or 7z archive.")
                archive = Archives.extract(source, source.name, kind, Workspace.cache) { done, total -> progress(ModProgress(done, total, "Extracting ${source.name}")) }
                files = archive.files
            }
            val name = source.name
            try {
                return build(name, files, archive, direction)
            } catch (e: Exception) {
                archive?.delete()
                throw e
            }
        }

        private fun build(name: String, files: ModFiles, archive: ExtractedArchive?, direction: Direction): LoadedMod = when (direction.from) {
            Engine.PSYCH -> {
                val scan = ModScanner.scan(files)
                require(!scan.isEmpty) { "No Psych Engine mod content was found in $name. Expected folders like data, songs, characters, stages, weeks or images." }
                LoadedMod(
                    name, scan.root,
                    listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size, "Scripts" to scan.scripts.size, "Assets" to scan.assets.size),
                    scan.songs.map { it.id },
                    ModConverter.readPack(files, scan),
                    files, archive
                ) { writer, info, progress ->
                    if (direction.to == Engine.CODENAME) PsychToCodenameMod(files, scan, writer, progress).convert(info)
                    else ModConverter(files, scan, writer, progress).convert(info)
                }
            }
            Engine.CODENAME -> {
                val scan = CodenameModScanner.scan(files)
                require(!scan.isEmpty) { "No Codename Engine mod content was found in $name. Expected data/config/modpack.ini or folders like songs/<song>/charts, data/characters, data/stages or data/weeks." }
                LoadedMod(
                    name, scan.root,
                    listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size, "Scripts" to scan.scriptCount, "Assets" to scan.assets.size),
                    scan.songs.map { it.id },
                    CodenameModConverter.readModpack(files, scan),
                    files, archive
                ) { writer, info, progress ->
                    if (direction.to == Engine.PSYCH) CodenameToPsychMod(files, scan, writer, progress).convert(info)
                    else CodenameModConverter(files, scan, writer, progress).convert(info)
                }
            }
            Engine.VSLICE -> {
                val scan = VSliceModScanner.scan(files)
                require(!scan.isEmpty) { "No V-Slice mod content was found in $name. Expected _polymod_meta.json or folders like data/songs, data/characters, data/stages or data/levels." }
                LoadedMod(
                    name, scan.root,
                    listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Levels" to scan.levels.size, "Scripts" to scan.scripts.size, "Assets" to scan.assets.size),
                    scan.songs.map { it.id },
                    VSliceModScanner.readInfo(files, scan),
                    files, archive
                ) { writer, info, progress -> VSliceModConverter(files, scan, writer, progress).convert(info) }
            }
        }
    }
}

class ModTabState(val direction: Direction) {
    var mod by mutableStateOf<LoadedMod?>(null)
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var author by mutableStateOf("")
    var progress by mutableStateOf<ModProgress?>(null)
    var status by mutableStateOf<Status?>(null)
    var report by mutableStateOf<Report?>(null)
    var busy by mutableStateOf(false)

    val folderName: String get() = ZipModWriter.folderName(title.ifBlank { "Converted Mod" })

    val suffix: String get() = when (direction.to) {
        Engine.PSYCH -> "psych"
        Engine.CODENAME -> "codename"
        Engine.VSLICE -> "vslice"
    }

    fun accept(loaded: LoadedMod, source: File) {
        mod?.release()
        mod = loaded
        title = loaded.info?.title?.takeIf { it.isNotBlank() } ?: loaded.root.trimEnd('/').substringAfterLast('/').ifBlank { source.nameWithoutExtension }
        description = loaded.info?.description ?: ""
        author = loaded.info?.author ?: ""
        report = null
        status = Status.Info("Found a mod in ${loaded.name}.")
    }

    fun info() = ModInfo(title.trim(), description.trim(), author.trim())

    fun clear() {
        mod?.release()
        mod = null
        report = null
        status = null
        title = ""
        description = ""
        author = ""
    }
}

class FileTabState(val converter: FileConverter) {
    var items by mutableStateOf(listOf<PickedText>())
    var output by mutableStateOf<ConvertedOutput?>(null)
    var status by mutableStateOf<Status?>(null)
    var busy by mutableStateOf(false)
    var preview by mutableStateOf<OutputFile?>(null)

    private fun tail(file: File, count: Int) = file.absoluteFile.invariantSeparatorsPath.split('/').filter { it.isNotEmpty() }.takeLast(count).joinToString("/")

    fun add(sources: List<File>) {
        val failures = mutableListOf<String>()
        var skipped = 0
        val picked = mutableListOf<PickedText>()
        val candidates = mutableListOf<Pair<ImportedFile, Boolean>>()
        val archives = mutableListOf<ExtractedArchive>()
        try {
            sources.forEach { source ->
                when {
                    source.isDirectory -> source.walkTopDown().filter { it.isFile }.forEach { f ->
                        val kind = runCatching { Archives.kind(f) }.getOrNull()
                        if (kind == null) {
                            val path = source.name + "/" + f.relativeTo(source).invariantSeparatorsPath
                            candidates += ImportedFile(f.name, path, true) { f.inputStream() } to true
                        }
                    }
                    else -> {
                        val kind = Archives.kind(source)
                        if (kind == null) {
                            candidates += ImportedFile(source.name, tail(source, 4), false) { source.inputStream() } to false
                        } else {
                            val archive = Archives.extract(source, source.name, kind, Workspace.cache)
                            archives += archive
                            archive.files.paths.forEach { path ->
                                candidates += ImportedFile(path.substringAfterLast('/'), path, true) { File(archive.directory, path).inputStream() } to true
                            }
                        }
                    }
                }
            }
            candidates.forEach { (file, filtered) ->
                if (filtered && !converter.accept(file)) {
                    skipped++
                    return@forEach
                }
                try {
                    picked += converter.classify(file, converter.read(file))
                } catch (e: Exception) {
                    if (filtered) skipped++ else failures += "${file.name}: ${e.message}"
                }
            }
        } finally {
            archives.forEach { it.delete() }
        }
        items = items.filterNot { old -> picked.any { it.key == old.key } } + picked
        output = null
        preview = null
        val text = buildString {
            append("Loaded ${picked.size} file(s).")
            if (skipped > 0) append(" $skipped other file(s) were ignored.")
        }
        status = when {
            failures.isNotEmpty() -> Status.Error(text + "\nSome files could not be read:\n" + failures.joinToString("\n"))
            picked.isEmpty() -> Status.Error(text)
            else -> Status.Info(text)
        }
    }

    fun remove(index: Int) {
        items = items.filterIndexed { i, _ -> i != index }
        output = null
        preview = null
    }

    fun setSlot(index: Int, slot: CharacterSlot) {
        items = items.mapIndexed { i, item -> if (i == index) item.copy(slot = slot) else item }
        output = null
        preview = null
    }

    fun clear() {
        items = emptyList()
        output = null
        preview = null
        status = null
    }

    fun convert() {
        require(items.isNotEmpty()) { "Add at least one ${converter.engine} ${converter.noun}." }
        val result = converter.build(items)
        output = result
        preview = result.files.firstOrNull()
        status = null
    }
}
