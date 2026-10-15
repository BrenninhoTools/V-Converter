package com.brenninhotools.vconverter.ui.charts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FileItem
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.theme.Funkin

@Composable
fun ChartsScreen(viewModel: ChartsViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Down
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val busy = viewModel.busy

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Charts", accent, 1)
            DropZone(
                title = "Add Psych charts",
                hint = "Pick every difficulty of one song (song-easy.json, song.json, song-hard.json…). Include events.json if the song has one. A .zip/.rar/.7z works too.",
                accent = accent,
                icon = R.drawable.ic_tab_chart,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            viewModel.charts.forEachIndexed { index, chart ->
                FileItem(
                    title = chart.fileName,
                    accent = accent,
                    enabled = !busy,
                    onRemove = { viewModel.remove(index) },
                    tags = {
                        Tag(if (chart.song.usesAbsoluteLanes) "Psych 1.0" else "Legacy", accent)
                        Tag("${formatBpm(chart.song.bpm)} BPM", Funkin.Muted)
                        Tag("${chart.noteCount} notes", Funkin.Muted)
                        Tag("Speed ${formatBpm(chart.song.speed)}", Funkin.Muted)
                    }
                ) {
                    FunkinTextField(
                        value = chart.difficulty,
                        onValueChange = { viewModel.setDifficulty(index, it) },
                        label = "Difficulty",
                        accent = accent,
                        enabled = !busy
                    )
                }
            }
            viewModel.events?.let { events ->
                FileItem(
                    title = events.fileName,
                    accent = Funkin.Warning,
                    enabled = !busy,
                    onRemove = viewModel::removeEvents,
                    tags = { Tag("${events.events.size} events", Funkin.Warning) }
                )
            }
        }

        FunkinCard {
            SectionTitle("Song info", accent, 2)
            FunkinTextField(viewModel.songId, { viewModel.songId = it }, "Song id (folder & file names)", accent, enabled = !busy)
            FunkinTextField(viewModel.songName, { viewModel.songName = it }, "Song name", accent, enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FunkinTextField(viewModel.artist, { viewModel.artist = it }, "Artist", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.charter, { viewModel.charter = it }, "Charter", accent, Modifier.weight(1f), enabled = !busy)
            }
            Text(
                "Saved to data/songs/<song id>/ in your mod.",
                style = MaterialTheme.typography.bodySmall,
                color = Funkin.Muted,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 3,
            canConvert = viewModel.charts.isNotEmpty(),
            onConvert = viewModel::convert,
            onSave = onSave,
            modFolderName = modFolderName
        )
    }
}

private fun formatBpm(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
