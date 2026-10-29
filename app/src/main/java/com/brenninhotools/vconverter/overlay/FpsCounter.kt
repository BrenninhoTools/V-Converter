package com.brenninhotools.vconverter.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun FpsCounter(modifier: Modifier = Modifier, fontFamily: FontFamily = FontFamily.Monospace) {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                frames++
                val elapsed = now - start
                if (elapsed >= 500_000_000L) {
                    fps = (frames * 1_000_000_000.0 / elapsed).roundToInt()
                    frames = 0
                    start = now
                }
            }
        }
    }
    Text(
        text = "FPS: $fps",
        style = TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(Color.Black, Offset(1.5f, 1.5f), 0f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
