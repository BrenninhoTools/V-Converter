package com.vconvertercrew.vconverter.online

import com.vconvertercrew.vconverter.json.JSONObject
import com.vconvertercrew.vconverter.platform.Http

data class DiscordProfile(val id: String, val username: String, val displayName: String, val avatarUrl: String) {
    val name: String get() = displayName.ifBlank { username }

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("displayName", displayName)
        .put("avatarUrl", avatarUrl)
        .toString()

    companion object {
        fun from(obj: JSONObject?): DiscordProfile? {
            val id = obj?.optString("id")?.takeIf { it.isNotBlank() } ?: return null
            return DiscordProfile(id, obj.optString("username"), obj.optString("displayName"), obj.optString("avatarUrl"))
        }

        fun parse(text: String?): DiscordProfile? =
            text?.takeIf { it.isNotBlank() }?.let { runCatching { from(JSONObject(it)) }.getOrNull() }
    }
}

class DiscordLoginTicket(val id: String, val url: String)

object Discord {
    const val APPLICATION_ID = "1536017631579078738"
    const val LARGE_IMAGE = "icon"

    fun download(url: String): ByteArray? {
        if (!url.startsWith("https://")) return null
        val response = runCatching { Http.request(url, "GET", mapOf("User-Agent" to "V-Converter"), null, 15_000, null) }.getOrNull() ?: return null
        return response.body.takeIf { response.status in 200..299 && it.isNotEmpty() }
    }
}
