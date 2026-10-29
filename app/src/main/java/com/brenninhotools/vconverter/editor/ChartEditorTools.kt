package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameChartsConverter
import com.brenninhotools.vconverter.convert.ReverseChartsConverter
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import org.json.JSONObject

class ChartHistory(initial: ChartDocument, private val limit: Int = 200) {
    private val undoStack = ArrayDeque<ChartDocument>()
    private val redoStack = ArrayDeque<ChartDocument>()
    var current: ChartDocument = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(next: ChartDocument): ChartDocument {
        if (next == current) return current
        undoStack.addLast(current)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        current = next
        return current
    }

    fun undo(): ChartDocument {
        val previous = undoStack.removeLastOrNull() ?: return current
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): ChartDocument {
        val next = redoStack.removeLastOrNull() ?: return current
        undoStack.addLast(current)
        current = next
        return current
    }

    fun reset(document: ChartDocument): ChartDocument {
        undoStack.clear()
        redoStack.clear()
        current = document
        return current
    }
}

enum class FieldType { NUMBER, TEXT, BOOL, CHOICE }

data class EventField(val key: String, val label: String, val type: FieldType, val default: Any, val choices: List<Pair<Any, String>> = emptyList())

data class EventTemplate(val kind: String, val title: String, val fields: List<EventField>) {
    fun defaults(): JSONObject = JSONObject().also { obj -> fields.forEach { obj.put(it.key, it.default) } }
}

object EventTemplates {
    private val eases = listOf("linear", "sine", "quad", "cube", "quart", "quint", "expo", "smoothStep", "elastic", "back", "bounce", "circ", "INSTANT", "CLASSIC").map { it to it }
    private val easeDirs = listOf("In", "Out", "InOut").map { it to it }
    private val targets = listOf("bf", "dad", "gf").map { it to it }

    val all = listOf(
        EventTemplate(
            "FocusCamera", "Focus camera",
            listOf(
                EventField("char", "Focus on", FieldType.CHOICE, 0, listOf(0 to "Player", 1 to "Opponent", 2 to "Girlfriend", -1 to "Position")),
                EventField("x", "X offset", FieldType.NUMBER, 0.0),
                EventField("y", "Y offset", FieldType.NUMBER, 0.0),
                EventField("duration", "Duration (steps)", FieldType.NUMBER, 4.0),
                EventField("ease", "Ease", FieldType.CHOICE, "CLASSIC", eases),
                EventField("easeDir", "Ease direction", FieldType.CHOICE, "In", easeDirs)
            )
        ),
        EventTemplate(
            "PlayAnimation", "Play animation",
            listOf(
                EventField("target", "Target", FieldType.CHOICE, "bf", targets),
                EventField("anim", "Animation", FieldType.TEXT, "hey"),
                EventField("force", "Force", FieldType.BOOL, true)
            )
        ),
        EventTemplate(
            "ZoomCamera", "Zoom camera",
            listOf(
                EventField("zoom", "Zoom", FieldType.NUMBER, 1.0),
                EventField("duration", "Duration (steps)", FieldType.NUMBER, 4.0),
                EventField("mode", "Mode", FieldType.CHOICE, "stage", listOf("stage" to "Stage zoom ×", "direct" to "Direct")),
                EventField("ease", "Ease", FieldType.CHOICE, "linear", eases),
                EventField("easeDir", "Ease direction", FieldType.CHOICE, "In", easeDirs)
            )
        ),
        EventTemplate(
            "ScrollSpeed", "Scroll speed",
            listOf(
                EventField("scroll", "Speed", FieldType.NUMBER, 1.0),
                EventField("duration", "Duration (steps)", FieldType.NUMBER, 4.0),
                EventField("absolute", "Absolute", FieldType.BOOL, false),
                EventField("ease", "Ease", FieldType.CHOICE, "linear", eases),
                EventField("easeDir", "Ease direction", FieldType.CHOICE, "In", easeDirs)
            )
        ),
        EventTemplate(
            "SetCameraBop", "Camera bop",
            listOf(
                EventField("rate", "Every (beats)", FieldType.NUMBER, 4.0),
                EventField("intensity", "Intensity", FieldType.NUMBER, 1.0)
            )
        )
    )

    fun of(kind: String): EventTemplate? = all.firstOrNull { it.kind == kind }
}

object ChartExport {
    fun vslice(document: ChartDocument): List<OutputFile> {
        val (metadata, chart) = document.fileNames()
        return listOf(OutputFile.of(metadata, document.metadataJson(), null), OutputFile.of(chart, document.chartJson(), null))
    }

    private fun pick(converter: FileConverter, files: List<OutputFile>) = files.filter { it.bytes == null }.mapNotNull { file ->
        runCatching { converter.classify(ImportedFile(file.name, file.relativePath, true) { file.content.byteInputStream() }, file.content) }.getOrNull()
    }

    fun psych(document: ChartDocument): ConvertedOutput {
        val converter = ReverseChartsConverter()
        return converter.build(pick(converter, vslice(document)))
    }

    fun codename(document: ChartDocument): ConvertedOutput {
        val psych = psych(document)
        val converter = PsychToCodenameChartsConverter()
        val result = converter.build(pick(converter, psych.files.filter { it.relativePath.startsWith("data/") }))
        val report = ReportBuilder()
        result.report.stats.forEach { report.stat(it.label, it.value) }
        result.report.sections.forEach { report.section(it.title, it.lines) }
        (psych.report.warnings + result.report.warnings).distinct().forEach { report.warn(it) }
        return ConvertedOutput(result.files, report.build())
    }

