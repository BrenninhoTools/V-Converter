package com.vconvertercrew.vconverter.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FunkinTransition {
    const val OUT_MS = 350
    const val IN_MS = 500

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    internal val black = Animatable(0f)
    private var running = false

    fun go(action: () -> Unit) {
        if (running) {
            action()
            return
        }
        running = true
        scope.launch {
            try {
                black.animateTo(1f, tween(OUT_MS, easing = LinearEasing))
                action()
                black.animateTo(0f, tween(IN_MS, easing = LinearEasing))
            } finally {
                black.snapTo(0f)
                running = false
            }
        }
    }
}

@Composable
fun FunkinTransitionOverlay(modifier: Modifier = Modifier) {
    val alpha = FunkinTransition.black.value
    if (alpha <= 0f) return
    Canvas(modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = alpha)) }
}
