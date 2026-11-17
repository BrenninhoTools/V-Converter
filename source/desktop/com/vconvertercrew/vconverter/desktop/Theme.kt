package com.vconvertercrew.vconverter.desktop

import com.vconvertercrew.vconverterui.VColors
import com.vconvertercrew.vconverterui.VConverterUITheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

val Palette: VColors = VColors.Funkin

object Resources {
    fun bytes(path: String): ByteArray? = Resources::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }

    fun image(path: String): ImageBitmap? = bytes(path)?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
}

private val Display: FontFamily = Resources.bytes("fonts/funkin.ttf")?.let { FontFamily(Font("funkin", it)) } ?: FontFamily.Default

private val Colors = darkColorScheme(
    primary = Palette.Down,
    onPrimary = Palette.Ink,
    secondary = Palette.Left,
    onSecondary = Palette.Ink,
    tertiary = Palette.Up,
    onTertiary = Palette.Ink,
    error = Palette.Right,
    onError = Palette.Ink,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline
)

private val Base = Typography()

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Display),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Display),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, fontSize = 26.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Display, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = Display, fontSize = 18.sp, letterSpacing = 0.4.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = Display, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Display, fontSize = 16.sp, letterSpacing = 1.sp),
    labelMedium = Base.labelMedium.copy(fontFamily = Display, letterSpacing = 0.6.sp),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelSmall = Base.labelSmall
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    VConverterUITheme(Palette, AppTypography, content)
}
