package com.vconvertercrew.vconverter.online

import com.vconvertercrew.vconverter.json.JSONArray
import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.platform.Http
import kotlin.time.TimeSource

object ServerDirectory {
    const val LIST_URL = "https://raw.githubusercontent.com/VConverterCrew/V-Converter/main/server/servers.json"
    const val PROBE_TIMEOUT_MS = 4_000
    const val LIST_TIMEOUT_MS = 8_000

    fun parse(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = runCatching {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("servers")
        }.getOrNull() ?: return emptyList()
        val found = ArrayList<String>()
        for (i in 0 until array.length()) {
            val entry = array.opt(i)
            val raw = when (entry) {
                is String -> entry
                is JSONObject -> if (entry.optBoolean("enabled", true)) entry.optString("url") else ""
                else -> ""
            }
            OnlineApi.normalize(raw).takeIf { it.isNotEmpty() }?.let(found::add)
        }
        return found.distinct()
    }

    fun fetch(url: String = LIST_URL): List<String> = runCatching {
        val response = Http.request(url, "GET", mapOf("Accept" to "application/json", "User-Agent" to "V-Converter"), null, LIST_TIMEOUT_MS)
        if (response.status in 200..299) parse(response.body.decodeToString()) else emptyList()
    }.getOrDefault(emptyList())

    fun probe(url: String): Long? {
        val mark = TimeSource.Monotonic.markNow()
        return if (OnlineApi(url, PROBE_TIMEOUT_MS).health()) mark.elapsedNow().inWholeMilliseconds else null
    }

    fun pick(candidates: List<String>, probe: (String) -> Long? = ::probe): String? =
        candidates.distinct().mapNotNull { url -> probe(url)?.let { url to it } }.minByOrNull { it.second }?.first

    fun discover(extra: List<String> = emptyList()): String? = pick(fetch() + extra)
}
