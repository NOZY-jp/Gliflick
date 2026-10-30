package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.GameInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.ImeStrokeDecoder
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.JapaneseTypingLanguage
import kotlin.random.Random

class MistypeStreakTest {
    private fun engine(vararg words: String) = WordMatchEngine(words.map { WordDefinition(it, it) })
    private fun type(engine: WordMatchEngine, text: String) =
        splitTextCharacters(text).flatMap { engine.accept(it) }.map { it.text }

    private fun controller(vararg targets: String): GameInputController {
        val pool = (targets.toList() + "よび").map { WordDefinition(it, it) }
        return (0..1000).asSequence().map {
            GameInputController(pool, activeWordCount = targets.size, random = Random(it))
        }.first { it.currentWords().map { word -> word.definition.text }.toSet() == targets.toSet() }
    }

    @Test fun simultaneousSuffixCompletionCountsBothWords() {
        val game = engine("えいご", "いご")
        assertEquals(setOf("えいご", "いご"), type(game, "eigo").toSet())
        assertEquals(3, game.streak)
        assertEquals(0, game.mistypeCount)
        assertEquals("", game.receivedText)
    }

    @Test fun innerCompletionPreservesTheOuterWordAndItsUnfinishedTrial() {
        val game = engine("ぱそこん", "そこ")
        assertEquals(listOf("そこ"), type(game, "pasoko"))
        assertEquals("pasoko", game.receivedText)
        assertEquals(3, game.streak)
        assertEquals(listOf("ぱそこん"), type(game, "nn"))
        assertEquals(4, game.streak)
        assertEquals(0, game.mistypeCount)
        assertEquals("", game.receivedText)
    }

    @Test fun partialOverlapContinuesAfterTheFirstCompletion() {
        val game = engine("ちゃわん", "わんがん")
        assertEquals(listOf("ちゃわん"), type(game, "tyawann"))
        assertEquals("wann", game.receivedText)
        assertEquals(listOf("わんがん"), type(game, "gann"))
        assertEquals(6, game.streak)
        assertEquals(0, game.mistypeCount)
        assertEquals("", game.receivedText)
    }

    @Test fun aMistakeAndAnotherWordsCompletionCoexist() {
        val game = engine("ちゃわん", "わに")
        assertEquals(listOf("わに"), type(game, "tyawani"))
        assertEquals(1, game.mistypeCount)
        assertEquals(1, game.streak)
    }

    @Test fun sharedPrefixAlternativesDoNotProduceFalseMistakes() {
        for (text in listOf("sango", "sanpo")) {
            val game = engine("さんご", "さんぽ")
            assertEquals(1, type(game, text).size)
            assertEquals(0, game.mistypeCount)
            assertEquals(3, game.streak)
        }
    }

    @Test fun failingAYoungerSuffixDoesNotInvalidateACompletedOuterWord() {
        val game = engine("ちゃわん", "わに")
        assertEquals(listOf("ちゃわん"), type(game, "tyawann"))
        assertEquals(0, game.mistypeCount)
    }

    @Test fun aShortWordDoesNotPreventItsLongerPrefixAlternative() {
        val game = engine("え", "えいご")
        assertEquals(listOf("え"), type(game, "e"))
        assertEquals("e", game.receivedText)
        assertEquals(listOf("えいご"), type(game, "igo"))
        assertEquals(3, game.streak)
        assertEquals(0, game.mistypeCount)
    }

    @Test fun aSuccessfulWordsCarryoverDoesNotForceThePlayerToFinishIt() {
        val game = engine("え", "えいご", "いか")
        assertEquals(listOf("え", "いか"), type(game, "eika"))
        assertEquals(0, game.mistypeCount)
        assertEquals(3, game.streak)
    }

    @Test fun innerSuccessDoesNotForgiveTheUnfinishedOuterPrefix() {
        val game = engine("ぱそこん", "そこ")
        assertEquals(listOf("そこ"), type(game, "pasoko"))
        type(game, "x")
        assertEquals(1, game.mistypeCount)
        assertEquals(0, game.streak)
    }

    @Test fun invalidStrokesBreakTheStreakOnceEachNotOncePerWord() {
        val game = engine("えいご", "いご")
        type(game, "eigo")
        type(game, "xyz")
        assertEquals(0, game.streak)
        assertEquals(3, game.mistypeCount)
    }

