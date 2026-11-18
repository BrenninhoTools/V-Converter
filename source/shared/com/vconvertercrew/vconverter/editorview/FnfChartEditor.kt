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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.editor.ChartDocument
import com.vconvertercrew.vconverter.editor.EditorNote
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vtouch.VContextItem
import com.vconvertercrew.vtouch.VContextMenu
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private const val STAGE_W = 1280f
private const val STAGE_H = 720f
private const val GRID_SIZE = 40f
private const val STEPS = 16
private const val GRID_W = GRID_SIZE * 8
private const val GRID_H = GRID_SIZE * STEPS

private object Flx {
    val gridA = Color(0xFFE7E6E6)
    val gridB = Color(0xFFD9D5D5)
    val chrome = Color(0xFFC8C8C8)
    val chromeDark = Color(0xFF7F7F7F)
    val tabOn = Color(0xFFE6E6E6)
    val tabOff = Color(0xFFA9A9A9)
    val button = Color(0xFFE2E2E2)
    val ink = Color.Black
}

private val legacyTabs = listOf("Song", "Section", "Note")

private val characterList = listOf(
    "bf", "dad", "gf", "spooky", "pico", "mom", "mom-car", "bf-car", "parents-christmas", "monster-christmas",
    "bf-christmas", "gf-christmas", "monster", "bf-pixel", "senpai", "senpai-angry", "spirit"
)

private fun roundDecimal(value: Double): String {
    val scaled = (value * 100.0).roundToInt()
    val whole = scaled / 100
    val part = kotlin.math.abs(scaled % 100)
    return if (part == 0) "$whole" else if (part % 10 == 0) "$whole.${part / 10}" else "$whole.${if (part < 10) "0$part" else "$part"}"
}

