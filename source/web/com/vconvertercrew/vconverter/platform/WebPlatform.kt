package com.vconvertercrew.vconverter.platform

import kotlin.js.JsAny
import kotlin.js.js

private fun jsXhr(method: String, url: String, headers: String, body: JsAny?): JsAny = js("VC.xhr(method, url, headers, body)")
private fun jsInt(obj: JsAny, name: String): Int = js("(obj[name] | 0)")
private fun jsText(obj: JsAny, name: String): String = js("String(obj[name])")

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class Volatile

class PlatformLock {
    fun <T> withLock(block: () -> T): T = block()
}

class Scheduled internal constructor() {
    internal var id = -1
    internal var cancelled = false

    fun cancel() {
        cancelled = true
        if (id >= 0) Browser.clearTimeout(id)
    }
}

object Platform {
    private const val CACHE_LIMIT = 1 shl 20
    private val resources = HashMap<String, ByteArray>()

    fun currentTimeMillis(): Long = Browser.now().toLong()

    fun nanoTime(): Long = (Browser.performanceNow() * 1_000_000.0).toLong()

    fun randomId(): String = Browser.uuid().lowercase()

    fun inflate(data: ByteArray): ByteArray = Inflate.inflate(data, 2, data.size - 2)

    fun background(block: () -> Unit) {
        Browser.timeout(0) { block() }
    }

    fun schedule(delayMs: Long, block: () -> Unit): Scheduled {
        val handle = Scheduled()
        handle.id = Browser.timeout(delayMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) {
            if (!handle.cancelled) block()
        }
        return handle
    }

    fun resourceBytes(name: String): ByteArray? {
        val clean = name.trimStart('/')
        resources[clean]?.let { return it }
        val bytes = runCatching { Http.request(clean, "GET", emptyMap(), null, 0) }.getOrNull()?.takeIf { it.status in 200..299 }?.body ?: return null
        if (bytes.size <= CACHE_LIMIT) resources[clean] = bytes
        return bytes
    }

    fun errorName(error: Throwable): String = error::class.simpleName ?: "Error"

    fun stackTrace(error: Throwable): String = error.stackTraceToString()
}

class HttpResponse(val status: Int, val body: ByteArray, val headers: Map<String, String>)

object Http {
    private val blocked = setOf("user-agent", "accept-encoding", "connection", "content-length", "host")

    fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeoutMs: Int,
        onProgress: ((Long, Long) -> Unit)? = null
    ): HttpResponse {
        val headerText = headers.filterKeys { it.lowercase() !in blocked }.entries.joinToString("\n") { "${it.key}:${it.value}" }
        val result = jsXhr(method, url, headerText, body?.let { Browser.toJs(it) })
        val status = jsInt(result, "status")
        if (status == 0) throw IOException(jsText(result, "error").ifBlank { "Could not reach $url" })
        val bytes = Browser.latin1(jsText(result, "text"))
        val map = HashMap<String, String>()
        jsText(result, "headers").lineSequence().forEach { line ->
            val at = line.indexOf(':')
            if (at > 0) map[line.substring(0, at).trim().lowercase()] = line.substring(at + 1).trim()
        }
        onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        return HttpResponse(status, bytes, map)
    }
}
