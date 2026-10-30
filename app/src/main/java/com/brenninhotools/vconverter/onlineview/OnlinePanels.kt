package com.brenninhotools.vconverter.onlineview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brenninhotools.vconverter.editorview.EditorChip
import com.brenninhotools.vconverter.editorview.EditorSectionTitle
import com.brenninhotools.vconverter.editorview.TextValueField
import com.brenninhotools.vconverter.online.ModEngine
import com.brenninhotools.vconverter.online.ModEntry
import java.util.Locale

private val warning = Color(0xFFFFC94A)

private fun size(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun NotConfigured(modifier: Modifier = Modifier) {
    Text(
        "No server is set. Open the options (the gear) and type the address of a V-Converter server, for example https://my-server.example.com. The server's code is in the server/ folder of the repository.",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun OnlineNewsPanel(state: OnlineState, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(state.configured) { if (state.configured && state.version == null) state.refreshNews() }
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorSectionTitle("News")
        if (!state.configured) {
            NotConfigured()
            return@Column
        }
        val version = state.version
        when {
            version == null -> Unit
            version.updateAvailable -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.primary).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Version ${version.latest} is out", color = scheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("You have ${version.current}.", color = scheme.onPrimary, fontSize = 13.sp)
                if (version.notes.isNotBlank()) Text(version.notes.take(600), color = scheme.onPrimary, fontSize = 12.sp, maxLines = 12, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (version.url.isNotBlank()) EditorChip("Download", false) { onOpenLink(version.url) }
                    if (version.page.isNotBlank()) EditorChip("Release page", false) { onOpenLink(version.page) }
                }
            }
            version.latest != null -> Text("You have the latest version (${version.current}).", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        state.announcements.forEach { a ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(a.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (a.level == "warning") warning else scheme.onSurface)
                if (a.body.isNotBlank()) Text(a.body, fontSize = 13.sp)
                if (a.url.isNotBlank()) EditorChip("Open link", false) { onOpenLink(a.url) }
            }
        }
        if (state.announcements.isEmpty() && version != null) Text("No announcements right now.", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        state.newsError?.let { Text(it, fontSize = 13.sp, color = scheme.error) }
        EditorChip(if (state.newsLoading) "Checking…" else "Check again", false) { state.refreshNews() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModGalleryPanel(state: OnlineState, onDownload: (ModEntry) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(state.configured) { if (state.configured && state.page == null) state.search() }
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorSectionTitle("Mod gallery")
        if (!state.configured) {
            NotConfigured()
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextValueField("Search mods", state.query, Modifier.weight(1f)) { state.query = it }
            EditorChip(if (state.loading) "Searching…" else "Search", false) { state.search() }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("All engines", state.engine == null) { state.engine = null; state.search() }
            ModEngine.entries.forEach { e -> EditorChip(e.label, state.engine == e) { state.engine = e; state.search() } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("new" to "Newest", "downloads" to "Most downloaded", "title" to "A–Z").forEach { (key, label) ->
                EditorChip(label, state.sort == key) { state.sort = key; state.search() }
            }
        }
        state.error?.let { Text(it, fontSize = 13.sp, color = scheme.error) }
        state.message?.let { Text(it, fontSize = 13.sp, color = scheme.primary) }
        val page = state.page
        if (page != null) {
            Text("${page.total} mod(s)", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            page.mods.forEach { mod ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(mod.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            mod.author.takeIf { it.isNotBlank() }?.let { "by $it" },
                            ModEngine.of(mod.engine).label,
                            size(mod.size),
                            "${mod.downloads} download(s)",
                            mod.createdAt.take(10)
                        ).joinToString(" · "),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant
                    )
                    if (mod.description.isNotBlank()) Text(mod.description, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    if (mod.tags.isNotEmpty()) Text(mod.tags.joinToString("  ") { "#$it" }, fontSize = 12.sp, color = scheme.primary)
                    if (state.downloading == mod.id) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        EditorChip("Download", false) { onDownload(mod) }
                    }
                }
            }
            if (page.mods.isEmpty()) Text("No mods found.", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            if (page.pages > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.pageNumber > 1) EditorChip("← Previous", false) { state.search(state.pageNumber - 1) }
                    Text("Page ${state.pageNumber} of ${page.pages}", fontSize = 12.sp)
                    if (state.pageNumber < page.pages) EditorChip("Next →", false) { state.search(state.pageNumber + 1) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModSharePanel(state: OnlineState, onPickFile: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorSectionTitle("Share a mod")
        if (!state.configured) {
            NotConfigured()
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.file?.let { "${it.first} · ${size(it.second.size.toLong())}" } ?: "No file picked", fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            EditorChip("Pick .zip", false, onPickFile)
        }
        TextValueField("Title", state.title, Modifier.fillMaxWidth()) { state.title = it }
        TextValueField("Description", state.description, Modifier.fillMaxWidth()) { state.description = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextValueField("Author", state.author, Modifier.weight(1f)) { state.author = it }
            TextValueField("Tags (comma separated)", state.tags, Modifier.weight(1f)) { state.tags = it }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ModEngine.entries.forEach { e -> EditorChip(e.label, state.uploadEngine == e) { state.uploadEngine = e } }
        }
        EditorChip(if (state.uploading) "Uploading…" else "Upload", false) { state.upload() }
        state.uploadError?.let { Text(it, fontSize = 13.sp, color = scheme.error) }
        state.uploaded?.let { done ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("\"${done.mod.title}\" is in the gallery.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SelectionContainer {
                    Text("Mod id: ${done.mod.id}\nDelete key: ${done.deleteToken}\nLink: ${done.mod.downloadUrl}", fontSize = 12.sp)
                }
                Text("Keep the delete key: it is the only way to remove the mod later.", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                EditorChip("Remove it", false) { state.deleteUploaded() }
            }
        }
        Text("Only .zip files are accepted. Share mods you made or have permission to share.", fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}
