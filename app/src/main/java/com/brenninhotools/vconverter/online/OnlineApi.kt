package com.brenninhotools.vconverter.online

import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.core.UrlEncoding
import com.brenninhotools.vconverter.platform.ByteArrayOutputStream
import com.brenninhotools.vconverter.platform.Http
import com.brenninhotools.vconverter.platform.HttpResponse
import com.brenninhotools.vconverter.platform.OutputStream
import com.brenninhotools.vconverter.platform.Platform

class OnlineException(message: String, val status: Int = 0) : Exception(message)

data class ModEntry(
    val id: String,
    val title: String,
    val description: String,
    val author: String,
    val engine: String,
    val tags: List<String>,
    val fileName: String,
    val size: Long,
    val downloads: Int,
    val createdAt: String,
    val downloadUrl: String
)

data class ModPage(val total: Int, val page: Int, val size: Int, val mods: List<ModEntry>) {
    val pages: Int get() = if (size <= 0) 1 else ((total + size - 1) / size).coerceAtLeast(1)
}

data class UploadedMod(val mod: ModEntry, val deleteToken: String)

data class VersionInfo(val latest: String?, val current: String, val updateAvailable: Boolean, val name: String, val notes: String, val url: String, val page: String)

data class Announcement(val id: Int, val title: String, val body: String, val level: String, val url: String)

data class RoomPeer(val id: Int, val name: String)

data class RoomInfo(val code: String, val title: String, val peers: List<RoomPeer>, val kind: String = "chart", val public: Boolean = false, val host: String = "")

enum class ModEngine(val key: String, val label: String) {
    VSLICE("vslice", "V-Slice"),
    PSYCH("psych", "Psych Engine"),
    CODENAME("codename", "Codename Engine"),
    OTHER("other", "Other");

