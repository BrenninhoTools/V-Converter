package com.brenninhotools.vconverter.chart.psych

data class PsychNote(
    val time: Double,
    val lane: Int,
    val length: Double,
    val type: String
) {
    val isPlayer: Boolean get() = lane < 4
    val direction: Int get() = lane % 4
}

data class PsychSection(
    val beats: Double,
    val mustHit: Boolean,
    val gfSection: Boolean,
    val altAnim: Boolean,
    val changeBpm: Boolean,
    val bpm: Double,
    val notes: List<PsychNote>
)

data class PsychEvent(
    val time: Double,
    val name: String,
    val value1: String,
    val value2: String
)

data class PsychSong(
    val name: String,
    val format: String,
    val bpm: Double,
    val speed: Double,
    val offset: Double,
    val needsVoices: Boolean,
    val player1: String,
    val player2: String,
    val girlfriend: String,
    val stage: String,
    val arrowSkin: String,
    val sections: List<PsychSection>,
    val events: List<PsychEvent>,
    val invalidNotes: Int,
    val wrappedLanes: Int
) {
    val usesAbsoluteLanes: Boolean get() = format.startsWith("psych_v1")
}
