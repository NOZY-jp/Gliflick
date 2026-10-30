package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuBackdropView

@RunWith(AndroidJUnit4::class)
class TitleMenuInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var title: TitleActivity
    private lateinit var monitor: Instrumentation.ActivityMonitor
    private var destination: Activity? = null

    @Before fun launch() {
        monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        title = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TitleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as TitleActivity
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 27) {
                title.setShowWhenLocked(true)
                title.setTurnScreenOn(true)
            }
        }
        waitUntil { title.hasWindowFocus() && title.findViewById<AppCompatEditText>(R.id.menuInputHost).hasFocus() }
    }

    @After fun finish() {
        instrumentation.runOnMainSync { destination?.finish(); title.finish() }
        instrumentation.removeMonitor(monitor)
    }

    @Test fun normalAndPlCompositionMatchTheFigmaStates() {
        capture("menu-default-pixel7a.png")
        instrumentation.runOnMainSync {
            val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
            val attributes = EditorInfo()
            val connection = editor.onCreateInputConnection(attributes)!!
            assertTrue(attributes.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0)
            connection.setComposingText("p", 1)
            connection.setComposingText("pl", 1)
            assertEquals(2, highlightedLetters(R.id.menuPlay))
            assertEquals(0, highlightedLetters(R.id.menuArmory))
            assertEquals(0, highlightedLetters(R.id.menuUpgrades))
            assertEquals(0, highlightedLetters(R.id.menuOptions))
        }
        assertEquals(0, monitor.hits)
        capture("menu-pl-pixel7a.png")
    }

    @Test fun allFourTypedCommandsNavigateAndIdentifyTheirOrigin() {
        for (command in listOf("play", "armory", "upgrades", "options")) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                editor.onCreateInputConnection(EditorInfo())!!.setComposingText(command, 1)
            }
            assertDestination(command)
            returnToMenu()
        }
    }

    @Test fun tapTypesAtOneHundredMillisecondIntervalsAndIgnoresDuplicateSelection() {
        instrumentation.runOnMainSync { title.findViewById<TextView>(R.id.menuOptions).performClick() }
        waitUntil { highlightedLetters(R.id.menuOptions) == 1 }
        instrumentation.runOnMainSync {
            assertEquals(1, highlightedLetters(R.id.menuOptions))
            assertEquals(0, monitor.hits)
            title.findViewById<TextView>(R.id.menuArmory).performClick()
            title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText("play", 1)
        }
        assertDestination("options")
        assertEquals(1, monitor.hits)
    }

    @Test fun tapContinuesTheMatchingPrefixAndBackReturnsToAnEmptyMenu() {
        instrumentation.runOnMainSync {
            val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
            editor.onCreateInputConnection(EditorInfo())!!.setComposingText("pl", 1)
            title.findViewById<TextView>(R.id.menuPlay).performClick()
        }
        assertDestination("play")
        returnToMenu()
        instrumentation.runOnMainSync {
            assertEquals("", title.findViewById<AppCompatEditText>(R.id.menuInputHost).text.toString())
            assertEquals(0, highlightedLetters(R.id.menuPlay))
            assertEquals(1f, title.findViewById<TextView>(R.id.menuPlay).alpha)
            assertEquals(0f, title.findViewById<MenuBackdropView>(R.id.menuBackdrop).transitionProgress)
        }
    }

    @Test fun invalidInputResetsAndCanBeRetried() {
        instrumentation.runOnMainSync {
            val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
            editor.onCreateInputConnection(EditorInfo())!!.commitText("plx", 1)
            assertEquals("", editor.text.toString())
            assertEquals(0, highlightedLetters(R.id.menuPlay))
            editor.onCreateInputConnection(EditorInfo())!!.commitText("ARMORY", 1)
        }
        assertDestination("armory")
    }

    @Test fun typoThenArmoryCompletesWithoutLosingTheRestartingA() {
        for (composing in listOf(false, true)) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                val source = "armorarmory"
                for (index in source.indices) {
                    if (composing) connection.setComposingText(source.take(index + 1), 1)
                    else connection.commitText(source[index].toString(), 1)
                    if (index == 5) assertEquals(1, highlightedLetters(R.id.menuArmory))
                }
            }
            assertDestination("armory")
            returnToMenu()
        }
    }

    @Test fun repeatedAStaysHighlightedForEveryCommittedAndComposingUpdate() {
        for (composing in listOf(false, true)) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                repeat(5) { index ->
                    if (composing) connection.setComposingText("a".repeat(index + 1), 1)
                    else connection.commitText("a", 1)
                    assertEquals("a must stay highlighted after update $index", 1,
                        highlightedLetters(R.id.menuArmory))
                }
                if (composing) connection.setComposingText("aaaaaarmory", 1)
                else connection.commitText("rmory", 1)
            }
            assertDestination("armory")
            returnToMenu()
        }
    }

    @Test fun allFourTapsNavigateToTheirOwnDestination() {
        for ((id, command) in listOf(R.id.menuPlay to "play", R.id.menuArmory to "armory",
            R.id.menuUpgrades to "upgrades", R.id.menuOptions to "options")) {
            instrumentation.runOnMainSync { title.findViewById<TextView>(id).performClick() }
            assertDestination(command)
            returnToMenu()
        }
    }

    @Test fun completionAnimatesScaleAndOpacityBeforeNavigation() {
        var centreX = 0f
        var centreY = 0f
        instrumentation.runOnMainSync {
            title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText("play", 1)
            val overlay = title.findViewById<TextView>(R.id.menuSelectionOverlay)
            centreX = overlay.x + overlay.measuredWidth / 2f
            centreY = overlay.y + overlay.measuredHeight / 2f
            assertEquals(1f, overlay.alpha, 0.01f)
        }
        waitUntil { (title.findViewById<TextView>(R.id.menuSelectionOverlay)?.scaleX ?: 0f) > 1.3f }
        instrumentation.runOnMainSync {
            val overlay = title.findViewById<TextView>(R.id.menuSelectionOverlay)
            assertTrue(overlay.scaleX in 1.3f..2f)
            assertEquals(overlay.scaleX, overlay.scaleY)
            assertTrue(overlay.alpha > 0f && overlay.alpha < 0.7f)
            assertEquals(centreX, overlay.x + overlay.width / 2f, 0.5f)
            assertEquals(centreY, overlay.y + overlay.height / 2f, 0.5f)
            assertTrue(title.findViewById<MenuBackdropView>(R.id.menuBackdrop).transitionProgress > 0f)
            captureWindow("menu-transition-pixel7a.png")
        }
        assertDestination("play")
    }

    @Test fun backgroundOutlinesDisappearWhileTheGridStaysVisible() {
        instrumentation.runOnMainSync {
            val backdrop = title.findViewById<MenuBackdropView>(R.id.menuBackdrop)
            fun render(): Bitmap = Bitmap.createBitmap(backdrop.width, backdrop.height,
                Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE)
                backdrop.draw(Canvas(it))
            }
            val initial = render()
            backdrop.transitionProgress = 1f
            val cleared = render()
            val unit = backdrop.width / 1080f
            val gridX = (46.5f * unit).toInt()
            val gridY = (100f * unit).toInt()
            assertEquals(initial.getPixel(gridX, gridY), cleared.getPixel(gridX, gridY))
            assertNotEquals(Color.WHITE, cleared.getPixel(gridX, gridY))
            var changed = 0
            for (y in 0 until backdrop.height step 4) {
                for (x in 0 until backdrop.width step 4) {
                    if (initial.getPixel(x, y) != cleared.getPixel(x, y)) changed++
                }
            }
            assertTrue("The independent outlines must disappear", changed > 1000)
            captureWindow("menu-grid-only-pixel7a.png")
            backdrop.transitionProgress = 0f
            initial.recycle()
            cleared.recycle()
        }
    }

    private fun assertDestination(command: String) {
        destination = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
        assertNotNull("$command should navigate after its animation", destination)
        instrumentation.runOnMainSync {
            assertEquals(command, destination!!.intent.getStringExtra(MainActivity.EXTRA_MENU_COMMAND))
            assertEquals("$command → 入力テスト", destination!!.findViewById<TextView>(R.id.inputTitle).text.toString())
        }
    }

    private fun returnToMenu() {
        instrumentation.runOnMainSync { destination!!.finish() }
        destination = null
        waitUntil { title.hasWindowFocus() && title.findViewById<AppCompatEditText>(R.id.menuInputHost).hasFocus() }
        // waitForMonitorWithTimeout consumes the monitor; register a fresh one for the next launch.
        monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
    }

    private fun highlightedLetters(id: Int): Int {
        val text = title.findViewById<TextView>(id).text as Spanned
        return text.getSpans(2, text.length, ForegroundColorSpan::class.java)
            .filter { text.getSpanStart(it) == 2 }.maxOfOrNull { text.getSpanEnd(it) - 2 } ?: 0
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = check() }
            if (done) return
            SystemClock.sleep(15)
        }
        fail("Timed out waiting for the title menu")
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        var focused = false
        instrumentation.runOnMainSync { focused = title.hasWindowFocus() }
        assertTrue("Capture only the visible application window", focused)
        instrumentation.runOnMainSync { captureWindow(name) }
    }

    /** Render only this app's window; never capture a lock screen or another app. */
    private fun captureWindow(name: String) {
        val decor = title.window.decorView
        val screenshot = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(screenshot))
        val directory = File(instrumentation.targetContext.filesDir, "verification").apply { mkdirs() }
        File(directory, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
