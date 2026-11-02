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
    private lateinit var combatMonitor: Instrumentation.ActivityMonitor
    private var destination: Activity? = null

    @Before fun launch() {
        monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        combatMonitor = instrumentation.addMonitor(CombatActivity::class.java.name, null, false)
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
        instrumentation.removeMonitor(combatMonitor)
    }

    @Test fun normalAndStCompositionShowCurrentMenu() {
        capture("menu-default-pixel7a.png")
        instrumentation.runOnMainSync {
            val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
            val attributes = EditorInfo()
            val connection = editor.onCreateInputConnection(attributes)!!
            assertTrue(attributes.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0)
            connection.setComposingText("s", 1)
            connection.setComposingText("st", 1)
            assertEquals(2, highlightedLetters(R.id.menuStart))
            assertEquals(0, highlightedLetters(R.id.menuKeyboardTest))
            assertEquals(0, highlightedLetters(R.id.menuComingSoon))
            assertEquals(0, highlightedLetters(R.id.menuQuit))
        }
        assertEquals(0, monitor.hits)
        capture("menu-st-pixel7a.png")
    }

    @Test fun activeTypedCommandsNavigateAndIdentifyTheirOrigin() {
        for (command in listOf("start", "keyboard test")) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                editor.onCreateInputConnection(EditorInfo())!!.setComposingText(command, 1)
            }
            assertDestination(command)
            returnToMenu()
        }
    }

    @Test fun tapTypingShowsProgressAndIgnoresDuplicateSelection() {
        instrumentation.runOnMainSync { title.findViewById<TextView>(R.id.menuKeyboardTest).performClick() }
        waitUntil { highlightedLetters(R.id.menuKeyboardTest) in 1..3 }
        instrumentation.runOnMainSync {
            assertEquals(0, monitor.hits)
            title.findViewById<TextView>(R.id.menuStart).performClick()
            title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText("start", 1)
        }
        assertDestination("keyboard test")
        assertEquals(1, monitor.hits)
    }

    @Test fun tapContinuesTheMatchingPrefixAndBackReturnsToAnEmptyMenu() {
        instrumentation.runOnMainSync {
            val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
            editor.onCreateInputConnection(EditorInfo())!!.setComposingText("st", 1)
            title.findViewById<TextView>(R.id.menuStart).performClick()
        }
        assertDestination("start")
        returnToMenu()
        instrumentation.runOnMainSync {
            assertEquals("", title.findViewById<AppCompatEditText>(R.id.menuInputHost).text.toString())
            assertEquals(0, highlightedLetters(R.id.menuStart))
            assertEquals(1f, title.findViewById<TextView>(R.id.menuStart).alpha)
            assertEquals(0f, title.findViewById<MenuBackdropView>(R.id.menuBackdrop).transitionProgress)
        }
    }

    @Test fun invalidInputResetsAndCanBeRetried() {
        instrumentation.runOnMainSync {
            val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
            editor.onCreateInputConnection(EditorInfo())!!.commitText("stx", 1)
            assertEquals("", editor.text.toString())
            assertEquals(0, highlightedLetters(R.id.menuStart))
            editor.onCreateInputConnection(EditorInfo())!!.commitText("KEYBOARD TEST", 1)
        }
        assertDestination("keyboard test")
    }

    @Test fun typoThenKeyboardTestCompletesWithoutLosingTheRestartingK() {
        for (composing in listOf(false, true)) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                val source = "keybokeyboard test"
                for (index in source.indices) {
                    if (composing) connection.setComposingText(source.take(index + 1), 1)
                    else connection.commitText(source[index].toString(), 1)
                    if (index == 5) assertEquals(1, highlightedLetters(R.id.menuKeyboardTest))
                }
            }
            assertDestination("keyboard test")
            returnToMenu()
        }
    }

    @Test fun repeatedKStaysHighlightedForEveryCommittedAndComposingUpdate() {
        for (composing in listOf(false, true)) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                repeat(5) { index ->
                    if (composing) connection.setComposingText("k".repeat(index + 1), 1)
                    else connection.commitText("k", 1)
                    assertEquals("k must stay highlighted after update $index", 1,
                        highlightedLetters(R.id.menuKeyboardTest))
                }
                if (composing) connection.setComposingText("kkkkkkeyboard test", 1)
                else connection.commitText("eyboard test", 1)
            }
            assertDestination("keyboard test")
            returnToMenu()
        }
    }

    @Test fun activeTapsNavigateToTheirOwnDestination() {
        for ((id, command) in listOf(R.id.menuStart to "start", R.id.menuKeyboardTest to "keyboard test")) {
            instrumentation.runOnMainSync { title.findViewById<TextView>(id).performClick() }
            assertDestination(command)
            returnToMenu()
        }
    }

    @Test fun keyboardTestAcceptsSeparateImeAndHardwareSpaceEvents() {
        for (hardware in listOf(false, true)) {
            instrumentation.runOnMainSync {
                val editor = title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                connection.setComposingText("keyboard", 1)
                if (hardware) {
                    connection.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_SPACE))
                    connection.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_SPACE))
                } else connection.commitText(" ", 1)
                assertEquals(9, highlightedLetters(R.id.menuKeyboardTest))
                connection.commitText("test", 1)
            }
            assertDestination("keyboard test")
            returnToMenu()
        }
    }

    @Test fun comingSoonCannotBeTappedOrTypedAndLabelsFit() {
        instrumentation.runOnMainSync {
            val soon = title.findViewById<TextView>(R.id.menuComingSoon)
            assertEquals("> coming soon...", soon.text.toString())
            assertFalse(soon.isEnabled)
            assertFalse(soon.isClickable)
            assertFalse(soon.performClick())
            title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText("coming soon...", 1)
            assertEquals(0, highlightedLetters(R.id.menuComingSoon))
            for (id in listOf(R.id.menuStart, R.id.menuComingSoon, R.id.menuKeyboardTest, R.id.menuQuit)) {
                val row = title.findViewById<TextView>(id)
                assertTrue(row.paint.measureText(row.text.toString()) <= row.width + 1)
                assertEquals(1, row.lineCount)
            }
        }
        SystemClock.sleep(750)
        assertEquals(0, monitor.hits)
        assertEquals(0, combatMonitor.hits)
        capture("menu-current-disabled.png")
    }

    @Test fun typedQuitClosesTheGameTask() = checkQuit(tap = false)
    @Test fun tappedQuitClosesTheGameTask() = checkQuit(tap = true)

    private fun checkQuit(tap: Boolean) {
        val taskId = title.taskId
        instrumentation.runOnMainSync {
            if (tap) title.findViewById<TextView>(R.id.menuQuit).performClick()
            else title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText("quit", 1)
        }
        waitUntil { title.isDestroyed }
        val manager = instrumentation.targetContext.getSystemService(android.app.ActivityManager::class.java)
        assertTrue(manager.appTasks.none { it.taskInfo?.taskId == taskId })
        assertEquals(0, monitor.hits)
        assertEquals(0, combatMonitor.hits)
    }

    @Test fun completionAnimatesScaleAndOpacityBeforeNavigation() {
        var centreX = 0f
        var centreY = 0f
        instrumentation.runOnMainSync {
            title.findViewById<AppCompatEditText>(R.id.menuInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText("start", 1)
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
        assertDestination("start")
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
        destination = instrumentation.waitForMonitorWithTimeout(if (command == "start") combatMonitor else monitor, 5000)
        assertNotNull("$command should navigate after its animation", destination)
        instrumentation.runOnMainSync {
            if (command == "start") {
                assertTrue(destination is CombatActivity)
                assertNotNull(destination!!.findViewById<CombatView>(R.id.combatArena))
            } else {
                assertEquals(command, destination!!.intent.getStringExtra(MainActivity.EXTRA_MENU_COMMAND))
                assertEquals("$command → 入力テスト", destination!!.findViewById<TextView>(R.id.inputTitle).text.toString())
            }
        }
    }

    private fun returnToMenu() {
        instrumentation.runOnMainSync { destination!!.finish() }
        destination = null
        waitUntil { title.hasWindowFocus() && title.findViewById<AppCompatEditText>(R.id.menuInputHost).hasFocus() }
        // waitForMonitorWithTimeout consumes the monitor; register a fresh one for the next launch.
        instrumentation.removeMonitor(monitor)
        instrumentation.removeMonitor(combatMonitor)
        monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        combatMonitor = instrumentation.addMonitor(CombatActivity::class.java.name, null, false)
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
