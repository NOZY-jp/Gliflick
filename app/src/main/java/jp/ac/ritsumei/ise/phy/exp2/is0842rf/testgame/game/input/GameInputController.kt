package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.OverlapEvent
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.session.GameSession
import kotlin.random.Random

data class GameInputUpdate(
    val words: List<WordMatchState>,
    val completedWords: List<WordDefinition>,
    val defeatedCount: Int,
    val receivedText: String,
    val streak: Int,
    val mistypeCount: Int,
    val overlapEvents: List<OverlapEvent>
)

/** IME composition is speculative; a full match is applied immediately without Enter. */
class GameInputController(
    definitions: List<WordDefinition>,
    activeWordCount: Int = GameSession.DEFAULT_ACTIVE_WORD_COUNT,
    random: Random = Random.Default
) {
    private val gameSession = GameSession(definitions, activeWordCount, random)

    fun onInput(strokes: List<String>, composing: Boolean): GameInputUpdate {
        return onInputTokens(strokes.map { InputToken.Stroke(it) }, composing, strokes.joinToString(""))
    }

    fun onInputTokens(tokens: List<InputToken>, composing: Boolean, inputText: String): GameInputUpdate {
        val preview = if (composing) gameSession.previewInput(tokens) else null
        val completeNow = !composing || preview!!.completedWords.isNotEmpty()
        val completed = mutableListOf<WordDefinition>()
        val overlaps = mutableListOf<OverlapEvent>()
        if (completeNow) {
            tokens.forEach { token ->
                completed += gameSession.accept(token)
                overlaps += gameSession.lastOverlapEvents
            }
        }
        return GameInputUpdate(
            words = if (completeNow) gameSession.snapshot() else preview!!.words,
            completedWords = completed,
            defeatedCount = gameSession.defeatedCount,
            receivedText = if (completeNow) gameSession.receivedText else preview!!.receivedText,
            streak = if (completeNow) gameSession.streak else preview!!.streak,
            mistypeCount = if (completeNow) gameSession.mistypeCount else preview!!.mistypeCount,
            overlapEvents = overlaps
        )
    }

    fun currentWords(): List<WordMatchState> = gameSession.snapshot()
}

