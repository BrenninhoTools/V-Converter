package com.brenninhotools.vconverter.core

enum class CharacterSlot(
    val label: String,
    val stageAnchorX: Double,
    val stageAnchorY: Double,
    val cameraBaseX: Double,
    val cameraBaseY: Double,
    val cameraSign: Double
) {
    PLAYER("Player", 219.5, 785.0, -100.0, -100.0, -1.0),
    OPPONENT("Opponent", 235.0, 785.0, 150.0, -100.0, 1.0),
    GIRLFRIEND("Girlfriend", 351.5, 657.0, 0.0, 0.0, 1.0)
}
