package com.brenninhotools.vconverter.online

import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import kotlin.concurrent.AtomicInt

class WebSocketClient(private val url: String, private val listener: WebSocketListener, private val maxMessage: Int = 16 * 1024 * 1024) {
    private var task: NSURLSessionWebSocketTask? = null
    private var session: NSURLSession? = null
    private val state = AtomicInt(0)

    val isOpen: Boolean get() = state.value == 1

    fun connect() {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            listener.onClose(1006, "Invalid room address.")
            return
        }
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
        val s = NSURLSession.sessionWithConfiguration(configuration)
        val t = s.webSocketTaskWithURL(nsUrl)
        t.maximumMessageSize = maxMessage.toLong()
        session = s
        task = t
        t.resume()
        state.value = 1
        listener.onOpen()
        receive(t)
    }

    private fun receive(t: NSURLSessionWebSocketTask) {
        t.receiveMessageWithCompletionHandler { message: NSURLSessionWebSocketMessage?, error ->
            if (error != null || message == null) {
                finish(1006, error?.localizedDescription ?: "Connection lost")
                return@receiveMessageWithCompletionHandler
            }
            message.string?.let { text -> runCatching { listener.onText(text) }.onFailure { listener.onError(it) } }
            if (state.value == 1) receive(t)
        }
    }

    private fun finish(code: Int, reason: String) {
        if (state.compareAndSet(1, 2) || state.compareAndSet(0, 2)) {
            runCatching { task?.cancel() }
            runCatching { session?.invalidateAndCancel() }
            listener.onClose(code, reason)
        }
    }

    fun send(text: String): Boolean {
        val t = task ?: return false
        if (!isOpen) return false
        t.sendMessage(NSURLSessionWebSocketMessage(text)) { error ->
            if (error != null) finish(1006, error.localizedDescription)
        }
        return true
    }

    fun close() {
        val t = task
        if (state.compareAndSet(1, 2)) {
            runCatching { t?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, null) }
            runCatching { session?.invalidateAndCancel() }
            listener.onClose(1000, "Closed")
        }
    }
}
