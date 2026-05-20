# Personal Chess Trainer

App Android offline para revisar partidas propias, detectar patrones de error y crear ejercicios desde posiciones reales del usuario.

Esta primera version no es un rival de ajedrez. El flujo esta pensado asi:

1. Pegas una partida PGN.
2. La app la guarda localmente.
3. La app reconstruye posiciones en FEN.
4. Un analizador local marca momentos criticos.
5. La pantalla principal resume debilidades, recomendaciones y ejercicios.

## Estado del MVP

- Android nativo con Kotlin y Jetpack Compose.
- Persistencia local en `filesDir/chess_trainer_games.json`.
- Importacion basica de PGN.
- Registro manual de partidas jugada por jugada en SAN.
- Reconstruccion de tablero y FEN para PGN SAN comun.
- Navegador de partidas.
- Analizador heuristico offline basado en cambios de material.
- Capa `EngineAnalyzer` preparada para conectar Stockfish.
- Workflow de GitHub Actions para generar un APK debug descargable.

## Compilar en GitHub Actions

Sube este proyecto a un repositorio de GitHub y ejecuta el workflow **Build Android APK** desde la pestaña Actions. Al terminar, descarga el artifact `chess-trainer-debug-apk`.

El APK debug ya viene firmado con una clave debug de Android. Para instalarlo manualmente en tu telefono, habilita la instalacion desde origenes permitidos.

## Compilar localmente

Necesitas Android SDK, JDK 17 y Gradle. Luego ejecuta:

```bash
gradle assembleDebug
```

El APK queda en:

```text
app/build/outputs/apk/debug/
```

## Stockfish offline

El proyecto incluye la abstraccion `EngineAnalyzer`. La version actual usa `HeuristicEngineAnalyzer` para que el MVP funcione sin binarios nativos. El siguiente paso es agregar binarios Android de Stockfish por arquitectura y crear una implementacion que hable UCI con el motor.

Si algun dia distribuyes la app, revisa la licencia GPL de Stockfish antes de publicarla.
