package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.JapaneseTypingLanguage
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GameInputInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
        }
        instrumentation.waitForIdleSync()
        var focused = false
        repeat(30) {
            instrumentation.runOnMainSync { focused = activity.hasWindowFocus() }
            if (!focused) SystemClock.sleep(100)
        }
        assertTrue("The actual app window must be visible before capturing it.", focused)
    }

    @After fun finish() { instrumentation.runOnMainSync { activity.finish() } }

    @Test fun realInputConnectionBlocksDeleteSwitchesModesAndDefeatsWithoutEnter() {
        lateinit var target: WordDefinition
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            target = surface.currentWords().minBy { it.definition.characters.size }.definition
            val connection = host.onCreateInputConnection(EditorInfo())!!
            val roman = JapaneseTypingLanguage.defaultRoman(target.characters)
            connection.setComposingText(roman.take(1), 1)
            assertTrue(surface.currentWords().all { it.inputText.all { c -> c.code < 128 } })
            val beforeDelete = host.text.toString()
            val beforeReceive = activity.findViewById<TextView>(R.id.receivedInputText).text.toString()
            connection.deleteSurroundingText(1, 0)
            connection.deleteSurroundingTextInCodePoints(1, 0)
            if (Build.VERSION.SDK_INT >= 34) {
                connection.replaceText(0, beforeDelete.length, "", 1, null)
            }
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            connection.setComposingText("", 1)
            assertEquals(beforeDelete, host.text.toString())
            assertEquals(beforeReceive, activity.findViewById<TextView>(R.id.receivedInputText).text.toString())
            connection.finishComposingText()
            connection.commitText("な", 1)
            assertTrue(surface.currentWords().all { it.inputText == it.definition.text })
            connection.setComposingText(target.text, 1)
            assertEquals(activity.getString(R.string.defeated_count, 1),
                activity.findViewById<TextView>(R.id.defeatedCountText).text.toString())
            assertTrue(host.text.isNullOrEmpty())
            val hasContinuation = surface.currentWords().any { it.matchedInputCharacters > 0 }
            assertEquals(!hasContinuation, activity.findViewById<TextView>(R.id.receivedInputText).text.toString() ==
                activity.getString(R.string.keyboard_input_empty))
            assertFalse(surface.currentWords().any { it.definition.id == target.id })
        }
    }

    @Test fun shaAndHaHighlightTogetherAndKanaRestoresTheKanaPrompt() {
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            val engine = WordMatchEngine(listOf(
                WordDefinition("slope", "しゃめん", "斜面"),
                WordDefinition("firework", "はなび", "花火"),
                WordDefinition("bowl", "ちゃわん", "茶碗")
            ))
            host.onGameInput = { edit ->
                val strokes = edit.tokens
                val composing = edit.composing
                if (!composing) strokes.forEach { engine.accept(it) }
                val states = if (composing) engine.previewTokens(strokes) else engine.snapshot()
                surface.showWords(states)
                activity.findViewById<TextView>(R.id.receivedInputText).text =
                    activity.getString(R.string.keyboard_input_received, edit.inputText)
                false
            }
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("s", 1)
            connection.setComposingText("sh", 1)
            connection.setComposingText("しゃ", 1)
            assertEquals(listOf(3, 2, 0), surface.currentWords().map { it.matchedInputCharacters })
        }
        capture("typing-sha.png")
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.finishComposingText()
            connection.setComposingText("め", 1)
            val states = activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords()
            assertEquals(listOf("しゃめん", "はなび", "ちゃわん"), states.map { it.inputText })
            assertEquals(3, states.first().matchedInputCharacters)
        }
        capture("typing-kana.png")
    }


    @Test fun bareVowelsInAnImoutoCompositionDoNotSwitchBackToKana() {
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            val engine = WordMatchEngine(listOf(
                WordDefinition("sister", "いもうと", "妹"),
                WordDefinition("penguin", "ぺんぎん", "ペンギン"),
                WordDefinition("lineup", "ならぶ", "並ぶ")
            ))
            host.onGameInput = { edit ->
                val strokes = edit.tokens
                val composing = edit.composing
                if (!composing) strokes.forEach { engine.accept(it) }
                surface.showWords(if (composing) engine.previewTokens(strokes) else engine.snapshot())
                activity.findViewById<TextView>(R.id.receivedInputText).text =
                    activity.getString(R.string.keyboard_input_received, edit.inputText)
                false
            }
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("い", 1)
            connection.setComposingText("いm", 1)
            connection.setComposingText("いも", 1)
            connection.setComposingText("いもう", 1)
            assertEquals("imouto", surface.currentWords().first().inputText)
            assertEquals(4, surface.currentWords().first().matchedInputCharacters)
            assertEquals(activity.getString(R.string.keyboard_input_received, "imou"),
                activity.findViewById<TextView>(R.id.receivedInputText).text.toString())
            assertTrue(surface.currentWords().all { it.inputText.all { c -> c.code < 128 } })
        }
        capture("typing-vowels.png")
    }


    @Test fun anUnvoicedFlickKanaKeepsThePrefixUntilDakutenArrives() {
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            val engine = WordMatchEngine(listOf(
                WordDefinition("kitchen", "だいどころ", "台所"),
                WordDefinition("highway", "こうそくどうろ", "高速道路"),
                WordDefinition("lineup", "ならぶ", "並ぶ")
            ))
            host.onGameInput = { edit ->
                val strokes = edit.tokens
                val composing = edit.composing
                if (!composing) strokes.forEach { engine.accept(it) }
                val states = if (composing) engine.previewTokens(strokes) else engine.snapshot()
                surface.showWords(states)
                activity.findViewById<TextView>(R.id.receivedInputText).text =
                    activity.getString(R.string.keyboard_input_received, edit.inputText)
                states.any { it.isCompleted }
            }
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("だい", 1)
            connection.setComposingText("だいと", 1)
            val pending = surface.currentWords().first()
            assertEquals(2, pending.matchedInputCharacters)
            assertFalse(pending.isCompleted)
            assertEquals("だいどころ", pending.inputText)
        }
        capture("typing-dakuten-pending.png")
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("だいど", 1)
            val state = activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords().first()
            assertEquals(3, state.matchedInputCharacters)
            assertFalse(state.isCompleted)
        }
        capture("typing-dakuten-applied.png")
    }


    @Test fun aLengthMarkFromTheImeKeepsRomanKeyboardPromptsAndProgress() {
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            val engine = WordMatchEngine(listOf(
                WordDefinition("keyboard", "きーぼーど", "キーボード"),
                WordDefinition("camera", "かめら", "カメラ"),
                WordDefinition("radio", "らじお", "ラジオ")
            ))
            host.onGameInput = { edit ->
                val strokes = edit.tokens
                val composing = edit.composing
                if (!composing) strokes.forEach { engine.accept(it) }
                surface.showWords(if (composing) engine.previewTokens(strokes) else engine.snapshot())
                activity.findViewById<TextView>(R.id.receivedInputText).text =
                    activity.getString(R.string.keyboard_input_received, edit.inputText)
                false
            }
            val connection = host.onCreateInputConnection(EditorInfo())!!
            for (text in listOf("k", "き", "きー", "きーb", "きーぼ", "きーぼー")) {
                connection.setComposingText(text, 1)
                assertTrue(surface.currentWords().all { it.inputText.all { c -> c.code < 128 } })
            }
            val state = surface.currentWords().first()
            assertEquals("ki-bo-do", state.inputText)
            assertEquals(6, state.matchedInputCharacters)
            assertFalse(state.isCompleted)
        }
        capture("typing-long-vowel.png")
    }


    @Test fun observedRomanLettersChooseTheSpellingAndSokuonDoesNotInventXtu() {
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            val engine = WordMatchEngine(listOf(
                WordDefinition("failure", "しっぱい", "失敗"),
                WordDefinition("sadness", "かなしみの", "悲しみの"),
                WordDefinition("network", "ねっと", "ネット")
            ))
            host.onGameInput = { edit ->
                if (!edit.composing) edit.tokens.forEach { engine.accept(it) }
                surface.showWords(if (edit.composing) engine.previewTokens(edit.tokens) else engine.snapshot())
                activity.findViewById<TextView>(R.id.receivedInputText).text =
                    activity.getString(R.string.keyboard_input_received, edit.inputText)
                false
            }
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("s", 1)
            assertEquals("shippai", surface.currentWords().first().inputText)
        }
        capture("typing-default-s.png")
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("si", 1)
            assertEquals("sippai", activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords().first().inputText)
            connection.commitText("si", 1)
            connection.setComposingText("kanasimi", 1)
            assertEquals("kanasimino", activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords()[1].inputText)
            assertEquals(activity.getString(R.string.keyboard_input_received, "kanasimi"),
                activity.findViewById<TextView>(R.id.receivedInputText).text.toString())
        }
        capture("typing-kanasimi.png")
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            connection.commitText("kanasimi", 1)
            for (text in listOf("n", "ね", "ねt", "ねっt")) connection.setComposingText(text, 1)
            val state = activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords()[2]
            assertEquals("netto", state.inputText)
            assertEquals(4, state.matchedInputCharacters)
        }
        capture("typing-netto.png")
    }

    @Test fun measuredSimejiNotificationsReachTheProductionUiWithoutChangingTheSpelling() {
        instrumentation.runOnMainSync { activity.finish() }
        activity = instrumentation.startActivitySync(
            Intent().setClassName(instrumentation.targetContext, "${instrumentation.targetContext.packageName}.InputDiagnosticActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            val received = activity.findViewById<TextView>(R.id.receivedInputText)
            for (source in listOf("s", "し", "しm", "しま")) connection.setComposingText(source, 1)
            assertEquals(activity.getString(R.string.keyboard_input_received, "sima"), received.text.toString())
            assertEquals("simano", surface.currentWords().first { it.definition.text == "しまの" }.inputText)
            connection.commitText("しま", 1)
            for (source in listOf("s", "sh", "し", "しm", "しま")) connection.setComposingText(source, 1)
            assertEquals(activity.getString(R.string.keyboard_input_received, "shima"), received.text.toString())
            assertEquals("shimano", surface.currentWords().first { it.definition.text == "しまの" }.inputText)
            connection.commitText("しま", 1)
            for (source in listOf("n", "ね", "ねt", "ねっt", "ねっと")) connection.setComposingText(source, 1)
            assertEquals(activity.getString(R.string.keyboard_input_received, "netto"), received.text.toString())
            assertEquals(5, surface.currentWords().first { it.definition.text == "ねっとの" }.matchedInputCharacters)
            connection.setComposingText("ねっとn", 1)
            connection.setComposingText("ねっとの", 1)
            assertEquals(activity.getString(R.string.defeated_count, 1),
                activity.findViewById<TextView>(R.id.defeatedCountText).text.toString())
            assertEquals(activity.getString(R.string.keyboard_input_empty), received.text.toString())
            assertTrue(host.text.isNullOrEmpty())
            assertFalse(surface.currentWords().any { it.definition.text == "ねっとの" })
        }
        capture("typing-measured-ime-defeat.png")
    }

    @Test fun measuredNasalTransitionsChangeThePromptAndRequireFinalDoubleN() {
        instrumentation.runOnMainSync { activity.finish() }
        activity = instrumentation.startActivitySync(
            Intent().setClassName(instrumentation.targetContext, "${instrumentation.targetContext.packageName}.InputDiagnosticActivity")
                .putExtra("nasal_board", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            for (source in listOf("h", "は", "はn")) connection.setComposingText(source, 1)
            assertEquals("hanpatsu", surface.currentWords().first { it.definition.text == "はんぱつ" }.inputText)
            connection.setComposingText("はん", 1)
            assertEquals("hannpatsu", surface.currentWords().first { it.definition.text == "はんぱつ" }.inputText)
            assertEquals(4, surface.currentWords().first { it.definition.text == "はんぱつ" }.matchedInputCharacters)
        }
        capture("typing-double-n.png")
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            for (source in listOf("はんp", "はんぱ", "はんぱt", "はんぱつ")) connection.setComposingText(source, 1)
            assertEquals(activity.getString(R.string.defeated_count, 1),
                activity.findViewById<TextView>(R.id.defeatedCountText).text.toString())
            for (source in listOf("k", "か", "かn")) connection.setComposingText(source, 1)
            val state = activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords()
                .first { it.definition.text == "かん" }
            assertEquals("kann", state.inputText)
            assertEquals(3, state.matchedInputCharacters)
            assertEquals(activity.getString(R.string.defeated_count, 1),
                activity.findViewById<TextView>(R.id.defeatedCountText).text.toString())
        }
        capture("typing-final-n-pending.png")
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            host.onCreateInputConnection(EditorInfo())!!.setComposingText("かん", 1)
            assertEquals(activity.getString(R.string.defeated_count, 2),
                activity.findViewById<TextView>(R.id.defeatedCountText).text.toString())
            assertTrue(host.text.isNullOrEmpty())
            assertEquals(activity.getString(R.string.keyboard_input_empty),
                activity.findViewById<TextView>(R.id.receivedInputText).text.toString())
        }
    }

    @Test fun overlapStreakAndMistakesReachTheEightWordUi() {
        instrumentation.runOnMainSync { activity.finish() }
        activity = instrumentation.startActivitySync(
            Intent().setClassName(instrumentation.targetContext, "${instrumentation.targetContext.packageName}.InputDiagnosticActivity")
                .putExtra("streak_board", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val host = activity.findViewById<GameImeEditText>(R.id.imeInputHost)
            val connection = host.onCreateInputConnection(EditorInfo())!!
            val surface = activity.findViewById<MySurfaceView>(R.id.mySurfaceView)
            assertEquals(8, surface.currentWords().size)
            connection.setComposingText("pasoko", 1)
            assertEquals(activity.getString(R.string.streak_count, 3),
                activity.findViewById<TextView>(R.id.streakText).text.toString())
            assertEquals(activity.getString(R.string.mistype_count, 0),
                activity.findViewById<TextView>(R.id.mistypeCountText).text.toString())
            assertEquals(activity.getString(R.string.keyboard_input_received, "pasoko"),
                activity.findViewById<TextView>(R.id.receivedInputText).text.toString())
            assertEquals(6, surface.currentWords().first { it.definition.text == "ぱそこん" }.matchedInputCharacters)
            assertTrue(host.text.isNullOrEmpty())
        }
        capture("typing-overlap-progress.png")
        instrumentation.runOnMainSync {
            val connection = activity.findViewById<GameImeEditText>(R.id.imeInputHost).onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("nn", 1)
            assertEquals(activity.getString(R.string.streak_count, 4),
                activity.findViewById<TextView>(R.id.streakText).text.toString())
            repeat(3) { connection.setComposingText("x", 1) }
            assertEquals(activity.getString(R.string.streak_count, 0),
                activity.findViewById<TextView>(R.id.streakText).text.toString())
            assertEquals(activity.getString(R.string.mistype_count, 1),
                activity.findViewById<TextView>(R.id.mistypeCountText).text.toString())
            connection.commitText("x", 1)
            connection.setComposingText("eigo", 1)
            assertEquals(activity.getString(R.string.streak_count, 3),
                activity.findViewById<TextView>(R.id.streakText).text.toString())
            assertEquals(activity.getString(R.string.mistype_count, 1),
                activity.findViewById<TextView>(R.id.mistypeCountText).text.toString())
            assertEquals(activity.getString(R.string.defeated_count, 4),
                activity.findViewById<TextView>(R.id.defeatedCountText).text.toString())
            assertEquals(8, activity.findViewById<MySurfaceView>(R.id.mySurfaceView).currentWords().size)
        }
        capture("typing-overlap-streak.png")
    }

    @Test fun sharedAndSeparateWordsDisplaySixAndEightKana() {
        fun openBoard() {
            instrumentation.runOnMainSync { activity.finish() }
            activity = instrumentation.startActivitySync(
                Intent().setClassName(instrumentation.targetContext, "${instrumentation.targetContext.packageName}.InputDiagnosticActivity")
                    .putExtra("streak_board", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
            instrumentation.waitForIdleSync()
        }
        openBoard()
        instrumentation.runOnMainSync {
            val connection = activity.findViewById<GameImeEditText>(R.id.imeInputHost).onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("tyawanngann", 1)
            assertEquals(activity.getString(R.string.streak_count, 6),
                activity.findViewById<TextView>(R.id.streakText).text.toString())
            assertEquals(activity.getString(R.string.mistype_count, 0),
                activity.findViewById<TextView>(R.id.mistypeCountText).text.toString())
        }
        capture("typing-shared-six.png")
        openBoard()
        instrumentation.runOnMainSync {
            val connection = activity.findViewById<GameImeEditText>(R.id.imeInputHost).onCreateInputConnection(EditorInfo())!!
            connection.setComposingText("tyawann", 1)
            connection.setComposingText("wanngann", 1)
            assertEquals(activity.getString(R.string.streak_count, 8),
                activity.findViewById<TextView>(R.id.streakText).text.toString())
            assertEquals(activity.getString(R.string.mistype_count, 0),
                activity.findViewById<TextView>(R.id.mistypeCountText).text.toString())
        }
        capture("typing-separate-eight.png")
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        if (Build.VERSION.SDK_INT >= 31) {
            val pipes = instrumentation.uiAutomation.executeShellCommandRw("dd of=/data/local/tmp/testgame-$name")
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
        } else {
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
            file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        screenshot.recycle()
    }
}

