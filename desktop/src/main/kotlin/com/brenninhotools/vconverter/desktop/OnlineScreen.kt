package com.brenninhotools.vconverter.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.onlineview.ModGalleryPanel
import com.brenninhotools.vconverter.onlineview.ModSharePanel
import com.brenninhotools.vconverter.onlineview.OnlineNewsPanel
import com.brenninhotools.vconverter.onlineview.PublicRoomsPanel
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
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                ModGalleryPanel(online, onDownload = { mod ->
                    val target = Dialogs.saveFile("Save the mod", mod.fileName) ?: return@ModGalleryPanel
                    online.download(mod, open = { FileOutputStream(target) }) { ok -> if (ok) state.lastOutput = target }
                })
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                PublicRoomsPanel(online, onJoin = { room ->
                    val live = when (room.kind) {
                        "character" -> state.characterEditor.live.also { state.tool = EditorTool.CHARACTER }
                        "stage" -> state.stageEditor.live.also { state.tool = EditorTool.STAGE }
                        "week" -> state.weekEditor.live.also { state.tool = EditorTool.WEEK }
                        else -> state.editor.live.also { state.tool = EditorTool.CHART }
                    }
                    live.join(room.code)
                })
            }
            Card { OnlineNewsPanel(online, onOpenLink = ::openLink) }
            Card {
                ModSharePanel(online, onPickFile = {
                    val file = Dialogs.openFiles("Pick the mod .zip").firstOrNull { it.extension.equals("zip", true) }
                    if (file != null) {
                        online.file = file.name to file.readBytes()
                        if (online.title.isBlank()) online.title = file.nameWithoutExtension
                    }
                })
            }
        }
    }
}
