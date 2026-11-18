package com.vconvertercrew.vconverter.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.editor.EditorNote
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vtouch.VContextItem
import com.vconvertercrew.vtouch.VContextMenu
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private object FnfGrid {
    val light = Color(0xFFE7E6E6)
    val dark = Color(0xFFD9D5D5)
    val eventLight = Color(0xFFD8D0E2)
    val eventDark = Color(0xFFC9BFD6)
    val line = Color.Black
    val strum = Color.White
    val box = Color(0xFF3A3A3A)
    val boxTab = Color(0xFF2A2A2A)
    val boxText = Color(0xFFF2F2F2)
    val hover = Color.White.copy(alpha = 0.55f)
}

private const val ROWS = 16
private const val LANES = 8

private val tabs = listOf("Song", "Section", "Note", "Events")

private fun two(value: Double): String {
    val scaled = (value * 100.0).roundToInt()
    val whole = scaled / 100
    val part = (scaled % 100).let { if (it < 10) "0$it" else "$it" }
    return "$whole.$part"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FnfChartEditor(
    state: ChartEditorState,
    skin: NoteSkin?,
    eventIcon: ImageBitmap?,
    onSave: () -> Unit,
    onReloadAudio: () -> Unit,
    onReloadJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    val measurer = rememberTextMeasurer()
    var hover by remember { mutableStateOf<Pair<Int, Double>?>(null) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var dragged by remember { mutableStateOf(0f) }

    LaunchedEffect(state.playing) {
        while (state.playing) withFrameNanos { state.tick(it) }
    }

    fun key(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || EditorTyping.fields > 0) return false
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val shift = event.isShiftPressed
        when {
            ctrl -> return false
            event.key == Key.D || event.key == Key.DirectionRight -> state.changeSection(if (shift) 4 else 1)
            event.key == Key.A || event.key == Key.DirectionLeft -> state.changeSection(if (shift) -4 else -1)
            event.key == Key.W || event.key == Key.S -> {
                if (state.playing) state.togglePlay()
                val stepMs = state.document.conductor.stepMs(state.position)
                val amount = if (shift) stepMs * 2.0 else stepMs * 0.5
                state.seek((state.position + if (event.key == Key.W) -amount else amount).coerceAtLeast(0.0))
            }
            event.key == Key.R -> state.resetSection(shift)
            event.key == Key.Tab -> state.legacyTab = ((state.legacyTab + if (shift) -1 else 1) % tabs.size + tabs.size) % tabs.size
            else -> return false
        }
        return true
    }

    Row(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { key(it) }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val section = state.section
            val doc = state.document
            val headerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
            val cell = min((constraints.maxHeight - headerPx) / ROWS, constraints.maxWidth / (LANES + 1.6f)).coerceAtLeast(8f)
            val gridWidth = cell * LANES
            val eventX = gridWidth + cell * 0.3f
            val totalWidth = eventX + cell
            val left = (constraints.maxWidth - totalWidth) / 2f
            val top = headerPx

            fun cellAt(offset: Offset): Pair<Int, Double>? {
                val x = offset.x - left
                val y = offset.y - top
                if (y < 0f || y >= cell * ROWS) return null
                val column = when {
                    x in 0f..<gridWidth -> (x / cell).toInt().coerceIn(0, LANES - 1)
                    x in eventX..<(eventX + cell) -> -1
                    else -> return null
                }
                return column to (y / cell).toDouble()
            }

            fun click(offset: Offset) {
                runCatching { focus.requestFocus() }
                val (column, row) = cellAt(offset) ?: return
                val modifiers = windowInfo.keyboardModifiers
                val snappedRow = if (modifiers.isShiftPressed) row else floor(row)
                val time = state.timeAtRow(section, snappedRow)
                if (column < 0) {
                    state.tap(time, -1)
                    UiSound.click()
                    return
                }
                val existing = state.noteAtCell(state.timeAtRow(section, row), column) ?: state.noteAtCell(time, column)
                when {
                    existing != null && (modifiers.isCtrlPressed || modifiers.isMetaPressed) -> state.selectNote(existing)
                    existing != null -> state.removeNote(existing)
                    else -> state.placeNote(time, column, modifiers.isCtrlPressed || modifiers.isMetaPressed)
                }
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(state, cell) {
                        detectTapGestures(
                            onLongPress = press@{ offset ->
                                val (column, row) = cellAt(offset) ?: return@press
                                if (state.selectAt(state.timeAtRow(section, row), column)) menuAt = offset
                            },
                            onTap = { click(it) }
                        )
                    }
                    .pointerInput(state, cell) {
                        detectVerticalDragGestures(onDragEnd = { dragged = 0f }) { change, amount ->
                            change.consume()
                            if (state.playing) state.togglePlay()
                            dragged += amount
                            val stepMs = state.document.conductor.stepMs(state.position)
                            val rows = (dragged / cell).toInt()
                            if (rows != 0) {
                                state.seek((state.position - rows * stepMs).coerceAtLeast(0.0))
                                dragged -= rows * cell
                            }
                        }
                    }
                    .pointerInput(state, cell) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                when (event.type) {
                                    PointerEventType.Move, PointerEventType.Enter -> hover = cellAt(change.position)
                                    PointerEventType.Exit -> hover = null
                                    PointerEventType.Scroll -> {
                                        if (state.playing) state.togglePlay()
                                        val stepMs = state.document.conductor.stepMs(state.position)
                                        state.seek((state.position + change.scrollDelta.y * stepMs).coerceAtLeast(0.0))
                                        change.consume()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
            ) {
                for (row in 0 until ROWS) {
                    for (column in 0 until LANES) {
                        drawRect(if ((row + column) % 2 == 0) FnfGrid.light else FnfGrid.dark, Offset(left + column * cell, top + row * cell), Size(cell, cell))
                    }
                    drawRect(if (row % 2 == 0) FnfGrid.eventLight else FnfGrid.eventDark, Offset(left + eventX, top + row * cell), Size(cell, cell))
                }
                drawRect(FnfGrid.line, Offset(left + gridWidth / 2f - 1f, top), Size(2f, cell * ROWS))

                val mustHit = state.mustHit(section)
                val labelStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                listOf(doc.meta.opponent to !mustHit, doc.meta.player to mustHit).forEachIndexed { index, (name, focused) ->
                    val text = measurer.measure(name.ifBlank { if (index == 0) "dad" else "bf" }.uppercase(), labelStyle.copy(color = if (focused) Color.Black else Color.White))
                    val x = left + index * gridWidth / 2f + (gridWidth / 2f - text.size.width) / 2f
                    val y = top - text.size.height - 14f
                    drawRoundRect(if (focused) Color.White else Color(0xFF555555), Offset(x - 10f, y - 6f), Size(text.size.width + 20f, text.size.height + 12f), androidx.compose.ui.geometry.CornerRadius(8f))
                    drawText(text, topLeft = Offset(x, y))
                }

                val selected = state.selectedNote
                state.sectionNotes(section).forEach { note ->
                    val row = state.rowAt(section, note.time)
                    val column = note.column
                    val x = left + column * cell
                    val y = top + (row * cell).toFloat()
                    if (note.length > 0.0) {
                        val endRow = state.rowAt(section, note.time + note.length)
                        val bottom = min(top + cell * ROWS, top + (endRow * cell).toFloat() + cell / 2f)
                        val sustainTop = y + cell / 2f
                        if (bottom > sustainTop) drawRect(EditorColors.lanes[note.direction], Offset(x + cell / 2f - 4f, sustainTop), Size(8f, bottom - sustainTop))
                    }
                    val center = Offset(x + cell / 2f, y + cell / 2f)
                    val frame = skin?.notes?.get(note.direction)
                    if (skin != null && frame != null) sprite(skin, frame, center, cell)
                    else arrow(center, cell * 0.8f, note.direction, EditorColors.lanes[note.direction], Color.Black)
                    if (note == selected) drawRect(Color.White, Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f), style = Stroke(3f))
                }

                val sectionStart = state.sectionStart(section)
                val sectionEnd = state.sectionEnd(section)
                doc.events.filter { it.time >= sectionStart - 0.5 && it.time < sectionEnd - 0.5 }.forEach { event ->
                    val y = top + (state.rowAt(section, event.time) * cell).toFloat()
                    val size = (cell * 0.8f).roundToInt()
                    if (eventIcon != null) {
                        drawImage(eventIcon, dstOffset = IntOffset((left + eventX + (cell - size) / 2f).roundToInt(), (y + (cell - size) / 2f).roundToInt()), dstSize = IntSize(size, size))
                    } else {
                        drawCircle(EditorColors.event, cell * 0.32f, Offset(left + eventX + cell / 2f, y + cell / 2f))
                    }
                    if (event == state.selectedEvent) drawRect(Color.White, Offset(left + eventX + 1f, y + 1f), Size(cell - 2f, cell - 2f), style = Stroke(3f))
                }

                hover?.let { (column, row) ->
                    val x = if (column < 0) left + eventX else left + column * cell
                    drawRect(FnfGrid.hover, Offset(x, top + floor(row).toFloat() * cell), Size(cell, cell))
                }

                val strumY = top + (state.rowAt(section, state.position) * cell).toFloat().coerceIn(0f, cell * ROWS)
                drawRect(FnfGrid.strum, Offset(left, strumY - 2f), Size(gridWidth, 4f))

                val info = "${two(state.position / 1000.0)} / ${two(state.songLength / 1000.0)}\nSection: $section"
                val infoText = measurer.measure(info, TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace))
                drawText(infoText, topLeft = Offset(min(size.width - infoText.size.width - 4f, left + totalWidth + 16f), top))
            }

            VContextMenu(
                menuAt,
                listOfNotNull(
                    VContextItem("Delete") { state.deleteSelection() },
                    if (state.selectedNote != null) VContextItem("Shorter Hold") { state.changeHold(-1) } else null,
                    if (state.selectedNote != null) VContextItem("Longer Hold") { state.changeHold(1) } else null,
                    VContextItem("Clear Selection") { state.clearSelection() }
                ),
                onDismiss = { menuAt = null }
            )
        }

        FnfTabBox(state, onSave, onReloadAudio, onReloadJson, Modifier.widthIn(min = 260.dp, max = 340.dp).fillMaxHeight())
    }
}

