package com.brenninhotools.vconverter.ui.weeks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FileItem
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.theme.Funkin

@Composable
fun WeeksScreen(viewModel: WeeksViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Right
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val busy = viewModel.busy

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Weeks", accent, 1)
            DropZone(
                title = "Add weeks & menu characters",
                hint = "Pick week .json files (weeks folder) and the menu character .json files from images/menucharacters, or a .zip/.rar/.7z with them.",
                accent = accent,
                icon = R.drawable.ic_tab_week,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            if (viewModel.menuCharacters.isNotEmpty()) {
                Text(
                    "Menu characters: " + viewModel.menuCharacters.keys.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Funkin.Muted
                )
            }
            viewModel.weeks.forEachIndexed { index, item ->
                val missing = viewModel.missingMenuCharacters(item)
                FileItem(
                    title = item.fileName,
                    accent = accent,
                    enabled = !busy,
                    onRemove = { viewModel.remove(index) },
                    tags = {
                        Tag("${item.week.songs.size} songs", accent)
                        item.week.storyName.takeIf { it.isNotBlank() }?.let { Tag(it, Funkin.Muted) }
                        if (missing.isEmpty()) Tag("Menu chars ready", Funkin.Up) else Tag("Missing ${missing.size} menu char(s)", Funkin.Warning)
                        if (item.week.hideStoryMode) Tag("Hidden", Funkin.Muted)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FunkinTextField(item.id, { viewModel.setId(index, it) }, "Level id", accent, enabled = !busy)
                        Text(
                            item.week.songs.joinToString(" · ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = Funkin.Muted
                        )
                        if (missing.isNotEmpty()) {
                            Text(
                                "Needs: " + missing.joinToString { "menucharacters/$it.json" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Funkin.Warning
                            )
                        }
                    }
                }
            }
        }

        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 2,
            canConvert = viewModel.weeks.isNotEmpty(),
            onConvert = viewModel::convert,
            onSave = onSave,
            modFolderName = modFolderName
        )
    }
}
