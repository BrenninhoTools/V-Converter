package com.brenninhotools.vconverter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(viewModel: ConverterViewModel) {
    val pickCharts = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addCharts(uris)
    }
    val pickEvents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickEvents(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.save(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("V-Converter", fontWeight = FontWeight.Bold)
                        Text(
                            "Psych Engine → V-Slice",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (viewModel.charts.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll, enabled = !viewModel.busy) { Text("Clear") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "1. Charts") {
                Text(
                    "Pick one or more Psych Engine charts of the same song (e.g. song-easy.json, song.json, song-hard.json). You can also pick events.json here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = { pickCharts.launch(arrayOf("*/*")) },
                    enabled = !viewModel.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add charts")
                }
                viewModel.charts.forEachIndexed { index, chart ->
                    ChartRow(
                        chart = chart,
                        onDifficultyChange = { viewModel.setDifficulty(index, it) },
                        onRemove = { viewModel.removeChart(index) },
                        enabled = !viewModel.busy
                    )
                }
                val events = viewModel.events
                if (events == null) {
                    OutlinedButton(
                        onClick = { pickEvents.launch(arrayOf("*/*")) },
                        enabled = !viewModel.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add events.json (optional)")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${events.fileName} · ${events.count} events",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = viewModel::clearEvents, enabled = !viewModel.busy) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove events file")
                        }
                    }
                }
            }

            SectionCard(title = "2. Song info") {
                OutlinedTextField(
                    value = viewModel.songId,
                    onValueChange = { viewModel.songId = it },
                    label = { Text("Song id (folder & file names)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.songName,
                    onValueChange = { viewModel.songName = it },
                    label = { Text("Song name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.artist,
                    onValueChange = { viewModel.artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.charter,
                    onValueChange = { viewModel.charter = it },
                    label = { Text("Charter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionCard(title = "3. Convert") {
                Button(
                    onClick = viewModel::convert,
                    enabled = !viewModel.busy && viewModel.charts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewModel.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Convert to V-Slice")
                }
                val output = viewModel.output
                if (output != null) {
                    FilledTonalButton(
                        onClick = { pickFolder.launch(null) },
                        enabled = !viewModel.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save to folder")
                    }
                }
                StatusText(viewModel.status)
                if (output != null) {
                    Text(
                        output.report.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ChartRow(
    chart: PickedChart,
    onDifficultyChange: (String) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(chart.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val notes = chart.song.sections.sumOf { it.notes.size }
            Text(
                "${chart.song.format} · ${chart.song.bpm.formatBpm()} BPM · $notes notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = chart.difficulty,
            onValueChange = onDifficultyChange,
            label = { Text("Difficulty") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.width(132.dp)
        )
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove ${chart.fileName}")
        }
    }
}

@Composable
private fun StatusText(status: StatusMessage?) {
    when (status) {
        is StatusMessage.Info -> Text(status.text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        is StatusMessage.Error -> Text(status.text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        null -> Unit
    }
}

private fun Double.formatBpm(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
