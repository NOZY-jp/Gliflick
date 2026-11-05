package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/** Decides whether one individual enemy appears during this simulation step. */
fun interface EnemySpawnDensity {
    fun shouldSpawn(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random): Boolean
    fun shouldSpawn(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random,
                    living: List<Enemy>): Boolean = shouldSpawn(dt, elapsedSeconds, playerLevel, random)
}

/** Absolute arrival intensity, including population-dependent eligibility; never a countdown. */
fun interface EnemySpawnRate {
    fun ratePerSecondAt(elapsedSeconds: Double, playerLevel: Int, living: List<Enemy>): Double
}

/** A sum of eligible species rates drives one shared Poisson arrival decision per frame. */
class PoissonEnemySpawnDensity(private val rates: EnemySpawnRate) : EnemySpawnDensity {
    override fun shouldSpawn(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random) =
        shouldSpawn(dt, elapsedSeconds, playerLevel, random, emptyList())

    override fun shouldSpawn(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random,
                             living: List<Enemy>): Boolean {
        if (dt <= 0f) return false
        val rate = rates.ratePerSecondAt(elapsedSeconds, playerLevel, living)
        require(rate >= 0.0 && rate.isFinite())
        return rate > 0.0 && random.nextDouble() < 1.0 - exp(-rate * dt)
    }
}

data class SpawnDensityParameters(
    val baseRatePerSecond: Double = 0.45,
    val smoothWavePeriodSeconds: Double = 120.0,
    val minimumWaveMultiplier: Double = 0.8,
    val maximumWaveMultiplier: Double = 1.2,
    val levelGrowthPerLevel: Double = 0.04,
    val maximumLevelMultiplier: Double = 1.4
) {
    init {
        require(baseRatePerSecond >= 0.0 && baseRatePerSecond.isFinite())
        require(smoothWavePeriodSeconds > 0.0 && smoothWavePeriodSeconds.isFinite())
        require(minimumWaveMultiplier > 0.0 && minimumWaveMultiplier.isFinite())
        require(maximumWaveMultiplier >= minimumWaveMultiplier && maximumWaveMultiplier.isFinite())
        require(levelGrowthPerLevel >= 0.0 && levelGrowthPerLevel.isFinite())
        require(maximumLevelMultiplier >= 1.0 && maximumLevelMultiplier.isFinite())
    }
}

/** Random Poisson arrivals under a smoothly varying density; each step can spawn at most one enemy. */
class RandomEnemySpawnDensity(
    private val parameters: SpawnDensityParameters = SpawnDensityParameters()
) : EnemySpawnDensity {
    fun ratePerSecondAt(elapsedSeconds: Double, playerLevel: Int): Double {
        val phase = (elapsedSeconds.coerceAtLeast(0.0) % parameters.smoothWavePeriodSeconds) /
            parameters.smoothWavePeriodSeconds
        val wave = (1.0 - cos(2.0 * PI * phase)) / 2.0
        val waveMultiplier = parameters.minimumWaveMultiplier +
            (parameters.maximumWaveMultiplier - parameters.minimumWaveMultiplier) * wave
        val levelMultiplier = (1.0 + (playerLevel - 1).coerceAtLeast(0) * parameters.levelGrowthPerLevel)
            .coerceAtMost(parameters.maximumLevelMultiplier)
        return parameters.baseRatePerSecond * waveMultiplier * levelMultiplier
    }

    override fun shouldSpawn(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random): Boolean {
        if (dt <= 0f) return false
        val stepMidpoint = (elapsedSeconds - dt / 2.0).coerceAtLeast(0.0)
        val arrivalChance = 1.0 - exp(-ratePerSecondAt(stepMidpoint, playerLevel) * dt)
        return random.nextDouble() < arrivalChance
    }
}
