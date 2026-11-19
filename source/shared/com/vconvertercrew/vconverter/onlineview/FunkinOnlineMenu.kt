package com.vconvertercrew.vconverter.onlineview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.core.fmt
import com.vconvertercrew.vconverter.online.ModEngine
import com.vconvertercrew.vconverter.online.ModEntry
import com.vconvertercrew.vconverter.online.RoomInfo
import com.vconvertercrew.vconverter.overlay.FunkinTransition
import com.vconvertercrew.vconverter.overlay.HealthIcons
import com.vconvertercrew.vconverter.overlay.MainMenuArt
import com.vconvertercrew.vconverter.overlay.MenuArt
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vtouch.vMenuNavigation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class OnlineMenuArt(val menu: MenuArt, val background: ImageBitmap?, val icons: HealthIcons) {
    companion object {
        const val DESAT = "images/ui/menu/menuDesat.png"

        fun load(image: (String) -> ImageBitmap?, text: (String) -> String?): OnlineMenuArt = OnlineMenuArt(
            MenuArt.load(image, text),
            image(DESAT) ?: image(MainMenuArt.BACKGROUND),
            HealthIcons(image)
        )
    }
}

class OnlineActions(
    val download: (ModEntry) -> Unit,
    val pickFile: () -> Unit,
    val join: (RoomInfo) -> Unit,
    val openLink: (String) -> Unit
)

private enum class OnlinePage(val title: String) {
    HUB("Online"),
    MODS("Mod Gallery"),
    ROOMS("Public Rooms"),
    SHARE("Share a Mod"),
    NEWS("News")
}

private class OnlineEdit(val label: String, val value: String, val placeholder: String = "", val set: (String) -> Unit)

private class OnlineItem(
    val text: String,
    val icon: String? = null,
    val color: Color = Colors.idle,
    val info: List<String> = emptyList(),
    val accept: (() -> Unit)? = null,
    val cycle: ((Int) -> Unit)? = null,
    val edit: OnlineEdit? = null
)

private object Colors {
    val idle = Color(0xFF9271FD)
    val mods = Color(0xFF31B0D1)
    val rooms = Color(0xFFA5004D)
    val share = Color(0xFFB7D855)
    val news = Color(0xFFAF66CE)
    val warning = Color(0xFFD57E00)
    val grey = Color(0xFF8D8D8D)
    val variety = listOf(
        Color(0xFF31B0D1), Color(0xFFD57E00), Color(0xFFB7D855), Color(0xFFD8558E), Color(0xFFAF66CE),
        Color(0xFFA5004D), Color(0xFFFFAA6F), Color(0xFF7BD6F6), Color(0xFFF3FF6E), Color(0xFFFF3C6E)
    )
}

private val sorts = listOf("new" to "Newest", "downloads" to "Most Downloaded", "title" to "A to Z")
private val roomKinds = listOf<Pair<String?, String>>(null to "All", "chart" to "Chart Editor", "character" to "Character Editor", "stage" to "Stage Editor", "week" to "Week Editor")
private val roomIcons = mapOf("chart" to "bf", "character" to "pico", "stage" to "spooky", "week" to "dad")
private const val NO_SERVER = "No V-Converter server answered. Check your internet connection and choose Retry, or type a server address in Options, then Preferences, then Online Server."
private const val MAX_CHARS = 26

private fun bytes(size: Long): String = when {
    size >= 1024 * 1024 -> fmt("%.1f MB", size / 1024.0 / 1024.0)
    size >= 1024 -> fmt("%.0f KB", size / 1024.0)
    else -> "$size B"
}

private fun pick(seed: String): Int = seed.fold(7) { hash, char -> hash * 31 + char.code } and Int.MAX_VALUE

private fun <T> List<T>.cycled(current: T, by: Int): T {
    val index = indexOf(current).coerceAtLeast(0)
    return this[((index + by) % size + size) % size]
}

private fun label(text: String): String = if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS - 3).trimEnd() + "..."