object ChartAutosave {
    var document: ChartDocument? = null
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
    modifier: Modifier = Modifier,
    onPlayTest: () -> Unit = {},
    onExit: () -> Unit = {},
    icon: (String) -> ImageBitmap? = { null }
) {
    val focus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    val measurer = rememberTextMeasurer()
    var mouse by remember { mutableStateOf<Offset?>(null) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var holdW by remember { mutableStateOf(false) }
    var holdS by remember { mutableStateOf(false) }
    var lastFrame by remember { mutableLongStateOf(0L) }
    var dragged by remember { mutableStateOf(0f) }
    val exit by rememberUpdatedState(onExit)
    val playTest by rememberUpdatedState(onPlayTest)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.playing) {
        while (state.playing) withFrameNanos { state.tick(it) }
    }
    LaunchedEffect(holdW, holdS) {
        lastFrame = 0L
        while (holdW || holdS) {
            withFrameNanos { frame ->
                val elapsed = if (lastFrame == 0L) 0.0 else (frame - lastFrame) / 1_000_000_000.0
                lastFrame = frame
                if (state.playing) state.togglePlay()
                val amount = 700.0 * elapsed
                state.seek((state.position + if (holdW) -amount else amount).coerceAtLeast(0.0))
            }
        }
    }

    fun stepCrochet(): Double = state.document.conductor.stepMs(state.position)

    fun key(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (EditorTyping.fields > 0) return false
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val shift = event.isShiftPressed
        if (event.type == KeyEventType.KeyUp) {
            when (event.key) {
                Key.W -> holdW = false
                Key.S -> holdS = false
                else -> return false
            }
            return true
        }
        if (event.type != KeyEventType.KeyDown || ctrl) return false
        when (event.key) {
            Key.Enter, Key.NumPadEnter -> playTest()
            Key.Escape -> exit()
            Key.E -> state.selectedNote?.let { state.setSustain(it.length + stepCrochet()) }
            Key.Q -> state.selectedNote?.let { state.setSustain(it.length - stepCrochet()) }
            Key.Tab -> state.legacyTab = ((state.legacyTab + if (shift) -1 else 1) % legacyTabs.size + legacyTabs.size) % legacyTabs.size
            Key.Spacebar -> state.togglePlay()
            Key.R -> state.resetSection(shift)
            Key.W, Key.S -> {
                if (shift) {
                    if (state.playing) state.togglePlay()
                    val amount = stepCrochet() * 2.0
                    state.seek((state.position + if (event.key == Key.W) -amount else amount).coerceAtLeast(0.0))
                } else if (event.key == Key.W) holdW = true else holdS = true
            }
            Key.D, Key.DirectionRight -> state.changeSection(if (shift) 4 else 1)
            Key.A, Key.DirectionLeft -> state.changeSection(if (shift) -4 else -1)
            else -> return false
        }
        return true
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { key(it) }
    ) {
        val outer = LocalDensity.current
        val scale = min(constraints.maxWidth / STAGE_W, constraints.maxHeight / STAGE_H)
        val originX = (constraints.maxWidth - STAGE_W * scale) / 2f
        val originY = (constraints.maxHeight - STAGE_H * scale) / 2f
        val section = state.section
        val strumY = (state.rowAt(section, state.position) * GRID_SIZE).toFloat().coerceIn(0f, GRID_H)
        val scrollX = GRID_W / 2f + GRID_W / 2f - STAGE_W / 2f
        val scrollY = strumY + 2f - STAGE_H / 2f

        fun toStage(offset: Offset) = Offset((offset.x - originX) / scale, (offset.y - originY) / scale)
        fun toWorld(stage: Offset) = Offset(stage.x + scrollX, stage.y + scrollY)
        fun inGrid(world: Offset) = world.x > 0f && world.x < GRID_W && world.y > 0f && world.y < GRID_H

        fun noteAt(world: Offset): EditorNote? = state.sectionNotes(section).firstOrNull { note ->
            val x = note.column * GRID_SIZE
            val y = floor(state.rowAt(section, note.time) * GRID_SIZE).toFloat()
            world.x >= x && world.x < x + GRID_SIZE && world.y >= y && world.y < y + GRID_SIZE
        }

        fun click(offset: Offset) {
            runCatching { focus.requestFocus() }
            val world = toWorld(toStage(offset))
            if (!inGrid(world)) return
            val modifiers = windowInfo.keyboardModifiers
            val ctrl = modifiers.isCtrlPressed || modifiers.isMetaPressed
            val existing = noteAt(world)
            if (existing != null) {
                if (ctrl) state.selectNote(existing) else state.removeNote(existing)
                return
            }
            val row = if (modifiers.isShiftPressed) world.y / GRID_SIZE else floor(world.y / GRID_SIZE)
            state.placeNote(state.timeAtRow(section, row.toDouble()), floor(world.x / GRID_SIZE).toInt().coerceIn(0, 7), ctrl)
            ChartAutosave.document = state.document
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, scale) {
                    detectTapGestures(
                        onLongPress = press@{ offset ->
                            val world = toWorld(toStage(offset))
                            val note = noteAt(world) ?: return@press
                            state.selectNote(note)
                            menuAt = offset
                        },
                        onTap = { click(it) }
                    )
                }
                .pointerInput(state, scale) {
                    detectVerticalDragGestures(onDragEnd = { dragged = 0f }) { change, amount ->
                        change.consume()
                        if (state.playing) state.togglePlay()
                        dragged += amount / scale
                        val rows = (dragged / GRID_SIZE).toInt()
                        if (rows != 0) {
                            state.seek((state.position - rows * stepCrochet()).coerceAtLeast(0.0))
                            dragged -= rows * GRID_SIZE
                        }
                    }
                }
                .pointerInput(state, scale) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Move, PointerEventType.Enter, PointerEventType.Press -> mouse = change.position
                                PointerEventType.Exit -> mouse = null
                                PointerEventType.Scroll -> {
                                    if (state.playing) state.togglePlay()
                                    state.seek((state.position + change.scrollDelta.y * stepCrochet() * 0.4).coerceAtLeast(0.0))
                                    change.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            fun sx(worldX: Float) = originX + (worldX - scrollX) * scale
            fun sy(worldY: Float) = originY + (worldY - scrollY) * scale
            val cell = GRID_SIZE * scale

            for (row in 0 until STEPS) for (column in 0 until 8) {
                drawRect(if ((row + column) % 2 == 0) Flx.gridA else Flx.gridB, Offset(sx(column * GRID_SIZE), sy(row * GRID_SIZE)), Size(cell, cell))
            }
            drawRect(Color.Black, Offset(sx(GRID_W / 2f), sy(0f)), Size(2f * scale, GRID_H * scale))

            val mustHit = state.mustHit(section)
            val doc = state.document
            listOf(doc.meta.opponent.ifBlank { "dad" } to 0f, doc.meta.player.ifBlank { "bf" } to GRID_W / 2f).forEach { (name, x) ->
                val image = icon(name)
                if (image != null) {
                    val frameWidth = if (image.width >= image.height * 2) image.width / 2 else image.width
                    val h = 45f * scale
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(frameWidth, image.height),
                        dstOffset = IntOffset(sx(x).roundToInt(), sy(-100f).roundToInt()),
                        dstSize = IntSize((h * frameWidth / image.height).roundToInt(), h.roundToInt())
                    )
                } else {
                    val text = measurer.measure(name.uppercase(), TextStyle(color = Color.White, fontSize = (20f * scale / (outer.density * outer.fontScale)).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                    drawText(text, topLeft = Offset(sx(x), sy(-100f)))
                }
            }
            val focusX = if (mustHit) GRID_W / 2f else 0f
            drawRect(Color.White.copy(alpha = 0.25f), Offset(sx(focusX), sy(-52f)), Size(GRID_W / 2f * scale, 4f * scale))

            val step = stepCrochet()
            state.sectionNotes(section).forEach { note ->
                val x = note.column * GRID_SIZE
                val y = floor(state.rowAt(section, note.time) * GRID_SIZE).toFloat()
                val frame = skin?.notes?.get(note.direction)
                val center = Offset(sx(x + GRID_SIZE / 2f), sy(y + GRID_SIZE / 2f))
                if (skin != null && frame != null) sprite(skin, frame, center, cell)
                else arrow(center, cell * 0.8f, note.direction, EditorColors.lanes[note.direction], Color.Black)
                if (note.length > 0.0) {
                    val height = (note.length / (step * STEPS) * GRID_H).toFloat()
                    drawRect(Color.White, Offset(sx(x + GRID_SIZE / 2f), sy(y + GRID_SIZE)), Size(8f * scale, height * scale))
                }
                if (note == state.selectedNote) drawRect(Color.White, Offset(sx(x), sy(y)), Size(cell, cell), style = Stroke(2f * scale))
            }

            mouse?.let { position ->
                val world = toWorld(toStage(position))
                if (inGrid(world)) {
                    val shift = windowInfo.keyboardModifiers.isShiftPressed
                    val ax = floor(world.x / GRID_SIZE) * GRID_SIZE
                    val ay = if (shift) world.y else floor(world.y / GRID_SIZE) * GRID_SIZE
                    drawRect(Color.White, Offset(sx(ax), sy(ay)), Size(cell, cell))
                }
            }

            drawRect(Color.White, Offset(sx(0f), sy(strumY)), Size(STAGE_W / 2f * scale, 4f * scale))

            val info = "${roundDecimal(state.position / 1000.0)} / ${roundDecimal(state.songLength / 1000.0)}\nSection: $section"
            val bpmTxt = measurer.measure(info, TextStyle(color = Color.White, fontSize = (16f * scale / (outer.density * outer.fontScale)).sp, fontFamily = FontFamily.Monospace))
            drawText(bpmTxt, topLeft = Offset(originX + 1000f * scale, originY + 50f * scale))
        }

        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Box(Modifier.offset { IntOffset((originX + 640f * scale).roundToInt(), (originY + 20f * scale).roundToInt()) }) {
                    UiBox(state, onSave, onReloadAudio, onReloadJson)
                }
            }
        }

        VContextMenu(
            menuAt,
            listOfNotNull(
                VContextItem("Delete") { state.deleteSelection() },
                VContextItem("Longer Hold") { state.selectedNote?.let { state.setSustain(it.length + stepCrochet()) } },
                VContextItem("Shorter Hold") { state.selectedNote?.let { state.setSustain(it.length - stepCrochet()) } },
                VContextItem("Deselect") { state.clearSelection() }
            ),
            onDismiss = { menuAt = null }
        )
    }
}

