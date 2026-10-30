package com.brenninhotools.vconverter.ui

import com.brenninhotools.vconverter.overlay.soundClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
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
import com.brenninhotools.vconverter.overlay.FpsCounter
import com.brenninhotools.vconverter.ui.characters.CharactersScreen
import com.brenninhotools.vconverter.ui.characters.CharactersViewModel
import com.brenninhotools.vconverter.ui.charts.ChartsScreen
import com.brenninhotools.vconverter.ui.charts.CodenameChartsViewModel
import com.brenninhotools.vconverter.ui.charts.CodenameToPsychChartsViewModel
import com.brenninhotools.vconverter.ui.charts.PsychToCodenameChartsViewModel
import com.brenninhotools.vconverter.ui.charts.PsychToVSliceChartsViewModel
import com.brenninhotools.vconverter.ui.charts.ReverseChartsViewModel
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
import com.brenninhotools.vconverter.ui.editor.CharacterEditorScreen
import com.brenninhotools.vconverter.ui.editor.CharacterEditorViewModel
import com.brenninhotools.vconverter.ui.generator.ModGenScreen
import com.brenninhotools.vconverter.ui.generator.ModGenViewModel
import com.brenninhotools.vconverter.ui.editor.ChartEditorScreen
import com.brenninhotools.vconverter.ui.editor.ChartEditorViewModel
import com.brenninhotools.vconverter.ui.generator.ScriptGenScreen
import com.brenninhotools.vconverter.ui.generator.ScriptGenViewModel
import com.brenninhotools.vconverter.ui.editor.StageEditorScreen
import com.brenninhotools.vconverter.ui.editor.StageEditorViewModel
import com.brenninhotools.vconverter.ui.editor.WeekEditorScreen
import com.brenninhotools.vconverter.ui.editor.WeekEditorViewModel
import com.brenninhotools.vconverter.ui.online.OnlineModsScreen
import com.brenninhotools.vconverter.ui.online.OnlineNewsScreen
import com.brenninhotools.vconverter.ui.online.OnlineRoomsScreen
import com.brenninhotools.vconverter.ui.online.OnlineShareScreen
import com.brenninhotools.vconverter.ui.online.OnlineViewModel
import com.brenninhotools.vconverter.ui.playtest.LocalPlayTest
import com.brenninhotools.vconverter.ui.playtest.PlayTestLauncher
import com.brenninhotools.vconverter.ui.playtest.PlayTestPlayScreen
import com.brenninhotools.vconverter.ui.playtest.PlayTestReportScreen
import com.brenninhotools.vconverter.ui.playtest.PlayTestViewModel
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import com.brenninhotools.vconverter.ui.codename.CodenameCharactersViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameModScreen
import com.brenninhotools.vconverter.ui.codename.CodenameModViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameScriptsViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameStagesViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameWeeksViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameToPsychModViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameToPsychCharactersViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameToPsychStagesViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameToPsychWeeksViewModel
import com.brenninhotools.vconverter.ui.codename.CodenameToPsychScriptsViewModel
import com.brenninhotools.vconverter.ui.codename.PsychToCodenameCharactersViewModel
import com.brenninhotools.vconverter.ui.codename.PsychToCodenameStagesViewModel
import com.brenninhotools.vconverter.ui.codename.PsychToCodenameWeeksViewModel
import com.brenninhotools.vconverter.ui.codename.PsychToCodenameScriptsViewModel
import com.brenninhotools.vconverter.ui.mod.PsychToCodenameModViewModel
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
    GENERATOR("Generator", R.drawable.ic_magic, Funkin.Warning),
    ONLINE("Online", R.drawable.ic_share, Funkin.Pink),
    PLAYTEST("Test", R.drawable.ic_tab_chart, Funkin.Up)
}

enum class PlayTab(
    override val label: String,
    @DrawableRes override val icon: Int,
    override val accent: Color,
    override val title: String,
    override val description: String
) : AppTab {
    PLAY("Play", R.drawable.ic_tab_chart, Funkin.Up, "PlayTest", "Play any song of a mod, a converter's result or the Chart Editor's chart, with its stage and characters, and see what is broken."),
    REPORT("Report", R.drawable.ic_warning, Funkin.Warning, "PlayTest report", "Problems found in the chart, characters, stage, audio and conversion of the loaded song.")
}

