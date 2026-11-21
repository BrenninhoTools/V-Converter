package com.vconvertercrew.vconverter.script

import com.vconvertercrew.vconverter.core.Report
import com.vconvertercrew.vconverter.core.ReportBuilder
import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject

object NativeData {
    var enabled: Boolean = true

    var conversions: Int = 0
        private set

    fun convert(kind: String, input: JSONObject): JSONObject? {
        if (!enabled || !VScriptNative.available) return null
        val raw = runCatching { VScriptNative.convertData(kind, input.toString()) }.getOrNull() ?: return null
        val result = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (!result.optBoolean("ok")) return null
        return result.optJSONObject("result")?.also { conversions++ }
    }

    fun report(json: JSONObject?): Report {
        val builder = ReportBuilder()
        if (json == null) return builder.build()
        json.optJSONArray("stats")?.let { stats ->
            for (i in 0 until stats.length()) {
                val stat = stats.optJSONArray(i) ?: continue
                builder.stat(stat.optString(0), stat.opt(1) ?: "")
            }
        }
        json.optJSONArray("sections")?.let { sections ->
            for (i in 0 until sections.length()) {
                val section = sections.optJSONArray(i) ?: continue
                builder.section(section.optString(0), strings(section.optJSONArray(1)))
            }
        }
        strings(json.optJSONArray("warnings")).forEach(builder::warn)
        return builder.build()
    }

    fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }
}
