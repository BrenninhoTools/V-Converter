package com.brenninhotools.vconverter.ui.mod

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
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
fun ModScreen(
    viewModel: ModViewModel,
    modFolderName: String?,
    onConvert: () -> Unit,
    intro: String = "Convert a whole Psych Engine mod at once: songs, characters, stages, weeks, Lua/HScript scripts, images and audio.",
    infoNote: String = "Written to _polymod_meta.json. pack.png becomes the mod icon.",
    convertNote: String = "Download the converted mod as a .zip (unzip it into V-Slice's mods folder), or write it straight into an empty mod folder."
) {
    val accent = Funkin.Pink
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
            SectionTitle("Psych mod", accent, 1)
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
                hint = "Pick the mod's folder inside Psych's mods directory.",
                accent = Funkin.Purple,
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
                if (scan.placeholders.isNotEmpty()) {
                    Text(
                        "${scan.placeholders.size} empty placeholder folder(s) will be left out: ${scan.placeholders.keys.joinToString { "$it/" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Funkin.Warning
                    )
                }
                ReportView(
                    Report(
                        stats = listOf(
                            ReportStat("Songs", scan.songs.size.toString()),
                            ReportStat("Characters", scan.characters.size.toString()),
                            ReportStat("Stages", scan.stages.size.toString()),
                            ReportStat("Weeks", scan.weeks.size.toString()),
                            ReportStat("Assets", scan.assets.size.toString()),
                            ReportStat("Scripts", scan.scripts.size.toString())
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
                            Tag("${song.id} · ${song.charts.keys.joinToString("/")}", Funkin.Down)
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (current.total > 0) {
                        LinearProgressIndicator(
                            progress = { current.done.toFloat() / current.total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = accent,
                            trackColor = Funkin.SurfaceHigh
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = accent,
                            trackColor = Funkin.SurfaceHigh
                        )
                    }
                    Row {
                        Text(
                            current.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Funkin.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (current.total > 0) {
                            Text("${current.done}/${current.total}", style = MaterialTheme.typography.bodySmall, color = Funkin.Muted)
                        }
                    }
                }
            }
            StatusBanner(viewModel.status, viewModel::dismissStatus)
            viewModel.result?.let { ReportView(it, accent) }
        }
    }
}
