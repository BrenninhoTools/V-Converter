package com.brenninhotools.vconverter.online

import com.brenninhotools.vconverter.platform.Platform
import com.brenninhotools.vconverter.platform.PlatformLock
import kotlin.concurrent.Volatile

object Online {
    @Volatile var serverUrl: String = ""
    @Volatile var reportsEnabled: Boolean = false
    @Volatile var platform: String = "android"
    @Volatile var appVersion: String = "0.0.1"

    val isConfigured: Boolean get() = OnlineApi.normalize(serverUrl).isNotEmpty()

    fun api(): OnlineApi = OnlineApi(serverUrl)
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