enum class OnlineTab(
    override val label: String,
    @DrawableRes override val icon: Int,
    override val accent: Color,
    override val title: String,
    override val description: String
) : AppTab {
    MODS("Mods", R.drawable.ic_download, Funkin.Pink, "Mod Gallery", "Find mods shared on the V-Converter server and download them."),
    SHARE("Share", R.drawable.ic_share, Funkin.Up, "Share a mod", "Upload a mod .zip to the server's gallery so others can download it."),
    ROOMS("Rooms", R.drawable.ic_tab_chart, Funkin.Left, "Public rooms", "Join a Multieditor room someone listed publicly and edit together."),
    NEWS("News", R.drawable.ic_warning, Funkin.Down, "News", "New versions of V-Converter and announcements from the server.")
}

enum class Tab(
    override val label: String,
    @DrawableRes override val icon: Int,
    override val accent: Color,
    override val title: String,
    override val description: String,
    val reverseDescription: String,
    val codenameDescription: String
) : AppTab {
    MOD("Mod", R.drawable.ic_tab_mod, Funkin.Pink, "Whole mod", "A full Psych mod from a folder, .zip, .rar or .7z.", "A full V-Slice mod from a folder, .zip, .rar or .7z.", "A full Codename mod from a folder, .zip, .rar or .7z."),
    CHARTS("Charts", R.drawable.ic_tab_chart, Funkin.Down, "Charts", "Psych charts and events.json into one V-Slice song.", "V-Slice metadata + chart into Psych difficulty charts.", "Codename strumline charts, meta.json and events into V-Slice songs."),
    CHARACTERS("Chars", R.drawable.ic_tab_character, Funkin.Left, "Characters", "Character JSON + spritesheet XML, pixel-exact offsets.", "V-Slice characters back to Psych, exact with the XML.", "Character XML + spritesheet XML, scaled offsets and game over merged."),
    STAGES("Stages", R.drawable.ic_tab_stage, Funkin.Up, "Stages", "Stage layouts, cameras and editor props.", "V-Slice stages into Psych stage editor files.", "Stage XML sprites, boxes and character spots into V-Slice props."),
    WEEKS("Weeks", R.drawable.ic_tab_week, Funkin.Right, "Weeks", "Story weeks into V-Slice levels with menu props.", "V-Slice levels into Psych weeks and menu characters.", "Week XMLs and menu characters into V-Slice levels."),
    SCRIPTS("Scripts", R.drawable.ic_tab_script, Funkin.Lavender, "Scripts", "Lua and HScript into V-Slice modules.", ".hxc scripts into Psych HScript (.hx).", "Codename HScript (.hx) into V-Slice modules and song events.");

    fun describe(direction: Direction): String = when (direction) {
        Direction.PSYCH_TO_VSLICE -> description
        Direction.VSLICE_TO_PSYCH -> reverseDescription
        Direction.CODENAME_TO_VSLICE -> codenameDescription
        Direction.CODENAME_TO_PSYCH -> when (this) {
            MOD -> "A full Codename mod into a Psych mod: charts, scripts, events and audio."
            CHARTS -> "Codename strumline charts and events into Psych difficulty charts."
            CHARACTERS -> "Character XML into Psych JSON with unscaled offsets."
            STAGES -> "Stage XML into Psych stage JSON plus a stage script."
            WEEKS -> "Week XMLs into Psych weeks, weekList.txt and menu characters."
            SCRIPTS -> "Codename HScript into Psych HScript (.hx) with the right callbacks."
        }
        Direction.PSYCH_TO_CODENAME -> when (this) {
            MOD -> "A full Psych mod into a Codename mod: songs, Lua/HScript, events and audio."
            CHARTS -> "Psych charts and events.json into Codename strumlines and meta.json."
            CHARACTERS -> "Character JSON into Codename XML with scaled offsets."
            STAGES -> "Stage JSON (and stage Lua sprites) into Codename stage XML."
            WEEKS -> "Psych weeks into week XMLs, weeks.txt and menu characters."
            SCRIPTS -> "Lua and HScript into Codename HScript with Psych helpers."
        }
    }
}

