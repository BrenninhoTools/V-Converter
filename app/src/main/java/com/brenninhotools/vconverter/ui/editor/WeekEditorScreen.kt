package com.brenninhotools.vconverter.ui.editor

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.WeekDocument
import com.brenninhotools.vconverter.editor.WeekExport
import com.brenninhotools.vconverter.editor.WeekLoader
import com.brenninhotools.vconverter.editor.WeekProp
import com.brenninhotools.vconverter.editor.WeekSlot
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.editorview.WeekEditorState
import com.brenninhotools.vconverter.editorview.WeekInfoPanel
import com.brenninhotools.vconverter.editorview.WeekPreview
import com.brenninhotools.vconverter.editorview.WeekPropsPanel
import com.brenninhotools.vconverter.editorview.WeekSongsPanel
import com.brenninhotools.vconverter.editorview.WeekToolbar
import com.brenninhotools.vconverter.editorview.weekSummary
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

enum class WeekImageRole { PROP, TITLE, BACKGROUND }

class WeekEditorViewModel(application: Application) : BaseConverterViewModel(application) {
    val editor = WeekEditorState(WeekDocument("new-week", "New Week"))
    val live = com.brenninhotools.vconverter.editorview.LiveSession(com.brenninhotools.vconverter.editorview.WeekLiveAdapter(editor) { asset -> decode(asset) }) { block -> android.os.Handler(android.os.Looper.getMainLooper()).post(block) }
    var target by mutableStateOf(ExportTarget.VSLICE)
        private set
    var source by mutableStateOf("New week")
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
        load(uris, { it.extension in setOf("json", "xml", "png", "hxc") }) { file -> file.path to file.open().use { it.readBytes() } }.items

    fun open(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = readAll(uris)
                val loaded = withContext(Dispatchers.Default) { WeekLoader.load(files) }
                val decoded = withContext(Dispatchers.Default) {
                    loaded.assets.mapNotNull { (key, asset) -> decode(asset)?.let { key to it } }.toMap()
                }
                editor.load(loaded.document, loaded.assets, decoded)
                source = "${loaded.source} · ${loaded.document.id}"
                invalidate()
                status = StatusMessage.Info("Opened ${loaded.document.id} from ${loaded.source}: ${weekSummary(loaded.document)}, ${decoded.size} image(s).")
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not open the week.")
            }
        }
    }

    fun addImage(role: WeekImageRole, slot: WeekSlot?, uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val files = readAll(uris)
                val png = files.firstOrNull { it.first.endsWith(".png", true) } ?: throw IllegalArgumentException("Pick a .png (with its .xml for an animated character).")
                val xml = files.firstOrNull { it.first.endsWith(".xml", true) }?.second?.toString(Charsets.UTF_8)?.takeIf { SpriteAtlas.parse(it).frames.isNotEmpty() }
                val stem = png.first.substringAfterLast('/').substringBeforeLast('.')
                val doc = editor.document
                val path = when (role) {
                    WeekImageRole.TITLE -> doc.title
                    WeekImageRole.BACKGROUND -> "menubackgrounds/menu_${doc.id}"
                    WeekImageRole.PROP -> doc.prop(slot ?: WeekSlot.DAD)?.takeIf { !it.isBase }?.assetPath ?: "storymenu/props/$stem"
                }
                val asset = StageAsset(png.second, if (role == WeekImageRole.PROP) xml else null)
                val image = withContext(Dispatchers.Default) { decode(asset) }
                editor.addAsset(path, asset, image)
                when (role) {
                    WeekImageRole.TITLE -> editor.edit { it.copy(titleAsset = path) }
                    WeekImageRole.BACKGROUND -> editor.edit { it.copy(background = path) }
                    WeekImageRole.PROP -> {
                        val target = slot ?: WeekSlot.DAD
                        val prefixes = image?.atlas?.prefixes() ?: emptyList()
                        val old = editor.document.prop(target)
                        val idle = prefixes.firstOrNull { it.contains("idle", true) } ?: prefixes.firstOrNull() ?: "idle"
                        val confirm = prefixes.firstOrNull { it.contains("confirm", true) } ?: ""
                        val base = WeekProp(path, offsetX = old?.offsetX ?: 0.0, offsetY = old?.offsetY ?: 0.0).withPrefix("idle", idle).withPrefix("confirm", confirm)
                        editor.edit { it.withProp(target, base) }
                        editor.selected = target
                    }
                }
                invalidate()
                status = null
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not load the image.")
            }
        }
    }

    fun createNew() {
        editor.load(WeekDocument("new-week", "New Week"), emptyMap(), emptyMap())
        source = "New week"
        invalidate()
        status = StatusMessage.Info("Started a new week with the base game menu characters. Add its songs and title image.")
    }

    fun export() {
        runTask {
            try {
                val document = editor.document
                val assets = editor.assets
                val chosen = target
                output = withContext(Dispatchers.Default) { WeekExport.build(document, chosen, assets) }
                exportedRevision = editor.revision
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Could not export the week.")
            }
        }
    }
}

