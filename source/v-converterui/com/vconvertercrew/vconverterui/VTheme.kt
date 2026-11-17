package com.vconvertercrew.vconverterui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Immutable
data class VColors(
    val Background: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val Outline: Color,
    val Text: Color,
    val Muted: Color,
    val Ink: Color,
    val Left: Color,
    val Down: Color,
    val Up: Color,
    val Right: Color,
    val Warning: Color,
    val Pink: Color,
    val Purple: Color,
    val Lavender: Color,
    val Orange: Color
) {
    val lanes: List<Color> get() = listOf(Left, Down, Up, Right)

    fun scheme(): ColorScheme = darkColorScheme(
        primary = Down,
        onPrimary = Ink,
        secondary = Left,
        onSecondary = Ink,
        tertiary = Up,
        onTertiary = Ink,
        error = Right,
        onError = Ink,
        background = Background,
        onBackground = Text,
        surface = Surface,
        onSurface = Text,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = Muted,
        surfaceContainer = Surface,
        surfaceContainerHigh = SurfaceHigh,
        surfaceContainerHighest = SurfaceHigh,
        outline = Outline,
        outlineVariant = Outline
    )

    companion object {
        val Funkin = VColors(
            Background = Color(0xFF0F0C18),
            Surface = Color(0xFF1A1528),
            SurfaceHigh = Color(0xFF241D38),
            Outline = Color(0xFF3A3052),
            Text = Color(0xFFF5F1FF),
            Muted = Color(0xFFA79CC4),
            Ink = Color(0xFF07050C),
            Left = Color(0xFFC24B99),
            Down = Color(0xFF00E5FF),
            Up = Color(0xFF3DDC4A),
            Right = Color(0xFFF9393F),
            Warning = Color(0xFFFFC24B),
            Pink = Color(0xFFFF2D95),
            Purple = Color(0xFF7B2FF7),
            Lavender = Color(0xFFB98CFF),
            Orange = Color(0xFFFF9A3C)
        )
    }
}

val LocalVColors = staticCompositionLocalOf { VColors.Funkin }

object VTheme {
    val colors: VColors
        @Composable get() = LocalVColors.current
}

object VTypography {
    private val Base = Typography()

    fun funkin(display: FontFamily, labelSize: TextUnit = 16.sp) = Typography(
        displaySmall = Base.displaySmall.copy(fontFamily = display),
        headlineMedium = Base.headlineMedium.copy(fontFamily = display),
        headlineSmall = Base.headlineSmall.copy(fontFamily = display, fontSize = 26.sp),
        titleLarge = Base.titleLarge.copy(fontFamily = display, fontSize = 22.sp, letterSpacing = 0.5.sp),
        titleMedium = Base.titleMedium.copy(fontFamily = display, fontSize = 18.sp, letterSpacing = 0.4.sp),
        titleSmall = Base.titleSmall.copy(fontFamily = display, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = display, fontSize = labelSize, letterSpacing = 1.sp),
        labelMedium = Base.labelMedium.copy(fontFamily = display, letterSpacing = 0.6.sp),
        bodyLarge = Base.bodyLarge,
        bodyMedium = Base.bodyMedium,
        bodySmall = Base.bodySmall,
        labelSmall = Base.labelSmall
    )
}

@Composable
fun VConverterUITheme(colors: VColors = VColors.Funkin, typography: Typography = VTypography.funkin(FontFamily.Default), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVColors provides colors) {
        MaterialTheme(colorScheme = colors.scheme(), typography = typography, content = content)
    }
}
