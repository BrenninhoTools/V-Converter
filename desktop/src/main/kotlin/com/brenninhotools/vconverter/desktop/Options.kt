package com.brenninhotools.vconverter.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.overlay.OverlayIcons
import java.util.prefs.Preferences
import kotlin.math.roundToInt

object AppSettings {
    private val prefs: Preferences? = runCatching { Preferences.userRoot().node("brenninhotools/v-converter") }.getOrNull()

    var fpsCounter by mutableStateOf(prefs?.getBoolean("fpsCounter", true) ?: true)
        private set

    fun updateFpsCounter(value: Boolean) {
        fpsCounter = value
        runCatching { prefs?.putBoolean("fpsCounter", value) }
    }

    var menuMusic by mutableStateOf(prefs?.getBoolean("menuMusic", true) ?: true)
        private set

    fun updateMenuMusic(value: Boolean) {
        menuMusic = value
        runCatching { prefs?.putBoolean("menuMusic", value) }
    }

    var musicVolume by mutableStateOf(prefs?.getFloat("musicVolume", 0.5f) ?: 0.5f)
        private set

    fun updateMusicVolume(value: Float) {
        musicVolume = value.coerceIn(0f, 1f)
        runCatching { prefs?.putFloat("musicVolume", musicVolume) }
    }
}

@Composable
fun OptionsButton() {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .background(if (open) Palette.SurfaceHigh else Palette.Surface)
                .border(1.dp, Palette.Outline, RoundedCornerShape(10.dp))
                .clickable { open = !open },
            contentAlignment = Alignment.Center
        ) {
            Icon(OverlayIcons.Gear, contentDescription = "Options", tint = Palette.Text, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(min = 280.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OPTIONS", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                OptionToggle("FPS Counter", "Shows \"FPS:\" in the top-right corner.", AppSettings.fpsCounter, AppSettings::updateFpsCounter)
                OptionToggle("Menu Music", "Plays freakyMenu while you work. It pauses while an editor plays a song.", AppSettings.menuMusic, AppSettings::updateMenuMusic)
                if (AppSettings.menuMusic) {
                    Text("Music volume: ${(AppSettings.musicVolume * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Slider(
                        value = AppSettings.musicVolume,
                        onValueChange = AppSettings::updateMusicVolume,
                        colors = SliderDefaults.colors(thumbColor = Palette.Down, activeTrackColor = Palette.Down)
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionToggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Palette.Ink, checkedTrackColor = Palette.Down)
        )
    }
}
