package com.brenninhotools.vconverter.editorview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.online.CollabListener
import com.brenninhotools.vconverter.online.CollabSession
import com.brenninhotools.vconverter.online.Online
import com.brenninhotools.vconverter.online.RoomPeer
import com.brenninhotools.vconverter.online.RoomWelcome
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.platform.Platform
import com.brenninhotools.vconverter.platform.PlatformLock
import com.brenninhotools.vconverter.platform.Scheduled

data class PeerCursor(val name: String, val time: Double, val difficulty: String)

class LiveSession(private val adapter: LiveAdapter, private val post: (() -> Unit) -> Unit) {
    var code by mutableStateOf("")
    var name by mutableStateOf("")
    var publicRoom by mutableStateOf(false)
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
    var files by mutableStateOf(0)
        private set

    val kind: LiveKind get() = adapter.kind

    private var session: CollabSession? = null
    private var hosting = false
    private val known = HashSet<String>()
    private val lock = PlatformLock()
    private var lastSnapshot = 0L
    private var pending: Scheduled? = null

    init {
        adapter.watch { change ->
            val live = session
            if (live != null && connected) {
                when (change) {
                    is LiveChange.Ops -> change.ops.forEach { live.sendOp(it) }
                    is LiveChange.Snapshot -> sendSnapshot(change.snapshot)
                }
                shareNewFiles()
            }
        }
    }

    private fun background(name: String, block: () -> Unit) = Platform.background(block)

    private fun sendSnapshot(snapshot: JSONObject) {
        lock.withLock {
            val now = Platform.currentTimeMillis()
            pending?.cancel()
            pending = null
            if (now - lastSnapshot >= 150) {
                lastSnapshot = now
                session?.sendSnapshot(snapshot)
                return@withLock
            }
            pending = Platform.schedule(150 - (now - lastSnapshot)) {
                post {
                    lock.withLock {
                        lastSnapshot = Platform.currentTimeMillis()
                        pending = null
                    }
                    if (connected) session?.sendSnapshot(adapter.snapshot())
                }
            }
        }
    }

    private fun knownCount(): Int = lock.withLock { known.size }

    private fun shareNewFiles() {
        val room = code
        val fresh = lock.withLock { adapter.sharedFiles().filterKeys { it !in known }.also { known += it.keys } }
        if (fresh.isEmpty()) return
        background("v-converter-share") {
            fresh.forEach { (fileName, bytes) ->
                val data = runCatching { bytes() }.getOrNull() ?: return@forEach
                runCatching { Online.api().uploadRoomAsset(room, fileName, data) }
                    .onSuccess { post { files = knownCount() } }
                    .onFailure { e -> post { status = e.message } }
            }
        }
    }

    private fun fetch(names: List<String>) {
        val room = code
        val wanted = lock.withLock { names.filter { known.add(it) } }
        if (wanted.isEmpty()) return
        background("v-converter-fetch") {
            wanted.sortedBy { if (it.endsWith(".xml")) 0 else 1 }.forEach { fileName ->
                val bytes = runCatching { Online.api().downloadRoomAsset(room, fileName) }.getOrNull() ?: return@forEach
                val apply = runCatching { adapter.receiveFile(fileName, bytes) }.getOrNull()
                post {
                    apply?.invoke()
                    files = knownCount()
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
        val title = adapter.title()
        val listed = publicRoom
        val host = displayName()
        background("v-converter-room") {
            try {
                val room = Online.api().createRoom(title, adapter.kind.key, listed, host)
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
        }
    }

    fun join(room: String = code) {
        if (connecting || connected) return
        if (!Online.isConfigured) {
            status = "Set the server address in the options first."
            return
        }
        val clean = room.trim().uppercase()
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
        lock.withLock { known.clear() }
        val live = CollabSession(Online.api().socketUrl(room, displayName()), object : CollabListener {
            override fun onWelcome(welcome: RoomWelcome) {
                if (welcome.kind != adapter.kind.key) {
                    post {
                        session?.close()
                        session = null
                        connecting = false
                        status = "Room $room is for the ${LiveKind.of(welcome.kind).label}; open that editor to join it."
                    }
                    return
                }
                post {
                    connecting = false
                    connected = true
                    peers = welcome.peers
                    val snapshot = welcome.snapshot
                    if (snapshot != null) {
                        runCatching { adapter.load(snapshot, welcome.ops) }
                            .onSuccess { status = "Joined \"${welcome.title.ifBlank { room }}\" ($room) with ${welcome.peers.size} other editor(s)." }
                            .onFailure { status = "Joined $room, but its content could not be read: ${it.message}" }
                    } else {
                        lastSnapshot = Platform.currentTimeMillis()
                        session?.sendSnapshot(adapter.snapshot())
                        status = if (hosting) "Room $room is open${if (publicRoom) " and listed publicly" else ""}: share the code so others can join." else "Joined $room and shared your work with it."
                    }
                    fetch(welcome.assets)
                    shareNewFiles()
                }
            }

            override fun onOp(from: Int, op: JSONObject) = post { runCatching { adapter.applyOp(op) } }

            override fun onSnapshot(from: Int, snapshot: JSONObject) = post { runCatching { adapter.load(snapshot, emptyList()) } }

            override fun onAsset(name: String) = post { fetch(listOf(name)) }

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
                if (session == null && !connecting) return@post
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
        pending?.cancel()
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
fun LiveSessionPanel(live: LiveSession, modifier: Modifier = Modifier, cursor: Pair<Double, String>? = null) {
    var message by remember { mutableStateOf("") }
    LaunchedEffect(cursor, live.connected) { if (cursor != null) live.cursor(cursor.first, cursor.second) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorSectionTitle("Multieditor")
        if (!live.connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextValueField("Your name", live.name, Modifier.weight(1f)) { live.name = it }
                TextValueField("Room code", live.code, Modifier.weight(1f)) { live.code = it.uppercase() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = live.publicRoom, onCheckedChange = { live.publicRoom = it })
                Text("List my room publicly (anyone can join from Online → Rooms)", fontSize = 13.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorChip(if (live.connecting) "Connecting…" else "Start a room", false) { live.host() }
                EditorChip("Join room", false) { live.join() }
            }
            Text(
                "Edit together with two or more people: start a room and share its code (or list it publicly), or join someone's room. Every change syncs live, and the files you load (images, spritesheets, song audio) are shared with the room.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer { Text("Room ${live.code}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
                if (live.publicRoom) Text("public", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorChip("Leave", false) { live.leave() }
            }
            Text(if (live.peers.isEmpty()) "Nobody else is here yet." else "With: " + live.peers.joinToString { it.name }, fontSize = 13.sp)
            if (live.files > 0) Text("${live.files} shared file(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
