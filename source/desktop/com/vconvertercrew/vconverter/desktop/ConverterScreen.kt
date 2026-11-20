package com.vconvertercrew.vconverter.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.vconvertercrew.vconverter.chart.preview.ChartWorkbench
import com.vconvertercrew.vconverter.converterview.ConverterArt
import com.vconvertercrew.vconverter.converterview.ConverterMenuSpec
import com.vconvertercrew.vconverter.converterview.ConverterMenus
import com.vconvertercrew.vconverter.converterview.ConverterRow
import com.vconvertercrew.vconverter.converterview.FunkinConverterMenu
import com.vconvertercrew.vconverter.converterview.FunkinModMenu
import com.vconvertercrew.vconverter.converterview.ModMenuInfo
import com.vconvertercrew.vconverter.converterview.ModMenuSpec
import com.vconvertercrew.vconverter.converterview.ModMenus
import com.vconvertercrew.vconverter.overlay.DiscordBadge

fun usesFunkinConverter(state: AppState): Boolean = state.tool == null && (state.tab == Tab.MOD || state.tab == Tab.CHARTS || state.tab == Tab.CHARACTERS)

@Composable
fun FunkinConverterScreen(state: AppState) {
    if (state.tab == Tab.MOD) {
        FunkinModScreen(state)
        return
    }
    val files = state.files()
    val art = remember { ConverterArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val charts = files.isCharts
    val rows = files.items.map { item ->
        val difficulty = if (charts) ChartWorkbench.difficultyOf(files.converter, item) else null
        ConverterRow(
            item.name,
            item.label,
            listOfNotNull(item.kind, difficulty).joinToString(" · "),
            if (charts) (if (item.kind == "chart") "bf" else "gf") else ConverterMenus.iconFor(item.name),
            item.slot
        )
    }
    val output = files.output
    val renames = if (charts && files.canRenameDifficulty) files.items.mapIndexedNotNull { index, item ->
        ChartWorkbench.difficultyOf(files.converter, item)?.let { difficulty ->
            Triple(item.name, { files.items.getOrNull(index)?.let { ChartWorkbench.difficultyOf(files.converter, it) } ?: difficulty }, { value: String -> if (value.isNotBlank()) files.setDifficulty(index, value.trim()) })
        }
    } else emptyList()
    val previews = if (files.showAfter && files.after.isNotEmpty()) files.after else files.before
    val spec = ConverterMenuSpec(
        title = state.tab.title,
        direction = "${state.direction.from.title} to ${state.direction.to.title}",
        accent = state.tab.accent,
        rows = rows,
        busy = files.busy,
        canConvert = files.converter.canConvert(files.items),
        output = output,
        status = files.status?.text,
        statusError = files.status is Status.Error,
        onAddFiles = { Dialogs.openFiles("Add ${state.direction.from.title} files").takeIf { it.isNotEmpty() }?.let { state.addFiles(it) } },
        onAddFolder = { Dialogs.chooseFolder("Add a folder")?.let { state.addFiles(listOf(it)) } },
        onRemove = { files.remove(it) },
        onClear = { files.clear() },
        onSlot = { index, slot -> files.setSlot(index, slot) },
        onConvert = { state.convertFiles() },
        onSaveZip = { state.saveFiles(zip = true) },
        onSaveFolder = { state.saveFiles(zip = false) },
        zipLabel = "Save As Zip",
        onPlayTest = if (output != null && output.files.any { playableResult(it.relativePath) }) {
            { state.playTestFiles(output.files.map { it.relativePath to (it.bytes ?: it.content.encodeToByteArray()) }, "${state.tab.label} result") }
        } else null,
        onTestGame = if (state.lastMod != null && state.direction.to == Engine.VSLICE && output != null) {
            { state.testInFunkin { files.status = it } }
        } else null,
        options = if (charts && files.items.any { it.kind == "chart" }) ConverterMenus.chartOptions(
            files.chartFields,
            { files.tweaks },
            { change -> files.updateTweaks(change) },
            files.difficulties,
            { difficulty -> files.before.firstOrNull { it.difficulty == difficulty }?.speed },
            { difficulty, raw -> files.setSpeed(difficulty, raw) },
            files.before.firstOrNull(),
            renames
        ) else emptyList(),
        previews = if (charts) previews else emptyList(),
        preview = if (charts) files.currentPreview else null,
        onPreview = { picked -> files.selected = files.key(files.before.firstOrNull { picked.matches(it) } ?: picked) },
        showingAfter = files.showAfter,
        onToggleAfter = { if (files.after.isNotEmpty()) files.showAfter = !files.showAfter },
        onBack = {
            MainMenu.open = true
            MainMenu.list = "converter"
        }
    )
    FunkinConverterMenu(spec, art, StripColors, overlay = { DiscordBadge(Modifier.align(Alignment.BottomStart).padding(16.dp)) })
}

@Composable
fun FunkinModScreen(state: AppState) {
    val mod = state.mod()
    val art = remember { ConverterArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val bananaScope = androidx.compose.runtime.rememberCoroutineScope()
    val banana = remember(state) {
        com.vconvertercrew.vconverter.converterview.GameBananaState(
            bananaScope,
            { bytes -> runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() }
        ) { _, file, progress ->
        val downloaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.vconvertercrew.vconverter.online.GameBananaDownloads.toFile(file, java.io.File(System.getProperty("java.io.tmpdir"), "v-converter-gamebanana"), progress)
        }
        state.openMod(downloaded)
    }
    }
    val loaded = mod.mod
    val written = state.lastOutput
    val spec = ModMenuSpec(
        source = state.direction.from.title,
        target = state.direction.to.title,
        accent = Tab.MOD.accent,
        modName = loaded?.name,
        modRoot = loaded?.root,
        stats = loaded?.stats.orEmpty(),
        songs = loaded?.songs.orEmpty(),
        warnings = emptyList(),
        info = ModMenuInfo(mod.title, mod.description, mod.author, { mod.title = it }, { mod.description = it }, { mod.author = it }, ModMenus.infoNote(state.direction.to.title)),
        busy = mod.busy,
        progress = mod.progress,
        onCancel = if (mod.cancel != null) ({ mod.cancelConversion() }) else null,
        cancelling = mod.cancelling,
        status = mod.status?.text,
        statusError = mod.status is Status.Error,
        report = mod.report,
        onOpenFolder = { Dialogs.chooseFolder("Open a ${state.direction.from.title} mod folder")?.let { state.openMod(it) } },
        onOpenArchive = { Dialogs.openFile("Open a mod archive", setOf("zip", "rar", "7z"))?.let { state.openMod(it) } },
        onClear = { mod.clear() },
        onInstall = { state.convertMod(zip = false) },
        onSaveZip = { state.convertMod(zip = true) },
        zipLabel = "Save As Zip",
        onPlayTest = if (mod.report != null && written != null && written.exists()) {
            {
                state.playtest.open(listOf(written))
                state.tool = EditorTool.PLAYTEST
            }
        } else null,
        onTestGame = if (mod.report != null && state.lastMod != null && state.direction.to == Engine.VSLICE) {
            { state.testInFunkin { mod.status = it } }
        } else null,
        onBack = {
            MainMenu.open = true
            MainMenu.list = "converter"
        }
    )
    FunkinModMenu(spec, art, gameBanana = banana, overlay = { DiscordBadge(Modifier.align(Alignment.BottomStart).padding(16.dp)) })
}
