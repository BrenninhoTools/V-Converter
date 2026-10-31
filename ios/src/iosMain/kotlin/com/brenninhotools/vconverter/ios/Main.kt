package com.brenninhotools.vconverter.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.brenninhotools.vconverter.online.Online
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.experimental.ExperimentalNativeApi

fun runOnMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}

@OptIn(ExperimentalNativeApi::class)
private fun installCrashReports() {
    val previous = setUnhandledExceptionHook { error ->
        if (Online.reportsEnabled && Online.isConfigured) {
            runCatching { Online.api().report("crash", Online.platform, Online.appVersion, error.toString().take(300), error.stackTraceToString()) }
        }
    }
    if (previous != null) Unit
}

fun MainViewController(): UIViewController {
    AppSettings.applyOnline()
    installCrashReports()
    return ComposeUIViewController {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        AppTheme {
            BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                CompositionLocalProvider(LocalCompact provides (maxWidth < 760.dp)) {
                    App(state)
                }
            }
            UiSounds()
            MenuMusic(suppressed = state.editor.editor.playing)
        }
    }
}
