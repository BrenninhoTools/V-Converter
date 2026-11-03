package com.brenninhotools.vconverter.ui.nmv

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.convert.NmvConverters
import com.brenninhotools.vconverter.convert.NmvTab
import com.brenninhotools.vconverter.ui.BaseConverterViewModel
import com.brenninhotools.vconverter.ui.Direction
import com.brenninhotools.vconverter.ui.Engine
import com.brenninhotools.vconverter.ui.Tab
import com.brenninhotools.vconverter.ui.codename.CodenameModScreen
import com.brenninhotools.vconverter.ui.mod.ModScreen
import com.brenninhotools.vconverter.ui.reverse.ReverseModScreen
import com.brenninhotools.vconverter.ui.reverse.ReverseScreen
import com.brenninhotools.vconverter.ui.theme.Funkin

class NmvModels(
    val toPsych: NmvToPsychModViewModel,
    val toVSlice: NmvToVSliceModViewModel,
    val toCodename: NmvToCodenameModViewModel,
    val fromPsych: PsychToNmvModViewModel,
    val fromVSlice: VSliceToNmvModViewModel,
    val fromCodename: CodenameToNmvModViewModel,
    val files: Map<Pair<Direction, Tab>, NmvFileViewModel>
) {
    fun mod(direction: Direction): BaseConverterViewModel = when (direction) {
        Direction.NMV_TO_VSLICE -> toVSlice
        Direction.NMV_TO_CODENAME -> toCodename
        Direction.PSYCH_TO_NMV -> fromPsych
        Direction.VSLICE_TO_NMV -> fromVSlice
        Direction.CODENAME_TO_NMV -> fromCodename
        else -> toPsych
    }

    fun forTab(direction: Direction, tab: Tab): BaseConverterViewModel = if (tab == Tab.MOD) mod(direction) else files.getValue(direction to tab)
}

val nmvDirections = listOf(
    Direction.NMV_TO_PSYCH, Direction.NMV_TO_VSLICE, Direction.NMV_TO_CODENAME,
    Direction.PSYCH_TO_NMV, Direction.VSLICE_TO_NMV, Direction.CODENAME_TO_NMV
)

private fun nmvTab(tab: Tab): NmvTab = when (tab) {
    Tab.CHARTS -> NmvTab.CHARTS
    Tab.CHARACTERS -> NmvTab.CHARACTERS
    Tab.STAGES -> NmvTab.STAGES
    Tab.WEEKS -> NmvTab.WEEKS
    else -> NmvTab.SCRIPTS
}

@Composable
fun rememberNmvModels(): NmvModels {
    val app = LocalContext.current.applicationContext as Application
    val files = LinkedHashMap<Pair<Direction, Tab>, NmvFileViewModel>()
    nmvDirections.forEach { direction ->
        Tab.entries.filter { it != Tab.MOD }.forEach { tab ->
            files[direction to tab] = viewModel<NmvFileViewModel>(
                key = "nmv-${direction.name}-${tab.name}",
                factory = viewModelFactory { initializer { NmvFileViewModel(app, NmvConverters.create(direction.from.key, direction.to.key, nmvTab(tab))) } }
            )
        }
    }
    return NmvModels(
        viewModel<NmvToPsychModViewModel>(),
        viewModel<NmvToVSliceModViewModel>(),
        viewModel<NmvToCodenameModViewModel>(),
        viewModel<PsychToNmvModViewModel>(),
        viewModel<VSliceToNmvModViewModel>(),
        viewModel<CodenameToNmvModViewModel>(),
        files
    )
}

fun nmvDescription(direction: Direction, tab: Tab): String {
    val target = direction.to.title
    return if (direction.from == Engine.NMV) when (tab) {
        Tab.MOD -> "A full NightmareVision mod into a $target mod: charts, HScript, characters, stages, weeks and audio."
        Tab.CHARTS -> "songs/<song>/charts (nmv2) into $target charts."
        Tab.CHARACTERS -> "NightmareVision character JSON into $target characters."
        Tab.STAGES -> "data/stages/<stage>/data.json and its stageObjects into $target stages."
        Tab.WEEKS -> "data/weeks and story menu characters into $target weeks."
        Tab.SCRIPTS -> "NightmareVision HScript into $target scripts."
    } else when (tab) {
        Tab.MOD -> "A full ${direction.from.title} mod into a NightmareVision mod, with Lua turned into HScript."
        Tab.CHARTS -> "${direction.from.title} charts into nmv2 charts with meta.json."
        Tab.CHARACTERS -> "${direction.from.title} characters into data/characters JSON."
        Tab.STAGES -> "${direction.from.title} stages into data/stages/<stage>/data.json with stageObjects."
        Tab.WEEKS -> "${direction.from.title} weeks into data/weeks and story menu characters."
        Tab.SCRIPTS -> "${direction.from.title} scripts (Lua too) into NightmareVision HScript."
    }
}

