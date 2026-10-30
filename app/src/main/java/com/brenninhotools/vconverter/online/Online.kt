package com.brenninhotools.vconverter.online

import java.util.concurrent.ConcurrentHashMap

object Online {
    @Volatile var serverUrl: String = ""
    @Volatile var reportsEnabled: Boolean = false
    @Volatile var platform: String = "android"
    @Volatile var appVersion: String = "0.0.1"

    val isConfigured: Boolean get() = OnlineApi.normalize(serverUrl).isNotEmpty()

    fun api(): OnlineApi = OnlineApi(serverUrl)
}

object ErrorReports {
    private val recent = ConcurrentHashMap<String, Long>()

    fun send(kind: String, message: String, error: Throwable? = null, context: Map<String, String> = emptyMap(), stack: String = "") {
        if (!Online.reportsEnabled || !Online.isConfigured || message.isBlank()) return
        val key = "$kind:$message"
        val now = System.currentTimeMillis()
        val last = recent[key]
        if (last != null && now - last < 60_000) return
        recent[key] = now
        val trace = error?.stackTraceToString() ?: stack
        Thread({
            runCatching { Online.api().report(kind, Online.platform, Online.appVersion, message, trace, context) }
        }, "v-converter-report").apply { isDaemon = true }.start()
    }
}
