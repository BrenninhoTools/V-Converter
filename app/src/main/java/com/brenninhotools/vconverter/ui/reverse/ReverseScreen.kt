package com.brenninhotools.vconverter.ui.reverse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.core.CharacterSlot
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.ChoiceChips
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FileItem
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.theme.Funkin

@Composable
fun ReverseScreen(
    viewModel: ReverseViewModel,
    accent: Color,
    title: String,
    dropTitle: String,
    hint: String,
    @DrawableRes icon: Int,
    modFolderName: String?,
    onSave: () -> Unit,
    footnote: String? = null
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val busy = viewModel.busy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle(title, accent, 1)
            DropZone(
                title = dropTitle,
                hint = hint,
                accent = accent,
                icon = icon,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            viewModel.files.forEachIndexed { index, item ->
                FileItem(
                    title = item.name,
                    accent = kindColor(item.kind, accent),
                    enabled = !busy,
                    onRemove = { viewModel.remove(index) },
                    tags = {
                        Tag(kindLabel(item.kind), kindColor(item.kind, accent))
                        if (item.label.isNotBlank()) Tag(item.label, Funkin.Muted)
                    }
                ) {
                    val slot = item.slot
                    if (slot != null) {
                        ChoiceChips(
                            options = CharacterSlot.entries,
                            label = { it.label },
                            isSelected = { it == slot },
                            onClick = { viewModel.setSlot(index, it) },
                            accent = accent,
                            enabled = !busy
                        )
                    }
                }
            }
            if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 2,
            canConvert = viewModel.canConvert,
            onConvert = viewModel::convert,
            onSave = onSave,
            modFolderName = modFolderName,
            buttonText = "Convert to Psych"
        )
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "metadata" -> "Metadata"
    "chart" -> "Chart"
    "atlas" -> "Spritesheet"
    "character" -> "Character"
    "stage" -> "Stage"
    "level" -> "Level"
    "script" -> "Script"
    else -> kind
}

private fun kindColor(kind: String, accent: Color): Color = when (kind) {
    "metadata" -> Funkin.Warning
    "atlas" -> Funkin.Up
    else -> accent
}
