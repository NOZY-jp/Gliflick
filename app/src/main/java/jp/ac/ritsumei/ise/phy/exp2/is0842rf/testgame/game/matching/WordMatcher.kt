package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.InputMode
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.RomanEdge
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.RomanObservationGraph
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters

/** Recognition positions describe observations, never manufactured input tokens. */
internal const val INPUT_POSITION_STRIDE = 1L shl 32

internal data class MatchStep(
    val position: Long,
    val acceptedStarts: Set<Long>,
    val completedStarts: Set<Long>,
    val kanaProgress: Map<Long, Int>
)

internal fun mergeMatchSteps(steps: List<MatchStep>): List<MatchStep> =
    steps.groupBy { it.position }.toSortedMap().map { (position, alternatives) ->
        val progress = alternatives.flatMap { it.kanaProgress.entries }
            .groupBy({ it.key }, { it.value }).mapValues { (_, counts) -> counts.min() }
        MatchStep(position, alternatives.flatMap { it.acceptedStarts }.toSet(),
            alternatives.flatMap { it.completedStarts }.toSet(), progress)
    }

/** Retains overlapping suffixes, including while another word is completed. */
class WordMatcher(
    val definition: WordDefinition,
    initialMode: InputMode = InputMode.KANA
) {
    private val language = TypingLanguages.forTag(definition.languageTag)
    private val reading = splitTextCharacters(language.normalize(definition.text))
    private val edges = reading.indices.map { language.romanEdges(reading, it) }
    private var paths = emptyList<Path>()
    private var nextPosition = 0L
    private var newStartsThrough = Long.MAX_VALUE
    private var newStartsFrom = Long.MIN_VALUE
    private var completedInputText: String? = null
    internal var isRetired = false
        private set

    internal var lastSteps: List<MatchStep> = emptyList()
        private set
    internal val activeStarts: Set<Long> get() = paths.map { it.start }.toSet()

    var inputMode = initialMode
        private set
    var isCompleted = false
        private set
    val matchedCharacters: Int get() = bestPath()?.offset ?: 0

    private data class Path(
        val offset: Int = 0,
        val romanHistory: String = "",
        val edge: RomanEdge? = null,
        val edgeProgress: Int = 0,
        val start: Long = 0L
    )

    fun accept(character: String, allowPendingKanaModifier: Boolean = false): Boolean =
        accept(InputToken.Stroke(character), allowPendingKanaModifier)

    fun accept(token: InputToken, allowPendingKanaModifier: Boolean = false): Boolean =
        acceptAt(token, nextPosition++ * INPUT_POSITION_STRIDE, allowPendingKanaModifier)

    internal fun acceptAt(token: InputToken, position: Long, allowPendingKanaModifier: Boolean = false): Boolean {
        lastSteps = when (token) {
            is InputToken.Stroke -> listOf(acceptCharacter(token.value, position, allowPendingKanaModifier))
            is InputToken.KanaObservation -> acceptObservation(token, position)
        }
        val completed = lastSteps.any { it.completedStarts.isNotEmpty() }
        if (completed) isCompleted = true
        return completed
    }

    private fun acceptCharacter(character: String, position: Long, allowPendingKanaModifier: Boolean): MatchStep {
        val normalized = language.normalize(character)
        val latin = normalized.singleOrNull()?.let { it in 'a'..'z' || it == '\'' || it == '-' } == true
        if (latin) inputMode = InputMode.ROMAJI
        else if (normalized.any { it in 'ぁ'..'ゖ' || it == 'ー' }) inputMode = InputMode.KANA

        val candidates = paths + freshStart(position)
        val pending = if (allowPendingKanaModifier && !latin) {
            candidates.filter { path ->
                reading.getOrNull(path.offset)?.let { language.canModifyKana(normalized, it) } == true
            }.map { it.copy(edge = null, edgeProgress = 0) }
        } else emptyList()
        val advanced = if (latin) advanceRoman(paths, normalized, position)
        else candidates.mapNotNull { path ->
            if (reading.getOrNull(path.offset) != normalized) null
            else {
                val offset = path.offset + 1
                Path(offset = offset, romanHistory = language.defaultRoman(reading.take(offset)), start = path.start)
            }
        }
        val next = (advanced + pending).distinct()
        val completed = next.filter { it.offset == reading.size }.map { it.start }.toSet()
        next.firstOrNull { it.offset == reading.size }?.let {
            completedInputText = if (latin) it.romanHistory else reading.joinToString("")
        }
        paths = next.filter { it.offset != reading.size }
        return MatchStep(position, next.map { it.start }.toSet(), completed, confirmedKana(next))
    }

    fun resetProgress() { paths = emptyList() }

    fun fork(): WordMatcher = WordMatcher(definition, inputMode).also {
        it.paths = paths
        it.isCompleted = isCompleted
        it.nextPosition = nextPosition
        it.newStartsThrough = newStartsThrough
        it.newStartsFrom = newStartsFrom
        it.completedInputText = completedInputText
        it.isRetired = isRetired
    }

    internal fun retire(throughPosition: Long) {
        newStartsThrough = minOf(newStartsThrough, throughPosition)
        isRetired = true
    }
    internal fun startFrom(position: Long) { newStartsFrom = position }

    internal fun completedSnapshot(): WordMatchState? = completedInputText?.let {
        WordMatchState(definition, reading.size, true, it, splitTextCharacters(it).size)
    }

    private fun freshStart(position: Long): List<Path> =
        if (position in newStartsFrom..newStartsThrough) listOf(Path(start = position)) else emptyList()

    fun snapshot(): WordMatchState {
        val path = bestPath()
        val prompt: String
        val highlighted: Int
        if (inputMode == InputMode.KANA) {
            prompt = reading.joinToString("")
            highlighted = path?.offset ?: 0
        } else if (path == null) {
            prompt = language.defaultRoman(reading)
            highlighted = 0
        } else {
            val edge = path.edge
            prompt = path.romanHistory + (edge?.spelling ?: "") +
                language.defaultRoman(reading.drop(edge?.end ?: path.offset))
            highlighted = path.romanHistory.length + path.edgeProgress
        }
        return WordMatchState(definition, path?.offset ?: 0, isCompleted, prompt, highlighted)
    }

    private fun acceptObservation(observation: InputToken.KanaObservation, position: Long): List<MatchStep> {
        data class State(val paths: List<Path>, val progress: RomanObservationGraph.Progress,
                         val steps: List<MatchStep>, val completedPrompt: String? = null)
        data class Key(val progress: RomanObservationGraph.Progress, val paths: Set<Path>,
                       val completions: Set<Pair<Long, Long>>)
        val graph = RomanObservationGraph(language, observation)
        val levels = List(graph.reading.size + 1) { linkedMapOf<Key, State>() }
        fun enqueue(offset: Int, state: State) {
            val key = Key(state.progress, state.paths.map { it.copy(romanHistory = "") }.toSet(),
                state.steps.flatMap { step -> step.completedStarts.map { step.position to it } }.toSet())
            val previous = levels[offset][key]
            levels[offset][key] = if (previous == null) state else
                previous.copy(steps = mergeMatchSteps(previous.steps + state.steps))
        }
        enqueue(0, State(paths, RomanObservationGraph.Progress(), emptyList()))
        for (offset in graph.reading.indices) {
            for (state in levels[offset].values) {
                for (edge in graph.edges(offset)) {
                    val progress = graph.advance(state.progress, edge.spelling) ?: continue
                    var branch = state.paths
                    val steps = state.steps.toMutableList()
                    var completedPrompt = state.completedPrompt
                    var symbol = state.progress.prefixMatched + if (state.progress.terminalUsed) 1 else 0
                    for (character in edge.spelling) {
                        val at = position + symbol++
                        val next = advanceRoman(branch, character.toString(), at)
                        val completed = next.filter { it.offset == reading.size }.map { it.start }.toSet()
                        next.firstOrNull { it.offset == reading.size }?.let { completedPrompt = it.romanHistory }
                        steps += MatchStep(at, next.map { it.start }.toSet(), completed, confirmedKana(next))
                        branch = next.filter { it.offset != reading.size }
                    }
                    enqueue(edge.end, State(branch, progress, steps, completedPrompt))
                }
            }
        }
        val compatible = levels.last().values.filter { graph.accepts(it.progress) }
        if (compatible.isEmpty()) {
            // Preserve actual observed text when the Roman rewrite cannot be explained.
            val fallback = splitTextCharacters(observation.observedRomanPrefix) +
                if (observation.kana == "ん") emptyList() else splitTextCharacters(observation.kana)
            return fallback.mapIndexed { index, text -> acceptCharacter(text, position + index, false) }
        }
        paths = compatible.flatMap { it.paths }.distinctBy { it.copy(romanHistory = "") }
        compatible.firstNotNullOfOrNull { it.completedPrompt }?.let { completedInputText = it }
        inputMode = InputMode.ROMAJI
        return mergeMatchSteps(compatible.flatMap { it.steps })
    }

    private fun advanceRoman(current: List<Path>, character: String, position: Long): List<Path> =
        (current + freshStart(position)).flatMap { path ->
            val possible = path.edge?.let(::listOf) ?: edges.getOrElse(path.offset) { emptyList() }
            possible.mapNotNull { edge ->
                if (edge.spelling.getOrNull(path.edgeProgress)?.toString() != character) null
                else {
                    val progress = path.edgeProgress + 1
                    if (progress == edge.spelling.length)
                        Path(offset = edge.end, romanHistory = path.romanHistory + edge.spelling, start = path.start)
                    else path.copy(edge = edge, edgeProgress = progress)
                }
            }
        }.distinct()

    // A single n is still ambiguous while both n and nn paths remain possible.
    // Count only kana shared by all remaining spellings of the same input start.
    private fun confirmedKana(candidates: List<Path>): Map<Long, Int> =
        candidates.groupBy { it.start }.mapValues { (_, alternatives) -> alternatives.minOf { it.offset } }

    // Stable order preserves the default spelling until actual input rules it out.
    private fun bestPath(): Path? = paths.maxByOrNull { it.edge?.end ?: it.offset }
}
