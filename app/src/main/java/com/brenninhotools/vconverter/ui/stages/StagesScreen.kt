package com.brenninhotools.vconverter.ui.stages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun StagesScreen(viewModel: StagesViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Up
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val busy = viewModel.busy

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Stages", accent, 1)
            DropZone(
                title = "Add Psych stages",
                hint = "Pick stage .json files from the stages folder, plus the stage's .lua if it builds sprites in a script: they become V-Slice props. Add the animated sprites' .xml (images/…) so scaled animations keep their exact offsets. Archives (.zip/.rar/.7z) work too.",
                accent = accent,
                icon = R.drawable.ic_tab_stage,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            viewModel.stages.forEachIndexed { index, item ->
                val objects = item.stage.objects
                FileItem(
                    title = item.fileName,
                    accent = accent,
                    enabled = !busy,
                    onRemove = { viewModel.remove(index) },
                    tags = {
                        if (item.lua != null) Tag("Lua sprites", Funkin.Down)
                        if (!item.fromJson) Tag("No JSON", Funkin.Warning)
                        if (objects != null) {
                            Tag("${objects.count { it.type in setOf("square", "sprite", "animatedSprite") }} sprites", accent)
                        } else if (item.lua == null) {
                            Tag("Script stage", Funkin.Warning)
                        }
                        Tag("Zoom ${item.stage.defaultZoom}", Funkin.Muted)
                        if (item.stage.isPixelStage) Tag("Pixel", Funkin.Left)
                        val sheets = viewModel.atlases.keys.map { it.substringAfterLast('/') }.toSet()
                        val animated = objects?.filter { it.type == "animatedSprite" }?.map { it.image.substringAfterLast('/').lowercase() }?.distinct() ?: emptyList()
                        if (animated.isNotEmpty()) Tag("${animated.count { it in sheets }}/${animated.size} spritesheets", if (animated.all { it in sheets }) Funkin.Up else Funkin.Muted)
                    }
                ) {
                    FunkinTextField(item.id, { viewModel.setId(index, it) }, "Stage id", accent, enabled = !busy)
                }
            }
        }

        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 2,
            canConvert = viewModel.stages.isNotEmpty(),
            onConvert = viewModel::convert,
            onSave = onSave,
            modFolderName = modFolderName
        )
    }
}
