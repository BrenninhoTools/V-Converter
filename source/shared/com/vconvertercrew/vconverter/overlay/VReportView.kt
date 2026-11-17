package com.vconvertercrew.vconverter.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vconvertercrew.vconverter.core.Report
import com.vconvertercrew.vconverterui.VReport
import com.vconvertercrew.vconverterui.VReportSection
import com.vconvertercrew.vconverterui.VStat

@Composable
fun ReportView(report: Report, accent: Color, modifier: Modifier = Modifier) {
    val stats = remember(report) { report.stats.map { VStat(it.value, it.label) } }
    val sections = remember(report) { report.sections.map { VReportSection(it.title, it.lines) } }
    VReport(stats, report.warnings, sections, accent, modifier)
}
