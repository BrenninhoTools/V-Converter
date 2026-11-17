package com.vconvertercrew.vtouch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

class VContextItem(val label: String, val enabled: Boolean = true, val run: () -> Unit)

fun Modifier.vPinchPan(key: Any?, onZoom: (Float, Offset) -> Unit, onPan: (Offset) -> Unit = {}): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (VTouch.editorGestures && event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (zoom != 1f) onZoom(zoom, centroid)
                if (pan != Offset.Zero) onPan(pan)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

fun Modifier.vMenuNavigation(
    key: Any?,
    step: () -> Float,
    onMove: (Int) -> Unit,
    onTap: (Offset) -> Unit,
    onBack: (() -> Unit)? = null,
    enabled: () -> Boolean = { true }
): Modifier = this
    .pointerInput(key) {
        detectTapGestures { if (enabled()) onTap(it) }
    }
    .pointerInput(key) {
        var vertical = 0f
        var totalX = 0f
        var totalY = 0f
        detectDragGestures(
            onDragStart = {
                vertical = 0f
                totalX = 0f
                totalY = 0f
            },
            onDragEnd = {
                val swipe = 110.dp.toPx()
                if (enabled() && VTouch.swipeBack && totalX > swipe && abs(totalX) > abs(totalY) * 2f) onBack?.invoke()
            }
        ) { change, amount ->
            change.consume()
            if (!enabled()) return@detectDragGestures
            totalX += amount.x
            totalY += amount.y
            if (abs(totalX) > abs(totalY) * 1.5f) return@detectDragGestures
            vertical += amount.y
            val size = step().coerceAtLeast(24f)
            while (abs(vertical) > size) {
                onMove(if (vertical > 0) -1 else 1)
                vertical -= if (vertical > 0) size else -size
            }
        }
    }
    .pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll && enabled()) {
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta > 0.2f) onMove(1) else if (delta < -0.2f) onMove(-1)
                }
            }
        }
    }

@Composable
fun VContextMenu(at: Offset?, items: List<VContextItem>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val point = at ?: Offset.Zero
    Box(Modifier.offset(with(density) { point.x.toDp() }, with(density) { point.y.toDp() })) {
        DropdownMenu(expanded = at != null, onDismissRequest = onDismiss) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    enabled = item.enabled,
                    onClick = {
                        onDismiss()
                        item.run()
                    }
                )
            }
        }
    }
}
