package com.brenninhotools.vconverter.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModRun
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ScannerColors(
    val accent: Color,
    val surface: Color,
    val track: Color,
    val text: Color,
    val muted: Color,
    val danger: Color = Color(0xFFFF5C7A)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModScannerPanel(
    progress: ModProgress,
    colors: ScannerColors,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    cancelling: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val beam by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beam")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val stripes by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "stripes")
    val determinate = progress.total > 0
    val target = if (determinate) (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) else 0f
    val fraction by animateFloatAsState(target, tween(250), label = "fraction")
    val accent = if (cancelling) colors.danger else colors.accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(modifier = Modifier.size(132.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 2f
                drawCircle(accent.copy(alpha = 0.06f), radius, center)
                for (ring in 1..4) drawCircle(accent.copy(alpha = 0.22f), radius * ring / 4f, center, style = Stroke(1.2f))
                drawLine(accent.copy(alpha = 0.18f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
                drawLine(accent.copy(alpha = 0.18f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)
                rotate(sweep, center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1f to accent.copy(alpha = 0.55f),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                    drawLine(accent, center, Offset(center.x + radius, center.y), 2.5f)
                }
                val count = progress.recent.size
                progress.recent.forEachIndexed { index, name ->
                    val hash = name.hashCode()
                    val angle = ((hash ushr 3) % 360) * PI / 180.0
                    val distance = radius * (0.25f + ((hash ushr 11) % 70) / 100f)
                    val point = Offset(center.x + (cos(angle) * distance).toFloat(), center.y + (sin(angle) * distance).toFloat())
                    val age = (count - 1 - index).toFloat() / count.coerceAtLeast(1)
                    val alpha = (1f - age).coerceIn(0.15f, 1f)
                    drawCircle(accent.copy(alpha = alpha), 3.5f + (if (index == count - 1) pulse * 3f else 0f), point)
                    if (index == count - 1) drawCircle(accent.copy(alpha = (1f - pulse) * 0.6f), 4f + pulse * 14f, point, style = Stroke(1.5f))
                }
                drawCircle(accent, 3f, center)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.track.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        (if (cancelling) "CANCELLING" else progress.phase.ifBlank { "Scanning" }.uppercase()) + "…",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    val lines = progress.recent.takeLast(4)
                    lines.forEachIndexed { index, name ->
                        Text(
                            "› $name",
                            color = colors.muted.copy(alpha = 0.45f + 0.55f * (index + 1) / lines.size),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        progress.label,
                        color = colors.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * beam
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to accent.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            startY = y - 18f,
                            endY = y + 18f
                        ),
                        topLeft = Offset(0f, y - 18f),
                        size = Size(size.width, 36f)
                    )
                    drawLine(accent.copy(alpha = 0.9f), Offset(0f, y), Offset(size.width, y), 1.5f)
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val corner = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(colors.track, size = size, cornerRadius = corner)
            val width = if (determinate) size.width * fraction else size.width
            if (width > 0f) {
                drawRoundRect(accent.copy(alpha = if (determinate) 1f else 0.35f), size = Size(width, size.height), cornerRadius = corner)
                val step = 22f
                var x = -step + stripes * step
                while (x < width) {
                    val left = x.coerceAtLeast(0f)
                    val right = (x + step / 2).coerceAtMost(width)
                    if (right > left) drawRect(Color.White.copy(alpha = 0.16f), Offset(left, 0f), Size(right - left, size.height))
                    x += step
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (determinate) "${(fraction * 100).toInt()}%  ·  ${progress.done}/${progress.total}" else "Working…",
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            val timing = buildList {
                if (progress.elapsedMs > 0) add(ModRun.formatMillis(progress.elapsedMs))
                progress.etaMs?.let { add("~${ModRun.formatMillis(it)} left") }
            }.joinToString("  ·  ")
            if (timing.isNotEmpty()) Text(timing, color = colors.muted, fontSize = 12.sp)
        }
        val chips = progress.counts.filterValues { it > 0 }
        if (chips.isNotEmpty() || progress.skipped > 0 || progress.written > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (progress.written > 0) Chip("${progress.written} files", colors.text, colors.track)
                chips.forEach { (category, value) -> Chip("${category.label} $value", colors.accent, colors.track) }
                if (progress.skipped > 0) Chip("${progress.skipped} skipped", colors.danger, colors.track)
            }
        }
        if (onCancel != null) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !cancelling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger)
            ) {
                Text(if (cancelling) "Cancelling…" else "Cancel conversion")
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, background: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(6.dp))
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
