package com.brenninhotools.vconverter.ui.editor

import android.app.Application
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.editor.AudioRole
import com.brenninhotools.vconverter.editor.ChartBundle
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.EditorAudio
import com.brenninhotools.vconverter.editor.ExportTarget
import com.brenninhotools.vconverter.editor.SongLoader
import com.brenninhotools.vconverter.editorview.ChartEditorState
import com.brenninhotools.vconverter.editorview.DifficultyPanel
import com.brenninhotools.vconverter.editorview.EditorGrid
import com.brenninhotools.vconverter.editorview.EditorLegend
import com.brenninhotools.vconverter.editorview.EditorToolbar
import com.brenninhotools.vconverter.editorview.SelectionPanel
import com.brenninhotools.vconverter.editorview.SnapPanel
import com.brenninhotools.vconverter.editorview.SongInfoPanel
import com.brenninhotools.vconverter.editorview.TimingPanel
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.io.DocumentIO
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.StatusMessage
import com.brenninhotools.vconverter.ui.components.AssetPicker
import com.brenninhotools.vconverter.ui.components.ChoiceChips
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FieldLabel
import com.brenninhotools.vconverter.ui.components.FieldRow
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.NumberField
import com.brenninhotools.vconverter.ui.components.OutlineButton
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.theme.Funkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChartEditorViewModel(application: Application) : BaseConverterViewModel(application) {
    val editor = ChartEditorState(ChartDocument.blank("new-song", "New Song", 120.0, listOf("easy", "normal", "hard")))
    var audio by mutableStateOf<List<EditorAudio>>(emptyList())
        private set
    var target by mutableStateOf(ExportTarget.VSLICE)
    var source by mutableStateOf("New song")
        private set
    var newName by mutableStateOf("")
    var newBpm by mutableStateOf("120")
    var newDifficulties by mutableStateOf(setOf("easy", "normal", "hard"))
    private var exportedRevision = -1

    init {
        useAudio(emptyList())
    }

    val difficultyOptions = listOf("easy", "normal", "hard", "erect", "nightmare")

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

    fun createNew() {
        val bpm = newBpm.trim().replace(',', '.').toDoubleOrNull()
        if (bpm == null || bpm <= 0) {
            status = StatusMessage.Error("BPM must be a number above 0.")
            return
        }
        if (newDifficulties.isEmpty()) {
            status = StatusMessage.Error("Pick at least one difficulty.")
            return
        }
        val name = newName.trim().ifEmpty { "New Song" }
        editor.load(ChartDocument.blank(Ids.of(name).ifEmpty { "new-song" }, name, bpm, difficultyOptions.filter { it in newDifficulties }))
        source = "New song"
        invalidate()
        status = StatusMessage.Info("Started $name at ${newBpm.trim()} BPM.")
    }

    fun open(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val loaded = load(uris, { it.extension in setOf("json", "ogg", "mp3", "wav") }) { file -> file.path to file.open().use { it.readBytes() } }
                val song = withContext(Dispatchers.Default) { SongLoader.load(loaded.items) }
                editor.load(song.document)
                useAudio(song.audio)
                source = "${song.source} · ${song.document.id}"
                invalidate()
                status = StatusMessage.Info(
                    "Opened ${song.document.meta.songName.ifEmpty { song.document.id }} from ${song.source}: " +
                        "${song.document.difficultyNames.size} difficulties, ${song.audio.size} audio file(s)."
                )
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not open the chart.")
            }
        }
    }

    fun audioFor(role: AudioRole): EditorAudio? = audio.firstOrNull { ChartBundle.role(it, editor.document) == role }

    fun pickAudio(uri: Uri, role: AudioRole) {
        runTask {
            try {
                val track = withContext(Dispatchers.IO) {
                    val name = DocumentIO.displayName(context, uri)
                    val ext = name.substringAfterLast('.', "").lowercase()
                    require(ext in setOf("ogg", "mp3", "wav")) { "$name is not an .ogg, .mp3 or .wav file." }
                    EditorAudio(roleName(role) + "." + ext, DocumentIO.readBytes(context, uri))
                }
                useAudio(audio.filterNot { ChartBundle.role(it, editor.document) == role } + track)
                invalidate()
                status = null
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not read the audio.")
            }
        }
    }

    fun clearAudio(role: AudioRole) {
        useAudio(audio.filterNot { ChartBundle.role(it, editor.document) == role })
        invalidate()
    }

    private fun roleName(role: AudioRole): String {
        val doc = editor.document
        val sfx = if (doc.variation.isEmpty()) "" else "-${doc.variation}"
        return when (role) {
            AudioRole.INST -> "Inst$sfx"
            AudioRole.PLAYER -> "Voices-${doc.meta.player}$sfx"
            AudioRole.OPPONENT -> "Voices-${doc.meta.opponent}$sfx"
            AudioRole.VOICES -> "Voices$sfx"
        }
    }

    private fun useAudio(tracks: List<EditorAudio>) {
        audio = tracks.sortedByDescending { ChartBundle.role(it, editor.document) == AudioRole.INST }
        if (editor.playing) editor.togglePlay()
        editor.attach(null)
        val snapshot = audio
        viewModelScope.launch {
            val player = withContext(Dispatchers.IO) { runCatching { AndroidSongPlayer.create(context, snapshot) }.getOrNull() }
            if (player == null) {
                status = StatusMessage.Error("Could not play the audio. The chart still saves fine.")
            } else if (snapshot == audio) {
                editor.attach(player)
            } else {
                player.release()
            }
        }
    }

    fun export() {
        runTask {
            try {
                val document = editor.document
                val tracks = audio
                val chosen = target
                output = withContext(Dispatchers.Default) { ChartBundle.build(document, chosen, tracks) }
                exportedRevision = editor.revision
                download = null
                status = null
            } catch (e: Exception) {
                output = null
                status = StatusMessage.Error(e.message ?: "Could not export the chart.")
            }
        }
    }

    fun pause() {
        if (editor.playing) editor.togglePlay()
    }

    override fun onCleared() {
        editor.attach(null)
        super.onCleared()
    }
}

