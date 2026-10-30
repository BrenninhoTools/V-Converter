package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.brenninhotools.vconverter.editor.StageProp
import com.brenninhotools.vconverter.editor.StageSlot
import kotlin.math.roundToInt

private val stageBackground = Color(0xFF0F0D17)
private val spotColors = mapOf(
    StageSlot.BOYFRIEND to Color(0xFF31B0D1),
    StageSlot.DAD to Color(0xFFAF66CE),
    StageSlot.GIRLFRIEND to Color(0xFFA5004D)
)
private val selectionColor = Color(0xFFFFC94A)

internal fun solidColor(raw: String): Color {
    val hex = raw.trim().removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return Color.Gray
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color.Gray
    }
}

@Composable
fun StagePreview(state: StageEditorState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 11.sp)

    Box(
        modifier
            .pointerInput(state) {
                detectTapGestures { offset -> state.tap(offset.x, offset.y, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { offset -> state.beginDrag(offset.x, offset.y, size.width.toFloat(), size.height.toFloat()) },
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
                                state.zoom = (state.zoom * if (delta > 0) 0.9f else 1.1f).coerceIn(0.05f, 4f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            state.revision
            drawRect(stageBackground)
            val document = state.document
            val zoom = state.zoom
            fun screenX(x: Double, scroll: Double) = (size.width / 2f + (x - state.camX * (if (state.parallax) scroll else 1.0)) * zoom).toFloat()
            fun screenY(y: Double, scroll: Double) = (size.height / 2f + (y - state.camY * (if (state.parallax) scroll else 1.0)) * zoom).toFloat()

            state.drawOrder().forEach { item ->
                val rect = state.rectOf(item)
                val left = screenX(rect.left, rect.scroll)
                val top = screenY(rect.top, rect.scrollY)
                val w = (rect.width * zoom).toFloat()
                val h = (rect.height * zoom).toFloat()
                when (item) {
                    is StageSelection.Prop -> drawProp(state, document.props[item.index], left, top, w, h)
                    is StageSelection.Spot -> {
                        val color = spotColors.getValue(item.slot)
                        val spot = document.spot(item.slot)
                        drawRect(color.copy(alpha = 0.28f * spot.alpha.toFloat()), Offset(left, top), Size(w, h))
                        drawRect(color, Offset(left, top), Size(w, h), style = Stroke(2.dp.toPx()))
                        val feetX = screenX(spot.x, spot.scroll)
                        val feetY = screenY(spot.y, spot.scroll)
                        drawCircle(color, radius = 5.dp.toPx(), center = Offset(feetX, feetY))
                        val label = measurer.measure(item.slot.label.uppercase(), labelStyle.copy(color = color, fontSize = 13.sp))
                        drawText(label, topLeft = Offset(left + (w - label.size.width) / 2f, top + (h - label.size.height) / 2f))
                    }
                }
            }

            if (state.showCamera) {
                val bf = document.spot(StageSlot.BOYFRIEND)
                val dad = document.spot(StageSlot.DAD)
                listOf(StageSlot.BOYFRIEND to bf, StageSlot.DAD to dad, StageSlot.GIRLFRIEND to document.spot(StageSlot.GIRLFRIEND)).forEach { (slot, spot) ->
                    val rect = state.rectOf(slot)
                    val cx = screenX(spot.x + spot.cameraX, spot.scroll)
                    val cy = screenY(rect.top + rect.height / 2 + spot.cameraY, spot.scroll)
                    val r = 7.dp.toPx()
                    val color = spotColors.getValue(slot)
                    drawLine(color, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 2.dp.toPx())
                    drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 2.dp.toPx())
                }
                val camW = (1280.0 / document.cameraZoom * zoom).toFloat()
                val camH = (720.0 / document.cameraZoom * zoom).toFloat()
                drawRect(
                    Color.White.copy(alpha = 0.55f),
                    topLeft = Offset((size.width - camW) / 2f, (size.height - camH) / 2f),
                    size = Size(camW, camH),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                )
            }

            state.selection?.let { sel ->
                val rect = state.rectOf(sel)
                val left = screenX(rect.left, rect.scroll)
                val top = screenY(rect.top, rect.scrollY)
                drawRect(selectionColor, Offset(left, top), Size((rect.width * zoom).toFloat(), (rect.height * zoom).toFloat()), style = Stroke(2.5.dp.toPx()))
            }

            val info = buildString {
                append("zoom ${(zoom * 100).roundToInt()}%  ·  camera ${state.camX.roundToInt()}, ${state.camY.roundToInt()}")
                when (val sel = state.selection) {
                    is StageSelection.Prop -> document.props.getOrNull(sel.index)?.let { append("  ·  ${it.name} at ${trim(it.x)}, ${trim(it.y)}") }
                    is StageSelection.Spot -> document.spot(sel.slot).let { append("  ·  ${sel.slot.label} at ${trim(it.x)}, ${trim(it.y)}") }
                    null -> Unit
                }
            }
            drawText(measurer.measure(info, labelStyle.copy(color = Color(0xFFB9B4D6))), topLeft = Offset(8.dp.toPx(), 6.dp.toPx()))
        }
    }
}

internal fun DrawScope.drawProp(state: StageEditorState, prop: StageProp, left: Float, top: Float, w: Float, h: Float) {
    if (w < 0.5f || h < 0.5f) return
    val alpha = prop.alpha.toFloat().coerceIn(0f, 1f)
    val center = Offset(left + w / 2f, top + h / 2f)
    rotate(prop.angle.toFloat(), center) {
        if (prop.isSolid) {
            drawRect(solidColor(prop.assetPath), Offset(left, top), Size(w, h), alpha = alpha)
            return@rotate
        }
        val image = state.imageFor(prop)
        if (image == null) {
            drawRect(Color(0xFF3A3052), Offset(left, top), Size(w, h), alpha = 0.6f)
            drawRect(Color(0xFF7A6B9C), Offset(left, top), Size(w, h), style = Stroke(1.dp.toPx()))
            return@rotate
        }
        val frame = state.frameFor(prop, image)
        val sheet = image.sheetScale
        val srcX = frame?.x ?: 0
        val srcY = frame?.y ?: 0
        val srcW = frame?.width ?: image.width
        val srcH = frame?.height ?: image.height
        val sx = if (frame != null) w / frame.frameWidth else w / image.width
        val sy = if (frame != null) h / frame.frameHeight else h / image.height
        val dstX = left - (frame?.frameX ?: 0) * sx
        val dstY = top - (frame?.frameY ?: 0) * sy
        scale(if (prop.flipX) -1f else 1f, if (prop.flipY) -1f else 1f, center) {
            drawImage(
                image.image,
                srcOffset = IntOffset((srcX * sheet).roundToInt(), (srcY * sheet).roundToInt()),
                srcSize = IntSize((srcW * sheet).roundToInt().coerceAtLeast(1), (srcH * sheet).roundToInt().coerceAtLeast(1)),
                dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                dstSize = IntSize((srcW * sx).roundToInt().coerceAtLeast(1), (srcH * sy).roundToInt().coerceAtLeast(1)),
                alpha = alpha,
                filterQuality = if (prop.isPixel) FilterQuality.None else FilterQuality.Low
            )
        }
    }
}
