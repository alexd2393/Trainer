package com.alexis.chesstrainer.engine

import com.alexis.chesstrainer.data.GameAnalysis
import com.alexis.chesstrainer.data.GameRecord

class StockfishEngineAnalyzer(
    private val fallback: EngineAnalyzer = HeuristicEngineAnalyzer()
) : EngineAnalyzer {
    override fun analyze(game: GameRecord): GameAnalysis {
        // Hook point for an Android Stockfish UCI process.
        // Keep the app offline: embed per-architecture binaries, copy the
        // selected binary to filesDir, mark it executable, then speak UCI.
        return fallback.analyze(game)
    }
}
