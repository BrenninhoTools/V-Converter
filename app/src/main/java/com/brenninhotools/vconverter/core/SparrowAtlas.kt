package com.brenninhotools.vconverter.core

data class SparrowFrame(
    val name: String,
    val sourceWidth: Double,
    val sourceHeight: Double
)

class SparrowAtlas(val frames: List<SparrowFrame>) {
    val firstFrame: SparrowFrame? get() = frames.firstOrNull()

    fun hasPrefix(prefix: String): Boolean = frames.any { it.name.startsWith(prefix) }

    fun firstFrameOf(prefix: String, indices: List<Int> = emptyList()): SparrowFrame? {
        val matching = frames.filter { it.name.startsWith(prefix) }
        if (matching.isEmpty()) return null
        if (indices.isNotEmpty()) {
            val wanted = indices.first()
            matching.firstOrNull { frameNumber(it.name, prefix) == wanted }?.let { return it }
        }
        return matching.minWithOrNull(compareBy { frameNumber(it.name, prefix) ?: Int.MAX_VALUE })
    }

    private fun frameNumber(name: String, prefix: String): Int? {
        val digits = name.removePrefix(prefix).takeLastWhile { it.isDigit() }
        return digits.toIntOrNull()
    }

    companion object {
        private val subTexture = Regex("<SubTexture\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
        private val attribute = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")

        fun parse(xml: String): SparrowAtlas {
            val frames = subTexture.findAll(xml).mapNotNull { match ->
                val attrs = attribute.findAll(match.groupValues[1]).associate { attr ->
                    attr.groupValues[1].lowercase() to unescape(attr.groupValues[3].ifEmpty { attr.groupValues[4] })
                }
                val name = attrs["name"] ?: return@mapNotNull null
                val width = attrs["width"]?.toDoubleOrNull() ?: 0.0
                val height = attrs["height"]?.toDoubleOrNull() ?: 0.0
                val rotated = attrs["rotated"]?.lowercase() == "true"
                val frameWidth = attrs["framewidth"]?.toDoubleOrNull()
                val frameHeight = attrs["frameheight"]?.toDoubleOrNull()
                val sourceWidth = frameWidth ?: if (rotated) height else width
                val sourceHeight = frameHeight ?: if (rotated) width else height
                SparrowFrame(name, sourceWidth, sourceHeight)
            }.toList()
            return SparrowAtlas(frames)
        }

        private fun unescape(text: String): String = text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }
}
