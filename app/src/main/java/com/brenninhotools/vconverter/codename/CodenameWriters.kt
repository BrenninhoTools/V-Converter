package com.brenninhotools.vconverter.codename

import com.brenninhotools.vconverter.core.json.toJsonNumber
import com.brenninhotools.vconverter.json.JSONArray
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.core.fmt

class XmlBuilder(private val doctype: String? = null) {
    private val sb = StringBuilder()
    private var depth = 0

    init {
        if (doctype != null) sb.append("<!DOCTYPE ").append(doctype).append(">\n")
    }

    private fun attrs(list: List<Pair<String, Any?>>): String = list.filter { it.second != null }.joinToString("") { (k, v) -> " $k=\"${escape(text(v))}\"" }

    private fun text(v: Any?): String = when (v) {
        is Double -> CodenameToPsychEvents.number(v)
        is Float -> CodenameToPsychEvents.number(v.toDouble())
        else -> v.toString()
    }

    fun open(name: String, vararg attributes: Pair<String, Any?>): XmlBuilder {
        sb.append("\t".repeat(depth)).append('<').append(name).append(attrs(attributes.toList())).append(">\n")
        depth++
        return this
    }

    fun close(name: String): XmlBuilder {
        depth--
        sb.append("\t".repeat(depth)).append("</").append(name).append(">\n")
        return this
    }

    fun leaf(name: String, vararg attributes: Pair<String, Any?>): XmlBuilder {
        sb.append("\t".repeat(depth)).append('<').append(name).append(attrs(attributes.toList())).append("/>\n")
        return this
    }

    fun textNode(name: String, value: String, vararg attributes: Pair<String, Any?>): XmlBuilder {
        sb.append("\t".repeat(depth)).append('<').append(name).append(attrs(attributes.toList())).append('>').append(escape(value)).append("</").append(name).append(">\n")
        return this
    }

    fun blank(): XmlBuilder {
        sb.append('\n')
        return this
    }

    override fun toString(): String = sb.toString()

    companion object {
        fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

data class CodenameOutStrumLine(
    val characters: List<String>,
    val type: Int,
    val position: String,
    val notes: List<CodenameNote>,
    val visible: Boolean = true,
    val vocalsSuffix: String = ""
)

data class CodenameOutMeta(
    val displayName: String,
    val bpm: Double,
    val beatsPerMeasure: Double = 4.0,
    val stepsPerBeat: Int = 4,
    val icon: String,
    val color: String,
    val difficulties: List<String>,
    val needsVoices: Boolean,
    val customValues: Map<String, Any> = emptyMap()
)

object CodenameWriters {
    const val CHART_VERSION = "1.6.0"

    fun chart(strumLines: List<CodenameOutStrumLine>, events: List<CodenameEvent>, noteTypes: List<String>, scrollSpeed: Double, stage: String): String {
        val lines = JSONArray()
        strumLines.forEach { line ->
            val obj = JSONObject()
                .put("characters", JSONArray(line.characters))
                .put("type", line.type)
                .put("position", line.position)
                .put("keyCount", 4)
                .put("visible", line.visible)
                .put("notes", JSONArray(line.notes.sortedBy { it.time }.map { n ->
                    JSONObject().put("time", n.time.toJsonNumber()).put("id", n.id).put("type", n.type).put("sLen", n.length.toJsonNumber())
                }))
            if (line.vocalsSuffix.isNotEmpty()) obj.put("vocalsSuffix", line.vocalsSuffix)
            lines.put(obj)
        }
        return JSONObject()
            .put("codenameChart", true)
            .put("chartVersion", CHART_VERSION)
            .put("strumLines", lines)
            .put("events", eventsArray(events))
            .put("noteTypes", JSONArray(noteTypes))
            .put("scrollSpeed", scrollSpeed.toJsonNumber())
            .put("stage", stage)
            .toString()
    }

    fun eventsArray(events: List<CodenameEvent>): JSONArray = JSONArray(events.sortedBy { it.time }.map { e ->
        JSONObject().put("name", e.name).put("time", e.time.toJsonNumber()).put("params", JSONArray(e.params.map { jsonValue(it) }))
    })

    private fun jsonValue(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Double -> v.toJsonNumber()
        is Float -> v.toDouble().toJsonNumber()
        else -> v
    }

    fun meta(meta: CodenameOutMeta): String {
        val obj = JSONObject()
            .put("displayName", meta.displayName)
            .put("bpm", meta.bpm.toJsonNumber())
            .put("icon", meta.icon)
            .put("color", meta.color)
            .put("difficulties", JSONArray(meta.difficulties))
            .put("needsVoices", meta.needsVoices)
            .put("coopAllowed", true)
            .put("opponentModeAllowed", true)
        if (meta.beatsPerMeasure != 4.0) obj.put("beatsPerMeasure", meta.beatsPerMeasure.toJsonNumber())
        if (meta.stepsPerBeat != 4) obj.put("stepsPerBeat", meta.stepsPerBeat)
        if (meta.customValues.isNotEmpty()) obj.put("customValues", JSONObject(meta.customValues))
        return obj.toString(2)
    }

    fun eventDefinition(params: List<Triple<String, String, Any?>>): String = JSONObject()
        .put("params", JSONArray(params.map { (name, type, def) -> JSONObject().put("name", name).put("type", type).put("defaultValue", def ?: JSONObject.NULL) }))
        .toString(2)

    fun hex(color: List<Int>): String = fmt("#%02X%02X%02X", color.getOrElse(0) { 161 }.coerceIn(0, 255), color.getOrElse(1) { 161 }.coerceIn(0, 255), color.getOrElse(2) { 161 }.coerceIn(0, 255))

    fun modpack(name: String, description: String, author: String, version: String): String = buildString {
        fun q(v: String) = "\"" + v.replace("\n", " ").replace("\"", "'") + "\""
        append("[Common]\n")
        append("NAME=").append(q(name)).append('\n')
        append("DESCRIPTION=").append(q(description)).append('\n')
        if (author.isNotBlank()) append("AUTHOR=").append(q(author)).append('\n')
        append("VERSION=").append(q(version)).append('\n')
        append("API_VERSION=3\n")
        append("\n[Flags]\n")
        append("DISABLE_WARNING_SCREEN=true\n")
    }
}
