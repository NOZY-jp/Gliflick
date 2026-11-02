package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Activity/Canvas with controlled combat states. Input below is synthetic, not human evidence. */
@RunWith(AndroidJUnit4::class)
class EnemyKindsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun fixture(body: (CombatActivity, CombatView) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CombatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as CombatActivity
        try {
            SystemClock.sleep(850)
            lateinit var arena: CombatView
            instrumentation.runOnMainSync {
                val original = activity.findViewById<CombatView>(R.id.combatArena)
                original.stop()
                val parent = original.parent as ViewGroup
                val index = parent.indexOfChild(original); val params = original.layoutParams
                parent.removeView(original)
                arena = CombatView(activity, listOf(WordDefinition("kaki","かき","柿"),
                    WordDefinition("kasa","かさ","傘"),WordDefinition("hana","はな","花"),
                    WordDefinition("yama","やま","山"),WordDefinition("umi","うみ","海"),
                    WordDefinition("mori","もり","森"),WordDefinition("hosi","ほし","星"),
                    WordDefinition("neko","ねこ","猫"),WordDefinition("inu","いぬ","犬")))
                arena.id = R.id.combatArena; parent.addView(arena,index,params)
            }
            instrumentation.waitForIdleSync()
            body(activity,arena)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun sync(arena: CombatView) {
        arena.session.update(0f)
        arena.session.captions.forEach { it.age = 1f }
        arena.invalidate()
    }
    @Test fun shieldAndShooterFiveTiersAndThreeSplitterFormsRender() {
        val profiles = listOf(EnemyKind.SHIELD_DRONE to (1..5).toList(),
            EnemyKind.SHOOTER to (1..5).toList(),EnemyKind.SPLITTER to (1..3).toList())
        for ((kind,tiers) in profiles) fixture { activity,arena ->
            instrumentation.runOnMainSync {
                val w=arena.session.world
                tiers.forEachIndexed { i,tier ->
                    val x=w.width*(if (i%2==0) .26f else .74f)
                    val y=w.height*(.22f+(i/2)*.26f)
                    val enemy=w.spawnEnemy(Vec2(x,y),EnemyCatalog.definition(kind,tier))
                    w.damageEnemy(enemy.id,35)
                    assertTrue(enemy.isAlive)
                }
                repeat(84) { arena.session.update(1f/120f) } // Let transient impact effects clear before silhouette review.
                sync(arena)
            }
            capture(activity,arena,"tiers-${kind.name.lowercase()}")
        }
    }
    @Test fun warderBarrierAndProtectionDisappearImmediatelyWithOwner() = fixture { activity,arena ->
        lateinit var owner:Enemy
        lateinit var protected:Enemy
        instrumentation.runOnMainSync {
            val w=arena.session.world
            owner=w.spawnEnemy(Vec2(w.width*.75f,w.height*.3f),EnemyCatalog.definition(EnemyKind.WARDER))
            protected=w.spawnEnemy(owner.position+Vec2(-65f,40f),EnemyDefinition())
            w.damageEnemy(protected.id,10000)
            assertEquals(80,protected.hp); assertTrue(w.isProtected(protected)); assertEquals(1,w.barriers.size)
            sync(arena)
        }
        capture(activity,arena,"warder-protected")
        instrumentation.runOnMainSync {
            arena.session.world.damageEnemy(owner.id,300)
            arena.session.update(.2f)
            assertTrue(arena.session.world.barriers.isEmpty())
            assertFalse(arena.session.world.isProtected(protected))
            arena.session.world.damageEnemy(protected.id,35)
            assertEquals(45,protected.hp);sync(arena)
        }
        capture(activity,arena,"warder-destroyed")
    }
    @Test fun splitterSurvivorsKeepHealthAndParentPartialInputWhileSeparating() = fixture { activity,arena ->
        lateinit var group:Enemy
        instrumentation.runOnMainSync {
            val w=arena.session.world
            group=w.spawnEnemy(Vec2(w.width*.5f,w.height*.28f),EnemyCatalog.definition(EnemyKind.SPLITTER))
            sync(arena)
            val caption=arena.session.captions.single()
            arena.session.onInput(listOf(InputToken.Stroke(TypingLanguages.forTag(caption.word.languageTag).defaultRoman(caption.word.characters).take(1))),false)
            w.dealDamage(group.id,100,WeaponId.REPEATER,DamageType.KINETIC,inflictStatus=false,hitPart=1)
            sync(arena)
        }
        capture(activity,arena,"splitter-before")
        instrumentation.runOnMainSync {
            val w=arena.session.world
            w.damageEnemy(group.id,350)
            repeat(84) { arena.session.update(1f/120f) }
            assertEquals(listOf(250,350,350),w.enemies.filter { it.isAlive && it.definition.kind==EnemyKind.SPLITTER_SHARD }.map { it.hp }.sorted())
            assertEquals(3,w.enemies.count { it.isAlive && it.definition.kind==EnemyKind.SPLITTER_SHARD })
            assertTrue(arena.session.captions.any { it.enemyId==group.id && it.retired })
            sync(arena)
        }
        capture(activity,arena,"splitter-after")
    }
    @Test fun shooterMissileChargeAndFlightHaveVisibleDamageableBodies() = fixture { activity,arena ->
        lateinit var missile:Enemy
        instrumentation.runOnMainSync {
            val w=arena.session.world
            val shooter=w.spawnEnemy(Vec2(w.width*.8f,w.height*.2f),EnemyCatalog.definition(EnemyKind.SHOOTER))
            missile=w.spawnEnemy(shooter.position+Vec2(-36f,35f),EnemyCatalog.definition(EnemyKind.SHOOTER_MISSILE))
            missile.parentId=shooter.id
            missile.flightDirection=(w.player.position-missile.position).normalized()
            assertEquals(0f,missile.activationProgress,0f);sync(arena)
        }
        capture(activity,arena,"missile-charging")
        instrumentation.runOnMainSync {
            val start=missile.position
            missile.advance(8f,arena.session.world.player.position)
            assertTrue((start-missile.position).length()>0f)
            arena.session.world.damageEnemy(missile.id,35)
            assertEquals(165,missile.hp)
            repeat(84) { arena.session.update(1f/120f) };sync(arena)
        }
        capture(activity,arena,"missile-flying")
    }
    private fun capture(activity:CombatActivity,arena:CombatView,name:String) {
        val frame=CountDownLatch(1)
        instrumentation.runOnMainSync {
            arena.viewTreeObserver.registerFrameCommitCallback { frame.countDown() };arena.invalidate()
        }
        assertTrue("Frame must reach actual screen",frame.await(4,TimeUnit.SECONDS))
        SystemClock.sleep(180);instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot();assertNotNull(bitmap)
        val dir=File(activity.getExternalFilesDir(null),"verification/enemy-kinds").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        instrumentation.runOnMainSync {
            val location=IntArray(2);arena.getLocationOnScreen(location)
            val unit=arena.width/arena.session.world.width
            val poses=JSONArray()
            arena.session.world.enemies.filter { it.isAlive }.forEach { enemy ->
                poses.put(JSONObject().put("kind",enemy.definition.kind.name).put("tier",enemy.definition.tier)
                    .put("x",enemy.position.x*unit+location[0]).put("y",enemy.position.y*unit+location[1])
                    .put("angle",enemy.angle).put("radius_dp",enemy.radius))
            }
            File(dir,"$name.json").writeText(JSONObject().put("unit",unit).put("enemies",poses).toString(2))
        }
        bitmap.recycle()
    }
}
