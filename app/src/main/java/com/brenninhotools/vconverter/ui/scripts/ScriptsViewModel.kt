package com.brenninhotools.vconverter.ui.scripts

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.script.ScriptConverter
import com.brenninhotools.vconverter.script.ScriptScope
import com.brenninhotools.vconverter.script.ScriptSource
import com.brenninhotools.vconverter.script.ScriptTarget
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertedOutput
import com.brenninhotools.vconverter.ui.StatusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedScript(
    val key: String,
    val fileName: String,
    val text: String,
    val scope: ScriptScope,
    val target: String
) {
    val isLua: Boolean get() = fileName.substringAfterLast('.').equals("lua", ignoreCase = true)
    val lineCount: Int get() = text.count { it == '\n' } + 1
}

class ScriptsViewModel(application: Application) : BaseConverterViewModel(application) {
    var scripts by mutableStateOf(listOf<PickedScript>())
        private set
    var modName by mutableStateOf("")

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            val loaded = load(uris, ::isScriptCandidate) { file ->
                require(file.extension in extensions) { "not a .lua or .hx script" }
                val detected = ScriptConverter.detectTarget(file.path)
                PickedScript(file.path, file.name, file.readText(), detected.scope, detected.key)
            }
            scripts = scripts.filterNot { old -> loaded.items.any { it.key == old.key } } + loaded.items
            invalidate()
            loadStatus(loaded, "script(s)")
        }
    }

    private fun isScriptCandidate(file: ImportedFile): Boolean = file.extension in extensions

    fun setScope(index: Int, scope: ScriptScope) {
        scripts = scripts.mapIndexed { i, item ->
            if (i != index) item else item.copy(
                scope = scope,
                target = when (scope) {
                    ScriptScope.GLOBAL -> ""
                    ScriptScope.EVENT, ScriptScope.NOTE_TYPE, ScriptScope.STAGE ->
                        item.target.ifBlank { item.fileName.substringBeforeLast('.') }
                    ScriptScope.SONG -> item.target
                }
            )
        }
        invalidate()
    }

    fun setTarget(index: Int, value: String) {
        scripts = scripts.mapIndexed { i, item -> if (i == index) item.copy(target = value) else item }
        invalidate()
    }

    fun remove(index: Int) {
        scripts = scripts.filterIndexed { i, _ -> i != index }
        invalidate()
    }

    fun clear() {
        scripts = emptyList()
        status = null
        invalidate()
    }

    fun convert() {
        runTask {
            try {
                require(scripts.isNotEmpty()) { "Add at least one Psych Engine script." }
                val missing = scripts.firstOrNull { it.scope != ScriptScope.GLOBAL && it.target.isBlank() }
                require(missing == null) { "${missing?.fileName} needs a ${missing?.scope?.label?.lowercase()} name." }
                val slug = ScriptConverter.slug(modName.ifBlank { "VConverter" })
                val snapshot = scripts
                val result = withContext(Dispatchers.Default) {
                    val sources = snapshot.map { item ->
                        val key = if (item.scope == ScriptScope.SONG) sanitizeId(item.target) else item.target.trim()
                        ScriptSource(item.key, item.text, ScriptTarget(item.scope, key))
                    }
                    val noteTypes = snapshot.filter { it.scope == ScriptScope.NOTE_TYPE }.map { it.target.trim() }
                    val bundle = ScriptConverter.convert(slug, sources, ScriptConverter.noteTypeMap(noteTypes))
                    require(bundle.converted > 0) { bundle.report.warnings.firstOrNull() ?: "No script could be converted." }
                    val files = bundle.files.map { file ->
                        OutputFile(file.path.substringBeforeLast('/').split('/'), file.path.substringAfterLast('/'), file.content)
                    }
                    ConvertedOutput(files, bundle.report)
                }
                output = result
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Conversion failed.")
            }
        }
    }

    companion object {
        val extensions = setOf("lua", "hx", "hxs", "hscript")
    }
}
