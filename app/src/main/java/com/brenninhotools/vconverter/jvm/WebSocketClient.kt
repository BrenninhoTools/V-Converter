package com.brenninhotools.vconverter.online

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class WebSocketClient(private val url: String, private val listener: WebSocketListener, private val maxMessage: Int = 16 * 1024 * 1024) {
    private val random = SecureRandom()
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "v-converter-websocket-writer").apply { isDaemon = true } }
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var closed = false
    @Volatile var isOpen = false
        private set

    fun connect() {
        Thread({ run() }, "v-converter-websocket").apply { isDaemon = true }.start()
    }

    private fun run() {
        var code = 1006
        var reason = "Connection lost"
        try {
            val uri = URI(url)
            val secure = uri.scheme.equals("wss", true)
            val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
            val raw = Socket()
            raw.connect(InetSocketAddress(uri.host, port), 15_000)
            val connection = if (secure) {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, uri.host, port, true).also { (it as SSLSocket).startHandshake() }
            } else raw
            connection.tcpNoDelay = true
            socket = connection
            val input = BufferedInputStream(connection.getInputStream())
            val out = connection.getOutputStream()
            output = out
            handshake(uri, input, out)
            connection.soTimeout = 0
            isOpen = true
            listener.onOpen()
            val message = ByteArrayOutputStream()
            var messageOpcode = 0
            while (!closed) {
                val b0 = input.read()
                if (b0 < 0) break
                val b1 = readByte(input)
                val fin = b0 and 0x80 != 0
                val opcode = b0 and 0x0F
                val masked = b1 and 0x80 != 0
                var length = (b1 and 0x7F).toLong()
                if (length == 126L) length = ((readByte(input) shl 8) or readByte(input)).toLong()
                else if (length == 127L) {
                    length = 0
                    repeat(8) { length = (length shl 8) or readByte(input).toLong() }
                }
                if (length > maxMessage) throw IllegalStateException("Message too large")
                val mask = if (masked) ByteArray(4).also { readFully(input, it) } else null
                val payload = ByteArray(length.toInt())
                readFully(input, payload)
                if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                when (opcode) {
                    0x0, 0x1, 0x2 -> {
                        if (opcode != 0) {
                            message.reset()
                            messageOpcode = opcode
                        }
                        if (message.size() + payload.size > maxMessage) throw IllegalStateException("Message too large")
                        message.write(payload)
                        if (fin) {
                            if (messageOpcode == 0x1) listener.onText(message.toString("UTF-8"))
                            message.reset()
                        }
                    }
                    0x8 -> {
                        code = if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 1000
                        reason = if (payload.size > 2) String(payload, 2, payload.size - 2, Charsets.UTF_8) else ""
                        frame(0x8, payload)
                        break
                    }
                    0x9 -> frame(0xA, payload)
                    else -> Unit
                }
            }
            if (closed && code == 1006) {
                code = 1000
                reason = ""
            }
        } catch (e: Throwable) {
            if (!closed) listener.onError(e)
            reason = e.message ?: reason
        } finally {
            isOpen = false
            runCatching { socket?.close() }
            writer.shutdown()
            listener.onClose(code, reason)
        }
    }

    private fun handshake(uri: URI, input: InputStream, out: OutputStream) {
        val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
        val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        val host = if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
        val request = "GET $path HTTP/1.1\r\nHost: $host\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nUser-Agent: V-Converter\r\n\r\n"
        out.write(request.toByteArray(Charsets.US_ASCII))
        out.flush()
        val header = StringBuilder()
        while (!header.endsWith("\r\n\r\n")) {
            header.append(readByte(input).toChar())
            if (header.length > 16 * 1024) throw IllegalStateException("Bad handshake")
        }
        val lines = header.split("\r\n")
        val status = lines.first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        if (status != 101) {
            throw OnlineException(
                when (status) {
                    404 -> "That room does not exist (or it expired)."
                    409 -> "That room is full."
                    else -> "The server refused the live session ($status)."
                },
                status
            )
        }
        val accept = lines.firstOrNull { it.startsWith("Sec-WebSocket-Accept:", true) }?.substringAfter(':')?.trim()
        val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII)))
        if (accept != expected) throw IllegalStateException("Bad handshake")
    }

    private fun readByte(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw EOFException()
        return b
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw EOFException()
            offset += read
        }
    }

    private fun frame(opcode: Int, payload: ByteArray): Boolean {
        val out = output ?: return false
        val mask = ByteArray(4).also { random.nextBytes(it) }
        val header = ByteArrayOutputStream()
        header.write(0x80 or opcode)
        when {
            payload.size < 126 -> header.write(0x80 or payload.size)
            payload.size <= 0xFFFF -> {
                header.write(0x80 or 126)
                header.write(payload.size shr 8)
                header.write(payload.size and 0xFF)
            }
            else -> {
                header.write(0x80 or 127)
                val size = payload.size.toLong()
                for (shift in 56 downTo 0 step 8) header.write(((size shr shift) and 0xFF).toInt())
            }
        }
        header.write(mask)
        val masked = ByteArray(payload.size) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        val bytes = header.toByteArray() + masked
        return try {
            writer.execute {
                try {
                    out.write(bytes)
                    out.flush()
                } catch (e: Exception) {
                    runCatching { socket?.close() }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun send(text: String): Boolean = isOpen && frame(0x1, text.toByteArray(Charsets.UTF_8))

    fun close() {
        if (closed) return
        closed = true
        if (isOpen) frame(0x8, byteArrayOf(0x03, 0xE8.toByte()))
        runCatching {
            writer.execute { runCatching { socket?.close() } }
            writer.shutdown()
        }.onFailure { runCatching { socket?.close() } }
    }

    private companion object {
        const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
