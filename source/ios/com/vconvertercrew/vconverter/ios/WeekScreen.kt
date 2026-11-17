package com.vconvertercrew.vconverter.ios

import androidx.compose.foundation.layout.*
import com.vconvertercrew.vconverter.editorview.*
import com.vconvertercrew.vconverter.platform.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.vconvertercrew.vconverter.editor.WeekDocument
import com.vconvertercrew.vconverter.editor.WeekExport
import com.vconvertercrew.vconverter.editor.WeekLoader
import com.vconvertercrew.vconverter.editor.WeekProp
import com.vconvertercrew.vconverter.editor.WeekSlot
import com.vconvertercrew.vconverter.editorview.EditorChip
import com.vconvertercrew.vconverter.editorview.EditorTyping
import com.vconvertercrew.vconverter.editorview.StageImage
import com.vconvertercrew.vconverter.editorview.WeekEditorState
import com.vconvertercrew.vconverter.editorview.WeekInfoPanel
import com.vconvertercrew.vconverter.editorview.WeekPreview
import com.vconvertercrew.vconverter.editorview.WeekPropsPanel
import com.vconvertercrew.vconverter.editorview.WeekSongsPanel
import com.vconvertercrew.vconverter.editorview.WeekToolbar
import com.vconvertercrew.vconverter.editorview.weekSummary
import com.vconvertercrew.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WeekImageRole { PROP, TITLE, BACKGROUND }

