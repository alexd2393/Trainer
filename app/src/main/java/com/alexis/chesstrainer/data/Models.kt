package com.alexis.chesstrainer.data

enum class GamePhase(val label: String) {
    OPENING("Apertura"),
    MIDDLEGAME("Medio juego"),
    ENDGAME("Final")
}

enum class MoveQuality(val label: String) {
    GOOD("Buena"),
    INACCURACY("Imprecision"),
    MISTAKE("Error"),
    BLUNDER("Error grave")
}

data class PositionSnapshot(
    val moveIndex: Int,
    val san: String,
    val fen: String,
    val phase: GamePhase
)

data class MoveAnalysis(
    val moveIndex: Int,
    val san: String,
    val fenBefore: String,
    val fenAfter: String,
    val phase: GamePhase,
    val quality: MoveQuality,
    val scoreBefore: Int,
    val scoreAfter: Int,
    val centipawnLoss: Int,
    val theme: String,
    val explanation: String,
    val recommendation: String
)

data class GameAnalysis(
    val generatedAt: Long,
    val averageAccuracy: Int,
    val criticalMoves: List<MoveAnalysis>,
    val recommendations: List<String>
)

data class GameRecord(
    val id: String,
    val title: String,
    val pgn: String,
    val tags: Map<String, String>,
    val moves: List<String>,
    val positions: List<PositionSnapshot>,
    val importedAt: Long,
    val parserWarnings: List<String>,
    val analysis: GameAnalysis?
)

data class TrainingExercise(
    val id: String,
    val gameId: String,
    val title: String,
    val fen: String,
    val moveIndex: Int,
    val prompt: String,
    val hint: String,
    val solution: String
)
