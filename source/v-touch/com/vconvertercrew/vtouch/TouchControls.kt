package com.vconvertercrew.vtouch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

val VLaneColors = listOf(Color(0xFFC24B99), Color(0xFF00FFFF), Color(0xFF12FA05), Color(0xFFF9393F))

private class PadLayout(val centers: List<Offset>, val radius: Float)

private fun padLayout(width: Float, height: Float): PadLayout {
    val radius = min(width, height) * 0.085f
    val gap = radius * 2.25f
    val cx = width - gap * 1.6f
    val cy = height - gap * 1.4f
    return PadLayout(
        listOf(Offset(cx - gap, cy), Offset(cx, cy + gap * 0.62f), Offset(cx, cy - gap * 0.62f), Offset(cx + gap, cy)),
        radius
    )
}

private fun laneAt(mode: VTouchMode, position: Offset, width: Float, height: Float): Int? = when (mode) {
    VTouchMode.HITBOX -> ((position.x / width.coerceAtLeast(1f)) * 4).toInt().coerceIn(0, 3)
    VTouchMode.PAD -> {
        val layout = padLayout(width, height)
        layout.centers.indices.minByOrNull { (layout.centers[it] - position).getDistance() }
            ?.takeIf { (layout.centers[it] - position).getDistance() <= layout.radius * 1.35f }
    }
    VTouchMode.OFF -> null
}

@Composable
fun VTouchControls(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mode: VTouchMode = VTouch.mode,
    opacity: Float = VTouch.opacity,
    colors: List<Color> = VLaneColors
) {
    if (mode == VTouchMode.OFF) return
    val held = remember { mutableStateListOf(0, 0, 0, 0) }
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    Canvas(
        modifier.fillMaxSize().pointerInput(mode) {
            val lanes = HashMap<PointerId, Int>()
            fun down(lane: Int) {
                held[lane] = held[lane] + 1
                if (held[lane] == 1) press(lane)
            }
            fun up(lane: Int) {
                held[lane] = (held[lane] - 1).coerceAtLeast(0)
                if (held[lane] == 0) release(lane)
            }
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    event.changes.forEach { change ->
                        val old = lanes[change.id]
                        if (change.pressed) {
                            val lane = laneAt(mode, change.position, w, h)
                            if (lane != old) {
                                old?.let { up(it) }
                                if (lane != null) {
                                    lanes[change.id] = lane
                                    down(lane)
                                } else {
                                    lanes.remove(change.id)
                                }
                            }
                        } else if (old != null) {
                            lanes.remove(change.id)
                            up(old)
                        }
                        change.consume()
                    }
                }
            }
        }
    ) {
        when (mode) {
            VTouchMode.HITBOX -> drawHitbox(held, colors, opacity)
            VTouchMode.PAD -> drawPad(held, colors, opacity)
            VTouchMode.OFF -> Unit
        }
    }
}

private fun DrawScope.drawHitbox(held: List<Int>, colors: List<Color>, opacity: Float) {
    val laneWidth = size.width / 4f
    val edge = laneWidth * 0.16f
    for (lane in 0 until 4) {
        val color = colors[lane]
        val left = lane * laneWidth
        val pressed = held[lane] > 0
        val alpha = if (pressed) (opacity * 2.2f).coerceAtMost(0.85f) else opacity
        drawRect(color.copy(alpha = alpha * 0.18f), Offset(left, 0f), Size(laneWidth, size.height))
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = alpha), Color.Transparent), left, left + edge), Offset(left, 0f), Size(edge, size.height))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = alpha)), left + laneWidth - edge, left + laneWidth), Offset(left + laneWidth - edge, 0f), Size(edge, size.height))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = alpha * 0.8f)), size.height * 0.75f, size.height), Offset(left, size.height * 0.75f), Size(laneWidth, size.height * 0.25f))
    }
}

private fun DrawScope.drawPad(held: List<Int>, colors: List<Color>, opacity: Float) {
    val layout = padLayout(size.width, size.height)
    val rotations = listOf(0f, 270f, 90f, 180f)
    layout.centers.forEachIndexed { lane, center ->
        val pressed = held[lane] > 0
        val alpha = if (pressed) (opacity * 2.4f).coerceAtMost(0.95f) else (opacity * 1.6f).coerceAtMost(0.8f)
        val color = colors[lane]
        drawCircle(Color.Black.copy(alpha = alpha * 0.45f), layout.radius, center)
        drawCircle(color.copy(alpha = alpha), layout.radius, center, style = Stroke(layout.radius * 0.12f))
        val arrow = Path().apply {
            val r = layout.radius * 0.5f
            moveTo(center.x - r, center.y)
            lineTo(center.x + r * 0.35f, center.y - r * 0.8f)
            lineTo(center.x + r * 0.35f, center.y + r * 0.8f)
            close()
        }
        rotate(rotations[lane], center) { drawPath(arrow, color.copy(alpha = alpha)) }
        if (pressed) drawRoundRect(color.copy(alpha = alpha * 0.25f), Offset(center.x - layout.radius, center.y - layout.radius), Size(layout.radius * 2f, layout.radius * 2f), CornerRadius(layout.radius))
    }
}
