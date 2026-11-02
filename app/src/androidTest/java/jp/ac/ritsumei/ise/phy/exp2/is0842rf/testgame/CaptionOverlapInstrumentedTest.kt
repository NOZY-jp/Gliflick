package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Activity/CombatView screenshots with controlled enemies; no human IME claims. */
@RunWith(AndroidJUnit4::class)
class CaptionOverlapInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun overlappingCaptionsRemainReadableWithHpAndTypingPriority() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        try {
            SystemClock.sleep(900)
            lateinit var arena: CombatView
            lateinit var first: Enemy
            lateinit var second: Enemy
            instrumentation.runOnMainSync {
                val original = activity.findViewById<CombatView>(R.id.combatArena)
                original.stop()
                val parent = original.parent as ViewGroup
                val index = parent.indexOfChild(original)
                val params = original.layoutParams
                parent.removeView(original)
                arena = CombatView(activity, listOf(WordDefinition("air", "air", "Air", "en"),
                    WordDefinition("bee", "bee", "Bee", "en"), WordDefinition("cup", "cup", "Cup", "en")))
                arena.id = R.id.combatArena
                parent.addView(arena, index, params)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val w = arena.session.world
                val at = Vec2(w.width * 0.48f, w.height * 0.3f)
                first = w.spawnEnemy(at, EnemyDefinition(maxHp = 100, speed = 0f))
                second = w.spawnEnemy(at + Vec2(18f, 6f), EnemyDefinition(maxHp = 1000, speed = 0f, tier = 3))
                first.takeDamage(90) // 90% loss beats 600 raw damage / 60% loss on the higher tier.
                second.takeDamage(600)
                arena.session.update(0f)
                arena.session.captions.forEach { it.age = 1f }
                assertSame(first, arena.session.captions.first { it.enemyId == first.id }.enemy)
                arena.invalidate()
            }
            capture(activity, "caption-priority-health.png")
            instrumentation.runOnMainSync {
                val word = arena.session.captions.first { it.enemyId == second.id }.state.inputText
                arena.session.onInput(listOf(InputToken.Stroke(word.take(1))), false)
                assertTrue(arena.session.captions.first { it.enemyId == second.id }.state.matchedInputCharacters > 0)
                arena.invalidate()
            }
            capture(activity, "caption-priority-input.png")
            instrumentation.runOnMainSync {
                second.position += Vec2(90f, 0f)
                arena.session.update(0f)
                arena.invalidate()
            }
            capture(activity, "caption-priority-separated.png")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun capture(activity: CombatActivity, name: String) {
        SystemClock.sleep(250)
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(image)
        val dir = File(activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(dir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
