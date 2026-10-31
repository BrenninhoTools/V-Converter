package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.editor.WeekDocument
import com.brenninhotools.vconverter.editor.WeekSlot
import kotlin.math.min
import kotlin.math.roundToInt

private const val MENU_WIDTH = 1280f
private const val MENU_HEIGHT = 720f
private val tracksColor = Color(0xFFE55777)
private val slotColors = mapOf(WeekSlot.DAD to Color(0xFFAF66CE), WeekSlot.BF to Color(0xFF31B0D1), WeekSlot.GF to Color(0xFFA5004D))
private val pickColor = Color(0xFFFFC94A)

private fun parseColor(raw: String): Color {
    val hex = raw.trim().removePrefix("#").removePrefix("0x")
    val value = hex.toLongOrNull(16) ?: return Color(0xFFF9CF51)
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color(0xFFF9CF51)
    }
}

private class MenuSpace(val k: Float, val ox: Float, val oy: Float) {
    fun x(v: Double) = ox + v.toFloat() * k
    fun y(v: Double) = oy + v.toFloat() * k
    fun worldX(sx: Float) = ((sx - ox) / k).toDouble()
    fun worldY(sy: Float) = ((sy - oy) / k).toDouble()
}

private fun space(width: Float, height: Float): MenuSpace {
    val k = min(width / MENU_WIDTH, height / MENU_HEIGHT)
    return MenuSpace(k, (width - MENU_WIDTH * k) / 2f, (height - MENU_HEIGHT * k) / 2f)
}