class IosWeekEditor(private val scope: CoroutineScope) {
    val editor = WeekEditorState(WeekDocument("new-week", "New Week"))
    val live = com.vconvertercrew.vconverter.editorview.LiveSession(com.vconvertercrew.vconverter.editorview.WeekLiveAdapter(editor) { asset -> decode(asset) }) { block -> runOnMain(block) }
    var target by mutableStateOf(ExportTarget.VSLICE)
    var source by mutableStateOf("New week")
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
        val wanted = setOf("json", "xml", "png", "hxc")
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
                val loaded = withContext(Dispatchers.IO) { WeekLoader.load(readAll(sources)) }
                val decoded = withContext(Dispatchers.Default) { loaded.assets.mapNotNull { (key, asset) -> decode(asset)?.let { key to it } }.toMap() }
                editor.load(loaded.document, loaded.assets, decoded)
                source = "${loaded.source} · ${loaded.document.id}"
                output = null
                status = Status.Info("Opened ${loaded.document.id} from ${loaded.source}: ${weekSummary(loaded.document)}, ${decoded.size} image(s).")
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not open the week.")
            }
        }
    }

    fun addImage(role: WeekImageRole, slot: WeekSlot?, sources: List<File>) {
        if (sources.isEmpty()) return
        task {
            try {
                val files = withContext(Dispatchers.IO) { readAll(sources) }
                val png = files.firstOrNull { it.first.endsWith(".png", true) } ?: throw IllegalArgumentException("Pick a .png (with its .xml for an animated character).")
                val xml = files.firstOrNull { it.first.endsWith(".xml", true) }?.second?.decodeToString()?.takeIf { SpriteAtlas.parse(it).frames.isNotEmpty() }
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
                        editor.edit { it.withProp(target, WeekProp(path, offsetX = old?.offsetX ?: 0.0, offsetY = old?.offsetY ?: 0.0).withPrefix("idle", idle).withPrefix("confirm", confirm)) }
                        editor.selected = target
                    }
                }
                output = null
                status = null
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not load the image.")
            }
        }
    }

    fun createNew() {
        editor.load(WeekDocument("new-week", "New Week"), emptyMap(), emptyMap())
        source = "New week"
        output = null
        status = Status.Info("Started a new week with the base game menu characters. Add its songs and title image.")
    }

    fun export() {
        val document = editor.document
        val assets = editor.assets
        val chosen = target
        task {
            try {
                output = withContext(Dispatchers.Default) { WeekExport.build(document, chosen, assets) }
                exportedRevision = editor.revision
                status = null
            } catch (e: Exception) {
                output = null
                status = Status.Error(e.message ?: "Could not export the week.")
            }
        }
    }

    fun save(zip: Boolean, onSaved: (File) -> Unit) {
        val vslice = target == ExportTarget.VSLICE
        val result = output ?: return
        Dialogs.pickTarget(zip, "Save the week", "${editor.document.id}-${this.target.name.lowercase()}.zip", "Choose the mod folder to write into") { target ->
            task {
                try {
                    withContext(Dispatchers.IO) {
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
fun WeekEditorScreen(state: AppState) {
    val model = state.weekEditor
    val editor = model.editor
    val accent = Palette.Right

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }

    val output = model.output
    val windows = rememberEditorWindows("songs", "properties")
    val menus = listOf(
        EditorMenu(
            "File",
            listOf(
                EditorMenuItem.Action("New Week", enabled = !model.busy) { model.createNew() },
                EditorMenuItem.Action("Open Week…", enabled = !model.busy) { Dialogs.openFiles("Open the week files and images, or an archive") { model.open(it) } },
                EditorMenuItem.Action("Open Folder…", enabled = !model.busy) { Dialogs.chooseFolder("Choose the mod folder with the week") { model.open(listOf(it)) } },
                EditorMenuItem.Action("Title Image…", enabled = !model.busy) { Dialogs.openFiles("Pick the title image (.png)") { model.addImage(WeekImageRole.TITLE, null, it) } },
                EditorMenuItem.Action("Background Image…", enabled = !model.busy) { Dialogs.openFiles("Pick the background image (.png)") { model.addImage(WeekImageRole.BACKGROUND, null, it) } },
                EditorMenuItem.Separator,
                EditorMenuItem.Action("Export ${model.target.label}", enabled = !model.busy && editor.document.songs.isNotEmpty()) {
                    model.export()
                    windows.show("export")
                },
                EditorMenuItem.Action("Export Settings…") { windows.show("export") },
                EditorMenuItem.Action("Save Into Mod Folder…", enabled = output != null && !model.busy) { model.save(false) { state.lastOutput = it } },
                EditorMenuItem.Action("Save As .zip…", enabled = output != null && !model.busy) { model.save(true) { state.lastOutput = it } },
                EditorMenuItem.Separator,
                EditorMenuItem.Action("Exit To Main Menu") { MainMenu.open = true }
            )
        ),
        WeekMenus.edit(editor),
        WeekMenus.view(editor),
        EditorMenu(
            "Help",
            listOf(
                EditorMenuItem.Action("Controls…") { windows.show("help") },
                EditorMenuItem.Action("Week Info…") { windows.show("info") }
            )
        )
    )
    val panels = listOf(
        EditorWindow("info", "Week") {
            Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Text("A V-Slice level .json, a Psych week .json (with images/menucharacters/*.json) or a Codename week .xml (with data/weeks/characters/*.xml), plus the title and menu character images. Opening the whole mod folder also finds the song names.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        },
        EditorWindow("songs", "Songs") { WeekSongsPanel(editor) },
        EditorWindow("properties", "Week Data") {
            WeekInfoPanel(editor)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Secondary("Title image…", !model.busy) { Dialogs.openFiles("Pick the title image (.png)") { model.addImage(WeekImageRole.TITLE, null, it) } }
                Secondary("Background image…", !model.busy) { Dialogs.openFiles("Pick the background image (.png)") { model.addImage(WeekImageRole.BACKGROUND, null, it) } }
            }
        },
        EditorWindow("props", "Menu Characters", 400.dp) { WeekPropsPanel(editor, onLoadImage = { slot -> Dialogs.openFiles("Pick the character image (.png and its .xml)") { model.addImage(WeekImageRole.PROP, slot, it) } }) },
        EditorWindow("live", "Multieditor") { com.vconvertercrew.vconverter.editorview.LiveSessionPanel(model.live) },
        EditorWindow("export", "Export", 420.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportTarget.entries.forEach { t -> EditorChip(t.label, t == model.target) { if (t != model.target) { model.target = t; model.changed(-2) } } }
            }
            Primary("Export ${model.target.label}", accent, !model.busy && editor.document.songs.isNotEmpty()) { model.export() }
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
        },
        EditorWindow("help", "Controls") { EditorHelpText(EditorTool.WEEK.hint) }
    )
    FunkinEditorShell(
        "Week Editor", accent, menus, windows, panels, Modifier.fillMaxSize(),
        footer = {
            val status = model.status
            if (status != null) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).widthIn(max = 560.dp)) { StatusBanner(status) }
        }
    ) { WeekEditorStage(editor) }
}

fun weekShortcut(editor: WeekEditorState, event: KeyEvent): Boolean {
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    if (!ctrl && EditorTyping.fields > 0) return false
    val step = if (event.isShiftPressed) 10.0 else 1.0
    val moving = editor.selected != null
    when {
        ctrl && event.key == Key.Z && event.isShiftPressed -> editor.redo()
        ctrl && event.key == Key.Z -> editor.undo()
        ctrl && event.key == Key.Y -> editor.redo()
        moving && event.key == Key.DirectionLeft -> editor.nudge(-step, 0.0)
        moving && event.key == Key.DirectionRight -> editor.nudge(step, 0.0)
        moving && event.key == Key.DirectionUp -> editor.nudge(0.0, -step)
        moving && event.key == Key.DirectionDown -> editor.nudge(0.0, step)
        event.key == Key.DirectionLeft -> editor.changeDifficulty(-1)
        event.key == Key.DirectionRight -> editor.changeDifficulty(1)
        event.key == Key.Enter -> editor.confirm()
        event.key == Key.Spacebar -> editor.playing = !editor.playing
        event.key == Key.Escape -> editor.selected = null
        else -> return false
    }
    return true
}
