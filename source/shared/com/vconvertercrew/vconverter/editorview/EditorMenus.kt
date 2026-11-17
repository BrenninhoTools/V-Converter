package com.vconvertercrew.vconverter.editorview

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.editorview.EditorMenuItem.Action
import com.vconvertercrew.vconverter.editorview.EditorMenuItem.Check
import com.vconvertercrew.vconverter.editorview.EditorMenuItem.Separator

object ChartMenus {
    fun edit(state: ChartEditorState) = EditorMenu(
        "Edit",
        listOf(
            Action("Undo", "Ctrl+Z", state.canUndo) { state.undo() },
            Action("Redo", "Ctrl+Y", state.canRedo) { state.redo() },
            Separator,
            Action("Delete Selection", "Delete") { state.deleteSelection() },
            Action("Clear Selection", "Esc") { state.clearSelection() },
            Separator,
            Action("Shorter Hold", "Q") { state.changeHold(-1) },
            Action("Longer Hold", "E") { state.changeHold(1) }
        )
    )

    fun playback(state: ChartEditorState): List<EditorMenuItem> = listOf(
        Action(if (state.playing) "Pause" else "Play", "Space") { state.togglePlay() },
        Action("Go To Start", "Home") { state.seek(0.0) },
        Action("Previous Measure", "PgUp") { state.jumpMeasure(-1) },
        Action("Next Measure", "PgDn") { state.jumpMeasure(1) },
        Separator,
        Check("Hitsounds", state.hitsounds) { state.hitsounds = !state.hitsounds }
    )

    fun view(state: ChartEditorState) = EditorMenu(
        "View",
        listOf(
            Action("Zoom In") { state.zoom = (state.zoom * 1.25f).coerceAtMost(6f) },
            Action("Zoom Out") { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.25f) },
            Separator
        ) + state.snaps.map { snap -> Check("Snap 1/$snap", state.snap == snap) { state.snap = snap } } +
            listOf(Separator) +
            state.document.difficultyNames.map { name ->
                Check("Difficulty: $name", state.difficulty == name) {
                    state.difficulty = name
                    state.clearSelection()
                }
            }
    )
}

object CharacterMenus {
    fun edit(state: CharacterEditorState) = EditorMenu(
        "Edit",
        listOf(
            Action("Undo", "Ctrl+Z", state.canUndo) { state.undo() },
            Action("Redo", "Ctrl+Y", state.canRedo) { state.redo() },
            Separator,
            Action("Previous Animation", "W") { state.select(state.selected - 1) },
            Action("Next Animation", "S") { state.select(state.selected + 1) },
            Action("Delete Animation", enabled = state.document.animations.isNotEmpty()) { state.removeSelected() }
        )
    )

    fun view(state: CharacterEditorState) = EditorMenu(
        "View",
        listOf(
            Action(if (state.playing) "Pause" else "Play", "Space") { state.togglePlay() },
            Action("Previous Frame", ",") { state.step(-1) },
            Action("Next Frame", ".") { state.step(1) },
            Separator,
            Check("Onion Skin", state.ghost, "G") { state.ghost = !state.ghost },
            Check("Camera", state.showCamera) { state.showCamera = !state.showCamera },
            Check("Stage", state.showBackground) { state.showBackground = !state.showBackground },
            Separator,
            Action("Zoom In") { state.zoom = (state.zoom * 1.25f).coerceAtMost(8f) },
            Action("Zoom Out") { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.05f) },
            Action("Reset View") { state.resetView() },
            Separator
        ) + DragMode.entries.map { mode -> Check("Drag Moves: ${mode.label}", state.dragMode == mode) { state.dragMode = mode } }
    )
}

object StageMenus {
    fun edit(state: StageEditorState) = EditorMenu(
        "Edit",
        listOf(
            Action("Undo", "Ctrl+Z", state.canUndo) { state.undo() },
            Action("Redo", "Ctrl+Y", state.canRedo) { state.redo() },
            Separator,
            Action("Duplicate", "Ctrl+D") { state.duplicateSelected() },
            Action("Delete", "Delete") { state.deleteSelected() },
            Action("Deselect", "Esc") { state.selection = null },
            Separator,
            Action("Layer Up", "PgUp") { state.moveLayer(1) },
            Action("Layer Down", "PgDn") { state.moveLayer(-1) }
        )
    )

    fun view(state: StageEditorState) = EditorMenu(
        "View",
        listOf(
            Action("Zoom In") { state.zoom = (state.zoom * 1.25f).coerceAtMost(4f) },
            Action("Zoom Out") { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.05f) },
            Action("Reset View") { state.resetView() },
            Separator,
            Check("Parallax", state.parallax) { state.parallax = !state.parallax },
            Check("Camera", state.showCamera) { state.showCamera = !state.showCamera }
        )
    )
}

object WeekMenus {
    fun edit(state: WeekEditorState) = EditorMenu(
        "Edit",
        listOf(
            Action("Undo", "Ctrl+Z", state.canUndo) { state.undo() },
            Action("Redo", "Ctrl+Y", state.canRedo) { state.redo() },
            Separator,
            Action("Deselect", "Esc") { state.selected = null }
        )
    )

    fun view(state: WeekEditorState) = EditorMenu(
        "View",
        listOf(
            Check("Animate", state.playing, "Space") { state.playing = !state.playing },
            Action("Play Confirm", "Enter") { state.confirm() },
            Separator,
            Action("Previous Difficulty", "Left") { state.changeDifficulty(-1) },
            Action("Next Difficulty", "Right") { state.changeDifficulty(1) }
        )
    )
}

@Composable
fun EditorHelpText(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ChartEditorStage(state: ChartEditorState, skin: NoteSkin?, eventIcon: ImageBitmap?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartToolbar(state, Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 820.dp).fillMaxHeight().clip(shape).border(1.5.dp, MaterialTheme.colorScheme.outline, shape)) {
                EditorGrid(state, Modifier.weight(1f).fillMaxHeight(), skin, eventIcon)
                ChartMiniMap(state, Modifier.width(40.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
fun CharacterEditorStage(state: CharacterEditorState, background: ImageBitmap?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CharacterToolbar(state)
        DragModeBar(state)
        CharacterPreview(state, Modifier.weight(1f).fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape), background)
    }
}

@Composable
fun StageEditorStage(state: StageEditorState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StageToolbar(state)
        StagePreview(state, Modifier.weight(1f).fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape))
    }
}

@Composable
fun WeekEditorStage(state: WeekEditorState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeekToolbar(state)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            WeekPreview(state, Modifier.aspectRatio(16f / 9f).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape))
        }
    }
}
