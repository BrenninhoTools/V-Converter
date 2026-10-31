package com.brenninhotools.vconverter.ios

import com.brenninhotools.vconverter.platform.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.editor.AudioRole
import com.brenninhotools.vconverter.editor.ChartBundle
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SongLoader
import com.brenninhotools.vconverter.editorview.ChartEditorState
import com.brenninhotools.vconverter.editorview.EditorChip
import com.brenninhotools.vconverter.editorview.ChartEditorWorkspace
import com.brenninhotools.vconverter.editorview.NoteSkin
import com.brenninhotools.vconverter.editorview.EditorTyping
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.io.Archives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IosEditor(private val scope: CoroutineScope) {
    val editor = ChartEditorState(ChartDocument.blank("new-song", "New Song", 120.0, listOf("easy", "normal", "hard")))
    val live = com.brenninhotools.vconverter.editorview.LiveSession(
        com.brenninhotools.vconverter.editorview.ChartLiveAdapter(editor, { audio }, { track -> useAudio(audio.filterNot { it.name.equals(track.name, true) } + track) })
    ) { block -> runOnMain(block) }
    var audio by mutableStateOf<List<EditorAudio>>(emptyList())
        private set
    var target by mutableStateOf(ExportTarget.VSLICE)
    var source by mutableStateOf("New song")
        private set
    var status by mutableStateOf<Status?>(null)
    var busy by mutableStateOf(false)
        private set
    var output by mutableStateOf<ConvertedOutput?>(null)
        private set
    var newName by mutableStateOf("")
    var newBpm by mutableStateOf("120")
    var newDifficulties by mutableStateOf(setOf("easy", "normal", "hard"))
    private var exportedRevision = -1

    val difficultyOptions = listOf("easy", "normal", "hard", "erect", "nightmare")

    init {
        useAudio(emptyList())
    }

    fun changed(revision: Int) {
        if (revision != exportedRevision) output = null
    }

    fun createNew() {
        val bpm = newBpm.trim().replace(',', '.').toDoubleOrNull()
        if (bpm == null || bpm <= 0) {
            status = Status.Error("BPM must be a number above 0.")
            return
        }
        if (newDifficulties.isEmpty()) {
            status = Status.Error("Pick at least one difficulty.")
            return
        }
        val name = newName.trim().ifEmpty { "New Song" }
        editor.load(ChartDocument.blank(Ids.of(name).ifEmpty { "new-song" }, name, bpm, difficultyOptions.filter { it in newDifficulties }))
        source = "New song"
        output = null
        status = Status.Info("Started $name at ${newBpm.trim()} BPM.")
    }

    private fun readAll(sources: List<File>): List<Pair<String, ByteArray>> {
        val wanted = setOf("json", "ogg", "mp3", "wav")
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

    fun open(sources: List<File>) {
        if (busy || sources.isEmpty()) return
        busy = true
        scope.launch {
            try {
                val song = withContext(Dispatchers.IO) { SongLoader.load(readAll(sources)) }
                editor.load(song.document)
                useAudio(song.audio)
                source = "${song.source} · ${song.document.id}"
                output = null
                status = Status.Info(
                    "Opened ${song.document.meta.songName.ifEmpty { song.document.id }} from ${song.source}: " +
                        "${song.document.difficultyNames.size} difficulties, ${song.audio.size} audio file(s)."
                )
            } catch (e: Exception) {
                status = Status.Error(e.message ?: "Could not open the chart.")
            } finally {
                busy = false
            }
        }
    }

    fun audioFor(role: AudioRole): EditorAudio? = audio.firstOrNull { ChartBundle.role(it, editor.document) == role }

    fun pickAudio(role: AudioRole) {
        Dialogs.openFile("Pick the audio", setOf("ogg", "mp3", "wav")) { file -> useAudioFile(role, file) }
    }

    private fun useAudioFile(role: AudioRole, file: File) {
        val doc = editor.document
        val sfx = if (doc.variation.isEmpty()) "" else "-${doc.variation}"
        val stem = when (role) {
            AudioRole.INST -> "Inst$sfx"
            AudioRole.PLAYER -> "Voices-${doc.meta.player}$sfx"
            AudioRole.OPPONENT -> "Voices-${doc.meta.opponent}$sfx"
            AudioRole.VOICES -> "Voices$sfx"
        }
        val track = runCatching { EditorAudio("$stem.${file.extension.lowercase()}", file.readBytes()) }.getOrElse {
            status = Status.Error(it.message ?: "Could not read ${file.name}.")
            return
        }
        useAudio(audio.filterNot { ChartBundle.role(it, doc) == role } + track)
        output = null
    }

    fun clearAudio(role: AudioRole) {
        useAudio(audio.filterNot { ChartBundle.role(it, editor.document) == role })
        output = null
    }

    private fun useAudio(tracks: List<EditorAudio>) {
        audio = tracks.sortedByDescending { ChartBundle.role(it, editor.document) == AudioRole.INST }
        if (editor.playing) editor.togglePlay()
        editor.attach(null)
        val snapshot = audio
        scope.launch {
            val (player, failures) = withContext(Dispatchers.Default) { IosSongPlayer.create(snapshot) }
            if (snapshot == audio) {
                editor.attach(player)
                if (failures.isNotEmpty()) status = Status.Error("Could not play ${failures.joinToString()}. The chart still saves fine.")
            } else {
                player.release()
            }
        }
    }

    fun export() {
        if (busy) return
        busy = true
        val document = editor.document
        val tracks = audio
        val chosen = target
        scope.launch {
            try {
                output = withContext(Dispatchers.Default) { ChartBundle.build(document, chosen, tracks) }
                exportedRevision = editor.revision
                status = null
            } catch (e: Exception) {
                output = null
                status = Status.Error(e.message ?: "Could not export the chart.")
            } finally {
                busy = false
            }
        }
    }

    fun save(zip: Boolean, onSaved: (File) -> Unit) {
        val result = output ?: return
        if (busy) return
        Dialogs.pickTarget(zip, "Save the chart", "${editor.document.id}-${this.target.name.lowercase()}.zip", "Choose the mod folder to write into") { target ->
            busy = true
            scope.launch {
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
                } finally {
                    busy = false
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(state: AppState) {
    val model = state.editor
    val editor = model.editor
    val accent = Palette.Down
    var showNew by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf<Boolean?>(null) }

    val skin by produceState<NoteSkin?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val image = Resources.image(NoteSkin.IMAGE)
                val xml = Resources.bytes(NoteSkin.ATLAS)?.decodeToString()
                if (image != null && xml != null) NoteSkin.create(image, xml) else null
            }.getOrNull()
        }
    }

    val eventIcon = remember { Resources.image(NoteSkin.EVENT_ICON) }

    LaunchedEffect(editor.revision) { model.changed(editor.revision) }
    DisposableEffect(Unit) { onDispose { if (editor.playing) editor.togglePlay() } }

    Card {
        SectionTitle("Song", accent)
        Text(model.source, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Open chart…", accent, !model.busy) { Dialogs.openFiles("Open the chart files, song folder or archive") { model.open(it) } }
            Secondary("Open folder…", !model.busy) { Dialogs.chooseFolder("Choose the song or mod folder") { model.open(listOf(it)) } }
            Secondary(if (showNew) "Hide new song" else "New song", !model.busy) { showNew = !showNew }
        }
        Text(
            "V-Slice <song>-metadata.json + <song>-chart.json, Psych chart .json files or a Codename song folder. Inst/Voices audio next to them is loaded too. You can also drop files on the window.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted
        )
        if (showNew) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(2f)) { Field(model.newName, { model.newName = it }, "Song name", accent, !model.busy) }
                Column(Modifier.weight(1f)) { Field(model.newBpm, { model.newBpm = it }, "BPM", accent, !model.busy) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                model.difficultyOptions.forEach { d ->
                    EditorChip(d, d in model.newDifficulties) {
                        model.newDifficulties = if (d in model.newDifficulties) model.newDifficulties - d else model.newDifficulties + d
                    }
                }
            }
            Primary("Create", accent, !model.busy) { model.createNew(); showNew = false }
        }
        StatusBanner(model.status)
    }

    Card {
        SectionTitle("Audio", accent)
        val isSplit = split ?: (model.audioFor(AudioRole.PLAYER) != null || model.audioFor(AudioRole.OPPONENT) != null)
        AudioRow("Instrumental", model.audioFor(AudioRole.INST), { model.pickAudio(AudioRole.INST) }, { model.clearAudio(AudioRole.INST) })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("One voices file", !isSplit) { split = false }
            EditorChip("Split voices", isSplit) { split = true }
        }
        if (isSplit) {
            AudioRow("Player voices", model.audioFor(AudioRole.PLAYER), { model.pickAudio(AudioRole.PLAYER) }, { model.clearAudio(AudioRole.PLAYER) })
            AudioRow("Opponent voices", model.audioFor(AudioRole.OPPONENT), { model.pickAudio(AudioRole.OPPONENT) }, { model.clearAudio(AudioRole.OPPONENT) })
        } else {
            AudioRow("Voices", model.audioFor(AudioRole.VOICES), { model.pickAudio(AudioRole.VOICES) }, { model.clearAudio(AudioRole.VOICES) })
        }
    }

    Card {
        SectionTitle("Chart", accent)
        ChartEditorWorkspace(editor, Modifier.fillMaxWidth(), skin, eventIcon, gridHeight = 680.dp)
        Secondary("PlayTest this chart") { state.playTestChart(editor.document, model.audio) }
    }

    Card { com.brenninhotools.vconverter.editorview.LiveSessionPanel(model.live, cursor = editor.position to editor.difficulty) }

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

@Composable
private fun AudioRow(label: String, audio: EditorAudio?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            Text(audio?.name ?: "Not picked", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Secondary(if (audio == null) "Pick…" else "Replace…", onClick = onPick)
        if (audio != null) Secondary("Remove", onClick = onClear)
    }
}

fun editorShortcut(editor: ChartEditorState, event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    if (!ctrl && EditorTyping.fields > 0) return false
    when {
        event.key == Key.Spacebar && !ctrl -> editor.togglePlay()
        ctrl && event.key == Key.Z && event.isShiftPressed -> editor.redo()
        ctrl && event.key == Key.Z -> editor.undo()
        ctrl && event.key == Key.Y -> editor.redo()
        event.key == Key.Delete || event.key == Key.Backspace -> editor.deleteSelection()
        event.key == Key.Escape -> editor.clearSelection()
        event.key == Key.PageUp -> editor.jumpMeasure(-1)
        event.key == Key.PageDown -> editor.jumpMeasure(1)
        event.key == Key.Home -> editor.seek(0.0)
        event.key == Key.Q -> editor.changeHold(-1)
        event.key == Key.E -> editor.changeHold(1)
        else -> return false
    }
    return true
}
