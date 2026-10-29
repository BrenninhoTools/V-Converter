package com.brenninhotools.vconverter.chart

import com.brenninhotools.vconverter.core.SparrowAtlas
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedNoteStyle(val id: String, val json: String, val notes: List<String>)

object PsychNoteStyles {
    const val NOTE_STYLE_VERSION = "1.1.0"

    private val defaultSkins = setOf("", "note_assets", "noteskins/note_assets", "shared/note_assets")
    private val directions = listOf("left", "down", "up", "right")
    private val colors = listOf("purple", "blue", "green", "red")
    private val texturePatterns = listOf(
        Regex("""['"]texture['"]\s*,\s*['"]([^'"]+)['"]"""),
        Regex("""\.texture\s*=\s*['"]([^'"]+)['"]"""),
        Regex("""reloadNote\s*\(\s*['"]([^'"]+)['"]""")
    )

    fun isCustom(skin: String): Boolean {
        val clean = skin.trim().replace('\\', '/').removePrefix("images/").removeSuffix(".png").lowercase()
        return clean !in defaultSkins && !clean.contains("pixel")
    }

    fun idFor(skin: String): String =
        "psych-" + skin.trim().replace('\\', '/').substringAfterLast('/').removeSuffix(".png").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "notes" }

    fun textureIn(script: String): String? = texturePatterns.firstNotNullOfOrNull { it.find(script)?.groupValues?.get(1) }

    fun build(skin: String, notesAtlas: SparrowAtlas?, splashSkin: String? = null, splashAtlas: SparrowAtlas? = null, strumline: Boolean = true, name: String = ""): GeneratedNoteStyle? {
        val path = skin.trim().replace('\\', '/').removePrefix("images/").removeSuffix(".png")
        val notes = mutableListOf<String>()
        val hasNotes = notesAtlas == null || colors.all { notesAtlas.hasPrefix(it + "0") }
        val hasStrums = strumline && (notesAtlas == null || directions.all { notesAtlas.hasPrefix("arrow" + it.uppercase()) })
        if (!hasNotes && !hasStrums) return null
        val assets = JSONObject()
        if (hasNotes) {
            val data = JSONObject()
            directions.forEachIndexed { i, dir -> data.put(dir, JSONObject().put("prefix", colors[i] + "0")) }
            assets.put("note", JSONObject().put("assetPath", path).put("scale", 0.7).put("data", data))
        }
        if (hasStrums) {
            val data = JSONObject()
            directions.forEach { dir ->
                data.put("${dir}Static", JSONObject().put("prefix", "arrow" + dir.uppercase()))
                data.put("${dir}Press", JSONObject().put("prefix", "$dir press"))
                data.put("${dir}Confirm", JSONObject().put("prefix", "$dir confirm"))
                data.put("${dir}ConfirmHold", JSONObject().put("prefix", "$dir confirm"))
            }
            assets.put("noteStrumline", JSONObject().put("assetPath", path).put("scale", 0.7).put("offsets", JSONArray(listOf(0, 0))).put("data", data))
        }
        if (splashSkin != null && splashAtlas != null) {
            val data = JSONObject().put("enabled", true)
            var found = 0
            directions.forEachIndexed { i, dir ->
                val list = JSONArray()
                (1..2).map { "note splash ${colors[i]} $it" }.filter { splashAtlas.hasPrefix(it) }.forEach { list.put(JSONObject().put("prefix", it)) }
                found += list.length()
                data.put("${dir}Splashes", list)
            }
            if (found > 0) {
                val splashPath = splashSkin.trim().replace('\\', '/').removePrefix("images/").removeSuffix(".png")
                assets.put("noteSplash", JSONObject().put("assetPath", splashPath).put("alpha", 0.6).put("data", data))
                notes += "Note splashes come from images/$splashPath."
            }
        }
        if (notesAtlas == null) notes += "The spritesheet XML was not available, so Psych's default animation names were assumed (purple0…, arrowLEFT, left press, left confirm)."
        if (!hasStrums && strumline) notes += "The sheet has no Psych strum animations (arrowLEFT…), so the strumline keeps V-Slice's default look."
        notes += "Hold notes keep V-Slice's default sheet: V-Slice draws sustains from a separate strip image, which Psych sheets don't have."
        val root = JSONObject()
            .put("version", NOTE_STYLE_VERSION)
            .put("name", name.ifBlank { path.substringAfterLast('/') })
            .put("author", "")
            .put("fallback", "funkin")
            .put("assets", assets)
        return GeneratedNoteStyle(idFor(skin), root.toString(2), notes)
    }

    fun noteKindScript(kind: String, psychName: String, styleId: String): Pair<String, String> {
        val pascal = psychName.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }.ifEmpty { "Psych" }
        val className = (if (pascal.first().isDigit()) "Kind$pascal" else pascal) + "TextureNoteKind"
        val source = """
import funkin.play.notes.notekind.NoteKind;

class $className extends NoteKind
{
  public function new()
  {
    super(${quote(kind)}, ${quote(psychName)}, ${quote(styleId)});
  }
}
""".trimStart()
        return "scripts/notekinds/$className.hxc" to source
    }

    private fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
