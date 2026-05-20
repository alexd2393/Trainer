package com.alexis.chesstrainer.engine

import com.alexis.chesstrainer.data.GameAnalysis
import com.alexis.chesstrainer.data.GameRecord

interface EngineAnalyzer {
    fun analyze(game: GameRecord): GameAnalysis
}
