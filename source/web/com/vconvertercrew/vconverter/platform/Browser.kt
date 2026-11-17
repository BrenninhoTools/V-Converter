package com.vconvertercrew.vconverter.platform

import kotlin.js.JsAny
import kotlin.js.js
import kotlin.math.min

private fun jsNewBytes(size: Int): JsAny = js("new Uint8Array(size)")
private fun jsPutLatin1(array: JsAny, offset: Int, text: String): Unit = js("VC.putLatin1(array, offset, text)")
private fun jsGetLatin1(array: JsAny, start: Int, end: Int): String = js("VC.getLatin1(array, start, end)")
private fun jsLength(array: JsAny): Int = js("array.length")
private fun jsStorageGet(key: String): String? = js("VC.storageGet(key)")
private fun jsStorageSet(key: String, value: String): Unit = js("VC.storageSet(key, value)")
private fun jsOpen(url: String): Unit = js("VC.open(url)")
private fun jsDownload(name: String, bytes: JsAny): Unit = js("VC.download(name, bytes)")
private fun jsTimeout(handler: () -> Unit, delay: Int): Int = js("setTimeout(handler, delay)")
private fun jsClearTimeout(id: Int): Unit = js("clearTimeout(id)")
private fun jsNow(): Double = js("Date.now()")
private fun jsPerformance(): Double = js("performance.now()")
private fun jsUuid(): String = js("(self.crypto && self.crypto.randomUUID) ? self.crypto.randomUUID() : (Date.now().toString(16) + '-' + Math.random().toString(16).slice(2))")

object Browser {
    private const val CHUNK = 1 shl 16

    fun toJs(bytes: ByteArray): JsAny {
        val array = jsNewBytes(bytes.size)
        var offset = 0
        while (offset < bytes.size) {
            val end = min(bytes.size, offset + CHUNK)
            val start = offset
            val chars = CharArray(end - start) { (bytes[start + it].toInt() and 0xFF).toChar() }
            jsPutLatin1(array, start, chars.concatToString())
            offset = end
        }
        return array
    }

    fun toBytes(array: JsAny): ByteArray {
        val size = jsLength(array)
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val end = min(size, offset + CHUNK)
            val text = jsGetLatin1(array, offset, end)
            for (i in text.indices) out[offset + i] = text[i].code.toByte()
            offset = end
        }
        return out
    }

    fun latin1(text: String): ByteArray = ByteArray(text.length) { text[it].code.toByte() }

    fun storageGet(key: String): String? = jsStorageGet(key)

    fun storageSet(key: String, value: String) = jsStorageSet(key, value)

    fun open(url: String) = jsOpen(url)

    fun download(name: String, bytes: ByteArray) = jsDownload(name, toJs(bytes))

    fun timeout(delayMs: Int, handler: () -> Unit): Int = jsTimeout(handler, delayMs)

    fun clearTimeout(id: Int) = jsClearTimeout(id)

    fun now(): Double = jsNow()

    fun performanceNow(): Double = jsPerformance()

    fun uuid(): String = jsUuid()
}
