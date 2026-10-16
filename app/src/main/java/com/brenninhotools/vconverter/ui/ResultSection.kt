package com.brenninhotools.vconverter.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.components.ChunkyButton
import com.brenninhotools.vconverter.ui.components.CodePreview
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.OutputFiles
import com.brenninhotools.vconverter.ui.components.SaveActions
import com.brenninhotools.vconverter.ui.components.ReportView
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.StatusBanner

@Composable
fun ConvertSection(
    viewModel: BaseConverterViewModel,
    accent: Color,
    step: Int,
    canConvert: Boolean,
    onConvert: () -> Unit,
    onSave: () -> Unit,
    modFolderName: String?,
    title: String = "Convert",
    buttonText: String = "Convert to V-Slice",
    @DrawableRes buttonIcon: Int = R.drawable.ic_swap
) {
    FunkinCard(accent = if (viewModel.output != null) accent else null) {
        SectionTitle(title, accent, step)
        ChunkyButton(
            text = buttonText,
            accent = accent,
            icon = buttonIcon,
            enabled = canConvert && !viewModel.busy,
            loading = viewModel.busy && viewModel.output == null,
            onClick = onConvert
        )
        StatusBanner(viewModel.status, viewModel::dismissStatus)
        val output = viewModel.output
        if (output != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportView(output.report, accent)
                output.preview?.let { CodePreview(it) }
                OutputFiles(output.files.map { it.relativePath }, accent)
                SaveActions(viewModel, accent, modFolderName, onSave)
            }
        }
    }
}
