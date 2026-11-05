package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class OscillatorMotionTest {
    private val dt = 1f / 120f
    private fun world() = CombatWorld(CombatConfig(spawningEnabled = false), Random(17))
        .also { it.resize(800f, 1000f); it.acquireWeapon(WeaponId.OSCILLATOR) }
    private fun aim(world: CombatWorld, offset: Vec2) {
        world.secondaryWeapons.single().onWord(world,
            CompletedCombatWord(WordDefinition("wave", "a", languageTag = "en"), -1L, world.player.position + offset))
    }
    private fun dots(world: CombatWorld) = world.projectiles.filter { it.source == WeaponId.OSCILLATOR }
    private fun heading(p: Projectile) = atan2(p.velocity.y, p.velocity.x)

    @Test fun denseDotsKeepEvenRadialSpacingAndIncreasingWaveEnvelope() {
        val w = world()
        repeat(105) { w.update(dt) }
        val dots = dots(w).sortedBy { it.age }
        assertTrue("A wave needs a dense row, not isolated bullets", dots.size >= 45)
        dots.zipWithNext().forEach { (a, b) ->
            val gap = (b.position - w.player.position).length() - (a.position - w.player.position).length()
            assertEquals(6f, gap, 0.02f)
        }
        val near = dots.filter { (it.position - w.player.position).length() in 10f..40f }
        val far = dots.filter { (it.position - w.player.position).length() in 170f..310f }
        assertTrue(far.maxOf { abs(it.position.x - w.player.position.x) } >
            near.maxOf { abs(it.position.x - w.player.position.x) } * 2f)
        assertTrue(dots.all { abs(it.velocity.length() - 360f) < 0.01f })
    }

    @Test fun retargetingTurnsAlongShortestArcAndLeavesTheOldWaveTipBehind() {
        val w = world()
        repeat(30) { w.update(dt) }
        val oldTip = dots(w).maxBy { it.age }
        val originalVelocity = oldTip.velocity
        aim(w, Vec2(150f, 0f))
        w.update(dt)
        assertEquals(originalVelocity, oldTip.velocity)
        assertTrue("The new heading must not snap to the right", heading(dots(w).minBy { it.age }) < -1f)
        repeat(30) { w.update(dt) }
        val newest = dots(w).minBy { it.age }
        assertTrue("The base turns first while older dots preserve the curved tip", heading(newest) > heading(oldTip) + 0.4f)
        assertEquals(originalVelocity, oldTip.velocity)
        repeat(35) { w.update(dt) }
        assertTrue(abs(heading(dots(w).minBy { it.age })) <= 0.201f)
    }

    @Test fun oneSweepCanHitAnEnemySeveralTimesWithoutRepeaterRings() {
        val w = world()
        aim(w, Vec2(150f, 0f))
        repeat(180) { w.update(dt) }
        val enemy = w.spawnEnemy(w.player.position + Vec2(150f, 0f), EnemyDefinition(maxHp = 100000, speed = 0f))
        val bursts = mutableListOf<Int>()
        var count = 0
        var quiet = 0f
        repeat(360) {
            w.update(dt)
            enemy.statuses.clear() // Separate wave contacts from the independently tested random Ignite.
            val hits = w.hitFeedback.count { it.age == 0f }
            if (hits > 0) { count += hits; quiet = 0f }
            else if (count > 0) {
                quiet += dt
                if (quiet > 0.12f) { bursts += count; count = 0 }
            }
            assertTrue(w.hitFeedback.none { it.showRing })
        }
        println("Oscillator sweep contacts: $bursts")
        assertTrue("Ordinary sweeps should produce roughly 3-5 contacts: $bursts", bursts.size >= 3 && bursts.all { it in 3..5 })
    }

    @Test fun twinHeadedAddsAnOpposingWaveAndPlasmaPreservesDownstreamHalfDamage() {
        val w = world()
        val state = w.secondaryWeapons.single().state
        state.evolutions[SecondaryEvolution.TWIN_HEADED] = 1
        state.evolutions[SecondaryEvolution.STABILIZED_PLASMA] = 1
        repeat(2) { w.update(dt) }
        val dots = dots(w)
        assertEquals(2, dots.size)
        assertEquals(0f, (dots[0].velocity + dots[1].velocity).length(), 0.001f)
        assertTrue(dots.all { it.penetrates })
    }

    @Test fun onlyDirectRepeaterContactsShowTheLargerRing() {
        val w = CombatWorld(CombatConfig(spawningEnabled = false), Random(4)).also { it.resize(800f, 1000f) }
        val target = w.spawnEnemy(w.player.position + Vec2(50f, 0f), EnemyDefinition(maxHp = 100000, speed = 0f))
        w.dealDamage(target.id, 10, WeaponId.SENTRY, DamageType.KINETIC, inflictStatus = false)
        w.dealDamage(target.id, 10, WeaponId.REPEATER, DamageType.HEAT, inflictStatus = false)
        assertTrue(w.hitFeedback.none { it.showRing })
        w.launch(Projectile(w.player.position, Vec2(520f, 0f), source = WeaponId.REPEATER))
        repeat(12) { w.update(dt) }
        val hit = w.hitFeedback.single { it.showRing }
        assertEquals(target.radius * 1.2f * (1f + 0.2f * hit.ringProgress), hit.ringRadius, 0.001f)
        assertTrue(hit.ringStrokeWidth > 0f)
        val other = w.hitFeedback.filter { !it.showRing }
        assertTrue(other.all { it.ringStrokeWidth == 0f && it.fragments.size == 10 })
    }
}
