package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model

/** One effect hook for words sharing input, including completions on different strokes. */
data class OverlapEvent(
    val completedNow: List<WordDefinition>,
    val completedEarlier: List<WordDefinition>
) {
    val words: List<WordDefinition> get() = (completedEarlier + completedNow).distinctBy { it.id }
}
