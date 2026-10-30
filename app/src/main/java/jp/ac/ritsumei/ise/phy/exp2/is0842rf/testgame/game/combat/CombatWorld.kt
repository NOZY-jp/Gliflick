package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.random.Random
import kotlin.math.atan2
import kotlin.math.roundToInt

data class ProgressionDefinition(val killExperience: Int = 10, val firstLevelExperience: Int = 50,
                                 val experienceIncreasePerLevel: Int = 20) {
    init { require(killExperience >= 0 && firstLevelExperience > 0 && experienceIncreasePerLevel >= 0) }
}
data class CombatConfig(val spawningEnabled: Boolean = true, val maxEnemiesAlive: Int = 16,
                        val playerMaxHp: Int = 300,
                        val progression: ProgressionDefinition = ProgressionDefinition()) {
    init { require(maxEnemiesAlive > 0 && playerMaxHp > 0) }
}

fun interface EnemySpawnPolicy {
    fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random): EnemyDefinition
    fun onBossDefeated(bossesDefeated: Int) = Unit
}
class FixedEnemySpawnPolicy(private val definition: EnemyDefinition = EnemyDefinition()) : EnemySpawnPolicy {
    override fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random) = definition
}

sealed interface CombatEvent {
    data class Spawned(val enemy: Enemy) : CombatEvent
    data class Died(val enemyId: Long, val position: Vec2) : CombatEvent
}

