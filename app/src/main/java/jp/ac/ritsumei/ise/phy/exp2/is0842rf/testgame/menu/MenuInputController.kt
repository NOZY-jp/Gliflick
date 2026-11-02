package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.InputMode
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition

/** Static menu targets adapt the shared game matcher; there is no menu-specific correctness algorithm. */
class MenuInputController {
    private val engine = WordMatchEngine(MenuCommand.entries.filter { it.selectable }.map {
        WordDefinition(it.text, it.text, languageTag = "en")
    }, InputMode.ROMAJI)

    fun currentInput(): MenuInput = MenuInput(engine.snapshot(), receivedText = engine.receivedText)

    fun onInputTokens(tokens: List<InputToken>, composing: Boolean): MenuInput {
        // Match the game's composition contract: preview unfinished composition,
        // apply committed input or a completed target immediately without Enter.
        val preview = if (composing) engine.previewInput(tokens) else null
        val apply = !composing || preview!!.completedWords.isNotEmpty()
        val completed = if (apply) tokens.flatMap { engine.accept(it) } else preview!!.completedWords
        return MenuInput(
            words = if (apply) engine.snapshot() else preview!!.words,
            completedCommand = completed.firstOrNull()?.let { word ->
                MenuCommand.entries.first { it.text == word.id }
            },
            receivedText = if (apply) engine.receivedText else preview!!.receivedText
        )
    }
}
