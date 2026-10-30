package com.brenninhotools.vconverter.ui.playtest

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editorview.CharacterSprite
import com.brenninhotools.vconverter.editorview.NoteSkin
import com.brenninhotools.vconverter.editorview.SongPlayer
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.playview.PlayPhase
import com.brenninhotools.vconverter.playview.PlayTestControls
import com.brenninhotools.vconverter.playview.PlayTestIssuesPanel
import com.brenninhotools.vconverter.playview.PlayTestPlatform
import com.brenninhotools.vconverter.playview.PlayTestResultsPanel
import com.brenninhotools.vconverter.playview.PlayTestSetupPanel
import com.brenninhotools.vconverter.playview.PlayTestState
import com.brenninhotools.vconverter.playview.PlayTestView
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.StatusMessage
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.editor.AndroidSongPlayer
import com.brenninhotools.vconverter.ui.theme.Funkin
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PlayTestLauncher {
    fun files(files: List<Pair<String, ByteArray>>, label: String)
    fun chart(document: ChartDocument, audio: List<EditorAudio>)
}

val LocalPlayTest = staticCompositionLocalOf<PlayTestLauncher?> { null }

private fun decodeBitmap(bytes: ByteArray): Pair<android.graphics.Bitmap, Float>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096 || (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 16_000_000L) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return bitmap to bitmap.width.toFloat() / bounds.outWidth.coerceAtLeast(1)
}

class AndroidPlayTestPlatform(private val context: Context) : PlayTestPlatform {
    override fun player(audio: List<EditorAudio>): SongPlayer? = if (audio.isEmpty()) null else AndroidSongPlayer.create(context, audio)

    override fun sprite(png: ByteArray, xml: String): CharacterSprite? {
        val (bitmap, scale) = decodeBitmap(png) ?: return null
        val atlas = SpriteAtlas.parse(xml).takeIf { it.frames.isNotEmpty() } ?: return null
        return CharacterSprite(bitmap.asImageBitmap(), atlas, scale)
    }

    override fun image(asset: StageAsset): StageImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(asset.png, 0, asset.png.size, bounds)
        val (bitmap, scale) = decodeBitmap(asset.png) ?: return null
        return StageImage(bitmap.asImageBitmap(), bounds.outWidth, bounds.outHeight, asset.xml?.let { SpriteAtlas.parse(it) }, scale)
    }
}

class PlayTestViewModel(application: Application) : BaseConverterViewModel(application) {
    val state = PlayTestState(viewModelScope, AndroidPlayTestPlatform(application))

    override fun downloadName(): String = "playtest.zip"

    fun open(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = load(uris, { true }) { file -> file.path to file.open().use { it.readBytes() } }.items
                val label = if (uris.size == 1) files.firstOrNull()?.first?.substringBefore('/')?.ifBlank { null } ?: "Picked files" else "${uris.size} files"
                state.open(files, label)
                status = null
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not read the files.")
            }
        }
    }

    override fun onCleared() {
        state.stop()
        super.onCleared()
    }
}

@Composable
private fun rememberSkin(): NoteSkin? {
    val context = LocalContext.current
    val skin by produceState<NoteSkin?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val image = context.assets.open(NoteSkin.IMAGE).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
                val xml = context.assets.open(NoteSkin.ATLAS).use { it.readBytes().toString(Charsets.UTF_8) }
                NoteSkin.create(image, xml)
            }.getOrNull()
        }
    }
    return skin
}

@Composable
fun PlayTestPlayScreen(viewModel: PlayTestViewModel) {
    val accent = Funkin.Up
    val state = viewModel.state
    val skin = rememberSkin()
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.open(it) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Open", accent, 1)
            DropZone(
                title = "Open a mod or a song",
                hint = "Pick a mod .zip (V-Slice, Psych or Codename), several files from a mod, or chart files with their audio. Converters' results and the Chart Editor have a PlayTest button too.",
                accent = accent,
                icon = R.drawable.ic_tab_chart,
                enabled = !viewModel.busy && !state.busy,
                onClick = { opener.launch(arrayOf("*/*")) }
            )
            com.brenninhotools.vconverter.ui.components.StatusBanner(viewModel.status, viewModel::dismissStatus)
        }
        FunkinCard {
            SectionTitle("PlayTest", accent, 2)
            PlayTestSetupPanel(state)
            if (state.phase == PlayPhase.RESULTS) PlayTestResultsPanel(state)
        }
    }
    val open = state.phase == PlayPhase.LOADING || state.phase == PlayPhase.PLAYING || state.phase == PlayPhase.PAUSED || state.phase == PlayPhase.RESULTS
    if (open) {
        Dialog(
            onDismissRequest = { if (state.phase == PlayPhase.PLAYING) state.pause() else state.backToSetup() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PlayTestView(state, Modifier.fillMaxSize(), skin)
                Column(
                    Modifier.align(Alignment.TopCenter).padding(8.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayTestControls(state, onClose = {})
                    if (state.phase == PlayPhase.RESULTS) {
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Funkin.Surface).border(1.5.dp, Funkin.Outline, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            PlayTestResultsPanel(state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayTestReportScreen(viewModel: PlayTestViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard { PlayTestIssuesPanel(viewModel.state) }
    }
}
