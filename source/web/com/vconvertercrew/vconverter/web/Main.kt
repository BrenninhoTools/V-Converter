package com.vconvertercrew.vconverter.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.vconvertercrew.vconverter.platform.Browser
import kotlinx.browser.document
import kotlin.js.js

private fun jsReady(): Unit = js("VC.ready()")

fun runOnMain(block: () -> Unit) {
    Browser.timeout(0) { block() }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    AppSettings.applyOnline()
    ComposeViewport(document.body!!) {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        AppTheme {
            BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
                CompositionLocalProvider(LocalCompact provides (maxWidth < 760.dp || maxHeight < 500.dp)) {
                    App(state)
                }
                MainMenuOverlay(state)
                OptionsOverlay()
                com.vconvertercrew.vconverter.overlay.FunkinTransitionOverlay()
            }
            UiSounds()
            MenuMusic(suppressed = state.editor.editor.playing)
        }
    }
    jsReady()
}
