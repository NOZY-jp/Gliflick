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
    val markerPattern: EnemyMarkerPattern,
    val bodyScale: Float
) {
    init { require(tier > 0 && maxHp > 0 && contactDamage >= 0 && bodyScale > 0f) }
}

object DroneTierCatalog {
    val tiers = listOf(
        DroneTierDefinition(1, 80, 40, EnemyMarkerPattern.DOT, 1f),
        DroneTierDefinition(2, 200, 60, EnemyMarkerPattern.RINGED_DOT, 2f),
        DroneTierDefinition(3, 500, 90, EnemyMarkerPattern.DOUBLE_RINGED_DOT, 2.83f),
        DroneTierDefinition(4, 1_000, 120, EnemyMarkerPattern.BULLSEYE, 2.83f),
        DroneTierDefinition(5, 1_800, 150, EnemyMarkerPattern.CENTER_HOLE, 2.83f),
        DroneTierDefinition(6, 3_200, 180, EnemyMarkerPattern.SEGMENTED_CENTER_HOLE, 2.83f),
        DroneTierDefinition(7, 5_800, 210, EnemyMarkerPattern.RINGED_SEGMENTED_CENTER_HOLE, 2.83f)
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
            size = balance.bodyScale * mutation.sizeMultiplier,
            tier = balance.tier,
            mutation = mutation
        )
    }

    const val BASE_SPEED = 5.95f
    const val BASE_RADIUS = 4.5f
}

data class DroneTierDistributionParameters(
    val tierOneOnlySeconds: Double = 60.0,
    val secondsPerTierProgress: Double = 210.0,
    val playerLevelsPerTierProgress: Int = 20,
    val centerShiftPerTierProgress: Double = 1.0,
    val initialSpread: Double = 0.4,
    val spreadGrowthPerTier: Double = 0.045,
    val maximumSpread: Double = 0.7,
    val maximumTier: Int = DroneTierCatalog.highestPublishedTier
) {
    init {
        require(tierOneOnlySeconds >= 0.0 && tierOneOnlySeconds.isFinite())
        require(secondsPerTierProgress > 0.0 && secondsPerTierProgress.isFinite())
        require(playerLevelsPerTierProgress > 0)
        require(centerShiftPerTierProgress >= 0.0 && centerShiftPerTierProgress.isFinite())
        require(initialSpread > 0.0 && initialSpread.isFinite())
        require(spreadGrowthPerTier >= 0.0 && spreadGrowthPerTier.isFinite())
        require(maximumSpread >= initialSpread && maximumSpread.isFinite())
        require(maximumTier in 1..DroneTierCatalog.highestPublishedTier)
    }
}

data class DroneTierProbability(val tier: Int, val probability: Double)

/**
 * Smoothly shifts a Gaussian-like tier distribution using elapsed time and player level.
 * Each new tier fades into the pool over one progression step, so a fresh run starts at Tier 1.
 * The exact source-game Threat formula is unpublished, so the rates remain tuning parameters.
 */
class DroneTierDistributionPolicy(
    private val parameters: DroneTierDistributionParameters = DroneTierDistributionParameters()
) {
    val maximumConfiguredTier get() = parameters.maximumTier

    fun distributionAt(
        elapsedSeconds: Double,
        playerLevel: Int,
        maximumAvailableTier: Int = parameters.maximumTier
    ): List<DroneTierProbability> {
        require(maximumAvailableTier in 1..DroneTierCatalog.highestPublishedTier)
        val tierLimit = min(parameters.maximumTier, maximumAvailableTier)
        val secondsAfterTierOneOpening =
            (elapsedSeconds - parameters.tierOneOnlySeconds).coerceAtLeast(0.0)
        val timeProgress = secondsAfterTierOneOpening / parameters.secondsPerTierProgress
        // Fade level adaptation in too: leaving the Tier-1 opening must not jump at high levels.
        val openingProgress = timeProgress.coerceIn(0.0, 1.0)
        val levelWeight = openingProgress * openingProgress * (3.0 - 2.0 * openingProgress)
        val levelProgress = (playerLevel - 1).coerceAtLeast(0).toDouble() /
            parameters.playerLevelsPerTierProgress * levelWeight
        val progression = if (elapsedSeconds < parameters.tierOneOnlySeconds) 0.0 else
            max(timeProgress, levelProgress).coerceAtMost((tierLimit - 1).toDouble())
        val center = 1.0 + progression * parameters.centerShiftPerTierProgress
        val spread = min(parameters.initialSpread + progression * parameters.spreadGrowthPerTier,
            parameters.maximumSpread)
        val weights = DroneTierCatalog.tiers.take(tierLimit).map { tier ->
            val distance = (tier.tier - center) / spread
            val gaussianWeight = exp(-0.5 * distance * distance)
            val unlockProgress = if (tier.tier <= 1) 1.0 else
                (progression - (tier.tier - 2)).coerceIn(0.0, 1.0)
            val smoothUnlock = unlockProgress * unlockProgress * (3.0 - 2.0 * unlockProgress)
            gaussianWeight * smoothUnlock
        }
        val totalWeight = weights.sum()
        return weights.mapIndexed { index, weight ->
            DroneTierProbability(DroneTierCatalog.tiers[index].tier, weight / totalWeight)
        }
    }

    fun choose(
        elapsedSeconds: Double,
        playerLevel: Int,
        random: Random,
        maximumAvailableTier: Int = parameters.maximumTier
    ): Int {
        val distribution = distributionAt(elapsedSeconds, playerLevel, maximumAvailableTier)
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