private fun hubItems(state: OnlineState, go: (OnlinePage) -> Unit): List<OnlineItem> {
    val online = com.vconvertercrew.vconverter.online.Online
    val server = when {
        state.configured -> (if (online.isAutomatic) "Server (automatic): " else "Server: ") + online.activeUrl
        AutoServer.searching -> "Looking for a server..."
        else -> NO_SERVER
    }
    return listOf(
        OnlineItem("Mod Gallery", "bf", Colors.mods, listOf("MOD GALLERY", "Find mods people shared on the server and download them.", server), accept = { go(OnlinePage.MODS) }),
        OnlineItem("Public Rooms", "gf", Colors.rooms, listOf("PUBLIC ROOMS", "Join a Multieditor room someone listed publicly and edit together.", server), accept = { go(OnlinePage.ROOMS) }),
        OnlineItem("Share a Mod", "pico", Colors.share, listOf("SHARE A MOD", "Upload a mod .zip to the gallery so others can download it.", server), accept = { go(OnlinePage.SHARE) }),
        OnlineItem("News", "dad", Colors.news, listOf("NEWS", "New versions of V-Converter and announcements from the server.", server), accept = { go(OnlinePage.NEWS) })
    )
}

private fun modItems(state: OnlineState, actions: OnlineActions): List<OnlineItem> {
    val list = ArrayList<OnlineItem>()
    list += OnlineItem(
        "Search: " + state.query.ifBlank { "Everything" }, "face", Colors.mods,
        listOf("SEARCH", "Press ENTER to type what you are looking for."),
        edit = OnlineEdit("Search mods", state.query, "Title, author or tag") {
            state.query = it
            state.search()
        }
    )
    list += OnlineItem(
        "Engine: " + (state.engine?.label ?: "All"), "face", Colors.mods,
        listOf("ENGINE", "< ${state.engine?.label ?: "All engines"} >", "LEFT and RIGHT change it."),
        cycle = { by ->
            state.engine = (listOf<ModEngine?>(null) + ModEngine.entries).cycled(state.engine, by)
            state.search()
        }
    )
    val sortLabel = sorts.firstOrNull { it.first == state.sort }?.second ?: "Newest"
    list += OnlineItem(
        "Sort: $sortLabel", "face", Colors.mods,
        listOf("SORT", "< $sortLabel >", "LEFT and RIGHT change it."),
        cycle = { by ->
            state.sort = sorts.map { it.first }.cycled(state.sort, by)
            state.search()
        }
    )
    val page = state.page
    when {
        page == null && state.loading -> list += OnlineItem("Loading...", "face", Colors.grey, listOf("LOADING", "Asking the server for mods."))
        page != null && page.mods.isEmpty() -> list += OnlineItem("No mods found", "face", Colors.grey, listOf("NO MODS", "Nothing matches this search yet."))
    }
    page?.mods?.forEach { mod ->
        val seed = pick(mod.id)
        val downloading = state.downloading == mod.id
        list += OnlineItem(
            label(mod.title),
            HealthIcons.bundled.filter { it != HealthIcons.FALLBACK }.let { it[seed % it.size] },
            Colors.variety[seed % Colors.variety.size],
            listOfNotNull(
                mod.title.uppercase(),
                mod.author.takeIf { it.isNotBlank() }?.let { "BY ${it.uppercase()}" },
                "${ModEngine.of(mod.engine).label} · ${bytes(mod.size)}",
                "${mod.downloads} DOWNLOAD(S) · ${mod.createdAt.take(10)}",
                mod.description.takeIf { it.isNotBlank() }?.take(220),
                mod.tags.takeIf { it.isNotEmpty() }?.joinToString("  ") { "#$it" },
                if (downloading) "DOWNLOADING ${(state.progress * 100).roundToInt()}%" else "ENTER: DOWNLOAD"
            ),
            accept = { if (state.downloading == null) actions.download(mod) }
        )
    }
    if (page != null && page.pages > 1) {
        val info = listOf("PAGE ${state.pageNumber} OF ${page.pages}", "${page.total} MOD(S)")
        if (state.pageNumber > 1) list += OnlineItem("Previous Page", "face", Colors.mods, info, accept = { state.search(state.pageNumber - 1) })
        if (state.pageNumber < page.pages) list += OnlineItem("Next Page", "face", Colors.mods, info, accept = { state.search(state.pageNumber + 1) })
    }
    return list
}

