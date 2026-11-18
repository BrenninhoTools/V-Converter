package com.vconvertercrew.vconverter.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vconvertercrew.vconverter.onlineview.FunkinOnlineMenu
import com.vconvertercrew.vconverter.onlineview.OnlineActions
import com.vconvertercrew.vconverter.onlineview.OnlineMenuArt
import com.vconvertercrew.vconverter.overlay.DiscordBadge
import java.awt.Desktop
import java.io.FileOutputStream
import java.net.URI

private fun openLink(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure {
        runCatching { ProcessBuilder(if (System.getProperty("os.name").lowercase().contains("mac")) "open" else "xdg-open", url).start() }
    }
}

@Composable
fun OnlineScreen(state: AppState) {
    val online = state.online
    val art = remember { OnlineMenuArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val actions = remember(state) {
        OnlineActions(
            download = { mod ->
                val target = Dialogs.saveFile("Save the mod", mod.fileName)
                if (target != null) online.download(mod, open = { FileOutputStream(target) }) { ok -> if (ok) state.lastOutput = target }
            },
            pickFile = {
                val file = Dialogs.openFiles("Pick the mod .zip").firstOrNull { it.extension.equals("zip", true) }
                if (file != null) {
                    online.file = file.name to file.readBytes()
                    if (online.title.isBlank()) online.title = file.nameWithoutExtension
                }
            },
            join = { room ->
                val live = when (room.kind) {
                    "character" -> state.characterEditor.live.also { state.tool = EditorTool.CHARACTER }
                    "stage" -> state.stageEditor.live.also { state.tool = EditorTool.STAGE }
                    "week" -> state.weekEditor.live.also { state.tool = EditorTool.WEEK }
                    else -> state.editor.live.also { state.tool = EditorTool.CHART }
                }
                live.join(room.code)
            },
            openLink = ::openLink
        )
    }
    FunkinOnlineMenu(
        online, art, actions,
        onBack = { MainMenu.open = true },
        overlay = { DiscordBadge(Modifier.align(Alignment.BottomStart).padding(16.dp)) }
    )
}
