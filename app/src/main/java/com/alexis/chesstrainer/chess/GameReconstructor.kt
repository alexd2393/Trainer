package com.alexis.chesstrainer.chess

import com.alexis.chesstrainer.data.PositionSnapshot

data class ReconstructionResult(
    val positions: List<PositionSnapshot>,
    val warnings: List<String>
)

object GameReconstructor {
    fun reconstruct(parsedPgn: ParsedPgn): ReconstructionResult {
        val board = ChessBoard.start()
        val positions = mutableListOf(
            PositionSnapshot(
                moveIndex = 0,
                san = "Inicio",
                fen = board.toFen(),
                phase = board.phase()
            )
        )
        val warnings = parsedPgn.warnings.toMutableList()

        parsedPgn.moves.forEachIndexed { index, san ->
            val applied = board.applySan(san)
            if (!applied) {
                warnings.add("No se pudo reconstruir la jugada ${index + 1}: $san")
                return@forEachIndexed
            }
            positions.add(
                PositionSnapshot(
                    moveIndex = index + 1,
                    san = san,
                    fen = board.toFen(),
                    phase = board.phase()
                )
            )
        }

        return ReconstructionResult(
            positions = positions,
            warnings = warnings.distinct()
        )
    }
}
