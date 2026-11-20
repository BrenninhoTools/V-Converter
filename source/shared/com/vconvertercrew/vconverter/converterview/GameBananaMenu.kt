package com.vconvertercrew.vconverter.converterview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.core.fmt
import com.vconvertercrew.vconverter.online.GameBanana
import com.vconvertercrew.vconverter.online.GameBananaDetails
import com.vconvertercrew.vconverter.online.GameBananaFeed
import com.vconvertercrew.vconverter.online.GameBananaFile
import com.vconvertercrew.vconverter.online.GameBananaMod
import com.vconvertercrew.vconverter.online.GameBananaPage
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vconverter.platform.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias GameBananaFetch = suspend (mod: GameBananaMod, file: GameBananaFile, progress: (Float) -> Unit) -> Unit

class GameBananaState(
    private val scope: CoroutineScope,
    private val decode: (ByteArray) -> ImageBitmap?,
    private val fetch: GameBananaFetch
) {
    var open by mutableStateOf(false)
    var feed by mutableStateOf(GameBananaFeed.POPULAR)
        private set
    var query by mutableStateOf("")
    var searched by mutableStateOf<String?>(null)
        private set
    val mods = mutableStateListOf<GameBananaMod>()
    var loading by mutableStateOf(false)
        private set
    var complete by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var details by mutableStateOf<GameBananaDetails?>(null)
        private set
    var downloading by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    val thumbs = mutableStateMapOf<Int, ImageBitmap>()
    private val asked = HashSet<Int>()
    private var page = 1
    private var job: Job? = null

    fun show() {
        open = true
        error = null
        if (mods.isEmpty() && !loading) reload()
    }

    fun hide() {
        if (downloading != null) return
        open = false
        details = null
    }

    fun switchFeed(next: GameBananaFeed) {
        feed = next
        searched = null
        reload()
    }

    fun search(text: String = query) {
        val trimmed = text.trim()
        if (trimmed.length < 2) {
            searched = null
            reload()
            return
        }
        searched = trimmed
        reload()
    }

    fun reload() {
        page = 1
        mods.clear()
        complete = false
        load()
    }

    fun loadMore() {
        if (loading || complete) return
        page++
        load()
    }

    private fun load() {
        job?.cancel()
        loading = true
        error = null
        val term = searched
        val current = feed
        val number = page
        job = scope.launch {
            try {
                val result: GameBananaPage = withContext(Dispatchers.Default) {
                    if (term != null) GameBanana.search(term, number) else GameBanana.feed(current, number)
                }
                val known = mods.map { it.id }.toHashSet()
                mods.addAll(result.mods.filter { it.id !in known })
                complete = result.complete || result.mods.isEmpty() || term != null && result.mods.size < 10
            } catch (e: Exception) {
                error = e.message ?: "GameBanana didn't answer."
            } finally {
                loading = false
            }
        }
    }

    fun thumbnail(mod: GameBananaMod) {
        val url = mod.thumbnail ?: return
        if (!asked.add(mod.id)) return
        scope.launch {
            val image = withContext(Dispatchers.Default) {
                runCatching {
                    val response = Http.request(url, "GET", mapOf("User-Agent" to "V-Converter"), null, 10000)
                    if (response.status in 200..299) decode(response.body) else null
                }.getOrNull()
            }
            if (image != null) thumbs[mod.id] = image
        }
    }

    fun openDetails(mod: GameBananaMod) {
        error = null
        scope.launch {
            try {
                details = withContext(Dispatchers.Default) { GameBanana.details(mod.id) }
            } catch (e: Exception) {
                error = e.message ?: "GameBanana didn't answer."
            }
        }
    }

    fun closeDetails() {
        details = null
    }

    fun download(mod: GameBananaMod, file: GameBananaFile? = null) {
        if (downloading != null) return
        downloading = mod.name
        progress = 0f
        error = null
        scope.launch {
            try {
                val chosen = file ?: withContext(Dispatchers.Default) {
                    val info = GameBanana.details(mod.id)
                    GameBanana.bestFile(info.files) ?: throw IllegalStateException("${mod.name} has no files to download.")
                }
                if (!chosen.isArchive) throw IllegalStateException("${chosen.name} isn't a .zip, .rar or .7z, so V-Converter can't open it.")
                fetch(mod, chosen) { progress = it.coerceIn(0f, 1f) }
                open = false
                details = null
            } catch (e: Exception) {
                error = e.message ?: "The download failed."
            } finally {
                downloading = null
            }
        }
    }
}

private val Banana = Color(0xFFFFD43B)
private val Ink = Color(0xFF15120A)

