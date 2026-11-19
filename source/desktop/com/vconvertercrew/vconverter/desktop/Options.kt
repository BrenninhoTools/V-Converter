package com.vconvertercrew.vconverter.desktop

import com.vconvertercrew.vconverter.overlay.soundClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import com.vconvertercrew.vconverter.overlay.DiscordBadge
import com.vconvertercrew.vconverter.overlay.discordOption
import com.vconvertercrew.vconverter.overlay.FunkinMainMenu
import com.vconvertercrew.vconverter.overlay.FunkinOptionsMenu
import com.vconvertercrew.vconverter.overlay.MainMenuArt
import com.vconvertercrew.vconverter.overlay.MainMenuEntry
import com.vconvertercrew.vconverter.overlay.MenuArt
import com.vconvertercrew.vconverter.overlay.MenuOption
import com.vconvertercrew.vconverter.overlay.OverlayIcons
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

    var uiSounds by mutableStateOf(prefs?.getBoolean("uiSounds", true) ?: true)
        private set

    fun updateUiSounds(value: Boolean) {
        uiSounds = value
        runCatching { prefs?.putBoolean("uiSounds", value) }
    }

    var serverUrl by mutableStateOf(prefs?.get("serverUrl", "") ?: "")
        private set

    fun updateServerUrl(value: String) {
        serverUrl = value.trim()
        com.vconvertercrew.vconverter.online.Online.serverUrl = serverUrl
        runCatching { prefs?.put("serverUrl", serverUrl) }
    }

    var errorReports by mutableStateOf(prefs?.getBoolean("errorReports", false) ?: false)
        private set

    fun updateErrorReports(value: Boolean) {
        errorReports = value
        com.vconvertercrew.vconverter.online.Online.reportsEnabled = value
        runCatching { prefs?.putBoolean("errorReports", value) }
    }

    var vslicePreview by mutableStateOf(prefs?.getBoolean("vslicePreview", false) ?: false)
        private set

    fun updateVSlicePreview(value: Boolean) {
        vslicePreview = value
        com.vconvertercrew.vconverter.vslice.VSlicePreview.enabled = value
        runCatching { prefs?.putBoolean("vslicePreview", value) }
    }

    var discordStatus by mutableStateOf(prefs?.getBoolean("discordStatus", true) ?: true)
        private set

    fun updateDiscordStatus(value: Boolean) {
        discordStatus = value
        runCatching { prefs?.putBoolean("discordStatus", value) }
    }

    fun saveString(key: String, value: String) {
        runCatching { prefs?.put(key, value) }
    }

    fun applyOnline() {
        com.vconvertercrew.vconverter.vslice.VSlicePreview.enabled = vslicePreview
        val os = System.getProperty("os.name").lowercase()
        com.vconvertercrew.vconverter.online.Online.platform = when {
            os.contains("win") -> "windows"
            os.contains("mac") -> "mac"
            else -> "linux"
        }
        com.vconvertercrew.vconverter.online.Online.appVersion = APP_VERSION
        com.vconvertercrew.vconverter.online.Online.serverUrl = serverUrl
        com.vconvertercrew.vconverter.online.Online.reportsEnabled = errorReports
        com.vconvertercrew.vconverter.overlay.TouchOptions.load(com.vconvertercrew.vtouch.VTouchMode.OFF) { key -> prefs?.get(key, "")?.takeIf { it.isNotEmpty() } }
        com.vconvertercrew.vconverter.overlay.DiscordAccount.setup(
            prefs?.get("discordProfile", "") ?: "",
            store = { value -> runCatching { prefs?.put("discordProfile", value) } },
            decode = { bytes -> org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        )
    }

    const val APP_VERSION = "0.1.0-playtest"

    var musicVolume by mutableStateOf(prefs?.getFloat("musicVolume", 0.5f) ?: 0.5f)
        private set

    fun updateMusicVolume(value: Float) {
        musicVolume = value.coerceIn(0f, 1f)
        runCatching { prefs?.putFloat("musicVolume", musicVolume) }
    }
}

object OptionsMenu {
    var open by mutableStateOf(false)
}

object MainMenu {
    var open by mutableStateOf(true)
    var list by mutableStateOf<String?>(null)
    var title by mutableStateOf(true)

    fun close() {
        list = null
        open = false
    }
}

private val mainMenuEntries = listOf(
    MainMenuEntry("converter", "converter"),
    MainMenuEntry("generator", "generator"),
    MainMenuEntry("online", "online"),
    MainMenuEntry("options", "options", flicker = false)
)

