package com.vconvertercrew.vtouch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity

class VContextItem(val label: String, val enabled: Boolean = true, val run: () -> Unit)

internal fun PointerInputChange.touchAction(): Int? = when {
    pressed && !previousPressed -> VTouchAction.DOWN
    !pressed && previousPressed -> VTouchAction.UP
    pressed && position != previousPosition -> VTouchAction.MOVE
    else -> null
}

internal fun VTouchEngine.feed(event: PointerEvent) {
    event.changes.forEach { change ->
        val action = change.touchAction() ?: return@forEach
        pointer(change.id.value, action, change.position.x, change.position.y, change.uptimeMillis.toDouble())
    }
}

internal inline fun VTouchEngine.drain(handle: (VTouchEvent) -> Unit) {
    while (true) handle(poll() ?: return)
}

fun Modifier.vPinchPan(key: Any?, onZoom: (Float, Offset) -> Unit, onPan: (Offset) -> Unit = {}): Modifier = pointerInput(key) {
    val engine = VTouchEngines.create(VTouchConfig(density = density, features = VTouchFeature.PINCH))
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                engine.viewport(size.width.toFloat(), size.height.toFloat())
                engine.feed(event)
                val active = VTouch.editorGestures
                engine.drain { touch ->
                    if (!active) return@drain
                    when (touch.type) {
                        VTouchEventType.ZOOM -> onZoom(touch.value, Offset(touch.x, touch.y))
                        VTouchEventType.PAN -> onPan(Offset(touch.dx, touch.dy))
                        else -> Unit
                    }
                }
                if (active && engine.pinching) event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        }
    } finally {
        engine.release()
    }
}

fun Modifier.vMenuNavigation(
    key: Any?,
    step: () -> Float,
    onMove: (Int) -> Unit,
    onTap: (Offset) -> Unit,
    onBack: (() -> Unit)? = null,
    enabled: () -> Boolean = { true },
    onLongPress: ((Offset) -> Unit)? = null
): Modifier = this
    .pointerInput(key) {
        val features = VTouchFeature.TAP or VTouchFeature.STEPS or VTouchFeature.SWIPE_BACK or VTouchFeature.FLICK or
            (if (onLongPress != null) VTouchFeature.LONG_PRESS else 0)
        val engine = VTouchEngines.create(VTouchConfig(density = density, features = features))
        try {
            awaitPointerEventScope {
                var clock = 0.0
                while (true) {
                    val event = if (onLongPress != null && engine.pointerCount > 0) {
                        withTimeoutOrNull(60L) { awaitPointerEvent() }
                    } else {
                        awaitPointerEvent()
                    }
                    val on = enabled()
                    engine.enabled(on)
                    engine.viewport(size.width.toFloat(), size.height.toFloat())
                    engine.step(step())
                    clock = event?.changes?.firstOrNull()?.uptimeMillis?.toDouble() ?: (clock + 60.0)
                    if (event != null) engine.feed(event)
                    engine.tick(clock)
                    engine.drain { touch ->
                        when (touch.type) {
                            VTouchEventType.TAP -> onTap(Offset(touch.x, touch.y))
                            VTouchEventType.STEP -> if (touch.dy == 0f || VTouch.flick) onMove(touch.value.toInt())
                            VTouchEventType.SWIPE_BACK -> if (VTouch.swipeBack) onBack?.invoke()
                            VTouchEventType.LONG_PRESS -> onLongPress?.invoke(Offset(touch.x, touch.y))
                            else -> Unit
                        }
                    }
                    if (event != null && on && engine.dragging) event.changes.forEach { it.consume() }
                }
            }
        } finally {
            engine.release()
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
