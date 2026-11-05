package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled real Activity frames, not evidence of human typing or a naturally obtained loadout. */
@RunWith(AndroidJUnit4::class)
class OscillatorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun denseWaveBendsDuringRetargetAndOnlyRepeaterHasAnImpactRing() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        try {
            val deadline = SystemClock.uptimeMillis() + 7000
            while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
            assertTrue(activity.hasWindowFocus())
            SystemClock.sleep(800)
            lateinit var arena: CombatView
            lateinit var world: CombatWorld
            instrumentation.runOnMainSync {
                arena = activity.findViewById(R.id.combatArena)
                arena.stop()
                world = arena.session.world
                assertTrue(world.acquireWeapon(WeaponId.OSCILLATOR))
                repeat(100) { arena.session.update(1f / 120f) }
                assertTrue(world.projectiles.count { it.source == WeaponId.OSCILLATOR } > 25)
            }
            capture(activity, "wave-up")
            instrumentation.runOnMainSync {
                val target = world.spawnEnemy(world.player.position + Vec2(130f, 0f), EnemyDefinition(maxHp = 100000, speed = 0f))
                world.secondaryWeapons.single().onWord(world,
                    CompletedCombatWord(WordDefinition("wave-target", "a", languageTag = "en"), target.id, target.position))
                repeat(20) { arena.session.update(1f / 120f) }
            }
            capture(activity, "wave-turn-early")
            instrumentation.runOnMainSync { repeat(28) { arena.session.update(1f / 120f) } }
            capture(activity, "wave-turn-late")
            instrumentation.runOnMainSync {
                repeat(70) { arena.session.update(1f / 120f) }
                assertTrue(world.dps(WeaponId.OSCILLATOR) > 0f)
                assertTrue(world.hitFeedback.none { it.showRing })
            }
            capture(activity, "wave-contacts")
            instrumentation.runOnMainSync {
                val target = world.spawnEnemy(world.player.position + Vec2(-65f, 40f), EnemyDefinition(maxHp = 100000, speed = 0f))
                world.launch(Projectile(world.player.position, (target.position - world.player.position).normalized() * 520f))
                repeat(120) {
                    if (world.hitFeedback.none { it.showRing }) arena.session.update(1f / 120f)
                }
                assertTrue(world.hitFeedback.any { it.showRing && it.ringStrokeWidth > 0f })
            }
            capture(activity, "repeater-grey-ring")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun capture(activity: CombatActivity, name: String) {
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            arena.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            arena.invalidate()
        }
        assertTrue("New Canvas frame must reach the display", committed.await(3, TimeUnit.SECONDS))
        SystemClock.sleep(250) // Wait for the committed surface to reach screenshot capture.
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        var bounds = IntArray(4)
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            val location = IntArray(2); arena.getLocationOnScreen(location)
            bounds = intArrayOf(location[0], location[1], arena.width, arena.height)
        }
        val app = Bitmap.createBitmap(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
        val directory = File(activity.getExternalFilesDir(null), "verification/oscillator").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { app.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.recycle(); bitmap.recycle()
    }
}
