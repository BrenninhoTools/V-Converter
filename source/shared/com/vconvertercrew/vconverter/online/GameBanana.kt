package com.vconvertercrew.vconverter.online

import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.platform.Http

class GameBananaException(message: String) : Exception(message)

data class GameBananaMod(
    val id: Int,
    val name: String,
    val submitter: String,
    val category: String,
    val likes: Int,
    val views: Int,
    val thumbnail: String?,
    val profileUrl: String,
    val hasFiles: Boolean
)

data class GameBananaFile(
    val id: Int,
    val name: String,
    val size: Long,
    val description: String,
    val downloads: Int,
    val url: String,
    val md5: String,
    val added: Long,
    val clean: Boolean
) {
    val isArchive: Boolean get() = GameBanana.archiveExtensions.any { name.lowercase().endsWith(it) }
}

data class GameBananaDetails(
    val mod: GameBananaMod,
    val files: List<GameBananaFile>,
    val downloads: Int,
    val engine: String?
)

data class GameBananaPage(val mods: List<GameBananaMod>, val total: Int, val complete: Boolean)

enum class GameBananaFeed(val label: String, val sort: String) {
    POPULAR("Popular", "default"),
    NEW("New", "new"),
    UPDATED("Updated", "updated")
}

object GameBanana {
    const val GAME_ID = 8694
    const val SITE = "https://gamebanana.com"
    const val API = "$SITE/apiv11"
    private const val TIMEOUT_MS = 15000

    val archiveExtensions = listOf(".zip", ".rar", ".7z")

    private val headers = mapOf("Accept" to "application/json", "User-Agent" to "V-Converter")

    private val engineWords = listOf(
        "v-slice" to "V-Slice", "vslice" to "V-Slice", "base game" to "V-Slice", "polymod" to "V-Slice",
        "psych" to "Psych Engine", "codename" to "Codename Engine", "nightmarevision" to "NightmareVision", "fps plus" to "FPS Plus",
        "kade" to "Kade Engine"
    )

