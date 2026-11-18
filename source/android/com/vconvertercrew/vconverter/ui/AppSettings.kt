package com.vconvertercrew.vconverter.ui

import com.vconvertercrew.vconverter.overlay.soundClickable
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import com.vconvertercrew.vconverter.overlay.FunkinOptionsMenu
import com.vconvertercrew.vconverter.overlay.MenuOption
import com.vconvertercrew.vconverter.R
import com.vconvertercrew.vconverter.overlay.OverlayIcons
import com.vconvertercrew.vconverter.ui.components.ChunkyButton
import com.vconvertercrew.vconverter.ui.components.SectionTitle
import com.vconvertercrew.vconverter.ui.components.ToggleRow
import com.vconvertercrew.vconverter.ui.theme.Funkin
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

    var uiSounds by mutableStateOf(prefs.getBoolean(KEY_UI_SOUNDS, true))
        private set

    fun updateUiSounds(value: Boolean) {
        uiSounds = value
        prefs.edit().putBoolean(KEY_UI_SOUNDS, value).apply()
    }

    var musicVolume by mutableStateOf(prefs.getFloat(KEY_VOLUME, 0.5f))
        private set

    fun updateMusicVolume(value: Float) {
        musicVolume = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_VOLUME, musicVolume).apply()
    }

    var serverUrl by mutableStateOf(prefs.getString(KEY_SERVER, "") ?: "")
        private set

    fun updateServerUrl(value: String) {
        serverUrl = value.trim()
        com.vconvertercrew.vconverter.online.Online.serverUrl = serverUrl
        prefs.edit().putString(KEY_SERVER, serverUrl).apply()
    }

    var errorReports by mutableStateOf(prefs.getBoolean(KEY_REPORTS, false))
        private set

    fun updateErrorReports(value: Boolean) {
        errorReports = value
        com.vconvertercrew.vconverter.online.Online.reportsEnabled = value
        prefs.edit().putBoolean(KEY_REPORTS, value).apply()
    }

    var vslicePreview by mutableStateOf(prefs.getBoolean(KEY_VSLICE_PREVIEW, false))
        private set

    fun updateVSlicePreview(value: Boolean) {
        vslicePreview = value
        com.vconvertercrew.vconverter.vslice.VSlicePreview.enabled = value
        prefs.edit().putBoolean(KEY_VSLICE_PREVIEW, value).apply()
    }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    init {
        com.vconvertercrew.vconverter.overlay.TouchOptions.load { prefs.getString(it, null) }
        com.vconvertercrew.vconverter.vslice.VSlicePreview.enabled = vslicePreview
        com.vconvertercrew.vconverter.online.Online.serverUrl = serverUrl
        com.vconvertercrew.vconverter.online.Online.reportsEnabled = errorReports
    }

    fun updateFpsCounter(value: Boolean) {
        fpsCounter = value
        prefs.edit().putBoolean(KEY_FPS, value).apply()
    }

    private companion object {
        const val KEY_FPS = "fps_counter"
        const val KEY_TOUCH_CURSOR = "touch_cursor"
        const val KEY_MUSIC = "menu_music"
        const val KEY_UI_SOUNDS = "ui_sounds"
        const val KEY_VOLUME = "music_volume"
        const val KEY_SERVER = "server_url"
        const val KEY_REPORTS = "error_reports"
        const val KEY_VSLICE_PREVIEW = "vslice_preview"
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
            .soundClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(OverlayIcons.Gear, contentDescription = "Options", tint = Funkin.Text, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun OptionsDialog(settings: AppSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val art = remember {
        val assets = context.assets
        com.vconvertercrew.vconverter.overlay.MenuArt.load(
            image = { path -> runCatching { assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull() },
            text = { path -> runCatching { assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull() }
        )
    }
    var backPresses by remember { mutableStateOf(0) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        androidx.activity.compose.BackHandler { backPresses++ }
        val links = androidx.compose.ui.platform.LocalUriHandler.current
        val preferences = listOf(
            MenuOption.Toggle("FPS Counter", "Shows \"FPS:\" in the top-right corner.", { settings.fpsCounter }, settings::updateFpsCounter),
            MenuOption.Toggle("Touch Cursor", "Shows the cursor where you touch the screen.", { settings.touchCursor }, settings::updateTouchCursor),
            MenuOption.Toggle("UI Sounds", "Plays scrollMenu when you tap tabs and buttons.", { settings.uiSounds }, settings::updateUiSounds),
            MenuOption.Toggle("Menu Music", "Plays freakyMenu while you work. It pauses while the Chart Editor plays a song.", { settings.menuMusic }, settings::updateMenuMusic),
            MenuOption.Stepper("Music Volume", "Left and right (or tapping it) change the menu music volume.", { "${(settings.musicVolume * 100).roundToInt()}%" }) { step ->
                val next = ((settings.musicVolume * 10).roundToInt() + step).let { if (it > 10) 0 else if (it < 0) 10 else it }
                settings.updateMusicVolume(next / 10f)
            },
            MenuOption.Toggle("V-Slice Preview", "V-Slice output uses the Public PlayTest layout (gameplay/songs, gameplay/characters, gameplay/stages, ui/story-mode). Off keeps the 0.7 layout.", { settings.vslicePreview }, settings::updateVSlicePreview),
            MenuOption.TextEntry("Online Server", "Leave it empty to connect to a V-Converter server automatically, or type the address of your own server. Tap it again to edit.", { settings.serverUrl }, settings::updateServerUrl, "Automatic"),
            MenuOption.Toggle("Error Reports", "Sends conversion errors and crashes (message, app version and platform) to the online server.", { settings.errorReports }, settings::updateErrorReports)
        )
        val options = listOf(
            MenuOption.Submenu("Preferences", "FPS counter, touch cursor, sounds, music, the V-Slice layout, the online server and error reports.") { preferences },
            com.vconvertercrew.vconverter.overlay.TouchOptions.submenu(settings::saveString),
            com.vconvertercrew.vconverter.overlay.discordOption { links.openUri(it) }
        )
        FunkinOptionsMenu(
            "Options", options, art, onClose = onDismiss, closeRequests = backPresses,
            overlay = {
                com.vconvertercrew.vconverter.overlay.DiscordBadge(
                    Modifier.align(Alignment.TopEnd).padding(16.dp)
                )
            }
        )
    }
}
