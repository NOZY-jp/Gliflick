package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.random.Random

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vec2(x * scale, y * scale)
    fun dot(other: Vec2) = x * other.x + y * other.y
    fun length() = sqrt(dot(this))
    fun normalized(): Vec2 = length().let { if (it < 0.0001f) ZERO else this * (1f / it) }
    fun scaled(xScale: Float, yScale: Float) = Vec2(x * xScale, y * yScale)
    companion object { val ZERO = Vec2(0f, 0f) }
}

enum class EnemyKind { DRONE }
enum class EnemyVariant { NORMAL }
enum class EnemyShape(val sides: Int) {
    CIRCLE(0), TRIANGLE(3), SQUARE(4), PENTAGON(5), HEXAGON(6), HEPTAGON(7), OCTAGON(8)
}

/** Circular Drone marks reproduced from the official Wiki's seven tier images. */
enum class EnemyMarkerPattern {
    DOT,
    RINGED_DOT,
    DOUBLE_RINGED_DOT,
    BULLSEYE,
    CENTER_HOLE,
    SEGMENTED_CENTER_HOLE,
    RINGED_SEGMENTED_CENTER_HOLE
}

/** Reusable stat modifier attached to an individual enemy definition. */
data class EnemyMutation(
    val id: String,
    val maxHpMultiplier: Float = 1f,
    val speedMultiplier: Float = 1f,
    val sizeMultiplier: Float = 1f
) {
    init {
        require(id.isNotBlank())
        require(maxHpMultiplier > 0f && speedMultiplier > 0f && sizeMultiplier > 0f)
    }

    companion object {
        val NONE = EnemyMutation("normal")
        val ENLARGED = EnemyMutation("enlarged", maxHpMultiplier = 3f, sizeMultiplier = 1.5f)
        val HASTENED = EnemyMutation("hastened", speedMultiplier = 2f)
    }
}

/** Reserved difficulty value. Its progression and gameplay effect are intentionally undefined. */
data class Threat(val value: Double) {
    init { require(value >= 0.0 && value.isFinite()) }
    companion object { val NONE = Threat(0.0) }
}

/** Fixed balance belongs to a definition; health and movement belong to each instance. */
data class EnemyDefinition(
    val kind: EnemyKind = EnemyKind.DRONE,
    val variant: EnemyVariant = EnemyVariant.NORMAL,
    val shape: EnemyShape = EnemyShape.CIRCLE,
    val markerPattern: EnemyMarkerPattern = EnemyMarkerPattern.DOT,
    val maxHp: Int = 80,
    val speed: Float = 5.95f,
    val contactDamage: Int = 40,
    val radius: Float = 4.5f,
    val size: Float = 1f,
    val behaviorFactory: () -> EnemyBehavior = { PursuePlayer() },
    val durabilityFactory: (Int) -> Durability = { HealthPool(it) },
    val tier: Int = 1,
    val mutation: EnemyMutation = EnemyMutation.NONE
) {
    init { require(maxHp > 0 && speed >= 0f && contactDamage >= 0 && radius > 0f && size > 0f && tier > 0) }
}

/** Shielding or damage immunity can decorate this contract without changing collision resolution. */
interface Durability {
    val hp: Int
    fun takeDamage(amount: Int)
}

class HealthPool(maxHp: Int) : Durability {
    init { require(maxHp > 0) }
    override var hp = maxHp
        private set
    override fun takeDamage(amount: Int) { hp = (hp - amount.coerceAtLeast(0)).coerceAtLeast(0) }
}

/** Per-instance behavior can keep its own timers; definitions never share mutable behavior state. */
fun interface EnemyBehavior {
    fun update(enemy: Enemy, dt: Float, playerPosition: Vec2)
}

class PursuePlayer : EnemyBehavior {
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        enemy.position += (playerPosition - enemy.position).normalized() * (enemy.definition.speed * dt)
    }
}

