package com.brenninhotools.vconverter.script

import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder

data class ScriptSource(val path: String, val text: String, val target: ScriptTarget? = null)

data class ScriptFile(val path: String, val content: String)

data class ScriptBundle(val files: List<ScriptFile>, val report: Report, val converted: Int)

object ScriptConverter {
    const val OUTPUT_FOLDER = "scripts/psych"

    fun detectTarget(path: String): ScriptTarget {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val stem = parts.lastOrNull()?.substringBeforeLast('.') ?: "script"
        val lower = parts.map { it.lowercase() }
        val dataIndex = lower.lastIndexOf("data")
        return when {
            "custom_events" in lower -> ScriptTarget(ScriptScope.EVENT, stem)
            "custom_notetypes" in lower -> ScriptTarget(ScriptScope.NOTE_TYPE, stem)
            "stages" in lower -> ScriptTarget(ScriptScope.STAGE, stem)
            dataIndex >= 0 && dataIndex + 2 < parts.size -> ScriptTarget(ScriptScope.SONG, parts[dataIndex + 1].lowercase())
            else -> ScriptTarget(ScriptScope.GLOBAL)
        }
    }

    fun slug(text: String): String {
        val cleaned = text.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return cleaned.ifEmpty { "Mod" }.let { if (it.first().isDigit()) "M$it" else it }
    }

    fun runtimeClass(modSlug: String) = "PsychLuaRuntime_$modSlug"

    fun runtimeId(modSlug: String) = "psych-lua-runtime-${modSlug.lowercase()}"

    fun runtimeFiles(modSlug: String, noteTypes: Map<String, String>): List<ScriptFile> {
        val noteTypeArray = noteTypes.entries.joinToString(", ", "[", "]") { (kind, name) ->
            "[${LuaToHScript.hxString(kind)}, ${LuaToHScript.hxString(name)}]"
        }
        val runtime = resource("PsychLuaRuntime.hxc")
            .replace("__CLASS__", runtimeClass(modSlug))
            .replace("__MODULE_ID__", runtimeId(modSlug))
            .replace("__NOTE_TYPES__", noteTypeArray)
        val event = resource("PsychEventKind.hxc")
            .replace("__CLASS__", "PsychEventKind_$modSlug")
            .replace("__RUNTIME_ID__", runtimeId(modSlug))
        return listOf(
            ScriptFile("$OUTPUT_FOLDER/${runtimeClass(modSlug)}.hxc", runtime),
            ScriptFile("$OUTPUT_FOLDER/PsychEventKind_$modSlug.hxc", event)
        )
    }

    fun noteTypeMap(names: Collection<String>): Map<String, String> =
        names.filter { it.isNotBlank() }.associateBy { PsychToVSliceChart.noteKindFor(it) }

    fun convert(modSlug: String, sources: List<ScriptSource>, noteTypes: Map<String, String>): ScriptBundle {
        val report = ReportBuilder()
        val files = mutableListOf<ScriptFile>()
        val usedNames = HashSet<String>()
        var converted = 0
        var hooks = 0
        val apiUsed = sortedSetOf<String>()

        sources.forEach { source ->
            val target = source.target ?: detectTarget(source.path)
            val stem = source.path.substringAfterLast('/').substringBeforeLast('.')
            val ext = source.path.substringAfterLast('.', "").lowercase()
            val scopeName = when (target.scope) {
                ScriptScope.GLOBAL -> "Global"
                ScriptScope.SONG -> "Song"
                ScriptScope.STAGE -> "Stage"
                ScriptScope.EVENT -> "Event"
                ScriptScope.NOTE_TYPE -> "NoteType"
            }
            val base = "Psych${if (ext == "lua") "Lua" else "Hx"}_${modSlug}_${scopeName}_${slug(if (target.scope == ScriptScope.SONG) target.key + "_" + stem else stem)}"
            var className = base
            var n = 2
            while (!usedNames.add(className.lowercase())) className = base + "_" + n++
            val moduleId = "psych-" + className.removePrefix("Psych").lowercase().replace('_', '-')
            val generated = try {
                if (ext == "lua") {
                    LuaToHScript(className, moduleId, runtimeId(modSlug), target).convert(source.text)
                } else {
                    PsychHScriptWrapper(className, moduleId, runtimeId(modSlug), target).convert(source.text)
                }
            } catch (e: Exception) {
                report.warn("${source.path}: could not be converted (${e.message}).")
                return@forEach
            }
            val label = "${source.path} · ${target.scope.label}${if (target.key.isNotEmpty()) " \"${target.key}\"" else ""}"
            if (generated.source.isEmpty()) {
                report.warn("${source.path}: ${generated.issues.firstOrNull()?.message ?: "could not be converted"}")
                return@forEach
            }
            files += ScriptFile("$OUTPUT_FOLDER/$className.hxc", generated.source)
            converted++
            hooks += generated.callbacks.size
            apiUsed += generated.apiCalls
            val lines = mutableListOf("Callbacks: ${generated.callbacks.joinToString().ifEmpty { "none" }}")
            generated.issues.take(25).forEach { lines += if (it.line > 0) "Line ${it.line}: ${it.message}" else it.message }
            if (generated.issues.size > 25) lines += "…and ${generated.issues.size - 25} more notes"
            report.section(label, lines)
        }

        if (converted > 0 || noteTypes.isNotEmpty()) files += runtimeFiles(modSlug, noteTypes)
        report.stat("Scripts", converted)
        report.stat("Callbacks", hooks)
        report.stat("API calls", apiUsed.size)
        val unsupported = apiUsed.filter { it in PsychApi.notImplemented }
        if (unsupported.isNotEmpty()) {
            report.warn("These Psych functions have no V-Slice equivalent and do nothing: ${unsupported.joinToString()}.")
        }
        report.section(
            "How it runs",
            listOf(
                "Each script became a V-Slice Module that only runs in its song, stage, event or note type.",
                "${runtimeClass(modSlug)} implements the Psych Lua API (sprites, texts, tweens, timers, sounds, properties) on top of V-Slice.",
                "Psych events without a V-Slice equivalent are kept in charts as \"PsychEvent\" and reach onEvent in your scripts."
            )
        )
        return ScriptBundle(files, report.build(), converted)
    }

    private fun resource(name: String): String =
        ScriptConverter::class.java.getResourceAsStream("/vconverter/$name")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Missing bundled template $name")
}
