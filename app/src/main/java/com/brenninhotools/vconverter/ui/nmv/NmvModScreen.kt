package com.brenninhotools.vconverter.ui.nmv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.overlay.ModScannerPanel
import com.brenninhotools.vconverter.overlay.ScannerColors
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportStat
import com.brenninhotools.vconverter.ui.components.SaveActions
import com.brenninhotools.vconverter.ui.components.DropZone
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.FunkinTextField
import com.brenninhotools.vconverter.ui.components.ReportView
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.StatusBanner
import com.brenninhotools.vconverter.ui.components.Tag
import com.brenninhotools.vconverter.ui.theme.Funkin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NmvModScreen(
    viewModel: NmvModViewModel,
    modFolderName: String?,
    onConvert: () -> Unit,
    intro: String,
    infoNote: String,
    convertNote: String
) {
    val accent = Funkin.Purple
    val busy = viewModel.busy
    val progress by viewModel.progress.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.openFolder(uri)
    }
    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openArchive(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FunkinCard {
            SectionTitle("NightmareVision mod", accent, 1)
            Text(
                intro,
                style = MaterialTheme.typography.bodySmall,
                color = Funkin.Muted
            )
            DropZone(
                title = "Import .zip / .rar / .7z",
                hint = "The mod can sit inside subfolders of the archive; it is found automatically.",
                accent = accent,
                icon = R.drawable.ic_archive,
                enabled = !busy,
                onClick = { archivePicker.launch(arrayOf("*/*")) }
            )
            DropZone(
                title = "Open mod folder",
                hint = "Pick the mod's folder inside NightmareVision's content directory.",
                accent = Funkin.Lavender,
                icon = R.drawable.ic_folder,
                enabled = !busy,
                onClick = { folderPicker.launch(null) }
            )
            val loaded = viewModel.mod
            if (loaded != null && progress == null) {
                val scan = loaded.scan
                Text(loaded.sourceName, style = MaterialTheme.typography.titleSmall, color = Funkin.Text)
                if (scan.root.isNotEmpty()) {
                    Text("Mod root: ${scan.root}", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
                }
                ReportView(
                    Report(
                        stats = listOf(
                            ReportStat("Songs", scan.songs.size.toString()),
                            ReportStat("Characters", scan.characters.size.toString()),
                            ReportStat("Stages", scan.stages.size.toString()),
                            ReportStat("Weeks", scan.weeks.size.toString()),
                            ReportStat("Assets", scan.assets.toString()),
                            ReportStat("Scripts", scan.scripts.toString())
                        )
                    ),
                    accent
                )
                if (scan.songs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        scan.songs.take(24).forEach { song ->
                            Tag(song, Funkin.Down)
                        }
                        if (scan.songs.size > 24) Tag("+${scan.songs.size - 24} more", Funkin.Muted)
                    }
                }
            }
        }

        if (viewModel.mod != null) {
            FunkinCard {
                SectionTitle("Mod info", accent, 2)
                FunkinTextField(viewModel.title, { viewModel.title = it }, "Title", accent, enabled = !busy)
                FunkinTextField(viewModel.description, { viewModel.description = it }, "Description", accent, enabled = !busy)
                FunkinTextField(viewModel.author, { viewModel.author = it }, "Author", accent, enabled = !busy)
                Text(
                    infoNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = Funkin.Muted
                )
            }
        }

        FunkinCard(accent = if (viewModel.result != null) accent else null) {
            SectionTitle("Convert", accent, 3)
            Text(
                convertNote,
                style = MaterialTheme.typography.bodySmall,
                color = Funkin.Muted
            )
            SaveActions(
                viewModel = viewModel,
                accent = accent,
                modFolderName = modFolderName,
                onSave = onConvert,
                saveLabel = if (modFolderName != null) "Convert into $modFolderName" else "Convert into a mod folder"
            )
            progress?.let { current ->
                ModScannerPanel(
                    progress = current,
                    colors = ScannerColors(accent, Funkin.Background, Funkin.SurfaceHigh, Funkin.Text, Funkin.Muted),
                    onCancel = if (viewModel.cancellable) viewModel::cancelConversion else null,
                    cancelling = viewModel.cancelling
                )
            }
            StatusBanner(viewModel.status, viewModel::dismissStatus)
            viewModel.result?.let { ReportView(it, accent) }
        }
    }
}
