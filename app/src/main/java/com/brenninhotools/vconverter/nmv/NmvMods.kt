package com.brenninhotools.vconverter.nmv

import com.brenninhotools.vconverter.codename.CodenameModScan
import com.brenninhotools.vconverter.codename.CodenameToPsychMod
import com.brenninhotools.vconverter.codename.PsychToCodenameMod
import com.brenninhotools.vconverter.core.Report
import com.brenninhotools.vconverter.core.ReportBuilder
import com.brenninhotools.vconverter.mod.ModConversionResult
import com.brenninhotools.vconverter.mod.ModConverter
import com.brenninhotools.vconverter.mod.ModFiles
import com.brenninhotools.vconverter.mod.ModInfo
import com.brenninhotools.vconverter.mod.ModProgress
import com.brenninhotools.vconverter.mod.ModScan
import com.brenninhotools.vconverter.mod.ModScanner
import com.brenninhotools.vconverter.mod.ModWriter
import com.brenninhotools.vconverter.reverse.VSliceModConverter
import com.brenninhotools.vconverter.reverse.VSliceModScan

enum class NmvTarget { PSYCH, VSLICE, CODENAME, FPSPLUS }

class NmvToMod(
    private val files: ModFiles,
    private val scan: NmvScan,
    private val writer: ModWriter,
    private val onProgress: (ModProgress) -> Unit = {}
) {
    fun convert(info: ModInfo, target: NmvTarget): ModConversionResult {
        if (target == NmvTarget.FPSPLUS) return com.brenninhotools.vconverter.fpsplus.ToFpsMod.fromNmv(files, scan, writer, info, onProgress)
        val view = NmvAsPsychFiles(files, scan)
        val result = when (target) {
            NmvTarget.PSYCH -> toPsych(view, info)
            NmvTarget.VSLICE -> ModConverter(view, ModScanner.scan(view), writer, onProgress).convert(info)
            NmvTarget.CODENAME -> PsychToCodenameMod(view, ModScanner.scan(view), writer, onProgress).convert(info)
            NmvTarget.FPSPLUS -> throw IllegalStateException()
        }
        val notes = ReportBuilder()
        notes.section(
            "NightmareVision",
            listOf(
                "The mod was read as NightmareVision: songs/<song>/charts became Psych charts, data/characters, data/stages/<stage>/data.json, data/weeks and the story menu images were mapped to their Psych places first.",
                "nmv2 charts use absolute lanes; lane 0 is the player and lane 1 the opponent. Extra lanes have no Psych equivalent and are dropped."
            )
        )
        view.warnings.distinct().forEach { notes.warn(it) }
        return ModConversionResult(result.filesWritten, result.report + notes.build())
    }

    private fun toPsych(view: NmvAsPsychFiles, info: ModInfo): ModConversionResult {
        val report = ReportBuilder()
        var written = 0
        val total = view.paths.size
        var hasPack = false
        view.paths.forEachIndexed { index, path ->
            onProgress(ModProgress(index, total, path))
            if (path == "pack.json") {
                hasPack = true
                val text = view.open(path).use { it.readBytes().decodeToString() }
                val json = runCatching { com.brenninhotools.vconverter.json.JSONObject(text) }.getOrNull()
                if (json != null && info.title.isNotBlank()) {
                    json.put("name", info.title)
                    if (info.description.isNotBlank()) json.put("description", info.description)
                    writer.writeText(path, json.toString(2))
                } else writer.writeText(path, text)
            } else writer.copy(path) { view.open(path) }
            written++
        }
        if (!hasPack) {
            val pack = com.brenninhotools.vconverter.json.JSONObject().put("name", info.title).put("description", info.description).put("restart", false).put("runsGlobally", false)
            writer.writeText("pack.json", pack.toString(2))
            written++
        }
        onProgress(ModProgress(total, total, "Done"))
        report.stat("Songs", scan.songs.size)
        report.stat("Characters", scan.characters.size)
        report.stat("Stages", scan.stages.size)
        report.stat("Weeks", scan.weeks.size)
        report.stat("Scripts", scan.scripts)
        report.stat("Files", written)
        report.section(
            "Scripts",
            listOf(
                "NightmareVision HScript became Psych HScript: onLoad became onCreate, funkin.* imports were mapped to Psych classes and ClientPrefs fields moved to ClientPrefs.data.",
                "Psych runs .hx files from data/<song>/, scripts/, stages/, characters/, custom_events/ and custom_notetypes/."
            )
        )
        return ModConversionResult(written, report.build())
    }
}

object ToNmvMod {
    private fun result(nmv: PsychToNmvWriter, base: Report?, extra: List<Pair<String, Any>> = emptyList()): ModConversionResult {
        nmv.finish()
        val report = ReportBuilder()
        extra.forEach { (label, value) -> report.stat(label, value) }
        report.stat("Scripts as NightmareVision HScript", nmv.scripts)
        report.section(
            "NightmareVision",
            listOf(
                "Charts are in songs/<song>/charts/<difficulty>.json (format nmv2) with meta.json, audio in songs/<song>/audio.",
                "Characters went to data/characters, stages to data/stages/<stage>/data.json with script.hx, weeks to data/weeks and global scripts to data/scripts.",
                "Lua scripts became HScript; the Psych functions they use are written into each script, so no extra library is needed."
            )
        )
        val built = report.build()
        return ModConversionResult(nmv.files, (base?.let { it + built } ?: built) + nmv.report.build())
    }

    fun fromPsych(files: ModFiles, scan: ModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val nmv = PsychToNmvWriter(writer, info)
        val roots = listOf(scan.root) + scan.otherRoots
        val inside = files.paths.filter { p -> roots.any { p.startsWith(it) } && p !in scan.placeholders.values }
        inside.forEachIndexed { index, path ->
            progress(ModProgress(index, inside.size, path))
            val root = roots.filter { path.startsWith(it) }.maxByOrNull { it.length } ?: scan.root
            val rel = com.brenninhotools.vconverter.mod.PsychLayout.normalize(path.removePrefix(root))
            if (rel.isNotEmpty()) nmv.copy(rel) { files.open(path) }
        }
        progress(ModProgress(inside.size, inside.size, "Done"))
        return result(nmv, null, listOf("Songs" to scan.songs.size, "Characters" to scan.characters.size, "Stages" to scan.stages.size, "Weeks" to scan.weeks.size))
    }

    fun fromVSlice(files: ModFiles, scan: VSliceModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val nmv = PsychToNmvWriter(writer, info)
        val base = VSliceModConverter(files, scan, nmv, progress).convert(info)
        return result(nmv, base.report)
    }

    fun fromCodename(files: ModFiles, scan: CodenameModScan, writer: ModWriter, info: ModInfo, progress: (ModProgress) -> Unit = {}): ModConversionResult {
        val nmv = PsychToNmvWriter(writer, info)
        val base = CodenameToPsychMod(files, scan, nmv, progress).convert(info)
        return result(nmv, base.report)
    }
}
