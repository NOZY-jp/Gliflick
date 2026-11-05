package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EnemyArrivalRatesTest {
    private val policy = MixedEnemySpawnPolicy()
    private fun rates(seconds: Double, level: Int = 1, living: List<Enemy> = emptyList()) =
        policy.distributionAt(seconds, level, living).groupBy { it.kind }
            .mapValues { (_, tiers) -> tiers.sumOf { it.weight } }
    private fun drones(count: Int) = (1..count).map { Enemy(it.toLong(), EnemyDefinition(), Vec2.ZERO) }

    @Test fun openingIsDroneOnlyAndShieldsRampToOneThirdOfTheNormalPair() {
        for (level in listOf(1, 100)) {
            for (time in listOf(0.0, 30.0, 60.0))
                assertEquals(setOf(EnemyKind.DRONE), rates(time, level).keys)
            val early = rates(61.0, level)
            assertTrue(early.getValue(EnemyKind.SHIELD_DRONE) / early.values.sum() in 0.0..0.001)
            assertEquals(1.0 / 6.0, policy.shieldShareAt(150.0), 1e-12)
            for (time in listOf(240.0, 360.0, 1800.0)) {
                val r = rates(time, level)
                assertEquals(2.0, r.getValue(EnemyKind.DRONE) / r.getValue(EnemyKind.SHIELD_DRONE), 1e-12)
            }
        }
        var previous = 0.0
        for (second in 60..240) {
            val share = policy.shieldShareAt(second.toDouble())
            assertTrue(share >= previous)
            previous = share
        }
        assertTrue(policy.shieldShareAt(60.001) < 1e-9)
        assertEquals(policy.shieldShareAt(239.999), policy.shieldShareAt(240.001), 1e-9)
    }

    @Test fun specialMeansAreAbsoluteAndIndependentOfWaveAndLevel() {
        val specials = mapOf(EnemyKind.SHOOTER to 15.0, EnemyKind.SPLITTER to 30.0, EnemyKind.WARDER to 90.0)
        assertTrue(rates(150.0).keys.none { it in specials })
        for (time in listOf(150.001, 180.0, 240.0, 300.0, 360.0, 1800.0)) {
            for (level in listOf(1, 30, 100)) {
                val r = rates(time, level)
                specials.forEach { (kind, mean) -> assertEquals(1.0 / mean, r.getValue(kind), 1e-12) }
            }
        }
    }

    @Test fun oldSpecialAndEliteCapsCannotPostponeTheNewSchedule() {
        val living = listOf(EnemyKind.SHOOTER, EnemyKind.SPLITTER, EnemyKind.WARDER).mapIndexed { i, kind ->
            Enemy(i.toLong(), EnemyCatalog.definition(kind), Vec2.ZERO)
        }
        val r = rates(180.0, living = living)
        assertEquals(1.0 / 15.0, r.getValue(EnemyKind.SHOOTER), 1e-12)
        assertEquals(1.0 / 30.0, r.getValue(EnemyKind.SPLITTER), 1e-12)
        assertEquals(1.0 / 90.0, r.getValue(EnemyKind.WARDER), 1e-12)
    }

    @Test fun unavailableSplitterRateIsDroppedRatherThanRedistributedToOtherKinds() {
        val fullRates = rates(360.0)
        val crowded = rates(360.0, living = drones(13))
        assertFalse(crowded.containsKey(EnemyKind.SPLITTER))
        crowded.forEach { (kind, rate) -> assertEquals(fullRates.getValue(kind), rate, 1e-12) }
        assertEquals(1.0 / 30.0, fullRates.values.sum() - crowded.values.sum(), 1e-12)
    }

    @Test fun arrivalIntensityEqualsTheSumOfEligibleTierRatesIncludingNearTheCap() {
        for (time in listOf(60.0, 150.001, 360.0, 900.0, 1800.0)) {
            for (count in 0..16) {
                val living = drones(count)
                assertEquals(policy.distributionAt(time, 40, living).sumOf { it.weight },
                    policy.ratePerSecondAt(time, 40, living), 1e-12)
            }
        }
        val density = PoissonEnemySpawnDensity(policy)
        repeat(1000) { assertFalse(density.shouldSpawn(1f, 1000.0 + it, 40, Random(it), drones(16))) }
    }

    @Test fun sampledArrivalsMatchTheRequestedMeansAndHaveShortAndLongRandomGaps() {
        val density = PoissonEnemySpawnDensity(policy)
        val random = Random(5728)
        val counts = mutableMapOf<EnemyKind, Int>()
        val shooterGaps = mutableListOf<Double>()
        var lastShooter: Double? = null
        val steps = 1_000_000
        val dt = 1f / 60f
        repeat(steps) { step ->
            val level = if (step < steps / 2) 1 else 30
            val time = if (step < steps / 2) 360.0 else 420.0
            if (density.shouldSpawn(dt, time, level, random)) {
                val kind = policy.choose(time, level, random).kind
                counts[kind] = (counts[kind] ?: 0) + 1
                if (kind == EnemyKind.SHOOTER) {
                    val now = step * dt.toDouble()
                    lastShooter?.let { shooterGaps += now - it }
                    lastShooter = now
                }
            }
        }
        val seconds = steps * dt.toDouble()
        for ((kind, mean) in mapOf(EnemyKind.SHOOTER to 15.0, EnemyKind.SPLITTER to 30.0, EnemyKind.WARDER to 90.0)) {
            val expected = seconds / mean
            val count = counts.getValue(kind).toDouble()
            assertTrue("$kind count $count vs expected $expected", count in expected * 0.8..expected * 1.2)
        }
        assertTrue(shooterGaps.any { it < 1.0 })
        assertTrue(shooterGaps.any { it > 30.0 })
        println("Sampled species arrivals over ${seconds}s: $counts")
    }

    @Test fun theDefaultWorldUsesTheScheduleAndCountsSplitterPartsInsideSixteenSlots() {
        val world = CombatWorld(CombatConfig(playerMaxHp = 1000000), Random(781))
        world.resize(800f, 1000f)
        val firstArrival = mutableMapOf<EnemyKind, Double>()
        repeat(360 * 120) {
            world.update(1f / 120f)
            assertTrue(world.livingPopulation <= 16)
            world.drainEvents().filterIsInstance<CombatEvent.Spawned>().forEach { event ->
                if (event.enemy.definition.kind != EnemyKind.SPLITTER_SHARD)
                    firstArrival.putIfAbsent(event.enemy.definition.kind, world.elapsedSeconds)
            }
            // Keep capacity available, without selecting words or granting main-weapon attacks.
            world.enemies.filter { it.isAlive }.toList().forEach { world.damageEnemy(it.id, 1000000) }
        }
        assertEquals(setOf(EnemyKind.DRONE, EnemyKind.SHIELD_DRONE, EnemyKind.SHOOTER,
            EnemyKind.SPLITTER, EnemyKind.WARDER), firstArrival.keys)
        assertTrue(firstArrival.getValue(EnemyKind.SHIELD_DRONE) > 60.0)
        for (kind in listOf(EnemyKind.SHOOTER, EnemyKind.SPLITTER, EnemyKind.WARDER))
            assertTrue(firstArrival.getValue(kind) > 150.0)
    }
}
