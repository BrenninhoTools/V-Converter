package com.brenninhotools.vconverter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.R

object Funkin {
    val Background = Color(0xFF0F0C18)
    val Surface = Color(0xFF1A1528)
    val SurfaceHigh = Color(0xFF241D38)
    val Outline = Color(0xFF3A3052)
    val Text = Color(0xFFF5F1FF)
    val Muted = Color(0xFFA79CC4)
    val Ink = Color(0xFF07050C)

    val Left = Color(0xFFC24B99)
    val Down = Color(0xFF00E5FF)
    val Up = Color(0xFF3DDC4A)
    val Right = Color(0xFFF9393F)
    val Warning = Color(0xFFFFC24B)

    val Pink = Color(0xFFFF2D95)
    val Purple = Color(0xFF7B2FF7)
    val Lavender = Color(0xFFB98CFF)
}

val Display = FontFamily(Font(R.font.lilita_one))

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

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Display),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Display),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, fontSize = 26.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Display, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = Display, fontSize = 18.sp, letterSpacing = 0.4.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = Display, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Display, fontSize = 17.sp, letterSpacing = 1.sp),
    labelMedium = Base.labelMedium.copy(fontFamily = Display, letterSpacing = 0.6.sp),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelSmall = Base.labelSmall
)

@Composable
fun VConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
