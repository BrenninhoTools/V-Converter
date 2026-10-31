package com.brenninhotools.vconverter.editorview

import com.brenninhotools.vconverter.core.fmt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.overlay.UiSound
import com.brenninhotools.vconverter.overlay.soundClickable

object EditorIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White))
            .build()

    val Play by lazy { icon("Play", "M8,5v14l11,-7z") }
    val Pause by lazy { icon("Pause", "M6,5h4v14h-4zM14,5h4v14h-4z") }
    val ToStart by lazy { icon("ToStart", "M6,6h2v12h-2zM9.5,12l8.5,6V6z") }
    val Back by lazy { icon("Back", "M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z") }
    val Forward by lazy { icon("Forward", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z") }
    val Undo by lazy { icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z") }
    val Redo by lazy { icon("Redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z") }
    val ZoomIn by lazy { icon("ZoomIn", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14zM12,10h-2v2H9v-2H7V9h2V7h1v2h2v1z") }
    val ZoomOut by lazy { icon("ZoomOut", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14zM7,9h5v1H7z") }
    val Sound by lazy { icon("Sound", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z") }
    val Mute by lazy { icon("Mute", "M3,9v6h4l5,5V4L7,9H3zM16,9.4l1.4,-1.4 2.1,2.1 2.1,-2.1 1.4,1.4 -2.1,2.1 2.1,2.1 -1.4,1.4 -2.1,-2.1 -2.1,2.1 -1.4,-1.4 2.1,-2.1z") }
}

@Composable
fun ToolButton(icon: ImageVector, description: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) scheme.primary else scheme.surfaceVariant)
            .soundClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
                active -> scheme.onPrimary
                else -> scheme.onSurface
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun <T> CompactChoice(label: String, value: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        Column(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surfaceVariant)
                .soundClickable { open = true }
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 9.sp, color = scheme.onSurfaceVariant)
            Text((options.firstOrNull { it.first == value }?.second ?: value.toString()) + " ▾", fontSize = 13.sp, color = scheme.onSurface, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { UiSound.click(); open = false; onSelect(key) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartToolbar(state: ChartEditorState, modifier: Modifier = Modifier) {
    val conductor = state.document.conductor
    val position = state.position
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton(EditorIcons.ToStart, "Go to start") { state.seek(0.0) }
            ToolButton(EditorIcons.Back, "Previous measure") { state.jumpMeasure(-1) }
            ToolButton(if (state.playing) EditorIcons.Pause else EditorIcons.Play, if (state.playing) "Pause" else "Play", active = state.playing) { state.togglePlay() }
            ToolButton(EditorIcons.Forward, "Next measure") { state.jumpMeasure(1) }
            ToolButton(EditorIcons.Undo, "Undo", enabled = state.canUndo) { state.undo() }
            ToolButton(EditorIcons.Redo, "Redo", enabled = state.canRedo) { state.redo() }
            ToolButton(EditorIcons.ZoomOut, "Zoom out") { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.25f) }
            ToolButton(EditorIcons.ZoomIn, "Zoom in") { state.zoom = (state.zoom * 1.25f).coerceAtMost(6f) }
            ToolButton(if (state.hitsounds) EditorIcons.Sound else EditorIcons.Mute, "Hitsounds", active = state.hitsounds) { state.hitsounds = !state.hitsounds }
            CompactChoice("Snap", state.snap, state.snaps.map { it to "1/$it" }) { state.snap = it }
            CompactChoice("Difficulty", state.difficulty, state.document.difficultyNames.map { it to it }) {
                state.difficulty = it
                state.clearSelection()
            }
        }
        Text(
            "${formatTime(position)}  ·  Measure ${conductor.measureAt(position) + 1}  ·  Beat ${fmt("%.2f", conductor.beatAt(position))}  ·  ${trim(conductor.change(position).bpm)} BPM  ·  ${state.document.notes(state.difficulty).size} notes",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChartMiniMap(state: ChartEditorState, modifier: Modifier = Modifier) {
    fun seekTo(y: Float, height: Int) {
        if (height <= 0) return
        if (state.playing) state.togglePlay()
        state.seek((y / height).coerceIn(0f, 1f) * state.songLength)
    }
    Canvas(
        modifier
            .pointerInput(state) { detectTapGestures { seekTo(it.y, size.height) } }
            .pointerInput(state) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    seekTo(change.position.y, size.height)
                }
            }
    ) {
        state.revision
        val length = state.songLength
        drawRect(EditorColors.outside)
        if (length <= 0) return@Canvas
        val lanesWidth = size.width * 0.8f
        val laneWidth = lanesWidth / 8f
        val tick = 2.dp.toPx()
        state.document.notes(state.difficulty).forEach { note ->
            val y = (note.time / length * size.height).toFloat()
            drawRect(EditorColors.lanes[note.direction], Offset(laneWidth * note.column, y), Size(laneWidth, tick))
        }
        state.document.events.forEach { event ->
            val y = (event.time / length * size.height).toFloat()
            drawRect(EditorColors.event, Offset(lanesWidth, y), Size(size.width - lanesWidth, tick))
        }
        drawLine(Color.White.copy(alpha = 0.25f), Offset(laneWidth * 4, 0f), Offset(laneWidth * 4, size.height))
        val visible = state.visibleMs
        val top = ((state.position - visible * STRUMLINE) / length * size.height).toFloat()
        val height = (visible / length * size.height).toFloat().coerceAtLeast(4f)
        drawRect(Color.White.copy(alpha = 0.12f), Offset(0f, top), Size(size.width, height))
        drawRect(Color.White.copy(alpha = 0.7f), Offset(0f, top), Size(size.width, height), style = Stroke(1.5.dp.toPx()))
        val playY = (state.position / length * size.height).toFloat()
        drawLine(EditorColors.playhead, Offset(0f, playY), Offset(size.width, playY), strokeWidth = 2.dp.toPx())
    }
}

private enum class ChartPanel(val label: String) { SELECTION("Selection"), DIFFICULTY("Difficulty"), TIMING("Timing"), SONG("Song") }

@Composable
fun ChartEditorWorkspace(
    state: ChartEditorState,
    modifier: Modifier = Modifier,
    skin: NoteSkin? = null,
    eventIcon: ImageBitmap? = null,
    gridHeight: Dp = 560.dp
) {
    val outline = MaterialTheme.colorScheme.outline
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= 860.dp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChartToolbar(state, Modifier.fillMaxWidth())
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectionPanel(state)
                        DifficultyPanel(state)
                    }
                    Row(Modifier.weight(1.5f).height(gridHeight).clip(RoundedCornerShape(12.dp)).border(1.5.dp, outline, RoundedCornerShape(12.dp))) {
                        EditorGrid(state, Modifier.weight(1f).height(gridHeight), skin, eventIcon)
                        ChartMiniMap(state, Modifier.width(40.dp).height(gridHeight))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimingPanel(state)
                        SongInfoPanel(state)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(gridHeight).clip(RoundedCornerShape(12.dp)).border(1.5.dp, outline, RoundedCornerShape(12.dp))) {
                    EditorGrid(state, Modifier.weight(1f).height(gridHeight), skin, eventIcon)
                    ChartMiniMap(state, Modifier.width(32.dp).height(gridHeight))
                }
                var panel by rememberSaveable { mutableStateOf(ChartPanel.SELECTION) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartPanel.entries.forEach { p -> EditorChip(p.label, p == panel) { panel = p } }
                }
                when (panel) {
                    ChartPanel.SELECTION -> SelectionPanel(state)
                    ChartPanel.DIFFICULTY -> DifficultyPanel(state)
                    ChartPanel.TIMING -> TimingPanel(state)
                    ChartPanel.SONG -> SongInfoPanel(state)
                }
            }
        }
    }
}
