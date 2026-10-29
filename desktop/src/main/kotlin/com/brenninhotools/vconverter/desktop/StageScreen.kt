package com.brenninhotools.vconverter.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.StageDocument
import com.brenninhotools.vconverter.editor.StageExport
import com.brenninhotools.vconverter.editor.StageLoader
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.editorview.EditorChip
import com.brenninhotools.vconverter.editorview.EditorTyping
import com.brenninhotools.vconverter.editorview.StageEditorState
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.editorview.StageInfoPanel
import com.brenninhotools.vconverter.editorview.StageLayersPanel
import com.brenninhotools.vconverter.editorview.StagePreview
import com.brenninhotools.vconverter.editorview.StageSelectionPanel
import com.brenninhotools.vconverter.editorview.StageToolbar
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DesktopStageEditor(private val scope: CoroutineScope) {
    val editor = StageEditorState(StageDocument("new-stage", "New Stage"))
    var target by mutableStateOf(ExportTarget.VSLICE)
    var source by mutableStateOf("New stage")
        private set
    var status by mutableStateOf<Status?>(null)
    var busy by mutableStateOf(false)
        private set
    var output by mutableStateOf<ConvertedOutput?>(null)
        private set
    private var exportedRevision = -1

    fun changed(revision: Int) {
        if (revision != exportedRevision) output = null
    }

    private fun readAll(sources: List<File>): List<Pair<String, ByteArray>> {
        val wanted = setOf("json", "xml", "png", "lua")
        val result = ArrayList<Pair<String, ByteArray>>()
        sources.forEach { source ->
            when {
                source.isDirectory -> source.walkTopDown().filter { it.isFile && it.extension.lowercase() in wanted }.forEach { f ->
                    result += (source.name + "/" + f.relativeTo(source).invariantSeparatorsPath) to f.readBytes()
                }
                else -> {
                    val kind = Archives.kind(source)
                    if (kind == null) {
                        if (source.extension.lowercase() in wanted) result += source.name to source.readBytes()
                    } else {
                        val archive = Archives.extract(source, source.name, kind, Workspace.cache)
                        try {
                            archive.files.paths.filter { it.substringAfterLast('.').lowercase() in wanted }.forEach { path ->
                                result += path to File(archive.directory, path).readBytes()
                            }
                        } finally {
                            archive.delete()
                        }
                    }
                }
            }
        }
        return result
    }

    private fun decode(asset: StageAsset): StageImage? = runCatching {
        val image = org.jetbrains.skia.Image.makeFromEncoded(asset.png)
        StageImage(image.toComposeImageBitmap(), image.width, image.height, asset.xml?.let { SpriteAtlas.parse(it) })
    }.getOrNull()

    private fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun open(sources: List<File>) {
        if (sources.isEmpty()) return
        task {
            try {
                val loaded = withContext(Dispatchers.IO) { StageLoader.load(readAll(sources)) }
                val decoded = withContext(Dispatchers.Default) { loaded.assets.mapNotNull { (key, asset) -> decode(asset)?.let { key to it } }.toMap() }
                editor.load(loaded.document, loaded.assets, decoded)
                source = "${loaded.source} · ${loaded.document.id}"
                output = null
                val missing = loaded.document.props.count { !it.isSolid && editor.imageFor(it) == null }
                status = Status.Info(
                    "Opened ${loaded.document.id} from ${loaded.source}: ${loaded.document.props.size} props, ${decoded.size} images." +
                        if (missing > 0) " $missing prop(s) have no image yet: open the mod folder or add the images." else ""
                )
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not open the stage.")
            }
        }
    }

    fun addImages(sources: List<File>) {
        if (sources.isEmpty()) return
        task {
            try {
                val files = withContext(Dispatchers.IO) { readAll(sources) }
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
                    editor.addProp(StageProp(Ids.of(stem).ifEmpty { "prop" }, assetPath, Math.round(editor.camX - w / 2.0).toDouble(), Math.round(editor.camY - h / 2.0).toDouble(), prefix = prefix))
                }
                output = null
                status = null
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not add the images.")
            }
        }
    }

    fun createNew() {
        editor.load(StageDocument("new-stage", "New Stage"), emptyMap(), emptyMap())
        source = "New stage"
        output = null
        status = Status.Info("Started a new stage with the base game character positions. Add images to build it.")
    }

    fun export() {
        val document = editor.document
        val assets = editor.assets
        val chosen = target
        task {
            try {
                output = withContext(Dispatchers.Default) { StageExport.build(document, chosen, assets) }
                exportedRevision = editor.revision
                status = null
            } catch (e: Exception) {
                output = null
                status = Status.Error(e.message ?: "Could not export the stage.")
            }
        }
    }

    fun save(zip: Boolean, onSaved: (File) -> Unit) {
        val result = output ?: return
        val target = if (zip) Dialogs.saveFile("Save the stage", "${editor.document.id}-${this.target.name.lowercase()}.zip") ?: return
        else Dialogs.chooseFolder("Choose the mod folder to write into") ?: return
        task {
            try {
                withContext(Dispatchers.IO) {
                    if (zip) Workspace.writeZip(target, "") { writer -> result.files.forEach { it.writeTo(writer) } }
                    else Workspace.writeFolder(target) { writer -> result.files.forEach { it.writeTo(writer) } }
                }
                status = Status.Success("Saved ${result.files.size} file(s) to ${target.absolutePath}")
                onSaved(target)
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not save the files.")
            }
        }
    }
}

