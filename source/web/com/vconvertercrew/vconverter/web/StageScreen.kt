package com.vconvertercrew.vconverter.web

import com.vconvertercrew.vconverterui.VButton as Primary
import com.vconvertercrew.vconverter.overlay.ReportView
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import com.vconvertercrew.vconverter.editorview.*
import com.vconvertercrew.vconverter.platform.File
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
import com.vconvertercrew.vconverter.convert.ConvertedOutput
import com.vconvertercrew.vconverter.editor.ExportTarget
import com.vconvertercrew.vconverter.editor.SpriteAtlas
import com.vconvertercrew.vconverter.editor.StageAsset
import com.vconvertercrew.vconverter.editor.StageDocument
import com.vconvertercrew.vconverter.editor.StageExport
import com.vconvertercrew.vconverter.editor.StageLoader
import com.vconvertercrew.vconverter.editor.StageProp
import com.vconvertercrew.vconverter.editorview.EditorChip
import com.vconvertercrew.vconverter.editorview.EditorTyping
import com.vconvertercrew.vconverter.editorview.StageEditorState
import com.vconvertercrew.vconverter.editorview.StageImage
import com.vconvertercrew.vconverter.editorview.StageInfoPanel
import com.vconvertercrew.vconverter.editorview.StageLayersPanel
import com.vconvertercrew.vconverter.editorview.StagePreview
import com.vconvertercrew.vconverter.editorview.StageSelectionPanel
import com.vconvertercrew.vconverter.editorview.StageToolbar
import com.vconvertercrew.vconverter.generator.Ids
import com.vconvertercrew.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vconvertercrew.vconverter.core.roundLong

class IosStageEditor(private val scope: CoroutineScope) {
    val editor = StageEditorState(StageDocument("new-stage", "New Stage"))
    val live = com.vconvertercrew.vconverter.editorview.LiveSession(com.vconvertercrew.vconverter.editorview.StageLiveAdapter(editor) { asset -> decode(asset) }) { block -> runOnMain(block) }
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
                val loaded = withContext(Dispatchers.Default) { StageLoader.load(readAll(sources)) }
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
                val files = withContext(Dispatchers.Default) { readAll(sources) }
                val pngs = files.filter { it.first.endsWith(".png", true) }
                require(pngs.isNotEmpty()) { "Pick at least one .png (with its .xml for animated props)." }
                val xmls = files.filter { it.first.endsWith(".xml", true) }.associate { it.first.substringAfterLast('/').substringBeforeLast('.').lowercase() to it.second.decodeToString() }
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
                    editor.addProp(StageProp(Ids.of(stem).ifEmpty { "prop" }, assetPath, (editor.camX - w / 2.0).roundLong().toDouble(), (editor.camY - h / 2.0).roundLong().toDouble(), prefix = prefix))
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
        val vslice = target == ExportTarget.VSLICE
        val result = output ?: return
        Dialogs.pickTarget(zip, "Save the stage", "${editor.document.id}-${this.target.name.lowercase()}.zip", "Choose the mod folder to write into") { target ->
            task {
                try {
                    withContext(Dispatchers.Default) {
                        if (zip) Workspace.writeZip(target, "", vslice) { writer -> result.files.forEach { it.writeTo(writer) } }
                        else Workspace.writeFolder(target, vslice) { writer -> result.files.forEach { it.writeTo(writer) } }
                    }
                    status = Status.Success("Saved ${result.files.size} file(s) to ${target.absolutePath}")
                    if (zip) Dialogs.export(target)
                    onSaved(target)
                } catch (e: Exception) {
                    status = Status.Error(e.message ?: "Could not save the files.")
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StageEditorScreen(state: AppState) {
    val model = state.stageEditor
    val editor = model.editor
    val accent = Palette.Up

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }

    val output = model.output
    val windows = rememberEditorWindows("layers", "selection")
    val menus = listOf(
        EditorMenu(
            "File",
            listOf(
                EditorMenuItem.Action("New Stage", enabled = !model.busy) { model.createNew() },
                EditorMenuItem.Action("Open Stage…", enabled = !model.busy) { Dialogs.openFiles("Open the stage files and images, or an archive") { model.open(it) } },
                EditorMenuItem.Action("Open Folder…", enabled = !model.busy) { Dialogs.chooseFolder("Choose the mod folder with the stage") { model.open(listOf(it)) } },
                EditorMenuItem.Action("Add Images…", enabled = !model.busy) { Dialogs.openFiles("Pick the images (.png, with .xml for animated ones)") { model.addImages(it) } },
                EditorMenuItem.Separator,
                EditorMenuItem.Action("Export ${model.target.label}", enabled = !model.busy) {
                    model.export()
                    windows.show("export")
                },
                EditorMenuItem.Action("Export Settings…") { windows.show("export") },
                EditorMenuItem.Action("Download .zip…", enabled = output != null && !model.busy) { model.save(true) { state.lastOutput = it } },
                EditorMenuItem.Separator,
                EditorMenuItem.Action("Exit To Main Menu") { MainMenu.open = true }
            )
        ),
        StageMenus.edit(editor),
        StageMenus.view(editor),
        EditorMenu(
            "Help",
            listOf(
                EditorMenuItem.Action("Controls…") { windows.show("help") },
                EditorMenuItem.Action("Stage Info…") { windows.show("info") }
            )
        )
    )
    val panels = listOf(
        EditorWindow("info", "Stage") {
            Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Text("A V-Slice or Psych stage .json (with its .lua) or a Codename stage .xml, plus the images. Opening the whole mod folder finds every image the stage uses.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        },
        EditorWindow("layers", "Props") { StageLayersPanel(editor, onAddImage = { Dialogs.openFiles("Pick the images (.png, with .xml for animated ones)") { model.addImages(it) } }) },
        EditorWindow("selection", "Selection") { StageSelectionPanel(editor) },
        EditorWindow("properties", "Stage Data") { StageInfoPanel(editor) },
        EditorWindow("live", "Multieditor") { com.vconvertercrew.vconverter.editorview.LiveSessionPanel(model.live) },
        EditorWindow("export", "Export", 420.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportTarget.entries.forEach { t -> EditorChip(t.label, t == model.target) { if (t != model.target) { model.target = t; model.changed(-2) } } }
            }
            Primary("Export ${model.target.label}", accent, !model.busy) { model.export() }
            if (output != null) {
                Primary("Download .zip…", accent, !model.busy) { model.save(true) { state.lastOutput = it } }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    output.files.forEach { file -> Text(file.relativePath, style = MaterialTheme.typography.bodySmall, color = Palette.Text) }
                }
                ReportView(output.report, accent)
            }
        },
        EditorWindow("help", "Controls") { EditorHelpText(EditorTool.STAGE.hint) }
    )
    FunkinEditorShell(
        "Stage Editor", accent, menus, windows, panels, Modifier.fillMaxSize(),
        footer = {
            val status = model.status
            if (status != null) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).widthIn(max = 560.dp)) { StatusBanner(status) }
        }
    ) { StageEditorStage(editor) }
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
