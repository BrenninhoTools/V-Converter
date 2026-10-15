package com.brenninhotools.vconverter.ui.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FileItem
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.theme.Funkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(viewModel: CharactersViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Left
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val busy = viewModel.busy

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Characters", accent, 1)
            DropZone(
                title = "Add characters & sheets",
                hint = "Pick Psych character .json files together with their spritesheet .xml files (images/characters). The XML makes positions pixel-exact. You can also pick a .zip/.rar/.7z.",
                accent = accent,
                icon = R.drawable.ic_tab_character,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            if (viewModel.atlases.isNotEmpty()) {
                Text(
                    "${viewModel.atlases.size} spritesheet(s) loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = Funkin.Muted
                )
            }
            viewModel.characters.forEachIndexed { index, item ->
                val missing = viewModel.missingAtlases(item)
                FileItem(
                    title = item.fileName,
                    accent = accent,
                    enabled = !busy,
                    onRemove = { viewModel.remove(index) },
                    tags = {
                        if (missing.isEmpty()) Tag("Sheet found", Funkin.Up) else Tag("Missing ${missing.size} sheet(s)", Funkin.Warning)
                        Tag("${item.character.animations.size} anims", Funkin.Muted)
                        Tag("Scale ${item.character.scale}", Funkin.Muted)
                        if (item.character.atlasImages.size > 1) Tag("Multi-atlas", accent)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FunkinTextField(item.id, { viewModel.setId(index, it) }, "Character id", accent, enabled = !busy)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            CharacterSlot.entries.forEachIndexed { slotIndex, slot ->
                                SegmentedButton(
                                    selected = item.slot == slot,
                                    onClick = { viewModel.setSlot(index, slot) },
                                    enabled = !busy,
                                    shape = SegmentedButtonDefaults.itemShape(slotIndex, CharacterSlot.entries.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = accent.copy(alpha = 0.25f),
                                        activeContentColor = Funkin.Text,
                                        activeBorderColor = accent,
                                        inactiveContainerColor = Funkin.Surface,
                                        inactiveContentColor = Funkin.Muted,
                                        inactiveBorderColor = Funkin.Outline
                                    ),
                                    icon = {}
                                ) {
                                    Text(slot.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (missing.isNotEmpty()) {
                            Text(
                                "Needs: " + missing.joinToString { it.substringAfterLast('/') + ".xml" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Funkin.Warning
                            )
                        }
                    }
                }
            }
            if (viewModel.characters.isNotEmpty()) {
                Text(
                    "Slot = where the character usually stands. It decides how offsets are rebased.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Funkin.Muted
                )
            }
        }

        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 2,
            canConvert = viewModel.characters.isNotEmpty(),
            onConvert = viewModel::convert,
            onSave = onSave,
            modFolderName = modFolderName
        )
    }
}