enum class Engine(val label: String, val title: String, val detail: String, val color: Color) {
    PSYCH("PSYCH", "Psych Engine", "Lua · HScript · JSON", Funkin.Left),
    CODENAME("CODENAME", "Codename Engine", "XML · HScript", Funkin.Orange),
    VSLICE("V-SLICE", "V-Slice", "Polymod · .hxc", Funkin.Down)
}

enum class Direction(val from: Engine, val to: Engine) {
    PSYCH_TO_VSLICE(Engine.PSYCH, Engine.VSLICE),
    VSLICE_TO_PSYCH(Engine.VSLICE, Engine.PSYCH),
    CODENAME_TO_VSLICE(Engine.CODENAME, Engine.VSLICE),
    CODENAME_TO_PSYCH(Engine.CODENAME, Engine.PSYCH),
    PSYCH_TO_CODENAME(Engine.PSYCH, Engine.CODENAME);

    val label: String get() = "${from.title} to ${to.title}"
    val canSwap: Boolean get() = entries.any { it.from == to && it.to == from }
    fun swapped(): Direction = entries.firstOrNull { it.from == to && it.to == from } ?: this
    fun nextTarget(): Direction {
        val options = entries.filter { it.from == from }
        return options[(options.indexOf(this) + 1) % options.size]
    }
    fun retarget(engine: Engine): Direction = entries.firstOrNull { it.from == from && it.to == engine } ?: this

    companion object {
        fun from(engine: Engine): Direction = when (engine) {
            Engine.PSYCH -> PSYCH_TO_VSLICE
            Engine.VSLICE -> VSLICE_TO_PSYCH
            Engine.CODENAME -> CODENAME_TO_VSLICE
        }

        fun targets(engine: Engine): List<Engine> = entries.filter { it.from == engine }.map { it.to }
    }
}

enum class GenTab(
    override val label: String,
    @DrawableRes override val icon: Int,
    override val accent: Color,
    override val title: String,
    override val description: String
) : AppTab {
    MOD("Mod", R.drawable.ic_tab_mod, Funkin.Pink, "New mod", "A ready V-Slice mod folder with metadata and icon."),
    SONG("Chart", R.drawable.ic_tab_chart, Funkin.Down, "Chart Editor", "Start a song or open any chart, then place notes, events and BPM changes to the music."),
    CHARACTER("Char", R.drawable.ic_tab_character, Funkin.Left, "Character Editor", "Open any character or a spritesheet, then preview the animations and fix offsets, camera and scale."),
    STAGE("Stage", R.drawable.ic_tab_stage, Funkin.Up, "Stage Editor", "Open any stage or start from your images, then place props and characters with the game camera in view."),
    WEEK("Week", R.drawable.ic_tab_week, Funkin.Right, "Week Editor", "Open any week or start a new one, then lay out the story menu, its songs and how it unlocks."),
    SCRIPT("Script", R.drawable.ic_tab_script, Funkin.Lavender, "New script", "Ready-to-edit .hxc classes for V-Slice.")
}

