package com.brenninhotools.vconverter.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalCompact = staticCompositionLocalOf { false }

class AdaptiveScope internal constructor(private val row: RowScope?) {
    fun Modifier.pane(weight: Float): Modifier = if (row != null) with(row) { this@pane.weight(weight) } else this.fillMaxWidth()
}

@Composable
fun AdaptiveRow(spacing: Dp = 16.dp, content: @Composable AdaptiveScope.() -> Unit) {
    if (LocalCompact.current) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) { AdaptiveScope(null).content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { AdaptiveScope(this).content() }
    }
}
