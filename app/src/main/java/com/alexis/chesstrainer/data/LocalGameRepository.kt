package com.alexis.chesstrainer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalGameRepository(context: Context) {
    private val storeFile = File(context.applicationContext.filesDir, "chess_trainer_games.json")

    fun loadGames(): List<GameRecord> {
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(storeFile.readText())
            val games = root.optJSONArray("games") ?: JSONArray()
            buildList {
                for (index in 0 until games.length()) {
                    add(games.getJSONObject(index).toGameRecord())
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveGames(games: List<GameRecord>) {
        val root = JSONObject()
        val gamesJson = JSONArray()
        games.forEach { gamesJson.put(it.toJson()) }
        root.put("schemaVersion", 1)
        root.put("games", gamesJson)
        storeFile.writeText(root.toString(2))
    }

    fun upsert(game: GameRecord): List<GameRecord> {
        val next = loadGames().filterNot { it.id == game.id } + game
        saveGames(next.sortedByDescending { it.importedAt })
        return next.sortedByDescending { it.importedAt }
    }

    fun deleteAll() {
        if (storeFile.exists()) storeFile.delete()
    }

    private fun GameRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("pgn", pgn)
            .put("tags", JSONObject(tags))
            .put("moves", JSONArray(moves))
            .put("positions", JSONArray(positions.map { it.toJson() }))
            .put("importedAt", importedAt)
            .put("parserWarnings", JSONArray(parserWarnings))
            .put("analysis", analysis?.toJson())
    }

    private fun PositionSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("moveIndex", moveIndex)
            .put("san", san)
            .put("fen", fen)
            .put("phase", phase.name)
    }

    private fun GameAnalysis.toJson(): JSONObject {
        return JSONObject()
            .put("generatedAt", generatedAt)
            .put("averageAccuracy", averageAccuracy)
            .put("criticalMoves", JSONArray(criticalMoves.map { it.toJson() }))
            .put("recommendations", JSONArray(recommendations))
    }

    private fun MoveAnalysis.toJson(): JSONObject {
        return JSONObject()
            .put("moveIndex", moveIndex)
            .put("san", san)
            .put("fenBefore", fenBefore)
            .put("fenAfter", fenAfter)
            .put("phase", phase.name)
            .put("quality", quality.name)
            .put("scoreBefore", scoreBefore)
            .put("scoreAfter", scoreAfter)
            .put("centipawnLoss", centipawnLoss)
            .put("theme", theme)
            .put("explanation", explanation)
            .put("recommendation", recommendation)
    }

    private fun JSONObject.toGameRecord(): GameRecord {
        return GameRecord(
            id = getString("id"),
            title = getString("title"),
            pgn = getString("pgn"),
            tags = optJSONObject("tags").toStringMap(),
            moves = optJSONArray("moves").toStringList(),
            positions = optJSONArray("positions").toPositions(),
            importedAt = optLong("importedAt"),
            parserWarnings = optJSONArray("parserWarnings").toStringList(),
            analysis = optJSONObject("analysis")?.toGameAnalysis()
        )
    }

    private fun JSONObject.toGameAnalysis(): GameAnalysis {
        return GameAnalysis(
            generatedAt = optLong("generatedAt"),
            averageAccuracy = optInt("averageAccuracy"),
            criticalMoves = optJSONArray("criticalMoves").toMoveAnalyses(),
            recommendations = optJSONArray("recommendations").toStringList()
        )
    }

    private fun JSONObject.toMoveAnalysis(): MoveAnalysis {
        return MoveAnalysis(
            moveIndex = optInt("moveIndex"),
            san = optString("san"),
            fenBefore = optString("fenBefore"),
            fenAfter = optString("fenAfter"),
            phase = enumValueOrDefault(optString("phase"), GamePhase.MIDDLEGAME),
            quality = enumValueOrDefault(optString("quality"), MoveQuality.GOOD),
            scoreBefore = optInt("scoreBefore"),
            scoreAfter = optInt("scoreAfter"),
            centipawnLoss = optInt("centipawnLoss"),
            theme = optString("theme"),
            explanation = optString("explanation"),
            recommendation = optString("recommendation")
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                add(optString(index))
            }
        }
    }

    private fun JSONArray?.toPositions(): List<PositionSnapshot> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    PositionSnapshot(
                        moveIndex = item.optInt("moveIndex"),
                        san = item.optString("san"),
                        fen = item.optString("fen"),
                        phase = enumValueOrDefault(item.optString("phase"), GamePhase.MIDDLEGAME)
                    )
                )
            }
        }
    }

    private fun JSONArray?.toMoveAnalyses(): List<MoveAnalysis> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                add(getJSONObject(index).toMoveAnalysis())
            }
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { key -> optString(key) }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
