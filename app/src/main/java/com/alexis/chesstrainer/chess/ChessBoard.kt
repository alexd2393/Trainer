package com.alexis.chesstrainer.chess

import com.alexis.chesstrainer.data.GamePhase
import kotlin.math.abs

class ChessBoard private constructor(
    private val squares: Array<CharArray>,
    private var whiteToMove: Boolean,
    private var castlingRights: String,
    private var enPassantTarget: String,
    private var halfMoveClock: Int,
    private var fullMoveNumber: Int
) {
    fun sideToMoveIsWhite(): Boolean = whiteToMove

    fun phase(): GamePhase {
        val pieces = squares.sumOf { row -> row.count { it != '.' } }
        val queens = squares.sumOf { row -> row.count { it == 'Q' || it == 'q' } }
        return when {
            pieces <= 12 || queens == 0 && pieces <= 18 -> GamePhase.ENDGAME
            fullMoveNumber <= 10 -> GamePhase.OPENING
            else -> GamePhase.MIDDLEGAME
        }
    }

    fun materialScore(): Int = materialScoreFromBoard(squares)

    fun toFen(): String {
        val board = buildString {
            for (row in 0..7) {
                var empty = 0
                for (col in 0..7) {
                    val piece = squares[row][col]
                    if (piece == '.') {
                        empty += 1
                    } else {
                        if (empty > 0) {
                            append(empty)
                            empty = 0
                        }
                        append(piece)
                    }
                }
                if (empty > 0) append(empty)
                if (row < 7) append('/')
            }
        }
        return listOf(
            board,
            if (whiteToMove) "w" else "b",
            castlingRights.ifBlank { "-" },
            enPassantTarget,
            halfMoveClock.toString(),
            fullMoveNumber.toString()
        ).joinToString(" ")
    }

    fun applySan(rawSan: String): Boolean {
        val san = sanitize(rawSan)
        if (san.isBlank()) return false
        if (san == "O-O" || san == "O-O-O") return applyCastle(san == "O-O")
        if (coordinateMoveRegex.matches(san)) return applyCoordinateMove(san)

        val targetMatch = targetRegex.findAll(san).lastOrNull() ?: return false
        val target = targetMatch.groupValues[1]
        val promotion = targetMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.first()
        val targetRow = rankToRow(target[1])
        val targetCol = fileToCol(target[0])
        val pieceType = if (san.first() in "KQRBN") san.first() else 'P'
        val beforeTarget = san.substring(0, targetMatch.range.first)
        val disambiguation = disambiguationFor(pieceType, beforeTarget)
        val candidates = findCandidates(pieceType, targetRow, targetCol, san.contains('x'), disambiguation)

        val from = candidates.firstOrNull() ?: return false
        movePiece(from.first, from.second, targetRow, targetCol, promotion)
        return true
    }

    private fun applyCoordinateMove(move: String): Boolean {
        val fromCol = fileToCol(move[0])
        val fromRow = rankToRow(move[1])
        val toCol = fileToCol(move[2])
        val toRow = rankToRow(move[3])
        val promotion = move.getOrNull(4)?.uppercaseChar()
        val piece = squares[fromRow][fromCol]
        if (piece == '.' || isWhite(piece) != whiteToMove) return false
        if (!canMove(piece, fromRow, fromCol, toRow, toCol, true)) return false
        movePiece(fromRow, fromCol, toRow, toCol, promotion)
        return true
    }

    private fun applyCastle(shortCastle: Boolean): Boolean {
        val row = if (whiteToMove) 7 else 0
        val kingCol = 4
        val rookCol = if (shortCastle) 7 else 0
        val kingTarget = if (shortCastle) 6 else 2
        val rookTarget = if (shortCastle) 5 else 3
        val king = squares[row][kingCol]
        val rook = squares[row][rookCol]
        if (king.uppercaseChar() != 'K' || rook.uppercaseChar() != 'R') return false
        squares[row][kingCol] = '.'
        squares[row][rookCol] = '.'
        squares[row][kingTarget] = king
        squares[row][rookTarget] = rook
        removeCastlingRightsForKing(whiteToMove)
        finishMove(wasPawnMove = false, wasCapture = false, newEnPassant = "-")
        return true
    }

    private fun findCandidates(
        pieceType: Char,
        targetRow: Int,
        targetCol: Int,
        isCapture: Boolean,
        disambiguation: String
    ): List<Pair<Int, Int>> {
        return buildList {
            for (row in 0..7) {
                for (col in 0..7) {
                    val piece = squares[row][col]
                    if (piece == '.') continue
                    if (isWhite(piece) != whiteToMove) continue
                    if (piece.uppercaseChar() != pieceType) continue
                    if (!matchesDisambiguation(row, col, disambiguation)) continue
                    if (canMove(piece, row, col, targetRow, targetCol, isCapture)) {
                        add(row to col)
                    }
                }
            }
        }
    }

    private fun canMove(
        piece: Char,
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        notationCapture: Boolean
    ): Boolean {
        if (fromRow == toRow && fromCol == toCol) return false
        val target = squares[toRow][toCol]
        if (target != '.' && isWhite(target) == isWhite(piece)) return false
        val rowDelta = toRow - fromRow
        val colDelta = toCol - fromCol
        return when (piece.uppercaseChar()) {
            'P' -> canPawnMove(piece, fromRow, fromCol, toRow, toCol, notationCapture)
            'N' -> (abs(rowDelta) to abs(colDelta)) in setOf(1 to 2, 2 to 1)
            'B' -> abs(rowDelta) == abs(colDelta) && pathIsClear(fromRow, fromCol, toRow, toCol)
            'R' -> (rowDelta == 0 || colDelta == 0) && pathIsClear(fromRow, fromCol, toRow, toCol)
            'Q' -> (rowDelta == 0 || colDelta == 0 || abs(rowDelta) == abs(colDelta)) &&
                pathIsClear(fromRow, fromCol, toRow, toCol)
            'K' -> abs(rowDelta) <= 1 && abs(colDelta) <= 1
            else -> false
        }
    }

    private fun canPawnMove(
        piece: Char,
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        notationCapture: Boolean
    ): Boolean {
        val direction = if (isWhite(piece)) -1 else 1
        val startRow = if (isWhite(piece)) 6 else 1
        val target = squares[toRow][toCol]
        val enPassant = squareName(toRow, toCol) == enPassantTarget

        if (notationCapture) {
            return abs(toCol - fromCol) == 1 &&
                toRow - fromRow == direction &&
                (target != '.' && isWhite(target) != isWhite(piece) || enPassant)
        }

        val oneStep = toCol == fromCol && toRow - fromRow == direction && target == '.'
        val twoSteps = toCol == fromCol &&
            fromRow == startRow &&
            toRow - fromRow == direction * 2 &&
            target == '.' &&
            squares[fromRow + direction][fromCol] == '.'
        return oneStep || twoSteps
    }

    private fun movePiece(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int, promotion: Char?) {
        val piece = squares[fromRow][fromCol]
        val target = squares[toRow][toCol]
        val wasPawnMove = piece.uppercaseChar() == 'P'
        val enPassantCapture = wasPawnMove && target == '.' && fromCol != toCol && squareName(toRow, toCol) == enPassantTarget
        if (enPassantCapture) {
            squares[fromRow][toCol] = '.'
        }

        squares[fromRow][fromCol] = '.'
        val promotedPiece = promotion?.let { if (isWhite(piece)) it.uppercaseChar() else it.lowercaseChar() }
        squares[toRow][toCol] = promotedPiece ?: piece

        updateCastlingRights(piece, fromRow, fromCol, target, toRow, toCol)

        val newEnPassant = if (wasPawnMove && abs(toRow - fromRow) == 2) {
            squareName((fromRow + toRow) / 2, fromCol)
        } else {
            "-"
        }
        finishMove(wasPawnMove, target != '.' || enPassantCapture, newEnPassant)
    }

    private fun finishMove(wasPawnMove: Boolean, wasCapture: Boolean, newEnPassant: String) {
        halfMoveClock = if (wasPawnMove || wasCapture) 0 else halfMoveClock + 1
        enPassantTarget = newEnPassant
        if (!whiteToMove) fullMoveNumber += 1
        whiteToMove = !whiteToMove
    }

    private fun updateCastlingRights(piece: Char, fromRow: Int, fromCol: Int, captured: Char, toRow: Int, toCol: Int) {
        if (piece.uppercaseChar() == 'K') removeCastlingRightsForKing(isWhite(piece))
        if (piece.uppercaseChar() == 'R') removeCastlingRightsForRook(fromRow, fromCol)
        if (captured.uppercaseChar() == 'R') removeCastlingRightsForRook(toRow, toCol)
    }

    private fun removeCastlingRightsForKing(white: Boolean) {
        castlingRights = if (white) {
            castlingRights.replace("K", "").replace("Q", "")
        } else {
            castlingRights.replace("k", "").replace("q", "")
        }
    }

    private fun removeCastlingRightsForRook(row: Int, col: Int) {
        castlingRights = when (row to col) {
            7 to 0 -> castlingRights.replace("Q", "")
            7 to 7 -> castlingRights.replace("K", "")
            0 to 0 -> castlingRights.replace("q", "")
            0 to 7 -> castlingRights.replace("k", "")
            else -> castlingRights
        }
    }

    private fun pathIsClear(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        val rowStep = (toRow - fromRow).compareTo(0)
        val colStep = (toCol - fromCol).compareTo(0)
        var row = fromRow + rowStep
        var col = fromCol + colStep
        while (row != toRow || col != toCol) {
            if (squares[row][col] != '.') return false
            row += rowStep
            col += colStep
        }
        return true
    }

    private fun matchesDisambiguation(row: Int, col: Int, disambiguation: String): Boolean {
        if (disambiguation.isBlank()) return true
        val fileOk = disambiguation.firstOrNull { it in 'a'..'h' }?.let { fileToCol(it) == col } ?: true
        val rankOk = disambiguation.firstOrNull { it in '1'..'8' }?.let { rankToRow(it) == row } ?: true
        return fileOk && rankOk
    }

    private fun disambiguationFor(pieceType: Char, beforeTarget: String): String {
        val withoutCapture = beforeTarget.replace("x", "")
        return if (pieceType == 'P') {
            withoutCapture.filter { it in 'a'..'h' || it in '1'..'8' }
        } else {
            withoutCapture.drop(1).filter { it in 'a'..'h' || it in '1'..'8' }
        }
    }

    private fun sanitize(rawSan: String): String {
        var san = rawSan.trim()
            .replace('0', 'O')
            .replace("e.p.", "")
            .replace("ep", "")
        while (san.lastOrNull() in setOf('+', '#', '!', '?')) {
            san = san.dropLast(1)
        }
        return san
    }

    companion object {
        private val targetRegex = Regex("""([a-h][1-8])(?:=?([QRBNqrbn]))?""")
        private val coordinateMoveRegex = Regex("""^[a-h][1-8][a-h][1-8][qrbnQRBN]?$""")

        fun start(): ChessBoard {
            return fromFen(START_FEN)
        }

        fun fromFen(fen: String): ChessBoard {
            val parts = fen.split(" ")
            val boardPart = parts.getOrElse(0) { START_FEN.substringBefore(" ") }
            val rows = boardPart.split("/")
            val board = Array(8) { CharArray(8) { '.' } }
            rows.take(8).forEachIndexed { rowIndex, row ->
                var col = 0
                row.forEach { char ->
                    if (char.isDigit()) {
                        repeat(char.digitToInt()) {
                            if (col <= 7) board[rowIndex][col++] = '.'
                        }
                    } else if (col <= 7) {
                        board[rowIndex][col++] = char
                    }
                }
            }
            return ChessBoard(
                squares = board,
                whiteToMove = parts.getOrNull(1) != "b",
                castlingRights = parts.getOrNull(2)?.takeIf { it != "-" } ?: "",
                enPassantTarget = parts.getOrNull(3) ?: "-",
                halfMoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                fullMoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1
            )
        }

        fun materialScoreFromFen(fen: String): Int = fromFen(fen).materialScore()

        fun whiteToMoveFromFen(fen: String): Boolean = fen.split(" ").getOrNull(1) != "b"

        fun boardRowsFromFen(fen: String): List<List<Char>> {
            return fromFen(fen).squares.map { it.toList() }
        }

        private fun materialScoreFromBoard(board: Array<CharArray>): Int {
            return board.sumOf { row ->
                row.sumOf { piece ->
                    val value = when (piece.uppercaseChar()) {
                        'P' -> 100
                        'N' -> 320
                        'B' -> 330
                        'R' -> 500
                        'Q' -> 900
                        else -> 0
                    }
                    if (piece.isUpperCase()) value else -value
                }
            }
        }

        private fun isWhite(piece: Char): Boolean = piece.isUpperCase()

        private fun fileToCol(file: Char): Int = file - 'a'

        private fun rankToRow(rank: Char): Int = 8 - rank.digitToInt()

        private fun squareName(row: Int, col: Int): String = "${'a' + col}${8 - row}"

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
