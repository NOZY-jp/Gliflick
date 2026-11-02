package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState

enum class MenuCommand(val text: String, val selectable: Boolean = true) {
    START("start"), COMING_SOON("coming soon...", selectable = false),
    KEYBOARD_TEST("keyboard test"), QUIT("quit");
}

data class MenuInput(
    val words: List<WordMatchState>,
    val completedCommand: MenuCommand? = null,
    val receivedText: String = ""
) {
    fun matchedCharacters(command: MenuCommand): Int =
        if (completedCommand == command) command.text.length
        else words.firstOrNull { it.definition.id == command.text }?.matchedInputCharacters ?: 0
}
