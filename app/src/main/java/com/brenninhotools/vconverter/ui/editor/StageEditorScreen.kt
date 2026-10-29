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
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageExport
import com.brenninhotools.vconverter.editor.StageLoader
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.editorview.StageEditorState
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.editorview.StageInfoPanel
import com.brenninhotools.vconverter.editorview.StageLayersPanel
import com.brenninhotools.vconverter.editorview.StagePreview
import com.brenninhotools.vconverter.editorview.StageSelectionPanel
import com.brenninhotools.vconverter.editorview.StageToolbar
import com.brenninhotools.vconverter.generator.Ids
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

class StageEditorViewModel(application: Application) : BaseConverterViewModel(application) {
    val editor = StageEditorState(StageDocument("new-stage", "New Stage"))
    var target by mutableStateOf(ExportTarget.VSLICE)
        private set
    var source by mutableStateOf("New stage")
        private set
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

    private fun decode(asset: StageAsset): StageImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(asset.png, 0, asset.png.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096 || (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 16_000_000L) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(asset.png, 0, asset.png.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        StageImage(bitmap.asImageBitmap(), bounds.outWidth, bounds.outHeight, asset.xml?.let { SpriteAtlas.parse(it) }, bitmap.width.toFloat() / bounds.outWidth.coerceAtLeast(1))
    }.getOrNull()

    private suspend fun readAll(uris: List<Uri>): List<Pair<String, ByteArray>> =
        load(uris, { it.extension in setOf("json", "xml", "png", "lua") }) { file -> file.path to file.open().use { it.readBytes() } }.items

    fun open(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = readAll(uris)
                val loaded = withContext(Dispatchers.Default) { StageLoader.load(files) }
                val decoded = withContext(Dispatchers.Default) {
                    loaded.assets.mapNotNull { (key, asset) -> decode(asset)?.let { key to it } }.toMap()
                }
                editor.load(loaded.document, loaded.assets, decoded)
                source = "${loaded.source} · ${loaded.document.id}"
                invalidate()
                val missing = loaded.document.props.count { !it.isSolid && editor.imageFor(it) == null }
                status = StatusMessage.Info(
                    "Opened ${loaded.document.id} from ${loaded.source}: ${loaded.document.props.size} props, ${decoded.size} images." +
                        if (missing > 0) " $missing prop(s) have no image yet: open them together with the stage." else ""
                )
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not open the stage.")
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = readAll(uris)
                val pngs = files.filter { it.first.endsWith(".png", true) }
                require(pngs.isNotEmpty()) { "Pick at least one .png (with its .xml for animated props)." }
                val xmls = files.filter { it.first.endsWith(".xml", true) }.associate { it.first.substringAfterLast('/').substringBeforeLast('.').lowercase() to it.second.toString(Charsets.UTF_8) }
                pngs.forEach { (path, bytes) ->
                    val stem = path.substringAfterLast('/').substringBeforeLast('.')
                    val assetPath = "stages/${editor.document.id}/$stem"
                    val asset = StageAsset(bytes, xmls[stem.lowercase()]?.takeIf { SpriteAtlas.parse(it).frames.isNotEmpty() })
                    val image = withContext(Dispatchers.Default) { decode(asset) }
                    editor.addAsset(assetPath.lowercase(), asset, image)
                    val prefix = image?.atlas?.prefixes()?.firstOrNull() ?: ""
                    val frame = prefix.takeIf { it.isNotEmpty() }?.let { image?.atlas?.framesFor(it)?.firstOrNull() }
                    val w = frame?.frameWidth ?: image?.width ?: 200
                    val h = frame?.frameHeight ?: image?.height ?: 200
                    editor.addProp(StageProp(Ids.of(stem).ifEmpty { "prop" }, assetPath, (editor.camX - w / 2f).toDouble().let { Math.round(it).toDouble() }, (editor.camY - h / 2f).toDouble().let { Math.round(it).toDouble() }, prefix = prefix))
                }
                invalidate()
                status = null
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not add the images.")
            }
        }
    }

    fun createNew() {
        editor.load(StageDocument("new-stage", "New Stage"), emptyMap(), emptyMap())
        source = "New stage"
        invalidate()
        status = StatusMessage.Info("Started a new stage with the base game character positions. Add images to build it.")
    }

    fun export() {
        runTask {
            try {
                val document = editor.document
                val assets = editor.assets
                val chosen = target
                output = withContext(Dispatchers.Default) { StageExport.build(document, chosen, assets) }
                exportedRevision = editor.revision
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Could not export the stage.")
            }
        }
    }
}

@Composable
fun StageEditorScreen(viewModel: StageEditorViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Up
    val busy = viewModel.busy
    val editor = viewModel.editor
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.open(it) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.addImages(it) }

    LaunchedEffect(editor.revision) { viewModel.changed(editor.revision) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Stage", accent, 1)
            Text(viewModel.source, color = Funkin.Muted)
            DropZone(
                title = "Open a stage",
                hint = "Pick a V-Slice or Psych stage .json (with its .lua if it builds sprites there) or a Codename stage .xml, together with the stage images (.png, and .xml for animated ones). Archives (.zip/.rar/.7z) and whole mod folders in an archive work too.",
                accent = accent,
                icon = R.drawable.ic_tab_stage,
                enabled = !busy,
                onClick = { opener.launch(arrayOf("*/*")) }
            )
            OutlineButton("Start a new stage", accent, onClick = viewModel::createNew, enabled = !busy)
        }
        FunkinCard {
            SectionTitle("Preview", accent, 2)
            StageToolbar(editor)
            StagePreview(
                editor,
                Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Funkin.Outline, RoundedCornerShape(14.dp))
            )
            StageSelectionPanel(editor)
        }
        FunkinCard {
            SectionTitle("Layers", accent, 3)
            StageLayersPanel(editor, onAddImage = { imagePicker.launch(arrayOf("image/png", "text/xml", "application/xml", "*/*")) })
        }
        FunkinCard {
            SectionTitle("Stage info", accent, 4)
            StageInfoPanel(editor)
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
                    ExportTarget.VSLICE -> "data/stages/<id>.json and the images in images/…"
                    ExportTarget.PSYCH -> "stages/<id>.json (plus a .lua for what Psych JSON can't hold) and the images."
                    ExportTarget.CODENAME -> "data/stages/<id>.xml and the images."
                },
                color = Funkin.Muted
            )
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 6,
            canConvert = true,
            onConvert = viewModel::export,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Export",
            buttonText = "Export ${viewModel.target.label}",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
