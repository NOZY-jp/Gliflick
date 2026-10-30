package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters

/**
 * A kana change confirms a syllable terminal, not an arbitrary hidden spelling.
 * Only language-defined terminal transitions are admitted. Raw events remain unchanged.
 */
class RomanObservationGraph(
    private val language: TypingLanguage,
    private val observation: InputToken.KanaObservation
) {
    data class Progress(val prefixMatched: Int = 0, val terminalUsed: Boolean = false)
    val reading = splitTextCharacters(language.normalize(observation.kana))

    fun edges(offset: Int): List<RomanEdge> =
        language.observationEdges(reading, offset, observation.followingRomanPrefix)

    fun advance(progress: Progress, spelling: String): Progress? {
        var seen = progress.prefixMatched
        var terminal = progress.terminalUsed
        for (character in spelling) {
            if (seen < observation.observedRomanPrefix.length) {
                if (observation.observedRomanPrefix[seen] != character) return null
                seen++
            } else {
                if (terminal || !language.isObservationTerminal(observation.kana, character, observation.followingRomanPrefix)) return null
                terminal = true
            }
        }
        return Progress(seen, terminal)
    }

    fun accepts(progress: Progress): Boolean =
        observation.followingRomanPrefix.startsWith(observation.observedRomanPrefix.drop(progress.prefixMatched))

    /** Presentation of a uniquely confirmed path. Never used to generate input tokens. */
    fun confirmedSpelling(): String? {
        val results = linkedSetOf<String>()
        fun walk(offset: Int, progress: Progress, spelling: String) {
            if (results.size > 1) return
            if (offset == reading.size) {
                if (accepts(progress)) results += spelling
                return
            }
            for (edge in edges(offset)) {
                val next = advance(progress, edge.spelling) ?: continue
                walk(edge.end, next, spelling + edge.spelling)
            }
        }
        walk(0, Progress(), "")
        return results.singleOrNull()
    }
}

