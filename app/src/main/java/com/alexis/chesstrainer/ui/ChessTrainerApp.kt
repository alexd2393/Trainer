package com.alexis.chesstrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexis.chesstrainer.chess.ChessBoard
import com.alexis.chesstrainer.chess.GameReconstructor
import com.alexis.chesstrainer.chess.ParsedPgn
import com.alexis.chesstrainer.chess.PgnParser
import com.alexis.chesstrainer.data.GameRecord
import com.alexis.chesstrainer.data.LocalGameRepository
import com.alexis.chesstrainer.data.MoveAnalysis
import com.alexis.chesstrainer.data.MoveQuality
import com.alexis.chesstrainer.data.TrainingExercise
import com.alexis.chesstrainer.engine.EngineAnalyzer

private enum class AppTab(val label: String) {
    SUMMARY("Resumen"),
    GAMES("Partidas"),
    TRAINING("Entrenar")
}

@Composable
fun ChessTrainerApp(
    repository: LocalGameRepository,
    analyzer: EngineAnalyzer
) {
    var games by remember { mutableStateOf(repository.loadGames()) }
    var selectedTab by remember { mutableStateOf(AppTab.SUMMARY) }
    var selectedGameId by remember { mutableStateOf(games.firstOrNull()?.id) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectedGame = games.firstOrNull { it.id == selectedGameId } ?: games.firstOrNull()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                games = games,
                onDeleteAll = { showDeleteDialog = true }
            )
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                AppTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                AppTab.SUMMARY -> SummaryScreen(
                    games = games,
                    onOpenGames = { selectedTab = AppTab.GAMES },
                    onOpenTraining = { selectedTab = AppTab.TRAINING }
                )

                AppTab.GAMES -> GamesScreen(
                    games = games,
                    selectedGame = selectedGame,
                    analyzer = analyzer,
                    onGameImported = { game ->
                        games = repository.upsert(game)
                        selectedGameId = game.id
                    },
                    onSelectGame = { selectedGameId = it.id }
                )

                AppTab.TRAINING -> TrainingScreen(
                    games = games,
                    exercises = buildExercises(games)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar datos locales") },
            text = { Text("Esto elimina partidas y analisis guardados en este telefono.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteAll()
                        games = emptyList()
                        selectedGameId = null
                        showDeleteDialog = false
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun Header(
    games: List<GameRecord>,
    onDeleteAll: () -> Unit
) {
    val criticalCount = games.sumOf { it.analysis?.criticalMoves?.size ?: 0 }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trainer personal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$criticalCount momentos criticos en ${games.size} partidas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDeleteAll, enabled = games.isNotEmpty()) {
                Text("Borrar")
            }
        }
    }
}

@Composable
private fun SummaryScreen(
    games: List<GameRecord>,
    onOpenGames: () -> Unit,
    onOpenTraining: () -> Unit
) {
    val criticalMoves = games.flatMap { it.analysis?.criticalMoves ?: emptyList() }
    val topThemes = criticalMoves.groupingBy { it.theme }.eachCount().entries.sortedByDescending { it.value }
    val averageAccuracy = games.mapNotNull { it.analysis?.averageAccuracy }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(title = "Partidas", value = games.size.toString(), modifier = Modifier.weight(1f))
            MetricTile(title = "Precision", value = if (games.isEmpty()) "-" else "$averageAccuracy%", modifier = Modifier.weight(1f))
            MetricTile(title = "Errores", value = criticalMoves.size.toString(), modifier = Modifier.weight(1f))
        }

        SectionSurface {
            Text("Debilidades principales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (topThemes.isEmpty()) {
                Text("Importa una partida PGN para empezar a construir tu perfil.")
            } else {
                topThemes.take(5).forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(theme.key, modifier = Modifier.weight(1f))
                        Text("${theme.value}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionSurface {
            Text("Guia de mejora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val recommendations = games.flatMap { it.analysis?.recommendations ?: emptyList() }.distinct().take(4)
            if (recommendations.isEmpty()) {
                Text("Tu primera recomendacion aparecera despues del primer analisis.")
            } else {
                recommendations.forEach { RecommendationLine(it) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenGames, modifier = Modifier.weight(1f)) {
                Text("Importar PGN")
            }
            OutlinedButton(onClick = onOpenTraining, modifier = Modifier.weight(1f), enabled = criticalMoves.isNotEmpty()) {
                Text("Entrenar")
            }
        }
    }
}

@Composable
private fun GamesScreen(
    games: List<GameRecord>,
    selectedGame: GameRecord?,
    analyzer: EngineAnalyzer,
    onGameImported: (GameRecord) -> Unit,
    onSelectGame: (GameRecord) -> Unit
) {
    var pgnInput by remember { mutableStateOf(samplePgn) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var manualMoves by remember { mutableStateOf<List<String>>(emptyList()) }
    var manualInput by remember { mutableStateOf("") }
    var manualMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionSurface {
                Text("Importar partida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pgnInput,
                    onValueChange = { pgnInput = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                    minLines = 5,
                    label = { Text("PGN") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val parsed = PgnParser.parse(pgnInput)
                        val reconstruction = GameReconstructor.reconstruct(parsed)
                        if (reconstruction.positions.size <= 1) {
                            importMessage = "No pude reconstruir la partida. Revisa el PGN."
                        } else {
                            val rawGame = GameRecord(
                                id = System.currentTimeMillis().toString(),
                                title = parsed.title,
                                pgn = pgnInput,
                                tags = parsed.tags,
                                moves = parsed.moves,
                                positions = reconstruction.positions,
                                importedAt = System.currentTimeMillis(),
                                parserWarnings = reconstruction.warnings,
                                analysis = null
                            )
                            val analyzed = rawGame.copy(analysis = analyzer.analyze(rawGame))
                            onGameImported(analyzed)
                            importMessage = "Partida importada con ${analyzed.analysis?.criticalMoves?.size ?: 0} momentos criticos."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Analizar y guardar")
                }
                importMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        item {
            ManualGameBuilder(
                moves = manualMoves,
                input = manualInput,
                message = manualMessage,
                onInputChange = { manualInput = it },
                onAddMove = {
                    val candidate = manualInput.trim()
                    if (candidate.isBlank()) {
                        manualMessage = "Escribe una jugada SAN, por ejemplo Nf3 o O-O."
                    } else {
                        val nextMoves = manualMoves + candidate
                        val parsed = ParsedPgn(
                            title = "Partida manual",
                            tags = mapOf("White" to "Yo", "Black" to "Rival", "Result" to "*"),
                            moves = nextMoves,
                            warnings = emptyList()
                        )
                        val reconstruction = GameReconstructor.reconstruct(parsed)
                        if (reconstruction.positions.size == nextMoves.size + 1) {
                            manualMoves = nextMoves
                            manualInput = ""
                            manualMessage = "Jugada agregada."
                        } else {
                            manualMessage = reconstruction.warnings.lastOrNull() ?: "No pude aplicar esa jugada."
                        }
                    }
                },
                onUndo = {
                    manualMoves = manualMoves.dropLast(1)
                    manualMessage = null
                },
                onSave = {
                    if (manualMoves.isEmpty()) {
                        manualMessage = "Agrega al menos una jugada antes de guardar."
                    } else {
                        val pgn = manualPgnFrom(manualMoves)
                        val parsed = PgnParser.parse(pgn)
                        val reconstruction = GameReconstructor.reconstruct(parsed)
                        val rawGame = GameRecord(
                            id = System.currentTimeMillis().toString(),
                            title = "Partida manual - ${manualMoves.size} jugadas",
                            pgn = pgn,
                            tags = parsed.tags,
                            moves = parsed.moves,
                            positions = reconstruction.positions,
                            importedAt = System.currentTimeMillis(),
                            parserWarnings = reconstruction.warnings,
                            analysis = null
                        )
                        val analyzed = rawGame.copy(analysis = analyzer.analyze(rawGame))
                        onGameImported(analyzed)
                        manualMoves = emptyList()
                        manualInput = ""
                        manualMessage = "Partida manual guardada."
                    }
                }
            )
        }

        item {
            Text("Partidas guardadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (games.isEmpty()) {
            item {
                Text("Todavia no hay partidas guardadas.")
            }
        } else {
            items(games, key = { it.id }) { game ->
                GameRow(
                    game = game,
                    selected = game.id == selectedGame?.id,
                    onClick = { onSelectGame(game) }
                )
            }
        }

        selectedGame?.let { game ->
            item {
                GameDetail(game = game)
            }
        }
    }
}

@Composable
private fun ManualGameBuilder(
    moves: List<String>,
    input: String,
    message: String?,
    onInputChange: (String) -> Unit,
    onAddMove: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit
) {
    val parsed = ParsedPgn(
        title = "Partida manual",
        tags = mapOf("White" to "Yo", "Black" to "Rival", "Result" to "*"),
        moves = moves,
        warnings = emptyList()
    )
    val reconstruction = GameReconstructor.reconstruct(parsed)
    val fen = reconstruction.positions.lastOrNull()?.fen ?: ChessBoard.START_FEN

    SectionSurface {
        Text("Registrar movida por movida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ChessBoardView(fen = fen)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (moves.isEmpty()) "Sin jugadas todavia." else moves.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Siguiente jugada SAN") }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddMove, modifier = Modifier.weight(1f)) {
                Text("Agregar")
            }
            OutlinedButton(onClick = onUndo, enabled = moves.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Deshacer")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSave, enabled = moves.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Guardar y analizar")
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun GameRow(
    game: GameRecord,
    selected: Boolean,
    onClick: () -> Unit
) {
    val critical = game.analysis?.criticalMoves?.size ?: 0
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(game.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${game.moves.size} jugadas PGN - $critical momentos criticos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GameDetail(game: GameRecord) {
    var moveIndex by remember(game.id) { mutableIntStateOf(0) }
    val position = game.positions.getOrNull(moveIndex) ?: game.positions.first()
    val moveAnalysis = game.analysis?.criticalMoves?.firstOrNull { it.moveIndex == position.moveIndex }

    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Jugada ${position.moveIndex} de ${game.positions.lastIndex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            moveAnalysis?.let { QualityChip(it.quality) }
        }
        Spacer(Modifier.height(12.dp))
        ChessBoardView(fen = position.fen)
        Spacer(Modifier.height(8.dp))
        NavigationControls(
            currentIndex = moveIndex,
            maxIndex = game.positions.lastIndex,
            onChange = { moveIndex = it.coerceIn(0, game.positions.lastIndex) }
        )
        Spacer(Modifier.height(8.dp))
        Text("FEN", fontWeight = FontWeight.SemiBold)
        Text(position.fen, style = MaterialTheme.typography.bodySmall)
        moveAnalysis?.let {
            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(10.dp))
            Text(it.explanation, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(it.recommendation)
        }
        if (game.parserWarnings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Avisos de importacion", fontWeight = FontWeight.SemiBold)
            game.parserWarnings.take(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun NavigationControls(
    currentIndex: Int,
    maxIndex: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(onClick = { onChange(0) }, enabled = currentIndex > 0, modifier = Modifier.weight(1f)) {
            Text("|<")
        }
        OutlinedButton(onClick = { onChange(currentIndex - 1) }, enabled = currentIndex > 0, modifier = Modifier.weight(1f)) {
            Text("<")
        }
        OutlinedButton(onClick = { onChange(currentIndex + 1) }, enabled = currentIndex < maxIndex, modifier = Modifier.weight(1f)) {
            Text(">")
        }
        OutlinedButton(onClick = { onChange(maxIndex) }, enabled = currentIndex < maxIndex, modifier = Modifier.weight(1f)) {
            Text(">|")
        }
    }
}

@Composable
private fun TrainingScreen(
    games: List<GameRecord>,
    exercises: List<TrainingExercise>
) {
    var selectedExerciseId by remember(exercises.size) { mutableStateOf(exercises.firstOrNull()?.id) }
    var showHint by remember(selectedExerciseId) { mutableStateOf(false) }
    var showSolution by remember(selectedExerciseId) { mutableStateOf(false) }
    val selected = exercises.firstOrNull { it.id == selectedExerciseId }

    if (games.isEmpty()) {
        EmptyTrainingState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Ejercicios desde tus partidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "No es modo rival: revisas posiciones donde tu partida mostro un patron debil.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selected != null) {
            item {
                SectionSurface {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    ChessBoardView(fen = selected.fen)
                    Spacer(Modifier.height(8.dp))
                    Text(selected.prompt)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showHint = !showHint }, modifier = Modifier.weight(1f)) {
                            Text("Pista")
                        }
                        Button(onClick = { showSolution = !showSolution }, modifier = Modifier.weight(1f)) {
                            Text("Revisar")
                        }
                    }
                    if (showHint) {
                        Spacer(Modifier.height(8.dp))
                        Text(selected.hint, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (showSolution) {
                        Spacer(Modifier.height(8.dp))
                        Text(selected.solution, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        items(exercises, key = { it.id }) { exercise ->
            GameExerciseRow(
                exercise = exercise,
                selected = exercise.id == selectedExerciseId,
                onClick = {
                    selectedExerciseId = exercise.id
                    showHint = false
                    showSolution = false
                }
            )
        }
    }
}

@Composable
private fun EmptyTrainingState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Importa una partida para generar ejercicios.", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GameExerciseRow(
    exercise: TrainingExercise,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(exercise.title, fontWeight = FontWeight.SemiBold)
            Text(exercise.prompt, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChessBoardView(fen: String) {
    val rows = ChessBoard.boardRowsFromFen(fen)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.weight(1f)) {
                row.forEachIndexed { colIndex, piece ->
                    val lightSquare = (rowIndex + colIndex) % 2 == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if (lightSquare) Color(0xFFDDE7D1) else Color(0xFF567568)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (piece != '.') {
                            Text(
                                text = piece.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (piece.isUpperCase()) Color(0xFFFDFDFB) else Color(0xFF18201C)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(84.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), content = { content() })
    }
}

@Composable
private fun RecommendationLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QualityChip(quality: MoveQuality) {
    val color = when (quality) {
        MoveQuality.BLUNDER -> MaterialTheme.colorScheme.secondary
        MoveQuality.MISTAKE -> Color(0xFF9B5C20)
        MoveQuality.INACCURACY -> MaterialTheme.colorScheme.tertiary
        MoveQuality.GOOD -> MaterialTheme.colorScheme.primary
    }
    AssistChip(
        onClick = {},
        label = { Text(quality.label) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.12f), labelColor = color)
    )
}

private fun buildExercises(games: List<GameRecord>): List<TrainingExercise> {
    return games.flatMap { game ->
        game.analysis?.criticalMoves.orEmpty().map { move ->
            move.toExercise(game)
        }
    }
}

private fun MoveAnalysis.toExercise(game: GameRecord): TrainingExercise {
    return TrainingExercise(
        id = "${game.id}-$moveIndex",
        gameId = game.id,
        title = "${game.title} - jugada $moveIndex",
        fen = fenBefore,
        moveIndex = moveIndex,
        prompt = "Encuentra una alternativa mejor antes de jugar $san.",
        hint = "Tema principal: $theme. Revisa jaques, capturas, amenazas y piezas indefensas.",
        solution = "$quality: $explanation $recommendation"
    )
}

private fun manualPgnFrom(moves: List<String>): String {
    val body = moves.chunked(2).mapIndexed { index, pair ->
        val moveNumber = index + 1
        if (pair.size == 1) "$moveNumber. ${pair[0]}" else "$moveNumber. ${pair[0]} ${pair[1]}"
    }.joinToString(" ")
    return """
[Event "Partida manual"]
[Site "Offline"]
[Date "????.??.??"]
[White "Yo"]
[Black "Rival"]
[Result "*"]

$body *
""".trimIndent()
}

private val samplePgn = """
[Event "Partida de ejemplo"]
[Site "?"]
[Date "2026.05.19"]
[White "Yo"]
[Black "Rival"]
[Result "*"]

1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. Qe2 Nf6 5. Nc3 O-O 6. O-O d6 7. d3 Bg4 8. h3 Nd4 *
""".trimIndent()
