package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.random.Random

/** Published Drone balance rows. Absolute movement speed remains prototype tuning. */
data class DroneTierDefinition(val tier: Int, val maxHp: Int, val contactDamage: Int) {
    init { require(tier > 0 && maxHp > 0 && contactDamage >= 0) }
}

object DroneTierCatalog {
    val tiers = listOf(
        DroneTierDefinition(tier = 1, maxHp = 80, contactDamage = 40),
        DroneTierDefinition(tier = 2, maxHp = 200, contactDamage = 60),
        DroneTierDefinition(tier = 3, maxHp = 500, contactDamage = 90),
        DroneTierDefinition(tier = 4, maxHp = 1_000, contactDamage = 120),
        DroneTierDefinition(tier = 5, maxHp = 1_800, contactDamage = 150),
        DroneTierDefinition(tier = 6, maxHp = 3_200, contactDamage = 180),
        DroneTierDefinition(tier = 7, maxHp = 5_800, contactDamage = 210)
    )

    val highestPublishedTier get() = tiers.last().tier

    fun definition(tier: Int, mutation: EnemyMutation = EnemyMutation.NONE): EnemyDefinition {
        val balance = tiers.firstOrNull { it.tier == tier }
            ?: error("No published Drone balance for tier $tier")
        return EnemyDefinition(
            maxHp = (balance.maxHp * mutation.maxHpMultiplier).toInt(),
            speed = BASE_SPEED * mutation.speedMultiplier,
            contactDamage = balance.contactDamage,
            radius = BASE_RADIUS * mutation.radiusMultiplier,
            tier = balance.tier,
            mutation = mutation
        )
    }

    const val BASE_SPEED = 8.5f
    const val BASE_RADIUS = 4.5f
}

data class DroneTierProgressionParameters(
    val secondsPerTimeTier: Double = 120.0,
    val playerLevelsPerTier: Int = 4,
    val maximumTier: Int = DroneTierCatalog.highestPublishedTier
) {
    init {
        require(secondsPerTimeTier > 0.0 && secondsPerTimeTier.isFinite())
        require(playerLevelsPerTier > 0)
        require(maximumTier in 1..DroneTierCatalog.highestPublishedTier)
    }
}

/**
 * The source game does not publish its Threat formula. This local policy uses the two
 * documented difficulty inputs without changing the placeholder Threat model.
 */
class DroneTierProgressionPolicy(
    private val parameters: DroneTierProgressionParameters = DroneTierProgressionParameters()
) {
    fun tierAt(elapsedSeconds: Double, playerLevel: Int): Int {
        val elapsed = elapsedSeconds.coerceAtLeast(0.0)
        val timeTier = (elapsed / parameters.secondsPerTimeTier).toInt() + 1
        val levelTier = ((playerLevel - 1).coerceAtLeast(0) / parameters.playerLevelsPerTier) + 1
        return maxOf(timeTier, levelTier).coerceAtMost(parameters.maximumTier)
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
    private val tiers: DroneTierProgressionPolicy = DroneTierProgressionPolicy(),
    private val mutations: DroneMutationPolicy = DroneMutationPolicy()
) : EnemySpawnPolicy {
    override fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random): EnemyDefinition {
        val tier = tiers.tierAt(elapsedSeconds, playerLevel)
        val mutation = mutations.choose(random)
        return DroneTierCatalog.definition(tier, mutation)
    }
}
