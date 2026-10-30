package com.brenninhotools.vconverter.desktop

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
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SpriteAtlas
import com.brenninhotools.vconverter.editor.StageAsset
import com.brenninhotools.vconverter.editor.WeekDocument
import com.brenninhotools.vconverter.editor.WeekExport
import com.brenninhotools.vconverter.editor.WeekLoader
import com.brenninhotools.vconverter.editor.WeekProp
import com.brenninhotools.vconverter.editor.WeekSlot
import com.brenninhotools.vconverter.editorview.EditorChip
import com.brenninhotools.vconverter.editorview.EditorTyping
import com.brenninhotools.vconverter.editorview.StageImage
import com.brenninhotools.vconverter.editorview.WeekEditorState
import com.brenninhotools.vconverter.editorview.WeekInfoPanel
import com.brenninhotools.vconverter.editorview.WeekPreview
import com.brenninhotools.vconverter.editorview.WeekPropsPanel
import com.brenninhotools.vconverter.editorview.WeekSongsPanel
import com.brenninhotools.vconverter.editorview.WeekToolbar
import com.brenninhotools.vconverter.editorview.weekSummary
import com.brenninhotools.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WeekImageRole { PROP, TITLE, BACKGROUND }

class DesktopWeekEditor(private val scope: CoroutineScope) {
    val editor = WeekEditorState(WeekDocument("new-week", "New Week"))
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
        val result = output ?: return
        val target = if (zip) Dialogs.saveFile("Save the week", "${editor.document.id}-${this.target.name.lowercase()}.zip") ?: return
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
fun WeekEditorScreen(state: AppState) {
    val model = state.weekEditor
    val editor = model.editor
    val accent = Palette.Right

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }

    Card {
        SectionTitle("Week", accent)
        Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Open week…", accent, !model.busy) { model.open(Dialogs.openFiles("Open the week files and images, or an archive")) }
            Secondary("Open folder…", !model.busy) { Dialogs.chooseFolder("Choose the mod folder with the week")?.let { model.open(listOf(it)) } }
            Secondary("New week", !model.busy) { model.createNew() }
        }
        Text(
            "A V-Slice level .json, a Psych week .json (with images/menucharacters/*.json) or a Codename week .xml (with data/weeks/characters/*.xml), plus the title and menu character images. Opening the whole mod folder also finds the song names. You can drop files on the window too.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted
        )
        StatusBanner(model.status)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                SectionTitle("Story menu", accent)
                WeekToolbar(editor)
                WeekPreview(
                    editor,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
                )
            }
            Card { WeekPropsPanel(editor, onLoadImage = { slot -> model.addImage(WeekImageRole.PROP, slot, Dialogs.openFiles("Pick the character image (.png and its .xml)")) }) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card { WeekSongsPanel(editor) }
            Card {
                WeekInfoPanel(editor)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Secondary("Title image…", !model.busy) { model.addImage(WeekImageRole.TITLE, null, Dialogs.openFiles("Pick the title image (.png)")) }
                    Secondary("Background image…", !model.busy) { model.addImage(WeekImageRole.BACKGROUND, null, Dialogs.openFiles("Pick the background image (.png)")) }
                }
            }
        }
    }

    val output = model.output
    Card(accent = if (output != null) accent else null) {
        SectionTitle("Export", accent)
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
    }
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
