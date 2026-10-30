package com.brenninhotools.vconverter.online

import org.json.JSONArray
import org.json.JSONObject

data class RoomWelcome(val id: Int, val kind: String, val title: String, val peers: List<RoomPeer>, val snapshot: JSONObject?, val ops: List<JSONObject>, val assets: List<String>)

interface CollabListener {
    fun onWelcome(id: Int, peers: List<RoomPeer>, snapshot: JSONObject?, ops: List<JSONObject>) {}
    fun onWelcome(welcome: RoomWelcome) = onWelcome(welcome.id, welcome.peers, welcome.snapshot, welcome.ops)
    fun onAsset(name: String) {}
    fun onOp(from: Int, op: JSONObject) {}
    fun onSnapshot(from: Int, snapshot: JSONObject) {}
    fun onPeers(peers: List<RoomPeer>) {}
    fun onCursor(from: Int, time: Double, difficulty: String) {}
    fun onChat(from: Int, name: String, text: String) {}
    fun onClosed(reason: String) {}
}

class CollabSession(socketUrl: String, private val listener: CollabListener) {
    private val peers = LinkedHashMap<Int, RoomPeer>()
    private var lastCursor = 0L
    @Volatile var selfId = 0
        private set
    @Volatile var welcomed = false
        private set

    private val socket = WebSocketClient(socketUrl, object : WebSocketListener {
        override fun onText(text: String) = handle(text)
        override fun onClose(code: Int, reason: String) {
            welcomed = false
            listener.onClosed(if (code == 1000) "The live session ended." else reason.ifBlank { "The connection to the room was lost." })
        }
    })

    val isConnected: Boolean get() = socket.isOpen && welcomed

    fun connect() = socket.connect()

    fun close() = socket.close()

    fun peerList(): List<RoomPeer> = synchronized(peers) { peers.values.toList() }

    private fun peer(obj: JSONObject) = RoomPeer(obj.optInt("id"), obj.optString("name"))

    private fun handle(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            "welcome" -> {
                selfId = message.optInt("id")
                val list = message.optJSONArray("peers") ?: JSONArray()
                synchronized(peers) {
                    peers.clear()
                    (0 until list.length()).mapNotNull { list.optJSONObject(it) }.map(::peer).forEach { peers[it.id] = it }
                }
                val ops = message.optJSONArray("ops") ?: JSONArray()
                val assets = message.optJSONArray("assets") ?: JSONArray()
                welcomed = true
                listener.onWelcome(
                    RoomWelcome(
                        selfId, message.optString("kind", "chart"), message.optString("title"), peerList(), message.optJSONObject("snapshot"),
                        (0 until ops.length()).mapNotNull { ops.optJSONObject(it)?.optJSONObject("op") },
                        (0 until assets.length()).map { assets.optString(it) }
                    )
                )
            }
            "op" -> message.optJSONObject("op")?.let { listener.onOp(message.optInt("from"), it) }
            "snapshot" -> message.optJSONObject("snapshot")?.let { listener.onSnapshot(message.optInt("from"), it) }
            "join" -> message.optJSONObject("peer")?.let {
                synchronized(peers) { peers[it.optInt("id")] = peer(it) }
                listener.onPeers(peerList())
            }
            "leave" -> {
                synchronized(peers) { peers.remove(message.optInt("id")) }
                listener.onPeers(peerList())
            }
            "cursor" -> listener.onCursor(message.optInt("from"), message.optDouble("time", 0.0), message.optString("difficulty"))
            "asset" -> listener.onAsset(message.optString("name"))
            "chat" -> listener.onChat(message.optInt("from"), message.optString("name"), message.optString("text"))
        }
    }

    fun sendOp(op: JSONObject): Boolean = socket.send(JSONObject().put("type", "op").put("op", op).toString())

    fun sendSnapshot(snapshot: JSONObject): Boolean = socket.send(JSONObject().put("type", "snapshot").put("snapshot", snapshot).toString())

    fun sendCursor(time: Double, difficulty: String) {
        val now = System.currentTimeMillis()
        if (now - lastCursor < 150) return
        lastCursor = now
        socket.send(JSONObject().put("type", "cursor").put("time", time).put("difficulty", difficulty).toString())
    }

    fun sendChat(text: String): Boolean = socket.send(JSONObject().put("type", "chat").put("text", text).toString())
}
