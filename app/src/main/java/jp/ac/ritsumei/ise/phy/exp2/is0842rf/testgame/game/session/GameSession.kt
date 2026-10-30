package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.session

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordInputPreview
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.OverlapEvent
import kotlin.random.Random

/** Owns the active words, replacements, and the typing score. */
class GameSession(
    definitions: List<WordDefinition>,
    activeWordCount: Int = DEFAULT_ACTIVE_WORD_COUNT,
    random: Random = Random.Default
) {
    private val wordSelector = RandomWordSelector(definitions, random)
    private var activeDefinitions: List<WordDefinition>
    private val matchEngine: WordMatchEngine

    var defeatedCount: Int = 0
        private set

    val streak: Int get() = matchEngine.streak
    val mistypeCount: Int get() = matchEngine.mistypeCount
    val receivedText: String get() = matchEngine.receivedText
    val lastOverlapEvents: List<OverlapEvent> get() = matchEngine.lastOverlapEvents

    init {
        require(activeWordCount > 0) { "At least one active word is required." }
        require(definitions.size > activeWordCount) {
            "The word pool must contain more than $activeWordCount words for replacements."
        }
        activeDefinitions = List(activeWordCount) { wordSelector.next(emptySet()) }
        matchEngine = WordMatchEngine(activeDefinitions)
    }

    /** Applies one committed character and replaces every word completed by it. */
    fun accept(character: String): List<WordDefinition> = accept(InputToken.Stroke(character))

    fun accept(token: InputToken): List<WordDefinition> {
        val completedWords = matchEngine.accept(token)
        if (completedWords.isEmpty()) return emptyList()

        defeatedCount += completedWords.size
        val replacements = activeDefinitions.toMutableList()
        completedWords.forEach { completed ->
            val slot = replacements.indexOfFirst { it.id == completed.id }
            if (slot >= 0) {
                val replacement = wordSelector.next(replacements.mapTo(mutableSetOf()) { it.id })
                replacements[slot] = replacement
                matchEngine.replaceWord(completed.id, replacement)
            }
        }

        activeDefinitions = replacements
        // Only completed word instances change; other words retain their progress.
        return completedWords
    }

    fun preview(characters: List<String>): List<WordMatchState> = matchEngine.preview(characters)
    fun previewTokens(tokens: List<InputToken>): List<WordMatchState> = matchEngine.previewTokens(tokens)
    fun previewInput(tokens: List<InputToken>): WordInputPreview = matchEngine.previewInput(tokens)

    fun snapshot(): List<WordMatchState> = matchEngine.snapshot()

    companion object {
        const val DEFAULT_ACTIVE_WORD_COUNT = 8
    }
}

/** Draws words without replacement until a new shuffle cycle is needed. */
private class RandomWordSelector(
    private val definitions: List<WordDefinition>,
    private val random: Random
) {
    private var remaining = mutableListOf<WordDefinition>()

    init {
        require(definitions.map { it.id }.toSet().size == definitions.size) {
            "Word ids must be unique."
        }
        require(definitions.map { it.text }.toSet().size == definitions.size) {
            "Word text must be unique."
        }
    }

    fun next(excludedIds: Set<String>): WordDefinition {
        var availableIndex = remaining.indexOfFirst { it.id !in excludedIds }
        if (availableIndex < 0) {
            remaining = definitions
                .filterNot { it.id in excludedIds }
                .shuffled(random)
                .toMutableList()
            availableIndex = remaining.indexOfFirst { it.id !in excludedIds }
        }
        check(availableIndex >= 0) { "No replacement word is available." }
        return remaining.removeAt(availableIndex)
    }
}
