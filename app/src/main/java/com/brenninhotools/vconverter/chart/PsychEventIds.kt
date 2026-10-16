package com.brenninhotools.vconverter.chart

object PsychEventIds {
    val vsliceEvents = setOf("FocusCamera", "PlayAnimation", "ScrollSpeed", "SetCameraBop", "SetHealthIcon", "SetTargetBopSpeed", "ZoomCamera")

    fun idFor(name: String): String {
        val pascal = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            .ifEmpty { "PsychEvent" }
        val id = if (pascal.first().isDigit()) "Event$pascal" else pascal
        return if (id in vsliceEvents) "Psych$id" else id
    }

    fun className(name: String): String = idFor(name).let { if (it.endsWith("Event")) it else it + "Event" }
}
