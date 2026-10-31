package com.brenninhotools.vconverter.ios

import com.brenninhotools.vconverter.platform.File
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
import com.brenninhotools.vconverter.editorview.EditorChip
import com.brenninhotools.vconverter.editorview.EditorTyping
import com.brenninhotools.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IosCharacterEditor(private val scope: CoroutineScope) {
    val editor = CharacterEditorState(CharacterDocument("new-character", "New Character"))
    val live = com.brenninhotools.vconverter.editorview.LiveSession(
        com.brenninhotools.vconverter.editorview.CharacterLiveAdapter(editor, { image to xml }, { png, text -> receiveSheet(png, text) })
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
        val result = output ?: return
        Dialogs.pickTarget(zip, "Save the character", "${editor.document.id}-${this.target.name.lowercase()}.zip", "Choose the mod folder to write into") { target ->
            task {
                try {
                    withContext(Dispatchers.IO) {
                        if (zip) Workspace.writeZip(target, "") { writer -> result.files.forEach { it.writeTo(writer) } }
                        else Workspace.writeFolder(target) { writer -> result.files.forEach { it.writeTo(writer) } }
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

@Composable
fun CharacterEditorScreen(state: AppState) {
    val model = state.characterEditor
    val editor = model.editor
    val accent = Palette.Left

    val stage = androidx.compose.runtime.remember { Resources.image("images/ui/character/stage.png") }

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }

    Card {
        SectionTitle("Character", accent)
        Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Open character…", accent, !model.busy) { Dialogs.openFiles("Open the character files, spritesheet or archive") { model.open(it) } }
            Secondary("Open folder…", !model.busy) { Dialogs.chooseFolder("Choose the character or mod folder") { model.open(listOf(it)) } }
            Secondary(model.sheetName?.let { "Spritesheet: $it" } ?: "Add spritesheet…", !model.busy) { Dialogs.openFiles("Pick the spritesheet .png and .xml") { model.replaceSheet(it) } }
            Secondary("New character", !model.busy) { model.createNew() }
        }
        Text(
            "A V-Slice or Psych character .json or a Codename character .xml with its spritesheet .png + .xml. A spritesheet alone starts a new character with guessed animations. You can also drop files on the window.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted
        )
        StatusBanner(model.status)
    }

    AdaptiveRow {
        Column(Modifier.pane(1.3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                SectionTitle("Preview", accent)
                CharacterToolbar(editor)
                DragModeBar(editor)
                CharacterPreview(
                    editor,
                    Modifier.fillMaxWidth().height(620.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Outline, RoundedCornerShape(12.dp)),
                    stage
                )
            }
        }
        Column(Modifier.pane(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card { AnimationListPanel(editor) }
            Card { AnimationPanel(editor) }
        }
    }

    Card { CharacterInfoPanel(editor) }

    Card { com.brenninhotools.vconverter.editorview.LiveSessionPanel(model.live) }

    val output = model.output
    Card(accent = if (output != null) accent else null) {
        SectionTitle("Export", accent)
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
    }
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
