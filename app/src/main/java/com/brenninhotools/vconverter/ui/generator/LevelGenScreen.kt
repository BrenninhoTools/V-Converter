package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.generator.LevelGenerator
import com.brenninhotools.vconverter.generator.LevelPropSpec
import com.brenninhotools.vconverter.generator.LevelSpec
import com.brenninhotools.vconverter.generator.MenuProp
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.AssetPicker
import com.brenninhotools.vconverter.ui.components.ChoiceChips
import com.brenninhotools.vconverter.ui.components.Collapsible
import com.brenninhotools.vconverter.ui.components.FieldLabel
import com.brenninhotools.vconverter.ui.components.FieldRow
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.NumberField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.ToggleRow
import com.brenninhotools.vconverter.ui.theme.Funkin

data class MenuPropRow(
    val mode: MenuProp = MenuProp.BASE,
    val assetPath: String = "",
    val idlePrefix: String = "idle",
    val confirmPrefix: String = "",
    val scale: String = "1",
    val offsetX: String = "0",
    val offsetY: String = "0",
    val image: PickedAsset? = null,
    val xml: PickedAsset? = null
)

class LevelGenViewModel(application: Application) : GeneratorViewModel(application) {
    var id by input("")
    var name by input("")
    var songs by input("")
    var background by input("#F9CF51")
    var visible by input(true)
    var title by input<PickedAsset?>(null)
    var props by input(listOf(MenuPropRow(), MenuPropRow(confirmPrefix = "confirm"), MenuPropRow()))

    override fun downloadName(): String = "${Ids.of(id).ifEmpty { "level" }}-level.zip"

    fun update(index: Int, transform: (MenuPropRow) -> MenuPropRow) {
        props = props.mapIndexed { i, row -> if (i == index) transform(row) else row }
    }

    fun pickTitle(uri: Uri) = pick(uri, setOf("png")) { title = it }

    fun pickPropImage(index: Int, uri: Uri) = pick(uri, setOf("png")) { asset -> update(index) { it.copy(image = asset) } }

    fun pickPropXml(index: Int, uri: Uri) = pick(uri, setOf("xml")) { asset -> update(index) { it.copy(xml = asset) } }

    private fun spec(row: MenuPropRow, label: String) = LevelPropSpec(
        mode = row.mode,
        assetPath = row.assetPath,
        idlePrefix = row.idlePrefix,
        confirmPrefix = row.confirmPrefix,
        scale = number(row.scale, "$label scale"),
        offsetX = number(row.offsetX, "$label offset X"),
        offsetY = number(row.offsetY, "$label offset Y"),
        image = row.image,
        xml = row.xml
    )

    override fun build(): Generated = LevelGenerator.generate(
        LevelSpec(
            id = id,
            name = name,
            songs = songs.split(',', '\n'),
            background = background,
            visible = visible,
            opponent = spec(props[0], "Opponent"),
            boyfriend = spec(props[1], "Boyfriend"),
            girlfriend = spec(props[2], "Girlfriend"),
            title = title
        )
    )

    companion object {
        val slots = listOf("Opponent", "Boyfriend", "Girlfriend")
    }
}

@Composable
fun LevelGenScreen(viewModel: LevelGenViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Right
    val busy = viewModel.busy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Level", accent, 1)
            FieldRow {
                FunkinTextField(viewModel.id, { viewModel.id = it }, "Id (week1, weekdude…)", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.name, { viewModel.name = it }, "Story name", accent, Modifier.weight(1f), enabled = !busy)
            }
            FunkinTextField(viewModel.songs, { viewModel.songs = it }, "Song ids (comma separated)", accent, enabled = !busy)
            FunkinTextField(viewModel.background, { viewModel.background = it }, "Background color (#hex)", accent, enabled = !busy)
            AssetPicker("Title image (.png)", viewModel.title?.name, accent, arrayOf("image/png"), viewModel::pickTitle, { viewModel.title = null }, enabled = !busy)
            ToggleRow("Show in story mode", viewModel.visible, { viewModel.visible = it }, accent, enabled = !busy)
        }
        FunkinCard {
            SectionTitle("Menu characters", accent, 2)
            viewModel.props.forEachIndexed { index, row ->
                val slot = LevelGenViewModel.slots[index]
                Collapsible(title = slot, count = if (row.mode == MenuProp.NONE) 0 else 1, accent = accent, startOpen = index == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChips(
                            options = MenuProp.entries,
                            label = { if (it == MenuProp.BASE) "Base ${listOf("dad", "bf", "gf")[index]}" else it.label },
                            isSelected = { it == row.mode },
                            onClick = { mode -> viewModel.update(index) { it.copy(mode = mode) } },
                            accent = accent,
                            enabled = !busy
                        )
                        if (row.mode == MenuProp.CUSTOM) {
                            AssetPicker("Image (.png)", row.image?.name, accent, arrayOf("image/png"), { viewModel.pickPropImage(index, it) }, { viewModel.update(index) { r -> r.copy(image = null) } }, enabled = !busy)
                            AssetPicker("Sparrow XML", row.xml?.name, accent, arrayOf("text/xml", "application/xml", "*/*"), { viewModel.pickPropXml(index, it) }, { viewModel.update(index) { r -> r.copy(xml = null) } }, enabled = !busy)
                            FunkinTextField(row.assetPath, { v -> viewModel.update(index) { it.copy(assetPath = v) } }, "Asset path (default storymenu/props/<image>)", accent, enabled = !busy)
                            FieldRow {
                                FunkinTextField(row.idlePrefix, { v -> viewModel.update(index) { it.copy(idlePrefix = v) } }, "Idle prefix", accent, Modifier.weight(1f), enabled = !busy)
                                FunkinTextField(row.confirmPrefix, { v -> viewModel.update(index) { it.copy(confirmPrefix = v) } }, "Confirm prefix", accent, Modifier.weight(1f), enabled = !busy)
                            }
                            FieldRow {
                                NumberField(row.scale, { v -> viewModel.update(index) { it.copy(scale = v) } }, "Scale", accent, Modifier.weight(1f), enabled = !busy)
                                NumberField(row.offsetX, { v -> viewModel.update(index) { it.copy(offsetX = v) } }, "Offset X", accent, Modifier.weight(1f), enabled = !busy)
                                NumberField(row.offsetY, { v -> viewModel.update(index) { it.copy(offsetY = v) } }, "Offset Y", accent, Modifier.weight(1f), enabled = !busy)
                            }
                        }
                    }
                }
            }
            FieldLabel("Base props use the base game's story menu sprites.")
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 3,
            canConvert = viewModel.id.isNotBlank() && viewModel.songs.isNotBlank(),
            onConvert = viewModel::generate,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Generate",
            buttonText = "Generate level",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
