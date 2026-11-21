package com.vconvertercrew.vconverter.ui.converter

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vconvertercrew.vconverter.convert.PsychScriptsConverter
import com.vconvertercrew.vconverter.convert.PsychStagesConverter
import com.vconvertercrew.vconverter.convert.PsychWeeksConverter
import com.vconvertercrew.vconverter.converterview.ConverterMenuSpec
import com.vconvertercrew.vconverter.converterview.FunkinConverterMenu
import com.vconvertercrew.vconverter.converterview.GeneratorMenus
import com.vconvertercrew.vconverter.converterview.ScriptForm
import com.vconvertercrew.vconverter.generator.ModSpec
import com.vconvertercrew.vconverter.overlay.MenuOption
import com.vconvertercrew.vconverter.ui.charts.FunkinStripColors
import com.vconvertercrew.vconverter.ui.components.rememberDownloader
import com.vconvertercrew.vconverter.ui.generator.ModGenViewModel
import com.vconvertercrew.vconverter.ui.generator.ScriptGenViewModel
import com.vconvertercrew.vconverter.ui.reverse.ReverseViewModel

class PsychStagesFilesViewModel(application: Application) : ReverseViewModel(application, PsychStagesConverter())

class PsychWeeksFilesViewModel(application: Application) : ReverseViewModel(application, PsychWeeksConverter())

class PsychScriptsFilesViewModel(application: Application) : ReverseViewModel(application, PsychScriptsConverter())

private fun ModGenViewModel.spec(): ModSpec = ModSpec(title, description, author, version, homepage, license, icon, starterModule)

private fun ModGenViewModel.edit(change: (ModSpec) -> ModSpec) {
    val next = change(spec())
    title = next.title
    description = next.description
    author = next.author
    version = next.version
    homepage = next.homepage
    license = next.license
    icon = next.icon
    starterModule = next.starterModule
}

private fun ScriptGenViewModel.form(): ScriptForm = ScriptForm(kind, targetId, className, callbacks, render, title, fields, priority)

private fun ScriptGenViewModel.edit(change: (ScriptForm) -> ScriptForm) {
    val next = change(form())
    kind = next.kind
    targetId = next.targetId
    className = next.className
    callbacks = next.callbacks
    render = next.render
    title = next.title
    fields = next.fields
    priority = next.priority
}

@Composable
fun FunkinModGenerator(
    viewModel: ModGenViewModel,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val art = rememberConverterArt()
    val zip = rememberDownloader(viewModel)
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.pickIcon(uri) }
    val (text, error) = status(viewModel)
    val current = viewModel.spec()
    val spec = ConverterMenuSpec(
        title = "New Mod",
        direction = "V-Slice mod folder",
        accent = GeneratorMenus.modAccent,
        rows = emptyList(),
        busy = viewModel.busy,
        canConvert = current.title.isNotBlank(),
        output = viewModel.output,
        status = text,
        statusError = error,
        onAddFiles = {},
        onAddFolder = null,
        onRemove = {},
        onClear = {},
        onSlot = null,
        onConvert = { viewModel.generate() },
        onSaveZip = zip,
        onSaveFolder = onSave,
        zipLabel = "Download Zip",
        folderLabel = if (modFolderName != null) "Save Into Mod" else "Save To Mod Folder",
        options = GeneratorMenus.modOptions({ viewModel.spec() }, { change -> viewModel.edit(change) }),
        onBack = onBack,
        addLabel = null,
        convertLabel = "Generate",
        convertAgainLabel = "Generate Again",
        convertHint = "Makes _polymod_meta.json, the folders and the starter script.",
        busyLabel = "Generating...",
        optionsLabel = "Mod Info",
        optionsHint = "Title, authors, version, homepage, license and the starter module.",
        extraItems = buildList {
            add(MenuOption.Action("Pick Icon", "A 150×150 .png that becomes _polymod_icon.png.") { runCatching { iconPicker.launch(arrayOf("image/png")) } })
            if (current.icon != null) add(MenuOption.Action("Remove Icon", "Generates the mod without an icon.") { viewModel.icon = null })
        },
        countLabel = "V-SLICE MOD",
        infoLines = GeneratorMenus.modSummary(current),
        backHint = "Goes back to the generator menu."
    )
    FunkinConverterMenu(spec, art, FunkinStripColors, overlay = overlay)
}

@Composable
fun FunkinScriptGenerator(
    viewModel: ScriptGenViewModel,
    modFolderName: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val art = rememberConverterArt()
    val zip = rememberDownloader(viewModel)
    val (text, error) = status(viewModel)
    val current = viewModel.form()
    val spec = ConverterMenuSpec(
        title = "New Script",
        direction = "V-Slice .hxc class",
        accent = GeneratorMenus.scriptAccent,
        rows = emptyList(),
        busy = viewModel.busy,
        canConvert = current.targetId.isNotBlank(),
        output = viewModel.output,
        status = text,
        statusError = error,
        onAddFiles = {},
        onAddFolder = null,
        onRemove = {},
        onClear = {},
        onSlot = null,
        onConvert = { viewModel.generate() },
        onSaveZip = zip,
        onSaveFolder = onSave,
        zipLabel = "Download Zip",
        folderLabel = if (modFolderName != null) "Save Into Mod" else "Save To Mod Folder",
        options = GeneratorMenus.scriptOptions({ viewModel.form() }, { change -> viewModel.edit(change) }),
        onBack = onBack,
        addLabel = null,
        convertLabel = "Generate",
        convertAgainLabel = "Generate Again",
        convertHint = "Writes the .hxc class into scripts/${current.kind.folder}/.",
        busyLabel = "Generating...",
        optionsLabel = "Script Setup",
        optionsHint = "Type, id, class name and the callbacks it overrides.",
        countLabel = current.kind.label.uppercase() + " SCRIPT",
        infoLines = GeneratorMenus.scriptSummary(current),
        code = viewModel.output?.preview,
        backHint = "Goes back to the generator menu."
    )
    FunkinConverterMenu(spec, art, FunkinStripColors, overlay = overlay)
}

@Composable
fun FunkinNmvModConverter(
    viewModel: com.vconvertercrew.vconverter.ui.nmv.NmvModViewModel,
    source: String,
    target: String,
    accent: androidx.compose.ui.graphics.Color,
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
            listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size, "Assets" to scan.assets, "Scripts" to scan.scripts),
            scan.songs,
            emptyMap()
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
