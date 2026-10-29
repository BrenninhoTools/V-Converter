package com.brenninhotools.vconverter.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import kotlin.math.roundToInt

private const val CURSOR_ASSET = "images/cursor/cursor.png"

@Composable
fun rememberAppCursor(): PointerIcon? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return remember(density) {
        runCatching {
            val source = context.assets.open(CURSOR_ASSET).use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val scale = max(1, density.roundToInt())
            val bitmap = if (scale == 1) source else Bitmap.createScaledBitmap(source, source.width * scale, source.height * scale, false)
            PointerIcon(android.view.PointerIcon.create(bitmap, scale.toFloat(), scale.toFloat()))
        }.getOrNull()
    }
}

fun Modifier.appCursor(icon: PointerIcon?): Modifier =
    if (icon == null) this else pointerHoverIcon(icon, overrideDescendants = true)