    @Test fun productionControllerKeepsProgressAcrossCompositionAndWordReplacement() {
        val game = controller("ぱそこん", "そこ")
        val inner = game.onInput(splitTextCharacters("pasoko"), true)
        assertEquals(listOf("そこ"), inner.completedWords.map { it.text })
        assertEquals("pasoko", inner.receivedText)
        assertEquals(0, inner.mistypeCount)
        // The input host clears its transport after a hit; the next segment is only nn.
        val outer = game.onInput(splitTextCharacters("nn"), true)
        assertEquals(listOf("ぱそこん"), outer.completedWords.map { it.text })
        assertEquals(4, outer.streak)
        assertEquals(0, outer.mistypeCount)
        assertEquals("", outer.receivedText)
    }

    @Test fun replayingACompositionDoesNotAccumulateTheSameMistake() {
        val game = controller("ぱそこん", "そこ")
        repeat(3) {
            assertEquals(1, game.onInput(splitTextCharacters("pax"), true).mistypeCount)
        }
        assertEquals(1, game.onInput(splitTextCharacters("pax"), false).mistypeCount)
        assertEquals(1, game.onInput(emptyList(), false).mistypeCount)
        val hit = game.onInput(splitTextCharacters("soko"), false)
        assertEquals(2, hit.streak)
        assertEquals(1, hit.mistypeCount)
    }

    @Test fun kanaModifierPreviewDoesNotCountTheTemporaryUnvoicedEndingAsAMistake() {
        val game = controller("だいどころ")
        assertEquals(0, game.onInput(splitTextCharacters("だいと"), true).mistypeCount)
        assertEquals(0, game.onInput(splitTextCharacters("だいど"), true).mistypeCount)
        assertEquals(1, game.onInput(splitTextCharacters("だいと"), false).mistypeCount)
    }

    @Test fun measuredSiAndShiObservationPrefixesHaveNoFalseMistakes() {
        for (notifications in listOf(listOf("s", "し", "しm", "しま"),
                                    listOf("s", "sh", "し", "しm", "しま"))) {
            val game = controller("しま")
            val decoder = ImeStrokeDecoder()
            for (source in notifications) {
                val edit = decoder.composing(source)
                val update = game.onInputTokens(edit.tokens, true, edit.inputText)
                assertEquals(source, 0, update.mistypeCount)
            }
            assertEquals(2, game.onInput(emptyList(), false).streak)
        }
    }

    @Test fun observationConstraintsPreserveNestedOriginsAndMistakeTiming() {
        // Constraint-level regression, not a claim about a new physical IME trace.
        val nested = engine("ぱそこん", "そこ")
        assertEquals(listOf("そこ"), nested.accept(InputToken.KanaObservation("ぱそこ", "pasok")).map { it.text })
        assertEquals("pasoko", nested.receivedText)
        assertEquals(listOf("ぱそこん"), type(nested, "nn"))
        assertEquals(4, nested.streak)
        assertEquals(0, nested.mistypeCount)

        val mistake = engine("ちゃわん", "わに")
        assertEquals(listOf("わに"), mistake.accept(InputToken.KanaObservation("ちゃわに", "tyawan")).map { it.text })
        assertEquals(1, mistake.mistypeCount)
        assertEquals(1, mistake.streak)
    }

    @Test fun theProductionDefaultIsEightWords() {
        val words = listOf("あ", "い", "う", "え", "お", "か", "き", "く", "け")
        assertEquals(8, GameInputController(words.map { WordDefinition(it, it) }).currentWords().size)
    }

    @Test fun romanStrokesCountOnlyConfirmedReadingCharacters() {
        val game = engine("ちゃわん")
        type(game, "ty")
        assertEquals(0, game.streak)
        type(game, "a")
        assertEquals(2, game.streak) // ち + ゃ, not the three Roman letters.
        type(game, "wa")
        assertEquals(3, game.streak)
        type(game, "n")
        assertEquals(3, game.streak)
        type(game, "n")
        assertEquals(4, game.streak)
    }

