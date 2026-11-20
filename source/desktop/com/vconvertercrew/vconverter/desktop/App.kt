package com.vconvertercrew.vconverter.desktop

import com.vconvertercrew.vconverterui.VButton as Primary
import com.vconvertercrew.vconverterui.VOutlineButton as Secondary
import com.vconvertercrew.vconverterui.VCard as Card
import com.vconvertercrew.vconverterui.VSectionTitle as SectionTitle
import com.vconvertercrew.vconverterui.VBadge as Chip
import com.vconvertercrew.vconverter.overlay.ReportView
import com.vconvertercrew.vconverter.overlay.Field
import com.vconvertercrew.vconverterui.VStatusBanner
import com.vconvertercrew.vconverterui.VStatusKind
import com.vconvertercrew.vconverter.overlay.UiSound
import com.vconvertercrew.vconverter.overlay.soundClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vconvertercrew.vconverter.convert.PickedText
import com.vconvertercrew.vconverter.editorview.typingField
import com.vconvertercrew.vconverter.overlay.CodePreviewText
import com.vconvertercrew.vconverter.overlay.FpsCounter
import com.vconvertercrew.vconverter.overlay.PreviewText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vconvertercrew.vconverter.core.CharacterSlot
import com.vconvertercrew.vconverter.core.Report
import com.vconvertercrew.vconverter.overlay.ModScannerPanel
import com.vconvertercrew.vconverter.overlay.ScannerColors

private val logo = Resources.image("icons/icon.png")

@Composable
fun App(state: AppState) {
    if (state.tool == EditorTool.CHART) {
        Box(Modifier.fillMaxSize()) { EditorScreen(state) }
        return
    }
    if (state.tool == EditorTool.XML) {
        Box(Modifier.fillMaxSize()) { XmlEditorScreen(state) }
        return
    }
    if (state.tool == EditorTool.ONLINE) {
        Box(Modifier.fillMaxSize()) { OnlineScreen(state) }
        return
    }
    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        Sidebar(state)
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Outline))
        Content(state)
    }
}

@Composable
private fun Sidebar(state: AppState) {
    Column(
        Modifier.width(260.dp).fillMaxHeight().background(Palette.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            logo?.let { Image(it, contentDescription = "Main menu", modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).soundClickable { MainMenu.open = true }) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("V-CONVERTER", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text("for Windows", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
        EnginePicker("FROM", state.direction.from, Engine.entries.filter { it.canBeSource }, Engine.entries.filter { it.canBeSource }) { state.selectSource(it) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val canSwap = state.direction.swapped() != null
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(state.direction.from.color, state.direction.to.color)))
                    .border(2.dp, Palette.Ink, CircleShape)
                    .soundClickable(enabled = canSwap) { state.swap() },
                contentAlignment = Alignment.Center
            ) {
                Text(if (canSwap) "⇅" else "↓", color = Palette.Ink, fontSize = 20.sp)
            }
        }
        EnginePicker("TO", state.direction.to, Engine.entries, Direction.targets(state.direction.from)) { state.selectTarget(it) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { tab ->
                val active = state.tool == null && tab == state.tab
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (active) tab.accent else Color.Transparent)
                        .soundClickable { state.tab = tab; state.tool = null }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) Palette.Ink else tab.accent))
                    Spacer(Modifier.width(12.dp))
                    Text(tab.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Ink else Palette.Text)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
        EditorTool.entries.forEach { tool ->
            val active = state.tool == tool
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (active) tool.accent else Palette.SurfaceHigh)
                    .soundClickable { state.tool = tool }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) Palette.Ink else tool.accent))
                Spacer(Modifier.width(12.dp))
                Text(tool.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Ink else Palette.Text)
            }
        }
        Spacer(Modifier.weight(1f))
        state.lastOutput?.let { file ->
            OutlinedButton(onClick = UiSound.wrap { Dialogs.reveal(file) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Palette.Outline)) {
                Text("Show last output", color = Palette.Muted, maxLines = 1)
            }
        }
        Text("Drop folders, archives or files on the window to load them.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

@Composable
private fun EnginePicker(caption: String, selected: Engine, all: List<Engine>, enabled: List<Engine>, onSelect: (Engine) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(caption, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        all.forEach { engine ->
            val active = engine == selected
            val usable = engine in enabled
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) engine.color.copy(alpha = 0.22f) else Palette.SurfaceHigh.copy(alpha = if (usable) 1f else 0.4f))
                    .border(1.5.dp, if (active) engine.color else Color.Transparent, RoundedCornerShape(10.dp))
                    .soundClickable(enabled = usable) { onSelect(engine) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(engine.label, style = MaterialTheme.typography.labelMedium, color = if (usable) engine.color else Palette.Muted.copy(alpha = 0.5f))
                    Text(engine.detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted.copy(alpha = if (usable) 1f else 0.5f))
                }
                if (active) Text("●", color = engine.color)
            }
        }
    }
}

