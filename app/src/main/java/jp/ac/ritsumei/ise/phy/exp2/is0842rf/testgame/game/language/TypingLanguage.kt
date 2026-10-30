package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language

enum class InputMode { KANA, ROMAJI }

/** One spelling consumes a chunk of the reading without enumerating whole-word variants. */
data class RomanEdge(val end: Int, val spelling: String)

/** Content language, independent of the Android UI locale and installed keyboard. */
interface TypingLanguage {
    val tag: String
    fun normalize(text: String): String
    fun romanEdges(reading: List<String>, offset: Int): List<RomanEdge>
    fun defaultRoman(reading: List<String>): String
    fun canModifyKana(source: String, target: String): Boolean = false
    fun observationEdges(reading: List<String>, offset: Int, followingPrefix: String): List<RomanEdge> =
        romanEdges(reading, offset)
    fun isObservationTerminal(kana: String, character: Char, followingPrefix: String): Boolean = false
}

object TypingLanguages {
    fun forTag(tag: String): TypingLanguage = when (tag) {
        "ja" -> JapaneseTypingLanguage
        "en" -> EnglishTypingLanguage
        else -> error("Unsupported word language: $tag")
    }
}

