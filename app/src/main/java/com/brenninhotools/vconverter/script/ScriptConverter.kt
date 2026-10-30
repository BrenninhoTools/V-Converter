package com.brenninhotools.vconverter.script

import com.brenninhotools.vconverter.chart.PsychEventIds
import com.brenninhotools.vconverter.chart.PsychToVSliceChart
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder

data class ScriptSource(val path: String, val text: String, val target: ScriptTarget? = null, val skipLines: Set<Int> = emptySet())

data class ScriptFile(val path: String, val content: String)

data class ScriptBundle(val files: List<ScriptFile>, val report: Report, val converted: Int)

object ScriptConverter {
    const val OUTPUT_FOLDER = "scripts/modules"

    fun detectTarget(path: String): ScriptTarget {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val stem = parts.lastOrNull()?.substringBeforeLast('.') ?: "script"
        val lower = parts.map { it.lowercase() }
        val dataIndex = lower.lastIndexOf("data")
        return when {
            "custom_events" in lower -> ScriptTarget(ScriptScope.EVENT, stem)
            "custom_notetypes" in lower -> ScriptTarget(ScriptScope.NOTE_TYPE, stem)
            "stages" in lower -> ScriptTarget(ScriptScope.STAGE, stem)
            dataIndex >= 0 && dataIndex + 3 < parts.size && lower[dataIndex + 1] == "songs" -> ScriptTarget(ScriptScope.SONG, parts[dataIndex + 2].lowercase())
            dataIndex >= 0 && dataIndex + 2 < parts.size -> ScriptTarget(ScriptScope.SONG, parts[dataIndex + 1].lowercase())
            else -> ScriptTarget(ScriptScope.GLOBAL)
        }
    }

    fun pascal(text: String): String {
        val joined = text.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        return when {
            joined.isEmpty() -> "Script"
            joined.first().isDigit() -> "S$joined"
            else -> joined
        }
    }

    private val takenNames = setOf(
        "Module", "ModuleHandler", "PlayState", "FlxG", "FlxSprite", "FlxText", "FlxTween", "FlxEase", "FlxTimer", "Conductor", "Paths",
        "Preferences", "Highscore", "PlayerSettings", "PlayStatePlaylist", "FunkinSound", "StringMap", "SongEvent", "SongEventSchema",
        "Std", "Math", "String", "Array", "Reflect", "Type", "Assets", "Constants"
    )

