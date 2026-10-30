package com.brenninhotools.vconverter

import com.brenninhotools.vconverter.editor.ChartChange
import com.brenninhotools.vconverter.editor.ChartDocument
import com.brenninhotools.vconverter.editor.ChartSync
import com.brenninhotools.vconverter.editor.EditorEvent
import com.brenninhotools.vconverter.editor.EditorNote
import com.brenninhotools.vconverter.online.CollabListener
import com.brenninhotools.vconverter.online.CollabSession
import com.brenninhotools.vconverter.online.ModEngine
import com.brenninhotools.vconverter.online.OnlineApi
import com.brenninhotools.vconverter.online.OnlineException
import com.brenninhotools.vconverter.online.RoomPeer
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OnlineTest {
    companion object {
        private var process: Process? = null
        private var base = ""
        private var data: File? = null

        @BeforeClass
        @JvmStatic
        fun start() {
            val server = File("server/src/index.js").absoluteFile
            if (!server.exists() || !File("server/node_modules").exists()) return
            val port = ServerSocket(0).use { it.localPort }
            data = kotlin.io.path.createTempDirectory("vcs").toFile()
            process = runCatching {
                ProcessBuilder("node", "--disable-warning=ExperimentalWarning", server.path)
                    .apply {
                        environment()["PORT"] = port.toString()
                        environment()["HOST"] = "127.0.0.1"
                        environment()["DATA_DIR"] = data!!.path
                        environment()["LATEST_VERSION"] = "9.9.9"
                        environment()["GITHUB_REPO"] = "invalid/invalid"
                    }
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull()
            base = "http://127.0.0.1:$port"
            repeat(50) {
                if (process != null && OnlineApi(base).health()) return
                Thread.sleep(100)
            }
        }

        @AfterClass
        @JvmStatic
        fun stop() {
            process?.destroy()
            process?.waitFor(5, TimeUnit.SECONDS)
            data?.deleteRecursively()
        }
    }

    private fun running() = assumeTrue("node server not available", process != null && OnlineApi(base).health())

    private fun zip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("data/test.txt"))
            z.write(text.toByteArray())
            z.closeEntry()
        }
    }.toByteArray()

    private val document = ChartDocument.blank("bopeebo", "Bopeebo", 100.0, listOf("easy", "normal"))
        .addNote("normal", EditorNote(600.0, 1))
        .addNote("normal", EditorNote(1200.0, 5, 300.0))

    @Test
    fun noteEditsBecomeSmallOps() {
        val next = document.addNote("normal", EditorNote(1800.0, 2)).removeNote("normal", EditorNote(600.0, 1))
        val change = ChartSync.diff(document, next) as ChartChange.Ops
        assertEquals(1, change.ops.size)
        val applied = change.ops.fold(document) { doc, op -> ChartSync.apply(doc, op) }
        assertEquals(next.notes("normal"), applied.notes("normal"))
        val events = next.addEvent(EditorEvent(2000.0, "FocusCamera", JSONObject().put("char", 0)))
        val eventOps = ChartSync.diff(next, events) as ChartChange.Ops
        val withEvents = eventOps.ops.fold(next) { doc, op -> ChartSync.apply(doc, op) }
        assertEquals(events.events.map { it.time to it.kind }, withEvents.events.map { it.time to it.kind })
        assertTrue(ChartSync.diff(next, next.withSpeed("normal", 2.0)) is ChartChange.Snapshot)
        val restored = ChartSync.fromSnapshot(ChartSync.snapshot(next))
        assertEquals(next.notes("normal"), restored.notes("normal"))
        assertEquals(next.difficultyNames, restored.difficultyNames)
        assertEquals(next.timing, restored.timing)
    }

    @Test
    fun modsRoundTrip() {
        running()
        val api = OnlineApi(base)
        val uploaded = api.upload("week.zip", zip("hello"), "Dude Week", "A test week", "Breno", ModEngine.PSYCH, listOf("week"))
        assertEquals("Dude Week", uploaded.mod.title)
        val page = api.mods(query = "dude")
        assertEquals(listOf(uploaded.mod.id), page.mods.map { it.id })
        assertTrue(api.download(uploaded.mod.id).contentEquals(zip("hello")))
        val duplicate = runCatching { api.upload("again.zip", zip("hello"), "Again", "", "", ModEngine.VSLICE) }.exceptionOrNull()
        assertEquals(409, (duplicate as OnlineException).status)
        api.deleteMod(uploaded.mod.id, uploaded.deleteToken)
        assertEquals(0, api.mods(query = "dude").total)
        api.report("conversion", "android", "0.0.1", "Chart failed", "at test", mapOf("tab" to "Charts"))
        val version = api.version("android", "0.0.1")
        assertEquals("9.9.9", version.latest)
        assertTrue(version.updateAvailable)
    }

    private class Recorder : CollabListener {
        val welcome = LinkedBlockingQueue<JSONObject?>()
        val ops = LinkedBlockingQueue<JSONObject>()
        val peers = LinkedBlockingQueue<List<RoomPeer>>()
        val replayed = LinkedBlockingQueue<List<JSONObject>>()
        override fun onWelcome(id: Int, peers: List<RoomPeer>, snapshot: JSONObject?, ops: List<JSONObject>) {
            replayed.put(ops)
            welcome.put(snapshot ?: JSONObject())
        }
        override fun onOp(from: Int, op: JSONObject) { ops.put(op) }
        override fun onSnapshot(from: Int, snapshot: JSONObject) {}
        override fun onPeers(peers: List<RoomPeer>) { this.peers.put(peers) }
    }

    @Test
    fun liveRoomsSyncCharts() {
        running()
        val api = OnlineApi(base)
        val room = api.createRoom("Bopeebo")
        val hostEvents = Recorder()
        val host = CollabSession(api.socketUrl(room.code, "Host"), hostEvents)
        host.connect()
        assertNotNull(hostEvents.welcome.poll(5, TimeUnit.SECONDS))
        assertTrue(host.sendSnapshot(ChartSync.snapshot(document)))
        val edited = document.addNote("normal", EditorNote(2400.0, 3))
        val change = ChartSync.diff(document, edited) as ChartChange.Ops
        change.ops.forEach { assertTrue(host.sendOp(it)) }
        Thread.sleep(300)

        val guestEvents = Recorder()
        val guest = CollabSession(api.socketUrl(room.code, "Guest"), guestEvents)
        guest.connect()
        val snapshot = guestEvents.welcome.poll(5, TimeUnit.SECONDS)!!
        val replay = guestEvents.replayed.poll(1, TimeUnit.SECONDS)!!
        val rebuilt = replay.fold(ChartSync.fromSnapshot(snapshot)) { doc, op -> ChartSync.apply(doc, op) }
        assertEquals(edited.notes("normal"), rebuilt.notes("normal"))
        assertEquals(listOf("Guest"), hostEvents.peers.poll(5, TimeUnit.SECONDS)!!.map { it.name })

        val guestEdit = rebuilt.removeNote("normal", EditorNote(600.0, 1))
        (ChartSync.diff(rebuilt, guestEdit) as ChartChange.Ops).ops.forEach { guest.sendOp(it) }
        val received = hostEvents.ops.poll(5, TimeUnit.SECONDS)!!
        assertEquals(guestEdit.notes("normal"), ChartSync.apply(edited, received).notes("normal"))

        val big = (0 until 4000).fold(document) { doc, i -> doc.addNote("easy", EditorNote(i * 10.0, i % 8)) }
        assertTrue(host.sendSnapshot(ChartSync.snapshot(big)))
        guest.close()
        host.close()
        val done = CountDownLatch(1)
        Thread { Thread.sleep(200); done.countDown() }.start()
        done.await()
    }

    @Test
    fun missingRoomsFailToJoin() {
        running()
        val closed = LinkedBlockingQueue<String>()
        val session = CollabSession(OnlineApi(base).socketUrl("NOPE00", "x"), object : CollabListener {
            override fun onWelcome(id: Int, peers: List<RoomPeer>, snapshot: JSONObject?, ops: List<JSONObject>) {}
            override fun onOp(from: Int, op: JSONObject) {}
            override fun onSnapshot(from: Int, snapshot: JSONObject) {}
            override fun onClosed(reason: String) { closed.put(reason) }
        })
        session.connect()
        assertEquals("That room does not exist (or it expired).", closed.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun publicRoomsShareFiles() {
        running()
        val api = OnlineApi(base)
        val room = api.createRoom("Week 9", "week", true, "Breno")
        assertEquals("week", room.kind)
        assertTrue(api.publicRooms("week").any { it.code == room.code })
        assertTrue(api.publicRooms("stage").none { it.code == room.code })
        val png = byteArrayOf(1, 2, 3, 4, 5)
        api.uploadRoomAsset(room.code, "images/storymenu/props/dude pixel.png", png)
        assertTrue(api.downloadRoomAsset(room.code, "images/storymenu/props/dude pixel.png").contentEquals(png))
        val welcomes = LinkedBlockingQueue<com.brenninhotools.vconverter.online.RoomWelcome>()
        val session = CollabSession(api.socketUrl(room.code, "Guest"), object : CollabListener {
            override fun onWelcome(welcome: com.brenninhotools.vconverter.online.RoomWelcome) { welcomes.put(welcome) }
        })
        session.connect()
        val got = welcomes.poll(5, TimeUnit.SECONDS)!!
        assertEquals("week", got.kind)
        assertEquals(listOf("images/storymenu/props/dude pixel.png"), got.assets)
        session.close()
    }

    @Test
    fun weekSnapshotsKeepEverything() {
        val doc = com.brenninhotools.vconverter.editor.WeekDocument(
            id = "weekdude", name = "Dude", weekName = "Dude Week",
            songs = listOf(com.brenninhotools.vconverter.editor.WeekSong("song-one", "Song One", "dude", "#FF0000")),
            difficulties = listOf("easy", "erect"), weekBefore = "week1", startUnlocked = false, hiddenUntilUnlocked = true, hideFreeplay = true
        ).withProp(com.brenninhotools.vconverter.editor.WeekSlot.DAD, null)
        val back = com.brenninhotools.vconverter.editor.WeekDocument.fromSnapshot(JSONObject(doc.toSnapshot().toString()))
        assertEquals(doc.songs, back.songs)
        assertEquals(doc.difficulties, back.difficulties)
        assertEquals(doc.weekBefore, back.weekBefore)
        assertEquals(doc.hiddenUntilUnlocked, back.hiddenUntilUnlocked)
        assertEquals(doc.hideFreeplay, back.hideFreeplay)
        assertEquals(null, back.prop(com.brenninhotools.vconverter.editor.WeekSlot.DAD))
        assertEquals(doc.prop(com.brenninhotools.vconverter.editor.WeekSlot.BF)!!.offsetX, back.prop(com.brenninhotools.vconverter.editor.WeekSlot.BF)!!.offsetX, 0.0)
    }
}
