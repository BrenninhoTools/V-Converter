package com.brenninhotools.vconverter.online

interface WebSocketListener {
    fun onOpen() {}
    fun onText(text: String)
    fun onClose(code: Int, reason: String) {}
    fun onError(error: Throwable) {}
}
