package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.codename.CodenameCharacterParser
import com.brenninhotools.vconverter.codename.PsychToCodenameCharacter
import com.brenninhotools.vconverter.convert.CodenameCharactersConverter
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.convert.PsychCharactersConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameCharactersConverter
import com.brenninhotools.vconverter.convert.ReverseCharactersConverter
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.json.JSONObject
import com.brenninhotools.vconverter.platform.byteInputStream

data class LoadedCharacter(
    val document: CharacterDocument,
    val image: ByteArray?,
    val imageName: String?,
    val xml: String?,
    val source: String
)

object CharacterLoader {
    private fun stem(path: String) = path.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')

    private fun key(path: String) = stem(path.substringAfterLast(':')).lowercase()

    fun load(files: List<Pair<String, ByteArray>>): LoadedCharacter {
        val texts = files.filter { it.first.substringAfterLast('.').lowercase() in setOf("json", "xml") }.map { it.first to it.second.decodeToString() }
        val pngs = files.filter { it.first.endsWith(".png", ignoreCase = true) }
        val sheets = texts.filter { it.first.endsWith(".xml", true) && !CodenameCharacterParser.isCharacterXml(it.second) && SpriteAtlas.parse(it.second).frames.isNotEmpty() }

        val vslice = texts.filter { it.first.endsWith(".json", true) }.firstNotNullOfOrNull { (path, text) ->
            runCatching { VSliceParser.character(text) }.getOrNull()?.let { CharacterDocument.fromVSlice(Ids.of(stem(path)), text) }
        }
        val (document, source) = vslice?.let { it to "V-Slice" }
            ?: convert(PsychCharactersConverter(), texts.filter { it.first.endsWith(".json", true) } + sheets)?.let { it to "Psych Engine" }
            ?: convert(CodenameCharactersConverter(), texts.filter { it.first.endsWith(".xml", true) })?.let { it to "Codename Engine" }
            ?: sheets.firstOrNull()?.let { (path, text) ->
                val id = Ids.of(stem(path)).ifEmpty { "character" }
                CharacterDocument.fromAtlas(id, SpriteAtlas.parse(text), "characters/${stem(path)}") to "Spritesheet"
            }
            ?: throw IllegalArgumentException("No character found. Pick a V-Slice or Psych character .json, a Codename character .xml, or a spritesheet .png + .xml.")

        val wanted = key(document.assetPath)
        val sheet = sheets.firstOrNull { key(it.first) == wanted } ?: sheets.firstOrNull()
        val png = sheet?.let { s -> pngs.firstOrNull { key(it.first) == key(s.first) } } ?: pngs.firstOrNull { key(it.first) == wanted } ?: pngs.firstOrNull()
        return LoadedCharacter(document, png?.second, png?.first?.replace('\\', '/')?.substringAfterLast('/'), sheet?.second, source)
    }

    private fun convert(converter: FileConverter, texts: List<Pair<String, String>>): CharacterDocument? {
        val items = texts.mapNotNull { (path, text) ->
            runCatching { converter.classify(ImportedFile(path.replace('\\', '/').substringAfterLast('/'), path, true) { text.byteInputStream() }, text) }.getOrNull()
        }
        if (!converter.canConvert(items)) return null
        val character = items.first { it.kind == "character" }
        val output = runCatching { converter.build(items) }.getOrNull() ?: return null
        val json = output.files.firstOrNull { it.bytes == null && it.relativePath.startsWith("data/characters/") } ?: return null
        val id = json.name.substringBeforeLast('.')
        return runCatching { CharacterDocument.fromVSlice(id, json.content, character.slot) }.getOrNull()?.copy(rawJson = null)
    }
}

object CharacterExport {
    private fun imagePath(raw: String): String = raw.trim().replace('\\', '/').substringAfter(':').removePrefix("images/").removeSuffix(".png")

    fun build(document: CharacterDocument, target: ExportTarget, image: ByteArray?, xml: String?): ConvertedOutput {
        require(document.id.isNotBlank()) { "Enter a character id." }
        val animations = document.animations.filter { it.name.isNotBlank() && it.prefix.isNotBlank() }
        require(animations.isNotEmpty()) { "Give at least one animation a name and a prefix." }
        val duplicate = animations.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }?.key
        require(duplicate == null) { "The animation \"$duplicate\" is used more than once." }

        val report = ReportBuilder()
        val files = ArrayList<OutputFile>()
        val vsliceJson = document.toJson()
        val assetPath = imagePath(document.assetPath)
        fun sheet(path: String) {
            image?.let { files += OutputFile.of("images/$path.png", null, it) }
            xml?.let { files += OutputFile.of("images/$path.xml", it, null) }
        }
        when (target) {
            ExportTarget.VSLICE -> {
                files += OutputFile.of("data/characters/${document.id}.json", vsliceJson, null)
                sheet(assetPath)
            }
            ExportTarget.PSYCH, ExportTarget.CODENAME -> {
                val psych = psych(document, vsliceJson, xml, report)
                val psychImage = imagePath(JSONObject(psych.content).optString("image").ifEmpty { assetPath })
                if (target == ExportTarget.PSYCH) {
                    files += psych
                    sheet(psychImage)
                } else {
                    val converter = PsychToCodenameCharactersConverter()
                    val item = PickedText(psych.relativePath, psych.name, psych.relativePath, psych.content, "character", "", document.slot)
                    val result = converter.build(listOf(item))
                    files += result.files.filter { it.relativePath.startsWith("data/") }
                    result.report.warnings.forEach { report.warn(it) }
                    sheet("characters/" + PsychToCodenameCharacter.spriteFor(psychImage).first)
                }
            }
        }
        report.stat("Animations", animations.size)
        report.stat("Files", files.size)
        val names = animations.map { it.name }.toSet()
        val missing = listOf("idle", "singLEFT", "singDOWN", "singUP", "singRIGHT")
            .filter { it !in names && !(it == "idle" && "danceLeft" in names && "danceRight" in names) }
        if (missing.isNotEmpty()) report.warn("Missing common animations: ${missing.joinToString()}.")
        if (image == null || xml == null) report.warn("No spritesheet loaded: add images/$assetPath.png and .xml yourself.")
        return ConvertedOutput(files, report.build())
    }

    private fun psych(document: CharacterDocument, json: String, xml: String?, report: ReportBuilder): OutputFile {
        val converter = ReverseCharactersConverter()
        val items = ArrayList<PickedText>()
        items += PickedText("${document.id}.json", "${document.id}.json", "data/characters/${document.id}.json", json, "character", "", document.slot)
        if (xml != null) {
            val name = imagePath(document.assetPath).substringAfterLast('/') + ".xml"
            items += PickedText(name, name, "images/$name", xml, "atlas", "")
        }
        val result = converter.build(items)
        result.report.warnings.forEach { report.warn(it) }
        return result.files.first { it.bytes == null }
    }
}
