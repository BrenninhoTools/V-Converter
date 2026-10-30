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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    val cellLight = Color(0xFFE4E0EC)
    val cellDark = Color(0xFFCFC9DB)
    val eventLight = Color(0xFFD9CFE3)
    val eventDark = Color(0xFFC6B9D4)
    val measure = Color(0xFF3B3550)
    val beat = Color(0xFF7A7392)
    val sub = Color(0xFFA9A2BE)
    val label = Color(0xFF3B3550)
    val divider = Color(0xFF2A2438)
    val outside = Color(0xFF15131F)
    val strumBand = Color(0xFF15131F)
    val playhead = Color(0xFFFF4F7B)
    val event = Color(0xFFFFC94A)
    val timing = Color(0xFFFF8A3D)
    val selected = Color(0xFFFFC94A)
    val draft = Color(0xFFFFFFFF)
}

const val STRUMLINE = 0.15f

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
fun EditorGrid(state: ChartEditorState, modifier: Modifier = Modifier, skin: NoteSkin? = null, eventIcon: ImageBitmap? = null) {
    val density = LocalDensity.current
    val eventLaneWidth = with(density) { 44.dp.toPx() }
    val basePxPerMs = with(density) { 0.32.dp.toPx() }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = EditorColors.label, fontSize = 10.sp)

    LaunchedEffect(state.playing) {
        while (state.playing) withFrameNanos { state.tick(it) }
    }

    fun pxPerMs() = basePxPerMs * state.zoom

    Box(
        modifier
            .onSizeChanged {
                state.gridHeightPx = it.height.toFloat()
                state.basePxPerMs = basePxPerMs
            }
            .pointerInput(state) {
                detectTapGestures { offset ->
                    val playheadY = size.height * STRUMLINE
                    val time = state.position + (offset.y - playheadY) / pxPerMs()
                    val laneWidth = (size.width - eventLaneWidth) / 8f
                    val column = if (offset.x >= size.width - eventLaneWidth) -1 else (offset.x / laneWidth).toInt().coerceIn(0, 7)
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
            val playheadY = size.height * STRUMLINE
            val lanesWidth = size.width - eventLaneWidth
            val laneWidth = lanesWidth / 8f
            val noteSize = minOf(laneWidth * 0.86f, 64.dp.toPx())
            val from = position - playheadY / pxPerMs - 200
            val to = position + (size.height - playheadY) / pxPerMs + 200
            fun y(time: Double) = (playheadY + (time - position) * pxPerMs).toFloat()
            fun laneX(column: Int) = laneWidth * column
            val eventX = lanesWidth

            drawRect(EditorColors.outside)
            val songTop = y(0.0).coerceIn(0f, size.height)
            val songBottom = y(state.songLength).coerceIn(0f, size.height)
            if (songBottom > songTop) {
                val steps = conductor.gridLines(maxOf(from, 0.0), minOf(to, state.songLength), 16)
                val edges = steps.map { it.first } + minOf(to, state.songLength)
                for (i in 0 until edges.size - 1) {
                    val top = y(edges[i]).coerceIn(songTop, songBottom)
                    val bottom = y(edges[i + 1]).coerceIn(songTop, songBottom)
                    if (bottom <= top) continue
                    val stepIndex = Math.round(conductor.beatAt(edges[i]) * 4).toInt()
                    for (column in 0 until 8) {
                        val light = (stepIndex + column) % 2 == 0
                        drawRect(if (light) EditorColors.cellLight else EditorColors.cellDark, Offset(laneX(column), top), Size(laneWidth + 0.5f, bottom - top))
                    }
                    drawRect(if (stepIndex % 2 == 0) EditorColors.eventLight else EditorColors.eventDark, Offset(eventX, top), Size(eventLaneWidth, bottom - top))
                }
            }

            conductor.gridLines(maxOf(from, 0.0), minOf(to, state.songLength), state.snap).forEach { (time, kind) ->
                val ly = y(time)
                val color = when (kind) { 2 -> EditorColors.measure; 1 -> EditorColors.beat; else -> EditorColors.sub }
                drawLine(color, Offset(0f, ly), Offset(size.width, ly), strokeWidth = when (kind) { 2 -> 2.dp.toPx(); 1 -> 1.dp.toPx(); else -> 0.6.dp.toPx() })
                if (kind == 2) {
                    val label = measurer.measure((conductor.measureAt(time) + 1).toString(), labelStyle)
                    drawText(label, topLeft = Offset(eventX + 3.dp.toPx(), ly + 1.dp.toPx()))
                }
            }
            document.timing.forEach { timing ->
                if (timing.time in from..to) {
                    val ly = y(timing.time)
                    drawLine(EditorColors.timing, Offset(0f, ly), Offset(size.width, ly), strokeWidth = 2.dp.toPx())
                    val label = measurer.measure("${trim(timing.bpm)} BPM ${timing.numerator}/4", labelStyle.copy(color = EditorColors.timing, fontSize = 9.sp))
                    drawText(label, topLeft = Offset(lanesWidth - label.size.width - 3.dp.toPx(), ly - label.size.height - 1.dp.toPx()))
                }
            }
            drawLine(EditorColors.divider, Offset(laneX(4), 0f), Offset(laneX(4), size.height), strokeWidth = 3.dp.toPx())
            drawLine(EditorColors.divider, Offset(eventX, 0f), Offset(eventX, size.height), strokeWidth = 3.dp.toPx())

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
                    } else {
                        arrow(center, noteSize, note.direction, color, Color.Black.copy(alpha = 0.55f))
                    }
                    if (note == selected) {
                        val half = noteSize / 2 + 2.dp.toPx()
                        drawRoundRect(
                            EditorColors.selected,
                            topLeft = Offset(center.x - half, center.y - half),
                            size = Size(half * 2, half * 2),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(3.dp.toPx())
                        )
                    }
                    if (note.kind.isNotEmpty()) {
                        val label = measurer.measure(note.kind.take(6), labelStyle.copy(color = Color.White, fontSize = 8.sp))
                        drawRoundRect(EditorColors.divider.copy(alpha = 0.8f), Offset(center.x - label.size.width / 2f - 3f, center.y + noteSize * 0.3f), Size(label.size.width + 6f, label.size.height.toFloat()), CornerRadius(4f))
                        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y + noteSize * 0.3f))
                    }
                }
            }

            val iconSize = minOf(eventLaneWidth * 0.82f, 36.dp.toPx())
            fun eventMark(time: Double, alpha: Float, highlight: Boolean) {
                val center = Offset(eventX + eventLaneWidth / 2, y(time))
                if (eventIcon != null) {
                    drawImage(
                        eventIcon,
                        dstOffset = IntOffset((center.x - iconSize / 2).roundToInt(), (center.y - iconSize / 2).roundToInt()),
                        dstSize = IntSize(iconSize.roundToInt(), iconSize.roundToInt()),
                        alpha = alpha,
                        filterQuality = FilterQuality.Low
                    )
                } else {
                    val d = iconSize / 2.4f
                    val path = Path().apply {
                        moveTo(center.x, center.y - d)
                        lineTo(center.x + d, center.y)
                        lineTo(center.x, center.y + d)
                        lineTo(center.x - d, center.y)
                        close()
                    }
                    drawPath(path, EditorColors.event, alpha = alpha)
                }
                if (highlight) {
                    val half = iconSize / 2 + 2.dp.toPx()
                    drawRoundRect(EditorColors.selected, Offset(center.x - half, center.y - half), Size(half * 2, half * 2), CornerRadius(6.dp.toPx()), style = Stroke(3.dp.toPx()))
                }
            }
            document.events.forEach { event -> if (event.time in from..to) eventMark(event.time, 1f, event == state.selectedEvent) }
            state.eventDraftTime?.let { eventMark(it, 0.45f, true) }

            drawRect(EditorColors.strumBand.copy(alpha = 0.35f), Offset(0f, playheadY - noteSize / 2 - 4.dp.toPx()), Size(lanesWidth, noteSize + 8.dp.toPx()))
            for (column in 0 until 8) {
                val center = Offset(laneX(column) + laneWidth / 2, playheadY)
                val receptor = skin?.receptors?.get(column % 4)
                if (skin != null && receptor != null) sprite(skin, receptor, center, noteSize, alpha = 0.9f)
                else arrow(center, noteSize, column % 4, Color.White.copy(alpha = 0.7f), null, filled = false)
            }
            drawLine(EditorColors.playhead, Offset(0f, playheadY), Offset(size.width, playheadY), strokeWidth = 2.dp.toPx())
        }
    }
}

private val kindTint = ColorFilter.tint(Color.Black.copy(alpha = 0.35f), BlendMode.SrcAtop)

internal fun DrawScope.sprite(skin: NoteSkin, frame: SkinFrame, center: Offset, size: Float, alpha: Float = 1f, colorFilter: ColorFilter? = null) {
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
