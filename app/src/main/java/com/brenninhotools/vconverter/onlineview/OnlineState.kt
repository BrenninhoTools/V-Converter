package com.brenninhotools.vconverter.onlineview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brenninhotools.vconverter.online.Announcement
import com.brenninhotools.vconverter.online.ModEngine
import com.brenninhotools.vconverter.online.ModEntry
import com.brenninhotools.vconverter.online.ModPage
import com.brenninhotools.vconverter.online.Online
import com.brenninhotools.vconverter.online.RoomInfo
import com.brenninhotools.vconverter.online.UploadedMod
import com.brenninhotools.vconverter.online.VersionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.brenninhotools.vconverter.platform.OutputStream

class OnlineState(private val scope: CoroutineScope) {
    var version by mutableStateOf<VersionInfo?>(null)
        private set
    var announcements by mutableStateOf<List<Announcement>>(emptyList())
        private set
    var newsError by mutableStateOf<String?>(null)
        private set
    var newsLoading by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
    var engine by mutableStateOf<ModEngine?>(null)
    var sort by mutableStateOf("new")
    var page by mutableStateOf<ModPage?>(null)
        private set
    var pageNumber by mutableIntStateOf(1)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var downloading by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var author by mutableStateOf("")
    var tags by mutableStateOf("")
    var uploadEngine by mutableStateOf(ModEngine.VSLICE)
    var file by mutableStateOf<Pair<String, ByteArray>?>(null)
    var uploading by mutableStateOf(false)
        private set
    var uploaded by mutableStateOf<UploadedMod?>(null)
        private set
    var uploadError by mutableStateOf<String?>(null)
        private set

    val configured: Boolean get() = Online.isConfigured

    var rooms by mutableStateOf<List<RoomInfo>?>(null)
        private set
    var roomsLoading by mutableStateOf(false)
        private set
    var roomsError by mutableStateOf<String?>(null)
        private set
    var roomKind by mutableStateOf<String?>(null)

    fun refreshRooms() {
        if (!configured || roomsLoading) return
        roomsLoading = true
        roomsError = null
        val kind = roomKind
        scope.launch {
            try {
                rooms = withContext(Dispatchers.IO) { Online.api().publicRooms(kind) }
            } catch (e: Exception) {
                roomsError = e.message ?: "Could not load the rooms."
            } finally {
                roomsLoading = false
            }
        }
    }

    fun refreshNews() {
        if (!configured || newsLoading) return
        newsLoading = true
        newsError = null
        scope.launch {
            try {
                val api = Online.api()
                val (v, list) = withContext(Dispatchers.IO) {
                    runCatching { api.version(Online.platform, Online.appVersion) }.getOrNull() to api.announcements(Online.platform)
                }
                version = v
                announcements = list
            } catch (e: Exception) {
                newsError = e.message ?: "Could not reach the server."
            } finally {
                newsLoading = false
            }
        }
    }

    fun search(target: Int = 1) {
        if (!configured || loading) return
        loading = true
        error = null
        val q = query
        val e = engine
        val s = sort
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { Online.api().mods(q, e, s, target) }
                page = result
                pageNumber = result.page
            } catch (ex: Exception) {
                error = ex.message ?: "Could not load the mods."
            } finally {
                loading = false
            }
        }
    }

    fun download(mod: ModEntry, open: () -> OutputStream?, onDone: (Boolean) -> Unit = {}) {
        if (downloading != null) return
        downloading = mod.id
        progress = 0f
        message = null
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val out = open() ?: return@withContext false
                    out.use { stream ->
                        Online.api().download(mod.id, stream) { done, total -> if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f) }
                    }
                    true
                }
            } catch (e: Exception) {
                message = e.message ?: "The download failed."
                false
            }
            if (ok) message = "Downloaded ${mod.fileName}."
            downloading = null
            onDone(ok)
        }
    }

    fun upload() {
        val chosen = file
        when {
            !configured -> uploadError = "Set the server address in the options first."
            chosen == null -> uploadError = "Pick the mod .zip first."
            title.isBlank() -> uploadError = "Give the mod a title."
            uploading -> return
            else -> {
                uploading = true
                uploadError = null
                val fields = listOf(title.trim(), description.trim(), author.trim())
                val list = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val kind = uploadEngine
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { Online.api().upload(chosen.first, chosen.second, fields[0], fields[1], fields[2], kind, list) }
                        uploaded = result
                        file = null
                        title = ""
                        description = ""
                        tags = ""
                        search()
                    } catch (e: Exception) {
                        uploadError = e.message ?: "The upload failed."
                    } finally {
                        uploading = false
                    }
                }
            }
        }
    }

    fun deleteUploaded() {
        val mod = uploaded ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Online.api().deleteMod(mod.mod.id, mod.deleteToken) }
                uploaded = null
                uploadError = "Removed \"${mod.mod.title}\" from the gallery."
                search()
            } catch (e: Exception) {
                uploadError = e.message ?: "Could not remove the mod."
            }
        }
    }
}
