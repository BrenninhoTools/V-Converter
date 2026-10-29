package com.brenninhotools.vconverter.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.overlay.OverlayIcons
import com.brenninhotools.vconverter.ui.components.ChunkyButton
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.ToggleRow
import com.brenninhotools.vconverter.ui.theme.Funkin

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var fpsCounter by mutableStateOf(prefs.getBoolean(KEY_FPS, true))
        private set

    fun updateFpsCounter(value: Boolean) {
        fpsCounter = value
        prefs.edit().putBoolean(KEY_FPS, value).apply()
    }

    private companion object {
        const val KEY_FPS = "fps_counter"
    }
}

@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppSettings(context) }
}

@Composable
fun OptionsButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(Funkin.Surface)
            .border(1.5.dp, Funkin.Outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(OverlayIcons.Gear, contentDescription = "Options", tint = Funkin.Text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun OptionsDialog(settings: AppSettings, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Funkin.Surface)
                .border(2.dp, Funkin.Outline, shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("Options", Funkin.Down)
            ToggleRow(
                label = "FPS Counter",
                checked = settings.fpsCounter,
                onChange = settings::updateFpsCounter,
                accent = Funkin.Down,
                hint = "Shows \"FPS:\" in the top-right corner."
            )
            Text("Options are saved on this device.", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
            ChunkyButton(text = "Done", accent = Funkin.Down, onClick = onDismiss, icon = R.drawable.ic_check)
        }
    }
}