@Composable
fun WeekEditorScreen(viewModel: WeekEditorViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Right
    val busy = viewModel.busy
    val editor = viewModel.editor
    var pending by remember { mutableStateOf<Pair<WeekImageRole, WeekSlot?>>(WeekImageRole.PROP to null) }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.open(it) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.addImage(pending.first, pending.second, it) }
    fun pick(role: WeekImageRole, slot: WeekSlot? = null) {
        pending = role to slot
        imagePicker.launch(arrayOf("image/png", "text/xml", "application/xml", "*/*"))
    }

    LaunchedEffect(editor.revision) { viewModel.changed(editor.revision) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Week", accent, 1)
            Text(viewModel.source, color = Funkin.Muted)
            DropZone(
                title = "Open a week",
                hint = "Pick a V-Slice level .json, a Psych week .json (with images/menucharacters/*.json) or a Codename week .xml (with data/weeks/characters/*.xml), plus the title and menu character images. Song metadata files fill in the song names. Archives (.zip/.rar/.7z) work too.",
                accent = accent,
                icon = R.drawable.ic_tab_week,
                enabled = !busy,
                onClick = { opener.launch(arrayOf("*/*")) }
            )
            OutlineButton("Start a new week", accent, onClick = viewModel::createNew, enabled = !busy)
        }
        FunkinCard {
            SectionTitle("Story menu", accent, 2)
            WeekToolbar(editor)
            WeekPreview(
                editor,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Funkin.Outline, RoundedCornerShape(14.dp))
            )
            WeekPropsPanel(editor, onLoadImage = { slot -> pick(WeekImageRole.PROP, slot) })
        }
        FunkinCard {
            SectionTitle("Songs", accent, 3)
            WeekSongsPanel(editor)
        }
        FunkinCard {
            SectionTitle("Week info", accent, 4)
            WeekInfoPanel(editor)
            OutlineButton("Title image (.png)", accent, onClick = { pick(WeekImageRole.TITLE) }, enabled = !busy)
            OutlineButton("Background image (.png)", accent, onClick = { pick(WeekImageRole.BACKGROUND) }, enabled = !busy)
        }
        FunkinCard {
            com.brenninhotools.vconverter.editorview.LiveSessionPanel(viewModel.live)
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
                    ExportTarget.VSLICE -> "data/levels/<id>.json (plus scripts/levels/ when locked or hidden from freeplay) and the images."
                    ExportTarget.PSYCH -> "weeks/<id>.json, images/menucharacters/*.json and the images."
                    ExportTarget.CODENAME -> "data/weeks/weeks/<id>.xml, data/weeks/characters/*.xml and the images."
                },
                color = Funkin.Muted
            )
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 6,
            canConvert = editor.document.songs.isNotEmpty(),
            onConvert = viewModel::export,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Export",
            buttonText = "Export ${viewModel.target.label}",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
