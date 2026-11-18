package com.vconvertercrew.vconverter.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object FunkinTransition {
    const val OUT_MS = 350
    const val IN_MS = 500

    internal var pending by mutableStateOf<(() -> Unit)?>(null)
    internal var hosts = 0

    fun go(action: () -> Unit) {
        if (hosts <= 0 || pending != null) {
            action()
            return
        }
        pending = action
    }
}

@Composable
fun FunkinTransitionOverlay(modifier: Modifier = Modifier) {
    val black = remember { Animatable(0f) }
    val action = FunkinTransition.pending
    DisposableEffect(Unit) {
        FunkinTransition.hosts++
        onDispose {
            FunkinTransition.hosts--
            FunkinTransition.pending?.let { leftover ->
                FunkinTransition.pending = null
                leftover()
            }
        }
    }
    LaunchedEffect(action) {
        if (action == null) return@LaunchedEffect
        try {
            black.animateTo(1f, tween(FunkinTransition.OUT_MS, easing = LinearEasing))
            runCatching { action() }
            black.animateTo(0f, tween(FunkinTransition.IN_MS, easing = LinearEasing))
        } finally {
            if (FunkinTransition.pending === action) FunkinTransition.pending = null
        }
    }
    val alpha = black.value
    if (alpha > 0f) Canvas(modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = alpha)) }
}
