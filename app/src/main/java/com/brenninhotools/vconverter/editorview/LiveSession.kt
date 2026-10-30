package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editor.ChartChange
import com.brenninhotools.vconverter.editor.ChartSync
import com.brenninhotools.vconverter.online.CollabListener
import com.brenninhotools.vconverter.online.CollabSession
import com.brenninhotools.vconverter.online.Online
import com.brenninhotools.vconverter.online.RoomPeer
import org.json.JSONObject

data class PeerCursor(val name: String, val time: Double, val difficulty: String)

class LiveSession(private val editor: ChartEditorState, private val post: (() -> Unit) -> Unit) {
    var code by mutableStateOf("")
    var name by mutableStateOf("")
    var status by mutableStateOf<String?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set
    var connected by mutableStateOf(false)
        private set
    var peers by mutableStateOf<List<RoomPeer>>(emptyList())
        private set
    var cursors by mutableStateOf<Map<Int, PeerCursor>>(emptyMap())
        private set
    var chat by mutableStateOf<List<String>>(emptyList())
        private set

    private var session: CollabSession? = null
    private var hosting = false

    init {
        editor.onLocalChange = { old, new ->
            val live = session
            if (live != null && connected) {
                when (val change = ChartSync.diff(old, new)) {
                    is ChartChange.Ops -> change.ops.forEach { live.sendOp(it) }
                    is ChartChange.Snapshot -> live.sendSnapshot(change.snapshot)
                    null -> Unit
                }
            }
        }
    }

    private fun displayName(): String = name.trim().ifEmpty { "Player" }

    fun host() {
        if (connecting || connected) return
        if (!Online.isConfigured) {
            status = "Set the server address in the options first."
            return
        }
        connecting = true
        status = "Creating a room…"
        val title = editor.document.meta.songName.ifBlank { editor.document.id }
        Thread({
            try {
                val room = Online.api().createRoom(title)
                post {
                    code = room.code
                    hosting = true
                    open(room.code)
                }
            } catch (e: Exception) {
                post {
                    connecting = false
                    status = e.message ?: "Could not create the room."
                }
            }
        }, "v-converter-room").apply { isDaemon = true }.start()
    }

    fun join() {
        if (connecting || connected) return
        if (!Online.isConfigured) {
            status = "Set the server address in the options first."
            return
        }
        val clean = code.trim().uppercase()
        if (clean.length < 4) {
            status = "Type the room code first."
            return
        }
        code = clean
        hosting = false
        connecting = true
        open(clean)
    }

    private fun open(room: String) {
        status = "Connecting to $room…"
        val live = CollabSession(Online.api().socketUrl(room, displayName()), object : CollabListener {
            override fun onWelcome(id: Int, peers: List<RoomPeer>, snapshot: JSONObject?, ops: List<JSONObject>) {
                val remote = snapshot?.let { s -> runCatching { ops.fold(ChartSync.fromSnapshot(s)) { doc, op -> ChartSync.apply(doc, op) } }.getOrNull() }
                post {
                    connecting = false
                    connected = true
                    this@LiveSession.peers = peers
                    if (remote != null) {
                        editor.loadRemote(remote)
                        status = "Joined room $room with ${peers.size} other editor(s). The chart came from the room."
                    } else {
                        session?.sendSnapshot(ChartSync.snapshot(editor.document))
                        status = if (hosting) "Room $room is open: share the code so others can join." else "Joined room $room and shared your chart with it."
                    }
                }
            }

            override fun onOp(from: Int, op: JSONObject) = post { editor.applyRemote { ChartSync.apply(it, op) } }

            override fun onSnapshot(from: Int, snapshot: JSONObject) {
                val remote = runCatching { ChartSync.fromSnapshot(snapshot) }.getOrNull() ?: return
                post { editor.loadRemote(remote) }
            }

            override fun onPeers(peers: List<RoomPeer>) = post {
                this@LiveSession.peers = peers
                cursors = cursors.filterKeys { id -> peers.any { it.id == id } }
            }

            override fun onCursor(from: Int, time: Double, difficulty: String) = post {
                val who = peers.firstOrNull { it.id == from }?.name ?: "Editor $from"
                cursors = cursors + (from to PeerCursor(who, time, difficulty))
            }

            override fun onChat(from: Int, name: String, text: String) = post {
                chat = (chat + "$name: $text").takeLast(30)
            }

            override fun onClosed(reason: String) = post {
                val wasConnected = connected
                connecting = false
                connected = false
                peers = emptyList()
                cursors = emptyMap()
                session = null
                status = if (wasConnected) reason else reason.ifBlank { "Could not join the room." }
            }
        })
        session = live
        live.connect()
    }

    fun leave() {
        session?.close()
        session = null
        connected = false
        connecting = false
        peers = emptyList()
        cursors = emptyMap()
        status = "Left the room."
    }

    fun cursor(time: Double, difficulty: String) {
        if (connected) session?.sendCursor(time, difficulty)
    }

    fun say(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty() && connected) session?.sendChat(clean)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveSessionPanel(live: LiveSession, editor: ChartEditorState, modifier: Modifier = Modifier) {
    var message by androidx.compose.runtime.remember { mutableStateOf("") }
    LaunchedEffect(editor.position, editor.difficulty, live.connected) { live.cursor(editor.position, editor.difficulty) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Live session")
        if (!live.connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextValueField("Your name", live.name, Modifier.weight(1f)) { live.name = it }
                TextValueField("Room code", live.code, Modifier.weight(1f)) { live.code = it.uppercase() }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorChip(if (live.connecting) "Connecting…" else "Start a room", false) { live.host() }
                EditorChip("Join room", false) { live.join() }
            }
            Text(
                "Edit this chart together: start a room and share its code, or join someone's room. Notes and events sync as you place them; each person loads the song audio on their own device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer { Text("Room ${live.code}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
                EditorChip("Leave", false) { live.leave() }
            }
            Text(
                if (live.peers.isEmpty()) "Nobody else is here yet." else "With: " + live.peers.joinToString { it.name },
                fontSize = 13.sp
            )
            live.cursors.values.forEach { c ->
                Text("${c.name} · ${formatTime(c.time)} · ${c.difficulty}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            live.chat.takeLast(6).forEach { Text(it, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextValueField("Message", message, Modifier.weight(1f)) { message = it }
                EditorChip("Send", false) {
                    live.say(message)
                    message = ""
                }
            }
        }
        live.status?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()) }
    }
}