@Composable
fun MainMenuOverlay(state: AppState) {
    if (!MainMenu.open) return
    if (MainMenu.title) {
        val titleArt = remember { com.vconvertercrew.vconverter.overlay.TitleArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
        com.vconvertercrew.vconverter.overlay.FunkinTitle(titleArt, onFinish = { MainMenu.title = false })
        return
    }
    val art = remember { MainMenuArt.load(mainMenuEntries.map { it.id }, { Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val listArt = remember { MenuArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val list = MainMenu.list
    if (list == null) {
        FunkinMainMenu(
            entries = mainMenuEntries,
            art = art,
            version = "V-Converter v${AppSettings.APP_VERSION}",
            onSelect = { entry ->
                when (entry.id) {
                    "converter", "generator" -> MainMenu.list = entry.id
                    "online" -> {
                        state.tool = EditorTool.ONLINE
                        MainMenu.close()
                    }
                    else -> OptionsMenu.open = true
                }
            },
            overlay = { DiscordBadge(Modifier.align(Alignment.TopEnd).padding(16.dp)) }
        )
        return
    }
    val options = if (list == "converter") Direction.entries.map { direction ->
        MenuOption.Submenu("${direction.from.label} to ${direction.to.label}", direction.label, direction.label) {
            Tab.entries.map { tab ->
                MenuOption.Action(tab.title, "Convert ${tab.title.lowercase()} from ${direction.from.title} to ${direction.to.title}.") {
                    com.vconvertercrew.vconverter.overlay.FunkinTransition.go {
                        state.direction = direction
                        state.tab = tab
                        state.tool = null
                        MainMenu.close()
                    }
                }
            }
        }
    } else generatorTools.map { tool ->
        MenuOption.Action(tool.title, tool.hint) {
            com.vconvertercrew.vconverter.overlay.FunkinTransition.go {
                state.tool = tool
                MainMenu.close()
            }
        }
    }
    key(list) {
        FunkinOptionsMenu(
            title = if (list == "converter") "Converter" else "Generator",
            options = options,
            art = listArt,
            onClose = { MainMenu.list = null },
            overlay = { DiscordBadge(Modifier.align(Alignment.TopEnd).padding(16.dp)) }
        )
    }
}

private val generatorTools = listOf(EditorTool.CHART, EditorTool.CHARACTER, EditorTool.STAGE, EditorTool.WEEK, EditorTool.XML)

@Composable
fun OptionsButton() {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
            .background(if (OptionsMenu.open) Palette.SurfaceHigh else Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(10.dp))
            .soundClickable { OptionsMenu.open = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(OverlayIcons.Gear, contentDescription = "Options", tint = Palette.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun OptionsOverlay() {
    if (!OptionsMenu.open) return
    val art = remember { MenuArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val links = LocalUriHandler.current
    val preferences = listOf(
        MenuOption.Toggle("FPS Counter", "Shows \"FPS:\" in the top-right corner.", { AppSettings.fpsCounter }, AppSettings::updateFpsCounter),
        MenuOption.Toggle("UI Sounds", "Plays scrollMenu when you click tabs and buttons.", { AppSettings.uiSounds }, AppSettings::updateUiSounds),
        MenuOption.Toggle("Menu Music", "Plays freakyMenu while you work. It pauses while an editor plays a song.", { AppSettings.menuMusic }, AppSettings::updateMenuMusic),
        MenuOption.Stepper("Music Volume", "Left and right (or Enter) change the menu music volume.", { "${(AppSettings.musicVolume * 100).roundToInt()}%" }) { step ->
            val next = ((AppSettings.musicVolume * 10).roundToInt() + step).let { if (it > 10) 0 else if (it < 0) 10 else it }
            AppSettings.updateMusicVolume(next / 10f)
        },
        MenuOption.Toggle("Discord Status", "Shows what you are doing in V-Converter on your Discord profile (needs the Discord app open).", { AppSettings.discordStatus }, AppSettings::updateDiscordStatus),
        MenuOption.Toggle("V-Slice Preview", "V-Slice output uses the Public PlayTest layout (gameplay/songs, gameplay/characters, gameplay/stages, ui/story-mode). Off keeps the 0.7 layout.", { AppSettings.vslicePreview }, AppSettings::updateVSlicePreview),
        MenuOption.TextEntry("Online Server", "Leave it empty to connect to a V-Converter server automatically, or type the address of your own server. Press Enter on it to edit.", { AppSettings.serverUrl }, AppSettings::updateServerUrl, "Automatic"),
        MenuOption.Toggle("Error Reports", "Sends conversion errors and crashes (message, app version and platform) to the online server.", { AppSettings.errorReports }, AppSettings::updateErrorReports)
    )
    val options = listOf(
        MenuOption.Submenu("Preferences", "FPS counter, sounds, music, Discord status, the V-Slice layout, the online server and error reports.") { preferences },
        com.vconvertercrew.vconverter.overlay.TouchOptions.submenu(AppSettings::saveString),
        discordOption { links.openUri(it) }
    )
    FunkinOptionsMenu(
        "Options", options, art, onClose = { OptionsMenu.open = false },
        overlay = { DiscordBadge(Modifier.align(Alignment.TopEnd).padding(16.dp)) }
    )
}
