package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuCommand
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import org.junit.Assert.*
import org.junit.Test

class MenuCommandTest {
    @Test fun eachPrefixHighlightsOnlyItsCommandAndOnlyFullInputCompletes() {
        for (command in MenuCommand.entries) {
            val controller = MenuInputController()
            for ((index, letter) in command.text.withIndex()) {
                val match = feed(controller, letter.toString())
                assertEquals(index + 1, match.matchedCharacters(command))
                assertEquals(if (index == command.text.lastIndex) command else null, match.completedCommand)
            }
        }
    }

    @Test fun repeatedAAlwaysRemainsTheStartOfArmory() {
        val controller = MenuInputController()
        repeat(20) {
            val match = feed(controller, "a")
            assertEquals(1, match.matchedCharacters(MenuCommand.ARMORY))
            assertNull(match.completedCommand)
        }
    }

    @Test fun armorArmoryCompletesAcrossCommittedAndComposingInput() {
        val source = "armorarmory"
        val committed = MenuInputController()
        val composing = MenuInputController()
        for (index in source.indices) {
            val a = feed(committed, source[index].toString())
            val b = feed(composing, source.take(index + 1), composing = true)
            val completed = if (index == source.lastIndex) MenuCommand.ARMORY else null
            assertEquals(completed, a.completedCommand)
            assertEquals(completed, b.completedCommand)
            if (index == 5) {
                assertEquals(1, a.matchedCharacters(MenuCommand.ARMORY))
                assertEquals(1, b.matchedCharacters(MenuCommand.ARMORY))
            }
        }
        assertEquals(MenuCommand.ARMORY, feed(MenuInputController(), source).completedCommand)
        assertEquals(MenuCommand.ARMORY, feed(MenuInputController(), source, true).completedCommand)
    }

    @Test fun aMistypedLetterCanStartADifferentCommandImmediately() {
        val controller = MenuInputController()
        val match = feed(controller, "armorp")
        assertEquals(0, match.matchedCharacters(MenuCommand.ARMORY))
        assertEquals(1, match.matchedCharacters(MenuCommand.PLAY))
        assertEquals(MenuCommand.PLAY, feed(controller, "lay").completedCommand)
    }

    @Test fun compositionReplacementAndCommitDoNotLoseOrDuplicateProgress() {
        val controller = MenuInputController()
        for (snapshot in listOf("a", "aa", "aaa", "aaa")) {
            assertEquals(1, feed(controller, snapshot, true).matchedCharacters(MenuCommand.ARMORY))
        }
        assertEquals(1, feed(controller, "aaa").matchedCharacters(MenuCommand.ARMORY))
        assertEquals(2, feed(controller, "r", true).matchedCharacters(MenuCommand.ARMORY))
        assertEquals(3, feed(controller, "rm", true).matchedCharacters(MenuCommand.ARMORY))
        assertEquals(MenuCommand.ARMORY, feed(controller, "rmory", true).completedCommand)
    }

    @Test fun overlappingPrefixesUseTheSameParallelRecognitionAsTheGame() {
        val match = feed(MenuInputController(), "up")
        assertEquals(2, match.matchedCharacters(MenuCommand.UPGRADES))
        assertEquals(1, match.matchedCharacters(MenuCommand.PLAY))
        assertNull(match.completedCommand)
    }

    @Test fun invalidLettersDoNotPreventLaterWordsAndEmptyInputDoesNotSelect() {
        val controller = MenuInputController()
        val empty = controller.currentInput()
        assertNull(empty.completedCommand)
        assertTrue(MenuCommand.entries.all { empty.matchedCharacters(it) == 0 })
        val invalid = feed(controller, "zzzz")
        assertNull(invalid.completedCommand)
        assertTrue(MenuCommand.entries.all { invalid.matchedCharacters(it) == 0 })
        assertEquals(MenuCommand.ARMORY, feed(controller, "armxarmory").completedCommand)
        assertEquals(MenuCommand.PLAY, feed(MenuInputController(), "xplay").completedCommand)
    }

    @Test fun uppercaseEnglishUsesTheSameMatchingRules() {
        val controller = MenuInputController()
        assertEquals(2, feed(controller, "PL").matchedCharacters(MenuCommand.PLAY))
        assertEquals(MenuCommand.PLAY, feed(controller, "AY").completedCommand)
    }

    private fun feed(controller: MenuInputController, text: String, composing: Boolean = false) =
        controller.onInputTokens(text.map { InputToken.Stroke(it.toString()) }, composing)
}
