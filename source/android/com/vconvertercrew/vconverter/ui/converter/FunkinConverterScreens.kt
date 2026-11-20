package com.vconvertercrew.vconverter.ui.converter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.vconvertercrew.vconverter.convert.ConvertedOutput
import com.vconvertercrew.vconverter.converterview.ConverterArt
import com.vconvertercrew.vconverter.converterview.ConverterMenuSpec
import com.vconvertercrew.vconverter.converterview.ConverterMenus
import com.vconvertercrew.vconverter.converterview.ConverterRow
import com.vconvertercrew.vconverter.converterview.FunkinConverterMenu
import com.vconvertercrew.vconverter.converterview.FunkinModMenu
import com.vconvertercrew.vconverter.converterview.ModMenuInfo
import com.vconvertercrew.vconverter.converterview.ModMenuSpec
import com.vconvertercrew.vconverter.converterview.ModMenus
import com.vconvertercrew.vconverter.ui.BaseConverterViewModel
import com.vconvertercrew.vconverter.ui.StatusMessage
import com.vconvertercrew.vconverter.ui.characters.CharactersViewModel
import com.vconvertercrew.vconverter.ui.charts.ChartsViewModel
import com.vconvertercrew.vconverter.ui.charts.FunkinStripColors
import com.vconvertercrew.vconverter.ui.components.rememberDownloader
import com.vconvertercrew.vconverter.ui.components.rememberFunkinInstaller
import com.vconvertercrew.vconverter.ui.playtest.LocalPlayTest
import com.vconvertercrew.vconverter.ui.reverse.ReverseViewModel

private val playableChart = Regex("(^|/)data/(songs/[^/]+/[^/]+-chart|[^/]+/[^/]+)\\.json$|(^|/)songs/[^/]+/charts/[^/]+\\.json$", RegexOption.IGNORE_CASE)
private val notCharts = Regex("(^|/)data/(characters|stages|levels|weeks|events|notes|config|dialogue)/", RegexOption.IGNORE_CASE)

private fun playable(output: ConvertedOutput?): Boolean = output?.files?.any { playableChart.containsMatchIn(it.relativePath) && !notCharts.containsMatchIn(it.relativePath) } == true