    @Test fun anAmbiguousInternalNIsNotCountedTwiceAcrossOverlappingWords() {
        val game = engine("ちゃわん", "わんがん")
        type(game, "tyawan")
        assertEquals(3, game.streak)
        type(game, "n")
        assertEquals(4, game.streak)
        type(game, "gann")
        assertEquals(6, game.streak)
    }

    @Test fun flickKanaAndSmallKanaEachCountOneCharacter() {
        val game = engine("きゃべつ")
        type(game, "きゃ")
        assertEquals(2, game.streak)
        type(game, "べつ")
        assertEquals(4, game.streak)
    }

    @Test fun everyDictionaryWordCountsItsReadingLengthWithoutFalseMistakes() {
        val file = listOf(File("src/main/res/raw/word_list.txt"), File("app/src/main/res/raw/word_list.txt"))
            .first { it.isFile }
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val columns = line.split('\t')
            val reading = columns.getOrElse(1) { columns[0] }
            for (input in listOf(reading, JapaneseTypingLanguage.defaultRoman(splitTextCharacters(reading)))) {
                val game = engine(reading)
                type(game, input)
                assertEquals("$reading / $input", splitTextCharacters(reading).size, game.streak)
                assertEquals("$reading / $input", 0, game.mistypeCount)
            }
        }
    }

    @Test fun sharedAndSeparateWordsHaveDifferentCountsAndEffectHooks() {
        for ((firstWord, sharedTail, secondWord) in listOf(
            Triple("tyawann", "gann", "wanngann"), Triple("ちゃわん", "がん", "わんがん")
        )) {
            val shared = controller("ちゃわん", "わんがん")
            val first = shared.onInput(splitTextCharacters(firstWord), true)
            assertEquals(4, first.streak)
            assertTrue(first.overlapEvents.isEmpty())
            val joined = shared.onInput(splitTextCharacters(sharedTail), true)
            assertEquals(6, joined.streak)
            assertEquals(0, joined.mistypeCount)
            assertEquals(1, joined.overlapEvents.size)
            assertEquals(setOf("ちゃわん", "わんがん"), joined.overlapEvents.single().words.map { it.text }.toSet())
            assertEquals(listOf("わんがん"), joined.overlapEvents.single().completedNow.map { it.text })
            assertEquals(listOf("ちゃわん"), joined.overlapEvents.single().completedEarlier.map { it.text })

            val separate = controller("ちゃわん", "わんがん")
            separate.onInput(splitTextCharacters(firstWord), true)
            val second = separate.onInput(splitTextCharacters(secondWord), true)
            assertEquals(8, second.streak)
            assertEquals(0, second.mistypeCount)
            assertTrue(second.overlapEvents.isEmpty())

            val batch = controller("ちゃわん", "わんがん")
                .onInput(splitTextCharacters(firstWord + secondWord), true)
            assertEquals(8, batch.streak)
            assertEquals(0, batch.mistypeCount)
            assertTrue(batch.overlapEvents.isEmpty())
            val sharedBatch = controller("ちゃわん", "わんがん")
                .onInput(splitTextCharacters(firstWord + sharedTail), true)
            assertEquals(6, sharedBatch.streak)
            assertEquals(1, sharedBatch.overlapEvents.size)
        }
    }

    @Test fun simultaneousAndNestedSharedCompletionsExposeOneEffectEvent() {
        val simultaneous = controller("えいご", "いご").onInput(splitTextCharacters("eigo"), true)
        assertEquals(3, simultaneous.streak)
        assertEquals(1, simultaneous.overlapEvents.size)
        assertEquals(2, simultaneous.overlapEvents.single().completedNow.size)
        assertTrue(simultaneous.overlapEvents.single().completedEarlier.isEmpty())

        val nested = controller("ぱそこん", "そこ")
        assertTrue(nested.onInput(splitTextCharacters("pasoko"), true).overlapEvents.isEmpty())
        val outer = nested.onInput(splitTextCharacters("nn"), true)
        assertEquals(4, outer.streak)
        assertEquals(1, outer.overlapEvents.size)
        assertEquals(setOf("ぱそこん", "そこ"), outer.overlapEvents.single().words.map { it.text }.toSet())
        assertTrue(nested.onInput(emptyList(), false).overlapEvents.isEmpty())
    }
}