class Enemy(val id: Long, val definition: EnemyDefinition, var position: Vec2) {
    /** Numeric per-instance scale applied consistently to collision and presentation. */
    val size = definition.size
    val radius get() = definition.radius * size
    var spawnOrigin = position
        internal set
    private val behavior = definition.behaviorFactory()
    private val durability = definition.durabilityFactory(definition.maxHp)
    var previousPosition = position
    val hp get() = durability.hp
    var deathAge: Float? = null
        internal set
    val isAlive get() = hp > 0 && deathAge == null
    var frozenSeconds = 0f
    val statuses = mutableListOf<EnemyStatus>()
    fun advance(dt: Float, destination: Vec2) {
        previousPosition = position
        if (frozenSeconds > 0f) { frozenSeconds = (frozenSeconds - dt).coerceAtLeast(0f); return }
        val slow = if (statuses.any { it.type == DamageType.CHEMICAL && it.remaining > 0f }) 0.6f else 1f
        if (isAlive) behavior.update(this, dt * slow, destination)
    }
    fun takeDamage(damage: Int): Int {
        val hpBefore = hp
        durability.takeDamage(damage)
        return (hpBefore - hp).coerceAtLeast(0)
    }
}

class Player(val maxHp: Int = 300, val radius: Float = 11f) {
    var position = Vec2.ZERO
    var hp = maxHp
        private set
    fun takeDamage(damage: Int) { hp = (hp - damage.coerceAtLeast(0)).coerceAtLeast(0) }
}

class Projectile(
    var position: Vec2,
    var velocity: Vec2,
    var damage: Int = 35,
    val radius: Float = 1.25f,
    val criticalChance: Float = 0f,
    val criticalDamageMultiplier: Float = 1.5f,
    val source: WeaponId = WeaponId.REPEATER,
    val damageType: DamageType = DamageType.KINETIC,
    val explosionRadius: Float = 0f,
    var ricochets: Int = 0,
    val freezeChance: Float = 0f
) {
    init {
        require(damage >= 0 && radius > 0f)
        require(criticalChance in 0f..1f && criticalDamageMultiplier >= 1f)
    }
    var previousPosition = position
    val hitEnemyIds = mutableSetOf<Long>()
    var hasRicocheted = false
    var age = 0f
        private set
    fun advance(dt: Float) {
        previousPosition = position
        position += velocity * dt
        age += dt
    }
}

data class ProjectileDefinition(
    val damage: Int = 35,
    val speed: Float = 520f,
    val radius: Float = 1.25f
) {
    init { require(damage >= 0 && speed > 0f && radius > 0f) }
}
data class RepeaterDefinition(val burstCount: Int = 3, val shotInterval: Float = 0.12f,
                              val burstInterval: Float = 0.16f, val projectile: ProjectileDefinition = ProjectileDefinition(),
                              val criticalChance: Float = 0.05f, val criticalDamageMultiplier: Float = 1.5f) {
    init {
        require(burstCount > 0 && shotInterval > 0f && burstInterval > 0f)
        require(criticalChance in 0f..1f && criticalDamageMultiplier >= 1f)
    }
}

sealed interface WeaponAction {
    data class LaunchProjectile(val projectile: Projectile, val startsMainWeaponFire: Boolean = true) : WeaponAction
}

/** Shared runtime modifiers let each weapon decide how its own firing pattern responds. */
data class WeaponRuntimeModifiers(
    val criticalChanceBonus: Float = 0f,
    val rateOfFireMultiplier: Float = 1f
) {
    init { require(criticalChanceBonus >= 0f && criticalChanceBonus.isFinite() &&
        rateOfFireMultiplier > 0f && rateOfFireMultiplier.isFinite()) }
}

/** Input and simulation depend on this contract, never a concrete weapon's firing algorithm. */
interface MainWeapon {
    val displayName: String
    fun target(enemyId: Long, position: Vec2)
    fun targetDied(enemyId: Long, position: Vec2)
    fun resize(xScale: Float, yScale: Float)
    fun update(dt: Float, origin: Vec2, resolve: (Long) -> Vec2?): List<WeaponAction>
    fun update(dt: Float, origin: Vec2, resolve: (Long) -> Vec2?, modifiers: WeaponRuntimeModifiers): List<WeaponAction> =
        update(dt, origin, resolve)
}

