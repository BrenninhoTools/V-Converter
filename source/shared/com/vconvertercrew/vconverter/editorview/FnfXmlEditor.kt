package com.vconvertercrew.vconverter.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.vconvertercrew.vconverter.generator.SheetFrame
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private const val XML_STAGE_W = 1280f
private const val XML_STAGE_H = 720f
private const val GRID_CELL = 10f
private val xmlTabs = listOf("Images", "Animation", "Export")

@Composable
fun FnfXmlEditor(
    state: XmlEditorState,
    onAddFrames: () -> Unit,
    onAddSheet: () -> Unit,
    onSave: (XmlExport) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
    val exit by rememberUpdatedState(onExit)
    val save by rememberUpdatedState(onSave)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.playing, state.selected) {
        state.stopClock()
        while (state.playing) withFrameNanos { state.tick(it) }
    }

    fun key(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (EditorTyping.fields > 0 || event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed || event.isMetaPressed) return false
        val big = if (event.isShiftPressed) 5 else 1
        when (event.key) {
            Key.Escape -> exit()
            Key.W, Key.DirectionUp -> state.selectAnimation(state.selected - 1)
            Key.S, Key.DirectionDown -> state.selectAnimation(state.selected + 1)
            Key.A, Key.DirectionLeft -> state.step(-big)
            Key.D, Key.DirectionRight -> state.step(big)
            Key.Spacebar -> state.playing = !state.playing
            Key.Q -> state.zoom = (state.zoom / 1.1f).coerceAtLeast(0.1f)
            Key.E -> state.zoom = (state.zoom * 1.1f).coerceAtMost(8f)
            Key.R -> {
                state.zoom = 1f
                state.pan = Offset.Zero
            }
            Key.Delete -> state.removeFrame()
            Key.Tab -> state.tab = ((state.tab + if (event.isShiftPressed) -1 else 1) % xmlTabs.size + xmlTabs.size) % xmlTabs.size
            Key.Enter, Key.NumPadEnter -> state.generate { save(it) }
            else -> return false
        }
        return true
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { key(it) }
    ) {
        val outer = LocalDensity.current
        val scale = min(constraints.maxWidth / XML_STAGE_W, constraints.maxHeight / XML_STAGE_H)
        val originX = (constraints.maxWidth - XML_STAGE_W * scale) / 2f
        val originY = (constraints.maxHeight - XML_STAGE_H * scale) / 2f

        fun style(size: Float, color: Color, bold: Boolean = false) = TextStyle(
            color = color,
            fontSize = (size * scale / (outer.density * outer.fontScale)).sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectTransformGestures { _, pan, gesture, _ ->
                        runCatching { focus.requestFocus() }
                        state.pan += pan / scale
                        if (gesture != 1f) state.zoom = (state.zoom * gesture).coerceIn(0.1f, 8f)
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.firstOrNull() ?: continue
                                val factor = if (change.scrollDelta.y < 0f) 1.1f else 1f / 1.1f
                                state.zoom = (state.zoom * factor).coerceIn(0.1f, 8f)
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            val cell = GRID_CELL * scale
            val columns = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            drawRect(Color.White)
            for (row in 0 until rows) for (column in 0 until columns) {
                if ((row + column) % 2 == 1) drawRect(Flx.gridA, Offset(column * cell, row * cell), Size(cell, cell))
            }

            val entry = state.current
            val current = state.currentFrame
            val centerX = originX + (640f + state.pan.x) * scale
            val centerY = originY + (380f + state.pan.y) * scale
            val zoom = state.zoom * scale
            if (current != null && entry != null) {
                if (state.onion && entry.frames.size > 1) {
                    val previous = entry.frames[(state.frame - 1 + entry.frames.size) % entry.frames.size]
                    frameSprite(state, previous, centerX, centerY, zoom, 0.35f)
                }
                frameSprite(state, current, centerX, centerY, zoom, 1f)
                if (state.showBounds) {
                    val left = centerX - current.canvasWidth * zoom / 2f
                    val top = centerY - current.canvasHeight * zoom / 2f
                    drawRect(Color.Red, Offset(left, top), Size(current.canvasWidth * zoom, current.canvasHeight * zoom), style = Stroke(1.5f))
                }
            }

            fun text(value: String, x: Float, y: Float, size: Float, color: Color, bold: Boolean = false) {
                drawText(measurer.measure(value, style(size, color, bold)), topLeft = Offset(originX + x * scale, originY + y * scale))
            }

            if (state.animations.isEmpty()) {
                text("NO FRAMES YET", 40f, 300f, 32f, Color.Black, true)
                text("Open the Images tab and add your .png frames\nor one image that holds every frame.", 40f, 345f, 16f, Color.Black)
            }
            state.animations.forEachIndexed { index, animation ->
                val y = 20f + index * 18f
                if (y > 640f) return@forEachIndexed
                val chosen = index == state.selected
                text("${animation.name.ifBlank { "(no name)" }}: ${animation.frames.size}", 20f, y, 15f, if (chosen) Color.Blue else Color.Black, chosen)
            }
            val info = buildString {
                if (current != null && entry != null) {
                    append("Frame: ${state.frame + 1}/${entry.frames.size}   ${current.canvasWidth}x${current.canvasHeight}   ${state.fps} FPS")
                    append("\nFrom: ${current.source.name} (${current.region.x}, ${current.region.y}, ${current.region.width}x${current.region.height})\n")
                }
                append("Zoom: ${roundDecimal(state.zoom.toDouble())}x   Frames: ${state.frameCount}")
            }
            text(info, 20f, 632f, 13f, Color.Black)
            text("W/S: Animation   A/D: Frame   SPACE: Play   Q/E: Zoom   R: Reset   ENTER: Generate   ESC: Exit", 20f, 698f, 11f, Color.Black)
            state.status?.let { text(it, 650f, 432f, 13f, Color.Black) }
            if (state.busy) text("WORKING...", 650f, 456f, 16f, Color.Blue, true)
        }

        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Box(Modifier.offset { IntOffset((originX + 950f * scale).roundToInt(), (originY + 20f * scale).roundToInt()) }) {
                    FlxTabBox(xmlTabs, state.tab, { state.tab = it }, 310.dp, 400.dp) {
                        when (state.tab) {
                            0 -> ImagesGroup(state, onAddFrames, onAddSheet)
                            1 -> AnimationGroup(state)
                            else -> ExportGroup(state) { save(it) }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.frameSprite(state: XmlEditorState, frame: SheetFrame, centerX: Float, centerY: Float, zoom: Float, alpha: Float) {
    val image = state.bitmap(frame) ?: return
    val left = centerX - frame.canvasWidth * zoom / 2f + frame.canvasX * zoom
    val top = centerY - frame.canvasHeight * zoom / 2f + frame.canvasY * zoom
    drawImage(
        image,
        srcOffset = IntOffset(frame.region.x, frame.region.y),
        srcSize = IntSize(frame.region.width, frame.region.height),
        dstOffset = IntOffset(floor(left).toInt(), floor(top).toInt()),
        dstSize = IntSize((frame.region.width * zoom).roundToInt().coerceAtLeast(1), (frame.region.height * zoom).roundToInt().coerceAtLeast(1)),
        alpha = alpha
    )
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ImagesGroup(state: XmlEditorState, onAddFrames: () -> Unit, onAddSheet: () -> Unit) {
    FlxLabel("Pick every frame as a .png. Names like idle0000.png and idle0001.png become the \"idle\" animation.")
    FlxButton("Add Frames", Modifier.width(120.dp), onAddFrames)
    FlxLabel("Or pick one image with all the frames on it:")
    FlxDropDown(state.sliceMode.label, SliceMode.entries.map { it.label }) { label ->
        SliceMode.entries.firstOrNull { it.label == label }?.let { state.sliceMode = it }
    }
    when (state.sliceMode) {
        SliceMode.AUTO -> FlxCheck("Line frames up at the bottom", state.align) { state.align = it }
        SliceMode.GRID -> {
            FlxStepper(state.columns.toDouble(), 1.0, 1.0, 64.0, 0, "Columns") { state.columns = it.roundToInt() }
            FlxStepper(state.rows.toDouble(), 1.0, 1.0, 64.0, 0, "Rows") { state.rows = it.roundToInt() }
        }
        SliceMode.WHOLE -> FlxLabel("The whole image becomes one frame.")
    }
    FlxButton("Add Spritesheet", Modifier.width(120.dp), onAddSheet)
    FlxLabel("Animations: ${state.animations.size}   Frames: ${state.frameCount}")
    FlxButton("Clear All") { state.clear() }
}

@Composable
private fun AnimationGroup(state: XmlEditorState) {
    val entry = state.current
    if (entry == null) {
        FlxLabel("No animation yet. Add frames in the Images tab.")
        return
    }
    FlxLabel("Animation name (the XML prefix):")
    FlxInput(entry.name, 200) { state.renameAnimation(it) }
    FlxLabel("Animation ${state.selected + 1} of ${state.animations.size}, ${entry.frames.size} frame(s)")
    ButtonRow {
        FlxButton("< Animation") { state.selectAnimation(state.selected - 1) }
        FlxButton("Animation >") { state.selectAnimation(state.selected + 1) }
    }
    ButtonRow {
        FlxButton("Move Up") { state.moveAnimation(-1) }
        FlxButton("Move Down") { state.moveAnimation(1) }
        FlxButton("Delete") { state.removeAnimation() }
    }
    FlxLabel("Frame ${state.frame + 1} of ${entry.frames.size}")
    ButtonRow {
        FlxButton("< Frame") { state.step(-1) }
        FlxButton("Frame >") { state.step(1) }
    }
    ButtonRow {
        FlxButton("Earlier") { state.moveFrame(-1) }
        FlxButton("Later") { state.moveFrame(1) }
        FlxButton("Delete Frame") { state.removeFrame() }
    }
    FlxButton("Split Here", Modifier.width(120.dp)) { state.splitFrame() }
    FlxStepper(state.fps.toDouble(), 1.0, 1.0, 60.0, 0, "Preview FPS") { state.fps = it.roundToInt() }
    FlxCheck("Play", state.playing) { state.playing = it }
    FlxCheck("Onion skin", state.onion) { state.onion = it }
    FlxCheck("Show frame box", state.showBounds) { state.showBounds = it }
}

@Composable
private fun ExportGroup(state: XmlEditorState, onSave: (XmlExport) -> Unit) {
    FlxLabel("Sheet name (.png and .xml):")
    FlxInput(state.sheetName, 200) { state.sheetName = it }
    FlxCheck("Trim the empty borders", state.trim) { state.trim = it }
    FlxCheck("Reuse repeated frames", state.dedupe, enabled = !state.keepSheet) { state.dedupe = it }
    FlxCheck("Power of two size", state.powerOfTwo, enabled = !state.keepSheet) { state.powerOfTwo = it }
    FlxStepper(state.padding.toDouble(), 1.0, 0.0, 16.0, 0, "Padding") { state.padding = it.roundToInt() }
    FlxCheck("Keep the original image (XML only)", state.keepSheet && state.singleSource, enabled = state.singleSource) { state.keepSheet = it }
    FlxButton("Generate XML", Modifier.width(120.dp)) { state.generate(onSave) }
    state.result?.let { result ->
        FlxLabel("${result.pngName} + ${result.xmlName}\n${result.summary}")
        FlxButton("Save Again", Modifier.width(120.dp)) { onSave(result) }
    }
}