@Composable
fun WeekPreview(state: WeekEditorState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()

    LaunchedEffect(state.playing) {
        if (state.playing) {
            while (true) withFrameNanos { state.tick(it) }
        } else {
            state.stopTicking()
        }
    }

    Box(
        modifier
            .pointerInput(state) {
                detectTapGestures { offset ->
                    val m = space(size.width.toFloat(), size.height.toFloat())
                    state.tap(m.worldX(offset.x), m.worldY(offset.y))
                }
            }
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val m = space(size.width.toFloat(), size.height.toFloat())
                        state.beginDrag(m.worldX(offset.x), m.worldY(offset.y))
                    },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() }
                ) { change, amount ->
                    change.consume()
                    val m = space(size.width.toFloat(), size.height.toFloat())
                    state.dragBy((amount.x / m.k).toDouble(), (amount.y / m.k).toDouble())
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            state.revision
            state.time
            val doc = state.document
            val m = space(size.width, size.height)
            drawRect(Color(0xFF0B0A10))
            clipRect(m.ox, m.oy, m.ox + MENU_WIDTH * m.k, m.oy + MENU_HEIGHT * m.k) {
                drawRect(Color.Black, Offset(m.ox, m.oy), Size(MENU_WIDTH * m.k, MENU_HEIGHT * m.k))
                drawBackground(state, doc, m)
                WeekSlot.entries.forEach { slot -> drawMenuProp(state, slot, m, measurer) }
                drawTexts(state, doc, m, measurer)
                state.selected?.let { slot ->
                    state.rectOf(slot)?.let { r ->
                        drawRect(pickColor, Offset(m.x(r.left), m.y(r.top)), Size((r.width * m.k).toFloat(), (r.height * m.k).toFloat()), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            drawRect(Color.White.copy(alpha = 0.25f), Offset(m.ox, m.oy), Size(MENU_WIDTH * m.k, MENU_HEIGHT * m.k), style = Stroke(1.dp.toPx()))
        }
    }
}

private fun DrawScope.drawBackground(state: WeekEditorState, doc: WeekDocument, m: MenuSpace) {
    if (doc.backgroundIsColor) {
        drawRect(parseColor(doc.background), Offset(m.x(0.0), m.y(56.0)), Size(MENU_WIDTH * m.k, 400f * m.k))
        return
    }
    val image = state.imageFor(doc.background)
    if (image == null) {
        drawRect(Color(0xFF3A3052), Offset(m.x(0.0), m.y(56.0)), Size(MENU_WIDTH * m.k, 400f * m.k))
        return
    }
    drawImage(
        image.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize((image.width * image.sheetScale).roundToInt().coerceAtLeast(1), (image.height * image.sheetScale).roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset(m.x(0.0).roundToInt(), m.y(56.0).roundToInt()),
        dstSize = IntSize((image.width * m.k).roundToInt().coerceAtLeast(1), (image.height * m.k).roundToInt().coerceAtLeast(1))
    )
}

private fun DrawScope.drawMenuProp(state: WeekEditorState, slot: WeekSlot, m: MenuSpace, measurer: TextMeasurer) {
    val prop = state.document.prop(slot) ?: return
    val rect = state.rectOf(slot) ?: return
    val left = m.x(rect.left)
    val top = m.y(rect.top)
    val w = (rect.width * m.k).toFloat()
    val h = (rect.height * m.k).toFloat()
    val frame = state.frameFor(slot)
    if (frame == null) {
        val color = slotColors.getValue(slot)
        drawRect(color.copy(alpha = 0.3f), Offset(left, top), Size(w, h))
        drawRect(color, Offset(left, top), Size(w, h), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
        val label = if (prop.isBase) "BASE ${slot.key.uppercase()}" else prop.assetPath.substringAfterLast('/').uppercase()
        val text = measurer.measure(label, TextStyle(color = Color.White, fontSize = (22f * m.k).toSp(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
        drawText(text, topLeft = Offset(left + (w - text.size.width) / 2f, top + (h - text.size.height) / 2f))
        return
    }
    val image = frame.image
    val sheet = image.sheetScale
    val f = frame.frame
    val s = (prop.scale * (if (prop.isPixel) 6.0 else 1.0)).toFloat() * m.k
    val srcX = f?.x ?: 0
    val srcY = f?.y ?: 0
    val srcW = f?.width ?: image.width
    val srcH = f?.height ?: image.height
    val dstX = left - (f?.frameX ?: 0) * s
    val dstY = top - (f?.frameY ?: 0) * s
    scale(if (prop.flipX) -1f else 1f, 1f, Offset(left + w / 2f, top + h / 2f)) {
        drawImage(
            image.image,
            srcOffset = IntOffset((srcX * sheet).roundToInt(), (srcY * sheet).roundToInt()),
            srcSize = IntSize((srcW * sheet).roundToInt().coerceAtLeast(1), (srcH * sheet).roundToInt().coerceAtLeast(1)),
            dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
            dstSize = IntSize((srcW * s).roundToInt().coerceAtLeast(1), (srcH * s).roundToInt().coerceAtLeast(1)),
            filterQuality = if (prop.isPixel) FilterQuality.None else FilterQuality.Low
        )
    }
}

private fun DrawScope.drawTexts(state: WeekEditorState, doc: WeekDocument, m: MenuSpace, measurer: TextMeasurer) {
    fun style(size: Float, color: Color = Color.White, align: TextAlign = TextAlign.Start) =
        TextStyle(color = color, fontSize = (size * m.k).toSp(), fontFamily = FontFamily.Monospace, textAlign = align, lineHeight = (size * 1.15f * m.k).toSp())

    drawText(measurer.measure("LEVEL SCORE: 0", style(32f)), topLeft = Offset(m.x(10.0), m.y(10.0)))
    val name = measurer.measure(doc.storyName, style(32f, Color.White.copy(alpha = 0.7f)))
    drawText(name, topLeft = Offset(m.x(1270.0) - name.size.width, m.y(10.0)))

    val title = state.imageFor(doc.title)
    val titleTop = 480.0
    if (title != null) {
        val tw = title.width * m.k
        val th = title.height * m.k
        drawImage(
            title.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize((title.width * title.sheetScale).roundToInt().coerceAtLeast(1), (title.height * title.sheetScale).roundToInt().coerceAtLeast(1)),
            dstOffset = IntOffset((m.x(640.0) - tw / 2f).roundToInt(), m.y(titleTop).roundToInt()),
            dstSize = IntSize(tw.roundToInt().coerceAtLeast(1), th.roundToInt().coerceAtLeast(1))
        )
    } else {
        val text = measurer.measure(doc.storyName.uppercase(), style(44f).copy(fontWeight = FontWeight.Bold))
        drawText(text, topLeft = Offset(m.x(640.0) - text.size.width / 2f, m.y(titleTop)))
    }
    if (doc.isLocked) {
        val lock = measurer.measure("LOCKED · beat ${doc.weekBefore.trim()}", style(20f, Color(0xFFB9B4D6)))
        drawText(lock, topLeft = Offset(m.x(640.0) - lock.size.width / 2f, m.y(titleTop + 70)))
    }

    val tracks = "TRACKS\n\n" + doc.songs.joinToString("\n") { it.displayName.uppercase() }
    val tracksText = measurer.measure(tracks, style(32f, tracksColor, TextAlign.Center))
    drawText(tracksText, topLeft = Offset(m.x(640.0 - 1280 * 0.33) - tracksText.size.width / 2f, m.y(500.0)))

    val diff = measurer.measure(state.currentDifficulty().uppercase(), style(40f).copy(fontWeight = FontWeight.Bold))
    val arrowStyle = style(40f, Color.White)
    val leftArrow = measurer.measure("◀", arrowStyle)
    val rightArrow = measurer.measure("▶", arrowStyle)
    drawText(leftArrow, topLeft = Offset(m.x(870.0), m.y(480.0)))
    drawText(rightArrow, topLeft = Offset(m.x(1245.0) - rightArrow.size.width, m.y(480.0)))
    drawText(diff, topLeft = Offset(m.x(1060.0) - diff.size.width / 2f, m.y(484.0)))

    val notes = listOfNotNull(
        if (!doc.visible) "Hidden from story mode" else null,
        if (doc.hideFreeplay) "Songs hidden from freeplay" else null,
        if (doc.isLocked && doc.hiddenUntilUnlocked) "Hidden until unlocked" else null
    )
    if (notes.isNotEmpty()) {
        val text = measurer.measure(notes.joinToString("  ·  "), style(18f, Color(0xFFFFC94A)))
        drawText(text, topLeft = Offset(m.x(10.0), m.y(700.0) - text.size.height))
    }
}
