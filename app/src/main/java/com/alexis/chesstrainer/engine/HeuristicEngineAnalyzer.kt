package com.alexis.chesstrainer.engine

import com.alexis.chesstrainer.chess.ChessBoard
import com.alexis.chesstrainer.data.GameAnalysis
import com.alexis.chesstrainer.data.GamePhase
import com.alexis.chesstrainer.data.GameRecord
import com.alexis.chesstrainer.data.MoveAnalysis
import com.alexis.chesstrainer.data.MoveQuality
import kotlin.math.roundToInt

class HeuristicEngineAnalyzer : EngineAnalyzer {
    override fun analyze(game: GameRecord): GameAnalysis {
        val moveAnalyses = buildList {
            for (index in 1 until game.positions.size) {
                val before = game.positions[index - 1]
                val after = game.positions[index]
                val moverWasWhite = ChessBoard.whiteToMoveFromFen(before.fen)
                val beforeScore = ChessBoard.materialScoreFromFen(before.fen)
                val afterScore = ChessBoard.materialScoreFromFen(after.fen)
                val gainForMover = if (moverWasWhite) afterScore - beforeScore else beforeScore - afterScore
                val loss = (-gainForMover).coerceAtLeast(0)
                val quality = qualityFor(loss)
                if (quality != MoveQuality.GOOD) {
                    val theme = themeFor(after.san, after.phase, loss)
                    add(
                        MoveAnalysis(
                            moveIndex = after.moveIndex,
                            san = after.san,
                            fenBefore = before.fen,
                            fenAfter = after.fen,
                            phase = after.phase,
                            quality = quality,
                            scoreBefore = beforeScore,
                            scoreAfter = afterScore,
                            centipawnLoss = loss,
                            theme = theme,
                            explanation = explanationFor(quality, theme, loss),
                            recommendation = recommendationFor(theme)
                        )
                    )
                }
            }
        }

        val averageLoss = if (moveAnalyses.isEmpty()) 0.0 else moveAnalyses.map { it.centipawnLoss }.average()
        val accuracy = (100 - averageLoss / 8.0).roundToInt().coerceIn(0, 100)

        return GameAnalysis(
            generatedAt = System.currentTimeMillis(),
            averageAccuracy = accuracy,
            criticalMoves = moveAnalyses,
            recommendations = recommendationsFor(moveAnalyses)
        )
    }

    private fun qualityFor(loss: Int): MoveQuality {
        return when {
            loss >= 350 -> MoveQuality.BLUNDER
            loss >= 180 -> MoveQuality.MISTAKE
            loss >= 80 -> MoveQuality.INACCURACY
            else -> MoveQuality.GOOD
        }
    }

    private fun themeFor(san: String, phase: GamePhase, loss: Int): String {
        return when {
            loss >= 500 -> "Material colgado"
            phase == GamePhase.ENDGAME -> "Finales"
            san.contains('x') -> "Calculo de capturas"
            san.firstOrNull() == 'Q' -> "Uso temprano de la dama"
            san.firstOrNull() == 'K' -> "Seguridad del rey"
            phase == GamePhase.OPENING -> "Apertura"
            else -> "Vision tactica"
        }
    }

    private fun explanationFor(quality: MoveQuality, theme: String, loss: Int): String {
        return when (quality) {
            MoveQuality.INACCURACY -> "La jugada parece ceder alrededor de $loss centipeones. El patron principal es $theme."
            MoveQuality.MISTAKE -> "La posicion empeora de forma clara, con una perdida aproximada de $loss centipeones. Conviene revisar $theme."
            MoveQuality.BLUNDER -> "Esta jugada cambia mucho la evaluacion material. La perdida aproximada es de $loss centipeones y apunta a $theme."
            MoveQuality.GOOD -> "La jugada mantiene la posicion."
        }
    }

    private fun recommendationFor(theme: String): String {
        return when (theme) {
            "Material colgado" -> "Antes de mover, revisa piezas sin defensa y amenazas directas del rival."
            "Calculo de capturas" -> "Entrena secuencias de captura: calcula capturas, recapturas y la pieza que queda suelta al final."
            "Finales" -> "Dedica sesiones cortas a finales tecnicos y conversion de ventajas pequenas."
            "Uso temprano de la dama" -> "Comprueba si la dama queda expuesta antes de desarrollarla o iniciar un ataque."
            "Seguridad del rey" -> "Prioriza la seguridad del rey y pregunta que jaques forzados tiene el rival."
            "Apertura" -> "Revisa tus primeras diez jugadas: desarrollo, centro y rey seguro antes de buscar tactica."
            else -> "Usa una pausa de blunder-check: jaques, capturas y amenazas del rival antes de confirmar."
        }
    }

    private fun recommendationsFor(criticalMoves: List<MoveAnalysis>): List<String> {
        if (criticalMoves.isEmpty()) {
            return listOf(
                "No aparecieron caidas materiales claras en esta partida.",
                "El siguiente paso es agregar Stockfish para detectar planes y tacticas no materiales."
            )
        }

        val topThemes = criticalMoves
            .groupingBy { it.theme }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)

        return buildList {
            topThemes.forEach { (theme, count) ->
                add("$theme aparece $count veces. ${recommendationFor(theme)}")
            }
            add("Repite las posiciones marcadas como error grave hasta encontrar una alternativa candidata.")
        }
    }
}