    fun keepName(stem: String): String {
        val cleaned = stem.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "Script" }
        val name = (if (cleaned.first().isDigit()) "S$cleaned" else cleaned).replaceFirstChar { it.uppercaseChar() }
        return if (name in takenNames) name + "Script" else name
    }

    fun keepFileName(stem: String): String = stem.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "script" }

    fun kebab(className: String): String =
        className.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2").lowercase()

    fun slug(text: String): String {
        val cleaned = text.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return cleaned.ifEmpty { "Mod" }.let { if (it.first().isDigit()) "M$it" else it }
    }

    fun eventClass(name: String) = PsychEventIds.className(name)

    fun eventId(name: String) = PsychEventIds.idFor(name)

    fun builtinEvents(): Set<String> = PsychNativeLibrary.builtinEventNames

    fun eventFiles(names: Collection<String>, scripted: Collection<String> = emptyList()): List<ScriptFile> =
        names.filter { it in PsychNativeLibrary.builtinEventNames && scripted.none { s -> eventId(s) == eventId(it) } }.distinct().sorted().map { name ->
            ScriptFile("scripts/events/${eventClass(name)}.hxc", HxImports.complete(eventSource(name)))
        }

    private fun eventSource(name: String): String {
        val body = PsychNativeLibrary.library.builtinEvents.getValue(name).lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { "      " + it.removePrefix("          ") }
        val id = LuaToHScript.hxString(eventId(name))
        val title = LuaToHScript.hxString(name)
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
    return $title;
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
        return Naturalizer.module(imports.joinToString("") { "import $it;\n" } + "\n" + embedded.moduleText + "\n" + embedded.members + "\n" + stubs + "}\n")
    }

    fun noteTypeMap(names: Collection<String>): Map<String, String> =
        names.filter { it.isNotBlank() }.associateBy { PsychToVSliceChart.noteKindFor(it) }

    fun eventForwarder(name: String, moduleId: String): String {
        val id = LuaToHScript.hxString(eventId(name))
        val title = LuaToHScript.hxString(name)
        return """
class ${eventClass(name)} extends SongEvent
{
  public function new()
  {
    super($id);
  }

  override public function handleEvent(data:Dynamic):Void
  {
    if (data == null) return;
    var handler:Dynamic = ModuleHandler.getModule(${LuaToHScript.hxString(moduleId)});
    if (handler == null) return;
    var raw:Dynamic = data.value;
    var value1:Dynamic = raw == null ? "" : Reflect.field(raw, "value1");
    var value2:Dynamic = raw == null ? "" : Reflect.field(raw, "value2");
    handler.scriptCall("callback", ["onEvent", [$title, value1 == null ? "" : value1, value2 == null ? "" : value2, data.time]]);
  }

  override public function getTitle():String
  {
    return $title;
  }

  override public function getEventSchema():Dynamic
  {
    return new SongEventSchema([
      {name: "value1", title: "Value 1", type: "string", defaultValue: ""},
      {name: "value2", title: "Value 2", type: "string", defaultValue: ""}
    ]);
  }
}
""".trimStart('\n')
    }

    private fun withForwarder(module: String, name: String, moduleId: String): String {
        val lines = module.lines()
        val existing = lines.takeWhile { !it.startsWith("class ") }.filter { it.startsWith("import ") }.toMutableSet()
        val body = lines.dropWhile { !it.startsWith("class ") }.joinToString("\n").trimEnd()
        existing += listOf("import funkin.data.event.SongEventSchema;", "import funkin.modding.module.ModuleHandler;", "import funkin.play.event.SongEvent;")
        return existing.sorted().joinToString("\n") + "\n\n" + eventForwarder(name, moduleId) + "\n" + body + "\n"
    }

    fun convert(modSlug: String, sources: List<ScriptSource>, noteTypes: Map<String, String>, chartEvents: Collection<String> = emptyList()): ScriptBundle {
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
            val prefix = pascal(modSlug)
            val base = when (target.scope) {
                ScriptScope.EVENT -> eventClass(target.key.ifBlank { stem }) + "Handler"
                ScriptScope.GLOBAL -> keepName(stem)
                ScriptScope.SONG -> prefix + pascal(target.key) + pascal(stem).removePrefix(pascal(target.key)).ifEmpty { "Script" }
                ScriptScope.STAGE -> prefix + pascal(target.key) + "Stage"
                ScriptScope.NOTE_TYPE -> prefix + pascal(target.key) + "NoteType"
            }
            var className = base
            var n = 2
            while (!usedNames.add(className.lowercase())) className = base + n++
            val moduleId = if (target.scope == ScriptScope.GLOBAL && className == base) stem else kebab(className)
            Triple(source, target, className to moduleId)
        }
        val siblings = planned.map { it.third.second }
        val eventNames = (builtinEvents() + chartEvents + planned.filter { it.second.scope == ScriptScope.EVENT }.map { it.second.key })
            .filter { it.isNotBlank() }.associateBy { eventId(it) }
        val noteTypeArray = noteTypes.entries.joinToString(", ", "[", "]") { (kind, name) ->
            "[${LuaToHScript.hxString(kind)}, ${LuaToHScript.hxString(name)}]"
        }

        planned.forEach { (source, target, names) ->
            val (className, moduleId) = names
            val ext = source.path.substringAfterLast('.', "").lowercase()
            val isEvent = target.scope == ScriptScope.EVENT && target.key.isNotBlank()
            val ownEvent = if (isEvent) eventId(target.key) else null
            val generated = try {
                if (ext == "lua") {
                    LuaToHScript(className, moduleId, target, siblings, noteTypeArray, eventNames, ownEvent, source.skipLines).convert(source.text)
                } else {
                    PsychHScriptWrapper(className, moduleId, target, siblings, noteTypeArray, eventNames, ownEvent).convert(source.text)
                }
            } catch (e: Exception) {
                report.warn("${source.path}: could not be converted (${e.message}).")
                return@forEach
            }
            val label = "${source.path} · ${target.scope.label}${if (target.key.isNotEmpty()) " \"${target.key}\"" else ""}" +
                if (target.scope == ScriptScope.EVENT && target.key.isNotBlank()) " → scripts/events/${eventClass(target.key)}.hxc (id \"${eventId(target.key)}\")" else ""
            if (generated.source.isEmpty()) {
                report.warn("${source.path}: ${generated.issues.firstOrNull()?.message ?: "could not be converted"}")
                return@forEach
            }
            if (isEvent) {
                files += ScriptFile("scripts/events/${eventClass(target.key)}.hxc", HxImports.complete(withForwarder(generated.source, target.key, moduleId)))
            } else {
                val fileName = if (target.scope == ScriptScope.GLOBAL) keepFileName(source.path.substringAfterLast('/').substringBeforeLast('.')) else className
                files += ScriptFile("$OUTPUT_FOLDER/$fileName.hxc", HxImports.complete(generated.source))
            }
            converted++
            hooks += generated.callbacks.size
            apiUsed += generated.apiCalls
            val lines = mutableListOf("Callbacks: ${generated.callbacks.joinToString().ifEmpty { "none" }}")
            generated.issues.take(40).forEach { lines += if (it.line > 0) "Line ${it.line}: ${it.message}" else it.message }
            if (generated.issues.size > 40) lines += "…and ${generated.issues.size - 40} more notes"
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
                "Psych events get V-Slice ids (\"Flash Camera\" → \"FlashCamera\") in charts and scripts alike; your scripts still see the original names in onEvent.",
                "custom_events scripts go to scripts/events/<Name>Event.hxc: a SongEvent (Chart Editor entry) plus a Module with the converted code."
            )
        )
        return ScriptBundle(files, report.build(), converted)
    }

    private fun resource(name: String): String =
        ScriptConverter::class.java.getResourceAsStream("/vconverter/$name")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Missing bundled template $name")
}
