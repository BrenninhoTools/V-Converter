package com.vconvertercrew.vconverterui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class VStatusKind { INFO, SUCCESS, ERROR }

class VStat(val value: String, val label: String)

class VReportSection(val title: String, val lines: List<String>)

@Composable
fun VCard(accent: Color? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = VTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(colors.Surface)
            .border(1.5.dp, accent?.copy(alpha = 0.6f) ?: colors.Outline, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun VSectionTitle(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.titleMedium, color = VTheme.colors.Text)
    }
}

@Composable
fun VButton(text: String, accent: Color, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = VTheme.colors
    Button(
        onClick = VSound.wrap(onClick),
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.Ink, disabledContainerColor = colors.SurfaceHigh, disabledContentColor = colors.Muted)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun VOutlineButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = VTheme.colors
    OutlinedButton(onClick = VSound.wrap(onClick), enabled = enabled, modifier = modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, colors.Outline)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) colors.Text else colors.Muted)
    }
}

@Composable
fun VBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1) }
}

@Composable
fun VChip(text: String, selected: Boolean, accent: Color, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = VTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else colors.SurfaceHigh)
            .border(1.5.dp, if (selected) accent else colors.Outline, shape)
            .vClickable(enabled, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (!enabled) colors.Muted else if (selected) accent else colors.Text, maxLines = 1) }
}

@Composable
fun VStatusBanner(text: String, kind: VStatusKind, modifier: Modifier = Modifier) {
    val colors = VTheme.colors
    val color = when (kind) {
        VStatusKind.INFO -> colors.Down
        VStatusKind.SUCCESS -> colors.Up
        VStatusKind.ERROR -> colors.Right
    }
    SelectionContainer {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.Text,
            modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp)
        )
    }
}

@Composable
fun VTextField(value: String, onChange: (String) -> Unit, label: String, accent: Color, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(modifier),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent, cursorColor = accent)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VReport(stats: List<VStat>, warnings: List<String>, sections: List<VReportSection>, accent: Color, modifier: Modifier = Modifier) {
    val colors = VTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stats.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { stat ->
                    Column(Modifier.widthIn(min = 110.dp).clip(RoundedCornerShape(12.dp)).background(colors.SurfaceHigh).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(stat.value, style = MaterialTheme.typography.titleMedium, color = accent)
                        Text(stat.label, style = MaterialTheme.typography.labelSmall, color = colors.Muted)
                    }
                }
            }
        }
        if (warnings.isNotEmpty()) {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.Warning.copy(alpha = 0.10f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("WARNINGS (${warnings.size})", style = MaterialTheme.typography.labelMedium, color = colors.Warning)
                    warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.Text) }
                }
            }
        }
        sections.forEach { section ->
            var open by remember(section.title) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.SurfaceHigh)) {
                Row(Modifier.fillMaxWidth().vClickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (open) "▾" else "▸", color = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(section.title, style = MaterialTheme.typography.bodyMedium, color = colors.Text, modifier = Modifier.weight(1f))
                    Text("${section.lines.size}", style = MaterialTheme.typography.labelSmall, color = colors.Muted)
                }
                if (open) {
                    SelectionContainer {
                        Column(Modifier.padding(start = 30.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            section.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.Muted) }
                        }
                    }
                }
            }
        }
    }
}
