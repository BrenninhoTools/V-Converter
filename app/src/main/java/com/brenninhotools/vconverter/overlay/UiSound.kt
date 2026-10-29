package com.brenninhotools.vconverter.overlay

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

object UiSound {
    const val SCROLL = "sounds/scrollMenu.ogg"

    var enabled: Boolean = true
    var player: (() -> Unit)? = null

    fun click() {
        if (enabled) runCatching { player?.invoke() }
    }

    fun wrap(onClick: () -> Unit): () -> Unit = {
        click()
        onClick()
    }
}

fun Modifier.soundClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, onClick = UiSound.wrap(onClick))

fun Modifier.soundClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = clickable(interactionSource = interactionSource, indication = indication, enabled = enabled, onClick = UiSound.wrap(onClick))
