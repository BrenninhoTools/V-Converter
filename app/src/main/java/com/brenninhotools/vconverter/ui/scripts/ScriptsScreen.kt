package com.brenninhotools.vconverter.ui.scripts

import com.brenninhotools.vconverter.overlay.soundClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.script.ScriptScope
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FileItem
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.theme.Funkin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptsScreen(viewModel: ScriptsViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Lavender
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val busy = viewModel.busy

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Scripts", accent, 1)
            DropZone(
                title = "Add Psych scripts",
                hint = "Pick .lua or .hx files, or an archive with the mod's data, stages, scripts, custom_events and custom_notetypes folders. Each script becomes a V-Slice module.",
                accent = accent,
                icon = R.drawable.ic_tab_script,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            if (viewModel.scripts.isNotEmpty()) {
                FunkinTextField(viewModel.modName, { viewModel.modName = it }, "Mod name (used in class names)", accent, enabled = !busy)
            }
            viewModel.scripts.forEachIndexed { index, item ->
                FileItem(
                    title = item.fileName,
                    accent = accent,
                    enabled = !busy,
                    onRemove = { viewModel.remove(index) },
                    tags = {
                        Tag(if (item.isLua) "Lua" else "HScript", if (item.isLua) Funkin.Down else Funkin.Left)
                        Tag("${item.lineCount} lines", Funkin.Muted)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Runs in", style = MaterialTheme.typography.labelSmall, color = Funkin.Muted)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ScriptScope.entries.forEach { scope ->
                                ScopeChip(scope.label, scope == item.scope, accent, enabled = !busy) { viewModel.setScope(index, scope) }
                            }
                        }
                        if (item.scope != ScriptScope.GLOBAL) {
                            FunkinTextField(item.target, { viewModel.setTarget(index, it) }, "${item.scope.label} name", accent, enabled = !busy)
                        }
                    }
                }
            }
        }

        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 2,
            canConvert = viewModel.scripts.isNotEmpty(),
            onConvert = viewModel::convert,
            onSave = onSave,
            modFolderName = modFolderName
        )
    }
}

@Composable
private fun ScopeChip(text: String, selected: Boolean, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Funkin.Ink else Funkin.Text,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent else Funkin.Surface)
            .border(1.5.dp, if (selected) Funkin.Ink else Funkin.Outline, shape)
            .soundClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
