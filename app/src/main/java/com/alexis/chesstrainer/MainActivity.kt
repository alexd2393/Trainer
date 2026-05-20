package com.alexis.chesstrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.alexis.chesstrainer.data.LocalGameRepository
import com.alexis.chesstrainer.engine.StockfishEngineAnalyzer
import com.alexis.chesstrainer.ui.ChessTrainerApp
import com.alexis.chesstrainer.ui.ChessTrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalGameRepository(this)
        val analyzer = StockfishEngineAnalyzer()

        setContent {
            ChessTrainerTheme {
                ChessTrainerApp(repository = repository, analyzer = analyzer)
            }
        }
    }
}
