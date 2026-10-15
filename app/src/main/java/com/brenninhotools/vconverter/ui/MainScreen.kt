package com.brenninhotools.vconverter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.brenninhotools.vconverter.ui.stages.StagesScreen
import com.brenninhotools.vconverter.ui.stages.StagesViewModel
import com.brenninhotools.vconverter.ui.mod.ModScreen
import com.brenninhotools.vconverter.ui.mod.ModViewModel
import com.brenninhotools.vconverter.ui.weeks.WeeksScreen
import com.brenninhotools.vconverter.ui.weeks.WeeksViewModel
import com.brenninhotools.vconverter.ui.theme.Funkin

enum class Tab(val label: String, @DrawableRes val icon: Int, val accent: Color) {
    MOD("Mod", R.drawable.ic_tab_mod, Funkin.Pink),
    CHARTS("Charts", R.drawable.ic_tab_chart, Funkin.Down),
    CHARACTERS("Chars", R.drawable.ic_tab_character, Funkin.Left),
    STAGES("Stages", R.drawable.ic_tab_stage, Funkin.Up),
    WEEKS("Weeks", R.drawable.ic_tab_week, Funkin.Right)
}

@Composable
fun MainScreen() {
    val modFolder: ModFolderViewModel = viewModel()
    val charts: ChartsViewModel = viewModel()
    val characters: CharactersViewModel = viewModel()
    val stages: StagesViewModel = viewModel()
    val weeks: WeeksViewModel = viewModel()
    val mod: ModViewModel = viewModel()

    var tab by rememberSaveable { mutableStateOf(Tab.MOD) }
    var pendingSave by rememberSaveable { mutableStateOf<Tab?>(null) }

    fun converterFor(target: Tab): BaseConverterViewModel = when (target) {
        Tab.MOD -> mod
        Tab.CHARTS -> charts
        Tab.CHARACTERS -> characters
        Tab.STAGES -> stages
        Tab.WEEKS -> weeks
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
        bottomBar = {
            NavigationBar(containerColor = Funkin.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Funkin.Ink,
                            selectedTextColor = item.accent,
                            indicatorColor = item.accent,
                            unselectedIconColor = Funkin.Muted,
                            unselectedTextColor = Funkin.Muted
                        )
                    )
                }
            }
        }
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
                when (current) {
                    Tab.MOD -> ModScreen(mod, folderName, save)
                    Tab.CHARTS -> ChartsScreen(charts, folderName, save)
                    Tab.CHARACTERS -> CharactersScreen(characters, folderName, save)
                    Tab.STAGES -> StagesScreen(stages, folderName, save)
                    Tab.WEEKS -> WeeksScreen(weeks, folderName, save)
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
