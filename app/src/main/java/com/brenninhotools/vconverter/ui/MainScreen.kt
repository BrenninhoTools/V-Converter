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

enum class Tab(val label: String, @DrawableRes val icon: Int, val accent: Color, val title: String, val description: String) {
    MOD("Mod", R.drawable.ic_tab_mod, Funkin.Pink, "Whole mod", "A full Psych mod from a folder, .zip, .rar or .7z."),
    CHARTS("Charts", R.drawable.ic_tab_chart, Funkin.Down, "Charts", "Psych charts and events.json into one V-Slice song."),
    CHARACTERS("Chars", R.drawable.ic_tab_character, Funkin.Left, "Characters", "Character JSON + spritesheet XML, pixel-exact offsets."),
    STAGES("Stages", R.drawable.ic_tab_stage, Funkin.Up, "Stages", "Stage layouts, cameras and editor props."),
    WEEKS("Weeks", R.drawable.ic_tab_week, Funkin.Right, "Weeks", "Story weeks into V-Slice levels with menu props."),
    SCRIPTS("Scripts", R.drawable.ic_tab_script, Funkin.Lavender, "Scripts", "Lua and HScript into V-Slice modules.")
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

    var tab by rememberSaveable { mutableStateOf(Tab.MOD) }
    var pendingSave by rememberSaveable { mutableStateOf<Tab?>(null) }

    fun converterFor(target: Tab): BaseConverterViewModel = when (target) {
        Tab.MOD -> mod
        Tab.CHARTS -> charts
        Tab.CHARACTERS -> characters
        Tab.STAGES -> stages
        Tab.WEEKS -> weeks
        Tab.SCRIPTS -> scripts
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
                onPickFolder = { folderPicker.launch(null) }
            )
        },
        bottomBar = { FunkinNavBar(tab) { tab = it } }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            label = "tab"
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                TabHero(current)
                Spacer(Modifier.size(16.dp))
                when (current) {
                    Tab.MOD -> ModScreen(mod, folderName, save)
                    Tab.CHARTS -> ChartsScreen(charts, folderName, save)
                    Tab.CHARACTERS -> CharactersScreen(characters, folderName, save)
                    Tab.STAGES -> StagesScreen(stages, folderName, save)
                    Tab.WEEKS -> WeeksScreen(weeks, folderName, save)
                    Tab.SCRIPTS -> ScriptsScreen(scripts, folderName, save)
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Header(folderName: String?, onPickFolder: () -> Unit) {
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
                    Text("Psych Engine", style = MaterialTheme.typography.labelMedium, color = Funkin.Left)
                    Text("  →  ", style = MaterialTheme.typography.labelMedium, color = Funkin.Muted)
                    Text("V-Slice", style = MaterialTheme.typography.labelMedium, color = Funkin.Down)
                }
            }
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
private fun TabHero(tab: Tab) {
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
            Text(tab.description, style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
        }
    }
}

@Composable
private fun FunkinNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
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
                .background(Brush.horizontalGradient(Tab.entries.map { it.accent }))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Tab.entries.forEach { item ->
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