    fun summary(document: ChartDocument): ConvertedOutput {
        val report = ReportBuilder()
        document.difficulties.forEach { (name, chart) -> report.stat(name.replaceFirstChar { it.uppercase() }, chart.notes.size) }
        report.stat("Events", document.events.size)
        report.stat("BPM changes", document.timing.size)
        return ConvertedOutput(vslice(document), report.build())
    }
}

enum class ExportTarget(val label: String) { VSLICE("V-Slice"), PSYCH("Psych Engine"), CODENAME("Codename Engine") }

enum class AudioRole { INST, PLAYER, OPPONENT, VOICES }

object ChartBundle {
    fun role(audio: EditorAudio, document: ChartDocument): AudioRole {
        val suffix = if (document.variation.isEmpty()) "" else "-${document.variation.lowercase()}"
        val stem = audio.name.substringBeforeLast('.').lowercase().let { if (suffix.isNotEmpty() && it.endsWith(suffix)) it.dropLast(suffix.length) else it }
        val meta = document.meta
        return when {
            stem.startsWith("inst") -> AudioRole.INST
            stem == "voices" -> AudioRole.VOICES
            stem == "voices-player" || stem == "voices-${meta.player.lowercase()}" -> AudioRole.PLAYER
            stem == "voices-opponent" || stem == "voices-${meta.opponent.lowercase()}" -> AudioRole.OPPONENT
            stem.startsWith("voices-") -> AudioRole.VOICES
            else -> AudioRole.INST
        }
    }

    fun build(document: ChartDocument, target: ExportTarget, audio: List<EditorAudio>): ConvertedOutput {
        val roles = LinkedHashMap<AudioRole, EditorAudio>()
        audio.forEach { a -> roles.putIfAbsent(role(a, document), a) }
        fun ext(a: EditorAudio) = a.name.substringAfterLast('.', "ogg").lowercase()
        val files = ArrayList<OutputFile>()
        val base = when (target) {
            ExportTarget.VSLICE -> ChartExport.summary(document)
            ExportTarget.PSYCH -> ChartExport.psych(document)
            ExportTarget.CODENAME -> ChartExport.codename(document)
        }
        val split = roles.containsKey(AudioRole.PLAYER) || roles.containsKey(AudioRole.OPPONENT)
        when (target) {
            ExportTarget.VSLICE -> {
                files += base.files
                val sfx = if (document.variation.isEmpty()) "" else "-${document.variation}"
                val dir = "songs/${document.id}"
                roles.forEach { (role, a) ->
                    val name = when (role) {
                        AudioRole.INST -> "Inst$sfx"
                        AudioRole.PLAYER -> "Voices-${document.meta.player}$sfx"
                        AudioRole.OPPONENT -> "Voices-${document.meta.opponent}$sfx"
                        AudioRole.VOICES -> if (roles.containsKey(AudioRole.PLAYER)) null else "Voices-${document.meta.player}$sfx"
                    }
                    if (name != null) files += OutputFile.of("$dir/$name.${ext(a)}", null, a.bytes)
                }
            }
            ExportTarget.PSYCH -> {
                files += base.files
                val id = com.brenninhotools.vconverter.reverse.VSliceToPsychChart.songIdFor(document.id, document.variation)
                roles.forEach { (role, a) ->
                    val name = when (role) {
                        AudioRole.INST -> "Inst"
                        AudioRole.PLAYER -> "Voices-Player"
                        AudioRole.OPPONENT -> "Voices-Opponent"
                        AudioRole.VOICES -> if (split) null else "Voices"
                    }
                    if (name != null) files += OutputFile.of("songs/$id/$name.${ext(a)}", null, a.bytes)
                }
            }
            ExportTarget.CODENAME -> {
                val folder = base.files.firstOrNull { it.relativePath.endsWith("/meta.json") }?.relativePath?.removeSuffix("/meta.json")
                    ?: "songs/${document.id}"
                base.files.forEach { file ->
                    files += if (split && file.relativePath.startsWith("$folder/charts/") && file.bytes == null) {
                        OutputFile.of(file.relativePath, codenameSuffixes(file.content), null)
                    } else file
                }
                roles.forEach { (role, a) ->
                    val name = when (role) {
                        AudioRole.INST -> "Inst"
                        AudioRole.PLAYER -> "Voices-Player"
                        AudioRole.OPPONENT -> "Voices-Opponent"
                        AudioRole.VOICES -> if (split) null else "Voices"
                    }
                    if (name != null) files += OutputFile.of("$folder/song/$name.${ext(a)}", null, a.bytes)
                }
            }
        }
        val report = ReportBuilder()
        base.report.stats.forEach { report.stat(it.label, it.value) }
        report.stat("Audio files", files.count { it.bytes != null })
        base.report.sections.forEach { report.section(it.title, it.lines) }
        base.report.warnings.forEach { report.warn(it) }
        if (!roles.containsKey(AudioRole.INST)) report.warn("No instrumental picked: add Inst.ogg to the song folder yourself.")
        return ConvertedOutput(files, report.build())
    }

    private fun codenameSuffixes(json: String): String {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        val lines = root.optJSONArray("strumLines") ?: return json
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            when (line.optInt("type", -1)) {
                0 -> line.put("vocalsSuffix", "-Opponent")
                1 -> line.put("vocalsSuffix", "-Player")
            }
        }
        return root.toString(1)
    }
}
