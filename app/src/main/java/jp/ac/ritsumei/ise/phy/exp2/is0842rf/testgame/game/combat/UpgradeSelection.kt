package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.InputMode
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*

/** Independent menu targets use the same language/IME matching contract as combat and title. */
class UpgradeSelection(
    val offers: List<UpgradeOffer>, val words: List<WordDefinition>,
    val evolution: Boolean, val level: Int, initialMode: InputMode
) {
    private val engine = WordMatchEngine(words, initialMode)
    var states: List<WordMatchState> = engine.snapshot(); private set
    var selectedIndex: Int? = null; private set
    var age = 0f; private set
    private var selectedAge = 0f
    private var delivered = false
    val isChoosing get() = selectedIndex != null
    val selectionProgress get() = if (selectedIndex == null) 0f else (selectedAge / 0.25f).coerceIn(0f, 1f)

    fun onInput(tokens: List<InputToken>, composing: Boolean): Boolean {
        if (selectedIndex != null) return false
        val preview = if (composing) engine.previewInput(tokens) else null
        val apply = !composing || preview!!.completedWords.isNotEmpty()
        if (apply) {
            for (token in tokens) {
                val completed = engine.accept(token).firstOrNull()
                if (completed != null) {
                    selectedIndex = words.indexOfFirst { it.id == completed.id }
                    break
                }
            }
            states = engine.snapshot()
        } else states = preview!!.words
        return selectedIndex != null
    }

    fun advance(dt: Float): UpgradeOffer? {
        age += dt
        if (selectedIndex != null) {
            selectedAge += dt
            if (selectedAge >= 0.25f && !delivered) {
                delivered = true
                return offers[selectedIndex!!]
            }
        }
        return null
    }
}
