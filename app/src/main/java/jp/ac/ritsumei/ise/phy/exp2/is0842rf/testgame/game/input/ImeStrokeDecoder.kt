package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.JapaneseTypingLanguage
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.RomanObservationGraph
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters

data class ImeStrokeEdit(
    val accepted: Boolean,
    val tokens: List<InputToken>,
    val composing: Boolean,
    val sourceText: String,
    val inputText: String = sourceText
)

interface ImeTextDecoder {
    fun composing(text: String): ImeStrokeEdit
    fun commit(text: String): ImeStrokeEdit
    fun finish(): ImeStrokeEdit
    fun reset()
}

/** Preserves actual text/prefix observations. Never reconstructs missing Roman keys. */
class ImeStrokeDecoder : ImeTextDecoder {
    private data class Segment(val visible: String, val tokens: List<InputToken>)
    private var segments = emptyList<Segment>()
    private var rawVisible = ""
    private var romanInputDetected = false
    private val ambiguousKana = setOf("あ", "い", "う", "え", "お", "ー")
    private val visible: String get() = segments.joinToString("") { it.visible }

    override fun composing(text: String): ImeStrokeEdit = replace(text, true)
    override fun commit(text: String): ImeStrokeEdit = replace(text, false)

    override fun finish(): ImeStrokeEdit {
        val tokens = segments.flatMap { it.tokens }
        val result = ImeStrokeEdit(true, tokens, false, rawVisible, renderConfirmedInput(tokens))
        reset()
        return result
    }

    override fun reset() {
        segments = emptyList()
        rawVisible = ""
    }

    private fun replace(text: String, composing: Boolean): ImeStrokeEdit {
        val next = JapaneseTypingLanguage.normalize(text)
        val previous = visible
        if (next != previous) {
            val common = previous.commonPrefixWith(next).length
            val kept = mutableListOf<Segment>()
            var boundary = 0
            for (segment in segments) {
                if (boundary + segment.visible.length > common) break
                kept += segment
                boundary += segment.visible.length
            }
            val removed = previous.substring(boundary)
            val added = next.substring(boundary)
            val kanaPrefix = added.takeWhile { it in 'ぁ'..'ゖ' || it == 'ー' }
            val latinConversion = removed.isNotEmpty() && removed.all(::isRomanCharacter) &&
                kanaPrefix.isNotEmpty()
            if (next.length < previous.length && !latinConversion) {
                return ImeStrokeEdit(false, emptyList(), composing, rawVisible)
            }
            if (latinConversion || added.any(::isRomanCharacter)) {
                romanInputDetected = true
                if (kept.all { it.visible in ambiguousKana && it.tokens == listOf(InputToken.Stroke(it.visible)) }) {
                    for (index in kept.indices) {
                        val segment = kept[index]
                        kept[index] = segment.copy(tokens = listOf(InputToken.KanaObservation(segment.visible, "")))
                    }
                }
            }
            if (latinConversion) {
                val following = added.drop(kanaPrefix.length)
                val tokens = if (!composing && kanaPrefix == "ん" && removed == "n" && following.isEmpty()) {
                    // Enter can commit a pending n as ん without a second n keystroke.
                    segments.drop(kept.size).flatMap { it.tokens }
                } else listOf(InputToken.KanaObservation(kanaPrefix, removed, following))
                kept += Segment(kanaPrefix, tokens)
                kept += decodeAdded(following)
            } else {
                val oldTail = segments.drop(kept.size).flatMap { it.tokens }
                val newSegments = decodeAdded(added)
                if (removed.isNotEmpty() && !JapaneseTypingLanguage.canModifyKana(removed, added)) {
                    kept += Segment(added, oldTail + newSegments.flatMap { it.tokens })
                } else {
                    kept += newSegments
                }
            }
            segments = kept
        }
        rawVisible = text
        val tokens = segments.flatMap { it.tokens }
        val result = ImeStrokeEdit(true, tokens, composing, text, renderConfirmedInput(tokens))
        if (!composing) reset()
        return result
    }

    private fun decodeAdded(text: String): List<Segment> = splitTextCharacters(text).map { character ->
        when {
            character.singleOrNull()?.let(::isRomanCharacter) == true -> {
                romanInputDetected = true
                Segment(character, listOf(InputToken.Stroke(character)))
            }
            romanInputDetected && character in ambiguousKana ->
                Segment(character, listOf(InputToken.KanaObservation(character, "")))
            else -> {
                if (character.any { it in 'ぁ'..'ゖ' || it == 'ー' }) romanInputDetected = false
                Segment(character, listOf(InputToken.Stroke(character)))
            }
        }
    }

    private fun renderConfirmedInput(tokens: List<InputToken>): String = tokens.joinToString("") { token ->
        when (token) {
            is InputToken.Stroke -> token.value
            is InputToken.KanaObservation ->
                RomanObservationGraph(JapaneseTypingLanguage, token).confirmedSpelling() ?: token.kana
        }
    }

    private fun isRomanCharacter(character: Char): Boolean =
        character in 'a'..'z' || character == '\'' || character == '-'
}