private fun roomItems(state: OnlineState, actions: OnlineActions): List<OnlineItem> {
    val list = ArrayList<OnlineItem>()
    val kindLabel = roomKinds.firstOrNull { it.first == state.roomKind }?.second ?: "All"
    list += OnlineItem(
        "Editor: $kindLabel", "face", Colors.rooms,
        listOf("EDITOR", "< $kindLabel >", "LEFT and RIGHT change it."),
        cycle = { by ->
            state.roomKind = roomKinds.map { it.first }.cycled(state.roomKind, by)
            state.refreshRooms()
        }
    )
    list += OnlineItem(
        if (state.roomsLoading) "Refreshing..." else "Refresh", "face", Colors.rooms,
        listOf("REFRESH", "Rooms people opened with \"List my room publicly\" in an editor's Multieditor card."),
        accept = { state.refreshRooms() }
    )
    val rooms = state.rooms
    if (rooms != null && rooms.isEmpty()) list += OnlineItem("No rooms right now", "face", Colors.grey, listOf("NO ROOMS", "Start one from any editor's Multieditor card."))
    rooms?.forEach { room ->
        list += OnlineItem(
            label(room.title.ifBlank { room.code }),
            roomIcons[room.kind] ?: "bf",
            Colors.variety[pick(room.code) % Colors.variety.size],
            listOfNotNull(
                room.title.ifBlank { room.code }.uppercase(),
                roomKinds.firstOrNull { it.first == room.kind }?.second?.uppercase(),
                room.host.takeIf { it.isNotBlank() }?.let { "HOST: $it" },
                "${room.peers.size} EDITING · ${room.code}",
                room.peers.takeIf { it.isNotEmpty() }?.joinToString { it.name },
                "ENTER: JOIN"
            ),
            accept = { actions.join(room) }
        )
    }
    return list
}

private fun newsItems(state: OnlineState, actions: OnlineActions): List<OnlineItem> {
    val list = ArrayList<OnlineItem>()
    val version = state.version
    when {
        version == null && state.newsLoading -> list += OnlineItem("Loading...", "face", Colors.grey, listOf("LOADING", "Asking the server for news."))
        version != null && version.updateAvailable -> list += OnlineItem(
            "Update ${version.latest}", "bf", Colors.news,
            listOfNotNull("VERSION ${version.latest} IS OUT", "You have ${version.current}.", version.notes.takeIf { it.isNotBlank() }?.take(400), "ENTER: DOWNLOAD"),
            accept = { (version.url.ifBlank { version.page }).takeIf { it.isNotBlank() }?.let(actions.openLink) }
        )
        version?.latest != null -> list += OnlineItem("Up to date", "bf", Colors.news, listOf("UP TO DATE", "You have the latest version (${version?.current})."))
    }
    state.announcements.forEach { news ->
        val warning = news.level == "warning"
        list += OnlineItem(
            label(news.title),
            if (warning) "spirit" else "gf",
            if (warning) Colors.warning else Colors.variety[pick(news.title) % Colors.variety.size],
            listOfNotNull(news.title.uppercase(), news.body.takeIf { it.isNotBlank() }?.take(400), news.url.takeIf { it.isNotBlank() }?.let { "ENTER: OPEN THE LINK" }),
            accept = { news.url.takeIf { it.isNotBlank() }?.let(actions.openLink) }
        )
    }
    if (state.announcements.isEmpty() && version != null) list += OnlineItem("No announcements", "face", Colors.grey, listOf("NO ANNOUNCEMENTS", "Nothing new right now."))
    list += OnlineItem(if (state.newsLoading) "Checking..." else "Check Again", "face", Colors.news, listOf("CHECK AGAIN", "Ask the server for news again."), accept = { state.refreshNews() })
    return list
}

private fun shareItems(state: OnlineState, actions: OnlineActions): List<OnlineItem> {
    val list = ArrayList<OnlineItem>()
    val file = state.file
    list += OnlineItem(
        "File: " + (file?.first ?: "None"), "pico", Colors.share,
        listOfNotNull("MOD FILE", file?.let { "${it.first} · ${bytes(it.second.size.toLong())}" } ?: "No file picked.", "ENTER: PICK THE .ZIP", "Only .zip files are accepted. Share mods you made or have permission to share."),
        accept = actions.pickFile
    )
    fun field(name: String, value: String, hint: String, set: (String) -> Unit) {
        list += OnlineItem(
            "$name: " + value.ifBlank { "-" }, "face", Colors.share,
            listOf(name.uppercase(), value.ifBlank { hint }, "ENTER: TYPE IT"),
            edit = OnlineEdit(name, value, hint, set)
        )
    }
    field("Title", state.title, "The mod's name") { state.title = it }
    field("Description", state.description, "What is in the mod") { state.description = it }
    field("Author", state.author, "Who made it") { state.author = it }
    field("Tags", state.tags, "Comma separated") { state.tags = it }
    list += OnlineItem(
        "Engine: " + state.uploadEngine.label, "face", Colors.share,
        listOf("ENGINE", "< ${state.uploadEngine.label} >", "LEFT and RIGHT change it."),
        cycle = { by -> state.uploadEngine = ModEngine.entries.cycled(state.uploadEngine, by) }
    )
    list += OnlineItem(
        if (state.uploading) "Uploading..." else "Upload", "bf", Colors.share,
        listOf("UPLOAD", "Send the mod to the gallery."),
        accept = { state.upload() }
    )
    state.uploaded?.let { done ->
        list += OnlineItem(
            "Remove It", "face", Colors.warning,
            listOf("\"${done.mod.title}\" IS IN THE GALLERY", "MOD ID: ${done.mod.id}", "DELETE KEY: ${done.deleteToken}", done.mod.downloadUrl, "Keep the delete key: it is the only way to remove the mod later.", "ENTER: REMOVE IT"),
            accept = { state.deleteUploaded() }
        )
    }
    return list
}

