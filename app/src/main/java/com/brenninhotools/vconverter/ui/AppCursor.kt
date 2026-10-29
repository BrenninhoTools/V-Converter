package com.brenninhotools.vconverter.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import kotlin.math.roundToInt

private const val CURSOR_ASSET = "images/cursor/cursor.png"

@Composable
fun rememberAppCursor(): PointerIcon? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return remember(density) {
        runCatching {
            val source = context.assets.open(CURSOR_ASSET).use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val scale = max(1, density.roundToInt())
            val bitmap = if (scale == 1) source else Bitmap.createScaledBitmap(source, source.width * scale, source.height * scale, false)
            PointerIcon(android.view.PointerIcon.create(bitmap, scale.toFloat(), scale.toFloat()))
        }.getOrNull()
    }
}

fun Modifier.appCursor(icon: PointerIcon?): Modifier =
    if (icon == null) this else pointerHoverIcon(icon, overrideDescendants = true)

class TouchCursorState {
    var position by mutableStateOf<Offset?>(null)
    var pressed by mutableStateOf(false)
}

@Composable
fun rememberCursorImage(): ImageBitmap? {
    val context = LocalContext.current
    return remember {
        runCatching { context.assets.open(CURSOR_ASSET).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
}

fun Modifier.touchCursor(state: TouchCursorState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val touch = event.changes.firstOrNull { it.type == PointerType.Touch || it.type == PointerType.Stylus } ?: continue
            state.position = touch.position
            state.pressed = event.changes.any { it.pressed }
        }
    }
}

@Composable
fun TouchCursorOverlay(state: TouchCursorState, image: ImageBitmap?, modifier: Modifier = Modifier) {
    image ?: return
    val alpha by animateFloatAsState(
        targetValue = if (state.pressed) 1f else 0f,
        animationSpec = if (state.pressed) tween(60) else tween(durationMillis = 500, delayMillis = 350),
        label = "touchCursor"
    )
    val position = state.position ?: return
    if (alpha <= 0f) return
    val scale = max(1, LocalDensity.current.density.roundToInt())
    Canvas(modifier) {
        drawImage(
            image = image,
            dstOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
            dstSize = IntSize(image.width * scale, image.height * scale),
            alpha = alpha,
            filterQuality = FilterQuality.None
        )
    }
}
