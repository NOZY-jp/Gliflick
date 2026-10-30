package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/** Chooses how many enemies should be requested on each simulation update. */
fun interface EnemySpawnSchedule {
    fun update(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random): Int
}

data class WaveSpawnParameters(
    val firstBurstAtSeconds: Double = 3.0,
    val firstTrickleAtSeconds: Double = 0.8,
    val gentleWavePeriodSeconds: Double = 120.0,
    val burstPeriodSeconds: Double = 5.0,
    val burstTimingJitterSeconds: Double = 0.3,
    val burstChancePerPulse: Double = 0.45,
    val averageBurstCount: Double = 1.5,
    val maximumBurstCount: Int = 8,
    val trickleRatePerSecond: Double = 0.18,
    val levelGrowthPerLevel: Double = 0.04,
    val maximumLevelMultiplier: Double = 1.4,
    val minimumWaveMultiplier: Double = 0.75,
    val maximumWaveMultiplier: Double = 1.25
) {
    init {
        require(firstBurstAtSeconds > 0.0 && firstTrickleAtSeconds >= 0.0)
        require(gentleWavePeriodSeconds > 0.0 && burstPeriodSeconds > 0.0)
        require(burstTimingJitterSeconds >= 0.0 && burstTimingJitterSeconds < burstPeriodSeconds)
        require(burstChancePerPulse in 0.0..1.0)
        require(averageBurstCount > 0.0 && maximumBurstCount > 0)
        require(trickleRatePerSecond >= 0.0 && levelGrowthPerLevel >= 0.0)
        require(maximumLevelMultiplier >= 1.0)
        require(minimumWaveMultiplier > 0.0 && maximumWaveMultiplier >= minimumWaveMultiplier)
    }
}

/**
 * Combines a two-minute density envelope with noisy five-second groups and a low-rate trickle.
 * The explicit player-level multiplier is prototype tuning, independent of the uncalibrated Threat model.
 */
class CompositeWaveSpawnSchedule(
    private val parameters: WaveSpawnParameters = WaveSpawnParameters()
) : EnemySpawnSchedule {
    private var nextBurstAtSeconds = parameters.firstBurstAtSeconds

    override fun update(dt: Float, elapsedSeconds: Double, playerLevel: Int, random: Random): Int {
        if (dt <= 0f) return 0
        val previousTime = (elapsedSeconds - dt).coerceAtLeast(0.0)
        var requested = 0
        var crossedBurst = false

        while (elapsedSeconds >= nextBurstAtSeconds) {
            if (random.nextDouble() < parameters.burstChancePerPulse) {
                val multiplier = waveMultiplier(nextBurstAtSeconds) * levelMultiplier(playerLevel)
                requested += samplePoisson(parameters.averageBurstCount * multiplier, random)
            }
            crossedBurst = true
            val jitter = if (parameters.burstTimingJitterSeconds == 0.0) 0.0 else
                random.nextDouble(-parameters.burstTimingJitterSeconds, parameters.burstTimingJitterSeconds)
            nextBurstAtSeconds += parameters.burstPeriodSeconds + jitter
        }

        if (!crossedBurst) {
            val activeStart = maxOf(previousTime, parameters.firstTrickleAtSeconds)
            val activeDuration = (elapsedSeconds - activeStart).coerceAtLeast(0.0)
            if (activeDuration > 0.0) {
                val midpoint = activeStart + activeDuration / 2.0
                val rate = parameters.trickleRatePerSecond * waveMultiplier(midpoint) * levelMultiplier(playerLevel)
                val chance = 1.0 - exp(-rate * activeDuration)
                if (random.nextDouble() < chance) requested++
            }
        }
        return requested
    }

    private fun waveMultiplier(time: Double): Double {
        val phase = (time % parameters.gentleWavePeriodSeconds) / parameters.gentleWavePeriodSeconds
        val wave = (1.0 - cos(2.0 * PI * phase)) / 2.0
        return parameters.minimumWaveMultiplier +
            (parameters.maximumWaveMultiplier - parameters.minimumWaveMultiplier) * wave
    }

    private fun levelMultiplier(playerLevel: Int): Double =
        (1.0 + (playerLevel - 1).coerceAtLeast(0) * parameters.levelGrowthPerLevel)
            .coerceAtMost(parameters.maximumLevelMultiplier)

    private fun samplePoisson(mean: Double, random: Random): Int {
        val boundedMean = mean.coerceAtMost(parameters.maximumBurstCount.toDouble())
        val stopAt = exp(-boundedMean)
        var product = 1.0
        var count = 0
        while (product > stopAt && count < parameters.maximumBurstCount) {
            product *= random.nextDouble()
            count++
        }
        return count
    }
}
