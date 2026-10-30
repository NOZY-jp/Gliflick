package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.GameInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.ImeStrokeDecoder
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.JapaneseTypingLanguage
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatcher
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class WordMatchingTest {
    @Test fun committingASinglePendingNDoesNotSupplyASecondN() {
        val decoder = ImeStrokeDecoder()
        for (source in listOf("k", "か", "かn")) decoder.composing(source)
        val committed = decoder.commit("かん")
        assertEquals("かん", committed.sourceText)
        assertEquals("kan", committed.inputText)
        assertFalse(WordMatchEngine(listOf(word("かん"))).previewTokens(committed.tokens).single().isCompleted)
        for (prefix in listOf("n'", "xn")) {
            val matcher = WordMatcher(word("かん"))
            feed(matcher, "ka")
            assertFalse(matcher.accept(InputToken.KanaObservation("ん", prefix)))
        }
    }

    // Real Japanese QWERTY trace on 2026-10-28: 95–113, 118–136, 168–186, 191–211, 216–224.
    @Test fun measuredSimejiNasalTransitionsPreserveTheSingleAndDoubleNSpellings() {
        for ((notifications, input) in listOf(
            listOf("h", "は", "はn", "はんp", "はんぱ", "はんぱt", "はんぱつ") to "hanpatu",
            listOf("h", "は", "はn", "はん", "はんp", "はんぱ", "はんぱt", "はんぱつ") to "hannpatu",
            listOf("h", "ほ", "ほn", "ほny", "ほにゃ", "ほにゃk", "ほにゃく") to "honyaku",
            listOf("h", "ほ", "ほn", "ほん", "ほんy", "ほんや", "ほんやk", "ほんやく") to "honnyaku"
        )) {
            val decoder = ImeStrokeDecoder()
            val target = if (input.startsWith("han")) "はんぱつの" else "ほんやくの"
            val engine = WordMatchEngine(listOf(word(target)))
            for (source in notifications) {
                val edit = decoder.composing(source)
                assertEquals(source, edit.sourceText)
                assertTrue("$input / $source / ${edit.inputText}", input.startsWith(edit.inputText))
                val state = engine.previewTokens(edit.tokens).single()
                if (source == "はn") assertEquals("hanpatsuno", state.inputText)
                if (source == "はん") assertEquals("hannpatsuno", state.inputText)
            }
            val edit = decoder.composing(notifications.last())
            assertEquals(input, edit.inputText)
            val state = engine.previewTokens(edit.tokens).single()
            if (input == "honyaku") assertEquals(0, state.matchedCharacters)
            else assertEquals(input + "no", state.inputText)
        }
    }

    @Test fun measuredSimejiFinalNasalOnlyCompletesAtTheSecondNTransition() {
        val decoder = ImeStrokeDecoder()
        val engine = WordMatchEngine(listOf(word("かん")))
        for (source in listOf("k", "か", "かn")) {
            assertFalse(engine.previewTokens(decoder.composing(source).tokens).single().isCompleted)
        }
        val final = decoder.composing("かん")
        assertEquals("kann", final.inputText)
        assertEquals("かん", final.sourceText)
        assertTrue(engine.previewTokens(final.tokens).single().isCompleted)
        assertEquals(listOf(InputToken.KanaObservation("か", "k"), InputToken.KanaObservation("ん", "n")), final.tokens)
    }

    @Test fun optionalDoubleNIsOnlyDisplayedAfterTheSecondNIsEntered() {
        val matcher = WordMatcher(word("はんぱつ"))
        assertEquals("hanpatsu", JapaneseTypingLanguage.defaultRoman(word("はんぱつ").characters))
        feed(matcher, "han")
        assertEquals("hanpatsu", matcher.snapshot().inputText)
        assertEquals(3, matcher.snapshot().matchedInputCharacters)
        val singleN = matcher.fork()
        singleN.accept("p")
        assertEquals("hanpatsu", singleN.snapshot().inputText)
        matcher.accept("n")
        assertEquals("hannpatsu", matcher.snapshot().inputText)
        assertEquals(4, matcher.snapshot().matchedInputCharacters)
        assertEquals("honnyaku", JapaneseTypingLanguage.defaultRoman(word("ほんやく").characters))
        assertEquals("kann", JapaneseTypingLanguage.defaultRoman(word("かん").characters))
    }

    @Test fun nasalBeforeAConsonantAcceptsBothNAndNnWithoutRewritingTheChoice() {
        for (input in listOf("hanpatu", "hannpatu")) {
            val matcher = WordMatcher(word("はんぱつの"))
            assertFalse(feed(matcher, input))
            assertEquals(input + "no", matcher.snapshot().inputText)
            assertEquals(input.length, matcher.snapshot().matchedInputCharacters)
            assertTrue(feed(matcher, "no"))
        }
    }

    @Test fun nFollowedByAVowelOrYBelongsToTheNextSyllable() {
        assertTrue(feed(WordMatcher(word("な")), "na"))
        assertTrue(feed(WordMatcher(word("にゃ")), "nya"))
        assertTrue(feed(WordMatcher(word("ほにゃく")), "honyaku"))
        assertFalse(feed(WordMatcher(word("ほんやく")), "honyaku"))
        assertTrue(feed(WordMatcher(word("ほんやく")), "honnyaku"))
    }

    @Test fun aFinalNasalRequiresExactlyTheNnSpelling() {
        val matcher = WordMatcher(word("かん"))
        assertFalse(feed(matcher, "kan"))
        assertEquals("kann", matcher.snapshot().inputText)
        assertEquals(3, matcher.snapshot().matchedInputCharacters)
        assertTrue(matcher.accept("n"))
        for (input in listOf("kan", "kan'", "kaxn")) {
            assertFalse(input, feed(WordMatcher(word("かん")), input))
        }
        assertTrue(feed(WordMatcher(word("かん")), "かん"))
    }

    // Replays of the user's Simeji trace, sequence 191–199, 216–226 and 231–243.
    // These are measured callbacks, not assumptions about physical key events.
    @Test fun realSimejiSiAndShiRetainDifferentConfirmedSpellings() {
        for ((notifications, spelling) in listOf(
            listOf("s", "し", "しm", "しま") to "sima",
            listOf("s", "sh", "し", "しm", "しま") to "shima"
        )) {
            val decoder = ImeStrokeDecoder()
            val controller = GameInputController(listOf("しまの", "しまのの").map(::word), activeWordCount = 1, random = Random(0))
            notifications.forEach { source ->
                val edit = decoder.composing(source)
                val update = controller.onInputTokens(edit.tokens, true, edit.inputText)
                assertEquals(source, edit.sourceText)
                assertTrue(edit.inputText, spelling.startsWith(edit.inputText))
                assertEquals(edit.inputText, update.receivedText)
                if (source == "s") assertTrue(update.words.single().inputText.startsWith("shimano"))
                if (source == "しま") {
                    assertEquals(spelling, update.receivedText)
                    assertTrue(update.words.single().inputText.startsWith(spelling + "no"))
                    assertEquals(spelling.length, update.words.single().matchedInputCharacters)
                }
            }
            assertEquals(spelling, decoder.commit("しま").inputText)
        }
    }

    @Test fun realSimejiNettoHasNoXtuInTheConfirmedInputDisplay() {
        val decoder = ImeStrokeDecoder()
        for ((source, input) in listOf("n" to "n", "ね" to "ne", "ねt" to "net",
            "ねっt" to "nett", "ねっと" to "netto")) {
            val edit = decoder.composing(source)
            assertEquals(source, edit.sourceText)
            assertEquals(input, edit.inputText)
        }
    }

    @Test fun aKanaObservationCannotSilentlySupplyAnUnobservedConsonant() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("s")
        val edit = decoder.composing("し")
        val state = WordMatchEngine(listOf(word("しっぱい"))).previewTokens(edit.tokens).single()
        assertEquals("sippai", state.inputText)
        assertEquals(2, state.matchedInputCharacters)
        assertEquals("si", edit.inputText)
        assertEquals(listOf(InputToken.KanaObservation("し", "s")), edit.tokens)
    }

    @Test fun anOpaqueMultiSyllableRewriteDoesNotClaimAnUnseenRomanSpelling() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("s")
        val edit = decoder.composing("しま")
        assertEquals("しま", edit.inputText)
        val state = WordMatchEngine(listOf(word("しまの"))).previewTokens(edit.tokens).single()
        assertEquals("しまの", state.inputText)
        assertEquals(2, state.matchedCharacters)
    }

    private fun word(text: String) = WordDefinition(text, text)
    private fun feed(matcher: WordMatcher, text: String): Boolean =
        splitTextCharacters(text).map(matcher::accept).any { it }
    private fun controller() = GameInputController(
        listOf("はなび", "しゃめん", "ちゃわん", "なごや", "きょうと").map(::word), activeWordCount = 3, random = Random(7)
    )

    @Test fun kanaPrefixesAndInvalidSuffixesFollowOriginalRules() {
        val engine = WordMatchEngine(listOf("しんおおさか", "しんよこはま", "しながわ", "だみーしん").map(::word))
        engine.accept("し")
        assertEquals(listOf(1, 1, 1, 0), engine.snapshot().map { it.matchedCharacters })
        engine.accept("ん")
        assertEquals(listOf(2, 2, 0, 0), engine.snapshot().map { it.matchedCharacters })
        engine.accept("こ")
        assertTrue(engine.snapshot().all { it.matchedCharacters == 0 })
    }

    @Test fun mistakesCanBeFollowedByAnotherWordWithoutDeleting() {
        for (input in listOf("しんしながわ", "あああしながわ", "とうきょしながわ")) {
            assertTrue(input, feed(WordMatcher(word("しながわ")), input))
        }
        assertTrue(feed(WordMatcher(word("ああい")), "あああい"))
    }

    @Test fun shaAdvancesBothShaAndHaSuffix() {
        val engine = WordMatchEngine(listOf(word("しゃめん"), word("はなび")))
        "sha".forEach { engine.accept(it.toString()) }
        val states = engine.snapshot()
        assertEquals(listOf(2, 1), states.map { it.matchedCharacters })
        assertEquals(listOf("shamenn", "hanabi"), states.map { it.inputText })
        assertEquals(listOf(3, 2), states.map { it.matchedInputCharacters })
    }

    @Test fun unfinishedRomanSyllableKeepsPrefixAndHighlightsNewStrokes() {
        val matcher = WordMatcher(word("はなび"))
        feed(matcher, "han")
        assertEquals(1, matcher.matchedCharacters)
        assertEquals("hanabi", matcher.snapshot().inputText)
        assertEquals(3, matcher.snapshot().matchedInputCharacters)
        feed(matcher, "ab")
        assertEquals(2, matcher.matchedCharacters)
        assertEquals(5, matcher.snapshot().matchedInputCharacters)
        assertTrue(matcher.accept("i"))
    }

    @Test fun chaAliasesDriveTheDisplayedSpelling() {
        for (form in listOf("cha", "tya", "cya", "tila", "tixa", "tilya", "tixya", "chixya", "chilya")) {
            val matcher = WordMatcher(word("ちゃわん"))
            assertFalse(form, feed(matcher, form))
            assertEquals(form, 2, matcher.matchedCharacters)
            assertEquals(form, form + "wann", matcher.snapshot().inputText)
            assertEquals(form, form.length, matcher.snapshot().matchedInputCharacters)
            assertTrue(form, feed(matcher, "wann"))
        }
    }

    @Test fun jiAndSmallVowelAliasesAreAccepted() {
        for (form in listOf("ja", "zya", "jya", "jixya", "zilya")) {
            assertTrue(form, feed(WordMatcher(word("じゃ")), form))
        }
        for (form in listOf("wi", "whi", "uxi", "uli")) {
            assertTrue(form, feed(WordMatcher(word("うぃ")), form))
        }
        for (form in listOf("we", "whe", "uxe", "ule")) {
            assertTrue(form, feed(WordMatcher(word("うぇ")), form))
        }
    }

    @Test fun sokuonAndNasalSpellingBoundariesAreHandled() {
        for (form in listOf("gakkou", "gaxtukou", "galtukou", "gaxtsukou")) {
            assertTrue(form, feed(WordMatcher(word("がっこう")), form))
        }
        for (form in listOf("shinbunn", "shinnbunn", "shin'bunn")) {
            assertTrue(form, feed(WordMatcher(word("しんぶん")), form))
        }
        assertTrue(feed(WordMatcher(word("かんい")), "kanni"))
        assertTrue(feed(WordMatcher(word("かんい")), "kan'i"))
        assertFalse(feed(WordMatcher(word("かんい")), "kani"))
    }

    @Test fun switchingToKanaPreservesCompletedReadingAndChangesPrompt() {
        val matcher = WordMatcher(word("はなび"))
        feed(matcher, "ha")
        assertEquals("hanabi", matcher.snapshot().inputText)
        matcher.accept("な")
        assertEquals("はなび", matcher.snapshot().inputText)
        assertEquals(2, matcher.snapshot().matchedInputCharacters)
        assertTrue(feed(matcher, "bi"))
    }

    @Test fun switchingToRomanAfterKanaPreservesProgress() {
        val matcher = WordMatcher(word("はなび"))
        matcher.accept("ハ")
        feed(matcher, "NA")
        assertEquals("hanabi", matcher.snapshot().inputText)
        assertEquals(4, matcher.snapshot().matchedInputCharacters)
        assertTrue(feed(matcher, "bi"))
    }

    @Test fun aCompletionPreservesOtherWordsPartialPaths() {
        val engine = WordMatchEngine(listOf(word("だみーしん"), word("しんおおさか")))
        splitTextCharacters("だみーしん").forEach { engine.accept(it) }
        assertTrue(engine.snapshot().first().isCompleted)
        assertEquals(2, engine.snapshot()[1].matchedCharacters)
        val romanEngine = WordMatchEngine(listOf(word("は"), word("ひ")))
        romanEngine.accept("h"); romanEngine.accept("a")
        assertEquals(0, romanEngine.snapshot()[1].matchedInputCharacters)
    }

    @Test fun compositionCompletionDefeatsWithoutEnterAndReplacesOneSlot() {
        val controller = controller()
        val before = controller.currentWords()
        val target = before.first().definition
        val roman = JapaneseTypingLanguage.defaultRoman(target.characters)
        val prefix = controller.onInput(splitTextCharacters(roman.dropLast(1)), true)
        assertEquals(0, prefix.defeatedCount)
        val hit = controller.onInput(splitTextCharacters(roman), true)
        assertEquals(listOf(target), hit.completedWords)
        assertEquals(1, hit.defeatedCount)
        assertEquals("", hit.receivedText)
        assertEquals(3, hit.words.size)
        assertFalse(hit.words.any { it.definition.id == target.id })
        assertTrue(hit.words.all { it.inputText.all { c -> c.code < 128 } })
        assertEquals(1, (hit.words.map { it.definition } - before.map { it.definition }.toSet()).size)
    }

    @Test fun inactiveWordsAreNotMatched() {
        val controller = controller()
        val active = controller.currentWords().map { it.definition.text }.toSet()
        val inactive = listOf("はなび", "しゃめん", "ちゃわん", "なごや", "きょうと").first { it !in active }
        val update = controller.onInput(splitTextCharacters(inactive), false)
        assertTrue(update.completedWords.isEmpty())
        assertEquals(0, update.defeatedCount)
    }

    @Test fun confirmingPartialTextDoesNotDeleteGameInput() {
        val controller = controller()
        val target = controller.currentWords().first().definition
        val first = target.characters.first()
        controller.onInput(listOf(first), true)
        val committed = controller.onInput(listOf(first), false)
        assertEquals(first, committed.receivedText)
        val empty = controller.onInput(emptyList(), false)
        assertEquals(first, empty.receivedText)
        assertEquals(1, empty.words.first { it.definition == target }.matchedCharacters)
        assertEquals(0, empty.defeatedCount)
    }

    @Test fun imeKanaReplacementPreservesObservedRomanStrokes() {
        val decoder = ImeStrokeDecoder()
        assertEquals(listOf(InputToken.Stroke("s")), decoder.composing("s").tokens)
        decoder.composing("sh")
        val converted = decoder.composing("しゃ")
        assertEquals(listOf(InputToken.KanaObservation("しゃ", "sh")), converted.tokens)
        assertEquals("しゃ", converted.sourceText)
        assertEquals(converted.tokens, decoder.commit("しゃ").tokens)
        assertTrue(decoder.finish().tokens.isEmpty())
    }

    @Test fun imeIntermediateLatinDoesNotEraseYellowPrefix() {
        val decoder = ImeStrokeDecoder()
        val engine = WordMatchEngine(listOf(word("はなび")))
        for ((visible, highlighted) in listOf("h" to 1, "は" to 2, "はn" to 3, "はな" to 4, "はなb" to 5)) {
            val edit = decoder.composing(visible)
            val state = engine.previewTokens(edit.tokens).single()
            assertEquals(visible, highlighted, state.matchedInputCharacters)
            assertEquals("hanabi", state.inputText)
        }
        assertTrue(engine.previewTokens(decoder.composing("はなび").tokens).single().isCompleted)
    }

    @Test fun kanaDakutenCanReviseCompositionWithoutLosingEarlierPrefix() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("なは")
        val revised = decoder.composing("なば")
        assertEquals(listOf("な", "ば").map { InputToken.Stroke(it) }, revised.tokens)
        val engine = WordMatchEngine(listOf(word("なばな")))
        assertEquals(2, engine.previewTokens(revised.tokens).single().matchedCharacters)
    }

    @Test fun deletionOfCompositionIsRejectedButKanaConversionIsAllowed() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("han")
        assertFalse(decoder.composing("ha").accepted)
        assertFalse(decoder.composing("").accepted)
        assertEquals(listOf(InputToken.KanaObservation("はな", "han")), decoder.composing("はな").tokens)
        decoder.reset()
        decoder.composing("かな")
        assertFalse(decoder.composing("か").accepted)
        assertEquals(listOf("か", "な").map { InputToken.Stroke(it) }, decoder.commit("かな").tokens)
    }

    @Test fun everyDictionaryReadingHasACompletableCanonicalRomanPath() {
        val file = listOf(File("src/main/res/raw/word_list.txt"), File("app/src/main/res/raw/word_list.txt"))
            .first { it.isFile }
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val columns = line.split('\t')
            val reading = columns.getOrElse(1) { columns[0] }
            val definition = word(reading)
            val roman = JapaneseTypingLanguage.defaultRoman(definition.characters)
            assertTrue("$reading -> $roman", feed(WordMatcher(definition), roman))
            assertTrue(reading, feed(WordMatcher(definition), reading))
        }
    }

    @Test fun anImeRewriteCannotUndoPreviouslyObservedMistypedLetters() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("han")
        assertFalse(decoder.composing("ha").accepted)
        assertEquals(listOf("h", "a", "n", "s").map { InputToken.Stroke(it) }, decoder.composing("has").tokens)
        decoder.reset()
        decoder.composing("はな")
        assertFalse(decoder.composing("は").accepted)
        assertEquals(listOf("は", "な", "び").map { InputToken.Stroke(it) }, decoder.composing("はび").tokens)
    }

    @Test fun promptFollowsAnUnfinishedAlternativeAndWiMeansSmallVowel() {
        val matcher = WordMatcher(word("ちゃわん"))
        matcher.accept("t")
        assertEquals("tyawann", matcher.snapshot().inputText)
        matcher.accept("i")
        assertEquals("tilawann", matcher.snapshot().inputText)
        matcher.accept("x")
        assertEquals("tixawann", matcher.snapshot().inputText)
        assertEquals(3, matcher.snapshot().matchedInputCharacters)
        assertFalse(feed(WordMatcher(word("ゐ")), "wi"))
        assertTrue(feed(WordMatcher(word("ゐ")), "wyi"))
        assertTrue(feed(WordMatcher(word("うぃ")), "wi"))
    }


    @Test fun pendingDakutenKeepsOnlyTheAlreadyMatchedPrefix() {
        val engine = WordMatchEngine(listOf(word("だいどころ"), word("だいとかい")))
        assertEquals(listOf(2, 2), engine.preview(splitTextCharacters("だい")).map { it.matchedCharacters })
        val pending = engine.preview(splitTextCharacters("だいと"))
        assertEquals(listOf(2, 3), pending.map { it.matchedCharacters })
        assertTrue(pending.none { it.isCompleted })
        val corrected = engine.preview(splitTextCharacters("だいど"))
        assertEquals(3, corrected.first().matchedInputCharacters)
        assertTrue(engine.preview(splitTextCharacters("だいどころ")).first().isCompleted)
    }

    @Test fun kanaModifiersCanWaitButCannotCompleteAnIncorrectWord() {
        for ((reading, pending, highlighted) in listOf(
            Triple("かぎ", "かき", 1), Triple("にっぽん", "にっほ", 2),
            Triple("きゃく", "きや", 1), Triple("まっちゃ", "まつ", 1)
        )) {
            val engine = WordMatchEngine(listOf(word(reading)))
            val preview = engine.preview(splitTextCharacters(pending)).single()
            assertEquals(reading, highlighted, preview.matchedInputCharacters)
            assertFalse(reading, preview.isCompleted)
            assertTrue(reading, engine.preview(splitTextCharacters(reading)).single().isCompleted)
        }
    }

    @Test fun confirmedOrInteriorWrongKanaStillInvalidatesProgress() {
        val engine = WordMatchEngine(listOf(word("だいどころ")))
        splitTextCharacters("だいと").forEach { engine.accept(it) }
        assertEquals(0, engine.snapshot().single().matchedCharacters)
        val fresh = WordMatchEngine(listOf(word("だいどころ")))
        assertEquals(0, fresh.preview(splitTextCharacters("だいとこ")).single().matchedCharacters)
    }

    @Test fun aPendingModifierDoesNotIncreaseTheDefeatCounter() {
        val controller = GameInputController(
            listOf("かぎ", "かぎや", "かぎばこ", "かぎあな").map(::word),
            activeWordCount = 1, random = Random(5)
        )
        val target = controller.currentWords().single().definition
        val pending = controller.onInput(splitTextCharacters("かき"), true)
        assertEquals(1, pending.words.single().matchedCharacters)
        assertEquals(0, pending.defeatedCount)
        assertTrue(pending.completedWords.isEmpty())
        val corrected = controller.onInput(target.characters, true)
        assertEquals(listOf(target), corrected.completedWords)
        assertEquals(1, corrected.defeatedCount)
    }


    @Test fun bareLengthMarksContinueRomanKeyboardInputWithoutFlickering() {
        val decoder = ImeStrokeDecoder()
        val engine = WordMatchEngine(listOf(word("きーぼーど")))
        for ((visible, expected) in listOf(
            "k" to "k", "き" to "ki", "きー" to "ki-", "きーb" to "ki-b",
            "きーぼ" to "ki-bo", "きーぼー" to "ki-bo-", "きーぼーd" to "ki-bo-d"
        )) {
            val edit = decoder.composing(visible)
            assertEquals(visible, edit.sourceText)
            val state = engine.previewTokens(edit.tokens).single()
            assertEquals(visible, "ki-bo-do", state.inputText)
            assertEquals(visible, expected.length, state.matchedInputCharacters)
        }
        assertTrue(engine.previewTokens(decoder.composing("きーぼーど").tokens).single().isCompleted)
    }

    @Test fun lengthMarkConversionDoesNotDuplicateAndKeepsTheInferredModeAcrossClears() {
        val decoder = ImeStrokeDecoder()
        assertEquals(listOf(InputToken.Stroke("ー")), decoder.commit("ー").tokens)
        decoder.composing("-")
        assertEquals(listOf(InputToken.KanaObservation("ー", "-")), decoder.commit("ー").tokens)
        decoder.reset()
        assertEquals(listOf(InputToken.KanaObservation("ー", "")), decoder.composing("ー").tokens)
        decoder.finish()
        decoder.commit("な")
        assertEquals(listOf(InputToken.Stroke("ー")), decoder.composing("ー").tokens)
    }


    @Test fun theDefaultShiSpellingStaysUntilAnObservedLetterRulesItOut() {
        val matcher = WordMatcher(word("しっぱい"))
        matcher.accept("s")
        assertEquals("shippai", matcher.snapshot().inputText)
        val withH = matcher.fork()
        withH.accept("h")
        assertEquals("shippai", withH.snapshot().inputText)
        matcher.accept("i")
        assertEquals("sippai", matcher.snapshot().inputText)
    }

    @Test fun kanasimiIsNeitherRewrittenNorDisplayedAsKanashimi() {
        val controller = GameInputController(
            listOf("かなしみの", "かなしみから", "かなしみへ", "かなしみまで").map(::word),
            activeWordCount = 1, random = Random(9)
        )
        val update = controller.onInput(splitTextCharacters("kanasimi"), false)
        assertEquals("kanasimi", update.receivedText)
        assertTrue(update.words.single().inputText.startsWith("kanasimi"))
        assertFalse(update.words.single().inputText.startsWith("kanashimi"))
        assertEquals(8, update.words.single().matchedInputCharacters)
    }

    @Test fun anImeSokuonObservationMatchesNettoWithoutCreatingXtuInput() {
        val decoder = ImeStrokeDecoder()
        val engine = WordMatchEngine(listOf(word("ねっと")))
        for ((visible, highlighted) in listOf("n" to 1, "ね" to 2, "ねt" to 3, "ねっt" to 4)) {
            val edit = decoder.composing(visible)
            assertEquals(visible, edit.sourceText)
            val state = engine.previewTokens(edit.tokens).single()
            assertEquals(visible, "netto", state.inputText)
            assertEquals(visible, highlighted, state.matchedInputCharacters)
        }
        val hit = decoder.composing("ねっと")
        assertEquals("ねっと", hit.sourceText)
        assertTrue(engine.previewTokens(hit.tokens).single().isCompleted)
        assertFalse(hit.tokens.filterIsInstance<InputToken.Stroke>().any { it.value == "x" })
    }

    @Test fun anAlreadyObservedDoubleConsonantIsNotCountedAgainOnKanaReplacement() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("n")
        decoder.composing("ね")
        decoder.composing("ねtt")
        val changed = decoder.composing("ねっt")
        val engine = WordMatchEngine(listOf(word("ねっと")))
        val state = engine.previewTokens(changed.tokens).single()
        assertEquals("netto", state.inputText)
        assertEquals(4, state.matchedInputCharacters)
        assertTrue(engine.previewTokens(decoder.composing("ねっと").tokens).single().isCompleted)
    }

    @Test fun explicitXtuInputIsAllowedButAnUnobservedSpellingIsNotInserted() {
        val matcher = WordMatcher(word("ねっとの"))
        feed(matcher, "nextuto")
        assertEquals("nextutono", matcher.snapshot().inputText)
        val decoder = ImeStrokeDecoder()
        decoder.composing("s")
        val kana = decoder.composing("し")
        assertEquals("し", kana.sourceText)
        assertEquals(listOf(InputToken.KanaObservation("し", "s")), kana.tokens)
        assertFalse(kana.tokens.any { it is InputToken.Stroke && it.value == "h" })
    }


    @Test fun nettoFromImeNotificationsDefeatsAndClearsTheActualReceivedText() {
        val pool = listOf("ねっと", "うま", "にんじん", "らじお").map(::word)
        val controller = (0..30).asSequence().map { GameInputController(pool, activeWordCount = 3, random = Random(it)) }
            .first { game -> game.currentWords().any { it.definition.text == "ねっと" } }
        val decoder = ImeStrokeDecoder()
        var update = controller.onInput(emptyList(), false)
        for (text in listOf("n", "ね", "ねt", "ねっt", "ねっと")) {
            val edit = decoder.composing(text)
            update = controller.onInputTokens(edit.tokens, edit.composing, edit.sourceText)
        }
        assertEquals(listOf("ねっと"), update.completedWords.map { it.text })
        assertEquals(1, update.defeatedCount)
        assertEquals("", update.receivedText)
    }

    @Test fun imoutoKeepsRomanModeWhenTheImeEmitsBareVowels() {
        val decoder = ImeStrokeDecoder()
        val engine = WordMatchEngine(listOf(word("いもうと")))
        assertEquals(listOf(InputToken.Stroke("い")), decoder.composing("い").tokens)
        for ((visible, expected) in listOf(
            "いm" to "im", "いも" to "imo", "いもう" to "imou", "いもうt" to "imout"
        )) {
            val edit = decoder.composing(visible)
            assertEquals(visible, edit.sourceText)
            val state = engine.previewTokens(edit.tokens).single()
            assertEquals(visible, "imouto", state.inputText)
            assertEquals(visible, expected.length, state.matchedInputCharacters)
        }
        assertEquals("いもうと", decoder.composing("いもうと").sourceText)
    }

    @Test fun allFiveBareVowelsContinueAnObservedRomanComposition() {
        val decoder = ImeStrokeDecoder()
        decoder.composing("k")
        decoder.composing("か")
        for ((visible, expected) in listOf(
            "かあ" to "kaa", "かあい" to "kaai", "かあいう" to "kaaiu",
            "かあいうえ" to "kaaiue", "かあいうえお" to "kaaiueo"
        )) {
            val edit = decoder.composing(visible)
            assertEquals(visible, edit.sourceText)
            val engine = WordMatchEngine(listOf(word("かあいうえおな")))
            assertEquals(expected.length, engine.previewTokens(edit.tokens).single().matchedInputCharacters)
        }
    }

    @Test fun transportClearsPreserveTheModeButDefiniteKanaSwitchesItBack() {
        val decoder = ImeStrokeDecoder()
        assertEquals(listOf(InputToken.Stroke("あ")), decoder.commit("あ").tokens)
        assertEquals(listOf(InputToken.Stroke("m")), decoder.commit("m").tokens)
        assertEquals(listOf(InputToken.KanaObservation("お", "")), decoder.commit("お").tokens)
        decoder.reset()
        assertEquals(listOf(InputToken.KanaObservation("う", "")), decoder.composing("う").tokens)
        decoder.finish()
        assertEquals(listOf(InputToken.Stroke("か")), decoder.composing("か").tokens)
        decoder.commit("か")
        assertEquals(listOf(InputToken.Stroke("う")), decoder.composing("う").tokens)
    }
}

