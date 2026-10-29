package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editor.EditorNote
import kotlin.math.max
import kotlin.math.roundToInt

object EditorColors {
    val lanes = listOf(Color(0xFFC24B99), Color(0xFF00FFFF), Color(0xFF12FA05), Color(0xFFF9393F))
    val background = Color(0xFF15131F)
    val laneDark = Color(0xFF1D1A2B)
    val laneLight = Color(0xFF221F33)
    val eventLane = Color(0xFF2A1F2E)
    val measure = Color(0xFFB9B4D6)
    val beat = Color(0xFF5E5878)
    val sub = Color(0xFF332F47)
    val playhead = Color(0xFFFF4F7B)
    val event = Color(0xFFFFC94A)
    val draft = Color(0xFFFFFFFF)
}

private val arrowPath: Path = Path().apply {
    moveTo(0f, -0.46f)
    lineTo(0.44f, 0.02f)
    lineTo(0.17f, 0.02f)
    lineTo(0.17f, 0.42f)
    lineTo(-0.17f, 0.42f)
    lineTo(-0.17f, 0.02f)
    lineTo(-0.44f, 0.02f)
    close()
}

private val arrowRotation = floatArrayOf(-90f, 180f, 0f, 90f)

private fun DrawScope.arrow(center: Offset, size: Float, direction: Int, color: Color, outline: Color?, filled: Boolean = true) {
    translate(center.x, center.y) {
        rotate(arrowRotation[direction], pivot = Offset.Zero) {
            scale(size, size, pivot = Offset.Zero) {
                if (filled) drawPath(arrowPath, color) else drawPath(arrowPath, color, style = Stroke(width = 0.06f))
                if (outline != null) drawPath(arrowPath, outline, style = Stroke(width = 0.08f))
            }
        }
    }
}

