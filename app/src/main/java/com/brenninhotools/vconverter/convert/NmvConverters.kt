package com.brenninhotools.vconverter.convert

import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.nmv.NmvFormats
import com.brenninhotools.vconverter.nmv.NmvKind
import com.brenninhotools.vconverter.nmv.NmvPaths
import com.brenninhotools.vconverter.nmv.NmvScripts
import com.brenninhotools.vconverter.nmv.PsychToNmvWriter
import com.brenninhotools.vconverter.platform.ByteArrayInputStream
import com.brenninhotools.vconverter.platform.InputStream

enum class NmvTab { CHARTS, CHARACTERS, STAGES, WEEKS, SCRIPTS }

private val folderMarkers = listOf(
    "songs", "data", "images", "characters", "stages", "weeks", "scripts", "custom_events", "custom_notetypes", "music", "sounds", "shaders", "videos"
)

private fun splitRoot(path: String): Pair<String, String> {
    val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    val index = parts.indexOfFirst { it.lowercase() in folderMarkers }
    if (index < 0 || index == parts.size - 1) return parts.dropLast(1).joinToString("/") to parts.lastOrNull().orEmpty()
    return parts.take(index).joinToString("/") to parts.drop(index).joinToString("/")
}

private fun join(prefix: String, rel: String) = if (prefix.isEmpty()) rel else "$prefix/$rel"

class NmvSourceConverter(private val inner: FileConverter) : FileConverter() {
    override val noun: String get() = inner.noun
    override val engine: String = "NightmareVision"

    override fun downloadName(): String = inner.downloadName()

    private val warnings = LinkedHashSet<String>()

    private fun mapped(file: ImportedFile): Pair<ImportedFile, NmvKind>? {
        val (prefix, rel) = splitRoot(file.path)
        val mapping = NmvPaths.toPsych(rel) ?: return null
        val path = join(prefix, mapping.path)
        return ImportedFile(path.substringAfterLast('/'), path, file.fromArchive) { file.open() } to mapping.kind
    }

    override fun accept(file: ImportedFile): Boolean {
        val (target, _) = mapped(file) ?: return false
        return inner.accept(target)
    }

    private fun convertText(kind: NmvKind, file: ImportedFile, text: String): String {
        val name = file.name.lowercase()
        val ext = name.substringAfterLast('.', "")
        val result = when {
            kind == NmvKind.CHART || (kind == NmvKind.COPY && ext == "json" && NmvFormats.isChart(text) && text.contains("\"nmv")) -> NmvFormats.chartToPsych(text).let { it.text to it.warnings }
            kind == NmvKind.CHARACTER -> NmvFormats.characterToPsych(text).let { it.text to it.warnings }
            kind == NmvKind.STAGE || (kind == NmvKind.COPY && ext == "json" && text.contains("\"stageObjects\"")) -> NmvFormats.stageToPsych(text).let { it.text to it.warnings }
            kind == NmvKind.HSCRIPT || ext in setOf("hx", "hxs", "hscript") -> NmvScripts.nmvHxToPsych(text).let { r -> r.source to r.issues.map { it.message } }
            kind == NmvKind.META -> NmvFormats.metaToPack(text) to emptyList()
            else -> text to emptyList()
        }
        result.second.forEach { warnings += "${file.name}: $it" }
        return result.first
    }

    override fun classify(file: ImportedFile, text: String): PickedText {
        val (target, kind) = mapped(file) ?: throw IllegalArgumentException("not a NightmareVision file this tab can use")
        val converted = convertText(kind, target, text)
        val bytes = converted.encodeToByteArray()
        val virtual = ImportedFile(target.name.let { if (kind == NmvKind.HSCRIPT && !it.endsWith(".hx")) it.substringBeforeLast('.') + ".hx" else it }, target.path, file.fromArchive) { ByteArrayInputStream(bytes) }
        return inner.classify(virtual, converted)
    }

    override fun canConvert(items: List<PickedText>): Boolean = inner.canConvert(items)