@Composable
private fun rememberConverterArt(): ConverterArt {
    val context = LocalContext.current
    return remember {
        val assets = context.assets
        ConverterArt.load(
            { path -> runCatching { assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() },
            { path -> runCatching { assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull() }
        )
    }
}

@Composable
private fun rememberPicker(onPick: (List<android.net.Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) onPick(uris) }
    return { launcher.launch(arrayOf("*/*")) }
}

private class GameTest(val action: (() -> Unit)?, val problem: String?)

@Composable
private fun rememberGameTest(viewModel: BaseConverterViewModel): GameTest {
    var problem by remember { mutableStateOf<String?>(null) }
    val open = rememberFunkinInstaller(viewModel) { problem = it }
    LaunchedEffect(viewModel.download, viewModel.output) { problem = null }
    return GameTest(if (viewModel.vsliceOutput && viewModel.download != null) open else null, problem)
}

private fun status(viewModel: BaseConverterViewModel): Pair<String?, Boolean> =
    viewModel.status?.text to (viewModel.status is StatusMessage.Error)

@Composable
fun FunkinChartsConverter(
    viewModel: ChartsViewModel,
    direction: String,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val art = rememberConverterArt()
    val pick = rememberPicker { viewModel.add(it) }
    val zip = rememberDownloader(viewModel)
    val launcher = LocalPlayTest.current
    val output = viewModel.output
    val (text, error) = status(viewModel)
    val game = rememberGameTest(viewModel)
    val rows = viewModel.files.map { item ->
        ConverterRow(item.name, item.label, listOfNotNull(item.kind, viewModel.difficultyOf(item)).joinToString(" · "), if (item.kind == "chart") "bf" else "gf", item.slot)
    }
    val renames = if (viewModel.canRenameDifficulty) viewModel.files.mapIndexedNotNull { index, item ->
        viewModel.difficultyOf(item)?.let { difficulty ->
            Triple(item.name, { viewModel.files.getOrNull(index)?.let { viewModel.difficultyOf(it) } ?: difficulty }, { value: String -> if (value.isNotBlank()) viewModel.setDifficulty(index, value.trim()) })
        }
    } else emptyList()
    val previews = if (viewModel.showAfter && viewModel.after.isNotEmpty()) viewModel.after else viewModel.before
    val spec = ConverterMenuSpec(
        title = "Charts",
        direction = direction,
        accent = accent,
        rows = rows,
        busy = viewModel.busy,
        canConvert = viewModel.canConvert,
        output = output,
        status = game.problem ?: text,
        statusError = game.problem != null || error,
        onAddFiles = pick,
        onAddFolder = null,
        onRemove = { viewModel.remove(it) },
        onClear = { viewModel.clear() },
        onSlot = { index, slot -> viewModel.setSlot(index, slot) },
        onConvert = { viewModel.convert() },
        onSaveZip = zip,
        onSaveFolder = onSave,
        zipLabel = "Download Zip",
        folderLabel = if (modFolderName != null) "Save Into Mod" else "Save To Mod Folder",
        onTestGame = game.action,
        onPlayTest = if (launcher != null && playable(output) && output != null) {
            { launcher.files(output.files.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) }, "Charts result") }
        } else null,
        options = if (viewModel.files.any { it.kind == "chart" }) ConverterMenus.chartOptions(
            viewModel.fields,
            { viewModel.tweaks },
            { change -> viewModel.updateTweaks(change) },
            viewModel.difficulties,
            { difficulty -> viewModel.before.firstOrNull { it.difficulty == difficulty }?.speed },
            { difficulty, raw -> viewModel.setSpeed(difficulty, raw) },
            viewModel.before.firstOrNull(),
            renames
        ) else emptyList(),
        previews = previews,
        preview = viewModel.current,
        onPreview = { picked -> viewModel.select(viewModel.before.firstOrNull { picked.matches(it) } ?: picked) },
        showingAfter = viewModel.showAfter,
        onToggleAfter = { if (viewModel.after.isNotEmpty()) viewModel.showAfter = !viewModel.showAfter },
        onBack = onBack
    )
    FunkinConverterMenu(spec, art, FunkinStripColors, overlay = overlay)
}

@Composable
fun FunkinFilesConverter(
    viewModel: ReverseViewModel,
    title: String,
    direction: String,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val art = rememberConverterArt()
    val pick = rememberPicker { viewModel.add(it) }
    val zip = rememberDownloader(viewModel)
    val (text, error) = status(viewModel)
    val game = rememberGameTest(viewModel)
    val spec = ConverterMenuSpec(
        title = title,
        direction = direction,
        accent = accent,
        rows = viewModel.files.map { item -> ConverterRow(item.name, item.label, item.kind, ConverterMenus.iconFor(item.name), item.slot) },
        busy = viewModel.busy,
        canConvert = viewModel.canConvert,
        output = viewModel.output,
        status = game.problem ?: text,
        statusError = game.problem != null || error,
        onAddFiles = pick,
        onAddFolder = null,
        onRemove = { viewModel.remove(it) },
        onClear = { viewModel.clear() },
        onSlot = { index, slot -> viewModel.setSlot(index, slot) },
        onConvert = { viewModel.convert() },
        onSaveZip = zip,
        onSaveFolder = onSave,
        zipLabel = "Download Zip",
        folderLabel = if (modFolderName != null) "Save Into Mod" else "Save To Mod Folder",
        onTestGame = game.action,
        onBack = onBack
    )
    FunkinConverterMenu(spec, art, FunkinStripColors, overlay = overlay)
}

