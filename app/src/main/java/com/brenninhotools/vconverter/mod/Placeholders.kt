package com.brenninhotools.vconverter.mod

object Placeholders {
    fun find(paths: List<String>, root: String): Map<String, String> {
        val inside = paths.filter { it.startsWith(root) }.map { it.removePrefix(root) }
        val counts = HashMap<String, MutableList<String>>()
        inside.forEach { rel ->
            val parts = rel.split('/')
            for (depth in 1 until parts.size) {
                counts.getOrPut(parts.take(depth).joinToString("/")) { mutableListOf() } += rel
            }
        }
        fun isPlaceholder(dir: String): Boolean {
            val files = counts[dir] ?: return false
            return files.size == 1 && files.single().substringAfterLast('.', "").equals("txt", ignoreCase = true)
        }
        return counts.keys.filter { dir ->
            isPlaceholder(dir) && !(dir.contains('/') && isPlaceholder(dir.substringBeforeLast('/')))
        }.sorted().associateWith { dir -> root + counts.getValue(dir).single() }
    }
}
