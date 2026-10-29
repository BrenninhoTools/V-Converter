package com.brenninhotools.vconverter.overlay

import org.json.JSONArray
import org.json.JSONObject

data class PreviewText(val text: String, val totalLines: Int, val shownLines: Int, val truncated: Boolean)

object CodePreviewText {
    private const val MAX_LINE = 400
    private const val PRETTY_LIMIT = 4_000_000

    fun prepare(raw: String, maxLines: Int): PreviewText {
        var source = raw.trimEnd()
        val firstBreak = source.indexOf('\n')
        val firstLine = if (firstBreak < 0) source.length else firstBreak
        if (firstLine > MAX_LINE * 4 && source.length <= PRETTY_LIMIT) {
            val trimmed = source.trimStart()
            source = runCatching {
                when {
                    trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                    trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                    else -> source
                }
            }.getOrDefault(source)
        }
        val total = source.count { it == '\n' } + 1
        val out = StringBuilder()
        var shown = 0
        var cut = false
        for (line in source.lineSequence()) {
            if (shown >= maxLines) break
            if (shown > 0) out.append('\n')
            val clean = line.replace("\t", "  ")
            if (clean.length > MAX_LINE) {
                out.append(clean, 0, MAX_LINE).append(" …")
                cut = true
            } else {
                out.append(clean)
            }
            shown++
        }
        return PreviewText(out.toString(), total, shown, cut || shown < total)
    }
}
