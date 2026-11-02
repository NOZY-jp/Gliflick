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
class WeaponEvolutionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun launch(): CombatActivity {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        val deadline = SystemClock.uptimeMillis() + 7000
        while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
        assertTrue(activity.hasWindowFocus()); SystemClock.sleep(650)
        instrumentation.runOnMainSync { activity.findViewById<CombatView>(R.id.combatArena).stop() }
        return activity
    }

    @Test fun allWeaponEvolutionChoicesRenderAndApplyWithoutSpendingOtherRewards() {
        for (id in WeaponId.entries) {
            val activity = launch()
            try {
                instrumentation.runOnMainSync {
                    val arena = activity.findViewById<CombatView>(R.id.combatArena)
                    val world = arena.session.world
                    if (id != WeaponId.REPEATER) assertTrue(world.acquireWeapon(id))
                    world.weaponState(id)!!.creditKills(50)
                    assertTrue(arena.session.handleSpace())
                    arena.session.update(0.2f)
                    assertTrue(arena.session.upgradeSelection!!.offers.all { it.weapon == id })
                    arena.invalidate()
                }
                capture(activity, "choices-${id.name.lowercase()}")
                instrumentation.runOnMainSync {
                    val arena = activity.findViewById<CombatView>(R.id.combatArena)
                    val time = arena.session.world.elapsedSeconds
                    arena.session.onInput(arena.session.upgradeSelection!!.states[0].inputText.map { jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken.Stroke(it.toString()) }, false)
                    repeat(300) { if (arena.session.upgradeSelection != null) arena.session.update(0.01f) }
                    assertNull(arena.session.upgradeSelection)
                    assertEquals(1, arena.session.world.weaponState(id)!!.evolutionsChosen)
                    assertEquals(0, arena.session.pendingEvolutionChoices)
                    assertEquals(time, arena.session.world.elapsedSeconds, 0.0)
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test fun evolvedCombatFormsRenderUsingDedicatedGeometry() {
        val profiles = mapOf(
            WeaponId.SPECTRE to listOf(SecondaryEvolution.SUPERCHARGED, SecondaryEvolution.SELF_DESTRUCT),
            WeaponId.SENTRY to listOf(SecondaryEvolution.DEFENSE_GRID, SecondaryEvolution.DEFENSE_GRID, SecondaryEvolution.SOLID_CORE),
            WeaponId.TESLA_MINE to listOf(SecondaryEvolution.ELECTRICAL_BURN, SecondaryEvolution.RESTRICTED_AREA),
            WeaponId.OSCILLATOR to listOf(SecondaryEvolution.TWIN_HEADED, SecondaryEvolution.BALL_LIGHTNING, SecondaryEvolution.STABILIZED_PLASMA),
            WeaponId.METEOR to listOf(SecondaryEvolution.RELENTLESS, SecondaryEvolution.ICE_RAIN),
            WeaponId.HUNTER to listOf(SecondaryEvolution.HUNTER_ELITE, SecondaryEvolution.CHAIN_LIGHTNING),
            WeaponId.CHOPPER to listOf(SecondaryEvolution.SAW_LAUNCHER, SecondaryEvolution.SAW_LAUNCHER, SecondaryEvolution.BOOMERANG),
            WeaponId.MINEFIELD to listOf(SecondaryEvolution.HUNTER_SEEKER, SecondaryEvolution.HUNTER_SEEKER, SecondaryEvolution.INFUSION),
            WeaponId.BLADE_TRAP to listOf(SecondaryEvolution.POISON_COATING, SecondaryEvolution.SAWTOOTH_BLADES),
            WeaponId.ORBITER to listOf(SecondaryEvolution.DOPPELGANGER, SecondaryEvolution.TETHER, SecondaryEvolution.STORM_RING),
            WeaponId.GUARDIAN to listOf(SecondaryEvolution.GUARDIAN_TEAM, SecondaryEvolution.GUARDIAN_TEAM, SecondaryEvolution.TOXIC_WARD)
        )
        for ((id, branches) in profiles) {
            val activity = launch()
            try {
                instrumentation.runOnMainSync {
                    val arena = activity.findViewById<CombatView>(R.id.combatArena)
                    val world = arena.session.world
                    assertTrue(world.acquireWeapon(id))
                    val weapon = world.secondaryWeapons.single()
                    weapon.state.creditKills(300)
                    branches.forEach { branch ->
                        assertTrue(world.applyUpgrade(EvolutionCatalog.offers(weapon.state).first { it.evolution == branch }))
                    }
                    val target = world.spawnEnemy(world.player.position + Vec2(80f, -45f), EnemyDefinition(maxHp = 100000, speed = 0f))
                    world.spawnEnemy(world.player.position + Vec2(120f, -20f), EnemyDefinition(maxHp = 100000, speed = 0f))
                    val glyph = when (id) {
                        WeaponId.SPECTRE -> "a"; WeaponId.HUNTER -> "i"; WeaponId.TESLA_MINE -> "boat"
                        WeaponId.MINEFIELD -> "fish"; WeaponId.BLADE_TRAP -> "kite"; WeaponId.ORBITER -> "north"
                        else -> "attack"
                    }
                    val count = when (id) { WeaponId.SPECTRE -> 20; WeaponId.HUNTER -> 12; else -> 1 }
                    repeat(count) { weapon.onWord(world, CompletedCombatWord(
                        WordDefinition("evolved", glyph, languageTag = "en"), target.id, target.position)) }
                    if (count > 1) world.weaponActors.filter { if (it is HunterActor) !it.enhanced else it is DroneActor && !it.enhanced }.forEach { it.finished = true }
                    if (id == WeaponId.CHOPPER) weapon.onMainWeaponFire(world,
                        Projectile(world.player.position, Vec2(200f, -80f)))
                    repeat(if (id == WeaponId.CHOPPER) 35 else 120) { arena.session.update(1f / 120f) }
                    if (id == WeaponId.HUNTER) repeat(100) {
                        if (world.weaponActors.filterIsInstance<HunterActor>().none { it.beams.isNotEmpty() }) arena.session.update(1f / 120f)
                    }
                    if (id == WeaponId.ORBITER) assertEquals(2, world.weaponActors.count { it.source == id })
                    arena.invalidate()
                }
                capture(activity, "evolved-${id.name.lowercase()}")
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test fun relentlessMeteorMovesAndRendersAtItsTarget() {
        val activity = launch()
        try {
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                val world = arena.session.world
                world.acquireWeapon(WeaponId.METEOR)
                val weapon = world.secondaryWeapons.single()
                weapon.state.creditKills(150)
                for (branch in listOf(SecondaryEvolution.RELENTLESS, SecondaryEvolution.ICE_RAIN))
                    assertTrue(world.applyUpgrade(EvolutionCatalog.offers(weapon.state).first { it.evolution == branch }))
                val target = world.spawnEnemy(world.player.position + Vec2(70f, -40f), EnemyDefinition(maxHp = 100000, speed = 0f))
                val field = world.weaponActors.single() as FieldActor
                field.position = target.position - Vec2(10f, 0f)
                val start = field.position
                weapon.onWord(world, CompletedCombatWord(WordDefinition("meteor", "a", languageTag = "en"), target.id, target.position))
                repeat(12) { arena.session.update(1f / 120f) }
                assertTrue(field.position.x > start.x)
                assertTrue(world.dps(WeaponId.METEOR) > 0f)
                assertEquals(DamageType.COLD, field.type)
                arena.invalidate()
            }
            capture(activity, "evolved-meteor")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
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
        val directory = File(activity.getExternalFilesDir(null), "verification/evolutions").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { app.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.recycle(); bitmap.recycle()
    }
}
