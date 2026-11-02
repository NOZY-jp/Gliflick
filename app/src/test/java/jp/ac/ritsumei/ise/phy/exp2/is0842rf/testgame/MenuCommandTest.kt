package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuCommand
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import org.junit.Assert.*
import org.junit.Test

class MenuCommandTest {
    @Test fun eachPrefixHighlightsOnlyItsCommandAndOnlyFullInputCompletes() {
        for (command in MenuCommand.entries.filter { it.selectable }) {
            val controller = MenuInputController()
            for ((index, letter) in command.text.withIndex()) {
                val match = feed(controller, letter.toString())
                assertEquals(index + 1, match.matchedCharacters(command))
                assertEquals(if (index == command.text.lastIndex) command else null, match.completedCommand)
            }
        }
    }

    @Test fun repeatedKAlwaysRemainsTheStartOfKeyboardTest() {
        val controller = MenuInputController()
        repeat(20) {
            val match = feed(controller, "k")
            assertEquals(1, match.matchedCharacters(MenuCommand.KEYBOARD_TEST))
            assertNull(match.completedCommand)
        }
    }

    @Test fun typoThenKeyboardTestCompletesAcrossCommittedAndComposingInput() {
        val source = "keybokeyboard test"
        val committed = MenuInputController()
        val composing = MenuInputController()
        for (index in source.indices) {
            val a = feed(committed, source[index].toString())
            val b = feed(composing, source.take(index + 1), composing = true)
            val completed = if (index == source.lastIndex) MenuCommand.KEYBOARD_TEST else null
            assertEquals(completed, a.completedCommand)
            assertEquals(completed, b.completedCommand)
            if (index == 5) {
                assertEquals(1, a.matchedCharacters(MenuCommand.KEYBOARD_TEST))
                assertEquals(1, b.matchedCharacters(MenuCommand.KEYBOARD_TEST))
            }
        }
        assertEquals(MenuCommand.KEYBOARD_TEST, feed(MenuInputController(), source).completedCommand)
        assertEquals(MenuCommand.KEYBOARD_TEST, feed(MenuInputController(), source, true).completedCommand)
    }

    @Test fun aMistypedLetterCanStartADifferentCommandImmediately() {
        val controller = MenuInputController()
        val match = feed(controller, "keybos")
        assertEquals(0, match.matchedCharacters(MenuCommand.KEYBOARD_TEST))
        assertEquals(1, match.matchedCharacters(MenuCommand.START))
        assertEquals(MenuCommand.START, feed(controller, "tart").completedCommand)
    }

    @Test fun compositionReplacementAndCommitDoNotLoseOrDuplicateProgress() {
        val controller = MenuInputController()
        for (snapshot in listOf("k", "kk", "kkk", "kkk")) {
            assertEquals(1, feed(controller, snapshot, true).matchedCharacters(MenuCommand.KEYBOARD_TEST))
        }
        assertEquals(1, feed(controller, "kkk").matchedCharacters(MenuCommand.KEYBOARD_TEST))
        assertEquals(2, feed(controller, "e", true).matchedCharacters(MenuCommand.KEYBOARD_TEST))
        assertEquals(3, feed(controller, "ey", true).matchedCharacters(MenuCommand.KEYBOARD_TEST))
        assertEquals(MenuCommand.KEYBOARD_TEST, feed(controller, "eyboard test", true).completedCommand)
    }

    @Test fun comingSoonIsNeverAnInputTarget() {
        for (composing in listOf(false, true)) {
            val match = feed(MenuInputController(), "coming soon...", composing)
            assertNull(match.completedCommand)
            assertEquals(0, match.matchedCharacters(MenuCommand.COMING_SOON))
            assertTrue(match.words.none { it.definition.id == MenuCommand.COMING_SOON.text })
        }
    }

    @Test fun invalidLettersDoNotPreventLaterWordsAndEmptyInputDoesNotSelect() {
        val controller = MenuInputController()
        val empty = controller.currentInput()
        assertNull(empty.completedCommand)
        assertTrue(MenuCommand.entries.all { empty.matchedCharacters(it) == 0 })
        val invalid = feed(controller, "zzzz")
        assertNull(invalid.completedCommand)
        assertTrue(MenuCommand.entries.all { invalid.matchedCharacters(it) == 0 })
        assertEquals(MenuCommand.KEYBOARD_TEST, feed(controller, "keybxkeyboard test").completedCommand)
        assertEquals(MenuCommand.START, feed(MenuInputController(), "xstart").completedCommand)
    }

    @Test fun uppercaseEnglishUsesTheSameMatchingRules() {
        val controller = MenuInputController()
        assertEquals(2, feed(controller, "ST").matchedCharacters(MenuCommand.START))
        assertEquals(MenuCommand.START, feed(controller, "ART").completedCommand)
    }

    private fun feed(controller: MenuInputController, text: String, composing: Boolean = false) =
        controller.onInputTokens(text.map { InputToken.Stroke(it.toString()) }, composing)
}
