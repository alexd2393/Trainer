package com.alexis.chesstrainer.chess

data class ParsedPgn(
    val title: String,
    val tags: Map<String, String>,
    val moves: List<String>,
    val warnings: List<String>
)

object PgnParser {
    private val tagRegex = Regex("""\[(\w+)\s+"([^"]*)"]""")
    private val resultTokens = setOf("1-0", "0-1", "1/2-1/2", "*")

    fun parse(rawPgn: String): ParsedPgn {
        val tags = tagRegex.findAll(rawPgn).associate { it.groupValues[1] to it.groupValues[2] }
        val body = rawPgn
            .replace(tagRegex, " ")
            .replace(Regex("""\{[^}]*}""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex(""";[^\n\r]*"""), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\$\d+"""), " ")
            .replace(Regex("""\d+\.(\.\.)?"""), " ")

        val moves = body
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it in resultTokens }
            .map { it.trimEnd(',', ';') }

        val warnings = buildList {
            if (moves.isEmpty()) add("No se detectaron jugadas en el PGN.")
            if (rawPgn.contains("(")) add("Se ignoraron variantes entre parentesis para esta version.")
            if (rawPgn.contains("{")) add("Se ignoraron comentarios PGN durante la importacion.")
        }

        return ParsedPgn(
            title = buildTitle(tags),
            tags = tags,
            moves = moves,
            warnings = warnings
        )
    }

    private fun buildTitle(tags: Map<String, String>): String {
        val white = tags["White"].orEmpty().ifBlank { "Blancas" }
        val black = tags["Black"].orEmpty().ifBlank { "Negras" }
        val date = tags["Date"].orEmpty().takeIf { it.isNotBlank() && it != "????.??.??" }
        return if (date == null) "$white vs $black" else "$white vs $black - $date"
    }
}
