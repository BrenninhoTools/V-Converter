package com.brenninhotools.vconverter.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUUID
import platform.Foundation.dataWithBytes
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import platform.posix.memcpy
import kotlin.concurrent.AtomicInt
import kotlin.time.TimeSource

class PlatformLock {
    private val lock = NSRecursiveLock()

    fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

class Scheduled internal constructor() {
    internal val cancelled = AtomicInt(0)

    fun cancel() {
        cancelled.value = 1
    }
}

object Platform {
    private val start = TimeSource.Monotonic.markNow()

    fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    fun nanoTime(): Long = start.elapsedNow().inWholeNanoseconds

    fun randomId(): String = NSUUID().UUIDString.lowercase()

    fun background(block: () -> Unit) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { block() }
    }

    fun schedule(delayMs: Long, block: () -> Unit): Scheduled {
        val handle = Scheduled()
        val time = dispatch_time(DISPATCH_TIME_NOW, delayMs.coerceAtLeast(0) * 1_000_000)
        dispatch_after(time, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (handle.cancelled.value == 0) block()
        }
        return handle
    }

    fun resourceBytes(name: String): ByteArray? {
        val clean = name.trimStart('/')
        val root = NSBundle.mainBundle.resourcePath ?: return null
        val candidates = listOf("$root/$clean", "$root/assets/$clean")
        for (path in candidates) {
            val data = NSData.dataWithContentsOfFile(path) ?: continue
            return data.toByteArray()
        }
        return null
    }

    fun errorName(error: Throwable): String = error::class.simpleName ?: "Error"

    fun stackTrace(error: Throwable): String = error.stackTraceToString()
}

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}

fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), size.toULong()) }
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
        val nsUrl = NSURL.URLWithString(url) ?: throw IOException("Invalid address: $url")
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.setHTTPMethod(method)
        request.setTimeoutInterval(timeoutMs / 1000.0 * 3)
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        if (body != null) request.setHTTPBody(body.toNSData())
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.timeoutIntervalForRequest = timeoutMs / 1000.0
        val session = NSURLSession.sessionWithConfiguration(configuration)
        val semaphore = dispatch_semaphore_create(0)
        var result: HttpResponse? = null
        var failure: String? = null
        val task = session.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                failure = error.localizedDescription
            } else {
                val http = response as? NSHTTPURLResponse
                val bytes = data?.toByteArray() ?: ByteArray(0)
                val map = HashMap<String, String>()
                http?.allHeaderFields?.forEach { (k, v) -> map[k.toString().lowercase()] = v.toString() }
                result = HttpResponse(http?.statusCode?.toInt() ?: 0, bytes, map)
            }
            dispatch_semaphore_signal(semaphore)
        }
        task.resume()
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        session.finishTasksAndInvalidate()
        failure?.let { throw IOException(it) }
        val response = result ?: throw IOException("No response from the server.")
        onProgress?.invoke(response.body.size.toLong(), response.body.size.toLong())
        return response
    }
}
