package com.vconvertercrew.vtouch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

val VLaneColors = listOf(Color(0xFFC24B99), Color(0xFF00FFFF), Color(0xFF12FA05), Color(0xFFF9393F))

val VManiaColors = listOf(
    Color(0xFFC24B99), Color(0xFF00FFFF), Color(0xFF12FA05), Color(0xFFF9393F), Color(0xFFCCCCCC),
    Color(0xFFFFFF00), Color(0xFF8B4AFF), Color(0xFFFF0000), Color(0xFF0033FF)
)

fun laneColors(lanes: Int): List<Color> = if (lanes == 4) VLaneColors else List(lanes) { VManiaColors[it % VManiaColors.size] }

@Composable
fun VTouchControls(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mode: VTouchMode = VTouch.mode,
    opacity: Float = VTouch.opacity,
    lanes: Int = 4,
    colors: List<Color> = laneColors(lanes)
) {
    if (mode == VTouchMode.OFF) return
    val density = LocalDensity.current.density
    val config = VTouchConfig(density = density, lanes = lanes, layout = mode.layout, features = VTouchFeature.LANES)
    val engine = remember { VTouchEngines.create(config) }
    val held = remember(lanes) { mutableStateListOf(*Array(lanes) { 0 }) }
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)

    fun flush() {
        engine.drain { touch ->
            when (touch.type) {
                VTouchEventType.LANE_PRESS -> {
                    if (touch.lane in held.indices) held[touch.lane] = 1
                    press(touch.lane)
                }
                VTouchEventType.LANE_RELEASE -> {
                    if (touch.lane in held.indices) held[touch.lane] = 0
                    release(touch.lane)
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(engine, config) {
        engine.configure(config)
        flush()
    }
    DisposableEffect(engine) {
        onDispose {
            engine.reset()
            flush()
            engine.release()
        }
    }

    Canvas(
        modifier.fillMaxSize().pointerInput(engine, mode, lanes) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    engine.viewport(size.width.toFloat(), size.height.toFloat())
                    engine.feed(event)
                    flush()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    ) {
        engine.viewport(size.width, size.height)
        when (mode) {
            VTouchMode.HITBOX -> drawHitbox(engine, held, colors, opacity)
            VTouchMode.PAD -> drawPad(engine, held, colors, opacity)
            VTouchMode.OFF -> Unit
        }
    }
}

private fun DrawScope.drawHitbox(engine: VTouchEngine, held: List<Int>, colors: List<Color>, opacity: Float) {
    val shape = FloatArray(4)
    for (lane in held.indices) {
        if (!engine.shape(lane, shape)) continue
        val color = colors[lane % colors.size]
        val left = shape[0]
        val laneWidth = shape[2]
        val edge = laneWidth * 0.16f
        val pressed = held[lane] > 0
        val alpha = if (pressed) (opacity * 2.2f).coerceAtMost(0.85f) else opacity
        drawRect(color.copy(alpha = alpha * 0.18f), Offset(left, 0f), Size(laneWidth, size.height))
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = alpha), Color.Transparent), left, left + edge), Offset(left, 0f), Size(edge, size.height))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = alpha)), left + laneWidth - edge, left + laneWidth), Offset(left + laneWidth - edge, 0f), Size(edge, size.height))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = alpha * 0.8f)), size.height * 0.75f, size.height), Offset(left, size.height * 0.75f), Size(laneWidth, size.height * 0.25f))
    }
}

private fun DrawScope.drawPad(engine: VTouchEngine, held: List<Int>, colors: List<Color>, opacity: Float) {
    val shape = FloatArray(4)
    val arrows = held.size == 4
    for (lane in held.indices) {
        if (!engine.shape(lane, shape)) continue
        val center = Offset(shape[0], shape[1])
        val radius = shape[2]
        val pressed = held[lane] > 0
        val alpha = if (pressed) (opacity * 2.4f).coerceAtMost(0.95f) else (opacity * 1.6f).coerceAtMost(0.8f)
        val color = colors[lane % colors.size]
        drawCircle(Color.Black.copy(alpha = alpha * 0.45f), radius, center)
        drawCircle(color.copy(alpha = alpha), radius, center, style = Stroke(radius * 0.12f))
        if (arrows) {
            val arrow = Path().apply {
                val r = radius * 0.5f
                moveTo(center.x - r, center.y)
                lineTo(center.x + r * 0.35f, center.y - r * 0.8f)
                lineTo(center.x + r * 0.35f, center.y + r * 0.8f)
                close()
            }
            rotate(shape[3], center) { drawPath(arrow, color.copy(alpha = alpha)) }
        } else {
            drawCircle(color.copy(alpha = alpha), radius * 0.38f, center)
        }
        if (pressed) drawRoundRect(color.copy(alpha = alpha * 0.25f), Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), CornerRadius(radius))
    }
}