@Composable
fun EditorGrid(state: ChartEditorState, modifier: Modifier = Modifier, skin: NoteSkin? = null) {
    val density = LocalDensity.current
    val eventLaneWidth = with(density) { 34.dp.toPx() }
    val basePxPerMs = with(density) { 0.32.dp.toPx() }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = EditorColors.measure, fontSize = 10.sp)

    LaunchedEffect(state.playing) {
        while (state.playing) withFrameNanos { state.tick(it) }
    }

    fun pxPerMs() = basePxPerMs * state.zoom

    Box(
        modifier
            .pointerInput(state) {
                detectTapGestures { offset ->
                    val playheadY = size.height * 0.3f
                    val time = state.position + (offset.y - playheadY) / pxPerMs()
                    val laneWidth = (size.width - eventLaneWidth) / 8f
                    val column = if (offset.x < eventLaneWidth) -1 else ((offset.x - eventLaneWidth) / laneWidth).toInt().coerceIn(0, 7)
                    if (time >= 0) state.tap(time, column)
                }
            }
            .pointerInput(state) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    if (state.playing) state.togglePlay()
                    state.scrollBy((-dragAmount / pxPerMs()).toDouble())
                }
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                val step = state.document.conductor.stepMs(state.position) * 16.0 / state.snap
                                state.seek(state.document.conductor.snap(state.position + delta * step, state.snap))
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            state.revision
            val document = state.document
            val conductor = document.conductor
            val position = state.position
            val pxPerMs = pxPerMs()
            val playheadY = size.height * 0.3f
            val laneWidth = (size.width - eventLaneWidth) / 8f
            val noteSize = minOf(laneWidth * 0.86f, 64.dp.toPx())
            val from = position - playheadY / pxPerMs - 200
            val to = position + (size.height - playheadY) / pxPerMs + 200
            fun y(time: Double) = (playheadY + (time - position) * pxPerMs).toFloat()
            fun laneX(column: Int) = eventLaneWidth + laneWidth * column

            drawRect(EditorColors.background)
            drawRect(EditorColors.eventLane, size = Size(eventLaneWidth, size.height))
            for (column in 0 until 8) {
                drawRect(if (column % 2 == 0) EditorColors.laneDark else EditorColors.laneLight, Offset(laneX(column), 0f), Size(laneWidth, size.height))
            }
            val songTop = y(0.0)
            if (songTop > 0) drawRect(Color.Black.copy(alpha = 0.45f), size = Size(size.width, songTop))
            val songEnd = y(state.songLength)
            if (songEnd < size.height) drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, songEnd), Size(size.width, size.height - songEnd))

            conductor.gridLines(maxOf(from, 0.0), minOf(to, state.songLength), state.snap).forEach { (time, kind) ->
                val ly = y(time)
                val color = when (kind) { 2 -> EditorColors.measure; 1 -> EditorColors.beat; else -> EditorColors.sub }
                drawLine(color, Offset(eventLaneWidth, ly), Offset(size.width, ly), strokeWidth = if (kind == 2) 2.dp.toPx() else 1.dp.toPx())
                if (kind == 2) {
                    val label = measurer.measure((conductor.measureAt(time) + 1).toString(), labelStyle)
                    drawText(label, topLeft = Offset(4.dp.toPx(), ly - label.size.height - 1.dp.toPx()))
                }
            }
            document.timing.forEach { timing ->
                if (timing.time in from..to) {
                    val ly = y(timing.time)
                    drawLine(EditorColors.event.copy(alpha = 0.6f), Offset(0f, ly), Offset(size.width, ly), strokeWidth = 1.dp.toPx())
                    val label = measurer.measure("${trim(timing.bpm)} BPM ${timing.numerator}/4", labelStyle.copy(color = EditorColors.event))
                    drawText(label, topLeft = Offset(size.width - label.size.width - 4.dp.toPx(), ly + 2.dp.toPx()))
                }
            }
            drawLine(Color.White.copy(alpha = 0.35f), Offset(laneX(4), 0f), Offset(laneX(4), size.height), strokeWidth = 2.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.2f), Offset(eventLaneWidth, 0f), Offset(eventLaneWidth, size.height), strokeWidth = 1.dp.toPx())

            for (column in 0 until 8) {
                val center = Offset(laneX(column) + laneWidth / 2, playheadY)
                val receptor = skin?.receptors?.get(column % 4)
                if (skin != null && receptor != null) sprite(skin, receptor, center, noteSize, alpha = 0.55f)
                else arrow(center, noteSize, column % 4, Color.White.copy(alpha = 0.18f), null, filled = false)
            }

            val selected = state.selectedNote
            val notes = document.notes(state.difficulty)
            notes.forEach { note -> if (note.length > 0 && note.time <= to && note.time + note.length >= from) drawHold(note, laneX(note.column), laneWidth, noteSize, skin, ::y) }
            notes.forEach { note ->
                if (note.time in from..to) {
                    val color = EditorColors.lanes[note.direction].let { if (note.kind.isNotEmpty()) it.copy(red = it.red * 0.7f, green = it.green * 0.7f, blue = it.blue * 0.7f) else it }
                    val center = Offset(laneX(note.column) + laneWidth / 2, y(note.time))
                    val frame = skin?.notes?.get(note.direction)
                    if (skin != null && frame != null) {
                        sprite(skin, frame, center, noteSize, colorFilter = if (note.kind.isNotEmpty()) kindTint else null)
                        if (note == selected) {
                            val half = noteSize / 2 + 2.dp.toPx()
                            drawRoundRect(
                                Color.White,
                                topLeft = Offset(center.x - half, center.y - half),
                                size = Size(half * 2, half * 2),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                                style = Stroke(2.5.dp.toPx())
                            )
                        }
                    } else {
                        arrow(center, noteSize, note.direction, color, if (note == selected) Color.White else Color.Black.copy(alpha = 0.55f))
                    }
                    if (note.kind.isNotEmpty()) {
                        val label = measurer.measure(note.kind.take(6), labelStyle.copy(color = Color.White, fontSize = 8.sp))
                        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + noteSize * 0.3f))
                    }
                }
            }

            val diamond = 9.dp.toPx()
            document.events.forEach { event ->
                if (event.time in from..to) {
                    val center = Offset(eventLaneWidth / 2, y(event.time))
                    val path = Path().apply {
                        moveTo(center.x, center.y - diamond)
                        lineTo(center.x + diamond, center.y)
                        lineTo(center.x, center.y + diamond)
                        lineTo(center.x - diamond, center.y)
                        close()
                    }
                    drawPath(path, EditorColors.event)
                    if (event == state.selectedEvent) drawPath(path, Color.White, style = Stroke(2.dp.toPx()))
                }
            }
            state.eventDraftTime?.let { time ->
                val ly = y(time)
                drawCircle(EditorColors.draft, radius = 5.dp.toPx(), center = Offset(eventLaneWidth / 2, ly), style = Stroke(2.dp.toPx()))
            }

            drawLine(EditorColors.playhead, Offset(0f, playheadY), Offset(size.width, playheadY), strokeWidth = 2.dp.toPx())
        }
    }
}

