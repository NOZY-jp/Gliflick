package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render.ArsenalLayout
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RewardFeedbackTest {
    private fun session()=CombatSession(listOf(WordDefinition("w","ひかり","光")),
        CombatWorld(CombatConfig(spawningEnabled=false),Random(8)).also { it.resize(400f,500f) },Random(4))
    @Test fun mixedQueueDrainsWithoutSpaceOrCombatTimeAndResetsEachInputBoundary() {
        val s=session();val w=s.world
        w.acquireWeapon(WeaponId.HUNTER);w.weaponState(WeaponId.HUNTER)!!.creditKills(150)
        w.grantExperience(200);s.update(0f)
        assertTrue(s.handleSpace())
        val elapsed=w.elapsedSeconds
        repeat(4) { index ->
            val selection=checkNotNull(s.upgradeSelection)
            assertEquals(index<2,selection.evolution)
            val epoch=s.inputEpoch
            // Prefer damage over learning, so this fixture awards exactly its original four choices.
            val choice=selection.offers.indexOfFirst { offer -> offer.effects.none { it.stat==WeaponStat.EVOLUTION } }.coerceAtLeast(0)
            assertTrue(s.onInput(selection.states[choice].inputText.map { InputToken.Stroke(it.toString()) },false))
            s.update(0.26f)
            assertEquals(elapsed,w.elapsedSeconds,0.0)
            assertTrue(s.inputEpoch>epoch)
            s.upgradeSelection?.let { next ->
                assertTrue(next.age>=0.18f)
                assertTrue(next.states.all { it.matchedInputCharacters==0 })
            }
        }
        assertNull(s.upgradeSelection);assertEquals(0,s.pendingChoiceCount)
        s.update(0.01f);assertTrue(w.elapsedSeconds>elapsed)
    }
    @Test fun learningRewardThatUnlocksEvolutionImmediatelyContinuesIntoItsSelection() {
        var exercised=false
        for(seed in 0..100) {
            val w=CombatWorld(CombatConfig(spawningEnabled=false),Random(8)).also { it.resize(400f,500f) }
            w.weaponState(WeaponId.REPEATER)!!.creditKills(40);w.grantExperience(50)
            val s=CombatSession(listOf(WordDefinition("w","ひかり","光")),w,Random(seed))
            s.update(0f);assertTrue(s.handleSpace())
            val selection=s.upgradeSelection!!
            val index=selection.offers.indexOfFirst { offer -> offer.weapon==WeaponId.REPEATER &&
                offer.effects.any { it.stat==WeaponStat.EVOLUTION } }
            if(index<0) continue
            val time=w.elapsedSeconds
            s.onInput(selection.states[index].inputText.map { InputToken.Stroke(it.toString()) },false)
            s.update(0.26f)
            assertTrue(s.upgradeSelection!!.evolution)
            assertEquals(WeaponId.REPEATER,s.upgradeSelection!!.offers.first().weapon)
            assertEquals(time,w.elapsedSeconds,0.0)
            assertEquals(0,w.pendingLevelUps)
            assertTrue(s.rewardFeedback.any { it.weapon==WeaponId.REPEATER })
            exercised=true;break
        }
        assertTrue("A real learning offer must be exercised",exercised)
    }
    @Test fun readinessIsNotRepeatedByConsumptionOrRerollAndActualEvolutionHasFeedback() {
        val s=session();val w=s.world
        w.acquireWeapon(WeaponId.HUNTER);w.weaponState(WeaponId.HUNTER)!!.creditKills(50)
        w.grantExperience(50);s.update(0f)
        assertEquals(listOf(null,WeaponId.HUNTER),s.rewardFeedback.map { it.weapon })
        s.update(1.3f);assertTrue(s.rewardFeedback.isEmpty())
        assertTrue(s.handleSpace());s.rerollUpgrades();s.update(0f)
        assertTrue(s.rewardFeedback.isEmpty())
        val selection=checkNotNull(s.upgradeSelection)
        s.onInput(selection.states[0].inputText.map { InputToken.Stroke(it.toString()) },false);s.update(0.26f)
        assertEquals(listOf(WeaponId.HUNTER),s.rewardFeedback.map { it.weapon })
        assertNotNull(s.upgradeSelection)
    }
    @Test fun roundedBounceKeepsVelocityContinuousAndReturnsAtTheIncomingSpeed() {
        fun radius(t:Float,start:Float)=RewardFeedback(null).also { it.advance(t) }.pose(start,18f).radius
        val h=0.0001f
        for(start in listOf(300f,1200f)) {
            fun velocity(t:Float)=(radius(t+h,start)-radius(t-h,start))/(2f*h)
            val entering=velocity(0.58f);val leaving=velocity(0.70f)
            assertEquals(-entering,leaving,kotlin.math.abs(entering)*0.01f)
            for(join in listOf(0.58f,0.70f)) {
                val before=(radius(join,start)-radius(join-h,start))/h
                val after=(radius(join+h,start)-radius(join,start))/h
                assertEquals(before,after,kotlin.math.abs(entering)*0.01f)
            }
            assertEquals(0f,velocity(0.64f),1f)
            for(t in listOf(0f,0.42f,0.58f,0.61f))
                assertEquals(radius(t,start),radius(1.28f-t,start),0.001f)
        }
    }
    @Test fun ringStartsOutsideCornersReachesItsTargetThenExpandsAndDisappearsAtTheActualSlot() {
        val s=session();val w=s.world;w.acquireWeapon(WeaponId.HUNTER)
        for((width,height) in listOf(320f to 360f,800f to 360f)) {
            val center=checkNotNull(ArsenalLayout.center(w,WeaponId.HUNTER,width,height))
            assertEquals(ArsenalLayout.left(width,1)+11.5f,center.x,0.001f)
            val start=listOf(Vec2(0f,0f),Vec2(width,0f),Vec2(0f,height),Vec2(width,height)).maxOf { (it-center).length() }+8f
            val ring=RewardFeedback(WeaponId.HUNTER)
            assertEquals(start,ring.pose(start,18f).radius,0.001f)
            ring.advance(0.64f);assertEquals(18f,ring.pose(start,18f).radius,0.001f)
            ring.advance(0.14f);assertTrue(ring.pose(start,18f).radius>18f)
            ring.advance(0.25f);val expanding=ring.pose(start,18f)
            assertTrue(expanding.radius>18f);assertTrue(expanding.alpha in 0f..0.4f)
            ring.advance(0.26f);assertTrue(ring.finished);assertEquals(0f,ring.pose(start,18f).alpha,0.001f)
        }
    }
}