@Composable
private fun Content(state: AppState) {
    val scroll = rememberScrollState()
    val editing = state.tool == EditorTool.CHART || state.tool == EditorTool.CHARACTER || state.tool == EditorTool.STAGE || state.tool == EditorTool.WEEK
    Box(Modifier.fillMaxSize()) {
        if (editing) Box(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 16.dp)) {
            when (state.tool) {
                EditorTool.CHART -> EditorScreen(state)
                EditorTool.CHARACTER -> CharacterEditorScreen(state)
                EditorTool.STAGE -> StageEditorScreen(state)
                EditorTool.WEEK -> WeekEditorScreen(state)
                else -> Unit
            }
        } else Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Hero(state)
            when {
                state.tool == EditorTool.ONLINE -> OnlineScreen(state)
                state.tool == EditorTool.PLAYTEST -> PlayTestScreen(state)
                state.tab == Tab.MOD -> ModScreen(state)
                else -> FilesScreen(state)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!editing) VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionsButton()
        }
    }
}

@Composable
private fun Hero(state: AppState) {
    val tab = state.tab
    val shape = RoundedCornerShape(18.dp)
    val tool = state.tool
    if (tool != null) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.linearGradient(listOf(tool.accent.copy(alpha = 0.24f), Palette.Surface, Palette.Surface)))
                .border(1.5.dp, tool.accent.copy(alpha = 0.4f), shape)
                .padding(18.dp)
        ) {
            Text(tool.title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Text(when (tool) { EditorTool.ONLINE -> "Mods, updates and news"; EditorTool.PLAYTEST -> "Play and check songs"; else -> "V-Slice, with Psych and Codename export" }, style = MaterialTheme.typography.titleSmall, color = tool.accent)
            Spacer(Modifier.height(4.dp))
            Text(tool.hint, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(tab.accent.copy(alpha = 0.24f), Palette.Surface, Palette.Surface)))
            .border(1.5.dp, tab.accent.copy(alpha = 0.4f), shape)
            .padding(18.dp)
    ) {
        Text(tab.title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        Text(state.direction.label, style = MaterialTheme.typography.titleSmall, color = tab.accent)
        Spacer(Modifier.height(4.dp))
        Text(Workspace.hint(state.direction, tab), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    }
}






@Composable
internal fun StatusBanner(status: Status?) {
    androidx.compose.runtime.LaunchedEffect(status) {
        if (status is Status.Error) com.vconvertercrew.vconverter.online.ErrorReports.send("conversion", status.text)
    }
    status ?: return
    VStatusBanner(
        status.text,
        when (status) {
            is Status.Info -> VStatusKind.INFO
            is Status.Success -> VStatusKind.SUCCESS
            is Status.Error -> VStatusKind.ERROR
        }
    )
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModScreen(state: AppState) {
    val mod = state.mod()
    val accent = Tab.MOD.accent
    val busy = mod.busy
    Card {
        SectionTitle("${state.direction.from.title} mod", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Open mod folder…", accent, !busy) { Dialogs.chooseFolder("Open a ${state.direction.from.title} mod folder")?.let { state.openMod(it) } }
            Secondary("Open archive…", !busy) { Dialogs.openFile("Open a mod archive", setOf("zip", "rar", "7z"))?.let { state.openMod(it) } }
            if (mod.mod != null) TextButton(onClick = UiSound.wrap { mod.clear() }, enabled = !busy) { Text("Clear", color = Palette.Muted) }
        }
        val loaded = mod.mod
        if (loaded != null) {
            Text(loaded.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            if (loaded.root.isNotEmpty()) Text("Mod root: ${loaded.root}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                loaded.stats.forEach { (label, count) -> Chip("$label: $count", accent) }
            }
            if (loaded.songs.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    loaded.songs.take(40).forEach { Chip(it, Palette.Down) }
                    if (loaded.songs.size > 40) Chip("+${loaded.songs.size - 40} more", Palette.Muted)
                }
            }
        }
    }
    if (mod.mod != null) {
        Card {
            SectionTitle("Mod info", accent)
            Field(mod.title, { mod.title = it }, "Title", accent, !busy)
            Field(mod.description, { mod.description = it }, "Description", accent, !busy)
            Field(mod.author, { mod.author = it }, "Author", accent, !busy)
            Text(
                when (state.direction.to) {
                    Engine.PSYCH -> "Written to pack.json. The icon becomes pack.png."
                    Engine.CODENAME -> "Written to data/config/modpack.ini. The icon becomes icon.png."
                    Engine.VSLICE -> "Written to _polymod_meta.json. The icon becomes _polymod_icon.png."
                    Engine.NMV -> "Written to meta.json. The icon becomes images/pack.png."
                    Engine.FPSPLUS -> "Written to meta.json with the uid and api_version FPS Plus needs. The icon becomes icon.png."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted
            )
        }
    }
    Card(accent = if (mod.report != null) accent else null) {
        SectionTitle("Convert to ${state.direction.to.title}", accent)
        Text(
            "Install writes a \"${mod.folderName}\" folder into the ${state.direction.to.title} mods folder you pick. The .zip holds the same folder.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Install into mods folder…", accent, mod.mod != null && !busy) { state.convertMod(zip = false) }
            Secondary("Save as .zip…", mod.mod != null && !busy) { state.convertMod(zip = true) }
        }
        mod.progress?.let {
            ModScannerPanel(
                progress = it,
                colors = ScannerColors(accent, Palette.Background, Palette.SurfaceHigh, Palette.Text, Palette.Muted),
                onCancel = if (mod.cancel != null) mod::cancelConversion else null,
                cancelling = mod.cancelling
            )
        }
        StatusBanner(mod.status)
        val written = state.lastOutput
        if (mod.report != null && written != null && written.exists()) {
            Secondary("PlayTest the converted mod", !busy) {
                state.playtest.open(listOf(written))
                state.tool = EditorTool.PLAYTEST
            }
        }
        mod.report?.let { ReportView(it, accent) }
    }
}

@Composable
private fun FilesScreen(state: AppState) {
    val files = state.files()
    if (files.isCharts) {
        ChartsScreen(state, files)
        return
    }
    val accent = state.tab.accent
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FilesCard(state, files, accent)
        }
        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutputCard(state, files, accent)
        }
    }
}

