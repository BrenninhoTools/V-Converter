package com.brenninhotools.vconverter.ui.mod

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.io.DocumentIO
import com.brenninhotools.vconverter.io.ExtractedArchive
import com.brenninhotools.vconverter.io.Importer
import com.brenninhotools.vconverter.io.TreeModFiles
import com.brenninhotools.vconverter.io.TreeModWriter
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModScan
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.mod.ZipModWriter
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LoadedMod(val sourceName: String, val files: ModFiles, val scan: ModScan)

class ModViewModel(application: Application) : BaseConverterViewModel(application) {
    var mod by mutableStateOf<LoadedMod?>(null)
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
                    Importer.extract(context, uri, name, kind) { done, total ->
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
        val scan = withContext(Dispatchers.Default) { ModScanner.scan(files) }
        releaseArchive()
        if (scan.isEmpty) {
            extracted?.delete()
            mod = null
            status = StatusMessage.Error("No Psych Engine mod content was found in $name. Expected folders like data, songs, characters, stages, weeks or images.")
            return
        }
        archive = extracted
        mod = LoadedMod(name, files, scan)
        result = null
        download = null
        val pack = withContext(Dispatchers.IO) { ModConverter.readPack(files, scan) }
        title = pack?.title?.takeIf { it.isNotBlank() } ?: scan.root.trimEnd('/').substringAfterLast('/').ifBlank { name.substringBeforeLast('.') }
        description = pack?.description ?: ""
        status = StatusMessage.Info("Found a mod in $name.")
    }

    override val canDownload: Boolean get() = mod != null

    override fun downloadName(): String = ZipModWriter.folderName(title).replace(' ', '-') + "-vslice.zip"

    override fun zipPrefix(): String = ZipModWriter.folderName(title)

    override fun writeDownload(writer: ModWriter) {
        val loaded = mod ?: throw IllegalStateException("Open a mod first.")
        result = null
        try {
            val conversion = ModConverter(loaded.files, loaded.scan, writer) { _progress.value = it }.convert(info())
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
                    ModConverter(loaded.files, loaded.scan, writer) { _progress.value = it }.convert(info)
                }
                result = conversion.report
                status = StatusMessage.Success("Done! Wrote ${conversion.filesWritten} files into ${DocumentIO.folderName(context, modFolder)}.")
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "The conversion stopped.")
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
