package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import java.text.Normalizer

/** Japanese spelling alternatives are shared by matching, prompts, and IME reconciliation. */
object JapaneseTypingLanguage : TypingLanguage {
    override val tag = "ja"
    override fun isObservationTerminal(kana: String, character: Char, followingPrefix: String): Boolean =
        character in "aiueo" || (kana == "ー" && character == '-') ||
            (kana == "ん" && character == 'n' && followingPrefix.isEmpty())
    private val spellings = linkedMapOf<String, List<String>>()
    private val modifierGroups = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つづっ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ"
    )

    override fun canModifyKana(source: String, target: String): Boolean =
        source != target && source.length == 1 && target.length == 1 &&
            modifierGroups.any { source[0] in it && target[0] in it }

    init {
        fun row(kana: String, roman: String) {
            splitTextCharacters(kana).zip(roman.split(" ")).forEach { (k, r) ->
                spellings[k] = r.split("/")
            }
        }
        row("あいうえお", "a i u/wu/whu e o")
        row("かきくけこ", "ka ki ku/cu/qu ke ko/co")
        row("さしすせそ", "sa shi/si/ci su se/ce so")
        row("たちつてと", "ta chi/ti tsu/tu te to")
        row("なにぬねの", "na ni nu ne no")
        row("はひふへほ", "ha hi fu/hu he ho")
        row("まみむめも", "ma mi mu me mo")
        row("やゆよ", "ya yu yo")
        row("らりるれろ", "ra ri ru re ro")
        row("わゐゑを", "wa wyi wye wo")
        row("がぎぐげご", "ga gi gu ge go")
        row("ざじずぜぞ", "za ji/zi zu ze zo")
        row("だぢづでど", "da di du de do")
        row("ばびぶべぼ", "ba bi bu be bo")
        row("ぱぴぷぺぽ", "pa pi pu pe po")
        row("ぁぃぅぇぉ", "xa/la xi/li/xyi/lyi xu/lu xe/le/xye/lye xo/lo")
        spellings["ゃ"] = listOf("xya", "lya")
        spellings["ゅ"] = listOf("xyu", "lyu")
        spellings["ょ"] = listOf("xyo", "lyo")
        spellings["ゎ"] = listOf("xwa", "lwa")
        spellings["ゕ"] = listOf("xka", "lka")
        spellings["ゖ"] = listOf("xke", "lke")
        spellings["っ"] = listOf("xtu", "ltu", "xtsu", "ltsu")
        spellings["ゔ"] = listOf("vu")
        spellings["ー"] = listOf("-")
        fun chunk(kana: String, vararg forms: String) { spellings[kana] = forms.toList() }
        val yRows = listOf("き" to "ky", "ぎ" to "gy", "に" to "ny", "ひ" to "hy",
            "び" to "by", "ぴ" to "py", "み" to "my", "り" to "ry")
        yRows.forEach { (kana, roman) ->
            listOf("ゃ" to "a", "ゅ" to "u", "ょ" to "o").forEach { (small, vowel) ->
                chunk(kana + small, roman + vowel)
            }
        }
        chunk("しゃ", "sha", "sya"); chunk("しゅ", "shu", "syu"); chunk("しょ", "sho", "syo")
        chunk("じゃ", "ja", "zya", "jya"); chunk("じゅ", "ju", "zyu", "jyu")
        chunk("じょ", "jo", "zyo", "jyo")
        // tila/tixa are explicit game aliases requested for ちゃ, in addition to standard forms.
        chunk("ちゃ", "cha", "tya", "cya", "tila", "tixa", "tilya", "tixya")
        chunk("ちゅ", "chu", "tyu", "cyu"); chunk("ちょ", "cho", "tyo", "cyo")
        chunk("ぢゃ", "dya"); chunk("ぢゅ", "dyu"); chunk("ぢょ", "dyo")
        chunk("いぇ", "ye"); chunk("うぃ", "wi", "whi"); chunk("うぇ", "we", "whe")
        chunk("うぉ", "who"); chunk("うぁ", "wha")
        chunk("しぇ", "she", "sye"); chunk("じぇ", "je", "jye", "zye")
        chunk("ちぇ", "che", "tye", "cye")
        chunk("てぃ", "thi"); chunk("てゅ", "thu"); chunk("とぅ", "twu")
        chunk("でぃ", "dhi"); chunk("でゅ", "dhu"); chunk("どぅ", "dwu")
        chunk("ふぁ", "fa", "fwa"); chunk("ふぃ", "fi", "fwi")
        chunk("ふぇ", "fe", "fwe"); chunk("ふぉ", "fo", "fwo"); chunk("ふゅ", "fyu")
        chunk("ゔぁ", "va"); chunk("ゔぃ", "vi"); chunk("ゔぇ", "ve"); chunk("ゔぉ", "vo")
        chunk("くぁ", "qa", "kwa"); chunk("くぃ", "qi", "qwi")
        chunk("くぇ", "qe", "qwe"); chunk("くぉ", "qo", "qwo"); chunk("くゃ", "qya")
        chunk("くゅ", "qyu"); chunk("くょ", "qyo")
        chunk("ぐぁ", "gwa"); chunk("ぐぃ", "gwi"); chunk("ぐぇ", "gwe"); chunk("ぐぉ", "gwo")
    }

    override fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).map { character ->
            if (character in 'ァ'..'ヶ') character - 0x60 else character.lowercaseChar()
        }.joinToString("")

    override fun romanEdges(reading: List<String>, offset: Int): List<RomanEdge> {
        if (offset >= reading.size) return emptyList()
        val kana = reading[offset]
        if (kana == "ん") {
            val next = reading.getOrNull(offset + 1)
            val forms = when {
                next == null -> listOf("nn")
                next in listOf("あ", "い", "う", "え", "お", "や", "ゆ", "よ", "ん") ->
                    listOf("nn", "n'", "xn")
                else -> listOf("n", "nn", "n'", "xn")
            }
            return forms
                .map { RomanEdge(offset + 1, it) }
        }
        val result = mutableListOf<RomanEdge>()
        if (offset + 1 < reading.size) {
            spellings[kana + reading[offset + 1]]?.forEach {
                result += RomanEdge(offset + 2, it)
            }
        }
        if (kana == "っ" && offset + 1 < reading.size && reading[offset + 1] != "っ") {
            romanEdges(reading, offset + 1).map { it.spelling.first() }
                .filter { it in "bcdfghjklmpqrstvwxyz" }.distinct().forEach {
                    result += RomanEdge(offset + 1, it.toString())
                }
        }
        spellings[kana]?.forEach { result += RomanEdge(offset + 1, it) }
        return result.distinct()
    }

    override fun defaultRoman(reading: List<String>): String {
        val result = StringBuilder()
        var offset = 0
        while (offset < reading.size) {
            val edge = romanEdges(reading, offset).firstOrNull()
                ?: error("No Japanese input spelling for ${reading[offset]}")
            result.append(edge.spelling)
            offset = edge.end
        }
        return result.toString()
    }

    override fun observationEdges(reading: List<String>, offset: Int, followingPrefix: String): List<RomanEdge> {
        // Measured Simeji distinction: n -> んp commits one n; n -> ん commits nn.
        val contextualNasal = if (reading.getOrNull(offset) == "ん" && offset == reading.lastIndex &&
            followingPrefix.firstOrNull()?.let { it in "bcdfghjklmpqrstvwxz" } == true) {
            listOf(RomanEdge(offset + 1, "n"))
        } else emptyList()
        val contextualSokuon = if (reading.getOrNull(offset) == "っ" && offset == reading.lastIndex &&
            followingPrefix.firstOrNull()?.let { it in "bcdfghjklmpqrstvwxyz" } == true) {
            listOf(RomanEdge(offset + 1, followingPrefix.take(1)))
        } else emptyList()
        return contextualNasal + contextualSokuon + romanEdges(reading, offset)
    }
}

