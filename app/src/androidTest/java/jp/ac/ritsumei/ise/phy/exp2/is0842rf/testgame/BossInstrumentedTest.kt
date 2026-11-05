package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Canvas frames using controlled encounters, not four/twelve minutes of human play. */
@RunWith(AndroidJUnit4::class)
class BossInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun tokens(s:String)=s.map { InputToken.Stroke(it.toString()) }
    private fun launch():CombatActivity {
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        instrumentation.runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val deadline=SystemClock.uptimeMillis()+15000
        while(!activity.hasWindowFocus() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(20)
        assertTrue(activity.hasWindowFocus());SystemClock.sleep(700)
        instrumentation.runOnMainSync { activity.findViewById<CombatView>(R.id.combatArena).stop() }
        return activity
    }
    @Test fun timedIntroductionAndGeminiTendrilReachTheActualScreen() {
        val a=launch()
        try {
            lateinit var arena:CombatView
            instrumentation.runOnMainSync {
                arena=a.findViewById(R.id.combatArena)
                arena.session.world.update(240f);arena.session.update(0f)
                assertEquals(BossKind.GEMINI,arena.session.world.bosses.introduction)
                val time=arena.session.world.elapsedSeconds;arena.session.update(5f);assertEquals(time,arena.session.world.elapsedSeconds,0.0)
            }
            capture(a,"gemini-introduction")
            instrumentation.runOnMainSync {
                assertTrue(arena.session.onInput(tokens("go"),false))
                val world=arena.session.world
                world.damageEnemy(world.bosses.enemies[0].id,500);world.damageEnemy(world.bosses.enemies[1].id,800)
                arena.session.update(0.8f)
            }
            capture(a,"gemini-pair")
            instrumentation.runOnMainSync {
                repeat(1450) { arena.session.update(1f/120f) }
                val word=checkNotNull(arena.session.tendrilState).definition
                val input=TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)
                arena.session.onInput(tokens(input.repeat(10)),false)
                assertEquals(10,arena.session.world.bosses.tendril!!.strokes)
            }
            capture(a,"gemini-tendril")
        } finally { instrumentation.runOnMainSync { a.finish() } }
    }
    @Test fun cypherAuraAndGeneratedKanaRenderTogether() {
        val a=launch()
        try {
            instrumentation.runOnMainSync {
                val arena=a.findViewById<CombatView>(R.id.combatArena);val world=arena.session.world
                world.bosses.begin(world,BossKind.CYPHER)
                world.spawnEnemy(world.bosses.enemies.single().position+Vec2(65f,40f),EnemyDefinition(maxHp=10000,speed=0f))
                world.spawnEnemy(world.player.position+Vec2(-100f,90f),EnemyDefinition(maxHp=10000,speed=0f))
                repeat(50) { arena.session.update(1f/120f) }
                assertTrue(arena.session.captions.any { it.cyphered })
            }
            capture(a,"cypher-aura")
        } finally { instrumentation.runOnMainSync { a.finish() } }
    }
    @Test fun poetPassageAndSquaredCaptionsRenderOnThePhone() {
        val a=launch()
        try {
            instrumentation.runOnMainSync {
                val arena=a.findViewById<CombatView>(R.id.combatArena);val world=arena.session.world
                world.bosses.begin(world,BossKind.POET)
                val enemy=world.spawnEnemy(world.player.position+Vec2(90f,65f),EnemyDefinition(maxHp=100000,speed=0f))
                repeat(3600) { if(!enemy.wordSealed) arena.session.update(1f/120f) }
                assertTrue("Poet orb must seal: viewport=${world.width}x${world.height}, enemy=${enemy.position}, time=${world.elapsedSeconds}, boss=${world.bosses.activeKind}, orbs=${world.bosses.orbs.map { it.position }}",enemy.wordSealed)
                repeat(3) {
                    val word=checkNotNull(arena.session.poemState).definition
                    arena.session.onInput(tokens(TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)),false)
                }
                assertEquals(3,world.bosses.poemIndex)
            }
            capture(a,"poet-passage-and-seal")
        } finally { instrumentation.runOnMainSync { a.finish() } }
    }
    @Test fun sparklingDiceCanBeCollectedThroughTheRealInputConnection() {
        val a=launch()
        try {
            lateinit var arena:CombatView
            instrumentation.runOnMainSync {
                arena=a.findViewById(R.id.combatArena)
                arena.session.world.spawnPickup(PickupKind.REROLL,arena.session.world.player.position+Vec2(90f,-30f))
                repeat(120) { arena.session.update(1f/120f) }
            }
            capture(a,"reroll-sparkling-dice")
            instrumentation.runOnMainSync {
                val word=arena.session.pickupCaptions.single().word
                val input=TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)
                val connection=a.findViewById<GameImeEditText>(R.id.combatInputHost).onCreateInputConnection(EditorInfo())!!
                input.forEach { connection.commitText(it.toString(),1) }
                arena.session.update(0f)
                assertEquals(4,arena.session.rerollsRemaining)
                assertTrue(arena.session.pickupCaptions.isEmpty())
            }
            capture(a,"reroll-collected")
        } finally { instrumentation.runOnMainSync { a.finish() } }
    }
    private fun capture(a:CombatActivity,name:String) {
        val committed=CountDownLatch(1)
        instrumentation.runOnMainSync {
            val arena=a.findViewById<CombatView>(R.id.combatArena)
            arena.viewTreeObserver.registerFrameCommitCallback { committed.countDown() };arena.invalidate()
        }
        assertTrue(committed.await(3,TimeUnit.SECONDS));SystemClock.sleep(250);instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()!!
        var bounds=IntArray(4)
        instrumentation.runOnMainSync {
            val arena=a.findViewById<CombatView>(R.id.combatArena);val at=IntArray(2);arena.getLocationOnScreen(at)
            bounds=intArrayOf(at[0],at[1],arena.width,arena.height)
        }
        val app=Bitmap.createBitmap(bitmap,bounds[0],bounds[1],bounds[2],bounds[3])
        val directory=File(a.getExternalFilesDir(null),"verification/bosses").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { app.compress(Bitmap.CompressFormat.PNG,100,it) }
        app.recycle();bitmap.recycle()
    }
}