@Composable
fun MainScreen() {
    val modFolder: ModFolderViewModel = viewModel()
    val charts: PsychToVSliceChartsViewModel = viewModel()
    val characters: CharactersViewModel = viewModel()
    val stages: StagesViewModel = viewModel()
    val weeks: WeeksViewModel = viewModel()
    val mod: ModViewModel = viewModel()
    val scripts: ScriptsViewModel = viewModel()
    val genMod: ModGenViewModel = viewModel()
    val genSong: ChartEditorViewModel = viewModel()
    val genCharacter: CharacterEditorViewModel = viewModel()
    val genStage: StageEditorViewModel = viewModel()
    val genWeek: WeekEditorViewModel = viewModel()
    val genScript: ScriptGenViewModel = viewModel()
    val online: OnlineViewModel = viewModel()
    val playtest: PlayTestViewModel = viewModel()
    val revMod: ReverseModViewModel = viewModel()
    val revCharts: ReverseChartsViewModel = viewModel()
    val revCharacters: ReverseCharactersViewModel = viewModel()
    val revStages: ReverseStagesViewModel = viewModel()
    val revWeeks: ReverseWeeksViewModel = viewModel()
    val revScripts: ReverseScriptsViewModel = viewModel()
    val cnMod: CodenameModViewModel = viewModel()
    val cnCharts: CodenameChartsViewModel = viewModel()
    val cnCharacters: CodenameCharactersViewModel = viewModel()
    val cnStages: CodenameStagesViewModel = viewModel()
    val cnWeeks: CodenameWeeksViewModel = viewModel()
    val cnScripts: CodenameScriptsViewModel = viewModel()
    val c2pMod: CodenameToPsychModViewModel = viewModel()
    val c2pCharts: CodenameToPsychChartsViewModel = viewModel()
    val c2pCharacters: CodenameToPsychCharactersViewModel = viewModel()
    val c2pStages: CodenameToPsychStagesViewModel = viewModel()
    val c2pWeeks: CodenameToPsychWeeksViewModel = viewModel()
    val c2pScripts: CodenameToPsychScriptsViewModel = viewModel()
    val p2cMod: PsychToCodenameModViewModel = viewModel()
    val p2cCharts: PsychToCodenameChartsViewModel = viewModel()
    val p2cCharacters: PsychToCodenameCharactersViewModel = viewModel()
    val p2cStages: PsychToCodenameStagesViewModel = viewModel()
    val p2cWeeks: PsychToCodenameWeeksViewModel = viewModel()
    val p2cScripts: PsychToCodenameScriptsViewModel = viewModel()
    var direction by rememberSaveable { mutableStateOf(Direction.PSYCH_TO_VSLICE) }

    var section by rememberSaveable { mutableStateOf(Section.CONVERTER) }
    var converterTab by rememberSaveable { mutableStateOf(Tab.MOD) }
    var generatorTab by rememberSaveable { mutableStateOf(GenTab.MOD) }
    var onlineTab by rememberSaveable { mutableStateOf(OnlineTab.MODS) }
    var playTab by rememberSaveable { mutableStateOf(PlayTab.PLAY) }
    var pendingSave by remember { mutableStateOf<AppTab?>(null) }
    val tab: AppTab = when (section) {
        Section.CONVERTER -> converterTab
        Section.GENERATOR -> generatorTab
        Section.ONLINE -> onlineTab
        Section.PLAYTEST -> playTab
    }

    fun converterFor(target: AppTab): BaseConverterViewModel = if (direction == Direction.VSLICE_TO_PSYCH && target is Tab) when (target) {
        Tab.MOD -> revMod
        Tab.CHARTS -> revCharts
        Tab.CHARACTERS -> revCharacters
        Tab.STAGES -> revStages
        Tab.WEEKS -> revWeeks
        Tab.SCRIPTS -> revScripts
    } else if (direction == Direction.CODENAME_TO_VSLICE && target is Tab) when (target) {
        Tab.MOD -> cnMod
        Tab.CHARTS -> cnCharts
        Tab.CHARACTERS -> cnCharacters
        Tab.STAGES -> cnStages
        Tab.WEEKS -> cnWeeks
        Tab.SCRIPTS -> cnScripts
    } else if (direction == Direction.CODENAME_TO_PSYCH && target is Tab) when (target) {
        Tab.MOD -> c2pMod
        Tab.CHARTS -> c2pCharts
        Tab.CHARACTERS -> c2pCharacters
        Tab.STAGES -> c2pStages
        Tab.WEEKS -> c2pWeeks
        Tab.SCRIPTS -> c2pScripts
    } else if (direction == Direction.PSYCH_TO_CODENAME && target is Tab) when (target) {
        Tab.MOD -> p2cMod
        Tab.CHARTS -> p2cCharts
        Tab.CHARACTERS -> p2cCharacters
        Tab.STAGES -> p2cStages
        Tab.WEEKS -> p2cWeeks
        Tab.SCRIPTS -> p2cScripts
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
        GenTab.WEEK -> genWeek
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

    val playLauncher = remember(playtest) {
        object : PlayTestLauncher {
            override fun files(files: List<Pair<String, ByteArray>>, label: String) {
                playtest.state.open(files, label)
                section = Section.PLAYTEST
                playTab = PlayTab.PLAY
            }

            override fun chart(document: com.brenninhotools.vconverter.editor.ChartDocument, audio: List<com.brenninhotools.vconverter.editor.EditorAudio>) {
                genSong.pause()
                playtest.state.useChart(document, audio, "Chart Editor · ${document.meta.songName.ifBlank { document.id }}")
                section = Section.PLAYTEST
                playTab = PlayTab.PLAY
            }
        }
    }

    val joinRoom: (com.brenninhotools.vconverter.online.RoomInfo) -> Unit = { room ->
        section = Section.GENERATOR
        val live = when (room.kind) {
            "character" -> genCharacter.live.also { generatorTab = GenTab.CHARACTER }
            "stage" -> genStage.live.also { generatorTab = GenTab.STAGE }
            "week" -> genWeek.live.also { generatorTab = GenTab.WEEK }
            else -> genSong.live.also { generatorTab = GenTab.SONG }
        }
        live.join(room.code)
    }

    val cursor = rememberAppCursor()
    val settings = rememberAppSettings()
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val touchCursor = remember { TouchCursorState() }
    val cursorImage = rememberCursorImage()

    androidx.compose.runtime.CompositionLocalProvider(LocalPlayTest provides playLauncher) {
    Box(Modifier.fillMaxSize().then(if (settings.touchCursor) Modifier.touchCursor(touchCursor) else Modifier)) {
        Scaffold(
            modifier = Modifier.appCursor(cursor),
            containerColor = Funkin.Background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Header(
                    folderName = folderName,
                    onPickFolder = { folderPicker.launch(null) },
                    onOptions = { optionsOpen = true },
                    section = section,
                    onSection = { section = it },
                    direction = direction
                )
            },
            bottomBar = {
                FunkinNavBar(
                    when (section) {
                        Section.CONVERTER -> Tab.entries
                        Section.GENERATOR -> GenTab.entries
                        Section.ONLINE -> OnlineTab.entries
                        Section.PLAYTEST -> PlayTab.entries
                    },
                    tab
                ) { selected ->
                    when (selected) {
                        is Tab -> converterTab = selected
                        is GenTab -> generatorTab = selected
                        is OnlineTab -> onlineTab = selected
                        is PlayTab -> playTab = selected
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab to (if (section == Section.CONVERTER) direction else Direction.PSYCH_TO_VSLICE),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                label = "tab"
            ) { (current, currentDirection) ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                ) {
                    if (current is Tab) {
                        EngineBridge(currentDirection) { direction = it }
                        Spacer(Modifier.size(12.dp))
                    }
                    TabHero(current, currentDirection)
                    Spacer(Modifier.size(16.dp))
                    if (currentDirection == Direction.CODENAME_TO_VSLICE && current is Tab) when (current) {
                        Tab.MOD -> CodenameModScreen(cnMod, folderName, save)
                        Tab.CHARTS -> ChartsScreen(cnCharts, folderName, save)
                        Tab.CHARACTERS -> ReverseScreen(cnCharacters, Funkin.Left, "Characters", "Add Codename characters", "Pick data/characters/*.xml with their spritesheet .xml (images/characters/…) for exact positions. Add <name>-dead.xml to merge the game over animations.", R.drawable.ic_tab_character, folderName, save, buttonText = "Convert to V-Slice")
                        Tab.STAGES -> ReverseScreen(cnStages, Funkin.Up, "Stages", "Add Codename stages", "Pick data/stages/*.xml. Add the stage images (.png and spritesheet .xml) so scaled sprites keep their exact spot.", R.drawable.ic_tab_stage, folderName, save, buttonText = "Convert to V-Slice")
                        Tab.WEEKS -> ReverseScreen(cnWeeks, Funkin.Right, "Weeks", "Add Codename weeks", "Pick data/weeks/weeks/*.xml plus data/weeks/characters/*.xml for the menu characters.", R.drawable.ic_tab_week, folderName, save, buttonText = "Convert to V-Slice")
                        Tab.SCRIPTS -> ReverseScreen(cnScripts, Funkin.Lavender, "Scripts", "Add Codename scripts", "Pick .hx scripts. Add the stage XML for stage scripts and data/events/*.json for custom events. Archives keep the folders, so song, stage, event and note scripts get the right scope.", R.drawable.ic_tab_script, folderName, save, buttonText = "Convert to V-Slice")
                    } else if (currentDirection == Direction.CODENAME_TO_PSYCH && current is Tab) when (current) {
                        Tab.MOD -> CodenameModScreen(
                            c2pMod, folderName, save,
                            intro = "Convert a whole Codename Engine mod to Psych: songs and variants, characters, stages (with their scripts), weeks, HScript scripts, events, note types, images and audio.",
                            infoNote = "Written to pack.json (read from data/config/modpack.ini). icon.png becomes pack.png.",
                            convertNote = "Download the converted mod as a .zip (unzip it into Psych's mods folder), or write it straight into an empty mod folder."
                        )
                        Tab.CHARTS -> ChartsScreen(c2pCharts, folderName, save)
                        Tab.CHARACTERS -> ReverseScreen(c2pCharacters, Funkin.Left, "Characters", "Add Codename characters", "Pick data/characters/*.xml with their spritesheet .xml (images/characters/…), or Animation.json + spritemap*.json for Animate atlases, so offsets stay exact.", R.drawable.ic_tab_character, folderName, save)
                        Tab.STAGES -> ReverseScreen(c2pStages, Funkin.Up, "Stages", "Add Codename stages", "Pick data/stages/*.xml. Sprites become stage objects; beat dancers, blends and extra settings go into a stage script.", R.drawable.ic_tab_stage, folderName, save)
                        Tab.WEEKS -> ReverseScreen(c2pWeeks, Funkin.Right, "Weeks", "Add Codename weeks", "Pick data/weeks/weeks/*.xml, data/weeks/characters/*.xml and the songs' meta.json for freeplay icons and colors.", R.drawable.ic_tab_week, folderName, save)
                        Tab.SCRIPTS -> ReverseScreen(c2pScripts, Funkin.Lavender, "Scripts", "Add Codename scripts", "Pick .hx scripts. Add the stage XML for stage scripts and data/events/*.json for custom events. Archives keep the folders, so song, stage, event and note scripts land in the right Psych folder.", R.drawable.ic_tab_script, folderName, save)
                    } else if (currentDirection == Direction.PSYCH_TO_CODENAME && current is Tab) when (current) {
                        Tab.MOD -> ModScreen(
                            p2cMod, folderName, save,
                            intro = "Convert a whole Psych Engine mod to Codename: songs, characters, stages, weeks, Lua/HScript scripts, custom events, note types, images and audio.",
                            infoNote = "Written to data/config/modpack.ini. pack.png becomes icon.png.",
                            convertNote = "Download the converted mod as a .zip (unzip it into Codename's mods folder), or write it straight into an empty mod folder."
                        )
                        Tab.CHARTS -> ChartsScreen(p2cCharts, folderName, save)
                        Tab.CHARACTERS -> ReverseScreen(p2cCharacters, Funkin.Left, "Characters", "Add Psych characters", "Pick characters/*.json. Offsets are scaled for Codename and the side decides the camera offsets. Add Animation.json + spritemap*.json for Animate atlas characters.", R.drawable.ic_tab_character, folderName, save, buttonText = "Convert to Codename")
                        Tab.STAGES -> ReverseScreen(p2cStages, Funkin.Up, "Stages", "Add Psych stages", "Pick stages/*.json and, if the stage builds its sprites in Lua, stages/<stage>.lua: those sprites move into the stage XML.", R.drawable.ic_tab_stage, folderName, save, buttonText = "Convert to Codename")
                        Tab.WEEKS -> ReverseScreen(p2cWeeks, Funkin.Right, "Weeks", "Add Psych weeks", "Pick weeks/*.json and images/menucharacters/*.json.", R.drawable.ic_tab_week, folderName, save, buttonText = "Convert to Codename")
                        Tab.SCRIPTS -> ReverseScreen(p2cScripts, Funkin.Lavender, "Scripts", "Add Lua / HScript", "Pick .lua and .hx scripts. Archives keep the folders, so scripts/, data/<song>/, stages/, custom_events/ and custom_notetypes/ land in the right Codename folder. Add stages/*.json for stage scripts.", R.drawable.ic_tab_script, folderName, save, buttonText = "Convert to Codename")
                    } else if (currentDirection == Direction.VSLICE_TO_PSYCH) when (current) {
                        Tab.MOD -> ReverseModScreen(revMod, folderName, save)
                        Tab.CHARTS -> ChartsScreen(revCharts, folderName, save)
                        Tab.CHARACTERS -> ReverseScreen(revCharacters, Funkin.Left, "Characters", "Add V-Slice characters", "Pick data/characters/*.json with their spritesheet .xml (images/…), or Animation.json + spritemap*.json for Animate atlases, for exact positions. Archives work too.", R.drawable.ic_tab_character, folderName, save)
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
                        GenTab.SONG -> ChartEditorScreen(genSong, folderName, save)
                        GenTab.CHARACTER -> CharacterEditorScreen(genCharacter, folderName, save)
                        GenTab.STAGE -> StageEditorScreen(genStage, folderName, save)
                        GenTab.WEEK -> WeekEditorScreen(genWeek, folderName, save)
                        GenTab.SCRIPT -> ScriptGenScreen(genScript, folderName, save)
                        OnlineTab.MODS -> OnlineModsScreen(online)
                        OnlineTab.SHARE -> OnlineShareScreen(online)
                        OnlineTab.NEWS -> OnlineNewsScreen(online)
                        OnlineTab.ROOMS -> OnlineRoomsScreen(online, joinRoom)
                        PlayTab.PLAY -> PlayTestPlayScreen(playtest)
                        PlayTab.REPORT -> PlayTestReportScreen(playtest)
                    }
                    Spacer(Modifier.size(24.dp))
                }
            }
            if (settings.fpsCounter) {
                FpsCounter(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = padding.calculateTopPadding() + 4.dp, end = 12.dp)
                )
            }
            }
        }
        if (settings.touchCursor) TouchCursorOverlay(touchCursor, cursorImage, Modifier.fillMaxSize())
    }
    }
    if (optionsOpen) OptionsDialog(settings) { optionsOpen = false }
    MenuMusic(settings.menuMusic, settings.musicVolume, suppressed = genSong.editor.playing)
    UiSounds(settings.uiSounds)
}

