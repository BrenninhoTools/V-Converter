package com.vconvertercrew.vconverter.ios

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
import com.vconvertercrew.vconverter.editor.CharacterDocument
import com.vconvertercrew.vconverter.editor.CharacterExport
import com.vconvertercrew.vconverter.editor.CharacterLoader
import com.vconvertercrew.vconverter.editor.ExportTarget
import com.vconvertercrew.vconverter.editor.SpriteAtlas
import com.vconvertercrew.vconverter.editorview.AnimationListPanel
import com.vconvertercrew.vconverter.editorview.AnimationPanel
import com.vconvertercrew.vconverter.editorview.CharacterEditorState
import com.vconvertercrew.vconverter.editorview.CharacterInfoPanel
import com.vconvertercrew.vconverter.editorview.CharacterPreview
import com.vconvertercrew.vconverter.editorview.CharacterSprite
import com.vconvertercrew.vconverter.editorview.CharacterToolbar
import com.vconvertercrew.vconverter.editorview.DragModeBar
import com.vconvertercrew.vconverter.editorview.EditorChip
import com.vconvertercrew.vconverter.editorview.EditorTyping
import com.vconvertercrew.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IosCharacterEditor(private val scope: CoroutineScope) {
    val editor = CharacterEditorState(CharacterDocument("new-character", "New Character"))
    val live = com.vconvertercrew.vconverter.editorview.LiveSession(
        com.vconvertercrew.vconverter.editorview.CharacterLiveAdapter(editor, { image to xml }, { png, text -> receiveSheet(png, text) })
    ) { block -> runOnMain(block) }

    private fun receiveSheet(png: ByteArray?, text: String?) {
        if (png != null) image = png
        if (text != null) xml = text
        val i = image
        val x = xml
        if (i != null && x != null) runCatching { editor.sprite = decode(i, x) }
    }
    var target by mutableStateOf(ExportTarget.VSLICE)
    var source by mutableStateOf("New character")
        private set
    var sheetName by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<Status?>(null)
    var busy by mutableStateOf(false)
        private set
    var output by mutableStateOf<ConvertedOutput?>(null)
        private set
    private var image: ByteArray? = null
    private var xml: String? = null
    private var exportedRevision = -1

    fun changed(revision: Int) {
        if (revision != exportedRevision) output = null
    }

    private fun readAll(sources: List<File>): List<Pair<String, ByteArray>> {
        val wanted = setOf("json", "xml", "png")
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

    private fun decode(bytes: ByteArray, text: String): CharacterSprite =
        CharacterSprite(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap(), SpriteAtlas.parse(text))

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
                val loaded = withContext(Dispatchers.IO) { CharacterLoader.load(readAll(sources)) }
                val sprite = withContext(Dispatchers.Default) {
                    val png = loaded.image
                    val sheet = loaded.xml
                    if (png != null && sheet != null) decode(png, sheet) else null
                }
                editor.load(loaded.document)
                editor.sprite = sprite
                image = loaded.image
                xml = loaded.xml
                sheetName = loaded.imageName
                source = "${loaded.source} · ${loaded.document.id}"
                output = null
                status = Status.Info(
                    "Opened ${loaded.document.id} from ${loaded.source}: ${loaded.document.animations.size} animations" +
                        if (sprite == null) ". Add the spritesheet (.png + .xml) to see it." else "."
                )
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not open the character.")
            }
        }
    }

    fun replaceSheet(sources: List<File>) {
        if (sources.isEmpty()) return
        task {
            try {
                val files = withContext(Dispatchers.IO) { readAll(sources) }
                val png = files.firstOrNull { it.first.endsWith(".png", true) } ?: throw IllegalArgumentException("Pick the spritesheet .png and its .xml.")
                val sheet = files.firstOrNull { it.first.endsWith(".xml", true) } ?: throw IllegalArgumentException("Pick the spritesheet .xml too.")
                val text = sheet.second.decodeToString()
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
                output = null
                status = Status.Info("Loaded ${sprite.atlas.frames.size} frames from $sheetName.")
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not read the spritesheet.")
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
        output = null
        status = Status.Info("Started a new character. Add its spritesheet to pick the animations.")
    }

    fun export() {
        val document = editor.document
        val chosen = target
        task {
            try {
                output = withContext(Dispatchers.Default) { CharacterExport.build(document, chosen, image, xml) }
                exportedRevision = editor.revision
                status = null
            } catch (e: Exception) {
                output = null
                status = Status.Error(e.message ?: "Could not export the character.")
            }
        }
    }

    fun save(zip: Boolean, onSaved: (File) -> Unit) {
        val vslice = target == ExportTarget.VSLICE
        val result = output ?: return
        Dialogs.pickTarget(zip, "Save the character", "${editor.document.id}-${this.target.name.lowercase()}.zip", "Choose the mod folder to write into") { target ->
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
fun CharacterEditorScreen(state: AppState) {
    val model = state.characterEditor
    val editor = model.editor
    val accent = Palette.Left

    val stage = androidx.compose.runtime.remember { Resources.image("images/ui/character/stage.png") }

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }

    val output = model.output
    val windows = rememberEditorWindows("animations", "animation")
    val menus = listOf(
        EditorMenu(
            "File",
            listOf(
                EditorMenuItem.Action("New Character", enabled = !model.busy) { model.createNew() },
                EditorMenuItem.Action("Open Character…", enabled = !model.busy) { Dialogs.openFiles("Open the character files, spritesheet or archive") { model.open(it) } },
                EditorMenuItem.Action("Open Folder…", enabled = !model.busy) { Dialogs.chooseFolder("Choose the character or mod folder") { model.open(listOf(it)) } },
                EditorMenuItem.Action(model.sheetName?.let { "Replace Spritesheet ($it)…" } ?: "Load Spritesheet…", enabled = !model.busy) { Dialogs.openFiles("Pick the spritesheet .png and .xml") { model.replaceSheet(it) } },
                EditorMenuItem.Separator,
                EditorMenuItem.Action("Export ${model.target.label}", enabled = !model.busy && editor.document.animations.isNotEmpty()) {
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
        CharacterMenus.edit(editor),
        CharacterMenus.view(editor),
        EditorMenu(
            "Help",
            listOf(
                EditorMenuItem.Action("Controls…") { windows.show("help") },
                EditorMenuItem.Action("Character Info…") { windows.show("info") }
            )
        )
    )
    val panels = listOf(
        EditorWindow("info", "Character") {
            Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Text("A V-Slice or Psych character .json or a Codename character .xml with its spritesheet .png + .xml. A spritesheet alone starts a new character with guessed animations.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        },
        EditorWindow("animations", "Animations") { AnimationListPanel(editor) },
        EditorWindow("animation", "Animation Data") { AnimationPanel(editor) },
        EditorWindow("properties", "Character Data") { CharacterInfoPanel(editor) },
        EditorWindow("live", "Multieditor") { com.vconvertercrew.vconverter.editorview.LiveSessionPanel(model.live) },
        EditorWindow("export", "Export", 420.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportTarget.entries.forEach { t -> EditorChip(t.label, t == model.target) { if (t != model.target) { model.target = t; model.changed(-2) } } }
            }
            Primary("Export ${model.target.label}", accent, !model.busy && editor.document.animations.isNotEmpty()) { model.export() }
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
        EditorWindow("help", "Controls") { EditorHelpText(EditorTool.CHARACTER.hint) }
    )
    FunkinEditorShell(
        "Character Editor", accent, menus, windows, panels, Modifier.fillMaxSize(),
        footer = {
            val status = model.status
            if (status != null) Box(Modifier.align(Alignment.BottomStart).padding(10.dp).widthIn(max = 560.dp)) { StatusBanner(status) }
        }
    ) { CharacterEditorStage(editor, stage) }
}

fun characterShortcut(editor: CharacterEditorState, event: KeyEvent): Boolean {
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    if (!ctrl && EditorTyping.fields > 0) return false
    val step = if (event.isShiftPressed) 10.0 else 1.0
    when {
        event.key == Key.Spacebar && !ctrl -> editor.togglePlay()
        ctrl && event.key == Key.Z && event.isShiftPressed -> editor.redo()
        ctrl && event.key == Key.Z -> editor.undo()
        ctrl && event.key == Key.Y -> editor.redo()
        event.key == Key.DirectionLeft -> editor.nudgeOffset(step, 0.0)
        event.key == Key.DirectionRight -> editor.nudgeOffset(-step, 0.0)
        event.key == Key.DirectionUp -> editor.nudgeOffset(0.0, step)
        event.key == Key.DirectionDown -> editor.nudgeOffset(0.0, -step)
        event.key == Key.W -> editor.select(editor.selected - 1)
        event.key == Key.S -> editor.select(editor.selected + 1)
        event.key == Key.Comma -> editor.step(-1)
        event.key == Key.Period -> editor.step(1)
        event.key == Key.G -> editor.ghost = !editor.ghost
        else -> return false
    }
    return true
}
