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

    fun save(modFolder: Uri) {
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

    protected suspend fun <T> readEach(uris: List<Uri>, read: (Uri, String) -> T): Pair<List<T>, List<String>> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<T>()
            val failures = mutableListOf<String>()
            uris.forEach { uri ->
                val name = DocumentIO.displayName(context, uri)
                try {
                    items += read(uri, name)
                } catch (e: Exception) {
                    failures += "$name: ${e.message}"
                }
            }
            items to failures
        }

    protected fun loadStatus(loaded: Int, noun: String, failures: List<String>) {
        status = if (failures.isEmpty()) {
            StatusMessage.Info("Loaded $loaded $noun.")
        } else {
            StatusMessage.Error("Some files could not be read:\n" + failures.joinToString("\n"))
        }
    }

    companion object {
        fun sanitizeId(raw: String): String =
            raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
