package com.brenninhotools.vconverter.ui.editor

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.editor.CharacterDocument
import com.brenninhotools.vconverter.editor.CharacterExport
import com.brenninhotools.vconverter.editor.CharacterLoader
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editorview.AnimationListPanel
import com.brenninhotools.vconverter.editorview.AnimationPanel
import com.brenninhotools.vconverter.editorview.CharacterEditorState
import com.brenninhotools.vconverter.editorview.CharacterInfoPanel
import com.brenninhotools.vconverter.editorview.CharacterPreview
import com.brenninhotools.vconverter.editorview.CharacterSprite
import com.brenninhotools.vconverter.editorview.CharacterToolbar
import com.brenninhotools.vconverter.editorview.DragModeBar
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.StatusMessage
import com.brenninhotools.vconverter.ui.components.ChoiceChips
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.OutlineButton
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.theme.Funkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CharacterEditorViewModel(application: Application) : BaseConverterViewModel(application) {
    val editor = CharacterEditorState(CharacterDocument("new-character", "New Character"))
    var target by mutableStateOf(ExportTarget.VSLICE)
        private set
    var source by mutableStateOf("New character")
        private set
    var sheetName by mutableStateOf<String?>(null)
        private set
    private var image: ByteArray? = null
    private var xml: String? = null
    private var exportedRevision = -1

    override fun downloadName(): String = "${editor.document.id}-${target.name.lowercase()}.zip"

    fun changed(revision: Int) {
        if (revision != exportedRevision && output != null) invalidate()
    }

    fun selectTarget(value: ExportTarget) {
        if (value != target) {
            target = value
            invalidate()
        }
    }

    private suspend fun readAll(uris: List<Uri>): List<Pair<String, ByteArray>> =
        load(uris, { it.extension in setOf("json", "xml", "png") }) { file -> file.path to file.open().use { it.readBytes() } }.items

    private fun decode(bytes: ByteArray, xmlText: String): CharacterSprite {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096 || (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 16_000_000L) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("Could not read the spritesheet image.")
        return CharacterSprite(bitmap.asImageBitmap(), SpriteAtlas.parse(xmlText), bitmap.width.toFloat() / bounds.outWidth.coerceAtLeast(1))
    }

    fun open(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = readAll(uris)
                val loaded = withContext(Dispatchers.Default) { CharacterLoader.load(files) }
                val sprite = withContext(Dispatchers.Default) {
                    if (loaded.image != null && loaded.xml != null) decode(loaded.image, loaded.xml) else null
                }
                editor.load(loaded.document)
                editor.sprite = sprite
                image = loaded.image
                xml = loaded.xml
                sheetName = loaded.imageName
                source = "${loaded.source} · ${loaded.document.id}"
                invalidate()
                status = StatusMessage.Info(
                    "Opened ${loaded.document.id} from ${loaded.source}: ${loaded.document.animations.size} animations" +
                        if (sprite == null) ". Add the spritesheet (.png + .xml) to see it." else "."
                )
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not open the character.")
            }
        }
    }

    fun replaceSheet(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = readAll(uris)
                val png = files.firstOrNull { it.first.endsWith(".png", true) } ?: throw IllegalArgumentException("Pick the spritesheet .png and its .xml.")
                val sheet = files.firstOrNull { it.first.endsWith(".xml", true) } ?: throw IllegalArgumentException("Pick the spritesheet .xml too.")
                val text = sheet.second.toString(Charsets.UTF_8)
                require(SpriteAtlas.parse(text).frames.isNotEmpty()) { "${sheet.first.substringAfterLast('/')} is not a Sparrow spritesheet." }
                val sprite = withContext(Dispatchers.Default) { decode(png.second, text) }
                editor.sprite = sprite
                image = png.second
                xml = text
                sheetName = png.first.substringAfterLast('/')
                if (editor.document.animations.isEmpty()) {
                    val stem = png.first.substringAfterLast('/').substringBeforeLast('.')
                    val fresh = CharacterDocument.fromAtlas(editor.document.id, sprite.atlas, "characters/$stem")
                    editor.load(fresh.copy(name = editor.document.name.ifEmpty { fresh.name }))
                }
                invalidate()
                status = StatusMessage.Info("Loaded ${sprite.atlas.frames.size} frames from ${sheetName}.")
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not read the spritesheet.")
            }
        }
    }

    fun createNew() {
        editor.load(CharacterDocument("new-character", "New Character"))
        editor.sprite = null
        image = null
        xml = null
        sheetName = null
        source = "New character"
        invalidate()
        status = StatusMessage.Info("Started a new character. Add its spritesheet to pick the animations.")
    }

    fun export() {
        runTask {
            try {
                val document = editor.document
                val chosen = target
                output = withContext(Dispatchers.Default) { CharacterExport.build(document, chosen, image, xml) }
                exportedRevision = editor.revision
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Could not export the character.")
            }
        }
    }

    fun pause() {
        if (editor.playing) editor.togglePlay()
    }
}

@Composable
fun CharacterEditorScreen(viewModel: CharacterEditorViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Left
    val busy = viewModel.busy
    val editor = viewModel.editor
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.open(it) }
    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.replaceSheet(it) }

    LaunchedEffect(editor.revision) { viewModel.changed(editor.revision) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Character", accent, 1)
            Text(viewModel.source, color = Funkin.Muted)
            DropZone(
                title = "Open a character",
                hint = "Pick a V-Slice or Psych character .json or a Codename character .xml together with its spritesheet .png + .xml. A spritesheet alone starts a new character with guessed animations. Archives (.zip/.rar/.7z) work too.",
                accent = accent,
                icon = R.drawable.ic_tab_character,
                enabled = !busy,
                onClick = { opener.launch(arrayOf("*/*")) }
            )
            OutlineButton(viewModel.sheetName?.let { "Spritesheet: $it (replace)" } ?: "Add spritesheet (.png + .xml)", accent, onClick = { sheetPicker.launch(arrayOf("*/*")) }, enabled = !busy)
            OutlineButton("Start a new character", accent, onClick = viewModel::createNew, enabled = !busy)
        }
        FunkinCard {
            SectionTitle("Preview", accent, 2)
            CharacterToolbar(editor)
            DragModeBar(editor)
            CharacterPreview(
                editor,
                Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Funkin.Outline, RoundedCornerShape(14.dp))
            )
        }
        FunkinCard {
            SectionTitle("Animations", accent, 3)
            AnimationListPanel(editor)
            AnimationPanel(editor)
        }
        FunkinCard {
            SectionTitle("Character info", accent, 4)
            CharacterInfoPanel(editor)
        }
        FunkinCard {
            SectionTitle("Export format", accent, 5)
            ChoiceChips(
                options = ExportTarget.entries,
                label = { it.label },
                isSelected = { it == viewModel.target },
                onClick = viewModel::selectTarget,
                accent = accent,
                enabled = !busy
            )
            Text(
                when (viewModel.target) {
                    ExportTarget.VSLICE -> "data/characters/<id>.json and images/<path>.png/.xml."
                    ExportTarget.PSYCH -> "characters/<id>.json and images/<path>.png/.xml, offsets and position converted."
                    ExportTarget.CODENAME -> "data/characters/<id>.xml and images/characters/<sprite>.png/.xml."
                },
                color = Funkin.Muted
            )
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 6,
            canConvert = editor.document.animations.isNotEmpty(),
            onConvert = viewModel::export,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Export",
            buttonText = "Export ${viewModel.target.label}",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
