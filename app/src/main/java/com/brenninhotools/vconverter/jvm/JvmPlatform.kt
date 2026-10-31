package com.brenninhotools.vconverter.platform

import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

typealias InputStream = java.io.InputStream
typealias OutputStream = java.io.OutputStream
typealias IOException = java.io.IOException
typealias ByteArrayInputStream = java.io.ByteArrayInputStream
typealias ByteArrayOutputStream = java.io.ByteArrayOutputStream

class PlatformLock {
    private val lock = ReentrantLock()

    fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

class Scheduled(private val task: java.util.TimerTask) {
    fun cancel() {
        task.cancel()
    }
}

object Platform {
    private val timer by lazy { java.util.Timer("v-converter-timer", true) }

    fun currentTimeMillis(): Long = System.currentTimeMillis()

    fun nanoTime(): Long = System.nanoTime()

    fun schedule(delayMs: Long, block: () -> Unit): Scheduled {
        val task = object : java.util.TimerTask() {
            override fun run() = block()
        }
        timer.schedule(task, delayMs.coerceAtLeast(0))
        return Scheduled(task)
    }

    fun randomId(): String = UUID.randomUUID().toString()

    fun background(block: () -> Unit) {
        Thread(block, "v-converter-background").apply { isDaemon = true }.start()
    }

    fun resourceBytes(name: String): ByteArray? =
        Platform::class.java.getResourceAsStream("/" + name.trimStart('/'))?.use { it.readBytes() }

    fun errorName(error: Throwable): String = error.javaClass.simpleName

    fun stackTrace(error: Throwable): String = error.stackTraceToString()
}

class HttpResponse(val status: Int, val body: ByteArray, val headers: Map<String, String>)

object Http {
    fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeoutMs: Int,
        onProgress: ((Long, Long) -> Unit)? = null
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs * 3
            connection.useCaches = false
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val total = connection.contentLengthLong
            val out = java.io.ByteArrayOutputStream()
            stream?.use { input ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    onProgress?.invoke(done, total)
                }
            }
            val responseHeaders = connection.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(",") }.mapKeys { it.key.lowercase() }
            return HttpResponse(status, out.toByteArray(), responseHeaders)
        } finally {
            connection.disconnect()
        }
    }
}

fun ByteArray.inputStream(): InputStream = java.io.ByteArrayInputStream(this)

fun String.byteInputStream(): InputStream = java.io.ByteArrayInputStream(encodeToByteArray())