@Composable
private fun Header(
    folderName: String?,
    onPickFolder: () -> Unit,
    onOptions: () -> Unit,
    section: Section,
    onSection: (Section) -> Unit,
    direction: Direction
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Funkin.Background)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(40)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("V-CONVERTER", style = MaterialTheme.typography.titleLarge, color = Funkin.Text, maxLines = 1)
                AnimatedContent(targetState = section to direction, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "subtitle") { (current, currentDirection) ->
                    if (current == Section.GENERATOR) {
                        Text("New V-Slice content", style = MaterialTheme.typography.labelSmall, color = Funkin.Warning, maxLines = 1)
                    } else if (current == Section.ONLINE) {
                        Text("Mods, updates and news", style = MaterialTheme.typography.labelSmall, color = Funkin.Pink, maxLines = 1)
                    } else if (current == Section.PLAYTEST) {
                        Text("Play and check songs", style = MaterialTheme.typography.labelSmall, color = Funkin.Up, maxLines = 1)
                    } else {
                        Text(
                            currentDirection.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Funkin.Muted,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FolderChip(folderName, onPickFolder)
            Spacer(Modifier.width(8.dp))
            OptionsButton(onOptions)
        }
        SectionSwitch(section, onSection)
    }
}

@Composable
private fun FolderChip(folderName: String?, onClick: () -> Unit) {
    val picked = folderName != null
    val shape = RoundedCornerShape(50)
    val tint = if (picked) Funkin.Up else Funkin.Muted
    Row(
        modifier = Modifier
            .widthIn(max = 130.dp)
            .clip(shape)
            .background(if (picked) Funkin.Up.copy(alpha = 0.12f) else Funkin.Surface)
            .border(1.5.dp, if (picked) Funkin.Up.copy(alpha = 0.6f) else Funkin.Outline, shape)
            .soundClickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_folder), contentDescription = "Mod folder", tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("MOD FOLDER", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Funkin.Muted, maxLines = 1)
            Text(
                folderName ?: "Choose",
                style = MaterialTheme.typography.labelMedium,
                color = if (picked) Funkin.Text else Funkin.Down,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TabHero(tab: AppTab, direction: Direction) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(tab.accent.copy(alpha = 0.26f), Funkin.Surface, Funkin.Surface)))
            .border(1.5.dp, tab.accent.copy(alpha = 0.4f), shape)
    ) {
        Icon(
            painterResource(tab.icon),
            contentDescription = null,
            tint = tab.accent.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 18.dp)
                .size(110.dp)
                .rotate(-12f)
        )
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(tab.accent)
                    .border(3.dp, Funkin.Ink, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = Funkin.Ink, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tab.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Funkin.Text)
                Spacer(Modifier.height(2.dp))
                Text(if (tab is Tab) tab.describe(direction) else tab.description, style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
            }
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
                        .soundClickable { onSelect(item) }
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
                    .soundClickable { onSelect(item) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active) {
                    Icon(painterResource(item.icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(item.label.uppercase(), style = MaterialTheme.typography.labelLarge, fontSize = 12.sp, color = content, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun EngineBridge(direction: Direction, onChange: (Direction) -> Unit) {
    val turn by animateFloatAsState(if (direction.from.ordinal > direction.to.ordinal) 180f else 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f), label = "swap")
    val from = direction.from
    val to = direction.to
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EngineCard("From", from, Modifier.weight(1f)) {
                    val engines = Engine.entries
                    onChange(Direction.from(engines[(engines.indexOf(from) + 1) % engines.size]))
                }
                EngineCard("To", to, Modifier.weight(1f)) { onChange(direction.nextTarget()) }
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Funkin.Ink)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(from.color, to.color)))
                    .border(2.5.dp, Funkin.Ink, CircleShape)
                    .soundClickable(enabled = direction.canSwap) { onChange(direction.swapped()) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (direction.canSwap) R.drawable.ic_swap else R.drawable.ic_arrow_forward),
                    contentDescription = if (direction.canSwap) "Swap direction" else null,
                    tint = Funkin.Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (direction.canSwap) turn else 0f)
                )
            }
        }
        SourcePicker("SOURCE", direction.from, Engine.entries) { onChange(if (it == direction.from) direction else Direction.from(it)) }
        SourcePicker("TARGET", direction.to, Direction.targets(direction.from)) { onChange(direction.retarget(it)) }
    }
}

