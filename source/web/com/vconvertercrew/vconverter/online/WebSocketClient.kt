package com.vconvertercrew.vconverter.online

import kotlin.js.JsAny
import kotlin.js.js

private fun jsSocket(url: String, onOpen: () -> Unit, onText: (String) -> Unit, onClose: (Int, String) -> Unit): JsAny? = js("VC.socket(url, onOpen, onText, onClose)")
private fun jsSocketSend(socket: JsAny, text: String): Boolean = js("VC.socketSend(socket, text)")
private fun jsSocketClose(socket: JsAny): Unit = js("VC.socketClose(socket)")

class WebSocketClient(private val url: String, private val listener: WebSocketListener, private val maxMessage: Int = 16 * 1024 * 1024) {
    private var socket: JsAny? = null
    private var state = 0

    val isOpen: Boolean get() = state == 1

    fun connect() {
        socket = jsSocket(
            url,
            onOpen = {
                if (state == 0) {
                    state = 1
                    listener.onOpen()
                }
            },
            onText = { text ->
                if (text.length <= maxMessage) runCatching { listener.onText(text) }.onFailure { listener.onError(it) }
            },
            onClose = { code, reason -> finish(code, reason) }
        )
    }

    private fun finish(code: Int, reason: String) {
        if (state == 2) return
        state = 2
        listener.onClose(code, reason)
    }

    fun send(text: String): Boolean {
        val s = socket ?: return false
        if (!isOpen) return false
        return jsSocketSend(s, text)
    }

    fun close() {
        val s = socket
        if (state == 1) {
            state = 2
            s?.let { jsSocketClose(it) }
            listener.onClose(1000, "Closed")
        }
    }
}
