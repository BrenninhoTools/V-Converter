package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editor.AtlasFrame
import com.brenninhotools.vconverter.editor.CharacterAnim
import com.brenninhotools.vconverter.editor.CharacterDocument
import kotlin.math.roundToInt

private val previewBackground = Color(0xFF15131F)
private val previewGrid = Color(0xFF221F33)
private val previewAxis = Color(0xFF5E5878)
private val cameraColor = Color(0xFFFFC94A)
private val ghostTint = ColorFilter.tint(Color(0xFF7FD8FF), androidx.compose.ui.graphics.BlendMode.Modulate)

private fun DrawScope.drawFrame(
    sprite: CharacterSprite,
    document: CharacterDocument,
    anim: CharacterAnim,
    frame: AtlasFrame,
    reference: AtlasFrame?,
    origin: Offset,
    zoom: Float,
    alpha: Float,
    colorFilter: ColorFilter?
) {
    val s = (document.scale.takeIf { it > 0 } ?: 1.0).toFloat()
    val refWidth = (reference ?: frame).frameWidth
    val refHeight = (reference ?: frame).frameHeight
    val left = (-s * refWidth / 2f - anim.offsetX.toFloat() * s + document.offsetX.toFloat())
    val top = (-s * refHeight - anim.offsetY.toFloat() * s + document.offsetY.toFloat())
    val dstX = origin.x + (left - frame.frameX * s) * zoom
    val dstY = origin.y + (top - frame.frameY * s) * zoom
    val dstW = frame.width * s * zoom
    val dstH = frame.height * s * zoom
    if (dstW < 0.5f || dstH < 0.5f) return
    val sheet = sprite.sheetScale
    val flipped = document.flipX != anim.flipX
    val pivot = Offset(origin.x + (left + frame.frameWidth * s / 2f) * zoom, origin.y + (top + frame.frameHeight * s / 2f) * zoom)
    scale(if (flipped) -1f else 1f, 1f, pivot) {
        drawImage(
            sprite.image,
            srcOffset = IntOffset((frame.x * sheet).roundToInt(), (frame.y * sheet).roundToInt()),
            srcSize = IntSize((frame.width * sheet).roundToInt().coerceAtLeast(1), (frame.height * sheet).roundToInt().coerceAtLeast(1)),
            dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
            dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1)),
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = if (document.isPixel) FilterQuality.None else FilterQuality.Low
        )
    }
}

@Composable
fun CharacterPreview(state: CharacterEditorState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color(0xFFB9B4D6), fontSize = 11.sp)

    LaunchedEffect(state.playing) {
        while (state.playing) withFrameNanos { state.tick(it) }
    }

    Box(
        modifier
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { if (state.dragMode != DragMode.VIEW) state.beginLiveEdit() },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() }
                ) { change, amount ->
                    change.consume()
                    state.dragBy(amount.x, amount.y)
                }
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                state.zoom = (state.zoom * if (delta > 0) 0.9f else 1.1f).coerceIn(0.05f, 8f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            state.revision
            val zoom = state.zoom
            val origin = Offset(size.width / 2f + state.panX, size.height * 0.82f + state.panY)
            drawRect(previewBackground)
            val step = 100f * zoom
            if (step >= 8f) {
                var gx = origin.x % step
                while (gx < size.width) {
                    drawLine(previewGrid, Offset(gx, 0f), Offset(gx, size.height))
                    gx += step
                }
                var gy = origin.y % step
                while (gy < size.height) {
                    drawLine(previewGrid, Offset(0f, gy), Offset(size.width, gy))
                    gy += step
                }
            }
            drawLine(previewAxis, Offset(0f, origin.y), Offset(size.width, origin.y), strokeWidth = 1.5.dp.toPx())
            drawLine(previewAxis, Offset(origin.x, 0f), Offset(origin.x, size.height), strokeWidth = 1.dp.toPx())

            val sprite = state.sprite
            val document = state.document
            val reference = state.referenceFrame
            if (sprite != null) {
                val ghostAnim = state.ghostAnimation
                val current = state.animation
                if (state.ghost && ghostAnim != null && ghostAnim != current) {
                    state.framesOf(ghostAnim).firstOrNull()?.let { drawFrame(sprite, document, ghostAnim, it, reference, origin, zoom, 0.35f, ghostTint) }
                }
                if (current != null) {
                    val frames = state.framesOf(current)
                    frames.getOrNull(state.frame.coerceIn(0, (frames.size - 1).coerceAtLeast(0)))?.let { drawFrame(sprite, document, current, it, reference, origin, zoom, 1f, null) }
                }
            } else {
                val text = measurer.measure("Open a spritesheet (.png + .xml) to preview the animations.", labelStyle)
                drawText(text, topLeft = Offset((size.width - text.size.width) / 2f, size.height / 2f - text.size.height))
            }

            if (state.showCamera) {
                val s = (document.scale.takeIf { it > 0 } ?: 1.0).toFloat()
                val refHeight = (reference?.frameHeight ?: 400) * s
                val cx = origin.x + (document.offsetX.toFloat() + document.cameraX.toFloat()) * zoom
                val cy = origin.y + (-refHeight / 2f + document.offsetY.toFloat() + document.cameraY.toFloat()) * zoom
                val r = 10.dp.toPx()
                drawCircle(cameraColor, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawLine(cameraColor, Offset(cx - r * 1.6f, cy), Offset(cx + r * 1.6f, cy), strokeWidth = 2.dp.toPx())
                drawLine(cameraColor, Offset(cx, cy - r * 1.6f), Offset(cx, cy + r * 1.6f), strokeWidth = 2.dp.toPx())
            }
            drawCircle(Color(0xFFFF4F7B), radius = 4.dp.toPx(), center = origin)

            val anim = state.animation
            val count = state.framesOf(anim).size
            val info = buildString {
                append(anim?.name ?: "No animation")
                if (anim != null) {
                    append("  ·  frame ${if (count == 0) 0 else state.frame + 1}/$count")
                    append("  ·  offset ${trim(anim.offsetX)}, ${trim(anim.offsetY)}")
                }
                append("  ·  zoom ${(zoom * 100).roundToInt()}%")
            }
            drawText(measurer.measure(info, labelStyle), topLeft = Offset(8.dp.toPx(), 6.dp.toPx()))
            if (anim != null && sprite != null && count == 0) {
                val warn = measurer.measure("No frames start with \"${anim.prefix}\"", labelStyle.copy(color = Color(0xFFFF6B6B)))
                drawText(warn, topLeft = Offset(8.dp.toPx(), 24.dp.toPx()))
            }
        }
    }
}