    override fun build(items: List<PickedText>): ConvertedOutput {
        val out = inner.build(items)
        if (warnings.isEmpty()) return out
        val notes = ReportBuilder()
        warnings.forEach { notes.warn(it) }
        warnings.clear()
        return ConvertedOutput(out.files, out.report + notes.build(), out.preview)
    }
}

private class CollectWriter : ModWriter {
    val files = mutableListOf<OutputFile>()

    override fun writeText(path: String, text: String) {
        files += OutputFile.of(path, text, null)
    }

    override fun copy(path: String, source: () -> InputStream) {
        files += OutputFile.of(path, null, source().use { it.readBytes() })
    }
}

class NmvTargetConverter(private val inner: FileConverter) : FileConverter() {
    override val noun: String get() = inner.noun
    override val engine: String get() = inner.engine

    override fun downloadName(): String = inner.downloadName().replace("-psych", "-nmv").let { if (it == inner.downloadName()) it.substringBeforeLast('.') + "-nmv.zip" else it }

    override fun read(file: ImportedFile): String = inner.read(file)

    override fun accept(file: ImportedFile): Boolean = inner.accept(file)

    override fun classify(file: ImportedFile, text: String): PickedText = inner.classify(file, text)

    override fun canConvert(items: List<PickedText>): Boolean = inner.canConvert(items)

    override fun build(items: List<PickedText>): ConvertedOutput {
        val out = inner.build(items)
        val collect = CollectWriter()
        val nmv = PsychToNmvWriter(collect)
        out.files.forEach { it.writeTo(nmv) }
        val preview = collect.files.firstOrNull { it.bytes == null }?.content ?: out.preview
        return ConvertedOutput(collect.files, out.report + nmv.report.build(), preview)
    }
}

class PsychPassConverter(private val tab: NmvTab) : FileConverter() {
    override val noun: String = when (tab) {
        NmvTab.CHARTS -> "chart"
        NmvTab.CHARACTERS -> "character"
        NmvTab.STAGES -> "stage"
        NmvTab.WEEKS -> "week"
        NmvTab.SCRIPTS -> "script"
    }
    override val engine: String = "Psych Engine"

    override fun downloadName(): String = noun + "s-psych.zip"

    override fun accept(file: ImportedFile): Boolean = when (tab) {
        NmvTab.SCRIPTS -> file.extension in setOf("lua", "hx", "hxs", "hscript")
        NmvTab.STAGES -> file.extension in setOf("json", "lua", "hx")
        NmvTab.CHARACTERS -> file.extension in setOf("json", "lua", "hx")
        else -> file.extension == "json"
    }

    private fun json(text: String): JSONObject? = runCatching { JSONObject(text.trim().removePrefix("﻿")) }.getOrNull()