@Composable
fun FunkinCharactersConverter(
    viewModel: CharactersViewModel,
    direction: String,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val art = rememberConverterArt()
    val pick = rememberPicker { viewModel.add(it) }
    val zip = rememberDownloader(viewModel)
    val (text, error) = status(viewModel)
    val game = rememberGameTest(viewModel)
    val spec = ConverterMenuSpec(
        title = "Characters",
        direction = direction,
        accent = accent,
        rows = viewModel.characters.map { item ->
            val missing = viewModel.missingAtlases(item)
            ConverterRow(
                item.fileName,
                "ID: ${item.id}" + if (viewModel.isAnimate(item)) " · Animate atlas" else "",
                "character",
                ConverterMenus.iconFor(item.character.healthIcon.ifBlank { item.id }),
                item.slot,
                if (missing.isNotEmpty()) "Missing spritesheet: ${missing.joinToString()}" else null
            )
        },
        busy = viewModel.busy,
        canConvert = viewModel.characters.isNotEmpty(),
        output = viewModel.output,
        status = game.problem ?: text,
        statusError = game.problem != null || error,
        onAddFiles = pick,
        onAddFolder = null,
        onRemove = { viewModel.remove(it) },
        onClear = { viewModel.clear() },
        onSlot = { index, slot -> viewModel.setSlot(index, slot) },
        onConvert = { viewModel.convert() },
        onSaveZip = zip,
        onSaveFolder = onSave,
        zipLabel = "Download Zip",
        folderLabel = if (modFolderName != null) "Save Into Mod" else "Save To Mod Folder",
        onTestGame = game.action,
        onBack = onBack
    )
    FunkinConverterMenu(spec, art, FunkinStripColors, overlay = overlay)
}

private class ModSummary(val name: String, val root: String, val stats: List<Pair<String, Int>>, val songs: List<String>, val placeholders: Map<String, String>)

@Composable
private fun FunkinModConverterMenu(
    viewModel: BaseConverterViewModel,
    summary: ModSummary?,
    source: String,
    target: String,
    accent: Color,
    title: String,
    description: String,
    author: String,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onAuthor: (String) -> Unit,
    report: com.vconvertercrew.vconverter.core.Report?,
    progress: com.vconvertercrew.vconverter.mod.ModProgress?,
    openFolder: (android.net.Uri) -> Unit,
    openArchive: (android.net.Uri) -> Unit,
    clear: () -> Unit,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    val art = rememberConverterArt()
    val zip = rememberDownloader(viewModel)
    val (text, error) = status(viewModel)
    val game = rememberGameTest(viewModel)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) openFolder(uri) }
    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) openArchive(uri) }
    val context = LocalContext.current
    val bananaScope = androidx.compose.runtime.rememberCoroutineScope()
    val banana = remember(viewModel) {
        com.vconvertercrew.vconverter.converterview.GameBananaState(
            bananaScope,
            { bytes -> android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        ) { _, file, progress ->
            val downloaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.vconvertercrew.vconverter.online.GameBananaDownloads.toFile(file, java.io.File(context.cacheDir, "gamebanana"), progress)
            }
            openArchive(android.net.Uri.fromFile(downloaded))
        }
    }
    val spec = ModMenuSpec(
        source = source,
        target = target,
        accent = accent,
        modName = summary?.name,
        modRoot = summary?.root,
        stats = summary?.stats.orEmpty(),
        songs = summary?.songs.orEmpty(),
        warnings = summary?.placeholders?.takeIf { it.isNotEmpty() }?.let { listOf("${it.size} empty placeholder folder(s) left out: ${it.keys.joinToString { key -> "$key/" }}") }.orEmpty(),
        info = ModMenuInfo(title, description, author, onTitle, onDescription, onAuthor, ModMenus.infoNote(target)),
        busy = viewModel.busy,
        progress = progress,
        onCancel = if (viewModel.cancellable) ({ viewModel.cancelConversion() }) else null,
        cancelling = viewModel.cancelling,
        status = game.problem ?: text,
        statusError = game.problem != null || error,
        report = report,
        onOpenFolder = { runCatching { folderPicker.launch(null) } },
        onOpenArchive = { runCatching { archivePicker.launch(arrayOf("*/*")) } },
        onClear = clear,
        onInstall = onSave,
        onSaveZip = zip,
        installLabel = if (modFolderName != null) "Convert Into Mod" else "Convert Into Folder",
        zipLabel = "Download Zip",
        onTestGame = game.action,
        onBack = onBack
    )
    FunkinModMenu(spec, art, gameBanana = banana, overlay = overlay)
}

