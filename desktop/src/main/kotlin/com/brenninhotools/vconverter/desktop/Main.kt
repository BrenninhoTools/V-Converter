package com.brenninhotools.vconverter.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import com.brenninhotools.vconverter.editorview.EditorTyping
import javax.swing.UIManager

fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    AppSettings.applyOnline()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        val online = com.brenninhotools.vconverter.online.Online
        if (online.reportsEnabled && online.isConfigured) {
            runCatching { online.api().report("crash", online.platform, online.appVersion, error.toString().take(300), error.stackTraceToString()) }
        }
        previous?.uncaughtException(thread, error)
    }
    application {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val state = remember { AppState(scope) }
        val icon = remember { Resources.image("icon.png")?.let { BitmapPainter(it) } }
        val windowState = rememberWindowState(size = DpSize(1240.dp, 820.dp))
        Window(
            onCloseRequest = {
                scope.cancel()
                exitApplication()
            },
            title = "V-Converter",
            icon = icon,
            state = windowState,
            onKeyEvent = { event ->
                if (state.tool == EditorTool.PLAYTEST && EditorTyping.fields == 0) state.playtest.key(event)
                else event.type == KeyEventType.KeyDown && when (state.tool) {
                    EditorTool.CHART -> editorShortcut(state.editor.editor, event)
                    EditorTool.CHARACTER -> characterShortcut(state.characterEditor.editor, event)
                    EditorTool.STAGE -> stageShortcut(state.stageEditor.editor, event)
                    EditorTool.WEEK -> weekShortcut(state.weekEditor.editor, event)
                    EditorTool.ONLINE -> false
                    EditorTool.PLAYTEST -> false
                    null -> false
                }
            }
        ) {
            window.minimumSize = java.awt.Dimension(980, 640)
            DisposableEffect(Unit) {
                AppCursor.cursor?.let { window.cursor = it }
                Dialogs.owner = window
                window.dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
                    override fun drop(event: DropTargetDropEvent) {
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        val dropped = runCatching {
                            (event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
                        }.getOrDefault(emptyList())
                        event.dropComplete(dropped.isNotEmpty())
                        state.drop(dropped)
                    }
                }, true)
                onDispose { Dialogs.owner = null }
            }
            AppTheme {
                val cursor = AppCursor.cursor
                Box(if (cursor != null) Modifier.fillMaxSize().pointerHoverIcon(PointerIcon(cursor), overrideDescendants = true) else Modifier.fillMaxSize()) {
                    App(state)
                    UiSounds()
                    MenuMusic(suppressed = windowState.isMinimized || state.editor.editor.playing)
                }
            }
        }
    }
}
