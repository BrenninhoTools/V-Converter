package com.brenninhotools.vconverter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.characters.CharactersScreen
import com.brenninhotools.vconverter.ui.characters.CharactersViewModel
import com.brenninhotools.vconverter.ui.charts.ChartsScreen
import com.brenninhotools.vconverter.ui.charts.ChartsViewModel
import com.brenninhotools.vconverter.ui.components.AppLogo
import com.brenninhotools.vconverter.ui.scripts.ScriptsScreen
import com.brenninhotools.vconverter.ui.scripts.ScriptsViewModel
import com.brenninhotools.vconverter.ui.stages.StagesScreen
import com.brenninhotools.vconverter.ui.stages.StagesViewModel
import com.brenninhotools.vconverter.ui.mod.ModScreen
import com.brenninhotools.vconverter.ui.mod.ModViewModel
import com.brenninhotools.vconverter.ui.weeks.WeeksScreen
import com.brenninhotools.vconverter.ui.weeks.WeeksViewModel
import com.brenninhotools.vconverter.ui.theme.Funkin
import com.brenninhotools.vconverter.ui.generator.CharacterGenScreen
import com.brenninhotools.vconverter.ui.generator.CharacterGenViewModel
import com.brenninhotools.vconverter.ui.generator.LevelGenScreen
import com.brenninhotools.vconverter.ui.generator.LevelGenViewModel
import com.brenninhotools.vconverter.ui.generator.ModGenScreen
import com.brenninhotools.vconverter.ui.generator.ModGenViewModel
import com.brenninhotools.vconverter.ui.generator.ScriptGenScreen
import com.brenninhotools.vconverter.ui.generator.ScriptGenViewModel
import com.brenninhotools.vconverter.ui.generator.SongGenScreen
import com.brenninhotools.vconverter.ui.generator.SongGenViewModel
import com.brenninhotools.vconverter.ui.generator.StageGenScreen
import com.brenninhotools.vconverter.ui.generator.StageGenViewModel
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import com.brenninhotools.vconverter.ui.reverse.ReverseChartsViewModel
import com.brenninhotools.vconverter.ui.reverse.ReverseCharactersViewModel
import com.brenninhotools.vconverter.ui.reverse.ReverseModScreen
import com.brenninhotools.vconverter.ui.reverse.ReverseModViewModel
import com.brenninhotools.vconverter.ui.reverse.ReverseScreen
import com.brenninhotools.vconverter.ui.reverse.ReverseScriptsViewModel
import com.brenninhotools.vconverter.ui.reverse.ReverseStagesViewModel
import com.brenninhotools.vconverter.ui.reverse.ReverseWeeksViewModel

interface AppTab {
    val label: String
    @get:DrawableRes val icon: Int
    val accent: Color
    val title: String
    val description: String
}

enum class Section(val label: String, @DrawableRes val icon: Int, val accent: Color) {
    CONVERTER("Converter", R.drawable.ic_swap, Funkin.Down),
    GENERATOR("Generator", R.drawable.ic_magic, Funkin.Warning)
}

enum class Tab(
    override val label: String,
    @DrawableRes override val icon: Int,
    override val accent: Color,
    override val title: String,
    override val description: String,
    val reverseDescription: String
) : AppTab {
    MOD("Mod", R.drawable.ic_tab_mod, Funkin.Pink, "Whole mod", "A full Psych mod from a folder, .zip, .rar or .7z.", "A full V-Slice mod from a folder, .zip, .rar or .7z."),
    CHARTS("Charts", R.drawable.ic_tab_chart, Funkin.Down, "Charts", "Psych charts and events.json into one V-Slice song.", "V-Slice metadata + chart into Psych difficulty charts."),
    CHARACTERS("Chars", R.drawable.ic_tab_character, Funkin.Left, "Characters", "Character JSON + spritesheet XML, pixel-exact offsets.", "V-Slice characters back to Psych, exact with the XML."),
    STAGES("Stages", R.drawable.ic_tab_stage, Funkin.Up, "Stages", "Stage layouts, cameras and editor props.", "V-Slice stages into Psych stage editor files."),
    WEEKS("Weeks", R.drawable.ic_tab_week, Funkin.Right, "Weeks", "Story weeks into V-Slice levels with menu props.", "V-Slice levels into Psych weeks and menu characters."),
    SCRIPTS("Scripts", R.drawable.ic_tab_script, Funkin.Lavender, "Scripts", "Lua and HScript into V-Slice modules.", ".hxc scripts into Psych HScript (.hx).")
}

