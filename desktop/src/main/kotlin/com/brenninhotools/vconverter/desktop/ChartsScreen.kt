package com.brenninhotools.vconverter.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.chart.preview.ChartField
import com.brenninhotools.vconverter.chart.preview.ChartPreview
import com.brenninhotools.vconverter.chart.preview.ChartWorkbench
import com.brenninhotools.vconverter.chartview.ChartStrip
import com.brenninhotools.vconverter.chartview.ChartStripColors
import com.brenninhotools.vconverter.chartview.formatBpm
import com.brenninhotools.vconverter.chartview.formatDuration

private val StripColors = ChartStripColors(
    background = Palette.Ink,
    lane = Palette.SurfaceHigh.copy(alpha = 0.55f),
    grid = Palette.Outline,
    text = Palette.Text,
    muted = Palette.Muted,
    left = Palette.Left,
    down = Palette.Down,
    up = Palette.Up,
    right = Palette.Right,
    opponent = Palette.Orange,
    player = Palette.Down,
    girlfriend = Palette.Pink,
    event = Palette.Warning
)

private val zoomLevels = listOf(null, 40f, 90f, 180f)
private val zoomLabels = listOf("Fit", "1×", "2×", "4×")

@Composable
internal fun ChartsScreen(state: AppState, files: FileTabState) {
    val accent = state.tab.accent
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FilesCard(
                state, files, accent,
                tags = { _, item ->
                    val difficulty = ChartWorkbench.difficultyOf(files.converter, item)
                    Chip(item.kind, if (item.kind == "chart") accent else Palette.Warning)
                    if (difficulty != null && !files.canRenameDifficulty) {
                        Spacer(Modifier.width(6.dp))
                        Chip(difficulty, Palette.Muted)
                    }
                },
                extra = { index, item ->
                    val difficulty = ChartWorkbench.difficultyOf(files.converter, item)
                    if (difficulty != null && files.canRenameDifficulty) {
                        Spacer(Modifier.width(8.dp))
                        var text by remember(item.key) { mutableStateOf(difficulty) }
                        CompactField(text, {
                            text = it
                            if (it.isNotBlank()) files.setDifficulty(index, it)
                        }, "Difficulty", accent, !files.busy, Modifier.width(130.dp))
                    }
                }
            )
            if (files.items.any { it.kind == "chart" }) OptionsCard(files, accent)
        }
        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (files.before.isNotEmpty()) PreviewCard(state, files, accent)
            OutputCard(state, files, accent)
        }
    }
}

