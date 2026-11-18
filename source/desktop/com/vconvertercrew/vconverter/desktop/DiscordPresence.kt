package com.vconvertercrew.vconverter.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.online.Discord
import com.vconvertercrew.vconverter.overlay.DiscordAccount
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.UUID

data class PresenceState(val details: String, val state: String?, val avatar: String?, val name: String?)

object DiscordPresence {
    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1
    private const val OP_CLOSE = 2
    private const val OP_PING = 3
    private const val OP_PONG = 4
    private const val RETRY_MS = 15_000L

    private val lock = Object()
    private val started = System.currentTimeMillis() / 1000
    private var desired: PresenceState? = null
    private var version = 0
    private var thread: Thread? = null

    fun update(presence: PresenceState?) {
        synchronized(lock) {
            if (presence == desired) return
            desired = presence
            version++
            lock.notifyAll()
            if (thread == null) {
                thread = Thread(::loop, "discord-rpc").apply {
                    isDaemon = true
                    start()
                }
            }
        }
    }

    private fun loop() {
        var connection: Ipc? = null
        var sent = -1
        var lastAttempt = 0L
        while (true) {
            val (wanted, wantedVersion) = synchronized(lock) {
                while (version == sent) lock.wait(RETRY_MS)
                desired to version
            }
            if (connection == null) {
                val now = System.currentTimeMillis()
                if (wanted == null) {
                    sent = wantedVersion
                    continue
                }
                if (now - lastAttempt < RETRY_MS) {
                    synchronized(lock) { lock.wait(RETRY_MS - (now - lastAttempt)) }
                    continue
                }
                lastAttempt = now
                connection = runCatching { Ipc.connect() }.getOrNull()
                if (connection == null) continue
            }
            try {
                connection.setActivity(wanted?.let(::activity))
                sent = wantedVersion
            } catch (e: Exception) {
                runCatching { connection.close() }
                connection = null
            }
        }
    }

    private fun activity(presence: PresenceState): JSONObject {
        val assets = JSONObject()
            .put("large_image", Discord.LARGE_IMAGE)
            .put("large_text", "V-Converter v${AppSettings.APP_VERSION}")
        if (!presence.avatar.isNullOrBlank()) {
            assets.put("small_image", presence.avatar)
            assets.put("small_text", presence.name ?: "")
        }
        val activity = JSONObject()
            .put("details", presence.details.take(128))
            .put("timestamps", JSONObject().put("start", started))
            .put("assets", assets)
            .put("instance", false)
        presence.state?.takeIf { it.length >= 2 }?.let { activity.put("state", it.take(128)) }
        return activity
    }

    private class Ipc(private val input: InputStream, private val output: OutputStream, private val handle: Closeable) : Closeable {
        fun write(op: Int, payload: JSONObject) {
            val body = payload.toString().encodeToByteArray()
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array()
            output.write(header + body)
            output.flush()
        }

        fun read(): Pair<Int, JSONObject> {
            val header = input.readNBytes(8)
            if (header.size < 8) throw EOFException("Discord closed the connection.")
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val op = buffer.int
            val length = buffer.int
            if (length < 0 || length > 1 shl 20) throw IOException("Bad Discord frame.")
            val body = input.readNBytes(length)
            if (body.size < length) throw EOFException("Discord closed the connection.")
            return op to runCatching { JSONObject(body.decodeToString()) }.getOrElse { JSONObject() }
        }

        fun reply(nonce: String?): JSONObject {
            while (true) {
                val (op, payload) = read()
                when (op) {
                    OP_PING -> write(OP_PONG, payload)
                    OP_CLOSE -> throw IOException(payload.optString("message", "Discord closed the connection."))
                    OP_FRAME -> if (nonce == null || payload.optString("nonce") == nonce) {
                        if (payload.optString("evt") == "ERROR") throw IOException(payload.optJSONObject("data")?.optString("message") ?: "Discord refused the status.")
                        return payload
                    }
                }
            }
        }

        fun setActivity(activity: JSONObject?) {
            val nonce = UUID.randomUUID().toString()
            val args = JSONObject().put("pid", ProcessHandle.current().pid())
            if (activity != null) args.put("activity", activity)
            write(OP_FRAME, JSONObject().put("cmd", "SET_ACTIVITY").put("args", args).put("nonce", nonce))
            reply(nonce)
        }

        override fun close() {
            runCatching { write(OP_CLOSE, JSONObject()) }
            handle.close()
        }

        companion object {
            fun connect(): Ipc? {
                for (path in candidates()) {
                    val ipc = runCatching { open(path) }.getOrNull() ?: continue
                    try {
                        ipc.write(OP_HANDSHAKE, JSONObject().put("v", 1).put("client_id", Discord.APPLICATION_ID))
                        val ready = ipc.reply(null)
                        if (ready.optString("evt") == "READY") return ipc
                        ipc.close()
                    } catch (e: Exception) {
                        runCatching { ipc.handle.close() }
                    }
                }
                return null
            }

            private val windows = System.getProperty("os.name").lowercase().contains("win")

            private fun candidates(): List<String> {
                if (windows) return (0..9).map { "\\\\.\\pipe\\discord-ipc-$it" }
                val bases = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) } + "/tmp"
                val folders = listOf("", "app/com.discordapp.Discord/", "app/com.discordapp.DiscordCanary/", "snap.discord/", ".flatpak/com.discordapp.Discord/xdg-run/", ".flatpak/dev.vencord.Vesktop/xdg-run/")
                return bases.distinct().flatMap { base -> folders.flatMap { folder -> (0..9).map { "${base.trimEnd('/')}/$folder" + "discord-ipc-$it" } } }
                    .filter { File(it).exists() }
            }

            private fun open(path: String): Ipc {
                if (windows) {
                    val pipe = RandomAccessFile(path, "rw")
                    val input = object : InputStream() {
                        override fun read(): Int = pipe.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = pipe.read(b, off, len)
                    }
                    val output = object : OutputStream() {
                        override fun write(b: Int) = pipe.write(b)
                        override fun write(b: ByteArray, off: Int, len: Int) = pipe.write(b, off, len)
                    }
                    return Ipc(input, output, pipe)
                }
                val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                try {
                    channel.connect(UnixDomainSocketAddress.of(path))
                } catch (e: Exception) {
                    channel.close()
                    throw e
                }
                return Ipc(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel)
            }
        }
    }
}

@Composable
fun DiscordStatus(state: AppState) {
    LaunchedEffect(state) {
        snapshotFlow {
            if (!AppSettings.discordStatus) return@snapshotFlow null
            val tool = state.tool
            val (details, line) = when {
                MainMenu.open && MainMenu.title -> "In the title screen" to null
                OptionsMenu.open -> "In the options" to null
                MainMenu.open && MainMenu.list == "converter" -> "Picking a converter" to null
                MainMenu.open && MainMenu.list == "generator" -> "Picking an editor" to null
                MainMenu.open -> "In the main menu" to null
                tool == null -> "Converting ${state.direction.from.title} to ${state.direction.to.title}" to state.tab.title
                tool == EditorTool.ONLINE -> "Browsing the online gallery" to null
                tool == EditorTool.PLAYTEST -> "PlayTesting a song" to null
                else -> "In the ${tool.title}" to null
            }
            val profile = DiscordAccount.profile
            PresenceState(details, line, profile?.avatarUrl, profile?.name)
        }.collect { DiscordPresence.update(it) }
    }
}
