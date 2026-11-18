package com.vconvertercrew.vconverter.onlineview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vconvertercrew.vconverter.online.Online
import com.vconvertercrew.vconverter.online.ServerDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AutoServer {
    var url by mutableStateOf("")
        private set
    var searching by mutableStateOf(false)
        private set
    var checked by mutableStateOf(false)
        private set

    fun start(scope: CoroutineScope) {
        if (url.isEmpty() && !searching) refresh(scope)
    }

    fun refresh(scope: CoroutineScope) {
        if (searching) return
        searching = true
        scope.launch {
            try {
                val found = withContext(ioDispatcher) { ServerDirectory.discover(listOfNotNull(Online.autoServer.takeIf { it.isNotEmpty() })) }.orEmpty()
                Online.autoServer = found
                url = found
                checked = true
            } finally {
                searching = false
            }
        }
    }
}