@Composable
private fun CompactField(value: String, onChange: (String) -> Unit, label: String, accent: Color, enabled: Boolean, modifier: Modifier = Modifier, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = if (placeholder.isNullOrBlank()) null else {
            { Text(placeholder, color = Palette.Muted.copy(alpha = 0.6f), maxLines = 1) }
        },
        enabled = enabled,
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent, cursorColor = accent, unfocusedBorderColor = Palette.Outline)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewCard(state: AppState, files: FileTabState, accent: Color) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("Preview", accent) }
            if (files.after.isNotEmpty()) {
                Toggle(listOf("${state.direction.from.title}", "${state.direction.to.title}"), if (files.showAfter) 1 else 0, accent) { files.showAfter = it == 1 }
                Spacer(Modifier.width(10.dp))
            }
            Toggle(zoomLabels, files.zoom, accent) { files.zoom = it }
        }
        val before = files.before
        if (before.size > 1) {
            val multipleSongs = before.map { it.song }.distinct().size > 1
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                before.forEach { preview ->
                    val active = files.selectedBefore == preview
                    Text(
                        if (multipleSongs) "${preview.song} · ${preview.label}" else preview.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Palette.Ink else Palette.Text,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) accent else Palette.SurfaceHigh)
                            .clickable { files.selected = files.key(preview) }.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
        val current = files.currentPreview ?: return@Card
        Text("${current.song} · ${current.label}", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        ChartStrip(current, StripColors, pixelsPerSecond = zoomLevels[files.zoom.coerceIn(0, zoomLevels.lastIndex)])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Dot(StripColors.opponent, "Opponent${if (current.opponent.isNotBlank()) " · ${current.opponent}" else ""}")
            Dot(StripColors.player, "Player${if (current.player.isNotBlank()) " · ${current.player}" else ""}")
            if (current.otherNotes > 0 || current.focus.any { it.side == 2 }) Dot(StripColors.girlfriend, "Girlfriend${if (current.girlfriend.isNotBlank()) " · ${current.girlfriend}" else ""}")
            Dot(StripColors.event, "Events")
            Text("Top band: camera focus", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
        Stats(current, accent)
        val other = if (files.showAfter) files.selectedBefore else null
        if (other != null) Compare(other, current)
    }
}

@Composable
private fun Toggle(options: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Ink).border(1.dp, Palette.Outline, RoundedCornerShape(10.dp)).padding(3.dp)) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) Palette.Ink else Palette.Muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) accent else Color.Transparent)
                    .clickable { onSelect(index) }.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun Dot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stats(preview: ChartPreview, accent: Color) {
    val (low, high) = preview.bpmRange
    val stats = listOf(
        "Player notes" to preview.playerNotes.toString(),
        "Opponent notes" to preview.opponentNotes.toString(),
        "Holds" to preview.holdNotes.toString(),
        "Peak NPS" to formatBpm(preview.peakNps),
        "Length" to formatDuration(preview.length),
        "BPM" to if (low == high) formatBpm(low) else "${formatBpm(low)}–${formatBpm(high)}",
        "Time signature" to preview.signatures.joinToString(" · ") { "$it/4" },
        "Events" to preview.events.size.toString(),
        "Scroll speed" to formatBpm(preview.speed)
    ) + listOfNotNull(
        if (preview.otherNotes > 0) "Girlfriend notes" to preview.otherNotes.toString() else null,
        if (preview.duplicates > 0) "Stacked duplicates" to preview.duplicates.toString() else null,
        if (preview.offset != 0.0) "Offset" to "${formatBpm(preview.offset)} ms" else null,
        if (preview.stage.isNotBlank()) "Stage" to preview.stage else null
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (label, value) ->
            Column(Modifier.widthIn(min = 96.dp).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = accent, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Compare(before: ChartPreview, after: ChartPreview) {
    val checks = listOf(
        "Player notes" to (before.playerNotes to after.playerNotes),
        "Opponent notes" to (before.opponentNotes to after.opponentNotes),
        "Holds" to (before.holdNotes to after.holdNotes),
        "BPM changes" to (before.timing.size to after.timing.size)
    ).filter { it.second.first != it.second.second }
    val color = if (checks.isEmpty()) Palette.Up else Palette.Warning
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            if (checks.isEmpty()) "✓ Every note, hold and BPM change made it across." else "Some counts changed in the conversion:",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Text
        )
        checks.forEach { (label, pair) -> Text("• $label: ${pair.first} → ${pair.second}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
    }
}

@Composable
private fun OptionsCard(files: FileTabState, accent: Color) {
    val fields = files.chartFields
    val tweaks = files.tweaks
    val sample = files.before.firstOrNull()
    val enabled = !files.busy
    Card {
        SectionTitle("Options", accent)
        Text("Empty fields keep what the chart already uses.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        if (ChartField.SONG_NAME in fields) {
            CompactField(tweaks.songName, { v -> files.updateTweaks { it.copy(songName = v) } }, "Song name", accent, enabled, Modifier.fillMaxWidth())
        }
        if (ChartField.ARTIST in fields) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactField(tweaks.artist, { v -> files.updateTweaks { it.copy(artist = v) } }, "Artist", accent, enabled, Modifier.weight(1f))
                CompactField(tweaks.charter, { v -> files.updateTweaks { it.copy(charter = v) } }, "Charter", accent, enabled, Modifier.weight(1f))
            }
        }
        if (ChartField.CHARACTERS in fields) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactField(tweaks.player, { v -> files.updateTweaks { it.copy(player = v.trim()) } }, "Player", accent, enabled, Modifier.weight(1f), sample?.player)
                CompactField(tweaks.opponent, { v -> files.updateTweaks { it.copy(opponent = v.trim()) } }, "Opponent", accent, enabled, Modifier.weight(1f), sample?.opponent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactField(tweaks.girlfriend, { v -> files.updateTweaks { it.copy(girlfriend = v.trim()) } }, "Girlfriend", accent, enabled, Modifier.weight(1f), sample?.girlfriend)
                if (ChartField.STAGE in fields) {
                    CompactField(tweaks.stage, { v -> files.updateTweaks { it.copy(stage = v.trim()) } }, "Stage", accent, enabled, Modifier.weight(1f), sample?.stage)
                }
            }
        }
        if (ChartField.NOTE_STYLE in fields) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Note style", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                val styles = listOf("" to "Auto", "funkin" to "Funkin", "pixel" to "Pixel")
                Toggle(styles.map { it.second }, styles.indexOfFirst { it.first == tweaks.noteStyle }.coerceAtLeast(0), accent) { index ->
                    if (enabled) files.updateTweaks { it.copy(noteStyle = styles[index].first) }
                }
            }
        }
        if (ChartField.SPEED in fields && files.difficulties.isNotEmpty()) {
            Text("Scroll speed", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            files.difficulties.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { difficulty ->
                        val detected = files.before.firstOrNull { it.difficulty == difficulty }?.speed
                        var text by remember(difficulty, tweaks.speeds.containsKey(difficulty)) { mutableStateOf(tweaks.speeds[difficulty]?.let(::formatBpm) ?: "") }
                        CompactField(text, {
                            text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            files.setSpeed(difficulty, text)
                        }, difficulty, accent, enabled, Modifier.weight(1f), detected?.let(::formatBpm))
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (ChartField.KEEP_EVENTS in fields) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = enabled) { files.updateTweaks { it.copy(keepUnknownEvents = !it.keepUnknownEvents) } }) {
                Checkbox(
                    checked = tweaks.keepUnknownEvents,
                    onCheckedChange = { value -> files.updateTweaks { it.copy(keepUnknownEvents = value) } },
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = Palette.Ink)
                )
                Column {
                    Text("Keep Psych-only events", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                    Text("Events without a V-Slice twin stay in the chart, with SongEvent scripts for the built-in ones.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
        }
        if (!tweaks.isEmpty) {
            TextButton(onClick = { files.updateTweaks { com.brenninhotools.vconverter.chart.preview.ChartTweaks(keepUnknownEvents = it.keepUnknownEvents) } }, enabled = enabled) {
                Text("Reset options", color = Palette.Muted)
            }
        }
    }
}
