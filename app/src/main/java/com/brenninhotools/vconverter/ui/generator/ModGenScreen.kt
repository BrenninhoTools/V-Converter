package com.brenninhotools.vconverter.ui.generator

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.generator.Generated
import com.brenninhotools.vconverter.generator.ModGenerator
import com.brenninhotools.vconverter.generator.ModSpec
import com.brenninhotools.vconverter.generator.PickedAsset
import com.brenninhotools.vconverter.mod.ZipModWriter
import com.brenninhotools.vconverter.ui.ConvertSection
import com.brenninhotools.vconverter.ui.components.AssetPicker
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.ToggleRow
import com.brenninhotools.vconverter.ui.theme.Funkin

class ModGenViewModel(application: Application) : GeneratorViewModel(application) {
    var title by input("")
    var description by input("")
    var author by input("")
    var version by input("1.0.0")
    var homepage by input("")
    var license by input("")
    var icon by input<PickedAsset?>(null)
    var starterModule by input(true)

    override fun downloadName(): String = ZipModWriter.folderName(title).replace(' ', '-') + ".zip"

    override fun zipPrefix(): String = ZipModWriter.folderName(title)

    override fun build(): Generated = ModGenerator.generate(ModSpec(title, description, author, version.trim(), homepage, license, icon, starterModule))

    fun pickIcon(uri: android.net.Uri) = pick(uri, setOf("png")) { icon = it }
}

@Composable
fun ModGenScreen(viewModel: ModGenViewModel, modFolderName: String?, onSave: () -> Unit) {
    val accent = Funkin.Pink
    val busy = viewModel.busy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("Mod info", accent, 1)
            FunkinTextField(viewModel.title, { viewModel.title = it }, "Title", accent, enabled = !busy)
            FunkinTextField(viewModel.description, { viewModel.description = it }, "Description", accent, enabled = !busy)
            FunkinTextField(viewModel.author, { viewModel.author = it }, "Authors (comma separated)", accent, enabled = !busy)
            FunkinTextField(viewModel.version, { viewModel.version = it }, "Mod version", accent, enabled = !busy)
            FunkinTextField(viewModel.homepage, { viewModel.homepage = it }, "Homepage (optional)", accent, enabled = !busy)
            FunkinTextField(viewModel.license, { viewModel.license = it }, "License (optional)", accent, enabled = !busy)
        }
        FunkinCard {
            SectionTitle("Extras", accent, 2)
            AssetPicker(
                label = "Mod icon (.png, 150×150)",
                fileName = viewModel.icon?.name,
                accent = accent,
                mimeTypes = arrayOf("image/png"),
                onPick = viewModel::pickIcon,
                onClear = { viewModel.icon = null },
                icon = R.drawable.ic_add,
                enabled = !busy
            )
            ToggleRow(
                label = "Starter module script",
                checked = viewModel.starterModule,
                onChange = { viewModel.starterModule = it },
                accent = accent,
                hint = "Adds scripts/modules/<Name>Module.hxc ready to edit.",
                enabled = !busy
            )
            Text(
                "Download gives you a zip with a ready mod folder: unzip it into V-Slice's mods directory.",
                style = MaterialTheme.typography.bodySmall,
                color = Funkin.Muted
            )
        }
        ConvertSection(
            viewModel = viewModel,
            accent = accent,
            step = 3,
            canConvert = viewModel.title.isNotBlank(),
            onConvert = viewModel::generate,
            onSave = onSave,
            modFolderName = modFolderName,
            title = "Generate",
            buttonText = "Generate mod",
            buttonIcon = R.drawable.ic_magic
        )
    }
}