private fun notConfigured(retry: () -> Unit): List<OnlineItem> =
    if (AutoServer.searching) listOf(OnlineItem("Connecting...", "face", Colors.grey, listOf("CONNECTING", "Looking for a V-Converter server.")))
    else listOf(OnlineItem("No server online", "face", Colors.grey, listOf("NO SERVER", NO_SERVER, "ENTER: RETRY"), accept = retry))

@Composable
fun FunkinOnlineMenu(
    state: OnlineState,
    art: OnlineMenuArt,
    actions: OnlineActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backRequests: Int = 0,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var page by remember { mutableStateOf(OnlinePage.HUB) }
    var selected by remember { mutableIntStateOf(0) }
    var hubSelected by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<OnlineEdit?>(null) }
    var draft by remember { mutableStateOf("") }
    var leaving by remember { mutableStateOf(false) }
    val exit = rememberUpdatedState(onBack)
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun open(target: OnlinePage) {
        hubSelected = selected
        page = target
        selected = 0
    }

    val items = when {
        page == OnlinePage.HUB -> hubItems(state, ::open)
        !state.configured -> notConfigured { AutoServer.refresh(scope) }
        page == OnlinePage.MODS -> modItems(state, actions)
        page == OnlinePage.ROOMS -> roomItems(state, actions)
        page == OnlinePage.NEWS -> newsItems(state, actions)
        else -> shareItems(state, actions)
    }
    val live = rememberUpdatedState(items)
    val count = items.size
    val current = items.getOrNull(selected)
    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.85f, stiffness = 260f), label = "online")
    val tint by animateColorAsState(current?.color ?: Colors.idle, tween(500), label = "tint")
    val ticker = rememberInfiniteTransition(label = "alphabet")
    val tickValue by ticker.animateFloat(0f, 4f, infiniteRepeatable(tween(4 * 1000 / 24, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val tick = tickValue.toInt().coerceIn(0, 3)
    val rects = remember { arrayOf<List<Rect>>(emptyList()) }
    val backRect = remember { arrayOf(Rect.Zero) }
    val area = remember { floatArrayOf(1280f, 720f) }

    fun move(by: Int) {
        val size = live.value.size
        if (size == 0 || by == 0) return
        selected = ((selected + by) % size + size) % size
        UiSound.click()
    }

    fun accept(direction: Int = 0) {
        if (leaving) return
        val item = live.value.getOrNull(selected) ?: return
        when {
            direction != 0 -> item.cycle?.invoke(direction) ?: return
            item.edit != null -> {
                draft = item.edit.value
                editing = item.edit
            }
            item.accept != null -> item.accept.invoke()
            item.cycle != null -> item.cycle.invoke(1)
            else -> return
        }
        UiSound.click()
    }

    fun back() {
        if (leaving) return
        UiSound.click()
        if (page != OnlinePage.HUB) {
            page = OnlinePage.HUB
            selected = hubSelected
            return
        }
        leaving = true
        FunkinTransition.go {
            leaving = false
            exit.value()
        }
    }

    LaunchedEffect(page, state.configured) {
        if (!state.configured) return@LaunchedEffect
        when (page) {
            OnlinePage.MODS -> if (state.page == null) state.search()
            OnlinePage.ROOMS -> if (state.rooms == null) state.refreshRooms()
            OnlinePage.NEWS -> if (state.version == null && state.announcements.isEmpty()) state.refreshNews()
            else -> Unit
        }
    }
    LaunchedEffect(count) { if (selected >= count) selected = max(0, count - 1) }
    LaunchedEffect(backRequests) {
        if (backRequests > 0) {
            if (editing != null) editing = null else back()
        }
    }
    LaunchedEffect(editing) { if (editing == null) runCatching { focus.requestFocus() } }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || editing != null) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> move(-1)
                    Key.DirectionDown, Key.S -> move(1)
                    Key.DirectionLeft, Key.A -> accept(-1)
                    Key.DirectionRight, Key.D -> accept(1)
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> accept()
                    Key.Escape, Key.Backspace, Key.Back -> back()
                    else -> return@onKeyEvent false
                }
                true
            }
            .onSizeChanged {
                area[0] = it.width.toFloat()
                area[1] = it.height.toFloat()
            }
            .vMenuNavigation(
                key = count to page,
                step = { 120f * 1.3f * onlineScale(area[0], area[1]) },
                onMove = { move(it) },
                onTap = { tap ->
                    if (backRect[0].contains(tap)) back() else {
                        val hit = rects[0].indexOfFirst { it.contains(tap) }
                        if (hit >= 0) {
                            if (hit == selected) accept() else {
                                selected = hit
                                UiSound.click()
                            }
                        }
                    }
                },
                onBack = { back() },
                enabled = { editing == null }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            art.background?.let { bg ->
                val cover = max(w / bg.width, h / bg.height) * 1.1f
                val dw = bg.width * cover
                val dh = bg.height * cover
                drawImage(
                    bg,
                    dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    colorFilter = ColorFilter.tint(tint, BlendMode.Modulate)
                )
            }
            val scale = onlineScale(w, h)
            val font = art.menu.font
            val hits = ArrayList<Rect>(items.size)
            items.forEachIndexed { index, item ->
                val target = index - position
                val x = (90f + target * 20f) * scale
                val y = h * 0.48f + target * 1.3f * 120f * scale
                val text = item.text.uppercase()
                val alpha = if (index == selected) 1f else 0.6f
                val lineHeight = (font?.lineHeight ?: 70f) * scale
                val width = (font?.width(text) ?: (text.length * 45f)) * scale
                hits += Rect(0f, y - 10f * scale, w, y + lineHeight + 10f * scale)
                if (y + lineHeight + 150f * scale < 0f || y - 150f * scale > h) return@forEachIndexed
                if (font != null) with(font) { draw(text, x, y, scale, alpha, tick) }
                item.icon?.let { name ->
                    val icon = art.icons[name] ?: return@let
                    val frame = if (icon.width >= icon.height * 2) icon.width / 2 else icon.width
                    val iconSize = HealthIcons.FRAME * scale
                    drawImage(
                        icon,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(frame, icon.height),
                        dstOffset = IntOffset((x + width + 10f * scale).roundToInt(), (y - 30f * scale).roundToInt()),
                        dstSize = IntSize((iconSize * frame / icon.height).roundToInt(), iconSize.roundToInt()),
                        alpha = alpha
                    )
                }
            }
            rects[0] = hits
            backRect[0] = art.menu.back?.let { back -> with(back) { draw(0, scale) } } ?: Rect.Zero
        }
        if (art.menu.font == null) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
                items.forEachIndexed { index, item ->
                    Text(item.text.uppercase(), color = Color.White.copy(alpha = if (index == selected) 1f else 0.6f), fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(
            if (page == OnlinePage.HUB) "ONLINE" else "ONLINE / ${page.title.uppercase()}",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        current?.let { item ->
            if (item.info.isEmpty()) return@let
            SelectionContainer(Modifier.align(Alignment.TopEnd).widthIn(max = 420.dp)) {
                Column(
                    Modifier.background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    item.info.forEachIndexed { index, line ->
                        Text(
                            line,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (index == 0) 20.sp else 13.sp,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
        overlay()
        val status = when (page) {
            OnlinePage.MODS -> state.error ?: state.message
            OnlinePage.ROOMS -> state.roomsError
            OnlinePage.NEWS -> state.newsError
            OnlinePage.SHARE -> state.uploadError ?: state.message
            OnlinePage.HUB -> null
        }
        if (status != null) {
            Text(
                status,
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 120.dp, bottom = 16.dp)
                    .widthIn(max = 640.dp)
                    .background(Color(0x99000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        editing?.let { edit ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1B26)).padding(18.dp)
                ) {
                    Text(edit.label.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text(edit.placeholder) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            edit.set(draft)
                            editing = null
                        }),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFEA71FD)),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                editing = null
                                true
                            } else false
                        }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = UiSound.wrap { editing = null }) { Text("CANCEL", color = Color(0xFFB8B8C8)) }
                        TextButton(onClick = UiSound.wrap {
                            edit.set(draft)
                            editing = null
                        }) { Text("OK", color = Color(0xFFEA71FD), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun onlineScale(width: Float, height: Float): Float = min(width / 1300f, height / 720f).coerceAtLeast(0.2f)