private fun count(value: Int): String = when {
    value >= 1_000_000 -> fmt("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> fmt("%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun size(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> fmt("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024 -> fmt("%.1f MB", bytes / 1024.0 / 1024.0)
    else -> fmt("%.0f KB", bytes / 1024.0)
}

@Composable
fun GameBananaMenu(state: GameBananaState, art: ConverterArt, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val mono = FontFamily.Monospace
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.mods.size) { if (selected >= state.mods.size) selected = maxOf(0, state.mods.size - 1) }
    LaunchedEffect(selected) {
        if (state.mods.isNotEmpty()) list.animateScrollToItem(selected.coerceIn(0, state.mods.size - 1))
        if (selected >= state.mods.size - 3) state.loadMore()
    }
    val feeds = GameBananaFeed.entries

    fun step(by: Int) {
        if (state.mods.isEmpty()) return
        selected = (selected + by).coerceIn(0, state.mods.size - 1)
        UiSound.click()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val details = state.details
                when (event.key) {
                    Key.Escape, Key.Back -> if (details != null) state.closeDetails() else state.hide()
                    Key.DirectionUp, Key.W -> step(-1)
                    Key.DirectionDown, Key.S -> step(1)
                    Key.DirectionLeft, Key.A -> if (state.searched == null) state.switchFeed(feeds[(feeds.indexOf(state.feed) + feeds.size - 1) % feeds.size])
                    Key.DirectionRight, Key.D -> if (state.searched == null) state.switchFeed(feeds[(feeds.indexOf(state.feed) + 1) % feeds.size])
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> state.mods.getOrNull(selected)?.let {
                        UiSound.click()
                        state.download(it)
                    }
                    Key.I, Key.Tab -> state.mods.getOrNull(selected)?.let { state.openDetails(it) }
                    Key.Slash, Key.F -> runCatching { search.requestFocus() }
                    else -> return@onKeyEvent false
                }
                true
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("GAMEBANANA", color = Banana, fontFamily = mono, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("FRIDAY NIGHT FUNKIN' MODS", color = Color.White.copy(alpha = 0.7f), fontFamily = mono, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "CLOSE",
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { state.hide() }.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                feeds.forEach { item ->
                    val active = state.searched == null && state.feed == item
                    Text(
                        item.label.uppercase(),
                        color = if (active) Ink else Color.White,
                        fontFamily = mono,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (active) Banana else Color(0x33FFFFFF))
                            .clickable { state.switchFeed(item) }.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontFamily = mono, fontSize = 14.sp),
                    cursorBrush = SolidColor(Banana),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        state.search()
                        runCatching { focus.requestFocus() }
                    }),
                    modifier = Modifier.weight(1f).focusRequester(search).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                state.search()
                                runCatching { focus.requestFocus() }
                                true
                            } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                runCatching { focus.requestFocus() }
                                true
                            } else {
                                false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    decorationBox = { inner ->
                        Box {
                            if (state.query.isEmpty()) Text("Search mods or paste a GameBanana link", color = Color.White.copy(alpha = 0.45f), fontFamily = mono, fontSize = 14.sp)
                            inner()
                        }
                    }
                )
                Text(
                    "SEARCH",
                    color = Ink,
                    fontFamily = mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Banana).clickable { state.search() }.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            state.searched?.let { Text("RESULTS FOR \"${it.uppercase()}\"", color = Banana, fontFamily = mono, fontSize = 12.sp) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.mods, key = { _, mod -> mod.id }) { index, mod ->
                        LaunchedEffect(mod.id) { state.thumbnail(mod) }
                        ModRow(mod, state.thumbs[mod.id], index == selected, state.downloading == null,
                            onSelect = {
                                if (selected == index) state.download(mod) else selected = index
                                UiSound.click()
                            },
                            onDetails = { state.openDetails(mod) },
                            onGet = {
                                selected = index
                                state.download(mod)
                            })
                    }
                    if (!state.complete && state.mods.isNotEmpty()) {
                        item {
                            Text(
                                if (state.loading) "LOADING..." else "LOAD MORE",
                                color = Banana,
                                fontFamily = mono,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().clickable { state.loadMore() }.padding(12.dp)
                            )
                        }
                    }
                }
                if (state.mods.isEmpty()) {
                    Text(
                        when {
                            state.loading -> "LOADING GAMEBANANA..."
                            state.error != null -> ""
                            else -> "NOTHING FOUND"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontFamily = mono,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            state.error?.let { Text(it, color = Color(0xFFFF6B6B), fontFamily = mono, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Text(
                "ENTER / TAP TWICE: DOWNLOAD AND OPEN  ·  I / LONG PRESS: FILES  ·  LEFT / RIGHT: FEED  ·  /: SEARCH  ·  ESC: CLOSE",
                color = Color.White.copy(alpha = 0.55f),
                fontFamily = mono,
                fontSize = 11.sp
            )
        }
        state.details?.let { DetailsPanel(state, it, Modifier.align(Alignment.CenterEnd)) }
        state.downloading?.let { name ->
            Box(Modifier.fillMaxSize().background(Color(0xB0000000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DOWNLOADING", color = Banana, fontFamily = mono, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(name, color = Color.White, fontFamily = mono, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp))
                    Box(Modifier.width(320.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(state.progress.coerceIn(0.02f, 1f)).background(Banana))
                    }
                    Text(if (state.progress >= 1f) "OPENING THE MOD..." else "${(state.progress * 100).toInt()}%", color = Color.White, fontFamily = mono, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModRow(
    mod: GameBananaMod,
    thumb: ImageBitmap?,
    active: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    onGet: () -> Unit
) {
    val mono = FontFamily.Monospace
    val engine = GameBanana.engineOf(mod.name + " " + mod.category)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0x40FFD43B) else Color(0x1AFFFFFF))
            .border(if (active) 2.dp else 0.dp, if (active) Banana else Color.Transparent, RoundedCornerShape(8.dp))
            .pointerInput(mod.id, enabled) {
                detectTapGestures(onTap = { if (enabled) onSelect() }, onLongPress = { if (enabled) onDetails() })
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))) {
            if (thumb != null) Image(thumb, mod.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(mod.name, color = Color.White, fontFamily = mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(mod.submitter.takeIf { it.isNotEmpty() }?.let { "by $it" }, mod.category.takeIf { it.isNotEmpty() }).joinToString(" · "),
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = mono,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("♥ ${count(mod.likes)}", color = Color(0xFFFF8FB1), fontFamily = mono, fontSize = 12.sp)
                Text("◉ ${count(mod.views)}", color = Color.White.copy(alpha = 0.6f), fontFamily = mono, fontSize = 12.sp)
                engine?.let { Text(it.uppercase(), color = Banana, fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            "GET",
            color = Ink,
            fontFamily = mono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (enabled && mod.hasFiles) Banana else Color(0x66FFD43B))
                .clickable(enabled = enabled && mod.hasFiles) { onGet() }.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DetailsPanel(state: GameBananaState, details: GameBananaDetails, modifier: Modifier) {
    val mono = FontFamily.Monospace
    Column(
        modifier
            .fillMaxHeight()
            .widthIn(max = 460.dp)
            .fillMaxWidth(0.9f)
            .background(Color(0xF2101010))
            .border(1.dp, Banana.copy(alpha = 0.6f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(details.mod.name, color = Banana, fontFamily = mono, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { state.closeDetails() }.padding(6.dp))
        }
        Text(
            listOfNotNull("by ${details.mod.submitter}".takeIf { details.mod.submitter.isNotEmpty() }, "${count(details.downloads)} downloads", details.engine).joinToString(" · "),
            color = Color.White.copy(alpha = 0.75f),
            fontFamily = mono,
            fontSize = 12.sp
        )
        Text("FILES", color = Color.White, fontFamily = mono, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (details.files.isEmpty()) Text("This mod has no files on GameBanana.", color = Color.White.copy(alpha = 0.7f), fontFamily = mono, fontSize = 12.sp)
        val best = GameBanana.bestFile(details.files)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(details.files, key = { _, file -> file.id }) { _, file ->
                FileRow(file, file == best, state.downloading == null) { state.download(details.mod, file) }
            }
        }
    }
}

@Composable
private fun FileRow(file: GameBananaFile, recommended: Boolean, enabled: Boolean, onGet: () -> Unit) {
    val mono = FontFamily.Monospace
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (recommended) Color(0x33FFD43B) else Color(0x1AFFFFFF)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, color = Color.White, fontFamily = mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(size(file.size), "${count(file.downloads)} downloads", "recommended".takeIf { recommended }, "not scanned clean".takeIf { !file.clean }).joinToString(" · "),
                color = if (file.clean) Color.White.copy(alpha = 0.65f) else Color(0xFFFFB86B),
                fontFamily = mono,
                fontSize = 11.sp
            )
            if (file.description.isNotBlank()) Text(file.description, color = Color.White.copy(alpha = 0.55f), fontFamily = mono, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (file.isArchive) "GET" else "N/A",
            color = Ink,
            fontFamily = mono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (enabled && file.isArchive) Banana else Color(0x66FFD43B))
                .clickable(enabled = enabled && file.isArchive) { onGet() }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
