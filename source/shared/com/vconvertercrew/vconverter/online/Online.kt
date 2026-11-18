package com.vconvertercrew.vconverter.online

import com.vconvertercrew.vconverter.platform.Platform
import com.vconvertercrew.vconverter.platform.PlatformLock
import com.vconvertercrew.vconverter.platform.Volatile

object Online {
    @Volatile var serverUrl: String = ""
    @Volatile var autoServer: String = ""
    @Volatile var reportsEnabled: Boolean = false
    @Volatile var platform: String = "android"
    @Volatile var appVersion: String = "0.1.0-playtest"

    val activeUrl: String get() = OnlineApi.normalize(serverUrl).ifEmpty { autoServer }

    val isAutomatic: Boolean get() = OnlineApi.normalize(serverUrl).isEmpty()

    val isConfigured: Boolean get() = activeUrl.isNotEmpty()

    fun api(): OnlineApi = OnlineApi(activeUrl)
}

object ErrorReports {
    private val recent = HashMap<String, Long>()
    private val lock = PlatformLock()

    fun send(kind: String, message: String, error: Throwable? = null, context: Map<String, String> = emptyMap(), stack: String = "") {
        if (!Online.reportsEnabled || !Online.isConfigured || message.isBlank()) return
        val key = "$kind:$message"
        val now = Platform.currentTimeMillis()
        val fresh = lock.withLock {
            val last = recent[key]
            if (last != null && now - last < 60_000) false else {
                recent[key] = now
                true
            }
        }
        if (!fresh) return
        val trace = error?.stackTraceToString() ?: stack
        Platform.background {
            runCatching { Online.api().report(kind, Online.platform, Online.appVersion, message, trace, context) }
        }
    }
}
