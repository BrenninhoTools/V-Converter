package com.vconvertercrew.vconverter.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vconvertercrew.vconverter.editor.AtlasFrame
import com.vconvertercrew.vconverter.skin.BackButtonSkin
import com.vconvertercrew.vconverter.skin.SkinAnimation
import com.vconvertercrew.vconverter.skin.SkinImage
import com.vconvertercrew.vconverter.skin.SkinSprite
import kotlin.math.max
import kotlin.math.roundToInt

class SkinArea(val left: Float, val top: Float, val width: Float, val height: Float, val scale: Float)

internal fun tintFilter(argb: Int?): ColorFilter? = argb?.let { ColorFilter.tint(Color(it), BlendMode.Modulate) }

class SkinSpriteArt(val spec: SkinSprite, val sheet: SpriteSheet?, val image: ImageBitmap?) {
    private val filter = tintFilter(spec.tint)

    fun frames(animation: SkinAnimation?): List<AtlasFrame> {
        val sheet = sheet ?: return emptyList()
        val all = if (animation == null) sheet.atlas.frames else sheet.prefixed(animation.prefix)
        val indices = animation?.indices ?: return all
        return indices.mapNotNull { all.getOrNull(it) }
    }

    private fun baseSize(): Pair<Float, Float> {
        val first = spec.animation(spec.play)?.let { frames(it).firstOrNull() }
            ?: spec.animations.firstNotNullOfOrNull { frames(it).firstOrNull() }
            ?: sheet?.atlas?.frames?.firstOrNull()
        if (first != null) return first.frameWidth.toFloat() to first.frameHeight.toFloat()
        val bitmap = image ?: return 0f to 0f
        return bitmap.width.toFloat() to bitmap.height.toFloat()
    }

    fun DrawScope.draw(area: SkinArea, animationName: String?, seconds: Double, extraScale: Float = 1f, scrollY: Float = 0f, alpha: Float = 1f): Rect? {
        if (!spec.visible) return null
        val sx = spec.scaleX * extraScale
        val sy = spec.scaleY * extraScale
        val (baseW, baseH) = baseSize()
        val width = baseW * sx * area.scale
        val height = baseH * sy * area.scale
        val (placedLeft, placedTop) = spec.anchor.place(area.left, area.top, area.width, area.height, spec.x, spec.y, width, height, area.scale)
        val animation = spec.animation(animationName)
        val left = placedLeft - (animation?.offsetX ?: 0f) * area.scale
        val top = placedTop - (animation?.offsetY ?: 0f) * area.scale - scrollY * spec.scrollFactor
        val opacity = (spec.alpha * alpha).coerceIn(0f, 1f)
        if (opacity <= 0f) return Rect(left, top, left + width, top + height)
        val pivot = Offset(left + width / 2f, top + height / 2f)
        val sheet = sheet
        if (sheet != null) {
            val list = frames(animation ?: spec.animations.firstOrNull())
            val index = (animation ?: spec.animations.firstOrNull())?.frameIndex(seconds, list.size) ?: 0
            val frame = list.getOrNull(index) ?: sheet.atlas.frames.firstOrNull() ?: return null
            scale(if (spec.flipX) -1f else 1f, 1f, pivot) {
                scale(sx / max(sx, sy), sy / max(sx, sy), Offset(left, top)) {
                    with(sheet) { drawFrame(frame, left, top, max(sx, sy) * area.scale, opacity, filter) }
                }
            }
        } else {
            val bitmap = image ?: return null
            scale(if (spec.flipX) -1f else 1f, 1f, pivot) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(max(1, width.roundToInt()), max(1, height.roundToInt())),
                    alpha = opacity,
                    colorFilter = filter
                )
            }
        }
        return Rect(left, top, left + width, top + height)
    }

    companion object {
        fun load(spec: SkinSprite, image: (String) -> ImageBitmap?, text: (String) -> String?): SkinSpriteArt? {
            val bitmap = image(spec.pngPath) ?: return null
            val sheet = text(spec.xmlPath)?.let { SpriteSheet.create(bitmap, it) }
            return SkinSpriteArt(spec, sheet, bitmap)
        }

        fun loadAll(specs: List<SkinSprite>, image: (String) -> ImageBitmap?, text: (String) -> String?): List<SkinSpriteArt> =
            specs.mapNotNull { load(it, image, text) }.sortedBy { it.spec.layer }
    }
}

class SkinImageArt(val spec: SkinImage, val image: ImageBitmap) {
    private val filter = tintFilter(spec.tint)

    fun DrawScope.drawCover(width: Float, height: Float, scroll: Float, alpha: Float = 1f) {
        val cover = max(width / image.width, height / image.height) * spec.scale
        val dw = image.width * cover
        val dh = image.height * cover
        drawImage(
            image,
            dstOffset = IntOffset(((width - dw) / 2f).roundToInt(), ((height - dh) / 2f - scroll * spec.scrollFactor).roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            alpha = (spec.alpha * alpha).coerceIn(0f, 1f),
            colorFilter = filter
        )
    }

    companion object {
        fun load(spec: SkinImage?, image: (String) -> ImageBitmap?): SkinImageArt? = spec?.let { s -> image(s.pngPath)?.let { SkinImageArt(s, it) } }
    }
}

class BackButtonArt(val sheet: SpriteSheet, val skin: BackButtonSkin) {
    val frames: List<AtlasFrame> = sheet.prefixed(skin.prefix).ifEmpty { sheet.atlas.frames }

    fun DrawScope.draw(frame: Int, menuScale: Float, alpha: Float = 1f): Rect? {
        if (!skin.visible) return null
        val current = frames.getOrNull(frame.coerceIn(0, max(0, frames.lastIndex))) ?: return null
        val scale = menuScale * skin.scale
        val bw = current.frameWidth * scale
        val bh = current.frameHeight * scale
        val (left, top) = skin.anchor.place(0f, 0f, size.width, size.height, skin.x, skin.y, bw, bh, menuScale)
        with(sheet) { drawFrame(current, left, top, scale, (skin.alpha * alpha).coerceIn(0f, 1f)) }
        val pad = skin.hitPadding * menuScale
        return Rect(left - pad, top - pad, left + bw + pad, top + bh + pad)
    }

    companion object {
        fun load(skin: BackButtonSkin, image: (String) -> ImageBitmap?, text: (String) -> String?): BackButtonArt? =
            SpriteSheet.create(image(skin.pngPath), text(skin.xmlPath))?.let { BackButtonArt(it, skin) }
    }
}