enum class GenTab(
    override val label: String,
    @DrawableRes override val icon: Int,
    override val accent: Color,
    override val title: String,
    override val description: String
) : AppTab {
    MOD("Mod", R.drawable.ic_tab_mod, Funkin.Pink, "New mod", "A ready V-Slice mod folder with metadata and icon."),
    SONG("Song", R.drawable.ic_tab_chart, Funkin.Down, "New song", "Metadata, empty charts and audio for the Chart Editor."),
    CHARACTER("Char", R.drawable.ic_tab_character, Funkin.Left, "New character", "A character JSON from a Sparrow spritesheet."),
    STAGE("Stage", R.drawable.ic_tab_stage, Funkin.Up, "New stage", "A stage from your images, with base game positions."),
    LEVEL("Level", R.drawable.ic_tab_week, Funkin.Right, "New level", "A story mode week with songs and menu characters."),
    SCRIPT("Script", R.drawable.ic_tab_script, Funkin.Lavender, "New script", "Ready-to-edit .hxc classes for V-Slice.")
}

@Composable
fun MainScreen() {
    val modFolder: ModFolderViewModel = viewModel()
    val charts: ChartsViewModel = viewModel()
    val characters: CharactersViewModel = viewModel()
    val stages: StagesViewModel = viewModel()
    val weeks: WeeksViewModel = viewModel()
    val mod: ModViewModel = viewModel()
    val scripts: ScriptsViewModel = viewModel()
    val genMod: ModGenViewModel = viewModel()
    val genSong: SongGenViewModel = viewModel()
    val genCharacter: CharacterGenViewModel = viewModel()
    val genStage: StageGenViewModel = viewModel()
    val genLevel: LevelGenViewModel = viewModel()
    val genScript: ScriptGenViewModel = viewModel()
    val revMod: ReverseModViewModel = viewModel()
    val revCharts: ReverseChartsViewModel = viewModel()
    val revCharacters: ReverseCharactersViewModel = viewModel()
    val revStages: ReverseStagesViewModel = viewModel()
    val revWeeks: ReverseWeeksViewModel = viewModel()
    val revScripts: ReverseScriptsViewModel = viewModel()
    var reverse by rememberSaveable { mutableStateOf(false) }

    var section by rememberSaveable { mutableStateOf(Section.CONVERTER) }
    var converterTab by rememberSaveable { mutableStateOf(Tab.MOD) }
    var generatorTab by rememberSaveable { mutableStateOf(GenTab.MOD) }
    var pendingSave by remember { mutableStateOf<AppTab?>(null) }
    val tab: AppTab = if (section == Section.CONVERTER) converterTab else generatorTab

    fun converterFor(target: AppTab): BaseConverterViewModel = if (reverse && target is Tab) when (target) {
        Tab.MOD -> revMod
        Tab.CHARTS -> revCharts
        Tab.CHARACTERS -> revCharacters
        Tab.STAGES -> revStages
        Tab.WEEKS -> revWeeks
        Tab.SCRIPTS -> revScripts
    } else when (target) {
        Tab.MOD -> mod
        Tab.CHARTS -> charts
        Tab.CHARACTERS -> characters
        Tab.STAGES -> stages
        Tab.WEEKS -> weeks
        Tab.SCRIPTS -> scripts
        GenTab.MOD -> genMod
        GenTab.SONG -> genSong
        GenTab.CHARACTER -> genCharacter
        GenTab.STAGE -> genStage
        GenTab.LEVEL -> genLevel
        GenTab.SCRIPT -> genScript
        else -> mod
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = pendingSave
        pendingSave = null
        if (uri != null) {
            modFolder.select(uri)
            if (target != null) converterFor(target).save(uri)
        }
    }

    val save: () -> Unit = {
        val folder = modFolder.folder
        if (folder != null) {
            converterFor(tab).save(folder)
        } else {
            pendingSave = tab
            folderPicker.launch(null)
        }
    }
    val folderName = modFolder.folder?.let { modFolder.folderName }

    Scaffold(
        containerColor = Funkin.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Header(
                folderName = folderName,
                onPickFolder = { folderPicker.launch(null) },
                section = section,
                onSection = { section = it },
                reverse = reverse,
                onReverse = { reverse = it }
            )
        },
        bottomBar = {
            FunkinNavBar(if (section == Section.CONVERTER) Tab.entries else GenTab.entries, tab) { selected ->
                when (selected) {
                    is Tab -> converterTab = selected
                    is GenTab -> generatorTab = selected
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab to (reverse && section == Section.CONVERTER),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            label = "tab"
        ) { (current, isReverse) ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                TabHero(current, isReverse)
                Spacer(Modifier.size(16.dp))
                if (isReverse) when (current) {
                    Tab.MOD -> ReverseModScreen(revMod, folderName, save)
                    Tab.CHARTS -> ReverseScreen(revCharts, Funkin.Down, "Songs", "Add V-Slice songs", "Pick a song's <song>-metadata.json and <song>-chart.json (and -erect variants). Variations become their own Psych songs. Archives work too.", R.drawable.ic_tab_chart, folderName, save)
                    Tab.CHARACTERS -> ReverseScreen(revCharacters, Funkin.Left, "Characters", "Add V-Slice characters", "Pick data/characters/*.json with their spritesheet .xml (images/…) for exact positions. Archives work too.", R.drawable.ic_tab_character, folderName, save)
                    Tab.STAGES -> ReverseScreen(revStages, Funkin.Up, "Stages", "Add V-Slice stages", "Pick data/stages/*.json. Props, layers, scroll and animations become Psych stage objects.", R.drawable.ic_tab_stage, folderName, save)
                    Tab.WEEKS -> ReverseScreen(revWeeks, Funkin.Right, "Levels", "Add V-Slice levels", "Pick data/levels/*.json, plus the songs' -metadata.json files for names, icons and difficulties.", R.drawable.ic_tab_week, folderName, save)
                    Tab.SCRIPTS -> ReverseScreen(revScripts, Funkin.Lavender, "Scripts", "Add .hxc scripts", "Pick V-Slice .hxc scripts. Modules, songs, stages, characters, song events and note kinds become Psych HScript files in the right folders.", R.drawable.ic_tab_script, folderName, save)
                    else -> Unit
                } else when (current) {
                    Tab.MOD -> ModScreen(mod, folderName, save)
                    Tab.CHARTS -> ChartsScreen(charts, folderName, save)
                    Tab.CHARACTERS -> CharactersScreen(characters, folderName, save)
                    Tab.STAGES -> StagesScreen(stages, folderName, save)
                    Tab.WEEKS -> WeeksScreen(weeks, folderName, save)
                    Tab.SCRIPTS -> ScriptsScreen(scripts, folderName, save)
                    GenTab.MOD -> ModGenScreen(genMod, folderName, save)
                    GenTab.SONG -> SongGenScreen(genSong, folderName, save)
                    GenTab.CHARACTER -> CharacterGenScreen(genCharacter, folderName, save)
                    GenTab.STAGE -> StageGenScreen(genStage, folderName, save)
                    GenTab.LEVEL -> LevelGenScreen(genLevel, folderName, save)
                    GenTab.SCRIPT -> ScriptGenScreen(genScript, folderName, save)
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Header(
    folderName: String?,
    onPickFolder: () -> Unit,
    section: Section,
    onSection: (Section) -> Unit,
    reverse: Boolean,
    onReverse: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Funkin.Background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("V-CONVERTER", style = MaterialTheme.typography.headlineSmall, color = Funkin.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (section == Section.GENERATOR) {
                        Text("New V-Slice content", style = MaterialTheme.typography.labelMedium, color = Funkin.Warning)
                    } else {
                        Text(if (reverse) "V-Slice" else "Psych Engine", style = MaterialTheme.typography.labelMedium, color = if (reverse) Funkin.Down else Funkin.Left)
                        Text("  →  ", style = MaterialTheme.typography.labelMedium, color = Funkin.Muted)
                        Text(if (reverse) "Psych Engine" else "V-Slice", style = MaterialTheme.typography.labelMedium, color = if (reverse) Funkin.Left else Funkin.Down)
                    }
                }
            }
        }
        SectionSwitch(section, onSection)
        AnimatedVisibility(visible = section == Section.CONVERTER) {
            DirectionSwitch(reverse, onReverse)
        }
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Funkin.Surface)
                .border(1.5.dp, if (folderName != null) Funkin.Up.copy(alpha = 0.5f) else Funkin.Outline, shape)
                .clickable(onClick = onPickFolder)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = if (folderName != null) Funkin.Up else Funkin.Muted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("MOD FOLDER", style = MaterialTheme.typography.labelSmall, color = Funkin.Muted)
                Text(
                    folderName ?: "Not selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (folderName != null) Funkin.Text else Funkin.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onPickFolder) {
                Text(if (folderName != null) "CHANGE" else "CHOOSE", style = MaterialTheme.typography.labelMedium, color = Funkin.Down)
            }
        }
    }
}

