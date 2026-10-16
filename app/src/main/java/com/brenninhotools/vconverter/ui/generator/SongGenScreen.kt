package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.generator.SongGenerator
import com.brenninhotools.vconverter.generator.SongSpec
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.AssetPicker
import com.brenninhotools.vconverter.ui.components.ChoiceChips
import com.brenninhotools.vconverter.ui.components.FieldLabel
import com.brenninhotools.vconverter.ui.components.FieldRow
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.NumberField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.ToggleRow
import com.brenninhotools.vconverter.ui.theme.Funkin

class SongGenViewModel(application: Application) : GeneratorViewModel(application) {
    var id by input("")
    var name by input("")
    var artist by input("")
    var charter by input("")
    var bpm by input("120")
    var numerator by input("4")
    var denominator by input("4")
    var speed by input("1.0")
    var difficulties by input(SongGenerator.defaultDifficulties.toSet())
    var player by input("bf")
    var opponent by input("dad")
    var girlfriend by input("gf")
    var stage by input("mainStage")
    var noteStyle by input("funkin")
    var splitVocals by input(false)
    var instrumental by input<PickedAsset?>(null)
    var voices by input<PickedAsset?>(null)
    var playerVoices by input<PickedAsset?>(null)
    var opponentVoices by input<PickedAsset?>(null)

    override fun downloadName(): String = "${Ids.of(id).ifEmpty { "song" }}-song.zip"

    fun updateName(value: String) {
        val automatic = id.isBlank() || id == Ids.of(name)
        name = value
        if (automatic) id = Ids.of(value)
    }

    fun toggleDifficulty(value: String) {
        difficulties = if (value in difficulties) difficulties - value else difficulties + value
    }

    fun pickAudio(uri: Uri, apply: (PickedAsset) -> Unit) = pick(uri, setOf("ogg"), apply)

    override fun build(): Generated = SongGenerator.generate(
        SongSpec(
            id = id,
            name = name,
            artist = artist,
            charter = charter,
            bpm = number(bpm, "BPM"),
            numerator = whole(numerator, "Beats per measure"),
            denominator = whole(denominator, "Beat value"),
            scrollSpeed = number(speed, "Scroll speed"),
            difficulties = SongGenerator.allDifficulties.filter { it in difficulties },
            player = player,
            opponent = opponent,
            girlfriend = girlfriend,
            stage = stage,
            noteStyle = noteStyle,
            instrumental = instrumental,
            voices = if (splitVocals) null else voices,
            playerVoices = if (splitVocals) playerVoices else null,
            opponentVoices = if (splitVocals) opponentVoices else null
        )
    )
}

@Composable
fun SongGenScreen(viewModel: SongGenViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Down
    val busy = viewModel.busy
    val audio = arrayOf("audio/ogg", "application/ogg", "audio/*")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Song", accent, 1)
            FunkinTextField(viewModel.name, viewModel::updateName, "Song name", accent, enabled = !busy)
            FunkinTextField(viewModel.id, { viewModel.id = it }, "Song id (folder name)", accent, enabled = !busy)
            FieldRow {
                FunkinTextField(viewModel.artist, { viewModel.artist = it }, "Artist", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.charter, { viewModel.charter = it }, "Charter", accent, Modifier.weight(1f), enabled = !busy)
            }
            FieldRow {
                NumberField(viewModel.bpm, { viewModel.bpm = it }, "BPM", accent, Modifier.weight(1f), enabled = !busy)
                NumberField(viewModel.speed, { viewModel.speed = it }, "Scroll speed", accent, Modifier.weight(1f), enabled = !busy)
            }
            FieldRow {
                NumberField(viewModel.numerator, { viewModel.numerator = it }, "Beats / measure", accent, Modifier.weight(1f), enabled = !busy, decimal = false)
                NumberField(viewModel.denominator, { viewModel.denominator = it }, "Beat value", accent, Modifier.weight(1f), enabled = !busy, decimal = false)
            }
            FieldLabel("Difficulties")
            ChoiceChips(
                options = SongGenerator.allDifficulties,
                label = { it.replaceFirstChar { c -> c.uppercase() } },
                isSelected = { it in viewModel.difficulties },
                onClick = viewModel::toggleDifficulty,
                accent = accent,
                enabled = !busy
            )
        }
        FunkinCard {
            SectionTitle("Gameplay", accent, 2)
            FieldRow {
                FunkinTextField(viewModel.player, { viewModel.player = it }, "Player", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.opponent, { viewModel.opponent = it }, "Opponent", accent, Modifier.weight(1f), enabled = !busy)
            }
            FieldRow {
                FunkinTextField(viewModel.girlfriend, { viewModel.girlfriend = it }, "Girlfriend", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.stage, { viewModel.stage = it }, "Stage", accent, Modifier.weight(1f), enabled = !busy)
            }
            FieldLabel("Note style")
            ChoiceChips(
                options = SongGenerator.noteStyles,
                label = { it.replaceFirstChar { c -> c.uppercase() } },
                isSelected = { it == viewModel.noteStyle },
                onClick = { viewModel.noteStyle = it },
                accent = accent,
                enabled = !busy
            )
        }
        FunkinCard {
            SectionTitle("Audio", accent, 3)
            AssetPicker("Instrumental (.ogg)", viewModel.instrumental?.name, accent, audio, { uri -> viewModel.pickAudio(uri) { viewModel.instrumental = it } }, { viewModel.instrumental = null }, enabled = !busy)
            ToggleRow("Split vocals", viewModel.splitVocals, { viewModel.splitVocals = it }, accent, "One voices file per character.", enabled = !busy)
            if (viewModel.splitVocals) {
                AssetPicker("Player voices (.ogg)", viewModel.playerVoices?.name, accent, audio, { uri -> viewModel.pickAudio(uri) { viewModel.playerVoices = it } }, { viewModel.playerVoices = null }, enabled = !busy)
                AssetPicker("Opponent voices (.ogg)", viewModel.opponentVoices?.name, accent, audio, { uri -> viewModel.pickAudio(uri) { viewModel.opponentVoices = it } }, { viewModel.opponentVoices = null }, enabled = !busy)
            } else {
                AssetPicker("Voices (.ogg)", viewModel.voices?.name, accent, audio, { uri -> viewModel.pickAudio(uri) { viewModel.voices = it } }, { viewModel.voices = null }, enabled = !busy)
            }
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 4,
            canConvert = viewModel.id.isNotBlank() && viewModel.difficulties.isNotEmpty(),
            onConvert = viewModel::generate,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Generate",
            buttonText = "Generate song",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