@Composable
private fun FnfTabBox(state: ChartEditorState, onSave: () -> Unit, onReloadAudio: () -> Unit, onReloadJson: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.clip(shape).background(FnfGrid.box).border(1.dp, Color(0xFF6A6A6A), shape)) {
        Row(Modifier.fillMaxWidth().background(FnfGrid.boxTab)) {
            tabs.forEachIndexed { index, title ->
                val active = index == state.legacyTab
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (active) FnfGrid.box else Color.Transparent)
                        .pointerInput(index) { detectTapGestures { UiSound.click(); state.legacyTab = index } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(title, color = if (active) FnfGrid.boxText else Color(0xFFAAAAAA), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state.legacyTab) {
                0 -> SongTab(state, onSave, onReloadAudio, onReloadJson)
                1 -> SectionTab(state)
                2 -> NoteTab(state)
                else -> SelectionPanel(state)
            }
        }
    }
}

@Composable
private fun FnfButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE0E0E0))
            .border(1.dp, Color(0xFF8A8A8A), RoundedCornerShape(4.dp))
            .pointerInput(text) { detectTapGestures { UiSound.click(); onClick() } }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.Black, fontSize = 12.sp) }
}

@Composable
private fun FnfCheck(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = UiSound.wrapValue(onChange), enabled = enabled)
        Text(label, color = if (enabled) FnfGrid.boxText else Color(0xFF888888), fontSize = 13.sp)
    }
}

