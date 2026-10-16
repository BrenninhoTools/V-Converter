package com.brenninhotools.vconverter.generator

import com.brenninhotools.vconverter.chart.vslice.VSliceChart
import com.brenninhotools.vconverter.chart.vslice.VSliceCharacters
import com.brenninhotools.vconverter.chart.vslice.VSliceEvent
import com.brenninhotools.vconverter.chart.vslice.VSliceMetadata
import com.brenninhotools.vconverter.chart.vslice.VSliceTimeChange
import com.brenninhotools.vconverter.chart.vslice.VSliceWriter
import com.brenninhotools.vconverter.core.ReportBuilder
import org.json.JSONArray
import org.json.JSONObject

data class SongSpec(
    val id: String,
    val name: String,
    val artist: String = "",
    val charter: String = "",
    val bpm: Double = 120.0,
    val numerator: Int = 4,
    val denominator: Int = 4,
    val scrollSpeed: Double = 1.0,
    val difficulties: List<String> = listOf("easy", "normal", "hard"),
    val player: String = "bf",
    val opponent: String = "dad",
    val girlfriend: String = "gf",
    val stage: String = "mainStage",
    val noteStyle: String = "funkin",
    val previewStart: Int = 0,
    val previewEnd: Int = 15000,
    val instrumental: PickedAsset? = null,
    val voices: PickedAsset? = null,
    val playerVoices: PickedAsset? = null,
    val opponentVoices: PickedAsset? = null
)

object SongGenerator {
    val defaultDifficulties = listOf("easy", "normal", "hard")
    val variationDifficulties = listOf("erect", "nightmare")
    val allDifficulties = defaultDifficulties + variationDifficulties
    val noteStyles = listOf("funkin", "pixel")
    const val GENERATED_BY = "V-Converter Generator"

    fun generate(spec: SongSpec): Generated {
        val id = Ids.of(spec.id)
        require(id.isNotEmpty()) { "Enter a song id." }
        require(spec.bpm > 0 && spec.bpm < 1000) { "BPM must be between 1 and 999." }
        require(spec.scrollSpeed > 0) { "Scroll speed must be above 0." }
        require(spec.numerator in 1..32 && spec.denominator in listOf(1, 2, 4, 8, 16)) { "Invalid time signature." }
        val main = spec.difficulties.filter { it in defaultDifficulties }
        val variation = spec.difficulties.filter { it in variationDifficulties }
        require(main.isNotEmpty() || variation.isNotEmpty()) { "Pick at least one difficulty." }

        val split = spec.playerVoices != null || spec.opponentVoices != null
        val characters = VSliceCharacters(
            player = spec.player.trim().ifEmpty { "bf" },
            girlfriend = spec.girlfriend.trim(),
            opponent = spec.opponent.trim().ifEmpty { "dad" },
            playerVocals = if (split) listOf(spec.player.trim().ifEmpty { "bf" }) else null,
            opponentVocals = if (split) listOf(spec.opponent.trim().ifEmpty { "dad" }) else null
        )
        val folder = "data/songs/$id"
        val files = mutableListOf<GenFile>()
        val report = ReportBuilder()

        fun write(difficulties: List<String>, suffix: String, variations: List<String>) {
            val metadata = VSliceMetadata(
                songName = spec.name.trim().ifEmpty { Ids.pretty(id) },
                artist = spec.artist.trim().ifEmpty { "Unknown" },
                charter = spec.charter.trim().ifEmpty { "Unknown" },
                difficulties = difficulties,
                characters = characters,
                stage = spec.stage.trim().ifEmpty { "mainStage" },
                noteStyle = spec.noteStyle,
                timeChanges = listOf(VSliceTimeChange(0.0, 0.0, spec.bpm, spec.numerator, spec.denominator)),
                previewStart = spec.previewStart,
                previewEnd = spec.previewEnd.coerceAtLeast(spec.previewStart),
                generatedBy = GENERATED_BY
            )
            val metadataJson = JSONObject(VSliceWriter.metadataJson(metadata))
            metadataJson.getJSONObject("playData").put("songVariations", JSONArray(variations))
            val chart = VSliceChart(
                scrollSpeed = difficulties.associateWith { spec.scrollSpeed },
                events = listOf(VSliceEvent(0.0, "FocusCamera", linkedMapOf("char" to 1))),
                notes = difficulties.associateWith { emptyList() },
                generatedBy = GENERATED_BY
            )
            files += GenFile("$folder/$id-metadata$suffix.json", metadataJson.toString(2))
            files += GenFile("$folder/$id-chart$suffix.json", VSliceWriter.chartJson(chart))
        }

        if (main.isNotEmpty()) {
            write(main, "", if (variation.isNotEmpty()) listOf("erect") else emptyList())
            if (variation.isNotEmpty()) write(variation, "-erect", emptyList())
        } else {
            write(variation, "", emptyList())
        }

        val audio = mutableListOf<String>()
        fun addAudio(asset: PickedAsset?, name: String) {
            if (asset == null) return
            require(asset.extension == "ogg") { "${asset.name}: V-Slice songs need .ogg audio." }
            files += GenFile("songs/$id/$name", bytes = asset.bytes)
            audio += name
        }
        addAudio(spec.instrumental, "Inst.ogg")
        if (split) {
            addAudio(spec.playerVoices, "Voices-${characters.player}.ogg")
            addAudio(spec.opponentVoices, "Voices-${characters.opponent}.ogg")
        } else {
            addAudio(spec.voices, "Voices.ogg")
        }

        report.stat("Difficulties", main.size + variation.size)
        report.stat("BPM", spec.bpm.toString().removeSuffix(".0"))
        report.stat("Files", files.size)
        val missing = mutableListOf<String>()
        if (spec.instrumental == null) missing += "songs/$id/Inst.ogg"
        if (!split && spec.voices == null) missing += "songs/$id/Voices.ogg (optional, or Voices-${characters.player}.ogg + Voices-${characters.opponent}.ogg)"
        if (missing.isNotEmpty()) report.section("Audio to add", missing)
        report.section(
            "Next steps",
            listOf(
                "The charts are empty: open the song in V-Slice's Chart Editor to place notes.",
                "Add the song to a week with the Level generator."
            )
        )
        if (audio.isNotEmpty()) report.section("Audio included", audio)
        return Generated(files, report.build())
    }
}
