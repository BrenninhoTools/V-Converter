package com.brenninhotools.vconverter.core

data class ReportStat(val label: String, val value: String)

data class ReportSection(val title: String, val lines: List<String>)

data class Report(
    val stats: List<ReportStat> = emptyList(),
    val sections: List<ReportSection> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    operator fun plus(other: Report) = Report(
        stats = stats + other.stats,
        sections = sections + other.sections,
        warnings = warnings + other.warnings
    )
}

class ReportBuilder {
    private val stats = mutableListOf<ReportStat>()
    private val sections = mutableListOf<ReportSection>()
    private val warnings = LinkedHashSet<String>()

    fun stat(label: String, value: Any) {
        stats += ReportStat(label, value.toString())
    }

    fun section(title: String, lines: List<String>) {
        if (lines.isNotEmpty()) sections += ReportSection(title, lines)
    }

    fun warn(message: String) {
        warnings += message
    }

    fun build() = Report(stats.toList(), sections.toList(), warnings.toList())
}
