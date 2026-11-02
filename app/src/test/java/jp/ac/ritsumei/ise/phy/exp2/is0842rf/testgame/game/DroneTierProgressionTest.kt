package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DroneTierProgressionTest {
    private val policy = DroneTierDistributionPolicy()
    private fun probabilities(seconds: Double, level: Int = 1) =
        policy.distributionAt(seconds, level).map { it.probability }

    @Test fun firstMinuteHasOnlyTierOneEvenAtHighPlayerLevels() {
        for (seconds in listOf(0.0, 30.0, 59.999, 60.0)) {
            assertEquals(listOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), probabilities(seconds, 100))
        }
    }

    @Test fun sixMinutesHasCommonTierTwoAndOccasionalTierThreeWithoutABoss() {
        val p = probabilities(360.0)
        assertTrue(p[1] in 0.75..0.80)
        assertTrue(p[2] in 0.20..0.25)
        assertTrue(p[0] < 0.02)
        assertTrue(p.drop(3).all { it == 0.0 })
        assertTrue(probabilities(120.0)[1] in 0.04..0.07)
        assertTrue(probabilities(180.0)[1] in 0.45..0.50)
    }

    @Test fun openingAndEveryTierBoundaryRemainContinuousAtLowAndHighLevels() {
        for (level in listOf(1, 20, 100)) {
            for (step in 0..6) {
                val boundary = 60.0 + step * 210.0
                val before = probabilities(boundary - 0.001, level)
                val after = probabilities(boundary + 0.001, level)
                before.zip(after).forEach { (a, b) -> assertTrue(abs(a - b) < 0.0001) }
            }
        }
    }

    @Test fun expectedTierIncreasesAndProbabilitiesStayNormalizedThroughoutRun() {
        for (level in listOf(1, 20, 100)) {
            var previousMean = 1.0
            for (seconds in 0..3600) {
                val p = probabilities(seconds.toDouble(), level)
                assertEquals(1.0, p.sum(), 1e-12)
                assertTrue(p.all { it.isFinite() && it in 0.0..1.0 })
                val mean = p.mapIndexed { index, chance -> (index + 1) * chance }.sum()
                assertTrue("Mean fell at $seconds / level $level", mean + 1e-10 >= previousMean)
                previousMean = mean
            }
        }
    }

    @Test fun actualSpawnPolicySamplesNewTiersWithoutBossNotifications() {
        val spawns = DroneEnemySpawnPolicy()
        val random = Random(821)
        val counts = IntArray(8)
        repeat(10000) { counts[spawns.choose(360.0, 1, random).tier]++ }
        assertTrue(counts[2] in 7400..8100)
        assertTrue(counts[3] in 1900..2600)
        assertEquals(0, counts.drop(4).sum())
        assertTrue((1..1000).any { spawns.choose(1260.0, 1, random).tier == 7 })
    }

    @Test fun configuredTierCapStillLimitsLongRuns() {
        val limited = DroneTierDistributionPolicy(DroneTierDistributionParameters(maximumTier = 3))
        assertEquals(listOf(1, 2, 3), limited.distributionAt(3600.0, 100).map { it.tier })
        assertEquals(listOf(1), policy.distributionAt(3600.0, 100, maximumAvailableTier = 1).map { it.tier })
    }
}
