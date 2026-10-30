package com.brenninhotools.vconverter.playview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editorview.EditorChip
import com.brenninhotools.vconverter.editorview.EditorSectionTitle
import com.brenninhotools.vconverter.editorview.NumberField
import com.brenninhotools.vconverter.playtest.IssueLevel
import com.brenninhotools.vconverter.playtest.Judgement
import kotlin.math.abs

private val levelColors = mapOf(IssueLevel.ERROR to Color(0xFFFF6B6B), IssueLevel.WARNING to Color(0xFFFFC94A), IssueLevel.INFO to Color(0xFF7CC8FF))

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayTestSetupPanel(state: PlayTestState, modifier: Modifier = Modifier, onStart: () -> Unit = { state.start() }) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorSectionTitle("Song")
        Text(state.source, fontSize = 13.sp, color = scheme.onSurfaceVariant)
        if (state.busy) Text("Loading…", fontSize = 13.sp, color = scheme.primary)
        state.error?.let { Text(it, fontSize = 13.sp, color = scheme.error) }
        if (state.songs.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.songs.forEach { ref -> EditorChip(ref.label, ref == state.selected) { state.select(ref) } }
            }
        }
        val doc = state.document
        if (doc == null) {
            Text("Open a mod (folder or .zip), a converter's output or chart files, or send a chart from the Chart Editor. Psych and Codename mods are converted to V-Slice first, so PlayTest also tests the converter.", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            return@Column
        }
        Text("${doc.meta.songName.ifBlank { doc.id }} · ${doc.meta.player} vs ${doc.meta.opponent} · stage ${doc.meta.stage}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            doc.difficultyNames.forEach { name ->
                EditorChip("$name (${doc.notes(name).size})", name == state.difficulty) { state.difficulty = name }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Check("Botplay", state.botplay) { state.botplay = it }
            Check("Downscroll", state.downscroll) { state.downscroll = it }
            Check("Stage & characters", state.showStage) { state.showStage = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField("Start at (seconds)", state.startAt / 1000.0, Modifier.weight(1f)) { v -> state.startAt = (v * 1000.0).coerceAtLeast(0.0) }
            EditorChip(if (state.phase == PlayPhase.LOADING) "Loading…" else "Play", true, onStart)
        }
        val errors = state.issues.count { it.level == IssueLevel.ERROR }
        val warnings = state.issues.count { it.level == IssueLevel.WARNING }
        if (errors + warnings > 0) Text("$errors error(s), $warnings warning(s) found: see the report.", fontSize = 12.sp, color = if (errors > 0) levelColors.getValue(IssueLevel.ERROR) else levelColors.getValue(IssueLevel.WARNING))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayTestControls(state: PlayTestState, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.phase) {
            PlayPhase.PLAYING -> EditorChip("Pause", false) { state.pause() }
            PlayPhase.PAUSED -> EditorChip("Resume", true) { state.resume() }
            else -> EditorChip("Play again", false) { state.start() }
        }
        EditorChip("Botplay", state.botplay) { state.toggleBotplay() }
        if (state.phase == PlayPhase.PLAYING || state.phase == PlayPhase.PAUSED) EditorChip("Stop", false) { state.stop() }
        if (onClose != null) EditorChip("Close", false) {
            state.backToSetup()
            onClose()
        }
    }
}

@Composable
fun PlayTestResultsPanel(state: PlayTestState, modifier: Modifier = Modifier) {
    val game = state.engine ?: return
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorSectionTitle("Results")
        Text("${game.rating} · ${"%.2f".format(game.accuracy * 100)}% · ${game.score} points", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(Judgement.entries.joinToString("   ") { "${it.label} ${game.counts.getValue(it)}" } + "   MISS ${game.misses}", fontSize = 13.sp)
        Text("Max combo ${game.maxCombo} · dropped holds ${game.holdDrops} · lowest health ${"%.0f".format(game.lowestHealth * 50)}%", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        if (game.offsets.isNotEmpty()) {
            val average = game.offsets.average()
            Text("Average timing ${"%.1f".format(abs(average))} ms ${if (average >= 0) "late" else "early"} over ${game.offsets.size} hit(s)", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        game.deathTime?.let { Text("Health ran out at ${PlayTestState.formatMs(it)}.", fontSize = 13.sp, color = scheme.error) }
        if (state.botplay) Text("Botplay was on: the score shows the chart, not you.", fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
fun PlayTestIssuesPanel(state: PlayTestState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Report")
        val issues = state.issues
        if (state.document == null) {
            Text("Load a song to check it.", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            return@Column
        }
        if (issues.isEmpty()) Text("No problems found in the chart, characters, stage or audio.", fontSize = 13.sp, color = scheme.primary)
        IssueLevel.entries.forEach { level ->
            val list = issues.filter { it.level == level }
            if (list.isEmpty()) return@forEach
            Text("${level.name.lowercase().replaceFirstChar { it.uppercase() }}s (${list.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = levelColors.getValue(level))
            list.forEach { issue ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(issue.area, fontSize = 12.sp, color = levelColors.getValue(level))
                    Text(issue.message, fontSize = 13.sp)
                }
            }
        }
    }
}
