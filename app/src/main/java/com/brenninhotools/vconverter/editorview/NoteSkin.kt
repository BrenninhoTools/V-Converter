package com.brenninhotools.vconverter.editorview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

class SkinFrame(val offset: IntOffset, val size: IntSize)

class NoteSkin(val image: ImageBitmap, frames: Map<String, SkinFrame>) {
    private fun find(frames: Map<String, SkinFrame>, vararg prefixes: String): SkinFrame? =
        prefixes.firstNotNullOfOrNull { prefix -> frames.keys.filter { it.startsWith(prefix) }.minOrNull()?.let { frames[it] } }

    val notes: List<SkinFrame?> = listOf(
        find(frames, "purple instance", "purple0", "left note"),
        find(frames, "blue instance", "blue0", "down note"),
        find(frames, "green instance", "green0", "up note"),
        find(frames, "red instance", "red0", "right note")
    )

    val receptors: List<SkinFrame?> = listOf(
        find(frames, "arrow static instance 1", "arrowLEFT", "static left"),
        find(frames, "arrow static instance 2", "arrowDOWN", "static down"),
        find(frames, "arrow static instance 4", "arrowUP", "static up"),
        find(frames, "arrow static instance 3", "arrowRIGHT", "static right")
    )

    val holdPieces: List<SkinFrame?> = listOf(
        find(frames, "purple hold piece", "left hold piece"),
        find(frames, "blue hold piece", "down hold piece"),
        find(frames, "green hold piece", "up hold piece"),
        find(frames, "red hold piece", "right hold piece")
    )

    val holdEnds: List<SkinFrame?> = listOf(
        find(frames, "pruple end hold", "purple hold end", "purple end hold", "left hold end"),
        find(frames, "blue hold end", "down hold end"),
        find(frames, "green hold end", "up hold end"),
        find(frames, "red hold end", "right hold end")
    )

    val isComplete: Boolean get() = notes.all { it != null }

    companion object {
        const val IMAGE = "images/ui/NOTE_assets.png"
        const val ATLAS = "images/ui/NOTE_assets.xml"

        private val subTexture = Regex("<SubTexture\\b[^>]*>")
        private fun attr(tag: String, name: String): String? = Regex("\\b$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

        fun parse(xml: String): Map<String, SkinFrame> {
            val frames = LinkedHashMap<String, SkinFrame>()
            subTexture.findAll(xml).forEach { match ->
                val tag = match.value
                val name = attr(tag, "name") ?: return@forEach
                val x = attr(tag, "x")?.toIntOrNull() ?: return@forEach
                val y = attr(tag, "y")?.toIntOrNull() ?: return@forEach
                val w = attr(tag, "width")?.toIntOrNull() ?: return@forEach
                val h = attr(tag, "height")?.toIntOrNull() ?: return@forEach
                if (w > 0 && h > 0) frames.putIfAbsent(name, SkinFrame(IntOffset(x, y), IntSize(w, h)))
            }
            return frames
        }

        fun create(image: ImageBitmap, xml: String): NoteSkin? = NoteSkin(image, parse(xml)).takeIf { it.isComplete }
    }
}
