package com.brenninhotools.vconverter.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brenninhotools.vconverter.R
import com.brenninhotools.vconverter.ui.components.ChunkyButton
import com.brenninhotools.vconverter.ui.components.CodeFiles
import com.brenninhotools.vconverter.ui.components.FunkinCard
import com.brenninhotools.vconverter.ui.components.OutputFiles
import com.brenninhotools.vconverter.ui.components.SaveActions
import com.brenninhotools.vconverter.ui.components.ReportView
import com.brenninhotools.vconverter.ui.components.SectionTitle
import com.brenninhotools.vconverter.ui.components.StatusBanner

private val CODE_EXTENSIONS = setOf("hxc", "hx", "lua", "json", "txt", "xml")

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
                val code = output.files
                    .filter { it.bytes == null && it.name.substringAfterLast('.').lowercase() in CODE_EXTENSIONS }
                    .map { it.relativePath to it.content }
                    .ifEmpty { listOfNotNull(output.preview?.let { "Preview" to it }) }
                CodeFiles(code, accent)
                OutputFiles(output.files.map { it.relativePath }, accent)
                SaveActions(viewModel, accent, modFolderName, onSave)
                val launcher = com.brenninhotools.vconverter.ui.playtest.LocalPlayTest.current
                if (launcher != null && output.files.any { playable(it.relativePath) }) {
                    com.brenninhotools.vconverter.ui.components.OutlineButton("PlayTest the result", accent, onClick = {
                        launcher.files(output.files.map { it.relativePath to (it.bytes ?: it.content.toByteArray()) }, "$title result")
                    })
                }
            }
        }
    }
}

private val playableChart = Regex("(^|/)data/(songs/[^/]+/[^/]+-chart|[^/]+/[^/]+)\\.json$|(^|/)songs/[^/]+/charts/[^/]+\\.json$", RegexOption.IGNORE_CASE)
private val notCharts = Regex("(^|/)data/(characters|stages|levels|weeks|events|notes|config|dialogue)/", RegexOption.IGNORE_CASE)

private fun playable(path: String): Boolean = playableChart.containsMatchIn(path) && !notCharts.containsMatchIn(path)