    override fun classify(file: ImportedFile, text: String): PickedText {
        val (_, rel) = splitRoot(file.path)
        val top = rel.substringBefore('/').lowercase()
        val hasFolder = rel.contains('/')
        val name = file.name
        val stem = name.substringBeforeLast('.')
        val ext = file.extension
        val root = json(text)
        val path: String
        val kind: String
        when (tab) {
            NmvTab.CHARTS -> {
                require(root != null) { "not valid JSON" }
                val song = root.optJSONObject("song") ?: root
                if (stem.equals("events", true)) {
                    kind = "events"
                    path = if (hasFolder && top == "data") rel else "data/${NmvFormats.sanitize(song.optString("song").ifBlank { "song" })}/events.json"
                } else {
                    require(song.opt("notes") is JSONArray) { "not a Psych chart (no notes)" }
                    kind = "chart"
                    val id = NmvFormats.sanitize(song.optString("song").ifBlank { stem })
                    path = if (hasFolder && top == "data") rel else "data/$id/" + if (stem.equals(id, true) || stem.startsWith("$id-", true)) name else "$id-$stem.json"
                }
            }
            NmvTab.CHARACTERS -> {
                if (ext == "json") {
                    require(root?.opt("animations") is JSONArray) { "not a Psych character (no animations)" }
                    kind = "character"
                } else kind = "script"
                path = if (hasFolder && top == "characters") rel else "characters/$name"
            }
            NmvTab.STAGES -> {
                if (ext == "json") {
                    require(root != null && NmvFormats.isStage(text)) { "not a Psych stage" }
                    kind = "stage"
                } else kind = "script"
                path = if (hasFolder && top == "stages") rel else "stages/$name"
            }
            NmvTab.WEEKS -> {
                require(root != null) { "not valid JSON" }
                if (root.opt("songs") is JSONArray) {
                    kind = "week"
                    path = if (hasFolder && top == "weeks") rel else "weeks/$name"
                } else {
                    require(root.has("image")) { "not a Psych week or menu character" }
                    kind = "menu character"
                    path = if (hasFolder && rel.startsWith("images/menucharacters/", true)) rel else "images/menucharacters/$name"
                }
            }
            NmvTab.SCRIPTS -> {
                kind = "script"
                path = if (hasFolder && top in setOf("data", "scripts", "stages", "characters", "custom_events", "custom_notetypes")) rel else "scripts/$name"
            }
        }
        return PickedText(path, name, path, text, kind, path)
    }

    override fun build(items: List<PickedText>): ConvertedOutput {
        val report = ReportBuilder()
        report.stat(noun.replaceFirstChar { it.uppercaseChar() } + "s", items.size)
        return ConvertedOutput(items.map { out(it.path, it.text) }, report.build(), items.firstOrNull()?.text)
    }
}

object NmvConverters {
    fun create(from: String, to: String, tab: NmvTab): FileConverter {
        fun psychToVSlice() = when (tab) {
            NmvTab.CHARTS -> PsychChartsConverter()
            NmvTab.CHARACTERS -> PsychCharactersConverter()
            NmvTab.STAGES -> PsychStagesConverter()
            NmvTab.WEEKS -> PsychWeeksConverter()
            NmvTab.SCRIPTS -> PsychScriptsConverter()
        }
        fun psychToCodename() = when (tab) {
            NmvTab.CHARTS -> PsychToCodenameChartsConverter()
            NmvTab.CHARACTERS -> PsychToCodenameCharactersConverter()
            NmvTab.STAGES -> PsychToCodenameStagesConverter()
            NmvTab.WEEKS -> PsychToCodenameWeeksConverter()
            NmvTab.SCRIPTS -> PsychToCodenameScriptsConverter()
        }
        fun vsliceToPsych() = when (tab) {
            NmvTab.CHARTS -> ReverseChartsConverter()
            NmvTab.CHARACTERS -> ReverseCharactersConverter()
            NmvTab.STAGES -> ReverseStagesConverter()
            NmvTab.WEEKS -> ReverseWeeksConverter()
            NmvTab.SCRIPTS -> ReverseScriptsConverter()
        }
        fun codenameToPsych() = when (tab) {
            NmvTab.CHARTS -> CodenameToPsychChartsConverter()
            NmvTab.CHARACTERS -> CodenameToPsychCharactersConverter()
            NmvTab.STAGES -> CodenameToPsychStagesConverter()
            NmvTab.WEEKS -> CodenameToPsychWeeksConverter()
            NmvTab.SCRIPTS -> CodenameToPsychScriptsConverter()
        }
        return when (from to to) {
            "nmv" to "psych" -> NmvSourceConverter(PsychPassConverter(tab))
            "nmv" to "vslice" -> NmvSourceConverter(psychToVSlice())
            "nmv" to "codename" -> NmvSourceConverter(psychToCodename())
            "psych" to "nmv" -> NmvTargetConverter(PsychPassConverter(tab))
            "vslice" to "nmv" -> NmvTargetConverter(vsliceToPsych())
            "codename" to "nmv" -> NmvTargetConverter(codenameToPsych())
            else -> throw IllegalArgumentException("No converter from $from to $to")
        }
    }
}