    fun encode(text: String): String = buildString {
        text.encodeToByteArray().forEach { byte ->
            val c = (byte.toInt() and 0xFF).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c) else append('%').append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
        }
    }

    fun parseModId(text: String): Int? {
        val trimmed = text.trim()
        trimmed.toIntOrNull()?.let { return it.takeIf { id -> id > 0 } }
        val match = Regex("gamebanana\\.com/(?:mods|wips|tools|sounds)/(?:download/)?(\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
            ?: Regex("gamebanana\\.com/.*[?&]_?id(?:Row)?=(\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun parseFileId(text: String): Int? =
        Regex("gamebanana\\.com/(?:dl|mmdl)/(\\d+)", RegexOption.IGNORE_CASE).find(text.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun engineOf(text: String): String? {
        val lower = text.lowercase()
        return engineWords.firstOrNull { lower.contains(it.first) }?.second
    }

    private fun thumbnail(record: JSONObject): String? {
        val images = record.optJSONObject("_aPreviewMedia")?.optJSONArray("_aImages") ?: return null
        val first = images.optJSONObject(0) ?: return null
        val base = first.optString("_sBaseUrl")
        val file = first.optString("_sFile220").ifEmpty { first.optString("_sFile530") }.ifEmpty { first.optString("_sFile") }
        return if (base.isEmpty() || file.isEmpty()) null else "$base/$file"
    }

    fun parseMod(record: JSONObject): GameBananaMod? {
        val id = record.optInt("_idRow")
        if (id <= 0) return null
        if (record.optString("_sModelName", "Mod") != "Mod") return null
        val category = record.optJSONObject("_aRootCategory")?.optString("_sName")?.takeIf { it.isNotEmpty() }
            ?: record.optJSONObject("_aCategory")?.optString("_sName").orEmpty()
        return GameBananaMod(
            id = id,
            name = record.optString("_sName").ifBlank { "Mod $id" },
            submitter = record.optJSONObject("_aSubmitter")?.optString("_sName").orEmpty(),
            category = category,
            likes = record.optInt("_nLikeCount"),
            views = record.optInt("_nViewCount"),
            thumbnail = thumbnail(record),
            profileUrl = record.optString("_sProfileUrl").ifEmpty { "$SITE/mods/$id" },
            hasFiles = record.optBoolean("_bHasFiles", true)
        )
    }

    fun parsePage(text: String): GameBananaPage {
        val root = JSONObject(text)
        val records = root.optJSONArray("_aRecords") ?: JSONArray()
        val meta = root.optJSONObject("_aMetadata")
        val mods = (0 until records.length()).mapNotNull { records.optJSONObject(it)?.let(::parseMod) }
        return GameBananaPage(mods, meta?.optInt("_nRecordCount") ?: mods.size, meta?.optBoolean("_bIsComplete", true) ?: true)
    }

    fun parseFile(file: JSONObject): GameBananaFile? {
        val id = file.optInt("_idRow")
        if (id <= 0) return null
        val analysis = file.optString("_sAnalysisResult").lowercase()
        val av = file.optString("_sAvResult").lowercase()
        return GameBananaFile(
            id = id,
            name = file.optString("_sFile").ifBlank { "file-$id" },
            size = file.optLong("_nFilesize"),
            description = file.optString("_sDescription"),
            downloads = file.optInt("_nDownloadCount"),
            url = file.optString("_sDownloadUrl").ifEmpty { "$SITE/dl/$id" },
            md5 = file.optString("_sMd5Checksum"),
            added = file.optLong("_tsDateAdded"),
            clean = (analysis.isEmpty() || analysis == "ok") && (av.isEmpty() || av == "clean")
        )
    }

    fun parseDetails(text: String): GameBananaDetails {
        val root = JSONObject(text)
        val game = root.optJSONObject("_aGame")?.optInt("_idRow") ?: GAME_ID
        if (game != GAME_ID) throw GameBananaException("That GameBanana page isn't a Friday Night Funkin' mod.")
        val mod = parseMod(root) ?: throw GameBananaException("GameBanana didn't return a mod.")
        val files = root.optJSONArray("_aFiles")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseFile) } }.orEmpty()
        val tags = root.optJSONArray("_aTags")?.let { array -> (0 until array.length()).joinToString(" ") { array.opt(it).toString() } }.orEmpty()
        val engine = engineOf(mod.name + " " + mod.category + " " + tags + " " + root.optJSONObject("_aSuperCategory")?.optString("_sName").orEmpty())
        return GameBananaDetails(mod, files, root.optInt("_nDownloadCount"), engine)
    }

    fun bestFile(files: List<GameBananaFile>): GameBananaFile? =
        files.filter { it.clean }.sortedWith(compareByDescending<GameBananaFile> { it.isArchive }.thenByDescending { it.added }).firstOrNull()
            ?: files.maxByOrNull { it.added }

    private fun get(url: String): String {
        val response = try {
            Http.request(url, "GET", headers, null, TIMEOUT_MS)
        } catch (e: Exception) {
            throw GameBananaException("Couldn't reach GameBanana: ${e.message ?: e::class.simpleName}")
        }
        if (response.status == 404) throw GameBananaException("GameBanana has no such mod.")
        if (response.status !in 200..299) throw GameBananaException("GameBanana answered ${response.status}.")
        return response.body.decodeToString()
    }

    fun feedUrl(feed: GameBananaFeed, page: Int): String =
        "$API/Game/$GAME_ID/Subfeed?_nPage=$page&_sSort=${feed.sort}&_csvModelInclusions=Mod"

    fun searchUrl(query: String, page: Int): String =
        "$API/Util/Search/Results?_sModelName=Mod&_sOrder=best_match&_idGameRow=$GAME_ID&_sSearchString=${encode(query.trim())}&_nPage=$page"

    fun detailsUrl(id: Int): String = "$API/Mod/$id/ProfilePage"

    fun feed(feed: GameBananaFeed, page: Int = 1): GameBananaPage = parsePage(get(feedUrl(feed, page)))

    fun search(query: String, page: Int = 1): GameBananaPage {
        parseModId(query)?.let { id -> return GameBananaPage(listOf(details(id).mod), 1, true) }
        return try {
            parsePage(get(searchUrl(query, page)))
        } catch (e: GameBananaException) {
            val words = query.lowercase().split(' ').filter { it.isNotBlank() }
            val found = (1..3).flatMap { feed(GameBananaFeed.POPULAR, it).mods }.filter { mod -> words.all { mod.name.lowercase().contains(it) } }
            GameBananaPage(found, found.size, true)
        }
    }

    fun details(id: Int): GameBananaDetails = parseDetails(get(detailsUrl(id)))

    fun safeFileName(file: GameBananaFile): String =
        file.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "mod-${file.id}.zip" }

    fun download(file: GameBananaFile, onProgress: (Long, Long) -> Unit = { _, _ -> }): ByteArray {
        val response = try {
            Http.request(file.url, "GET", mapOf("User-Agent" to "V-Converter"), null, TIMEOUT_MS * 4, onProgress)
        } catch (e: Exception) {
            throw GameBananaException("The download failed: ${e.message ?: e::class.simpleName}")
        }
        if (response.status !in 200..299) throw GameBananaException("The download failed (${response.status}).")
        return response.body
    }
}
