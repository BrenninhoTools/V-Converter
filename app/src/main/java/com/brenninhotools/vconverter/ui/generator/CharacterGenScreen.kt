package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.core.SparrowAtlas
import com.brenninhotools.vconverter.generator.AnimSpec
import com.brenninhotools.vconverter.generator.CharacterGenerator
import com.brenninhotools.vconverter.generator.CharacterSpec
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.AssetPicker
import com.brenninhotools.vconverter.ui.components.FieldRow
import com.brenninhotools.vconverter.ui.components.FileItem
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.NumberField
import com.brenninhotools.vconverter.ui.components.OutlineButton
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.components.ToggleRow
import com.brenninhotools.vconverter.ui.theme.Funkin

data class AnimRow(
    val name: String,
    val prefix: String,
    val frameRate: String = "24",
    val looped: Boolean = false,
    val offsetX: String = "0",
    val offsetY: String = "0"
)

class CharacterGenViewModel(application: Application) : GeneratorViewModel(application) {
    var id by input("")
    var name by input("")
    var assetPath by input("")
    var scale by input("1")
    var isPixel by input(false)
    var flipX by input(false)
    var singTime by input("8")
    var healthIcon by input("")
    var cameraX by input("0")
    var cameraY by input("0")
    var image by input<PickedAsset?>(null)
    var xml by input<PickedAsset?>(null)
    var animations by input(listOf<AnimRow>())

    override fun downloadName(): String = "${Ids.of(id).ifEmpty { "character" }}-character.zip"

    fun pickImage(uri: Uri) = pick(uri, setOf("png")) { asset ->
        image = asset
        if (id.isBlank()) id = Ids.of(asset.stem)
    }

    fun pickXml(uri: Uri) = pick(uri, setOf("xml")) { asset ->
        val atlas = SparrowAtlas.parse(asset.bytes.toString(Charsets.UTF_8))
        require(atlas.frames.isNotEmpty()) { "${asset.name} has no Sparrow frames." }
        xml = asset
        if (id.isBlank()) id = Ids.of(asset.stem)
        animations = CharacterGenerator.guessAll(CharacterGenerator.prefixes(atlas)).map {
            AnimRow(it.name, it.prefix, it.frameRate.toString(), it.looped)
        }
    }

    fun updateAnim(index: Int, transform: (AnimRow) -> AnimRow) {
        animations = animations.mapIndexed { i, row -> if (i == index) transform(row) else row }
    }

    fun addAnim() {
        animations = animations + AnimRow("", "")
    }

    fun removeAnim(index: Int) {
        animations = animations.filterIndexed { i, _ -> i != index }
    }

    override fun build(): Generated = CharacterGenerator.generate(
        CharacterSpec(
            id = id,
            name = name,
            assetPath = assetPath,
            scale = number(scale, "Scale"),
            isPixel = isPixel,
            flipX = flipX,
            singTime = number(singTime, "Sing time"),
            healthIcon = healthIcon,
            cameraX = number(cameraX, "Camera X"),
            cameraY = number(cameraY, "Camera Y"),
            animations = animations.map { row ->
                AnimSpec(
                    row.name.trim(),
                    row.prefix,
                    whole(row.frameRate, "Frame rate of ${row.name}"),
                    row.looped,
                    number(row.offsetX, "Offset X of ${row.name}"),
                    number(row.offsetY, "Offset Y of ${row.name}")
                )
            },
            image = image,
            xml = xml
        )
    )
}

