package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.*

sealed interface EnemyAction {
    data class FireMissile(val direction: Vec2) : EnemyAction
}

/** Damage passes through the shield into health; regeneration never heals health. */
class RegeneratingShield(maxHp: Int, override val maxShield: Int,
                         private val delaySeconds: Float = 0.5f,
                         private val recoveryPerSecond: Float = 120f) : Durability {
    private val health = HealthPool(maxHp)
    override val hp get() = health.hp
    override var shield = maxShield.toFloat(); private set
    private var delay = 0f
    override fun takeDamage(amount: Int) {
        if (amount <= 0) return
        delay = delaySeconds
        val absorbed = min(shield, amount.toFloat())
        shield -= absorbed
        health.takeDamage((amount - absorbed).roundToInt())
    }
    override fun advance(dt: Float) {
        val recoveryTime = (dt - delay).coerceAtLeast(0f)
        delay = (delay - dt).coerceAtLeast(0f)
        if (hp > 0) shield = min(maxShield.toFloat(), shield + recoveryPerSecond * recoveryTime)
    }
}

data class SplitterDefinition(val partCount: Int, val partHp: Int, val orbitRadius: Float,
                              val partRadius: Float) {
    init { require(partCount >= 2 && partHp > 0 && orbitRadius > 0f && partRadius > 0f) }
}

/** Parts stay individually damageable while the formation has one input target. */
class SplitterHealth(count: Int, partHp: Int) : Durability {
    private val parts = MutableList(count) { partHp }
    val partHealth: List<Int> get() = parts
    override val hp get() = parts.sum()
    val hasBrokenPart get() = parts.any { it == 0 }
    override fun takeDamage(amount: Int) = takeDamageAt(amount, 0)
    override fun takeDamageAt(amount: Int, part: Int) {
        if (part in parts.indices) parts[part] = (parts[part] - amount.coerceAtLeast(0)).coerceAtLeast(0)
    }
}

data class EnemyHitCircle(val part: Int, val position: Vec2, val previousPosition: Vec2, val radius: Float)

class ShooterBehavior(private val intervalSeconds: Float = 10f) : EnemyBehavior {
    private var cooldown: Float? = null
    private val actions = mutableListOf<EnemyAction>()
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        enemy.angle = atan2(playerPosition.y - enemy.position.y, playerPosition.x - enemy.position.x)
        if (!moveToDeployment(enemy, dt)) return
        var next = (cooldown ?: enemy.initialAttackDelay) - dt
        while (next <= 0f) {
            actions += EnemyAction.FireMissile((playerPosition - enemy.position).normalized())
            next += intervalSeconds
        }
        cooldown = next
    }
    override fun drainActions() = actions.toList().also { actions.clear() }
}

class MissileBehavior(private val preparationSeconds: Float = 7f) : EnemyBehavior {
    private var remaining = preparationSeconds
    override val activationProgress get() = (1f - remaining / preparationSeconds).coerceIn(0f, 1f)
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        val movingTime = (dt - remaining).coerceAtLeast(0f)
        remaining = (remaining - dt).coerceAtLeast(0f)
        enemy.angle += dt * 2f * PI.toFloat() / 3f
        if (movingTime > 0f) enemy.position += enemy.flightDirection * (enemy.definition.speed * movingTime)
    }
}

class WarderBehavior : EnemyBehavior {
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        moveToDeployment(enemy, dt)
        enemy.angle += dt * PI.toFloat() / 3f
    }
}

class SplitterBehavior : EnemyBehavior {
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        enemy.position += (playerPosition - enemy.position).normalized() * (enemy.definition.speed * dt)
        enemy.angle += dt * 1.2f
    }
}

class ReleasedShardBehavior : EnemyBehavior {
    private var age = 0f
    private var origin: Vec2? = null
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        val start = origin ?: enemy.position.also { origin = it }
        val previous = age
        age += dt
        if (previous < 0.5f) {
            val p = (age / 0.5f).coerceIn(0f, 1f)
            enemy.position = start + enemy.releaseOffset * (1f - (1f - p).pow(3))
        }
        if (age > 0.5f) {
            val active = min(dt, age - 0.5f)
            enemy.position += (playerPosition - enemy.position).normalized() * (enemy.definition.speed * active)
        }
        enemy.angle = atan2(playerPosition.y - enemy.position.y, playerPosition.x - enemy.position.x)
    }
}

class RotatingPursuit : EnemyBehavior {
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        enemy.position += (playerPosition - enemy.position).normalized() * (enemy.definition.speed * dt)
        enemy.angle += dt * PI.toFloat() / 3f
    }
}

private fun moveToDeployment(enemy: Enemy, dt: Float): Boolean {
    val destination = enemy.deploymentPosition ?: enemy.position
    val delta = destination - enemy.position
    val travel = enemy.definition.speed * dt
    if (delta.length() > travel) {
        enemy.position += delta.normalized() * travel
        return false
    }
    enemy.position = destination
    return true
}

/** An owned barrier, independent of the enemy's damageable body. */
class WarderBarrier(val owner: Enemy, val radius: Float = 25.1f,
                    val thickness: Float = 3.2f, val protectionRadius: Float = 192.9f) {
    var angle = 0f; private set
    val halfArc = PI.toFloat() / 3f // Requested 120 degrees, with square ends.
    val isAlive get() = owner.isAlive
    fun advance(dt: Float) { angle = (angle + dt * 0.6f) % (2f * PI.toFloat()) }
    fun hitFraction(projectile: Projectile): Float? = sweptArcHit(
        projectile.previousPosition - owner.previousPosition, projectile.position - owner.position,
        radius, thickness / 2f + projectile.radius, angle, halfArc)
}

/** Earliest segment contact with an arc band, including its two end caps. */
fun sweptArcHit(start: Vec2, end: Vec2, radius: Float, padding: Float,
               angle: Float, halfArc: Float): Float? {
    fun inside(p: Vec2): Boolean {
        val theta = atan2(p.y, p.x) - angle
        return abs(atan2(sin(theta), cos(theta))) <= halfArc + 0.0001f && abs(p.length() - radius) <= padding + 0.0001f
    }
    if (inside(start)) return 0f
    val candidates = mutableListOf<Float>()
    val delta = end - start
    val a = delta.dot(delta)
    if (a > 0.000001f) for (r in listOf(radius + padding, (radius - padding).coerceAtLeast(0f))) {
        val b = 2f * start.dot(delta)
        val c = start.dot(start) - r * r
        val disc = b * b - 4f * a * c
        if (disc >= 0f) for (t in listOf((-b - sqrt(disc)) / (2f * a), (-b + sqrt(disc)) / (2f * a))) {
            if (t in 0f..1f && inside(start + delta * t)) candidates += t
        }
    }
    for (theta in listOf(angle - halfArc, angle + halfArc)) {
        val cap = Vec2(cos(theta), sin(theta)) * radius
        sweptCircleHit(start - cap, end - cap, padding)?.let(candidates::add)
    }
    return candidates.minOrNull()
}