@Composable
private fun UiBox(state: ChartEditorState, onSave: () -> Unit, onReloadAudio: () -> Unit, onReloadJson: () -> Unit) {
    Column(Modifier.size(300.dp, 400.dp)) {
        Row(Modifier.fillMaxWidth().height(20.dp)) {
            legacyTabs.forEachIndexed { index, title ->
                val active = index == state.legacyTab
                Box(
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .background(if (active) Flx.tabOn else Flx.tabOff)
                        .border(1.dp, Flx.chromeDark)
                        .pointerInput(index) {
                            detectTapGestures {
                                UiSound.click()
                                state.legacyTab = index
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text(title, color = Flx.ink, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Flx.chrome)
                .border(1.dp, Flx.chromeDark)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (state.legacyTab) {
                0 -> SongGroup(state, onSave, onReloadAudio, onReloadJson)
                1 -> SectionGroup(state)
                else -> NoteGroup(state)
            }
        }
    }
}

@Composable
private fun FlxLabel(text: String) {
    Text(text, color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun FlxButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(80.dp)
            .heightIn(min = 20.dp)
            .background(Flx.button)
            .border(1.dp, Flx.chromeDark)
            .pointerInput(text) {
                detectTapGestures {
                    UiSound.click()
                    onClick()
                }
            }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Flx.ink, fontSize = 8.sp, fontFamily = FontFamily.Monospace) }
}

@Composable
private fun FlxInput(value: String, width: Int, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        singleLine = true,
        textStyle = TextStyle(color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Flx.ink),
        modifier = Modifier.width(width.dp).background(Color.White).border(1.dp, Flx.ink).padding(2.dp).typingField()
    )
}

@Composable
private fun FlxCheck(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.pointerInput(checked, enabled) {
            detectTapGestures {
                if (enabled) {
                    UiSound.click()
                    onChange(!checked)
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(11.dp).background(Color.White).border(1.dp, Flx.ink), contentAlignment = Alignment.Center) {
            if (checked) Box(Modifier.size(7.dp).background(if (enabled) Flx.ink else Flx.chromeDark))
        }
        Text(label, color = if (enabled) Flx.ink else Flx.chromeDark, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FlxStepper(value: Double, step: Double, min: Double, max: Double, decimals: Int, label: String, onValue: (Double) -> Unit) {
    fun format(v: Double) = if (decimals == 0) v.roundToInt().toString() else roundDecimal(v)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.width(40.dp).background(Color.White).border(1.dp, Flx.ink).padding(2.dp)) {
            Text(format(value), color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        FlxButton("+", Modifier.width(14.dp)) { onValue((value + step).coerceIn(min, max)) }
        FlxButton("-", Modifier.width(14.dp)) { onValue((value - step).coerceIn(min, max)) }
        if (label.isNotEmpty()) FlxLabel(label)
    }
}

@Composable
private fun FlxDropDown(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.width(120.dp).background(Color.White).border(1.dp, Flx.ink).pointerInput(selected) {
                detectTapGestures {
                    UiSound.click()
                    open = true
                }
            }.padding(2.dp)
        ) { Text("$selected ▾", color = Flx.ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        UiSound.click()
                        open = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SongGroup(state: ChartEditorState, onSave: () -> Unit, onReloadAudio: () -> Unit, onReloadJson: () -> Unit) {
    val doc = state.document
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlxInput(doc.meta.songName, 90) { name -> state.edit { it.copy(meta = it.meta.copy(songName = name)) } }
            FlxCheck("Hitsounds (in editor)", state.hitsounds) { state.hitsounds = it }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FlxButton("Save", onClick = onSave)
                FlxButton("Reload Audio", onClick = onReloadAudio)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FlxButton("Reload JSON", onClick = onReloadJson)
                FlxButton("load autosave") {
                    ChartAutosave.document?.let { saved -> state.edit { saved } }
                }
            }
        }
    }
    FlxStepper(doc.timing.firstOrNull()?.bpm ?: 120.0, 1.0, 1.0, 339.0, 0, "BPM") { bpm ->
        state.edit { it.withTiming(it.timing.mapIndexed { i, t -> if (i == 0) t.copy(bpm = bpm) else t }) }
    }
    FlxStepper(doc.difficulties[state.difficulty]?.speed ?: 1.0, 0.1, 0.1, 10.0, 1, "Speed") { speed ->
        state.edit { it.withSpeed(state.difficulty, (speed * 10.0).roundToInt() / 10.0) }
    }
    val characters = (characterList + listOf(doc.meta.player, doc.meta.opponent)).filter { it.isNotBlank() }.distinct()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlxDropDown(doc.meta.player.ifBlank { "bf" }, characters) { name -> state.edit { it.copy(meta = it.meta.copy(player = name)) } }
        FlxDropDown(doc.meta.opponent.ifBlank { "dad" }, characters) { name -> state.edit { it.copy(meta = it.meta.copy(opponent = name)) } }
    }
    if (doc.difficultyNames.size > 1) {
        FlxLabel("Difficulty")
        FlxDropDown(state.difficulty, doc.difficultyNames) { name ->
            state.difficulty = name
            state.clearSelection()
        }
    }
}

@Composable
private fun SectionGroup(state: ChartEditorState) {
    var copyBack by remember { mutableIntStateOf(1) }
    val section = state.section
    FlxStepper(STEPS.toDouble(), 4.0, 16.0, 16.0, 0, "Section length") {}
    FlxCheck("Must hit section", state.mustHit(section)) { state.setMustHit(it) }
    FlxCheck("Change BPM", state.changesBpm(section), enabled = section > 0) { state.setChangeBpm(it) }
    FlxStepper(state.sectionBpm(section), 1.0, 1.0, 999.0, 0, "Section BPM") { state.setSectionBpm(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        FlxButton("Copy last section") { state.copySection(copyBack) }
        FlxStepper(copyBack.toDouble(), 1.0, -999.0, 999.0, 0, "") { copyBack = it.roundToInt() }
    }
    FlxButton("Clear") { state.clearSection() }
    FlxButton("Swap section") { state.swapSection() }
    FlxCheck("Alt Animation", state.altAnim(section)) { state.setAltAnim(it) }
}

@Composable
private fun NoteGroup(state: ChartEditorState) {
    val note = state.selectedNote
    val step = state.document.conductor.stepMs(note?.time ?: state.position)
    var pending by remember(note) { mutableStateOf(note?.length ?: 0.0) }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        FlxStepper(pending, step / 2.0, 0.0, step * 16.0, 0, "") { pending = it }
        FlxButton("Apply") { state.setSustain(pending) }
    }
    if (note == null) FlxLabel("Ctrl+click a note (or long press it) to select it.")
}
