package com.vconvertercrew.vconverterui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

object VSound {
    var onClick: () -> Unit = {}

    fun click() {
        runCatching { onClick() }
    }

    fun wrap(action: () -> Unit): () -> Unit = {
        click()
        action()
    }

    fun <T> wrapValue(action: (T) -> Unit): (T) -> Unit = { value ->
        click()
        action(value)
    }
}

fun Modifier.vClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = clickable(enabled = enabled, onClick = VSound.wrap(onClick))
