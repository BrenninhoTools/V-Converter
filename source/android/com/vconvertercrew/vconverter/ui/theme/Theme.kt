package com.vconvertercrew.vconverter.ui.theme

import com.vconvertercrew.vconverterui.VColors
import com.vconvertercrew.vconverterui.VConverterUITheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Funkin: VColors = VColors.Funkin

private val Colors = darkColorScheme(
    primary = Funkin.Down,
    onPrimary = Funkin.Ink,
    secondary = Funkin.Left,
    onSecondary = Funkin.Ink,
    tertiary = Funkin.Up,
    onTertiary = Funkin.Ink,
    error = Funkin.Right,
    onError = Funkin.Ink,
    background = Funkin.Background,
    onBackground = Funkin.Text,
    surface = Funkin.Surface,
    onSurface = Funkin.Text,
    surfaceVariant = Funkin.SurfaceHigh,
    onSurfaceVariant = Funkin.Muted,
    surfaceContainer = Funkin.Surface,
    surfaceContainerHigh = Funkin.SurfaceHigh,
    surfaceContainerHighest = Funkin.SurfaceHigh,
    outline = Funkin.Outline,
    outlineVariant = Funkin.Outline
)

private val Base = Typography()

private fun appTypography(display: FontFamily) = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = display),
    headlineMedium = Base.headlineMedium.copy(fontFamily = display),
    headlineSmall = Base.headlineSmall.copy(fontFamily = display, fontSize = 26.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = display, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = display, fontSize = 18.sp, letterSpacing = 0.4.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = display, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = display, fontSize = 17.sp, letterSpacing = 1.sp),
    labelMedium = Base.labelMedium.copy(fontFamily = display, letterSpacing = 0.6.sp),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelSmall = Base.labelSmall
)

private const val FONT_ASSET = "fonts/funkin.ttf"

@Composable
fun VConverterTheme(content: @Composable () -> Unit) {
    val assets = LocalContext.current.assets
    val typography = remember(assets) {
        val family = runCatching { FontFamily(Font(FONT_ASSET, assets)) }.getOrDefault(FontFamily.Default)
        appTypography(family)
    }
    VConverterUITheme(Funkin, typography, content)
}