@Composable
fun FunkinPsychModConverter(
    viewModel: com.vconvertercrew.vconverter.ui.mod.ModViewModel,
    source: String,
    target: String,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val progress by viewModel.progress.collectAsState()
    val loaded = viewModel.mod
    val summary = loaded?.let { mod ->
        val scan = mod.scan
        ModSummary(
            mod.sourceName,
            scan.root,
            listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size, "Assets" to scan.assets.size, "Scripts" to scan.scripts.size),
            scan.songs.map { song -> song.id + if (song.charts.isNotEmpty()) " (${song.charts.keys.joinToString("/")})" else "" },
            scan.placeholders
        )
    }
    FunkinModConverterMenu(
        viewModel, summary, source, target, accent,
        viewModel.title, viewModel.description, viewModel.author,
        { viewModel.title = it }, { viewModel.description = it }, { viewModel.author = it },
        viewModel.result, progress,
        viewModel::openFolder, viewModel::openArchive, viewModel::clear,
        modFolderName, onSave, onBack, overlay
    )
}

@Composable
fun FunkinCodenameModConverter(
    viewModel: com.vconvertercrew.vconverter.ui.codename.CodenameModViewModel,
    source: String,
    target: String,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val progress by viewModel.progress.collectAsState()
    val loaded = viewModel.mod
    val summary = loaded?.let { mod ->
        val scan = mod.scan
        ModSummary(
            mod.sourceName,
            scan.root,
            listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size, "Assets" to scan.assets.size, "Scripts" to scan.scriptCount),
            scan.songs.map { song ->
                val difficulties = (song.charts[""]?.keys ?: emptySet()).joinToString("/")
                song.id + (if (difficulties.isNotEmpty()) " ($difficulties)" else "") + if (song.variants.isNotEmpty()) " +" + song.variants.joinToString("/") else ""
            },
            scan.placeholders
        )
    }
    FunkinModConverterMenu(
        viewModel, summary, source, target, accent,
        viewModel.title, viewModel.description, viewModel.author,
        { viewModel.title = it }, { viewModel.description = it }, { viewModel.author = it },
        viewModel.result, progress,
        viewModel::openFolder, viewModel::openArchive, viewModel::clear,
        modFolderName, onSave, onBack, overlay
    )
}

@Composable
fun FunkinVSliceModConverter(
    viewModel: com.vconvertercrew.vconverter.ui.reverse.ReverseModViewModel,
    source: String,
    target: String,
    accent: Color,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val progress by viewModel.progress.collectAsState()
    val loaded = viewModel.mod
    val summary = loaded?.let { mod ->
        val scan = mod.scan
        ModSummary(
            mod.sourceName,
            scan.root,
            listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Levels" to scan.levels.size, "Assets" to scan.assets.size, "Scripts" to scan.scripts.size),
            scan.songs.map { song ->
                val difficulties = song.charts.keys.filter { it.isNotEmpty() }
                song.id + if (difficulties.isNotEmpty()) " (${difficulties.joinToString("/")})" else ""
            },
            scan.placeholders
        )
    }
    FunkinModConverterMenu(
        viewModel, summary, source, target, accent,
        viewModel.title, viewModel.description, viewModel.author,
        { viewModel.title = it }, { viewModel.description = it }, { viewModel.author = it },
        viewModel.result, progress,
        viewModel::openFolder, viewModel::openArchive, viewModel::clear,
        modFolderName, onSave, onBack, overlay
    )
}
