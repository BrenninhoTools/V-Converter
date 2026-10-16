package com.brenninhotools.vconverter.chart.vslice

data class VSliceTimeChange(
    val time: Double,
    val beat: Double,
    val bpm: Double,
    val numerator: Int = 4,
    val denominator: Int = 4
)

data class VSliceCharacters(
    val player: String,
    val girlfriend: String,
    val opponent: String,
    val instrumental: String = "",
    val playerVocals: List<String>? = null,
    val opponentVocals: List<String>? = null
)

data class VSliceMetadata(
    val songName: String,
    val artist: String,
    val charter: String,
    val difficulties: List<String>,
    val characters: VSliceCharacters,
    val stage: String,
    val noteStyle: String,
    val timeChanges: List<VSliceTimeChange>,
    val previewStart: Int = 0,
    val previewEnd: Int = 15000,
    val songVariations: List<String> = emptyList(),
    val generatedBy: String
)

data class VSliceNote(
    val time: Double,
    val direction: Int,
    val length: Double,
    val kind: String
)

data class VSliceEvent(
    val time: Double,
    val kind: String,
    val value: Map<String, Any>
)

data class VSliceChart(
    val scrollSpeed: Map<String, Double>,
    val events: List<VSliceEvent>,
    val notes: Map<String, List<VSliceNote>>,
    val generatedBy: String
)