/** Scheduling survives target death; aim is frozen separately for every emitted projectile. */
class Repeater(val definition: RepeaterDefinition = RepeaterDefinition(),
               val upgrades: WeaponRunState = WeaponRunState(WeaponId.REPEATER)) : MainWeapon {
    override val displayName = "リピーター"
    private data class Burst(val enemyId: Long, var lastAim: Vec2, var remaining: Int = 3, val total: Int = remaining)
    private val bursts = ArrayDeque<Burst>()
    private var cooldown = 0f
    val pendingShots get() = bursts.sumOf { it.remaining }
    override fun target(enemyId: Long, position: Vec2) {
        bursts.addLast(Burst(enemyId, position, definition.burstCount + 5 * upgrades.rank(RepeaterEvolution.SHOTGUN)))
    }
    override fun targetDied(enemyId: Long, position: Vec2) {
        bursts.filter { it.enemyId == enemyId }.forEach { it.lastAim = position }
    }
    override fun resize(xScale: Float, yScale: Float) {
        bursts.forEach { it.lastAim = it.lastAim.scaled(xScale, yScale) }
    }
    override fun update(dt: Float, origin: Vec2, resolve: (Long) -> Vec2?): List<WeaponAction> =
        update(dt, origin, resolve, WeaponRuntimeModifiers())

    override fun update(
        dt: Float,
        origin: Vec2,
        resolve: (Long) -> Vec2?,
        modifiers: WeaponRuntimeModifiers
    ): List<WeaponAction> {
        cooldown -= dt
        val emitted = mutableListOf<WeaponAction>()
        while (bursts.isNotEmpty() && cooldown <= 0f) {
            val burst = bursts.first()
            resolve(burst.enemyId)?.let { burst.lastAim = it }
            val aim = burst.lastAim - origin
            val shotgun = upgrades.rank(RepeaterEvolution.SHOTGUN)
            val spread = if (shotgun == 0) 0f else if (shotgun == 1) 0.44f else 0.88f
            val shotIndex = burst.total - burst.remaining
            val offset = if (burst.total <= 1) 0f else (shotIndex.toFloat() / (burst.total - 1) - 0.5f) * spread
            val angle = kotlin.math.atan2(aim.y, aim.x) + offset
            val direction = Vec2(cos(angle), sin(angle))
            val bullet = definition.projectile
            val frost = upgrades.rank(RepeaterEvolution.FROST_FANG)
            val explosion = upgrades.rank(RepeaterEvolution.EXPLODING_BULLETS)
            val baseDamage = bullet.damage - (if (shotgun > 0) 10 else 0) +
                (if (frost == 0) 0 else if (frost == 1) 10 else 20) +
                (if (explosion < 2) 0 else 5)
            emitted += WeaponAction.LaunchProjectile(Projectile(
                position = origin,
                velocity = direction * bullet.speed,
                damage = (baseDamage * upgrades.scale(WeaponStat.DAMAGE)).roundToInt().coerceAtLeast(1),
                radius = bullet.radius,
                criticalChance = (definition.criticalChance + upgrades.bonus(WeaponStat.CRIT) +
                    modifiers.criticalChanceBonus).coerceAtMost(1f),
                criticalDamageMultiplier = definition.criticalDamageMultiplier + upgrades.bonus(WeaponStat.CRIT_MULTIPLIER),
                damageType = if (explosion > 0) DamageType.EXPLOSIVE else DamageType.KINETIC,
                explosionRadius = if (explosion > 0) (if (explosion == 1) 15f else 20f) * upgrades.scale(WeaponStat.RANGE) else 0f,
                ricochets = when (upgrades.rank(RepeaterEvolution.RICOCHET)) { 1 -> 3; 2 -> 5; else -> 0 },
                freezeChance = if (frost > 0) 0.03f else 0f
            ), startsMainWeaponFire = shotIndex == 0)
            burst.remaining--
            if (burst.remaining == 0) bursts.removeFirst()
            val interval = if (burst.remaining == 0) definition.burstInterval else definition.shotInterval
            cooldown += interval / modifiers.rateOfFireMultiplier
        }
        if (bursts.isEmpty()) cooldown = cooldown.coerceAtLeast(0f)
        return emitted
    }
}

