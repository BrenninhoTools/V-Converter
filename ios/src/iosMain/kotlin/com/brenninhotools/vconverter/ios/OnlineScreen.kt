package com.brenninhotools.vconverter.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.onlineview.ModGalleryPanel
import com.brenninhotools.vconverter.onlineview.ModSharePanel
import com.brenninhotools.vconverter.onlineview.OnlineNewsPanel
import com.brenninhotools.vconverter.onlineview.PublicRoomsPanel
import com.brenninhotools.vconverter.platform.File

@Composable
fun OnlineScreen(state: AppState) {
    val online = state.online
    AdaptiveRow {
        Column(Modifier.pane(1.4f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                ModGalleryPanel(online, onDownload = { mod ->
                    val target = File(Dialogs.exports, mod.fileName.replace('/', '_'))
                    online.download(mod, open = { target.outputStream() }) { ok ->
                        if (ok) {
                            state.lastOutput = target
                            Dialogs.export(target)
                        }
                    }
                })
            }
        }
        Column(Modifier.pane(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Card { OnlineNewsPanel(online, onOpenLink = { Dialogs.openLink(it) }) }
            Card {
                ModSharePanel(online, onPickFile = {
                    Dialogs.openFile("Pick the mod .zip", setOf("zip")) { file ->
                        online.file = file.name to file.readBytes()
                        if (online.title.isBlank()) online.title = file.nameWithoutExtension
                    }
                })
            }
        }
    }
}
