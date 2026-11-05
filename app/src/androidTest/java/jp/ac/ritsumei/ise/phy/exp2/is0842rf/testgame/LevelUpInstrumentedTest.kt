package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Android UI/IME transport tests; injected notifications are not human keyboard evidence. */
@RunWith(AndroidJUnit4::class)
class LevelUpInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun launch(): CombatActivity =
        (instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity).also {
            instrumentation.runOnMainSync { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            waitUntil { it.hasWindowFocus() && it.findViewById<CombatView>(R.id.combatArena).height > 0 }
            SystemClock.sleep(550)
        }
    private fun reward(activity: CombatActivity) {
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            arena.session.world.grantExperience(arena.session.world.nextLevelExperience)
            activity.findViewById<GameImeEditText>(R.id.combatInputHost)
                .onCreateInputConnection(EditorInfo())!!.commitText(" ", 1)
        }
        waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection != null }
        SystemClock.sleep(220)
    }

    @Test fun phoneLevelUpIgnoresCardTapAndTypingAppliesOneReward() {
        val activity = launch()
        try {
            reward(activity)
            var pausedAt = 0.0
            var offer: UpgradeOffer? = null
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                pausedAt = arena.session.world.elapsedSeconds
                offer = arena.session.upgradeSelection!!.offers[0]
            }
            capture(activity, "levelup-phone.png")
            SystemClock.sleep(350)
            instrumentation.runOnMainSync {
                assertEquals(pausedAt, activity.findViewById<CombatView>(R.id.combatArena).session.world.elapsedSeconds, 0.0001)
            }
            tapBox(activity, choice = 0)
            SystemClock.sleep(450)
            instrumentation.runOnMainSync {
                val selection = activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection!!
                assertNull(selection.selectedIndex)
                assertTrue(selection.states.all { it.matchedInputCharacters == 0 })
            }
            typeChoice(activity)
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection == null }
            instrumentation.runOnMainSync {
                val world = activity.findViewById<CombatView>(R.id.combatArena).session.world
                assertEquals(0, world.pendingLevelUps)
                if (offer!!.kind == OfferKind.ACQUIRE) assertTrue(world.secondaryWeapons.any { it.id == offer!!.weapon })
                else assertEquals(2, (world.mainWeapon as Repeater).upgrades.level)
            }
            capture(activity, "levelup-applied.png")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun rerollAndRomanCompositionSelectTheShownWord() {
        val activity = launch()
        try {
            reward(activity)
            tapBox(activity, reroll = true)
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.rerollsRemaining == 2 }
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                val selection = arena.session.upgradeSelection!!
                val word = selection.words[1]
                val roman = TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)
                val connection = activity.findViewById<GameImeEditText>(R.id.combatInputHost)
                    .onCreateInputConnection(EditorInfo())!!
                connection.setComposingText(roman.take(2), 1)
                assertTrue(selection.states[1].matchedInputCharacters > 0)
            }
            capture(activity, "levelup-input.png")
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                val word = arena.session.upgradeSelection!!.words[1]
                val roman = TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)
                activity.findViewById<GameImeEditText>(R.id.combatInputHost)
                    .onCreateInputConnection(EditorInfo())!!.setComposingText(roman, 1)
            }
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection == null }
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                assertEquals(0, (arena.session.world.mainWeapon as Repeater).pendingShots)
                assertEquals("", activity.findViewById<GameImeEditText>(R.id.combatInputHost).text.toString())
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun acquiredArsenalRendersActorsAndFuelPickupsOnPhone() {
        val activity = launch()
        try {
            instrumentation.runOnMainSync {
                val arena = activity.findViewById<CombatView>(R.id.combatArena)
                arena.stop()
                val world = arena.session.world
                listOf(WeaponId.SENTRY, WeaponId.HUNTER, WeaponId.GUARDIAN, WeaponId.ORBITER).forEach { world.acquireWeapon(it) }
                val enemy = world.spawnEnemy(world.player.position + Vec2(50f, -30f),
                    EnemyDefinition(maxHp = 10000, speed = 0f))
                world.spawnEnemy(world.player.position + Vec2(-75f, 60f), EnemyDefinition(maxHp = 10000, speed = 0f))
                world.completeWord(WordDefinition("north", "north", languageTag = "en"), enemy.id)
                assertTrue("Expected all four actor types immediately: ${world.weaponActors.map { it.source }}",world.weaponActors.size >= 4)
                world.spawnPickup(PickupKind.FUEL, world.player.position + Vec2(-90f, -75f))
                repeat(84) { arena.session.update(1f/120f) }
            }
            SystemClock.sleep(700)
            capture(activity, "arsenal-phone.png")
            instrumentation.runOnMainSync {
                val world = activity.findViewById<CombatView>(R.id.combatArena).session.world
                assertEquals(4, world.secondaryWeapons.size)
                assertTrue("Expected all four actor types: ${world.weaponActors.map { it.source }}",world.weaponActors.size >= 4)
                assertTrue(world.dps(WeaponId.GUARDIAN) > 0f)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun evolutionCardsShowRealBranchEffectsAndApplyOneBranch() {
        val activity = launch()
        try {
            instrumentation.runOnMainSync {
                val world = activity.findViewById<CombatView>(R.id.combatArena).session.world
                (world.mainWeapon as Repeater).upgrades.creditKills(50)
                activity.findViewById<CombatView>(R.id.combatArena).handleSpaceKey()
            }
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection?.evolution == true }
            capture(activity, "evolution-phone.png")
            tapBox(activity, choice = 0)
            SystemClock.sleep(450)
            instrumentation.runOnMainSync {
                val selection = activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection!!
                assertNull(selection.selectedIndex)
                assertTrue(selection.states.all { it.matchedInputCharacters == 0 })
            }
            typeChoice(activity)
            waitUntil { activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection == null }
            instrumentation.runOnMainSync {
                val state = (activity.findViewById<CombatView>(R.id.combatArena).session.world.mainWeapon as Repeater).upgrades
                assertEquals(1, state.evolutionsChosen)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun typeChoice(activity: CombatActivity) {
        instrumentation.runOnMainSync {
            val word = activity.findViewById<CombatView>(R.id.combatArena).session.upgradeSelection!!.words[0]
            val roman = TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)
            activity.findViewById<GameImeEditText>(R.id.combatInputHost)
                .onCreateInputConnection(EditorInfo())!!.setComposingText(roman, 1)
        }
    }

    private fun tapBox(activity: CombatActivity, choice: Int = 0, reroll: Boolean = false) {
        var x = 0f; var y = 0f
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            val scale = arena.width / arena.session.world.width
            val layout = UpgradeLayout(arena.session.world.width, arena.session.world.height,
                arena.session.upgradeSelection!!.offers.size)
            val box = if (reroll) layout.reroll else layout.cards[choice]
            val location = IntArray(2); arena.getLocationOnScreen(location)
            x = location[0] + box.centerX * scale
            y = location[1] + (box.top + box.bottom) / 2f * scale
        }
        val time = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun capture(activity: CombatActivity, name: String) {
        SystemClock.sleep(250)
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val directory = File(activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var bounds = IntArray(4)
        instrumentation.runOnMainSync {
            val arena = activity.findViewById<CombatView>(R.id.combatArena)
            val location = IntArray(2); arena.getLocationOnScreen(location)
            bounds = intArrayOf(location[0], location[1], arena.width, arena.height)
        }
        val app = Bitmap.createBitmap(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
        File(directory, name.removeSuffix(".png") + "-app.png").outputStream().use {
            app.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        app.recycle()
        bitmap.recycle()
    }
    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 7000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = check() }
            if (done) return
            SystemClock.sleep(20)
        }
        fail("Timed out waiting for level-up UI")
    }
}