private val kindTint = ColorFilter.tint(Color.Black.copy(alpha = 0.35f), BlendMode.SrcAtop)

private fun DrawScope.sprite(skin: NoteSkin, frame: SkinFrame, center: Offset, size: Float, alpha: Float = 1f, colorFilter: ColorFilter? = null) {
    val scale = size / max(frame.size.width, frame.size.height)
    val width = frame.size.width * scale
    val height = frame.size.height * scale
    drawImage(
        skin.image,
        srcOffset = frame.offset,
        srcSize = frame.size,
        dstOffset = IntOffset((center.x - width / 2).roundToInt(), (center.y - height / 2).roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = FilterQuality.Low
    )
}

private fun DrawScope.drawHold(note: EditorNote, x: Float, laneWidth: Float, noteSize: Float, skin: NoteSkin?, y: (Double) -> Float) {
    val top = y(note.time)
    val bottom = y(note.time + note.length)
    val centerX = x + laneWidth / 2
    val piece = skin?.holdPieces?.get(note.direction)
    val end = skin?.holdEnds?.get(note.direction)
    val head = skin?.notes?.get(note.direction)
    if (skin != null && piece != null && end != null && head != null) {
        val scale = noteSize / max(head.size.width, head.size.height)
        val width = (piece.size.width * scale).roundToInt().coerceAtLeast(1)
        val endHeight = end.size.height * scale
        val left = (centerX - width / 2f).roundToInt()
        val endTop = max(top, bottom - endHeight)
        if (endTop > top) {
            drawImage(
                skin.image,
                srcOffset = IntOffset(piece.offset.x, piece.offset.y + 1),
                srcSize = IntSize(piece.size.width, (piece.size.height - 2).coerceAtLeast(1)),
                dstOffset = IntOffset(left, top.roundToInt()),
                dstSize = IntSize(width, (endTop - top).roundToInt().coerceAtLeast(1)),
                alpha = 0.9f,
                filterQuality = FilterQuality.Low
            )
        }
        val shown = (bottom - endTop).roundToInt().coerceAtLeast(1)
        val srcHeight = (shown / scale).roundToInt().coerceIn(1, end.size.height)
        drawImage(
            skin.image,
            srcOffset = IntOffset(end.offset.x, end.offset.y + end.size.height - srcHeight),
            srcSize = IntSize(end.size.width, srcHeight),
            dstOffset = IntOffset(left, endTop.roundToInt()),
            dstSize = IntSize(width, shown),
            alpha = 0.9f,
            filterQuality = FilterQuality.Low
        )
        return
    }
    val width = noteSize * 0.3f
    drawRoundRect(
        EditorColors.lanes[note.direction].copy(alpha = 0.75f),
        topLeft = Offset(centerX - width / 2, top),
        size = Size(width, (bottom - top).coerceAtLeast(1f)),
        cornerRadius = CornerRadius(width / 2, width / 2)
    )
}

internal fun trim(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')
