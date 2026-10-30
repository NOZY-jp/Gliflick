package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.OverlapEvent
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.InputMode
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.RomanObservationGraph

data class WordInputPreview(
    val words: List<WordMatchState>,
    val completedWords: List<WordDefinition>,
    val streak: Int,
    val mistypeCount: Int,
    val receivedText: String
)

/** Parallel recognition; successful words never erase another word's partial input. */
class WordMatchEngine(definitions: List<WordDefinition>, initialMode: InputMode = InputMode.KANA) {
    private var matchers = definitions.map { WordMatcher(it, initialMode) }.toMutableList()
    private var nextPosition = 0L
    private var trialStart: Long? = null
    private data class DisplayInput(val position: Long, val displayStart: Long, val text: String)
    private var displayInput = mutableListOf<DisplayInput>()
    private var confirmedKana = mutableMapOf<String, Map<Long, Int>>()
    private data class CompletionSpan(val word: WordDefinition, val start: Long, val end: Long)
    private var recentCompletions = mutableListOf<CompletionSpan>()

    var lastOverlapEvents: List<OverlapEvent> = emptyList()
        private set

    var streak = 0
        private set
    var mistypeCount = 0
        private set
    var receivedText = ""
        private set
    val inputMode: InputMode get() = matchers.first().inputMode

    init {
        require(matchers.isNotEmpty()) { "At least one word is required." }
        require(definitions.map { it.id }.toSet().size == definitions.size) { "Word ids must be unique." }
    }

    fun accept(character: String): List<WordDefinition> = accept(InputToken.Stroke(character))

    fun accept(token: InputToken, allowPendingKanaModifier: Boolean = false): List<WordDefinition> {
        val overlapEvents = mutableListOf<OverlapEvent>()
        lastOverlapEvents = emptyList()
        val position = nextPosition++ * INPUT_POSITION_STRIDE
        val presentation = when (token) {
            is InputToken.Stroke -> DisplayInput(position, position, token.value)
            is InputToken.KanaObservation -> {
                val language = TypingLanguages.forTag(matchers.first().definition.languageTag)
                val confirmed = RomanObservationGraph(language, token).confirmedSpelling()
                DisplayInput(position, position + if (confirmed == null) token.observedRomanPrefix.length else 0,
                    confirmed ?: token.kana)
            }
        }
        displayInput += presentation
        val byPosition = sortedMapOf<Long, MutableList<Pair<WordMatcher, MatchStep>>>()
        matchers.forEach { matcher ->
            matcher.acceptAt(token, position, allowPendingKanaModifier)
            matcher.lastSteps.forEach { step -> byPosition.getOrPut(step.position) { mutableListOf() } += matcher to step }
        }
        val completed = linkedSetOf<WordDefinition>()
        if (byPosition.isEmpty()) {
            // An unexplained observation cannot silently count as a valid stroke.
            byPosition[position] = mutableListOf()
        }
        byPosition.forEach { (at, evaluations) ->
            val starts = evaluations.flatMap { it.second.acceptedStarts }.toSet()
            // Several words can recognize the same newly typed kana. Count it once.
            val kanaGain = evaluations.maxOfOrNull { (matcher, step) ->
                val previous = confirmedKana[matcher.definition.id].orEmpty()
                val gain = step.kanaProgress.maxOfOrNull { (start, count) ->
                    (count - (previous[start] ?: 0)).coerceAtLeast(0)
                } ?: 0
                confirmedKana[matcher.definition.id] = step.kanaProgress
                gain
            } ?: 0
            if (trialStart == null) trialStart = at
            if (starts.none { it <= trialStart!! }) {
                mistypeCount++
                streak = 0
                trialStart = starts.minOrNull()
            }
            val hits = evaluations.filter { it.second.completedStarts.isNotEmpty() }
            if (trialStart != null && hits.any { (_, step) -> step.completedStarts.any { it <= trialStart!! } })
                trialStart = null
            val newWords = hits.map { it.first.definition }.filter { it !in completed }.distinct()
            val spans = newWords.map { word ->
                CompletionSpan(word, hits.filter { it.first.definition.id == word.id }
                    .flatMap { it.second.completedStarts }.min(), at)
            }
            if (spans.isNotEmpty()) {
                val earlier = recentCompletions.filter { old ->
                    spans.any { current -> current.start <= old.end && old.start <= current.end }
                }.map { it.word }.distinctBy { it.id }
                if (newWords.size > 1 || earlier.isNotEmpty())
                    overlapEvents += OverlapEvent(newWords, earlier)
                recentCompletions += spans
            }
            completed += newWords
            // Count reading characters, not Roman strokes or completed words.
            // A simultaneous mistake resets first; valid kana then start a new streak.
            streak += kanaGain
        }
        refreshReceivedText()
        lastOverlapEvents = overlapEvents
        return completed.toList()
    }

    fun resetProgress() {
        matchers.forEach { it.resetProgress() }
        trialStart = null
        displayInput.clear()
        confirmedKana.clear()
        recentCompletions.clear()
        lastOverlapEvents = emptyList()
        receivedText = ""
    }

    private fun fork(): WordMatchEngine = WordMatchEngine(matchers.map { it.definition }, inputMode).also {
        it.matchers = matchers.map { matcher -> matcher.fork() }.toMutableList()
        it.nextPosition = nextPosition
        it.trialStart = trialStart
        it.streak = streak
        it.mistypeCount = mistypeCount
        it.displayInput = displayInput.toMutableList()
        it.receivedText = receivedText
        it.confirmedKana = confirmedKana.toMutableMap()
        it.recentCompletions = recentCompletions.toMutableList()
    }

    fun previewInput(tokens: List<InputToken>): WordInputPreview {
        val copy = fork()
        val completed = mutableListOf<WordDefinition>()
        tokens.forEachIndexed { index, token ->
            completed += copy.accept(token, allowPendingKanaModifier = index == tokens.lastIndex)
        }
        return WordInputPreview(copy.snapshot(), completed.distinct(), copy.streak, copy.mistypeCount, copy.receivedText)
    }

    fun preview(characters: List<String>): List<WordMatchState> =
        previewTokens(characters.map { InputToken.Stroke(it) })
    fun previewTokens(tokens: List<InputToken>): List<WordMatchState> = previewInput(tokens).words
    fun snapshot(): List<WordMatchState> = matchers.map { it.snapshot() }

    fun replaceWord(id: String, replacement: WordDefinition) {
        val slot = matchers.indexOfFirst { it.definition.id == id }
        require(slot >= 0) { "The replaced word must be active." }
        require(matchers.none { it.definition.id != id && it.definition.id == replacement.id }) {
            "A replacement must not duplicate another active word."
        }
        matchers[slot] = WordMatcher(replacement, inputMode)
        confirmedKana.remove(id)
        refreshReceivedText()
    }

    private fun refreshReceivedText() {
        val oldest = matchers.flatMap { it.activeStarts }.minOrNull()
        if (oldest == null) {
            displayInput.clear()
            recentCompletions.clear()
            receivedText = ""
            return
        }
        val firstToken = oldest / INPUT_POSITION_STRIDE * INPUT_POSITION_STRIDE
        recentCompletions.removeAll { it.end < oldest }
        displayInput.removeAll { it.position < firstToken }
        receivedText = displayInput.joinToString("") { entry ->
            if (entry.position == firstToken)
                entry.text.drop((oldest - entry.displayStart).coerceAtLeast(0).coerceAtMost(entry.text.length.toLong()).toInt())
            else entry.text
        }
    }
}