@Composable
fun StageEditorScreen(state: AppState) {
    val model = state.stageEditor
    val editor = model.editor
    val accent = Palette.Up

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }

    Card {
        SectionTitle("Stage", accent)
        Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Open stage…", accent, !model.busy) { model.open(Dialogs.openFiles("Open the stage files and images, or an archive")) }
            Secondary("Open folder…", !model.busy) { Dialogs.chooseFolder("Choose the mod folder with the stage")?.let { model.open(listOf(it)) } }
            Secondary("New stage", !model.busy) { model.createNew() }
        }
        Text(
            "A V-Slice or Psych stage .json (with its .lua) or a Codename stage .xml, plus the images. Opening the whole mod folder finds every image the stage uses. You can also drop files on the window.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted
        )
        StatusBanner(model.status)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                SectionTitle("Preview", accent)
                StageToolbar(editor)
                StagePreview(
                    editor,
                    Modifier.fillMaxWidth().height(620.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
                )
            }
            Card { StageSelectionPanel(editor) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card { StageLayersPanel(editor, onAddImage = { model.addImages(Dialogs.openFiles("Pick the images (.png, with .xml for animated ones)")) }) }
            Card { StageInfoPanel(editor) }
        }
    }

    val output = model.output
    Card(accent = if (output != null) accent else null) {
        SectionTitle("Export", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExportTarget.entries.forEach { t -> EditorChip(t.label, t == model.target) { if (t != model.target) { model.target = t; model.changed(-2) } } }
        }
        Primary("Export ${model.target.label}", accent, !model.busy) { model.export() }
        if (output != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Primary("Save into mod folder…", accent, !model.busy) { model.save(false) { state.lastOutput = it } }
                Secondary("Save as .zip…", !model.busy) { model.save(true) { state.lastOutput = it } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                output.files.forEach { file -> Text(file.relativePath, style = MaterialTheme.typography.bodySmall, color = Palette.Text) }
            }
            ReportView(output.report, accent)
        }
    }
}

fun stageShortcut(editor: StageEditorState, event: KeyEvent): Boolean {
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    if (!ctrl && EditorTyping.fields > 0) return false
    val step = if (event.isShiftPressed) 10.0 else 1.0
    when {
        ctrl && event.key == Key.Z && event.isShiftPressed -> editor.redo()
        ctrl && event.key == Key.Z -> editor.undo()
        ctrl && event.key == Key.Y -> editor.redo()
        ctrl && event.key == Key.D -> editor.duplicateSelected()
        event.key == Key.DirectionLeft -> editor.nudge(-step, 0.0)
        event.key == Key.DirectionRight -> editor.nudge(step, 0.0)
        event.key == Key.DirectionUp -> editor.nudge(0.0, -step)
        event.key == Key.DirectionDown -> editor.nudge(0.0, step)
        event.key == Key.Delete -> editor.deleteSelected()
        event.key == Key.Escape -> editor.selection = null
        event.key == Key.PageUp -> editor.moveLayer(1)
        event.key == Key.PageDown -> editor.moveLayer(-1)
        else -> return false
    }
    return true
}