data class DeathParticle(val velocity: Vec2, val size: Float, val lifetime: Float, val angle: Float) {
    fun sizeAt(age: Float): Float = if (age >= lifetime) 0f else
        size * sin(PI.toFloat() * (age / lifetime).coerceIn(0f, 1f))
}

class DeathBurst(var origin: Vec2, random: Random) {
    var age = 0f
        private set
    val particles = List(10) {
        val angle = random.nextFloat() * PI.toFloat() * 2f
        DeathParticle(Vec2(cos(angle), sin(angle)) * (28f + random.nextFloat() * 65f),
            1.5f + random.nextFloat() * 3.5f, 0.36f + random.nextFloat() * 0.24f, angle)
    }
    val isFinished get() = particles.all { age >= it.lifetime }
    fun advance(dt: Float) { age += dt }
}

data class DamageFragment(
    val travelAngle: Float,
    val rotation: Float,
    val initialLength: Float,
    val thickness: Float,
    val speed: Float,
    val lifetime: Float
) {
    fun progressAt(age: Float) = (age / lifetime).coerceIn(0f, 1f)
    fun lengthAt(age: Float) = initialLength * (1f - progressAt(age))
    fun positionAt(origin: Vec2, age: Float): Vec2 {
        val travelTime = age.coerceIn(0f, lifetime)
        return origin + Vec2(cos(travelAngle), sin(travelAngle)) * (speed * travelTime)
    }
}

/** Presentation state for a single projectile impact; all animation timing stays in simulation time. */
class HitFeedback(
    var origin: Vec2,
    val damage: Int,
    var enemyRadius: Float,
    incomingAngle: Float,
    random: Random
) {
    var age = 0f
        private set

    val numberProgress get() = (age / DAMAGE_NUMBER_LIFETIME).coerceIn(0f, 1f)
    val numberAlpha get() = 0.75f * (1f - numberProgress)
    val numberScale get() = 1f - 0.5f * numberProgress
    val numberBaseline get() = origin.y - enemyRadius - 12f - 18f * numberProgress
    val ringProgress get() = (age / RING_LIFETIME).coerceIn(0f, 1f)
    val ringRadius get() = enemyRadius * (1f + 0.2f * ringProgress)
    val ringStrokeWidth get() = enemyRadius * 0.3f * (1f - ringProgress)
    val isFinished get() = age >= DAMAGE_NUMBER_LIFETIME

    val fragments = List(FRAGMENT_COUNT) {
        val spread = (random.nextFloat() * 2f - 1f) * MAX_SPREAD_RADIANS
        val travelAngle = incomingAngle + spread
        DamageFragment(
            travelAngle = travelAngle,
            rotation = travelAngle,
            initialLength = enemyRadius * (1.2f + random.nextFloat() * 1.4f),
            thickness = (enemyRadius * 0.28f).coerceAtLeast(1f),
            speed = 25f + random.nextFloat() * 45f,
            lifetime = (0.25f + random.nextFloat() * 0.16f) * 1.5f
        )
    }

    fun advance(dt: Float) { age += dt }

    fun resize(xScale: Float, yScale: Float) {
        origin = origin.scaled(xScale, yScale)
        enemyRadius *= kotlin.math.sqrt(kotlin.math.abs(xScale * yScale))
    }

    companion object {
        const val FRAGMENT_COUNT = 10
        const val DAMAGE_NUMBER_LIFETIME = 0.64f
        const val RING_LIFETIME = 0.24f
        private const val MAX_SPREAD_RADIANS = PI.toFloat() / 3f
    }
}

/** First contact in relative-motion space, including a segment starting inside the circle. */
fun sweptCircleHit(start: Vec2, end: Vec2, radius: Float): Float? {
    val c = start.dot(start) - radius * radius
    if (c <= 0f) return 0f
    val delta = end - start
    val a = delta.dot(delta)
    if (a < 0.000001f) return null
    val b = 2f * start.dot(delta)
    val discriminant = b * b - 4f * a * c
    if (discriminant < 0f) return null
    val t = (-b - sqrt(discriminant)) / (2f * a)
    return t.takeIf { it in 0f..1f }
}