@Composable
fun ChartEditorScreen(viewModel: ChartEditorViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Down
    val busy = viewModel.busy
    val editor = viewModel.editor
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.open(it) }
    val audioTypes = arrayOf("audio/ogg", "application/ogg", "audio/*")
    var showNew by remember { mutableStateOf(false) }
    var splitVoices by remember { mutableIntStateOf(-1) }

    LaunchedEffect(editor.revision) { viewModel.changed(editor.revision) }
    DisposableEffect(Unit) { onDispose { viewModel.pause() } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Song", accent, 1)
            Text(viewModel.source, color = Funkin.Muted)
            DropZone(
                title = "Open a chart",
                hint = "Pick V-Slice <song>-metadata.json + <song>-chart.json, Psych chart .json files or a Codename song (meta.json + charts/*.json). Add Inst/Voices audio to hear it. Archives (.zip/.rar/.7z) work too.",
                accent = accent,
                icon = R.drawable.ic_tab_chart,
                enabled = !busy,
                onClick = { opener.launch(arrayOf("*/*")) }
            )
            OutlineButton(if (showNew) "Hide new song" else "Start a new song", accent, onClick = { showNew = !showNew })
            if (showNew) {
                FunkinTextField(viewModel.newName, { viewModel.newName = it }, "Song name", accent, enabled = !busy)
                NumberField(viewModel.newBpm, { viewModel.newBpm = it }, "BPM", accent, enabled = !busy)
                FieldLabel("Difficulties")
                ChoiceChips(
                    options = viewModel.difficultyOptions,
                    label = { it.replaceFirstChar { c -> c.uppercase() } },
                    isSelected = { it in viewModel.newDifficulties },
                    onClick = { d -> viewModel.newDifficulties = if (d in viewModel.newDifficulties) viewModel.newDifficulties - d else viewModel.newDifficulties + d },
                    accent = accent,
                    enabled = !busy
                )
                OutlineButton("Create", accent, onClick = { viewModel.createNew(); showNew = false })
            }
        }
        FunkinCard {
            SectionTitle("Audio", accent, 2)
            AssetPicker("Instrumental", viewModel.audioFor(AudioRole.INST)?.name, accent, audioTypes, { viewModel.pickAudio(it, AudioRole.INST) }, { viewModel.clearAudio(AudioRole.INST) }, enabled = !busy)
            val split = if (splitVoices >= 0) splitVoices == 1 else viewModel.audioFor(AudioRole.PLAYER) != null || viewModel.audioFor(AudioRole.OPPONENT) != null
            ChoiceChips(
                options = listOf(false, true),
                label = { if (it) "Split voices" else "One voices file" },
                isSelected = { it == split },
                onClick = { splitVoices = if (it) 1 else 0 },
                accent = accent,
                enabled = !busy
            )
            if (split) {
                AssetPicker("Player voices", viewModel.audioFor(AudioRole.PLAYER)?.name, accent, audioTypes, { viewModel.pickAudio(it, AudioRole.PLAYER) }, { viewModel.clearAudio(AudioRole.PLAYER) }, enabled = !busy)
                AssetPicker("Opponent voices", viewModel.audioFor(AudioRole.OPPONENT)?.name, accent, audioTypes, { viewModel.pickAudio(it, AudioRole.OPPONENT) }, { viewModel.clearAudio(AudioRole.OPPONENT) }, enabled = !busy)
            } else {
                AssetPicker("Voices", viewModel.audioFor(AudioRole.VOICES)?.name, accent, audioTypes, { viewModel.pickAudio(it, AudioRole.VOICES) }, { viewModel.clearAudio(AudioRole.VOICES) }, enabled = !busy)
            }
            if (editor.player == null && viewModel.audio.isNotEmpty()) Text("Loading audio…", color = Funkin.Muted)
        }
        FunkinCard {
            SectionTitle("Chart", accent, 3)
            EditorToolbar(editor)
            EditorLegend()
            EditorGrid(
                editor,
                Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Funkin.Outline, RoundedCornerShape(14.dp))
            )
            SelectionPanel(editor)
            SnapPanel(editor)
        }
        FunkinCard {
            SectionTitle("Difficulties", accent, 4)
            DifficultyPanel(editor)
        }
        FunkinCard {
            SectionTitle("Timing", accent, 5)
            TimingPanel(editor)
        }
        FunkinCard {
            SectionTitle("Song info", accent, 6)
            SongInfoPanel(editor)
        }
        FunkinCard {
            SectionTitle("Export format", accent, 7)
            ChoiceChips(
                options = ExportTarget.entries,
                label = { it.label },
                isSelected = { it == viewModel.target },
                onClick = viewModel::selectTarget,
                accent = accent,
                enabled = !busy
            )
            FieldRow {
                Text(
                    when (viewModel.target) {
                        ExportTarget.VSLICE -> "data/songs/<id>/ metadata + chart, audio in songs/<id>/."
                        ExportTarget.PSYCH -> "data/<id>/ charts + events.json, audio in songs/<id>/."
                        ExportTarget.CODENAME -> "songs/<id>/ meta.json, charts/ and song/ audio."
                    },
                    color = Funkin.Muted
                )
            }
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 8,
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
