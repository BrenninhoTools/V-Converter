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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlin.math.roundToInt

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var fpsCounter by mutableStateOf(prefs.getBoolean(KEY_FPS, true))
        private set

    var touchCursor by mutableStateOf(prefs.getBoolean(KEY_TOUCH_CURSOR, true))
        private set

    fun updateTouchCursor(value: Boolean) {
        touchCursor = value
        prefs.edit().putBoolean(KEY_TOUCH_CURSOR, value).apply()
    }

    var menuMusic by mutableStateOf(prefs.getBoolean(KEY_MUSIC, true))
        private set

    fun updateMenuMusic(value: Boolean) {
        menuMusic = value
        prefs.edit().putBoolean(KEY_MUSIC, value).apply()
    }

    var musicVolume by mutableStateOf(prefs.getFloat(KEY_VOLUME, 0.5f))
        private set

    fun updateMusicVolume(value: Float) {
        musicVolume = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_VOLUME, musicVolume).apply()
    }

    fun updateFpsCounter(value: Boolean) {
        fpsCounter = value
        prefs.edit().putBoolean(KEY_FPS, value).apply()
    }

    private companion object {
        const val KEY_FPS = "fps_counter"
        const val KEY_TOUCH_CURSOR = "touch_cursor"
        const val KEY_MUSIC = "menu_music"
        const val KEY_VOLUME = "music_volume"
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
            ToggleRow(
                label = "Touch Cursor",
                checked = settings.touchCursor,
                onChange = settings::updateTouchCursor,
                accent = Funkin.Down,
                hint = "Shows the cursor where you touch the screen."
            )
            ToggleRow(
                label = "Menu Music",
                checked = settings.menuMusic,
                onChange = settings::updateMenuMusic,
                accent = Funkin.Down,
                hint = "Plays freakyMenu while you work. It pauses while the Chart Editor plays a song."
            )
            if (settings.menuMusic) {
                Text("Music volume: ${(settings.musicVolume * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
                Slider(
                    value = settings.musicVolume,
                    onValueChange = settings::updateMusicVolume,
                    colors = SliderDefaults.colors(thumbColor = Funkin.Down, activeTrackColor = Funkin.Down)
                )
            }
            Text("Options are saved on this device.", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
            ChunkyButton(text = "Done", accent = Funkin.Down, onClick = onDismiss, icon = R.drawable.ic_check)
        }
    }
}