/** Single-threaded domain simulation. Rendering and keyboard transport do not live here. */
class CombatWorld(
    val config: CombatConfig = CombatConfig(),
    private val random: Random = Random.Default,
    private val spawnPolicy: EnemySpawnPolicy = DroneEnemySpawnPolicy(),
    val mainWeapon: MainWeapon = Repeater(),
    private val spawnDensity: EnemySpawnDensity = RandomEnemySpawnDensity()
) {
    val player = Player(config.playerMaxHp)
    private val mutableEnemies = mutableListOf<Enemy>()
    private val mutableProjectiles = mutableListOf<Projectile>()
    private val mutableBursts = mutableListOf<DeathBurst>()
    private val mutableHitFeedback = mutableListOf<HitFeedback>()
    val enemies: List<Enemy> get() = mutableEnemies
    val projectiles: List<Projectile> get() = mutableProjectiles
    val deathBursts: List<DeathBurst> get() = mutableBursts
    val hitFeedback: List<HitFeedback> get() = mutableHitFeedback
    private val events = mutableListOf<CombatEvent>()
    private var nextEnemyId = 1L
    var width = 0f; private set
    var height = 0f; private set
    var elapsedSeconds = 0.0; private set
    var level = 1; private set
    var experience = 0; private set
    var kills = 0; private set
    var bossesDefeated = 0; private set
    /** Placeholder until the public Threat formula and its mapping to spawns are known. */
    var threat: Threat = Threat.NONE
        private set
    val nextLevelExperience get() = config.progression.firstLevelExperience +
        (level - 1) * config.progression.experienceIncreasePerLevel
    val experienceFraction get() = experience.toFloat() / nextLevelExperience
    val isGameOver get() = player.hp == 0

    fun resize(newWidth: Float, newHeight: Float) {
        if (newWidth <= 0f || newHeight <= 0f) return
        val first = width == 0f
        if (!first) {
            val sx = newWidth / width
            val sy = newHeight / height
            mutableEnemies.forEach {
                it.position = it.position.scaled(sx, sy)
                it.previousPosition = it.position
                it.spawnOrigin = it.spawnOrigin.scaled(sx, sy)
            }
            mutableProjectiles.forEach {
                it.position = it.position.scaled(sx, sy)
                it.previousPosition = it.position
                it.velocity = it.velocity.scaled(sx, sy).normalized() * it.velocity.length()
            }
            mutableBursts.forEach { it.origin = it.origin.scaled(sx, sy) }
            mutableHitFeedback.forEach { it.resize(sx, sy) }
            mainWeapon.resize(sx, sy)
        }
        width = newWidth
        height = newHeight
        player.position = Vec2(width / 2f, height / 2f)
    }

    fun spawnEnemy(position: Vec2, definition: EnemyDefinition = spawnPolicy.choose(elapsedSeconds, level, random)): Enemy =
        Enemy(nextEnemyId++, definition, position).also {
        mutableEnemies += it
        events += CombatEvent.Spawned(it)
    }

    fun target(enemyId: Long) {
        mutableEnemies.firstOrNull { it.id == enemyId && it.isAlive }?.let { mainWeapon.target(it.id, it.position) }
    }

    fun damageEnemy(enemyId: Long, damage: Int, incomingAngle: Float? = null) {
        mutableEnemies.firstOrNull { it.id == enemyId && it.isAlive }?.let {
            val applied = it.takeDamage(damage)
            if (applied > 0) {
                val angle = incomingAngle ?: atan2(it.position.y - player.position.y, it.position.x - player.position.x)
                mutableHitFeedback += HitFeedback(it.position, applied, it.radius, angle, random)
            }
            if (!it.isAlive) die(it, awardExperience = true)
        }
    }

    fun drainEvents(): List<CombatEvent> = events.toList().also { events.clear() }

    /** Unlocks the next Drone tier after a future boss system reports a defeat. */
    fun notifyBossDefeated() {
        bossesDefeated++
        spawnPolicy.onBossDefeated(bossesDefeated)
    }

    fun update(dt: Float, weaponModifiers: WeaponRuntimeModifiers = WeaponRuntimeModifiers()) {
        require(dt >= 0f && dt.isFinite())
        if (width == 0f) return
        mutableBursts.forEach { it.advance(dt) }
        mutableBursts.removeAll { it.isFinished }
        mutableHitFeedback.forEach { it.advance(dt) }
        mutableHitFeedback.removeAll { it.isFinished }
        mutableEnemies.forEach { enemy -> enemy.deathAge?.let { enemy.deathAge = it + dt } }
        mutableEnemies.removeAll { (it.deathAge ?: 0f) >= DEATH_FADE_SECONDS }
        if (isGameOver) return
        elapsedSeconds += dt
        mutableEnemies.filter { it.isAlive }.forEach { enemy ->
            enemy.advance(dt, player.position)
            if ((enemy.position - player.position).length() <= enemy.radius + player.radius) {
                player.takeDamage(enemy.definition.contactDamage)
                die(enemy, awardExperience = false)
            }
        }
        val actions = mainWeapon.update(dt, player.position, { id ->
            mutableEnemies.firstOrNull { it.id == id && it.isAlive }?.position
        }, weaponModifiers)
        actions.forEach { action -> when (action) {
            is WeaponAction.LaunchProjectile -> mutableProjectiles += action.projectile
        } }
        val spent = mutableSetOf<Projectile>()
        mutableProjectiles.forEach { projectile ->
            projectile.advance(dt)
            val hit = mutableEnemies.asSequence().filter { it.isAlive }.mapNotNull { enemy ->
                sweptCircleHit(projectile.previousPosition - enemy.previousPosition,
                    projectile.position - enemy.position, projectile.radius + enemy.radius)
                    ?.let { enemy to it }
            }.minByOrNull { it.second }
            if (hit != null) {
                val impactPosition = projectile.previousPosition +
                    (projectile.position - projectile.previousPosition) * hit.second
                projectile.position = impactPosition
                val enemyPositionAtImpact = hit.first.previousPosition +
                    (hit.first.position - hit.first.previousPosition) * hit.second
                val isCritical = projectile.criticalChance > 0f && random.nextFloat() < projectile.criticalChance
                val shotDamage = if (isCritical)
                    (projectile.damage * projectile.criticalDamageMultiplier).roundToInt()
                else projectile.damage
                val applied = hit.first.takeDamage(shotDamage)
                if (applied > 0) {
                    val incomingAngle = atan2(projectile.velocity.y, projectile.velocity.x)
                    mutableHitFeedback += HitFeedback(enemyPositionAtImpact, applied,
                        hit.first.radius, incomingAngle, random)
                }
                if (!hit.first.isAlive) die(hit.first, awardExperience = true)
                spent += projectile
            } else if (projectile.position.x !in -32f..width + 32f ||
                projectile.position.y !in -32f..height + 32f) spent += projectile
        }
        mutableProjectiles.removeAll(spent)
        val livingCount = mutableEnemies.count { it.isAlive }
        if (config.spawningEnabled && livingCount < config.maxEnemiesAlive &&
            spawnDensity.shouldSpawn(dt, elapsedSeconds, level, random)) {
            val definition = spawnPolicy.choose(elapsedSeconds, level, random)
            spawnEnemy(spawnPosition(definition.radius * definition.size), definition)
        }
    }

    private fun die(enemy: Enemy, awardExperience: Boolean) {
        if (enemy.deathAge != null) return
        enemy.takeDamage(Int.MAX_VALUE)
        enemy.deathAge = 0f
        mainWeapon.targetDied(enemy.id, enemy.position)
        mutableBursts += DeathBurst(enemy.position, random)
        events += CombatEvent.Died(enemy.id, enemy.position)
        if (awardExperience) {
            kills++
            experience += config.progression.killExperience
            while (experience >= nextLevelExperience) { experience -= nextLevelExperience; level++ }
        }
    }

    private fun spawnPosition(radius: Float): Vec2 {
        val margin = radius + 12f
        fun across(limit: Float): Float {
            val low = margin.coerceAtMost(limit / 2f)
            val high = (limit - margin).coerceAtLeast(low)
            return if (high <= low) limit / 2f else low + random.nextFloat() * (high - low)
        }
        fun candidate(): Vec2 = when (random.nextInt(4)) {
            0 -> Vec2(across(width), -margin)
            1 -> Vec2(width + margin, across(height))
            2 -> Vec2(across(width), height + margin)
            else -> Vec2(-margin, across(height))
        }
        return List(32) { candidate() }.maxBy { point ->
            minOf((point - player.position).length(),
                mutableEnemies.filter { it.isAlive }
                    .minOfOrNull { (point - it.position).length() } ?: Float.MAX_VALUE)
        }
    }

    companion object { const val DEATH_FADE_SECONDS = 0.14f }
}
