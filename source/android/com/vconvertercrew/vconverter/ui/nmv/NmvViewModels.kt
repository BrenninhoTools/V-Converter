package com.vconvertercrew.vconverter.ui.nmv

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vconvertercrew.vconverter.core.Report
import com.vconvertercrew.vconverter.io.DocumentIO
import com.vconvertercrew.vconverter.io.ExtractedArchive
import com.vconvertercrew.vconverter.io.Importer
import com.vconvertercrew.vconverter.io.TreeModFiles
import com.vconvertercrew.vconverter.io.TreeModWriter
import com.vconvertercrew.vconverter.mod.ModFiles
import com.vconvertercrew.vconverter.mod.ModInfo
import com.vconvertercrew.vconverter.mod.ModConversionResult
import com.vconvertercrew.vconverter.mod.ModProgress
import com.vconvertercrew.vconverter.nmv.NmvModScanner
import com.vconvertercrew.vconverter.nmv.NmvScan
import com.vconvertercrew.vconverter.nmv.NmvTarget
import com.vconvertercrew.vconverter.nmv.NmvToMod
import com.vconvertercrew.vconverter.mod.ModWriter
import com.vconvertercrew.vconverter.mod.ZipModWriter
import com.vconvertercrew.vconverter.ui.BaseConverterViewModel
import com.vconvertercrew.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LoadedNmvMod(val sourceName: String, val files: ModFiles, val scan: NmvScan)

abstract class NmvModViewModel(application: Application) : BaseConverterViewModel(application) {
    override val wholeMod: Boolean get() = true

    abstract val target: NmvTarget

    private val targetSuffix: String get() = target.name.lowercase()

    private fun runConversion(files: ModFiles, scan: NmvScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        NmvToMod(files, scan, writer, progress).convert(info, target)

    var mod by mutableStateOf<LoadedNmvMod?>(null)
        private set
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var author by mutableStateOf("")
    var result by mutableStateOf<Report?>(null)
        private set

    private val _progress = MutableStateFlow<ModProgress?>(null)
    val progress: StateFlow<ModProgress?> = _progress.asStateFlow()

    private var archive: ExtractedArchive? = null

    fun openFolder(treeUri: Uri) {
        runTask {
            _progress.value = ModProgress(0, 0, "Reading folder…")
            try {
                val files = withContext(Dispatchers.IO) {
                    TreeModFiles(context, treeUri) { found -> _progress.value = ModProgress(0, 0, "Reading folder… $found files") }
                }
                accept(DocumentIO.folderName(context, treeUri), files, null)
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not read the folder.")
            } finally {
                _progress.value = null
            }
        }
    }

    fun openArchive(uri: Uri) {
        runTask {
            _progress.value = ModProgress(0, 0, "Opening archive…")
            try {
                val extracted = withContext(Dispatchers.IO) {
                    val name = DocumentIO.displayName(context, uri)
                    val kind = Importer.archiveKind(context, uri, name)
                        ?: throw IllegalArgumentException("$name is not a ZIP, RAR or 7z archive. To convert a folder, use \"Open mod folder\".")
                    Importer.open(context, uri, name, kind) { done, total ->
                        _progress.value = ModProgress(done, total, "Extracting $name")
                    }
                }
                accept("${extracted.name} (${extracted.kind.label})", extracted.files, extracted)
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not open the archive.")
            } finally {
                _progress.value = null
            }
        }
    }

    private suspend fun accept(name: String, files: ModFiles, extracted: ExtractedArchive?) {
        val scan = withContext(Dispatchers.Default) { NmvModScanner.scan(files) }
        releaseArchive()
        if (scan.isEmpty) {
            extracted?.delete()
            mod = null
            status = StatusMessage.Error("No NightmareVision mod content was found in $name. Expected meta.json or folders like songs/<song>/charts, data/characters, data/stages or data/weeks.")
            return
        }
        archive = extracted
        mod = LoadedNmvMod(name, files, scan)
        result = null
        download = null
        val pack = withContext(Dispatchers.IO) { NmvModScanner.readInfo(files, scan) }
        title = pack?.title?.takeIf { it.isNotBlank() } ?: scan.root.trimEnd('/').substringAfterLast('/').ifBlank { name.substringBeforeLast('.') }
        description = pack?.description ?: ""
        author = pack?.author ?: ""
        status = StatusMessage.Info("Found a mod in $name.")
    }

    override val canDownload: Boolean get() = mod != null

    override fun downloadName(): String = ZipModWriter.folderName(title).replace(' ', '-') + "-$targetSuffix.zip"

    override fun zipPrefix(): String = ZipModWriter.folderName(title)

    override fun writeDownload(writer: ModWriter) {
        val loaded = mod ?: throw IllegalStateException("Open a mod first.")
        result = null
        try {
            val conversion = runMod(writer, false, { _progress.value = it }) { w, p -> runConversion(loaded.files, loaded.scan, w, info(), p) }
            result = conversion.report
        } finally {
            _progress.value = null
        }
    }

    private fun info() = ModInfo(title.trim(), description.trim(), author.trim())

    override fun save(modFolder: Uri) {
        val loaded = mod ?: return
        runTask {
            result = null
            try {
                val info = info()
                download = null
                val conversion = withContext(Dispatchers.IO) {
                    val writer = TreeModWriter(context, modFolder)
                    runMod(writer, true, { _progress.value = it }) { w, p -> runConversion(loaded.files, loaded.scan, w, info, p) }
                }
                result = conversion.report
                status = StatusMessage.Success("Done! Wrote ${conversion.filesWritten} files into ${DocumentIO.folderName(context, modFolder)}.")
            } catch (e: Exception) {
                status = failureMessage(e, "The conversion stopped.")
            } finally {
                _progress.value = null
            }
        }
    }

    fun clear() {
        releaseArchive()
        mod = null
        result = null
        download = null
        title = ""
        description = ""
        status = null
    }

    private fun releaseArchive() {
        archive?.delete()
        archive = null
    }

    override fun onCleared() {
        releaseArchive()
        super.onCleared()
    }
}

class NmvToPsychModViewModel(application: Application) : NmvModViewModel(application) {
    override val target: NmvTarget = NmvTarget.PSYCH
}

class NmvToVSliceModViewModel(application: Application) : NmvModViewModel(application) {
    override val target: NmvTarget = NmvTarget.VSLICE
}

class NmvToCodenameModViewModel(application: Application) : NmvModViewModel(application) {
    override val target: NmvTarget = NmvTarget.CODENAME
}

class PsychToNmvModViewModel(application: Application) : com.vconvertercrew.vconverter.ui.mod.ModViewModel(application) {
    override val targetSuffix: String = "nmv"

