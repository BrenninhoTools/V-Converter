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

    fun eventClass(name: String) = "PsychEvent_" + slug(name)

    fun builtinEvents(): Set<String> = PsychNativeLibrary.builtinEventNames

    fun eventFiles(names: Collection<String>): List<ScriptFile> =
        names.filter { it in PsychNativeLibrary.builtinEventNames }.distinct().sorted().map { name ->
            ScriptFile("scripts/events/${eventClass(name)}.hxc", eventSource(name))
        }

    private fun eventSource(name: String): String {
        val body = PsychNativeLibrary.library.builtinEvents.getValue(name).lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { "      " + it.removePrefix("          ") }
        val id = LuaToHScript.hxString(name)
        val draft = """
class ${eventClass(name)} extends SongEvent
{
  public function new()
  {
    super($id);
  }

  override public function handleEvent(data:Dynamic):Void
  {
    if (data == null || !ensureGame()) return;
    var raw:Dynamic = data.value;
    var value1:Dynamic = raw == null ? "" : Reflect.field(raw, "value1");
    var value2:Dynamic = raw == null ? "" : Reflect.field(raw, "value2");
    if (value1 == null) value1 = "";
    if (value2 == null) value2 = "";
    try
    {
$body
    }
    catch (e:Dynamic) {}
  }

  override public function getTitle():String
  {
    return $id;
  }

  override public function getEventSchema():Dynamic
  {
    return new SongEventSchema([
      {name: "value1", title: "Value 1", type: "string", defaultValue: ""},
      {name: "value2", title: "Value 2", type: "string", defaultValue: ""}
    ]);
  }
""".trimStart('\n')
        val embedded = PsychNativeLibrary.embed(draft, "[]", emptyList())
        val stubs = StringBuilder()
        val all = draft + embedded.members
        if (Regex("\\bpsychCallback\\(").containsMatchIn(all)) stubs.append("\n  function psychCallback(name:String, args:Array<Dynamic>):Dynamic\n  {\n    return null;\n  }\n")
        if (Regex("\\bpsychClose\\(").containsMatchIn(all)) stubs.append("\n  function psychClose():Void {}\n")
        val imports = (listOf("funkin.data.event.SongEventSchema", "funkin.play.event.SongEvent") + embedded.imports).distinct().sorted()
        return imports.joinToString("") { "import $it;\n" } + "\n" + draft + "\n" + embedded.members + "\n" + stubs + "}\n"
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

        val planned = sources.map { source ->
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
            Triple(source, target, className to moduleId)
        }
        val siblings = planned.map { it.third.second }
        val noteTypeArray = noteTypes.entries.joinToString(", ", "[", "]") { (kind, name) ->
            "[${LuaToHScript.hxString(kind)}, ${LuaToHScript.hxString(name)}]"
        }

        planned.forEach { (source, target, names) ->
            val (className, moduleId) = names
            val ext = source.path.substringAfterLast('.', "").lowercase()
            val generated = try {
                if (ext == "lua") {
                    LuaToHScript(className, moduleId, target, siblings, noteTypeArray).convert(source.text)
                } else {
                    PsychHScriptWrapper(className, moduleId, target, siblings, noteTypeArray).convert(source.text)
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
                "The Psych functions a script uses are implemented inside its own module with V-Slice and Flixel code; no extra runtime is needed.",
                "Psych events keep their names in the charts and reach onEvent in your scripts; built-in ones also get their own SongEvent script."
            )
        )
        return ScriptBundle(files, report.build(), converted)
    }

    private fun resource(name: String): String =
        ScriptConverter::class.java.getResourceAsStream("/vconverter/$name")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Missing bundled template $name")
}
