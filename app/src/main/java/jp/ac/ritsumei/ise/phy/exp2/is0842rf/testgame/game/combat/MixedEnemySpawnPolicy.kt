package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.*
import kotlin.random.Random

data class EnemySpawnDifficulty(val frames: Double, val threatBudget: Int, val specialCap: Int, val eliteCap: Int) {
    companion object {
        /** Source demo Spawner.getTimeFactor/getThreatBudget/getSpecialsBudget/getEliteBudget, 60fps. */
        fun at(seconds: Double, playerLevel: Int): EnemySpawnDifficulty {
            val elapsed = seconds.coerceAtLeast(0.0) * 60.0
            val adaptive = (2.0.pow((0.000025 * elapsed).coerceAtMost(30.0)) - 1.6) * playerLevel * 3600.0
            val frames = max(elapsed, adaptive).coerceAtMost(1e9)
            return EnemySpawnDifficulty(frames, floor(30.0 + 0.0045 * frames).toInt(),
                floor(1.0 + 0.000082 * frames).toInt(), floor(0.000035 * frames).toInt())
        }
    }
}

/** Preserve one firing slot under the local 16-body cap, so a full arena cannot silence Shooters. */
fun arrivalCapacity(enemies: List<Enemy>, limit: Int): Int {
    val alive = enemies.filter { it.isAlive }
    val reserve = if (alive.any { it.definition.kind == EnemyKind.SHOOTER } &&
        alive.none { it.definition.kind == EnemyKind.SHOOTER_MISSILE }) 1 else 0
    return (limit - alive.sumOf { it.populationCost } - reserve).coerceAtLeast(0)
}

data class SpawnWeight(val kind: EnemyKind, val tier: Int, val weight: Double)

/** A Poisson mean interval is an expectation, not a fixed timer or guaranteed arrival. */
data class SpeciesArrival(val kind: EnemyKind, val startsAfterSeconds: Double, val meanSeconds: Double) {
    init {
        require(startsAfterSeconds >= 0.0 && startsAfterSeconds.isFinite())
        require(meanSeconds > 0.0 && meanSeconds.isFinite())
        require(kind in setOf(EnemyKind.SHOOTER, EnemyKind.SPLITTER, EnemyKind.WARDER))
    }
}

data class MixedSpawnParameters(
    val droneOnlySeconds: Double = 60.0,
    val shieldBalanceSeconds: Double = 240.0,
    val finalShieldShare: Double = 1.0 / 3.0,
    val additionalArrivals: List<SpeciesArrival> = listOf(
        SpeciesArrival(EnemyKind.SHOOTER, 150.0, 15.0),
        SpeciesArrival(EnemyKind.SPLITTER, 150.0, 30.0),
        SpeciesArrival(EnemyKind.WARDER, 150.0, 90.0)
    )
) {
    init {
        require(droneOnlySeconds >= 0.0 && droneOnlySeconds.isFinite())
        require(shieldBalanceSeconds > droneOnlySeconds && shieldBalanceSeconds.isFinite())
        require(finalShieldShare in 0.0..1.0)
        require(additionalArrivals.map { it.kind }.distinct().size == additionalArrivals.size)
    }
}

