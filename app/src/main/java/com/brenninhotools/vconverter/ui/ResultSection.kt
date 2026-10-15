package com.brenninhotools.vconverter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.components.ChunkyButton
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.OutputFiles
import com.brenninhotools.vconverter.ui.components.ReportView
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.StatusBanner
import com.brenninhotools.vconverter.ui.theme.Funkin

@Composable
fun ConvertSection(
    viewModel: BaseConverterViewModel,
    accent: Color,
    step: Int,
    canConvert: Boolean,
    onConvert: () -> Unit,
    onSave: () -> Unit,
    modFolderName: String?
) {
    FunkinCard(accent = if (viewModel.output != null) accent else null) {
        SectionTitle("Convert", accent, step)
        ChunkyButton(
            text = "Convert to V-Slice",
            accent = accent,
            icon = R.drawable.ic_swap,
            enabled = canConvert && !viewModel.busy,
            loading = viewModel.busy && viewModel.output == null,
            onClick = onConvert
        )
        StatusBanner(viewModel.status, viewModel::dismissStatus)
        val output = viewModel.output
        if (output != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportView(output.report, accent)
                Text("OUTPUT", style = MaterialTheme.typography.labelMedium, color = Funkin.Muted)
                OutputFiles(output.files.map { it.relativePath })
                ChunkyButton(
                    text = if (modFolderName != null) "Save to $modFolderName" else "Choose mod folder & save",
                    accent = Funkin.Up,
                    icon = R.drawable.ic_save,
                    enabled = !viewModel.busy,
                    loading = viewModel.busy,
                    onClick = onSave
                )
            }
        }
    }
}
