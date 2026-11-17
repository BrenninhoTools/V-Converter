package com.vconvertercrew.vtouch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VTouchMode(val label: String) {
    HITBOX("Hitbox"),
    PAD("Virtual Pad"),
    OFF("Off");

    fun next(): VTouchMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): VTouchMode = entries.firstOrNull { it.name == name } ?: HITBOX
    }
}

object VTouch {
    var mode by mutableStateOf(VTouchMode.HITBOX)
    var opacity by mutableFloatStateOf(0.35f)
    var editorGestures by mutableStateOf(true)
    var swipeBack by mutableStateOf(true)

    fun changeOpacity(steps: Int) {
        opacity = ((opacity * 20f).toInt() + steps).coerceIn(0, 20) / 20f
    }

    val opacityLabel: String get() = "${(opacity * 100f).toInt()}%"
}
