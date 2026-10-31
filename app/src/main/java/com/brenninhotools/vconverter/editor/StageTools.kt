package com.brenninhotools.vconverter.editor

import com.brenninhotools.vconverter.convert.CodenameStagesConverter
import com.brenninhotools.vconverter.convert.ConvertedOutput
import com.brenninhotools.vconverter.convert.FileConverter
import com.brenninhotools.vconverter.convert.PickedText
import com.brenninhotools.vconverter.convert.PsychStagesConverter
import com.brenninhotools.vconverter.convert.PsychToCodenameStagesConverter
import com.brenninhotools.vconverter.convert.ReverseStagesConverter
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.generator.Ids
import com.brenninhotools.vconverter.io.ImportedFile
import com.brenninhotools.vconverter.io.OutputFile
import com.brenninhotools.vconverter.reverse.VSliceParser
import com.brenninhotools.vconverter.platform.byteInputStream
import com.brenninhotools.vconverter.platform.inputStream

class StageAsset(val png: ByteArray, val xml: String?)

data class LoadedStage(val document: StageDocument, val assets: Map<String, StageAsset>, val source: String)

object StageAssets {
    fun key(path: String): String {
        val clean = path.trim().replace('\\', '/').substringAfter(':')
        val parts = clean.split('/')
        val start = parts.indexOfLast { it.equals("images", ignoreCase = true) }
        val relative = (if (start >= 0) parts.drop(start + 1) else parts).joinToString("/")
        return relative.removeSuffix(".png").removeSuffix(".PNG").removeSuffix(".xml").lowercase()
    }

    fun find(assets: Map<String, StageAsset>, assetPath: String): StageAsset? {
        val k = key(assetPath)
        return assets[k] ?: assets.entries.firstOrNull { it.key.substringAfterLast('/') == k.substringAfterLast('/') }?.value
    }

    fun collect(files: List<Pair<String, ByteArray>>): Map<String, StageAsset> {
        val xmls = files.filter { it.first.endsWith(".xml", true) }.associate { key(it.first) to it.second.decodeToString() }
            .filterValues { SpriteAtlas.parse(it).frames.isNotEmpty() }
        return files.filter { it.first.endsWith(".png", true) }.associate { (path, bytes) ->
            val k = key(path)
            k to StageAsset(bytes, xmls[k])
        }
    }
}

object StageLoader {
    private fun stem(path: String) = path.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')

    fun load(files: List<Pair<String, ByteArray>>): LoadedStage {
        val assets = StageAssets.collect(files)
        val jsons = files.filter { it.first.endsWith(".json", true) }.map { it.first to it.second.decodeToString() }
        val vslice = jsons.firstNotNullOfOrNull { (path, text) ->
            runCatching { VSliceParser.stage(text) }.getOrNull()?.let { StageDocument.fromVSlice(Ids.of(stem(path)).ifEmpty { "stage" }, text) }
        }
        val (document, source) = vslice?.let { it to "V-Slice" }
            ?: convert(PsychStagesConverter(), files.filter { it.first.substringAfterLast('.').lowercase() in setOf("json", "lua", "xml") })?.let { it to "Psych Engine" }
            ?: convert(CodenameStagesConverter(), files.filter { it.first.substringAfterLast('.').lowercase() in setOf("xml", "png") })?.let { it to "Codename Engine" }
            ?: throw IllegalArgumentException("No stage found. Pick a V-Slice or Psych stage .json or a Codename stage .xml, with its images.")
        return LoadedStage(document, assets, source)
    }

    private fun convert(converter: FileConverter, files: List<Pair<String, ByteArray>>): StageDocument? {
        val items = files.mapNotNull { (path, bytes) ->
            val clean = path.replace('\\', '/')
            val file = ImportedFile(clean.substringAfterLast('/'), clean, true) { bytes.inputStream() }
            if (!converter.accept(file)) return@mapNotNull null
            runCatching { converter.classify(file, converter.read(file)) }.getOrNull()
        }
        if (!converter.canConvert(items)) return null
        val output = runCatching { converter.build(items) }.getOrNull() ?: return null
        val json = output.files.firstOrNull { it.bytes == null && it.relativePath.startsWith("data/stages/") && it.name.endsWith(".json") } ?: return null
        return runCatching { StageDocument.fromVSlice(json.name.substringBeforeLast('.'), json.content) }.getOrNull()?.copy(rawJson = null)
    }
}

object StageExport {
    private fun imagePath(raw: String) = raw.trim().replace('\\', '/').substringAfter(':').removePrefix("images/").removeSuffix(".png")

    fun build(document: StageDocument, target: ExportTarget, assets: Map<String, StageAsset>): ConvertedOutput {
        require(document.id.isNotBlank()) { "Enter a stage id." }
        require(document.cameraZoom > 0) { "Camera zoom must be above 0." }
        val duplicate = document.props.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }?.key
        require(duplicate == null) { "Two props are called \"$duplicate\"." }
        val report = ReportBuilder()
        val files = ArrayList<OutputFile>()
        val json = document.toJson()
        when (target) {
            ExportTarget.VSLICE -> files += OutputFile.of("data/stages/${document.id}.json", json, null)
            ExportTarget.PSYCH, ExportTarget.CODENAME -> {
                val psych = psych(document, json, assets, report)
                if (target == ExportTarget.PSYCH) {
                    files += psych
                } else {
                    val converter = PsychToCodenameStagesConverter()
                    val items = psych.map { file ->
                        converter.classify(ImportedFile(file.name, file.relativePath, true) { file.content.byteInputStream() }, file.content)
                    }
                    val result = converter.build(items)
                    files += result.files
                    result.report.warnings.forEach { report.warn(it) }
                    result.report.sections.forEach { report.section(it.title, it.lines) }
                }
            }
        }
        val missing = ArrayList<String>()
        val written = HashSet<String>()
        document.props.filterNot { it.isSolid }.forEach { prop ->
            val path = imagePath(prop.assetPath)
            if (!written.add(path.lowercase())) return@forEach
            val asset = StageAssets.find(assets, prop.assetPath)
            if (asset == null) {
                missing += "images/$path.png"
                return@forEach
            }
            files += OutputFile.of("images/$path.png", null, asset.png)
            asset.xml?.let { files += OutputFile.of("images/$path.xml", it, null) }
        }
        report.stat("Props", document.props.size)
        report.stat("Files", files.size)
        if (missing.isNotEmpty()) report.section("Images to add", missing)
        return ConvertedOutput(files, report.build())
    }

    private fun psych(document: StageDocument, json: String, assets: Map<String, StageAsset>, report: ReportBuilder): List<OutputFile> {
        val converter = ReverseStagesConverter()
        val items = ArrayList<PickedText>()
        items += PickedText("${document.id}.json", "${document.id}.json", "data/stages/${document.id}.json", json, "stage", "")
        document.props.filter { it.prefix.isNotEmpty() }.forEach { prop ->
            val xml = StageAssets.find(assets, prop.assetPath)?.xml ?: return@forEach
            val path = imagePath(prop.assetPath)
            items += PickedText("$path.xml", "${path.substringAfterLast('/')}.xml", "images/$path.xml", xml, "atlas", path)
        }
        val result = converter.build(items)
        result.report.warnings.forEach { report.warn(it) }
        result.report.sections.forEach { report.section(it.title, it.lines) }
        return result.files.filter { it.bytes == null }
    }
}
