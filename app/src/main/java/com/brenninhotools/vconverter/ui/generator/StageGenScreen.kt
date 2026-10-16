package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.generator.PropSpec
import com.brenninhotools.vconverter.generator.StageGenerator
import com.brenninhotools.vconverter.generator.StageSpec
import com.brenninhotools.vconverter.io.DocumentIO
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.StatusMessage
import com.brenninhotools.vconverter.ui.components.DropZone
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PropRow(
    val name: String,
    val image: PickedAsset? = null,
    val x: String = "-600",
    val y: String = "-300",
    val scrollX: String = "1",
    val scrollY: String = "1",
    val scale: String = "1",
    val front: Boolean = false
)

class StageGenViewModel(application: Application) : GeneratorViewModel(application) {
    var id by input("")
    var name by input("")
    var zoom by input("1.0")
    var isPixel by input(false)
    var background by input("")
    var props by input(listOf<PropRow>())

    override fun downloadName(): String = "${Ids.of(id).ifEmpty { "stage" }}-stage.zip"

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runTask {
            try {
                val assets = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val fileName = DocumentIO.displayName(context, uri)
                        require(fileName.endsWith(".png", ignoreCase = true)) { "$fileName is not a .png image." }
                        PickedAsset(fileName, DocumentIO.readBytes(context, uri))
                    }
                }
                props = props + assets.map { PropRow(Ids.of(it.stem), it) }
                status = null
            } catch (e: Exception) {
                status = StatusMessage.Error(e.message ?: "Could not read the images.")
            }
        }
    }

    fun addEmpty() {
        props = props + PropRow("prop${props.size + 1}")
    }

    fun update(index: Int, transform: (PropRow) -> PropRow) {
        props = props.mapIndexed { i, row -> if (i == index) transform(row) else row }
    }

    fun remove(index: Int) {
        props = props.filterIndexed { i, _ -> i != index }
    }

    override fun build(): Generated = StageGenerator.generate(
        StageSpec(
            id = id,
            name = name,
            cameraZoom = number(zoom, "Camera zoom"),
            isPixel = isPixel,
            backgroundColor = background,
            props = props.map { row ->
                PropSpec(
                    name = row.name,
                    image = row.image,
                    x = number(row.x, "X of ${row.name}"),
                    y = number(row.y, "Y of ${row.name}"),
                    scrollX = number(row.scrollX, "Scroll X of ${row.name}"),
                    scrollY = number(row.scrollY, "Scroll Y of ${row.name}"),
                    scale = number(row.scale, "Scale of ${row.name}"),
                    front = row.front
                )
            }
        )
    )
}

@Composable
fun StageGenScreen(viewModel: StageGenViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Up
    val busy = viewModel.busy
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.addImages(it) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Stage", accent, 1)
            FieldRow {
                FunkinTextField(viewModel.id, { viewModel.id = it }, "Id", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.name, { viewModel.name = it }, "Name", accent, Modifier.weight(1f), enabled = !busy)
            }
            FieldRow {
                NumberField(viewModel.zoom, { viewModel.zoom = it }, "Camera zoom", accent, Modifier.weight(1f), enabled = !busy)
                FunkinTextField(viewModel.background, { viewModel.background = it }, "Solid color (#hex)", accent, Modifier.weight(1f), enabled = !busy)
            }
            ToggleRow("Pixel stage", viewModel.isPixel, { viewModel.isPixel = it }, accent, "Turns off smoothing on every prop.", enabled = !busy)
            Text(
                "Characters use the base game's positions (like mainStage). Fine-tune them later in V-Slice's Stage Editor.",
                style = MaterialTheme.typography.bodySmall,
                color = Funkin.Muted
            )
        }
        FunkinCard {
            SectionTitle("Props", accent, 2)
            DropZone(
                title = "Add stage images",
                hint = "Each .png becomes a prop, back to front in the order you add them.",
                accent = accent,
                icon = R.drawable.ic_tab_stage,
                enabled = !busy,
                onClick = { picker.launch(arrayOf("image/png")) }
            )
            viewModel.props.forEachIndexed { index, row ->
                FileItem(
                    title = row.image?.name ?: row.name.ifBlank { "Prop" },
                    accent = accent,
                    onRemove = { viewModel.remove(index) },
                    enabled = !busy,
                    tags = {
                        Tag(if (row.front) "In front" else "Behind characters", if (row.front) Funkin.Warning else accent)
                        if (row.image == null) Tag("No image", Funkin.Muted)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FunkinTextField(row.name, { v -> viewModel.update(index) { it.copy(name = v) } }, "Prop name", accent, enabled = !busy)
                        FieldRow {
                            NumberField(row.x, { v -> viewModel.update(index) { it.copy(x = v) } }, "X", accent, Modifier.weight(1f), enabled = !busy)
                            NumberField(row.y, { v -> viewModel.update(index) { it.copy(y = v) } }, "Y", accent, Modifier.weight(1f), enabled = !busy)
                            NumberField(row.scale, { v -> viewModel.update(index) { it.copy(scale = v) } }, "Scale", accent, Modifier.weight(1f), enabled = !busy)
                        }
                        FieldRow {
                            NumberField(row.scrollX, { v -> viewModel.update(index) { it.copy(scrollX = v) } }, "Scroll X", accent, Modifier.weight(1f), enabled = !busy)
                            NumberField(row.scrollY, { v -> viewModel.update(index) { it.copy(scrollY = v) } }, "Scroll Y", accent, Modifier.weight(1f), enabled = !busy)
                        }
                        ToggleRow("In front of characters", row.front, { v -> viewModel.update(index) { it.copy(front = v) } }, accent, enabled = !busy)
                    }
                }
            }
            OutlineButton("Add prop without image", accent, viewModel::addEmpty, icon = R.drawable.ic_add, enabled = !busy)
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 3,
            canConvert = viewModel.id.isNotBlank(),
            onConvert = viewModel::generate,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Generate",
            buttonText = "Generate stage",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
