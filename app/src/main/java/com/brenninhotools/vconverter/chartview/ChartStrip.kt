package com.brenninhotools.vconverter.chartview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.chart.preview.ChartPreview
import com.brenninhotools.vconverter.chart.preview.ChartSide
import kotlin.math.max
import kotlin.math.min
import com.brenninhotools.vconverter.core.fmt

data class ChartStripColors(
    val background: Color,
    val lane: Color,
    val grid: Color,
    val text: Color,
    val muted: Color,
    val left: Color,
    val down: Color,
    val up: Color,
    val right: Color,
    val opponent: Color,
    val player: Color,
    val girlfriend: Color,
    val event: Color
) {
    fun direction(index: Int): Color = when (index.mod(4)) {
        0 -> left
        1 -> down
        2 -> up
        else -> right
    }

    fun side(side: Int): Color = when (side) {
        ChartSide.PLAYER -> player
        ChartSide.GIRLFRIEND -> girlfriend
        else -> opponent
    }
}

private object Bands {
    val ruler = 16.dp
    val focus = 6.dp
    val lane = 8.dp
    val gap = 6.dp
    val events = 14.dp
}

fun chartStripHeight(preview: ChartPreview): Dp {
    val groups = if (preview.otherNotes > 0) 3 else 2
    return Bands.ruler + Bands.focus + Bands.gap + (Bands.lane * 4 + Bands.gap) * groups + Bands.events
}

@Composable
fun ChartStrip(
    preview: ChartPreview,
    colors: ChartStripColors,
    modifier: Modifier = Modifier,
    pixelsPerSecond: Float? = null
) {
    val measurer = rememberTextMeasurer()
    val beats = remember(preview) { preview.beatLines() }
    val height = chartStripHeight(preview)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val seconds = (preview.length / 1000.0).toFloat().coerceAtLeast(1f)
        val zoomed = pixelsPerSecond != null && seconds * pixelsPerSecond > maxWidth.value
        val width = if (zoomed) (seconds * pixelsPerSecond!!).dp else maxWidth
        val scroll = rememberScrollState()
        Box(if (zoomed) Modifier.fillMaxWidth().horizontalScroll(scroll) else Modifier.fillMaxWidth()) {
            Canvas(Modifier.width(width).height(height)) {
                drawStrip(preview, beats, colors, measurer)
            }
        }
    }
}