@Composable
private fun SourcePicker(caption: String, selected: Engine, enabled: List<Engine>, onSelect: (Engine) -> Unit) {
    val outer = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(Funkin.Surface)
            .border(1.5.dp, Funkin.Outline, outer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(caption, style = MaterialTheme.typography.labelSmall, color = Funkin.Muted, modifier = Modifier.width(56.dp).padding(start = 8.dp))
        Engine.entries.forEach { engine ->
            val active = engine == selected
            val usable = engine in enabled
            val fill by animateColorAsState(if (active) engine.color else Color.Transparent, label = "source")
            val content by animateColorAsState(if (active) Funkin.Ink else if (usable) Funkin.Muted else Funkin.Muted.copy(alpha = 0.35f), label = "sourceText")
            val shape = RoundedCornerShape(11.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(shape)
                    .background(fill)
                    .soundClickable(enabled = usable) { onSelect(engine) },
                contentAlignment = Alignment.Center
            ) {
                Text(engine.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EngineCard(role: String, engine: Engine, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val color by animateColorAsState(engine.color, label = "engine")
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Funkin.Surface)))
            .border(1.5.dp, color.copy(alpha = 0.55f), shape)
            .soundClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = if (role == "From") Alignment.Start else Alignment.End
    ) {
        Text(role.uppercase(), style = MaterialTheme.typography.labelSmall, color = Funkin.Muted)
        AnimatedContent(
            targetState = engine,
            transitionSpec = {
                val direction = if (role == "From") -1 else 1
                (slideInVertically { it * direction } + fadeIn()) togetherWith (slideOutVertically { -it * direction } + fadeOut())
            },
            label = "engineName"
        ) { current ->
            Column(horizontalAlignment = if (role == "From") Alignment.Start else Alignment.End) {
                Text(current.label, style = MaterialTheme.typography.titleLarge, color = current.color, maxLines = 1)
                Text(current.detail, style = MaterialTheme.typography.labelSmall, color = Funkin.Muted, maxLines = 1)
            }
        }
    }
}
