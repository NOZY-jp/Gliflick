package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.Repeater
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.EnemyDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.Vec2
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic InputConnection integration; this is not evidence of new human Simeji keystrokes. */
@RunWith(AndroidJUnit4::class)
class CombatInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun keyboardCompletionFiresThreeShotsAndUpdatesSharedXpHud() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        try {
            seedEnemy(activity)
            waitUntil { activity.hasWindowFocus() && activity.findViewById<CombatView>(R.id.combatArena).session.captions.isNotEmpty() }
            instrumentation.runOnMainSync {
                capture(activity, "combat-hud-final.png")
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                val caption = arena.session.captions.first()
                val word = caption.word
                val spelling = TypingLanguages.forTag(word.languageTag).defaultRoman(splitTextCharacters(word.text))
                val editor = activity.findViewById<GameImeEditText>(R.id.combatInputHost)
                val connection = editor.onCreateInputConnection(EditorInfo())!!
                spelling.forEach { connection.commitText(it.toString(), 1) }
                assertEquals(3, (arena.session.world.mainWeapon as Repeater).pendingShots)
                assertNotEquals(word.id, arena.session.captions.first { it.enemyId == caption.enemyId }.word.id)
                assertTrue(arena.session.completions.any { it.state.inputText == spelling })
            }
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.world.projectiles.size == 3 }
            instrumentation.runOnMainSync { capture(activity, "combat-three-shots-final.png") }
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.world.kills >= 1 }
            SystemClock.sleep(180) // Capture the visible middle of the square-particle burst as well as XP.
            instrumentation.runOnMainSync {
                val world = activity.findViewById<CombatView>(R.id.combatArena).session.world
                assertEquals(10, world.experience)
                assertEquals(0.2f, world.experienceFraction, 0.001f)
                assertEquals(300, world.player.hp)
                capture(activity, "combat-xp-final.png")
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = check() }
            if (done) return
            SystemClock.sleep(10)
        }
        fail("Timed out waiting for combat state")
    }

    @Test fun pausedFrameLoopDoesNotAdvanceOrCatchUpOnResume() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        try {
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.world.elapsedSeconds > 0.1 }
            var pausedAt = 0.0
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                arena.stop()
                pausedAt = arena.session.world.elapsedSeconds
            }
            SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                assertEquals(pausedAt, arena.session.world.elapsedSeconds, 0.0001)
                arena.start()
            }
            SystemClock.sleep(80)
            instrumentation.runOnMainSync {
                val elapsed = activity.findViewById<CombatView>(R.id.combatArena).session.world.elapsedSeconds - pausedAt
                assertTrue(elapsed in 0.0..0.15)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun retryStartsACleanWorldAndClearsTheImeComposition() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        try {
            seedEnemy(activity)
            waitUntil { activity.hasWindowFocus() && activity.findViewById<CombatView>(R.id.combatArena).session.captions.isNotEmpty() }
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                val editor = activity.findViewById<GameImeEditText>(R.id.combatInputHost)
                editor.onCreateInputConnection(EditorInfo())!!.setComposingText("k", 1)
                val old = arena.session
                old.world.player.takeDamage(300)
                assertTrue(old.world.isGameOver)
                arena.performClick()
                assertNotSame(old, arena.session)
                assertEquals(300, arena.session.world.player.hp)
                assertEquals(0.0, arena.session.world.elapsedSeconds, 0.0)
                assertEquals("", editor.text.toString())
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun seedEnemy(activity: CombatActivity) {
        waitUntil { activity.hasWindowFocus() && activity.findViewById<CombatView>(R.id.combatArena).session.world.width > 0f }
        instrumentation.runOnMainSync {
            val world = activity.findViewById<CombatView>(R.id.combatArena).session.world
            world.spawnEnemy(Vec2(world.player.position.x, -12f), EnemyDefinition(speed = 0f))
        }
    }

    private fun capture(activity: CombatActivity, name: String) {
        assertTrue(activity.hasWindowFocus())
        val decor = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val directory = File(activity.filesDir, "verification").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