/** Local species timing/rates; source weights now determine only tiers within each family. */
class MixedEnemySpawnPolicy(private val drones: DroneEnemySpawnPolicy = DroneEnemySpawnPolicy(),
                           private val populationLimit: Int = 16,
                           private val parameters: MixedSpawnParameters = MixedSpawnParameters(),
                           private val normalDensity: RandomEnemySpawnDensity = RandomEnemySpawnDensity()
) : EnemySpawnPolicy, EnemySpawnRate {
    override fun onBossDefeated(bossesDefeated: Int) = drones.onBossDefeated(bossesDefeated)
    override fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random) =
        choose(elapsedSeconds, playerLevel, random, emptyList())
    override fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random, living: List<Enemy>): EnemyDefinition {
        val choices = distributionAt(elapsedSeconds, playerLevel, living)
        if (choices.isEmpty()) return drones.choose(elapsedSeconds, playerLevel, random)
        var roll = random.nextDouble() * choices.sumOf { it.weight }
        val choice = choices.firstOrNull { roll -= it.weight; roll < 0.0 } ?: choices.last()
        return if (choice.kind == EnemyKind.DRONE) drones.choose(elapsedSeconds, playerLevel, random)
            else EnemyCatalog.definition(choice.kind, choice.tier)
    }

    fun shieldShareAt(seconds: Double): Double {
        val progress = ((seconds - parameters.droneOnlySeconds) /
            (parameters.shieldBalanceSeconds - parameters.droneOnlySeconds)).coerceIn(0.0, 1.0)
        return parameters.finalShieldShare * progress * progress * (3.0 - 2.0 * progress)
    }

    /** Weights are arrivals per second, not percentages. Drone tiers are chosen separately. */
    fun distributionAt(seconds: Double, level: Int, enemies: List<Enemy> = emptyList()): List<SpawnWeight> {
        val remaining = arrivalCapacity(enemies, populationLimit)
        if (remaining == 0) return emptyList()
        return speciesRatesAt(seconds, level).flatMap { (kind, rate) ->
            tierFractionsAt(kind, seconds, level).map { tier ->
                SpawnWeight(kind, tier.tier, rate * tier.weight)
            }.filter { it.weight > 0.0 && populationCost(it.kind, it.tier) <= remaining }
        }
    }

    override fun ratePerSecondAt(elapsedSeconds: Double, playerLevel: Int, living: List<Enemy>): Double {
        val remaining = arrivalCapacity(living, populationLimit)
        if (remaining == 0) return 0.0
        // The normal pair both cost one slot. Avoid rebuilding the full tier table every frame.
        var total = normalDensity.ratePerSecondAt(elapsedSeconds, playerLevel)
        parameters.additionalArrivals.forEach { arrival ->
            if (elapsedSeconds > arrival.startsAfterSeconds) {
                val eligibleFraction = if (arrival.kind != EnemyKind.SPLITTER) 1.0 else when {
                    remaining < 4 -> 0.0
                    remaining >= 6 -> 1.0
                    else -> tierFractionsAt(arrival.kind, elapsedSeconds, playerLevel)
                        .filter { populationCost(it.kind, it.tier) <= remaining }.sumOf { it.weight }
                }
                total += eligibleFraction / arrival.meanSeconds
            }
        }
        return total
    }

    private fun speciesRatesAt(seconds: Double, level: Int): List<Pair<EnemyKind, Double>> {
        val normalRate = normalDensity.ratePerSecondAt(seconds, level)
        val shieldShare = shieldShareAt(seconds)
        return listOf(EnemyKind.DRONE to normalRate * (1.0 - shieldShare),
            EnemyKind.SHIELD_DRONE to normalRate * shieldShare) +
            parameters.additionalArrivals.filter { seconds > it.startsAfterSeconds }
                .map { it.kind to 1.0 / it.meanSeconds }
    }

    private fun populationCost(kind: EnemyKind, tier: Int) = if (kind == EnemyKind.SPLITTER) tier + 3 else 1

    private fun tierFractionsAt(kind: EnemyKind, seconds: Double, level: Int): List<SpawnWeight> {
        if (kind == EnemyKind.DRONE) return listOf(SpawnWeight(kind, 1, 1.0))
        val minute = EnemySpawnDifficulty.at(seconds, level).frames / 3600.0
        val index = rows.indexOfFirst { minute <= it.first }.let { if (it < 0) rows.lastIndex else it }
        val before = if (index == 0) rows[0].second else rows[index - 1].second
        val after = rows[index].second
        val start = if (index == 0) 0.0 else rows[index - 1].first.toDouble()
        val raw = (minute - start).coerceIn(0.0, 1.0)
        val blend = raw * raw * (3.0 - 2.0 * raw)
        val tiers = (before + after).filter { it.kind == kind }.map { it.tier }.distinct().map { tier ->
            val from = before.filter { it.kind == kind && it.tier == tier }.sumOf { it.weight }
            val to = after.filter { it.kind == kind && it.tier == tier }.sumOf { it.weight }
            SpawnWeight(kind, tier, from + (to - from) * blend)
        }.filter { it.weight > 0.0 }
        // A family can now arrive earlier than in the source table; start that family at Tier 1.
        if (tiers.isEmpty()) return listOf(SpawnWeight(kind, 1, 1.0))
        val total = tiers.sumOf { it.weight }
        return tiers.map { it.copy(weight = it.weight / total) }
    }
    companion object {
        private val rows = listOf(
        1 to listOf(SpawnWeight(EnemyKind.DRONE, 1, 100.0)),
        2 to listOf(SpawnWeight(EnemyKind.DRONE, 1, 90.0), SpawnWeight(EnemyKind.DRONE, 2, 10.0)),
        4 to listOf(SpawnWeight(EnemyKind.DRONE, 1, 80.0), SpawnWeight(EnemyKind.DRONE, 2, 20.0), SpawnWeight(EnemyKind.SHOOTER, 1, 10.0)),
        7 to listOf(SpawnWeight(EnemyKind.DRONE, 1, 50.0), SpawnWeight(EnemyKind.DRONE, 2, 50.0), SpawnWeight(EnemyKind.SHOOTER, 1, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 1, 15.0)),
        9 to listOf(SpawnWeight(EnemyKind.DRONE, 2, 90.0), SpawnWeight(EnemyKind.DRONE, 3, 10.0), SpawnWeight(EnemyKind.SHOOTER, 1, 15.0), SpawnWeight(EnemyKind.SHOOTER, 2, 5.0), SpawnWeight(EnemyKind.SPLITTER, 1, 5.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 1, 20.0)),
        11 to listOf(SpawnWeight(EnemyKind.DRONE, 2, 70.0), SpawnWeight(EnemyKind.DRONE, 3, 30.0), SpawnWeight(EnemyKind.SHOOTER, 1, 10.0), SpawnWeight(EnemyKind.SHOOTER, 2, 10.0), SpawnWeight(EnemyKind.SPLITTER, 1, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 1, 20.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 2, 10.0), SpawnWeight(EnemyKind.WARDER, 1, 10.0)),
        13 to listOf(SpawnWeight(EnemyKind.DRONE, 2, 30.0), SpawnWeight(EnemyKind.DRONE, 3, 70.0), SpawnWeight(EnemyKind.SHOOTER, 2, 20.0), SpawnWeight(EnemyKind.SPLITTER, 1, 8.0), SpawnWeight(EnemyKind.SPLITTER, 2, 2.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 1, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 2, 20.0), SpawnWeight(EnemyKind.WARDER, 1, 10.0)),
        16 to listOf(SpawnWeight(EnemyKind.DRONE, 3, 70.0), SpawnWeight(EnemyKind.DRONE, 4, 30.0), SpawnWeight(EnemyKind.SHOOTER, 2, 15.0), SpawnWeight(EnemyKind.SHOOTER, 3, 5.0), SpawnWeight(EnemyKind.SPLITTER, 1, 5.0), SpawnWeight(EnemyKind.SPLITTER, 2, 5.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 2, 30.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        19 to listOf(SpawnWeight(EnemyKind.DRONE, 3, 30.0), SpawnWeight(EnemyKind.DRONE, 4, 70.0), SpawnWeight(EnemyKind.SHOOTER, 2, 10.0), SpawnWeight(EnemyKind.SHOOTER, 3, 10.0), SpawnWeight(EnemyKind.SPLITTER, 1, 2.0), SpawnWeight(EnemyKind.SPLITTER, 2, 8.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 2, 20.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 3, 10.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        21 to listOf(SpawnWeight(EnemyKind.DRONE, 4, 70.0), SpawnWeight(EnemyKind.DRONE, 5, 30.0), SpawnWeight(EnemyKind.SHOOTER, 3, 20.0), SpawnWeight(EnemyKind.SPLITTER, 2, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 2, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 3, 20.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        24 to listOf(SpawnWeight(EnemyKind.DRONE, 4, 30.0), SpawnWeight(EnemyKind.DRONE, 5, 70.0), SpawnWeight(EnemyKind.SHOOTER, 3, 15.0), SpawnWeight(EnemyKind.SHOOTER, 4, 5.0), SpawnWeight(EnemyKind.SPLITTER, 2, 8.0), SpawnWeight(EnemyKind.SPLITTER, 3, 2.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 3, 30.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        27 to listOf(SpawnWeight(EnemyKind.DRONE, 5, 70.0), SpawnWeight(EnemyKind.DRONE, 6, 30.0), SpawnWeight(EnemyKind.SHOOTER, 3, 10.0), SpawnWeight(EnemyKind.SHOOTER, 4, 10.0), SpawnWeight(EnemyKind.SPLITTER, 2, 5.0), SpawnWeight(EnemyKind.SPLITTER, 3, 5.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 3, 20.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 4, 10.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        30 to listOf(SpawnWeight(EnemyKind.DRONE, 5, 30.0), SpawnWeight(EnemyKind.DRONE, 6, 70.0), SpawnWeight(EnemyKind.SHOOTER, 4, 20.0), SpawnWeight(EnemyKind.SPLITTER, 2, 2.0), SpawnWeight(EnemyKind.SPLITTER, 3, 8.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 3, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 4, 20.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        32 to listOf(SpawnWeight(EnemyKind.DRONE, 6, 70.0), SpawnWeight(EnemyKind.DRONE, 7, 30.0), SpawnWeight(EnemyKind.SHOOTER, 4, 15.0), SpawnWeight(EnemyKind.SHOOTER, 5, 5.0), SpawnWeight(EnemyKind.SPLITTER, 3, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 4, 30.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        34 to listOf(SpawnWeight(EnemyKind.DRONE, 6, 30.0), SpawnWeight(EnemyKind.DRONE, 7, 70.0), SpawnWeight(EnemyKind.SHOOTER, 4, 10.0), SpawnWeight(EnemyKind.SHOOTER, 5, 10.0), SpawnWeight(EnemyKind.SPLITTER, 3, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 4, 20.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 5, 10.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        36 to listOf(SpawnWeight(EnemyKind.DRONE, 7, 100.0), SpawnWeight(EnemyKind.SHOOTER, 4, 5.0), SpawnWeight(EnemyKind.SHOOTER, 5, 15.0), SpawnWeight(EnemyKind.SPLITTER, 3, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 4, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 5, 20.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0)),
        999999 to listOf(SpawnWeight(EnemyKind.DRONE, 7, 100.0), SpawnWeight(EnemyKind.SHOOTER, 5, 20.0), SpawnWeight(EnemyKind.SPLITTER, 3, 10.0), SpawnWeight(EnemyKind.SHIELD_DRONE, 5, 30.0), SpawnWeight(EnemyKind.WARDER, 1, 15.0))
        )
    }
}
