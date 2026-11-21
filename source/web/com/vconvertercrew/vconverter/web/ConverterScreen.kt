package com.vconvertercrew.vconverter.web

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.vconvertercrew.vconverter.platform.File
import com.vconvertercrew.vconverter.chart.preview.ChartWorkbench
import com.vconvertercrew.vconverter.converterview.ConverterArt
import com.vconvertercrew.vconverter.converterview.ConverterMenuSpec
import com.vconvertercrew.vconverter.converterview.ConverterMenus
import com.vconvertercrew.vconverter.converterview.ConverterRow
import com.vconvertercrew.vconverter.converterview.FunkinConverterMenu
import com.vconvertercrew.vconverter.converterview.FunkinModMenu
import com.vconvertercrew.vconverter.converterview.GeneratorActions
import com.vconvertercrew.vconverter.converterview.GeneratorMenus
import com.vconvertercrew.vconverter.converterview.ModMenuInfo
import com.vconvertercrew.vconverter.converterview.ModMenuSpec
import com.vconvertercrew.vconverter.converterview.ModMenus
import com.vconvertercrew.vconverter.overlay.DiscordBadge

fun usesFunkinConverter(state: AppState): Boolean = state.tool == null || state.tool == EditorTool.MOD_GEN || state.tool == EditorTool.SCRIPT_GEN

@Composable
fun FunkinConverterScreen(state: AppState) {
    if (state.tool == EditorTool.MOD_GEN || state.tool == EditorTool.SCRIPT_GEN) {
        FunkinGeneratorScreen(state)
        return
    }
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
        onAddFiles = { Dialogs.openFiles("Add ${state.direction.from.title} files") { if (it.isNotEmpty()) state.addFiles(it) } },
        onAddFolder = { Dialogs.chooseFolder("Add a folder") { state.addFiles(listOf(it)) } },
        onRemove = { files.remove(it) },
        onClear = { files.clear() },
        onSlot = { index, slot -> files.setSlot(index, slot) },
        onConvert = { state.convertFiles() },
        onSaveZip = { state.saveFiles(zip = true) },
        onSaveFolder = null,
        zipLabel = "Download Zip",
        onPlayTest = if (output != null && output.files.any { playableResult(it.relativePath) }) {
            { state.playTestFiles(output.files.map { it.relativePath to (it.bytes ?: it.content.encodeToByteArray()) }, "${state.tab.label} result") }
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
        val bytes = com.vconvertercrew.vconverter.online.GameBanana.download(file) { done, total -> if (total > 0) progress(done.toFloat() / total) }
        val target = File("/gamebanana/" + com.vconvertercrew.vconverter.online.GameBanana.safeFileName(file))
        target.writeBytes(bytes)
        state.openMod(target)
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
        onOpenFolder = { Dialogs.chooseFolder("Open a ${state.direction.from.title} mod folder") { state.openMod(it) } },
        onOpenArchive = { Dialogs.openFile("Open a mod archive", setOf("zip", "rar", "7z")) { state.openMod(it) } },
        onClear = { mod.clear() },
        onInstall = null,
        onSaveZip = { state.convertMod(zip = true) },
        zipLabel = "Download Zip",
        onPlayTest = if (mod.report != null && written != null && written.exists()) {
            {
                state.playtest.open(listOf(written))
                state.tool = EditorTool.PLAYTEST
            }
        } else null,
        onTestGame = null,
        onBack = {
            MainMenu.open = true
            MainMenu.list = "converter"
        }
    )
    FunkinModMenu(spec, art, gameBanana = banana, overlay = { DiscordBadge(Modifier.align(Alignment.BottomStart).padding(16.dp)) })
}

@Composable
fun FunkinGeneratorScreen(state: AppState) {
    val art = remember { ConverterArt.load({ Resources.image(it) }, { Resources.bytes(it)?.decodeToString() }) }
    val back = {
        MainMenu.open = true
        MainMenu.list = "generator"
    }
    val spec = if (state.tool == EditorTool.SCRIPT_GEN) {
        val run = state.scriptGenRun
        GeneratorMenus.scriptSpec(
            { state.scriptGen.form },
            { change -> state.scriptGen.update(change) },
            run,
            GeneratorActions(
                onSaveZip = { output -> state.saveGenerated(run, output, true, state.scriptGen.form.downloadName(), "") },
                onSaveFolder = { output -> state.saveGenerated(run, output, false, "", "") }
            ),
            back
        )
    } else {
        val run = state.modGenRun
        GeneratorMenus.modSpec(
            { state.modGen.spec },
            { change -> state.modGen.update(change) },
            run,
            GeneratorActions(
                onSaveZip = { output ->
                    val folder = GeneratorMenus.folderName(state.modGen.spec.title)
                    state.saveGenerated(run, output, true, folder.replace(' ', '-') + ".zip", folder)
                },
                onSaveFolder = { output -> state.saveGenerated(run, output, false, "", "") }
            ),
            {
            Dialogs.openFile("Pick the mod icon", setOf("png")) { file ->
                runCatching { GeneratorMenus.pickedIcon(file.name, file.readBytes()) }
                    .onSuccess { icon -> state.modGen.update { it.copy(icon = icon) } }
                    .onFailure { state.modGenRun.report(it.message ?: "Could not read the icon.", true) }
            }
        },
            back
        )
    }
    FunkinConverterMenu(spec, art, StripColors, overlay = { DiscordBadge(Modifier.align(Alignment.BottomStart).padding(16.dp)) })
}