private fun DrawScope.drawStrip(preview: ChartPreview, beats: List<Pair<Double, Boolean>>, colors: ChartStripColors, measurer: TextMeasurer) {
    val length = preview.length
    fun x(time: Double): Float = (time / length * size.width).toFloat()
    drawRoundRect(colors.background, cornerRadius = CornerRadius(10.dp.toPx()))

    val ruler = Bands.ruler.toPx()
    val focusTop = ruler
    val focusHeight = Bands.focus.toPx()
    val lane = Bands.lane.toPx()
    val gap = Bands.gap.toPx()
    val groups = buildList {
        add(ChartSide.OPPONENT)
        add(ChartSide.PLAYER)
        if (preview.otherNotes > 0) add(ChartSide.GIRLFRIEND)
    }
    val laneTop = focusTop + focusHeight + gap
    val groupHeight = lane * 4 + gap
    val eventsTop = laneTop + groupHeight * groups.size
    val bottom = eventsTop + Bands.events.toPx()

    val beatSpacing = if (beats.size > 1) size.width / beats.size else size.width
    beats.forEach { (time, measure) ->
        if (!measure && beatSpacing < 4f) return@forEach
        if (measure && beatSpacing * 4 < 3f) return@forEach
        val px = x(time)
        drawLine(
            if (measure) colors.grid else colors.grid.copy(alpha = colors.grid.alpha * 0.45f),
            Offset(px, laneTop - gap / 2),
            Offset(px, eventsTop - gap / 2),
            strokeWidth = if (measure) 1.2f else 0.8f
        )
    }

    groups.forEachIndexed { index, side ->
        val top = laneTop + index * groupHeight
        drawRect(colors.lane, Offset(0f, top), Size(size.width, lane * 4))
        drawRect(colors.side(side), Offset(0f, top), Size(3.dp.toPx(), lane * 4))
    }

    val focus = preview.focus
    focus.forEachIndexed { index, change ->
        val start = x(change.time)
        val end = focus.getOrNull(index + 1)?.let { x(it.time) } ?: size.width
        drawRect(colors.side(change.side).copy(alpha = 0.85f), Offset(start, focusTop), Size(max(1f, end - start), focusHeight))
    }

    val noteWidth = max(2f, min(5.dp.toPx(), size.width / 400f))
    preview.notes.forEach { note ->
        val group = groups.indexOf(note.side).takeIf { it >= 0 } ?: return@forEach
        val top = laneTop + group * groupHeight + note.direction * lane
        val color = colors.direction(note.direction)
        val px = x(note.time)
        if (note.length > 0.0) {
            val end = x(note.time + note.length)
            drawRect(color.copy(alpha = 0.45f), Offset(px, top + lane * 0.3f), Size(max(1f, end - px), lane * 0.4f))
        }
        drawRoundRect(
            if (note.kind.isNotBlank()) color.copy(alpha = 0.55f) else color,
            Offset(px - noteWidth / 2, top + 1f),
            Size(noteWidth, lane - 2f),
            CornerRadius(1.5f)
        )
    }

    val eventY = eventsTop + Bands.events.toPx() / 2
    val diamond = 3.5.dp.toPx()
    var lastEventX = -10f
    preview.events.forEach { event ->
        val px = x(event.time)
        if (px - lastEventX < 1.5f) return@forEach
        lastEventX = px
        val path = Path().apply {
            moveTo(px, eventY - diamond)
            lineTo(px + diamond, eventY)
            lineTo(px, eventY + diamond)
            lineTo(px - diamond, eventY)
            close()
        }
        drawPath(path, colors.event)
    }

    val labelStyle = TextStyle(color = colors.text, fontSize = 9.sp)
    var lastLabelEnd = -100f
    val taken = mutableListOf<ClosedFloatingPointRange<Float>>()
    preview.timing.sortedBy { it.time }.forEachIndexed { index, change ->
        val px = x(change.time)
        if (index > 0) drawLine(colors.text.copy(alpha = 0.7f), Offset(px, 0f), Offset(px, bottom), strokeWidth = 1.5f)
        val previous = preview.timing.getOrNull(index - 1)
        val label = buildString {
            append(formatBpm(change.bpm))
            if (change.numerator != 4 || (previous != null && previous.numerator != change.numerator)) append(" · ").append(change.numerator).append("/4")
        }
        if (px < lastLabelEnd + 4f) return@forEachIndexed
        val layout = measurer.measure(label, labelStyle)
        val left = (px + 3f).coerceAtMost(size.width - layout.size.width - 2f).coerceAtLeast(2f)
        drawText(layout, topLeft = Offset(left, (ruler - layout.size.height) / 2))
        lastLabelEnd = left + layout.size.width
        taken += (left - 4f)..(lastLabelEnd + 4f)
    }

    val mutedStyle = TextStyle(color = colors.muted, fontSize = 8.sp)
    val totalSeconds = (length / 1000.0).toInt()
    val step = listOf(5, 10, 15, 30, 60, 120).firstOrNull { size.width / max(1, totalSeconds) * it > 60f } ?: 300
    var second = step
    while (second < totalSeconds) {
        val px = x(second * 1000.0)
        val text = fmt("%d:%02d", second / 60, second % 60)
        val layout = measurer.measure(text, mutedStyle)
        val left = px - layout.size.width / 2f
        val right = px + layout.size.width / 2f
        if (right + 2f < size.width && taken.none { left <= it.endInclusive && right >= it.start }) {
            drawText(layout, topLeft = Offset(left, (ruler - layout.size.height) / 2))
        }
        second += step
    }
}

fun formatBpm(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else fmt("%.2f", value).trimEnd('0').trimEnd('.')

fun formatDuration(ms: Double): String {
    val total = (ms / 1000.0).toInt().coerceAtLeast(0)
    return fmt("%d:%02d", total / 60, total % 60)
}
