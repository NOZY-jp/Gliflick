package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model

/** Immutable source data for one word shown in the game. */
data class WordDefinition(
    val id: String,
    val text: String,
    val displayName: String = text,
    val languageTag: String = "ja"
) {
    val characters: List<String> = splitTextCharacters(text)
    /** Number of Unicode characters in the reading; small kana and ー each count once. */
    val readingLength: Int get() = characters.size

    init {
        require(id.isNotBlank()) { "A word must have an id." }
        require(text.isNotBlank()) { "A word must not be empty." }
    }
}

/** A display-ready snapshot; it does not expose the mutable matcher. */
data class WordMatchState(
    val definition: WordDefinition,
    val matchedCharacters: Int,
    val isCompleted: Boolean,
    val inputText: String = definition.text,
    val matchedInputCharacters: Int = matchedCharacters
)

