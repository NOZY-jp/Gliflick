package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.random.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Published balance values and Wiki-derived appearance motif for one Drone tier. */
data class DroneTierDefinition(
    val tier: Int,
    val maxHp: Int,
    val contactDamage: Int,
    val markerPattern: EnemyMarkerPattern
) {
    init { require(tier > 0 && maxHp > 0 && contactDamage >= 0) }
}

object DroneTierCatalog {
    val tiers = listOf(
        DroneTierDefinition(1, 80, 40, EnemyMarkerPattern.DOT),
        DroneTierDefinition(2, 200, 60, EnemyMarkerPattern.RINGED_DOT),
        DroneTierDefinition(3, 500, 90, EnemyMarkerPattern.DOUBLE_RINGED_DOT),
        DroneTierDefinition(4, 1_000, 120, EnemyMarkerPattern.BULLSEYE),
        DroneTierDefinition(5, 1_800, 150, EnemyMarkerPattern.CENTER_HOLE),
        DroneTierDefinition(6, 3_200, 180, EnemyMarkerPattern.SEGMENTED_CENTER_HOLE),
        DroneTierDefinition(7, 5_800, 210, EnemyMarkerPattern.RINGED_SEGMENTED_CENTER_HOLE)
    )

    val highestPublishedTier get() = tiers.last().tier

    fun definition(tier: Int, mutation: EnemyMutation = EnemyMutation.NONE): EnemyDefinition {
        val balance = tiers.firstOrNull { it.tier == tier }
            ?: error("No published Drone balance for tier $tier")
        return EnemyDefinition(
            shape = EnemyShape.CIRCLE,
            markerPattern = balance.markerPattern,
            maxHp = (balance.maxHp * mutation.maxHpMultiplier).toInt(),
            speed = BASE_SPEED * mutation.speedMultiplier,
            contactDamage = balance.contactDamage,
            radius = BASE_RADIUS,
            size = mutation.sizeMultiplier,
            tier = balance.tier,
            mutation = mutation
        )
    }

    const val BASE_SPEED = 5.95f
    const val BASE_RADIUS = 4.5f
}

data class DroneTierDistributionParameters(
    val secondsPerTierProgress: Double = 120.0,
    val playerLevelsPerTierProgress: Int = 4,
    val initialSpread: Double = 0.4,
    val spreadGrowthPerTier: Double = 0.045,
    val maximumSpread: Double = 0.7,
    val maximumTier: Int = DroneTierCatalog.highestPublishedTier
) {
    init {
        require(secondsPerTierProgress > 0.0 && secondsPerTierProgress.isFinite())
        require(playerLevelsPerTierProgress > 0)
        require(initialSpread > 0.0 && initialSpread.isFinite())
        require(spreadGrowthPerTier >= 0.0 && spreadGrowthPerTier.isFinite())
        require(maximumSpread >= initialSpread && maximumSpread.isFinite())
        require(maximumTier in 1..DroneTierCatalog.highestPublishedTier)
    }
}

data class DroneTierProbability(val tier: Int, val probability: Double)

/**
 * Smoothly shifts a Gaussian-like tier distribution using elapsed time and player level.
 * The exact source-game Threat formula is unpublished, so the rates remain tuning parameters.
 */
class DroneTierDistributionPolicy(
    private val parameters: DroneTierDistributionParameters = DroneTierDistributionParameters()
) {
    fun distributionAt(elapsedSeconds: Double, playerLevel: Int): List<DroneTierProbability> {
        val timeProgress = elapsedSeconds.coerceAtLeast(0.0) / parameters.secondsPerTierProgress
        val levelProgress = (playerLevel - 1).coerceAtLeast(0).toDouble() /
            parameters.playerLevelsPerTierProgress
        val progression = max(timeProgress, levelProgress).coerceAtMost((parameters.maximumTier - 1).toDouble())
        val center = progression + 1.0
        val spread = min(parameters.initialSpread + progression * parameters.spreadGrowthPerTier,
            parameters.maximumSpread)
        val weights = DroneTierCatalog.tiers.take(parameters.maximumTier).map { tier ->
            exp(-0.5 * ((tier.tier - center) / spread) * ((tier.tier - center) / spread))
        }
        val totalWeight = weights.sum()
        return weights.mapIndexed { index, weight ->
            DroneTierProbability(DroneTierCatalog.tiers[index].tier, weight / totalWeight)
        }
    }

    fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random): Int {
        val distribution = distributionAt(elapsedSeconds, playerLevel)
        val roll = random.nextDouble()
        var cumulative = 0.0
        distribution.forEach { outcome ->
            cumulative += outcome.probability
            if (roll < cumulative) return outcome.tier
        }
        return distribution.last().tier
    }
}

data class DroneMutationParameters(val combinedChance: Double = 0.05) {
    init { require(combinedChance in 0.0..1.0) }
}

/** The published source gives mutation effects but not their probability. */
class DroneMutationPolicy(
    private val parameters: DroneMutationParameters = DroneMutationParameters()
) {
    fun choose(random: Random): EnemyMutation {
        if (random.nextDouble() >= parameters.combinedChance) return EnemyMutation.NONE
        return if (random.nextBoolean()) EnemyMutation.ENLARGED else EnemyMutation.HASTENED
    }
}

class DroneEnemySpawnPolicy(
    private val tiers: DroneTierDistributionPolicy = DroneTierDistributionPolicy(),
    private val mutations: DroneMutationPolicy = DroneMutationPolicy()
) : EnemySpawnPolicy {
    override fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random): EnemyDefinition {
        val tier = tiers.choose(elapsedSeconds, playerLevel, random)
        val mutation = mutations.choose(random)
        return DroneTierCatalog.definition(tier, mutation)
    }
}