@Composable
internal fun FilesCard(
    state: AppState,
    files: FileTabState,
    accent: Color,
    tags: @Composable (Int, PickedText) -> Unit = { _, item -> Chip(item.kind, accent) },
    extra: @Composable (Int, PickedText) -> Unit = { _, _ -> }
) {
    val busy = files.busy
    Card {
        SectionTitle("${state.direction.from.title} files", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Add files…", accent, !busy) { Dialogs.openFiles("Add ${state.direction.from.title} files").takeIf { it.isNotEmpty() }?.let { state.addFiles(it) } }
            Secondary("Add folder…", !busy) { Dialogs.chooseFolder("Add a folder")?.let { state.addFiles(listOf(it)) } }
            if (files.items.isNotEmpty()) TextButton(onClick = UiSound.wrap { files.clear() }, enabled = !busy) { Text("Clear", color = Palette.Muted) }
        }
        Text("Archives (.zip, .rar, .7z) are opened and only the matching files are kept.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        if (files.items.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text("Drop files here", style = MaterialTheme.typography.titleMedium, color = accent.copy(alpha = 0.8f)) }
        }
        files.items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                tags(index, item)
                extra(index, item)
                item.slot?.let { slot ->
                    Spacer(Modifier.width(8.dp))
                    SlotPicker(slot, !busy) { files.setSlot(index, it) }
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = UiSound.wrap { files.remove(index) }, enabled = !busy) { Text("✕", color = Palette.Muted) }
            }
        }
        Primary("Convert to ${state.direction.to.title}", accent, files.converter.canConvert(files.items) && !busy) { state.convertFiles() }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = accent, trackColor = Palette.SurfaceHigh)
        StatusBanner(files.status)
    }
}

