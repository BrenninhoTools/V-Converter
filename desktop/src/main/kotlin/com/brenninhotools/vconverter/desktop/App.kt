package com.brenninhotools.vconverter.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.editorview.typingField
import com.brenninhotools.vconverter.overlay.CodePreviewText
import com.brenninhotools.vconverter.overlay.FpsCounter
import com.brenninhotools.vconverter.overlay.PreviewText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.core.Report

private val logo = Resources.image("icon.png")

@Composable
fun App(state: AppState) {
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
            logo?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("V-CONVERTER", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text("for Windows", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
        EnginePicker("FROM", state.direction.from, Engine.entries, Engine.entries) { state.selectSource(it) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val canSwap = state.direction.swapped() != null
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(state.direction.from.color, state.direction.to.color)))
                    .border(2.dp, Palette.Ink, CircleShape)
                    .clickable(enabled = canSwap) { state.swap() },
                contentAlignment = Alignment.Center
            ) {
                Text(if (canSwap) "⇅" else "↓", color = Palette.Ink, fontSize = 20.sp)
            }
        }
        EnginePicker("TO", state.direction.to, Engine.entries, Direction.targets(state.direction.from)) { state.selectTarget(it) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { tab ->
                val active = !state.editorOpen && tab == state.tab
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (active) tab.accent else Color.Transparent)
                        .clickable { state.tab = tab; state.editorOpen = false }
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
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (state.editorOpen) Palette.Down else Palette.SurfaceHigh)
                .clickable { state.editorOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.editorOpen) Palette.Ink else Palette.Down))
            Spacer(Modifier.width(12.dp))
            Text("CHART EDITOR", style = MaterialTheme.typography.labelLarge, color = if (state.editorOpen) Palette.Ink else Palette.Text)
        }
        Spacer(Modifier.weight(1f))
        state.lastOutput?.let { file ->
            OutlinedButton(onClick = { Dialogs.reveal(file) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Palette.Outline)) {
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
                    .clickable(enabled = usable) { onSelect(engine) }
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
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Hero(state)
            when {
                state.editorOpen -> EditorScreen(state)
                state.tab == Tab.MOD -> ModScreen(state)
                else -> FilesScreen(state)
            }
            Spacer(Modifier.height(8.dp))
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (AppSettings.fpsCounter) {
                FpsCounter()
                Spacer(Modifier.width(10.dp))
            }
            OptionsButton()
        }
    }
}

@Composable
private fun Hero(state: AppState) {
    val tab = state.tab
    val shape = RoundedCornerShape(18.dp)
    if (state.editorOpen) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.linearGradient(listOf(Palette.Down.copy(alpha = 0.24f), Palette.Surface, Palette.Surface)))
                .border(1.5.dp, Palette.Down.copy(alpha = 0.4f), shape)
                .padding(18.dp)
        ) {
            Text("CHART EDITOR", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Text("V-Slice, with Psych and Codename export", style = MaterialTheme.typography.titleSmall, color = Palette.Down)
            Spacer(Modifier.height(4.dp))
            Text("Start a song or open any chart, then click the lanes to place notes. Space plays, the mouse wheel scrolls by the snap, Ctrl+Z / Ctrl+Y undo and redo, Q / E change a hold, Delete removes the selection. The left lane holds events.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
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
internal fun Card(accent: Color? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Palette.Surface)
            .border(1.5.dp, accent?.copy(alpha = 0.6f) ?: Palette.Outline, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
internal fun SectionTitle(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    }
}

@Composable
internal fun Primary(text: String, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Palette.Ink, disabledContainerColor = Palette.SurfaceHigh, disabledContentColor = Palette.Muted)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun Secondary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, Palette.Outline)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Palette.Text else Palette.Muted)
    }
}

@Composable
internal fun Chip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1) }
}

@Composable
internal fun StatusBanner(status: Status?) {
    status ?: return
    val color = when (status) {
        is Status.Info -> Palette.Down
        is Status.Success -> Palette.Up
        is Status.Error -> Palette.Right
    }
    SelectionContainer {
        Text(
            status.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Text,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp)
        )
    }
}

@Composable
private fun Progress(done: Int, total: Int, label: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = accent,
                trackColor = Palette.SurfaceHigh
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), color = accent, trackColor = Palette.SurfaceHigh)
        }
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (total > 0) Text("$done/$total", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportView(report: Report, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (report.stats.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.stats.forEach { stat ->
                    Column(
                        Modifier.widthIn(min = 110.dp).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(stat.value, style = MaterialTheme.typography.titleMedium, color = accent)
                        Text(stat.label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    }
                }
            }
        }
        if (report.warnings.isNotEmpty()) {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Warning.copy(alpha = 0.10f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("WARNINGS (${report.warnings.size})", style = MaterialTheme.typography.labelMedium, color = Palette.Warning)
                    report.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Palette.Text) }
                }
            }
        }
        report.sections.forEach { section ->
            var open by remember(section) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh)) {
                Row(
                    Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (open) "▾" else "▸", color = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(section.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                    Text("${section.lines.size}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
                if (open) {
                    SelectionContainer {
                        Column(Modifier.padding(start = 30.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            section.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Field(value: String, onChange: (String) -> Unit, label: String, accent: Color, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().typingField(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent, cursorColor = accent)
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
            if (mod.mod != null) TextButton(onClick = { mod.clear() }, enabled = !busy) { Text("Clear", color = Palette.Muted) }
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
        mod.progress?.let { Progress(it.done, it.total, it.label, accent) }
        StatusBanner(mod.status)
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
            if (files.items.isNotEmpty()) TextButton(onClick = { files.clear() }, enabled = !busy) { Text("Clear", color = Palette.Muted) }
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
                TextButton(onClick = { files.remove(index) }, enabled = !busy) { Text("✕", color = Palette.Muted) }
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
        OutlinedButton(onClick = { open = true }, enabled = enabled, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Palette.Outline)) {
            Text(slot.label + " ▾", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CharacterSlot.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
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
                        .clickable { files.preview = file }
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
