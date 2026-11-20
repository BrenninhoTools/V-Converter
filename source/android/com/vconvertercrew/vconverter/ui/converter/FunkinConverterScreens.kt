package com.vconvertercrew.vconverter.ui.converter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.vconvertercrew.vconverter.convert.ConvertedOutput
import com.vconvertercrew.vconverter.converterview.ConverterArt
import com.vconvertercrew.vconverter.converterview.ConverterMenuSpec
import com.vconvertercrew.vconverter.converterview.ConverterMenus
import com.vconvertercrew.vconverter.converterview.ConverterRow
import com.vconvertercrew.vconverter.converterview.FunkinConverterMenu
import com.vconvertercrew.vconverter.ui.BaseConverterViewModel
import com.vconvertercrew.vconverter.ui.StatusMessage
import com.vconvertercrew.vconverter.ui.characters.CharactersViewModel
import com.vconvertercrew.vconverter.ui.charts.ChartsViewModel
import com.vconvertercrew.vconverter.ui.charts.FunkinStripColors
import com.vconvertercrew.vconverter.ui.components.rememberDownloader
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
        status = text,
        statusError = error,
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
    val spec = ConverterMenuSpec(
        title = title,
        direction = direction,
        accent = accent,
        rows = viewModel.files.map { item -> ConverterRow(item.name, item.label, item.kind, ConverterMenus.iconFor(item.name), item.slot) },
        busy = viewModel.busy,
        canConvert = viewModel.canConvert,
        output = viewModel.output,
        status = text,
        statusError = error,
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
        status = text,
        statusError = error,
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
        onBack = onBack
    )
    FunkinConverterMenu(spec, art, FunkinStripColors, overlay = overlay)
}
