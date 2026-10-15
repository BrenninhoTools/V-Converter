package com.brenninhotools.vconverter.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.io.DocumentIO
import com.brenninhotools.vconverter.io.ExtractedArchive
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.Importer
import com.brenninhotools.vconverter.io.OutputFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface StatusMessage {
    val text: String

    data class Info(override val text: String) : StatusMessage
    data class Success(override val text: String) : StatusMessage
    data class Error(override val text: String) : StatusMessage
}

data class ConvertedOutput(
    val files: List<OutputFile>,
    val report: Report
)

abstract class BaseConverterViewModel(application: Application) : AndroidViewModel(application) {
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<StatusMessage?>(null)
        protected set
    var output by mutableStateOf<ConvertedOutput?>(null)
        protected set

    protected val context: Application get() = getApplication()

    fun dismissStatus() {
        status = null
    }

    protected fun invalidate() {
        output = null
    }

    open fun save(modFolder: Uri) {
        val result = output ?: return
        runTask {
            try {
                val written = withContext(Dispatchers.IO) { DocumentIO.writeFiles(context, modFolder, result.files) }
                status = StatusMessage.Success("Saved ${written.size} file(s):\n" + written.joinToString("\n") { "• $it" })
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not save the files.")
            }
        }
    }

    protected fun runTask(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    protected class Loaded<T>(val items: List<T>, val failures: List<String>, val skipped: Int, val archives: List<String>)

    protected suspend fun <T> load(
        uris: List<Uri>,
        accept: (ImportedFile) -> Boolean,
        read: (ImportedFile) -> T
    ): Loaded<T> = withContext(Dispatchers.IO) {
        val items = mutableListOf<T>()
        val failures = mutableListOf<String>()
        var skipped = 0
        val archiveNames = mutableListOf<String>()
        val extracted = mutableListOf<ExtractedArchive>()
        try {
            uris.forEach { uri ->
                try {
                    val (files, archives) = Importer.importFiles(context, listOf(uri))
                    extracted += archives
                    archiveNames += archives.map { "${it.name} (${it.kind.label})" }
                    files.forEach { file ->
                        if (file.fromArchive && !accept(file)) {
                            skipped++
                        } else {
                            try {
                                items += read(file)
                            } catch (e: Exception) {
                                if (file.fromArchive) skipped++ else failures += "${file.name}: ${e.message}"
                            }
                        }
                    }
                } catch (e: Exception) {
                    failures += "${DocumentIO.displayName(context, uri)}: ${e.message}"
                }
            }
        } finally {
            extracted.forEach { it.delete() }
        }
        Loaded(items, failures, skipped, archiveNames)
    }

    protected fun loadStatus(loaded: Loaded<*>, noun: String) {
        val text = buildString {
            append("Loaded ${loaded.items.size} $noun")
            if (loaded.archives.isNotEmpty()) append(" from ${loaded.archives.joinToString()}")
            append('.')
            if (loaded.skipped > 0) append(" ${loaded.skipped} other file(s) in the archive were ignored.")
        }
        status = when {
            loaded.failures.isNotEmpty() -> StatusMessage.Error(text + "\nSome files could not be read:\n" + loaded.failures.joinToString("\n"))
            loaded.items.isEmpty() -> StatusMessage.Error(text)
            else -> StatusMessage.Info(text)
        }
    }

    companion object {
        fun sanitizeId(raw: String): String =
            raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