private fun hint(direction: Direction, tab: Tab): String = when (direction.from) {
    Engine.NMV -> when (tab) {
        Tab.CHARTS -> "Pick songs/<song>/charts/<difficulty>.json and events.json. Keep the songs/<song>/charts folders (or use an archive) so the song is known."
        Tab.CHARACTERS -> "Pick data/characters/*.json."
        Tab.STAGES -> "Pick data/stages/<stage>/data.json and script.hx (or an archive with the stage folders)."
        Tab.WEEKS -> "Pick data/weeks/*.json and images/menus/story/characters/*.json."
        else -> "Pick .hx scripts. Archives keep the folders, so songs/<song>/, data/scripts/, data/stages/, data/events/ and data/notetypes/ keep their scope."
    }
    Engine.PSYCH -> when (tab) {
        Tab.CHARTS -> "Pick data/<song>/<song>-<difficulty>.json and events.json."
        Tab.CHARACTERS -> "Pick characters/*.json (and their .lua/.hx scripts)."
        Tab.STAGES -> "Pick stages/*.json and stages/<stage>.lua or .hx."
        Tab.WEEKS -> "Pick weeks/*.json and images/menucharacters/*.json."
        else -> "Pick .lua and .hx scripts. Archives keep the folders, so scripts/, data/<song>/, stages/, custom_events/ and custom_notetypes/ keep their scope."
    }
    Engine.CODENAME -> when (tab) {
        Tab.CHARTS -> "Pick songs/<song>/charts/*.json with meta.json and events.json."
        Tab.CHARACTERS -> "Pick data/characters/*.xml with their spritesheet .xml."
        Tab.STAGES -> "Pick data/stages/*.xml."
        Tab.WEEKS -> "Pick data/weeks/weeks/*.xml and data/weeks/characters/*.xml."
        else -> "Pick .hx scripts, plus stage XMLs and data/events/*.json for context."
    }
    Engine.VSLICE -> when (tab) {
        Tab.CHARTS -> "Pick <song>-metadata.json and <song>-chart.json (and variations)."
        Tab.CHARACTERS -> "Pick data/characters/*.json with their spritesheet .xml."
        Tab.STAGES -> "Pick data/stages/*.json."
        Tab.WEEKS -> "Pick data/levels/*.json plus the songs' -metadata.json files."
        else -> "Pick .hxc scripts."
    }
}

@Composable
fun NmvTabScreen(models: NmvModels, direction: Direction, tab: Tab, folderName: String?, save: () -> Unit) {
    val button = "Convert to " + direction.to.title
    when (tab) {
        Tab.MOD -> when (direction) {
            Direction.PSYCH_TO_NMV -> ModScreen(
                models.fromPsych, folderName, save,
                intro = "Convert a whole Psych Engine mod to NightmareVision: charts, characters, stages, weeks, Lua scripts (as HScript), HScript, events, note types, images and audio.",
                infoNote = "Written to meta.json. pack.png becomes images/pack.png.",
                convertNote = "Download the converted mod as a .zip (unzip it into NightmareVision's content folder), or write it straight into an empty mod folder."
            )
            Direction.CODENAME_TO_NMV -> CodenameModScreen(
                models.fromCodename, folderName, save,
                intro = "Convert a whole Codename Engine mod to NightmareVision: songs, characters, stages, weeks, HScript scripts, events, note types, images and audio.",
                infoNote = "Written to meta.json (read from data/config/modpack.ini). icon.png becomes images/pack.png.",
                convertNote = "Download the converted mod as a .zip (unzip it into NightmareVision's content folder), or write it straight into an empty mod folder."
            )
            Direction.VSLICE_TO_NMV -> ReverseModScreen(
                models.fromVSlice, folderName, save,
                intro = "Convert a whole V-Slice mod to NightmareVision: songs (variations become their own songs), characters, stages, levels, .hxc scripts, images and audio.",
                convertNote = "Download the converted mod as a .zip (unzip it into NightmareVision's content folder), or write it straight into an empty mod folder."
            )
            else -> {
                val target = direction.to.title
                val note = when (direction.to) {
                    Engine.VSLICE -> "Written to _polymod_meta.json (read from meta.json)."
                    Engine.CODENAME -> "Written to data/config/modpack.ini (read from meta.json)."
                    else -> "Written to pack.json (read from meta.json). The meta.json icon becomes pack.png."
                }
                NmvModScreen(
                    models.mod(direction) as NmvModViewModel, folderName, save,
                    intro = "Convert a whole NightmareVision mod to $target: nmv2 charts, characters, stages with stageObjects, weeks, HScript, events, note types, images and audio.",
                    infoNote = note,
                    convertNote = "Download the converted mod as a .zip, or write it straight into an empty mod folder."
                )
            }
        }
        Tab.CHARTS -> ReverseScreen(models.files.getValue(direction to tab), Funkin.Down, "Charts", "Add charts", hint(direction, tab), R.drawable.ic_tab_chart, folderName, save, buttonText = button)
        Tab.CHARACTERS -> ReverseScreen(models.files.getValue(direction to tab), Funkin.Left, "Characters", "Add characters", hint(direction, tab), R.drawable.ic_tab_character, folderName, save, buttonText = button)
        Tab.STAGES -> ReverseScreen(models.files.getValue(direction to tab), Funkin.Up, "Stages", "Add stages", hint(direction, tab), R.drawable.ic_tab_stage, folderName, save, buttonText = button)
        Tab.WEEKS -> ReverseScreen(models.files.getValue(direction to tab), Funkin.Right, "Weeks", "Add weeks", hint(direction, tab), R.drawable.ic_tab_week, folderName, save, buttonText = button)
        Tab.SCRIPTS -> ReverseScreen(models.files.getValue(direction to tab), Funkin.Lavender, "Scripts", "Add scripts", hint(direction, tab), R.drawable.ic_tab_script, folderName, save, buttonText = button)
    }
}