@Composable
private fun FnfStepper(label: String, value: Double, step: Double, min: Double, max: Double, decimals: Int, onValue: (Double) -> Unit) {
    fun format(v: Double): String = if (decimals == 0) v.roundToInt().toString() else two(v)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FnfButton("-") { onValue((value - step).coerceIn(min, max)) }
        Text(format(value), color = FnfGrid.boxText, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(min = 52.dp))
        FnfButton("+") { onValue((value + step).coerceIn(min, max)) }
        Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp)
    }
}

@Composable
private fun SongTab(state: ChartEditorState, onSave: () -> Unit, onReloadAudio: () -> Unit, onReloadJson: () -> Unit) {
    val doc = state.document
    TextValueField("Song", doc.meta.songName, Modifier.fillMaxWidth()) { name -> state.edit { it.copy(meta = it.meta.copy(songName = name)) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FnfButton("Save", onClick = onSave)
        FnfButton("Reload Audio", onClick = onReloadAudio)
        FnfButton("Reload JSON", onClick = onReloadJson)
    }
    FnfCheck("Hitsounds (in editor)", state.hitsounds) { state.hitsounds = it }
    FnfStepper("BPM", doc.timing.firstOrNull()?.bpm ?: 120.0, 1.0, 1.0, 999.0, 0) { bpm ->
        state.edit { it.withTiming(it.timing.mapIndexed { i, t -> if (i == 0) t.copy(bpm = bpm) else t }) }
    }
    FnfStepper("Speed", doc.difficulties[state.difficulty]?.speed ?: 1.0, 0.1, 0.1, 10.0, 1) { speed ->
        state.edit { it.withSpeed(state.difficulty, (speed * 10.0).roundToInt() / 10.0) }
    }
    TextValueField("Player 1", doc.meta.player, Modifier.fillMaxWidth()) { name -> state.edit { it.copy(meta = it.meta.copy(player = name)) } }
    TextValueField("Player 2", doc.meta.opponent, Modifier.fillMaxWidth()) { name -> state.edit { it.copy(meta = it.meta.copy(opponent = name)) } }
    Text("Difficulty", color = Color(0xFFBBBBBB), fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        doc.difficultyNames.forEach { name ->
            EditorChip(name, name == state.difficulty) {
                state.difficulty = name
                state.clearSelection()
            }
        }
    }
}

@Composable
private fun SectionTab(state: ChartEditorState) {
    var copyBack by remember { mutableIntStateOf(1) }
    val section = state.section
    Text("Section $section · ${state.sectionNotes(section).size} notes · 16 steps", color = FnfGrid.boxText, fontSize = 13.sp)
    FnfCheck("Must hit section", state.mustHit(section)) { state.setMustHit(it) }
    FnfCheck("Change BPM", state.changesBpm(section), enabled = section > 0) { state.setChangeBpm(it) }
    FnfStepper("Section BPM", state.sectionBpm(section), 1.0, 1.0, 999.0, 0) { state.setSectionBpm(it) }
    FnfCheck("Alt Animation", state.altAnim(section)) { state.setAltAnim(it) }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FnfButton("Copy last section") { state.copySection(copyBack) }
        FnfStepper("", copyBack.toDouble(), 1.0, -999.0, 999.0, 0) { copyBack = it.roundToInt() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FnfButton("Clear") { state.clearSection() }
        FnfButton("Swap section") { state.swapSection() }
    }
}

@Composable
private fun NoteTab(state: ChartEditorState) {
    val note: EditorNote? = state.selectedNote
    if (note == null) {
        Text("Click a cell to place a note, click a note to delete it, Ctrl+click (or long press) to select it.", color = Color(0xFFBBBBBB), fontSize = 13.sp)
        return
    }
    val stepMs = state.document.conductor.stepMs(note.time)
    Text("${if (note.isPlayer) "Player" else "Opponent"} · lane ${note.direction} · ${formatTime(note.time)}", color = FnfGrid.boxText, fontSize = 13.sp)
    FnfStepper("Sustain (ms)", note.length, stepMs / 2.0, 0.0, stepMs * 16.0, 0) { state.setSustain(it) }
    TextValueField("Kind", note.kind, Modifier.fillMaxWidth()) { state.setKind(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FnfButton("Delete") { state.deleteSelection() }
        FnfButton("Deselect") { state.clearSelection() }
    }
}