@Composable
private fun TabHero(tab: AppTab, reverse: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tab.accent.copy(alpha = 0.22f), tab.accent.copy(alpha = 0.04f))))
            .border(1.5.dp, tab.accent.copy(alpha = 0.35f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tab.accent)
                .border(3.dp, Funkin.Ink, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(tab.icon), contentDescription = null, tint = Funkin.Ink, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tab.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Funkin.Text)
            Text(if (reverse && tab is Tab) tab.reverseDescription else tab.description, style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
        }
    }
}

@Composable
private fun FunkinNavBar(tabs: List<AppTab>, selected: AppTab, onSelect: (AppTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Funkin.Surface)
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Brush.horizontalGradient(tabs.map { it.accent }))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            tabs.forEach { item ->
                val active = item == selected
                val fill by animateColorAsState(if (active) item.accent else Color.Transparent, label = "fill")
                val tint by animateColorAsState(if (active) Funkin.Ink else Funkin.Muted, label = "tint")
                val lift by animateDpAsState(if (active) 0.dp else 3.dp, label = "lift")
                val shape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(item) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = lift)
                            .size(width = 46.dp, height = 32.dp)
                            .clip(shape)
                            .background(fill)
                            .then(if (active) Modifier.border(2.5.dp, Funkin.Ink, shape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) item.accent else Funkin.Muted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionSwitch(selected: Section, onSelect: (Section) -> Unit) {
    val outer = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(Funkin.Ink)
            .border(2.dp, Funkin.Outline, outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Section.entries.forEach { item ->
            val active = item == selected
            val fill by animateColorAsState(if (active) item.accent else Color.Transparent, label = "section")
            val content by animateColorAsState(if (active) Funkin.Ink else Funkin.Muted, label = "sectionText")
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(fill)
                    .clickable { onSelect(item) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(item.icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

@Composable
private fun DirectionSwitch(reverse: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Funkin.Surface)
            .border(1.5.dp, Funkin.Outline, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(false to ("Psych" to "V-Slice"), true to ("V-Slice" to "Psych")).forEach { (value, names) ->
            val active = value == reverse
            val fill by animateColorAsState(if (active) Funkin.SurfaceHigh else Color.Transparent, label = "direction")
            val inner = RoundedCornerShape(11.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(inner)
                    .background(fill)
                    .then(if (active) Modifier.border(1.5.dp, Funkin.Down.copy(alpha = 0.7f), inner) else Modifier)
                    .clickable { onChange(value) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (from, to) = names
                val fromColor = if (from == "Psych") Funkin.Left else Funkin.Down
                val toColor = if (to == "Psych") Funkin.Left else Funkin.Down
                Text(from.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (active) fromColor else Funkin.Muted)
                Icon(
                    painterResource(R.drawable.ic_swap),
                    contentDescription = null,
                    tint = if (active) Funkin.Text else Funkin.Muted,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(14.dp)
                )
                Text(to.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (active) toColor else Funkin.Muted)
            }
        }
    }
}
