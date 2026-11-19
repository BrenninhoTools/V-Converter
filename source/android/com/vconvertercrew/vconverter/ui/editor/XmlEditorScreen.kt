package com.vconvertercrew.vconverter.ui.editor

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vconvertercrew.vconverter.editorview.FnfXmlEditor
import com.vconvertercrew.vconverter.editorview.XmlEditorState
import com.vconvertercrew.vconverter.editorview.XmlExport
import com.vconvertercrew.vconverter.io.DocumentIO
import com.vconvertercrew.vconverter.io.OutputFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XmlEditorViewModel(application: Application) : AndroidViewModel(application) {
    val state = XmlEditorState(viewModelScope) { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }

    private fun read(uris: List<Uri>): List<Pair<String, ByteArray>> {
        val context = getApplication<Application>()
        return uris.mapNotNull { uri ->
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                DocumentIO.displayName(context, uri) to bytes
            }.getOrNull()
        }
    }

    fun addFrames(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { read(uris) }
            state.addFrames(files)
        }
    }

    fun addSheet(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { read(listOf(uri)).firstOrNull() } ?: return@launch
            state.addSheet(file.first, file.second)
        }
    }

    fun save(folder: Uri, export: XmlExport) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DocumentIO.writeFiles(
                        context, folder,
                        listOf(OutputFile(emptyList(), export.pngName, "", export.png), OutputFile(emptyList(), export.xmlName, export.xml))
                    )
                }
            }
            state.status = result.fold(
                { "Saved ${export.pngName} and ${export.xmlName} in ${DocumentIO.folderName(context, folder)}." },
                { "Could not save: ${it.message}" }
            )
        }
    }
}

@Composable
fun XmlEditorScreen(viewModel: XmlEditorViewModel, onExit: () -> Unit) {
    var pending by remember { mutableStateOf<XmlExport?>(null) }
    val frames = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.addFrames(it) }
    val sheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::addSheet) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val export = pending
        pending = null
        if (uri != null && export != null) viewModel.save(uri, export)
    }
    FnfXmlEditor(
        viewModel.state,
        onAddFrames = { frames.launch(arrayOf("image/*")) },
        onAddSheet = { sheet.launch(arrayOf("image/*")) },
        onSave = { export ->
            pending = export
            folder.launch(null)
        },
        onExit = onExit
    )
}