    override fun runConversion(files: ModFiles, scan: com.vconvertercrew.vconverter.mod.ModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        com.vconvertercrew.vconverter.nmv.ToNmvMod.fromPsych(files, scan, writer, info, progress)
}

class CodenameToNmvModViewModel(application: Application) : com.vconvertercrew.vconverter.ui.codename.CodenameModViewModel(application) {
    override val targetSuffix: String = "nmv"

    override fun runConversion(files: ModFiles, scan: com.vconvertercrew.vconverter.codename.CodenameModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        com.vconvertercrew.vconverter.nmv.ToNmvMod.fromCodename(files, scan, writer, info, progress)
}

class VSliceToNmvModViewModel(application: Application) : com.vconvertercrew.vconverter.ui.reverse.ReverseModViewModel(application) {
    override val targetSuffix: String = "nmv"

    override fun runConversion(files: ModFiles, scan: com.vconvertercrew.vconverter.reverse.VSliceModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        com.vconvertercrew.vconverter.nmv.ToNmvMod.fromVSlice(files, scan, writer, info, progress)
}

class NmvFileViewModel(application: Application, converter: com.vconvertercrew.vconverter.convert.FileConverter) : com.vconvertercrew.vconverter.ui.reverse.ReverseViewModel(application, converter)

class NmvToFpsModViewModel(application: Application) : NmvModViewModel(application) {
    override val target: NmvTarget = NmvTarget.FPSPLUS
}

class PsychToFpsModViewModel(application: Application) : com.vconvertercrew.vconverter.ui.mod.ModViewModel(application) {
    override val targetSuffix: String = "fpsplus"

    override fun runConversion(files: ModFiles, scan: com.vconvertercrew.vconverter.mod.ModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        com.vconvertercrew.vconverter.fpsplus.ToFpsMod.fromPsych(files, scan, writer, info, progress)
}

class CodenameToFpsModViewModel(application: Application) : com.vconvertercrew.vconverter.ui.codename.CodenameModViewModel(application) {
    override val targetSuffix: String = "fpsplus"

    override fun runConversion(files: ModFiles, scan: com.vconvertercrew.vconverter.codename.CodenameModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        com.vconvertercrew.vconverter.fpsplus.ToFpsMod.fromCodename(files, scan, writer, info, progress)
}

class VSliceToFpsModViewModel(application: Application) : com.vconvertercrew.vconverter.ui.reverse.ReverseModViewModel(application) {
    override val targetSuffix: String = "fpsplus"

    override fun runConversion(files: ModFiles, scan: com.vconvertercrew.vconverter.reverse.VSliceModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit): ModConversionResult =
        com.vconvertercrew.vconverter.fpsplus.ToFpsMod.fromVSlice(files, scan, writer, info, progress)
}
