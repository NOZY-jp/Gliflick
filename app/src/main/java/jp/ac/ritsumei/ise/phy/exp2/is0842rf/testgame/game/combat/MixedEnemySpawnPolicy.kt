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

/** Source table's requested species, interpolated over 60s instead of sudden introductions. */
class MixedEnemySpawnPolicy(private val drones: DroneEnemySpawnPolicy = DroneEnemySpawnPolicy(),
                           private val populationLimit: Int = 16) : EnemySpawnPolicy {
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
    fun distributionAt(seconds: Double, level: Int, enemies: List<Enemy> = emptyList()): List<SpawnWeight> {
        val difficulty = EnemySpawnDifficulty.at(seconds, level)
        val minute = difficulty.frames / 3600.0
        val index = rows.indexOfFirst { minute <= it.first }.let { if (it < 0) rows.lastIndex else it }
        val before = if (index == 0) rows[0].second else rows[index - 1].second
        val after = rows[index].second
        val start = if (index == 0) 0.0 else rows[index - 1].first.toDouble()
        val raw = (minute - start).coerceIn(0.0, 1.0)
        val blend = raw * raw * (3.0 - 2.0 * raw)
        val alive = enemies.filter { it.isAlive }
        val specialCount = alive.count { it.definition.kind in specials }
        val eliteCount = alive.count { it.definition.kind == EnemyKind.WARDER }
        val remaining = arrivalCapacity(alive, populationLimit)
        return (before + after).map { it.kind to it.tier }.distinct().map { (kind, tier) ->
            val from = before.filter { it.kind == kind && it.tier == tier }.sumOf { it.weight }
            val to = after.filter { it.kind == kind && it.tier == tier }.sumOf { it.weight }
            SpawnWeight(kind, tier, from + (to - from) * blend)
        }.filter {
            it.weight > 0.0 && (it.kind !in specials || specialCount < difficulty.specialCap) &&
                (it.kind != EnemyKind.WARDER || eliteCount < difficulty.eliteCap) &&
                (if (it.kind == EnemyKind.SPLITTER) it.tier + 3 else 1) <= remaining
        }
    }
    companion object {
        private val specials = setOf(EnemyKind.SHOOTER, EnemyKind.SPLITTER)
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
