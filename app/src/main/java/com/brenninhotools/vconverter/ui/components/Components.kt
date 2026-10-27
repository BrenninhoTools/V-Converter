package com.brenninhotools.vconverter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.ui.StatusMessage
import com.brenninhotools.vconverter.ui.theme.Funkin

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun FunkinCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Funkin.Surface)
            .border(2.dp, accent?.copy(alpha = 0.55f) ?: Funkin.Outline, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
fun SectionTitle(text: String, accent: Color, step: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (step != null) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(step.toString(), style = MaterialTheme.typography.labelMedium, color = Funkin.Ink)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.titleMedium, color = Funkin.Text)
    }
}

@Composable
fun ChunkyButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    val fill = if (enabled) accent else Funkin.SurfaceHigh
    val lift = if (pressed || !enabled) 0.dp else 4.dp
    Box(modifier = modifier.fillMaxWidth().height(60.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = 4.dp)
                .clip(shape)
                .background(Funkin.Ink)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(y = 4.dp - lift)
                .clip(shape)
                .background(fill)
                .border(3.dp, Funkin.Ink, shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val content = if (enabled) Funkin.Ink else Funkin.Muted
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp, color = content)
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

@Composable
fun DropZone(
    title: String,
    hint: String,
    accent: Color,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.03f))))
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.7f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(accent)
                .border(3.dp, Funkin.Ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Funkin.Ink, modifier = Modifier.size(28.dp))
        }
        Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, color = Funkin.Text)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Funkin.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileItem(
    title: String,
    accent: Color,
    onRemove: () -> Unit,
    enabled: Boolean,
    tags: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Funkin.SurfaceHigh)
            .padding(start = 14.dp, top = 6.dp, end = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Funkin.Text,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove $title", tint = Funkin.Muted, modifier = Modifier.size(20.dp))
            }
        }
        FlowRow(
            modifier = Modifier.padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tags()
        }
        Box(Modifier.padding(end = 10.dp)) {
            content()
        }
    }
}

@Composable
fun FunkinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = if (placeholder.isNullOrBlank()) null else {
            { Text(placeholder, color = Funkin.Muted.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            focusedLabelColor = accent,
            cursorColor = accent,
            unfocusedBorderColor = Funkin.Outline
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun StatusBanner(status: StatusMessage?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = status != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val current = status ?: return@AnimatedVisibility
        val color = when (current) {
            is StatusMessage.Error -> Funkin.Right
            is StatusMessage.Success -> Funkin.Up
            is StatusMessage.Info -> Funkin.Down
        }
        val icon = when (current) {
            is StatusMessage.Error -> R.drawable.ic_warning
            else -> R.drawable.ic_check
        }
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color.copy(alpha = 0.12f))
                .border(2.dp, color.copy(alpha = 0.6f), shape)
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                current.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Funkin.Text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Dismiss", tint = Funkin.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportView(report: Report, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (report.stats.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                report.stats.forEach { stat ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Funkin.SurfaceHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(stat.value, style = MaterialTheme.typography.titleLarge, color = accent)
                        Text(stat.label, style = MaterialTheme.typography.labelSmall, color = Funkin.Muted)
                    }
                }
            }
        }
        if (report.warnings.isNotEmpty()) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Funkin.Warning.copy(alpha = 0.10f))
                    .border(1.5.dp, Funkin.Warning.copy(alpha = 0.5f), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = Funkin.Warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("HEADS UP", style = MaterialTheme.typography.labelMedium, color = Funkin.Warning)
                }
                report.warnings.forEach { warning ->
                    Text("• $warning", style = MaterialTheme.typography.bodySmall, color = Funkin.Text)
                }
            }
        }
        report.sections.forEach { section ->
            Collapsible(
                title = section.title,
                count = section.lines.size,
                accent = accent,
                startOpen = section.lines.size <= 3
            ) {
                section.lines.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
                }
            }
        }
    }
}

@Composable
fun Collapsible(
    title: String,
    count: Int,
    accent: Color,
    startOpen: Boolean,
    content: @Composable () -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Funkin.SurfaceHigh.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Funkin.Text,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = accent)
            Icon(
                painterResource(R.drawable.ic_expand),
                contentDescription = if (open) "Collapse" else "Expand",
                tint = Funkin.Muted,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(22.dp)
                    .rotate(turn)
            )
        }
        AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                modifier = Modifier.padding(start = 26.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun OutputFiles(paths: List<String>, accent: Color = Funkin.Down) {
    val groups = paths.groupBy { it.substringBeforeLast('/', "") }.toSortedMap()
    Collapsible(
        title = "Output files",
        count = paths.size,
        accent = accent,
        startOpen = paths.size <= 6
    ) {
        groups.forEach { (folder, files) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Funkin.Ink)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (folder.isEmpty()) "/" else "$folder/",
                        style = MaterialTheme.typography.bodySmall,
                        color = Funkin.Text,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                files.forEach { path ->
                    Text(
                        "  " + path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = Funkin.Muted,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun AppLogo(size: Int = 44) {
    val shape = RoundedCornerShape((size * 0.28f).dp)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Funkin.Purple, Funkin.Pink)))
            .border(2.dp, Funkin.Ink, shape),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.5f).dp))
    }
}
