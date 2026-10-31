package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SecondaryWeaponMotionTest {
    private fun world() = CombatWorld(CombatConfig(spawningEnabled = false), Random(17))
        .also { it.resize(400f, 500f) }

    @Test fun spectreLeavesPlayerReachesRemoteEnemyAndKeepsFlying() {
        val world = world()
        val target = world.spawnEnemy(Vec2(330f, 250f), EnemyDefinition(maxHp = 100000, speed = 0f))
        world.acquireWeapon(WeaponId.SPECTRE)
        world.completeWord(WordDefinition("spectre-trigger", "a", languageTag = "en"), target.id)
        val drone = world.weaponActors.single { it.source == WeaponId.SPECTRE }
        val start = drone.position
        repeat(120) { world.update(1f / 120f) }
        assertTrue("Melee drones must move out of the centre", (drone.position - start).length() > 60f)
        assertTrue(drone.trail.distinct().size >= 10)
        repeat(140) { world.update(1f / 120f) }
        assertTrue("The drone itself must damage the distant enemy", world.dps(WeaponId.SPECTRE) > 0f)
        val afterHit = drone.position
        repeat(30) { world.update(1f / 120f) }
        assertTrue("Spectre must keep a visible flying trail after contact", (drone.position - afterHit).length() > 10f)
    }

    @Test fun hunterMovesIntoRangeAndFiresFromTriangleTip() {
        val world = world()
        val target = world.spawnEnemy(Vec2(330f, 250f), EnemyDefinition(maxHp = 100000, speed = 0f))
        world.acquireWeapon(WeaponId.HUNTER)
        world.completeWord(WordDefinition("hunter-trigger", "i", languageTag = "en"), target.id)
        val drone = world.weaponActors.single()
        val start = drone.position
        var beamSeen = false
        repeat(240) {
            world.update(1f / 120f)
            beamSeen = beamSeen || world.weaponEffects.any { it.source == WeaponId.HUNTER }
        }
        assertTrue((drone.position - start).length() > 60f)
        assertEquals(drone.range * 0.6f, (target.position - drone.position).length(), 0.1f)
        assertTrue(world.dps(WeaponId.HUNTER) > 0f)
        assertTrue(beamSeen)
    }

    @Test fun orbiterBeginsAtTheTypedEnemyAndKeepsThatOrbitRadius() {
        val world = world()
        val target = world.spawnEnemy(Vec2(310f, 250f), EnemyDefinition(maxHp = 100000, speed = 0f))
        world.acquireWeapon(WeaponId.ORBITER)
        world.completeWord(WordDefinition("orbiter-trigger", "north", languageTag = "en"), target.id)
        val orb = world.weaponActors.single()
        assertEquals(target.position, orb.position)
        repeat(120) { world.update(1f / 120f) }
        assertEquals(110f, (orb.position - world.player.position).length(), 0.1f)
        assertTrue((orb.position - target.position).length() > 50f)
        assertTrue(orb.trail.distinct().size >= 10)
    }

    @Test fun guardianPursuesAndItsOwnOrbitingFireballsDealDamage() {
        val world = world()
        val target = world.spawnEnemy(Vec2(255f, 250f), EnemyDefinition(maxHp = 100000, speed = 0f))
        world.acquireWeapon(WeaponId.GUARDIAN)
        val guardian = world.weaponActors.single() as GuardianActor
        repeat(240) { world.update(1f / 120f) }
        assertTrue((guardian.position - target.position).length() < 20f)
        assertTrue(world.dps(WeaponId.GUARDIAN) > 0f)
        assertFalse(world.weaponEffects.any { it.source == WeaponId.GUARDIAN })
        guardian.remaining = 0f
        world.update(0f)
        val stopped = guardian.position
        val orbit = guardian.orbitAngle
        repeat(120) { world.update(1f / 120f) }
        assertEquals(stopped, guardian.position)
        assertEquals(orbit, guardian.orbitAngle, 0f)
    }
}
