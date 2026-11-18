package com.vconvertercrew.vconverter.ui.online

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vconvertercrew.vconverter.online.ModEntry
import com.vconvertercrew.vconverter.onlineview.ModGalleryPanel
import com.vconvertercrew.vconverter.onlineview.FunkinOnlineMenu
import com.vconvertercrew.vconverter.onlineview.ModSharePanel
import com.vconvertercrew.vconverter.onlineview.OnlineActions
import com.vconvertercrew.vconverter.onlineview.OnlineMenuArt
import com.vconvertercrew.vconverter.onlineview.OnlineNewsPanel
import com.vconvertercrew.vconverter.onlineview.OnlineState
import com.vconvertercrew.vconverter.onlineview.PublicRoomsPanel
import com.vconvertercrew.vconverter.online.RoomInfo
import com.vconvertercrew.vconverter.ui.components.FunkinCard
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

@Composable
fun OnlineRoomsScreen(viewModel: OnlineViewModel, onJoin: (RoomInfo) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            PublicRoomsPanel(viewModel.state, onJoin = onJoin)
        }
    }
}

@Composable
fun OnlineMenuScreen(
    viewModel: OnlineViewModel,
    onJoin: (RoomInfo) -> Unit,
    onBack: () -> Unit,
    backRequests: Int,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    var pending by remember { mutableStateOf<ModEntry?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val mod = pending
        pending = null
        if (uri != null && mod != null) viewModel.saveTo(mod, uri)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.pick(uri) }
    val art = remember {
        val assets = context.assets
        OnlineMenuArt.load(
            image = { path -> runCatching { assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() },
            text = { path -> runCatching { assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull() }
        )
    }
    val actions = OnlineActions(
        download = { mod ->
            pending = mod
            saver.launch(mod.fileName)
        },
        pickFile = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
        join = onJoin,
        openLink = { runCatching { links.openUri(it) } }
    )
    FunkinOnlineMenu(viewModel.state, art, actions, onBack, backRequests = backRequests, overlay = overlay)
}
