package com.brenninhotools.vconverter.mod

enum class ArchiveKind(val label: String) {
    ZIP("ZIP"),
    RAR("RAR"),
    SEVEN_ZIP("7z");

    companion object {
        private val extensions = mapOf("zip" to ZIP, "rar" to RAR, "7z" to SEVEN_ZIP, "fnfc" to ZIP)

        fun detect(header: ByteArray): ArchiveKind? = when {
            header.startsWith(0x50, 0x4B, 0x03, 0x04) || header.startsWith(0x50, 0x4B, 0x05, 0x06) -> ZIP
            header.startsWith(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> RAR
            header.startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> SEVEN_ZIP
            else -> null
        }

        fun fromName(name: String): ArchiveKind? = extensions[name.substringAfterLast('.', "").lowercase()]

        private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
            size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xFF) == bytes[it] }
    }
}
