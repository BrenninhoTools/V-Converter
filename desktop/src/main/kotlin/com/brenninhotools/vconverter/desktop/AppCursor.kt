package com.brenninhotools.vconverter.desktop

import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

object AppCursor {
    val cursor: Cursor? by lazy {
        runCatching {
            val image = AppCursor::class.java.getResourceAsStream("/images/cursor/cursor.png")?.use { ImageIO.read(it) } ?: return@runCatching null
            val toolkit = Toolkit.getDefaultToolkit()
            val best = toolkit.getBestCursorSize(image.width, image.height)
            if (best.width <= 0 || best.height <= 0) return@runCatching null
            val scale = max(1, min(best.width / image.width, best.height / image.height))
            val canvas = BufferedImage(max(best.width, image.width * scale), max(best.height, image.height * scale), BufferedImage.TYPE_INT_ARGB)
            val graphics = canvas.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            graphics.drawImage(image, 0, 0, image.width * scale, image.height * scale, null)
            graphics.dispose()
            toolkit.createCustomCursor(canvas, Point(scale, scale), "V-Converter")
        }.getOrNull()
    }
}
