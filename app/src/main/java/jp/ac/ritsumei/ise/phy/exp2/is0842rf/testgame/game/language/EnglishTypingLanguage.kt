package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language

import java.util.Locale

/** Literal English spelling uses the same recognition paths as Japanese words. */
object EnglishTypingLanguage : TypingLanguage {
    override val tag = "en"
    override fun normalize(text: String): String = text.lowercase(Locale.ROOT)
    override fun romanEdges(reading: List<String>, offset: Int): List<RomanEdge> =
        reading.getOrNull(offset)?.let { listOf(RomanEdge(offset + 1, it)) }.orEmpty()
    override fun defaultRoman(reading: List<String>): String = reading.joinToString("")
}
