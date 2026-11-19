package com.vconvertercrew.vconverter.overlay

import com.vconvertercrew.vtouch.VTouch
import com.vconvertercrew.vtouch.VTouchMode

object TouchOptions {
    const val MODE = "touchMode"
    const val OPACITY = "touchOpacity"
    const val GESTURES = "editorGestures"
    const val SWIPE = "swipeBack"
    const val FLICK = "touchFlick"

    fun load(fallbackMode: VTouchMode = VTouchMode.HITBOX, get: (String) -> String?) {
        VTouch.mode = get(MODE)?.takeIf { it.isNotBlank() }?.let { VTouchMode.parse(it) } ?: fallbackMode
        get(OPACITY)?.toFloatOrNull()?.let { VTouch.opacity = it.coerceIn(0f, 1f) }
        get(GESTURES)?.toBooleanStrictOrNull()?.let { VTouch.editorGestures = it }
        get(SWIPE)?.toBooleanStrictOrNull()?.let { VTouch.swipeBack = it }
        get(FLICK)?.toBooleanStrictOrNull()?.let { VTouch.flick = it }
    }

    fun submenu(save: (String, String) -> Unit): MenuOption.Submenu = MenuOption.Submenu(
        "Touch Controls",
        "PlayTest hitbox or virtual pad, pinch and two-finger gestures in the editors, and swiping right to go back in the menus."
    ) {
        listOf(
            MenuOption.Stepper("PlayTest Controls", "Hitbox splits the screen into 4 lanes like FNF mobile, Virtual Pad shows 4 arrow buttons, Off leaves only the keyboard.", { VTouch.mode.label }) { direction ->
                val all = VTouchMode.entries
                VTouch.mode = all[((VTouch.mode.ordinal + direction) % all.size + all.size) % all.size]
                save(MODE, VTouch.mode.name)
            },
            MenuOption.Stepper("Controls Opacity", "How visible the hitbox or the pad is while you play.", { VTouch.opacityLabel }) { direction ->
                VTouch.changeOpacity(direction)
                save(OPACITY, VTouch.opacity.toString())
            },
            MenuOption.Toggle("Editor Gestures", "Pinch to zoom and drag with two fingers to move the view in the chart, stage and character editors. Long press a note or event for its menu.", { VTouch.editorGestures }) {
                VTouch.editorGestures = it
                save(GESTURES, it.toString())
            },
            MenuOption.Toggle("Swipe To Go Back", "Swipe right in the menus to go back.", { VTouch.swipeBack }) {
                VTouch.swipeBack = it
                save(SWIPE, it.toString())
            },
            MenuOption.Toggle("Flick To Scroll", "A fast swipe in the menus keeps moving a few more items.", { VTouch.flick }) {
                VTouch.flick = it
                save(FLICK, it.toString())
            },
            MenuOption.Action("Touch Engine: ${VTouch.backend}", "V-Touch reads every finger in its C++ engine; the web version runs the same engine written in Kotlin.") {}
        )
    }
}
