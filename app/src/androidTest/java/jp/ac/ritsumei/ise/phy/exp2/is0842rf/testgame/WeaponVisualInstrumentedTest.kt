package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Activity/Canvas rendering with controlled combat setup, not human input evidence. */
@RunWith(AndroidJUnit4::class)
class WeaponVisualInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun allElevenCombatAppearancesRenderAndSpectreMoves() {
        for (id in WeaponId.entries.filter { it != WeaponId.REPEATER }) {
            val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
            try {
                val deadline = SystemClock.uptimeMillis() + 7000
                while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
                assertTrue(activity.hasWindowFocus())
                SystemClock.sleep(1100)
                instrumentation.runOnMainSync {
                    val arena = activity.findViewById<CombatView>(R.id.combatArena)
                    arena.stop()
                    val world = arena.session.world
                    assertTrue(world.acquireWeapon(id))
                    val offset = if (id == WeaponId.GUARDIAN || id == WeaponId.SENTRY) Vec2(58f, -30f) else Vec2(100f, -65f)
                    val target = world.spawnEnemy(world.player.position + offset, EnemyDefinition(maxHp = 100000, speed = 0f))
                    world.spawnEnemy(world.player.position + Vec2(-90f, -50f), EnemyDefinition(maxHp = 100000, speed = 0f))
                    val word = when (id) {
                        WeaponId.SPECTRE -> "aeo"; WeaponId.HUNTER -> "iou"; WeaponId.TESLA_MINE -> "boat"
                        WeaponId.MINEFIELD -> "fish"; WeaponId.BLADE_TRAP -> "kite"; WeaponId.ORBITER -> "north"
                        else -> "attack"
                    }
                    world.completeWord(WordDefinition("visual-trigger", word, languageTag = "en"), target.id)
                    val moving = world.weaponActors.firstOrNull()
                    var travelled = 0f
                    val steps = when (id) { WeaponId.CHOPPER -> 48; WeaponId.OSCILLATOR -> 72; else -> 192 }
                    repeat(steps) {
                        val before = moving?.position
                        arena.session.update(1f / 120f)
                        if (before != null) travelled += (moving.position - before).length()
                    }
                    if (id == WeaponId.HUNTER) repeat(80) {
                        if (world.weaponActors.filterIsInstance<HunterActor>().none { it.beams.isNotEmpty() }) arena.session.update(1f / 120f)
                    }
                    if (id == WeaponId.SENTRY) {
                        repeat(100) { if (world.projectiles.none { it.source == id }) arena.session.update(1f / 120f) }
                        repeat(4) { arena.session.update(1f / 120f) }
                    }
                    if (id == WeaponId.METEOR) {
                        repeat(120) { if (!world.hasPickup(PickupKind.METEOR)) arena.session.update(1f / 120f) }
                        val marker = world.pickups.first { it.kind == PickupKind.METEOR }
                        marker.position = world.player.position + Vec2(85f, -50f)
                        world.activatePickup(marker.id)
                        repeat(12) { arena.session.update(1f / 120f) }
                    }
                    if (id == WeaponId.GUARDIAN) world.spawnPickup(PickupKind.FUEL, world.player.position + Vec2(-55f, 55f))
                    if (id == WeaponId.SPECTRE) {
                        val drone = world.weaponActors.first { it.source == id }
                        assertTrue(travelled > 60f)
                        assertTrue(drone.trail.distinct().size > 8)
                    }
                    arena.invalidate()
                }
                capture(activity, "weapon-" + id.name.lowercase())
                if (id == WeaponId.SPECTRE) {
                    instrumentation.runOnMainSync {
                        val arena = activity.findViewById<CombatView>(R.id.combatArena)
                        val world = arena.session.world
                        val before = world.weaponActors.first { it.source == id }.position
                        repeat(60) { arena.session.update(1f / 120f) }
                        assertTrue((world.weaponActors.first { it.source == id }.position - before).length() > 10f)
                        assertTrue(world.dps(id) > 0f)
                        arena.invalidate()
                    }
                    capture(activity, "weapon-spectre-after")
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    private fun capture(activity: CombatActivity, name: String) {
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            if (Build.VERSION.SDK_INT >= 29) arena.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            else arena.postOnAnimation { committed.countDown() }
            arena.invalidate()
        }
        assertTrue("Updated combat frame must reach the screen", committed.await(3, TimeUnit.SECONDS))
        SystemClock.sleep(250)
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        var bounds = IntArray(4)
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            val location = IntArray(2); arena.getLocationOnScreen(location)
            bounds = intArrayOf(location[0], location[1], arena.width, arena.height)
        }
        val app = Bitmap.createBitmap(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
        val directory = File(activity.getExternalFilesDir(null), "verification/weapon-visuals").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { app.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.recycle(); bitmap.recycle()
    }
}
