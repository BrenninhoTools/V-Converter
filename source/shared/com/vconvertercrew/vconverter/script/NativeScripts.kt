package com.vconvertercrew.vconverter.script

import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject

object NativeScripts {
    val available: Boolean get() = VScriptNative.available

    private fun baseKey(base: ScriptBase): String = when (base) {
        ScriptBase.MODULE -> "module"
        ScriptBase.SONG -> "song"
        ScriptBase.STAGE -> "stage"
        ScriptBase.NOTE_KIND -> "notekind"
        ScriptBase.SONG_EVENT -> "event"
    }

    fun options(
        className: String,
        host: ScriptHost,
        target: ScriptTarget,
        noteTypes: Map<String, String>,
        events: Map<String, String>,
        skipLines: Set<Int>,
        stageProps: Collection<String> = emptyList()
    ): JSONObject {
        val options = JSONObject()
            .put("base", baseKey(host.base))
            .put("className", className)
            .put("id", host.id)
            .put("title", host.title)
            .put("eventName", if (target.scope == ScriptScope.EVENT) target.key else "")
            .put("noteStyle", host.noteStyle ?: "")
            .put("priority", 1100)
        if (host.base == ScriptBase.MODULE) {
            when (target.scope) {
                ScriptScope.SONG -> options.put("scopeKind", "song").put("scopeId", target.key)
                ScriptScope.STAGE -> options.put("scopeKind", "stage").put("scopeId", target.key)
                else -> Unit
            }
        }
        val kinds = JSONObject()
        noteTypes.forEach { (kind, name) -> kinds.put(name, kind) }
        options.put("noteKinds", kinds)
        val eventIds = JSONObject()
        events.forEach { (id, name) -> eventIds.put(name, id) }
        options.put("events", eventIds)
        options.put("skipLines", JSONArray().also { array -> skipLines.sorted().forEach { array.put(it) } })
        options.put("stageProps", JSONArray().also { array -> stageProps.forEach { array.put(it) } })
        return options
    }

    fun convert(source: String, className: String, options: JSONObject): GeneratedScript? {
        val raw = VScriptNative.convertLua(source, options.toString()) ?: return null
        val result = JSONObject(raw)
        val issues = result.optJSONArray("issues")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { ScriptIssue(it.optInt("line"), it.optString("message")) }
        }.orEmpty()
        val callbacks = result.optJSONArray("callbacks")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty()
        val api = result.optJSONArray("api")?.let { array -> (0 until array.length()).map { array.optString(it) }.toSet() }.orEmpty()
        val text = if (result.optBoolean("ok")) result.optString("source") else ""
        return GeneratedScript(className, options.optString("id"), text, issues, callbacks, api)
    }

    fun builtinEvent(name: String, className: String, id: String): String? {
        val raw = VScriptNative.builtinEvent(name, className, id) ?: return null
        val result = JSONObject(raw)
        return if (result.optBoolean("known")) result.optString("source") else null
    }

    fun generate(spec: JSONObject): Pair<String?, String?>? {
        val raw = VScriptNative.generate(spec.toString()) ?: return null
        val result = JSONObject(raw)
        return if (result.optBoolean("ok")) result.optString("source") to null else null to result.optString("error", "The script could not be generated.")
    }
}