@Composable
fun CharacterGenScreen(viewModel: CharacterGenViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Left
    val busy = viewModel.busy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Spritesheet", accent, 1)
            AssetPicker("Spritesheet XML (Sparrow)", viewModel.xml?.name, accent, arrayOf("text/xml", "application/xml", "*/*"), viewModel::pickXml, { viewModel.xml = null }, enabled = !busy)
            AssetPicker("Spritesheet image (.png)", viewModel.image?.name, accent, arrayOf("image/png"), viewModel::pickImage, { viewModel.image = null }, enabled = !busy)
            Text(
                "Picking the XML fills the animation list and guesses idle / sing / miss names from the frame prefixes.",
                style = MaterialTheme.typography.bodySmall,
                color = Funkin.Muted
            )
        }
        FunkinCard {
            SectionTitle("Character", accent, 2)
            FieldRow {
                FunkinTextField(viewModel.id, { viewModel.id = it }, "Id", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.name, { viewModel.name = it }, "Name", accent, Modifier.weight(1f), enabled = !busy)
            }
            FunkinTextField(viewModel.assetPath, { viewModel.assetPath = it }, "Asset path (default characters/<image>)", accent, enabled = !busy)
            FieldRow {
                NumberField(viewModel.scale, { viewModel.scale = it }, "Scale", accent, Modifier.weight(1f), enabled = !busy)
                NumberField(viewModel.singTime, { viewModel.singTime = it }, "Sing time", accent, Modifier.weight(1f), enabled = !busy)
            }
            FieldRow {
                NumberField(viewModel.cameraX, { viewModel.cameraX = it }, "Camera X", accent, Modifier.weight(1f), enabled = !busy)
                NumberField(viewModel.cameraY, { viewModel.cameraY = it }, "Camera Y", accent, Modifier.weight(1f), enabled = !busy)
            }
            FunkinTextField(viewModel.healthIcon, { viewModel.healthIcon = it }, "Health icon id (default = id)", accent, enabled = !busy)
            ToggleRow("Pixel art", viewModel.isPixel, { viewModel.isPixel = it }, accent, "No smoothing; scale 1 becomes 6.", enabled = !busy)
            ToggleRow("Flip X", viewModel.flipX, { viewModel.flipX = it }, accent, enabled = !busy)
        }
        FunkinCard {
            SectionTitle("Animations", accent, 3)
            if (viewModel.animations.isEmpty()) {
                Text("Pick the XML or add animations by hand.", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
            }
            viewModel.animations.forEachIndexed { index, row ->
                FileItem(
                    title = row.prefix.ifBlank { "New animation" },
                    accent = if (row.name.isBlank()) Funkin.Muted else accent,
                    onRemove = { viewModel.removeAnim(index) },
                    enabled = !busy,
                    tags = {
                        if (row.name.isBlank()) Tag("Skipped", Funkin.Muted) else Tag(row.name, accent)
                        if (row.looped) Tag("Loop", Funkin.Down)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldRow {
                            FunkinTextField(row.name, { v -> viewModel.updateAnim(index) { it.copy(name = v) } }, "Name (idle, singLEFT…)", accent, Modifier.weight(1f), enabled = !busy)
                            FunkinTextField(row.prefix, { v -> viewModel.updateAnim(index) { it.copy(prefix = v) } }, "Prefix", accent, Modifier.weight(1f), enabled = !busy)
                        }
                        FieldRow {
                            NumberField(row.frameRate, { v -> viewModel.updateAnim(index) { it.copy(frameRate = v) } }, "FPS", accent, Modifier.weight(1f), enabled = !busy, decimal = false)
                            NumberField(row.offsetX, { v -> viewModel.updateAnim(index) { it.copy(offsetX = v) } }, "Offset X", accent, Modifier.weight(1f), enabled = !busy)
                            NumberField(row.offsetY, { v -> viewModel.updateAnim(index) { it.copy(offsetY = v) } }, "Offset Y", accent, Modifier.weight(1f), enabled = !busy)
                        }
                        ToggleRow("Loop", row.looped, { v -> viewModel.updateAnim(index) { it.copy(looped = v) } }, accent, enabled = !busy)
                    }
                }
            }
            OutlineButton("Add animation", accent, viewModel::addAnim, icon = R.drawable.ic_add, enabled = !busy)
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 4,
            canConvert = viewModel.id.isNotBlank() && viewModel.animations.any { it.name.isNotBlank() },
            onConvert = viewModel::generate,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Generate",
            buttonText = "Generate character",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
