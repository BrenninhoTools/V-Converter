package com.brenninhotools.vconverter.ui.online

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brenninhotools.vconverter.online.ModEntry
import com.brenninhotools.vconverter.onlineview.ModGalleryPanel
import com.brenninhotools.vconverter.onlineview.ModSharePanel
import com.brenninhotools.vconverter.onlineview.OnlineNewsPanel
import com.brenninhotools.vconverter.onlineview.OnlineState
import com.brenninhotools.vconverter.ui.components.FunkinCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineViewModel(application: Application) : AndroidViewModel(application) {
    val state = OnlineState(viewModelScope)

    fun saveTo(mod: ModEntry, uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        state.download(mod, open = { resolver.openOutputStream(uri, "w") })
    }

    fun pick(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val picked = withContext(Dispatchers.IO) {
                    val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "mod.zip"
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Could not read the file.")
                    name to bytes
                }
                require(picked.first.endsWith(".zip", ignoreCase = true)) { "Pick a .zip file." }
                state.file = picked
                if (state.title.isBlank()) state.title = picked.first.substringBeforeLast('.')
            } catch (e: Exception) {
                state.message = e.message ?: "Could not read the file."
            }
        }
    }
}

@Composable
fun OnlineModsScreen(viewModel: OnlineViewModel) {
    var pending by remember { mutableStateOf<ModEntry?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val mod = pending
        pending = null
        if (uri != null && mod != null) viewModel.saveTo(mod, uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            ModGalleryPanel(viewModel.state, onDownload = { mod ->
                pending = mod
                saver.launch(mod.fileName)
            })
        }
    }
}

@Composable
fun OnlineShareScreen(viewModel: OnlineViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.pick(uri) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            ModSharePanel(viewModel.state, onPickFile = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) })
        }
    }
}

@Composable
fun OnlineNewsScreen(viewModel: OnlineViewModel) {
    val links = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            OnlineNewsPanel(viewModel.state, onOpenLink = { runCatching { links.openUri(it) } })
        }
    }
}
