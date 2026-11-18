package com.vconvertercrew.vconverter.web

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
import com.vconvertercrew.vconverter.platform.File

@Composable
fun OnlineScreen(state: AppState) {
    val online = state.online
    val art = remember { OnlineMenuArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val actions = remember(state) {
        OnlineActions(
            download = { mod ->
                val target = File(Dialogs.exports, mod.fileName.replace('/', '_'))
                online.download(mod, open = { target.outputStream() }) { ok ->
                    if (ok) {
                        state.lastOutput = target
                        Dialogs.export(target)
                    }
                }
            },
            pickFile = {
                Dialogs.openFile("Pick the mod .zip", setOf("zip")) { file ->
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
            openLink = { Dialogs.openLink(it) }
        )
    }
    FunkinOnlineMenu(
        online, art, actions,
        onBack = { MainMenu.open = true },
        overlay = { DiscordBadge(Modifier.align(Alignment.BottomStart).padding(16.dp)) }
    )
}