@Composable
internal fun SlotPicker(slot: CharacterSlot, enabled: Boolean, onSelect: (CharacterSlot) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = UiSound.wrap { open = true }, enabled = enabled, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Palette.Outline)) {
            Text(slot.label + " ▾", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CharacterSlot.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                    UiSound.click()
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
internal fun OutputCard(state: AppState, files: FileTabState, accent: Color) {
    val output = files.output
    Card(accent = if (output != null) accent else null) {
        SectionTitle("Output", accent)
        if (output == null) {
            Text("Converted files show up here, ready to preview and save.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            return@Card
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Primary("Save into mod folder…", accent, !files.busy) { state.saveFiles(zip = false) }
            Secondary("Save as .zip…", !files.busy) { state.saveFiles(zip = true) }
            if (output.files.any { playableResult(it.relativePath) }) {
                Secondary("PlayTest the result", !files.busy) { state.playTestFiles(output.files.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) }, "${state.tab.label} result") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            output.files.forEach { file ->
                val active = file == files.preview
                Text(
                    file.relativePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) Palette.Ink else Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (active) accent else Palette.SurfaceHigh)
                        .soundClickable { files.preview = file }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        files.preview?.let { file ->
            val shown by produceState<PreviewText?>(null, file) {
                value = withContext(Dispatchers.Default) { if (file.bytes != null) null else CodePreviewText.prepare(file.content, 1500) }
            }
            val vertical = rememberScrollState()
            val horizontal = rememberScrollState()
            Box(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Ink)
                    .border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
            ) {
                SelectionContainer {
                    Text(
                        when {
                            file.bytes != null -> "(${file.bytes!!.size} bytes)"
                            shown == null -> "Loading…"
                            shown!!.truncated -> shown!!.text + "\n\n… ${shown!!.totalLines} lines in total; save the file to see all of it."
                            else -> shown!!.text
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Palette.Text,
                        softWrap = false,
                        modifier = Modifier.verticalScroll(vertical).horizontalScroll(horizontal).padding(12.dp)
                    )
                }
                VerticalScrollbar(rememberScrollbarAdapter(vertical), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        ReportView(output.report, accent)
    }
}

private val playableChart = Regex("(^|/)data/(songs/[^/]+/[^/]+-chart|[^/]+/[^/]+)\\.json$|(^|/)songs/[^/]+/charts/[^/]+\\.json$", RegexOption.IGNORE_CASE)
private val notCharts = Regex("(^|/)data/(characters|stages|levels|weeks|events|notes|config|dialogue)/", RegexOption.IGNORE_CASE)

internal fun playableResult(path: String): Boolean = playableChart.containsMatchIn(path) && !notCharts.containsMatchIn(path)
