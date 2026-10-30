package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model

/** Splits Unicode code points so supplementary characters are not cut in half. */
fun splitTextCharacters(text: String): List<String> {
    if (text.isEmpty()) return emptyList()

    val characters = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        val nextIndex = index + Character.charCount(codePoint)
        characters += text.substring(index, nextIndex)
        index = nextIndex
    }
    return characters
}