    companion object {
        fun of(key: String): ModEngine = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

class OnlineApi(baseUrl: String, private val timeoutMs: Int = 20_000) {
    val base: String = normalize(baseUrl)

    private fun encode(value: String) = UrlEncoding.encode(value)

    private fun failure(response: HttpResponse): Nothing {
        val body = response.body.decodeToString()
        val message = runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "The server answered ${response.status}."
        throw OnlineException(message, response.status)
    }

    private fun send(
        path: String,
        method: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
        failure: String = "Could not reach the server",
        onProgress: ((Long, Long) -> Unit)? = null
    ): ByteArray {
        require(base.isNotEmpty()) { "Set the server address in the options first." }
        val all = linkedMapOf("Accept" to "application/json", "User-Agent" to "V-Converter") + headers
        val response = try {
            Http.request(base + path, method, all, body, timeoutMs, onProgress)
        } catch (e: OnlineException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("$failure: ${e.message ?: Platform.errorName(e)}")
        }
        if (response.status !in 200..299) failure(response)
        return response.body
    }

    private fun request(path: String, method: String = "GET", json: JSONObject? = null, headers: Map<String, String> = emptyMap()): String {
        val all = if (json != null) headers + ("Content-Type" to "application/json; charset=utf-8") else headers
        return send(path, method, json?.toString()?.encodeToByteArray(), all).decodeToString()
    }

    fun health(): Boolean = runCatching { JSONObject(request("/api/health")).optString("status") == "ok" }.getOrDefault(false)

    fun version(platform: String, current: String): VersionInfo {
        val root = JSONObject(request("/api/version?platform=${encode(platform)}&current=${encode(current)}"))
        return VersionInfo(
            latest = root.optString("latest").takeIf { it.isNotBlank() && it != "null" },
            current = current,
            updateAvailable = root.optBoolean("updateAvailable", false),
            name = root.optString("name"),
            notes = root.optString("notes"),
            url = root.optString("url"),
            page = root.optString("page")
        )
    }

    fun announcements(platform: String): List<Announcement> {
        val array = JSONObject(request("/api/announcements?platform=${encode(platform)}")).optJSONArray("announcements") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
            Announcement(it.optInt("id"), it.optString("title"), it.optString("body"), it.optString("level", "info"), it.optString("url"))
        }
    }

    private fun mod(obj: JSONObject): ModEntry = ModEntry(
        id = obj.optString("id"),
        title = obj.optString("title"),
        description = obj.optString("description"),
        author = obj.optString("author"),
        engine = obj.optString("engine"),
        tags = obj.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        fileName = obj.optString("fileName"),
        size = obj.optLong("size"),
        downloads = obj.optInt("downloads"),
        createdAt = obj.optString("createdAt"),
        downloadUrl = obj.optString("downloadUrl")
    )

    fun mods(query: String = "", engine: ModEngine? = null, sort: String = "new", page: Int = 1, size: Int = 20): ModPage {
        val params = buildList {
            if (query.isNotBlank()) add("q=${encode(query.trim())}")
            if (engine != null) add("engine=${engine.key}")
            add("sort=${encode(sort)}")
            add("page=$page")
            add("size=$size")
        }.joinToString("&")
        val root = JSONObject(request("/api/mods?$params"))
        val array = root.optJSONArray("mods") ?: JSONArray()
        return ModPage(root.optInt("total"), root.optInt("page", page), root.optInt("size", size), (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map(::mod))
    }

    fun download(id: String, out: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        out.write(send("/api/mods/${encode(id)}/download", "GET", failure = "The download failed", onProgress = onProgress))
    }

    fun download(id: String): ByteArray = ByteArrayOutputStream().also { download(id, it) }.toByteArray()

    fun upload(fileName: String, bytes: ByteArray, title: String, description: String, author: String, engine: ModEngine, tags: List<String> = emptyList()): UploadedMod {
        val boundary = "----vconverter" + Platform.randomId().replace("-", "")
        val body = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n".encodeToByteArray())
            body.write(value.encodeToByteArray())
            body.write("\r\n".encodeToByteArray())
        }
        field("title", title)
        field("description", description)
        field("author", author)
        field("engine", engine.key)
        if (tags.isNotEmpty()) field("tags", tags.joinToString(","))
        val safeName = fileName.replace(Regex("[\"\\r\\n]"), "_").ifBlank { "mod.zip" }
        body.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\nContent-Type: application/zip\r\n\r\n".encodeToByteArray())
        body.write(bytes)
        body.write("\r\n--$boundary--\r\n".encodeToByteArray())
        val payload = body.toByteArray()
        val root = JSONObject(send("/api/mods", "POST", payload, mapOf("Content-Type" to "multipart/form-data; boundary=$boundary"), "The upload failed").decodeToString())
        return UploadedMod(mod(root), root.optString("deleteToken"))
    }

    fun deleteMod(id: String, deleteToken: String) {
        request("/api/mods/${encode(id)}", "DELETE", headers = mapOf("X-Delete-Token" to deleteToken))
    }

    fun report(kind: String, platform: String, appVersion: String, message: String, stack: String = "", context: Map<String, String> = emptyMap()) {
        request(
            "/api/reports", "POST",
            JSONObject().put("kind", kind).put("platform", platform).put("appVersion", appVersion)
                .put("message", message.take(2000)).put("stack", stack.take(16000)).put("context", JSONObject(context))
        )
    }

    private fun room(obj: JSONObject): RoomInfo {
        val peers = obj.optJSONArray("peers") ?: JSONArray()
        return RoomInfo(
            obj.optString("code"), obj.optString("title"),
            (0 until peers.length()).mapNotNull { peers.optJSONObject(it) }.map { RoomPeer(it.optInt("id"), it.optString("name")) },
            obj.optString("kind", "chart"), obj.optBoolean("public", false), obj.optString("host")
        )
    }

    fun createRoom(title: String, kind: String = "chart", public: Boolean = false, host: String = ""): RoomInfo =
        room(JSONObject(request("/api/rooms", "POST", JSONObject().put("title", title).put("kind", kind).put("public", public).put("host", host))))

    fun publicRooms(kind: String? = null): List<RoomInfo> {
        val array = JSONObject(request("/api/rooms" + (kind?.let { "?kind=${encode(it)}" } ?: ""))).optJSONArray("rooms") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map(::room)
    }

    private fun assetPath(code: String, name: String) =
        "/api/rooms/${encode(code.trim().uppercase())}/assets/" + name.split('/').joinToString("/") { encode(it).replace("+", "%20") }

    fun uploadRoomAsset(code: String, name: String, bytes: ByteArray) {
        send(assetPath(code, name), "PUT", bytes, mapOf("Content-Type" to "application/octet-stream"), "Could not share ${name.substringAfterLast('/')}")
    }

    fun downloadRoomAsset(code: String, name: String): ByteArray =
        send(assetPath(code, name), "GET", failure = "Could not get ${name.substringAfterLast('/')}")

    fun room(code: String): RoomInfo = room(JSONObject(request("/api/rooms/${encode(code.trim().uppercase())}")))

    fun socketUrl(code: String, name: String): String {
        val ws = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            else -> "ws://" + base.removePrefix("http://")
        }
        return "$ws/ws?room=${encode(code.trim().uppercase())}&name=${encode(name.trim().ifEmpty { "Guest" })}"
    }

    companion object {
        fun normalize(raw: String): String {
            val clean = raw.trim().trimEnd('/')
            if (clean.isEmpty()) return ""
            return if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "https://$clean"
        }
    }
}
