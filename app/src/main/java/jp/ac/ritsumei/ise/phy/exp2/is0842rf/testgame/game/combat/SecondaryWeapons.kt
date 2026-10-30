package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import kotlin.math.*

data class CompletedCombatWord(val word: WordDefinition, val targetId: Long, val position: Vec2) {
    /** Dictionary spelling is a gameplay condition, never reconstructed input or matcher input. */
    val glyphs = TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters).lowercase()
    fun containsCount(letters: String) = glyphs.count { it in letters }
    fun startsWith(letters: String) = glyphs.firstOrNull()?.let { it in letters } ?: false
}

enum class WeaponVisualKind { DRONE, SENTRY, TESLA, BLADE, ORB, MINE, BOMBARDMENT, GUARDIAN }
enum class PickupKind(val title: String) { METEOR("爆撃照準"), FUEL("燃料") }
class CombatPickup(val id: Long, val kind: PickupKind, var position: Vec2, val lifetime: Float = 14f) {
    var age = 0f
    val remainingFraction get() = (1f - age / lifetime).coerceIn(0f, 1f)
}
class WeaponEffect(val start: Vec2, val end: Vec2, val type: DamageType,
                   val radius: Float = 0f, val lifetime: Float = 0.13f) {
    var age = 0f
    val alpha get() = (1f - age / lifetime).coerceIn(0f, 1f)
}

/** Every secondary has its own activation/controller; the world owns damage and entity lifetimes. */
interface SecondaryWeapon {
    val id: WeaponId
    val state: WeaponRunState
    fun equip(world: CombatWorld) = Unit
    fun onWord(world: CombatWorld, event: CompletedCombatWord) = Unit
    fun update(world: CombatWorld, dt: Float) = Unit
    fun activatePickup(world: CombatWorld, pickup: CombatPickup) = Unit
}

abstract class BaseSecondary(override val id: WeaponId) : SecondaryWeapon {
    final override val state = WeaponRunState(id)
    fun scale(stat: WeaponStat) = state.scale(stat)
    fun deployment(world: CombatWorld, event: CompletedCombatWord): Vec2 =
        world.clampPoint(world.player.position + (event.position - world.player.position) * 0.65f, 24f)
}

abstract class WeaponActor(
    val state: WeaponRunState, var position: Vec2, val visual: WeaponVisualKind,
    val type: DamageType, var remaining: Float, val initialDuration: Float,
    val baseDamage: Float, val baseRange: Float
) {
    var angle = 0f
    var beamEnd: Vec2? = null
    var active = true
    var finished = false
    val source get() = state.id
    val range get() = baseRange * state.scale(WeaponStat.RANGE)
    val damage get() = (baseDamage * state.scale(WeaponStat.DAMAGE)).roundToInt().coerceAtLeast(1)
    val durationFraction get() = (remaining / initialDuration).coerceIn(0f, 1f)
    protected val hitTimes = mutableMapOf<Long, Double>()
    abstract fun update(world: CombatWorld, dt: Float)
    open fun resize(sx: Float, sy: Float) { position = position.scaled(sx, sy); beamEnd = null }
    protected fun hit(world: CombatWorld, enemy: Enemy, interval: Float, allowCrit: Boolean = true) {
        if (world.elapsedSeconds >= (hitTimes[enemy.id] ?: -1.0)) {
            world.dealDamage(enemy.id, damage, source, type,
                if (allowCrit) 0.01f + state.bonus(WeaponStat.CRIT) + world.currentWeaponModifiers.criticalChanceBonus else 0f,
                2f + state.bonus(WeaponStat.CRIT_MULTIPLIER))
            hitTimes[enemy.id] = world.elapsedSeconds + interval /
                (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
        }
    }
    protected fun advanceLifetime(dt: Float) {
        remaining -= dt
        if (remaining <= 0f) finished = true
    }
}

class DroneActor(state: WeaponRunState, position: Vec2, private val ranged: Boolean,
                 private val guardian: Boolean = false) : WeaponActor(
    state, position, if (guardian) WeaponVisualKind.GUARDIAN else WeaponVisualKind.DRONE,
    if (guardian) DamageType.HEAT else if (ranged) DamageType.ELECTRIC else DamageType.KINETIC,
    (if (guardian) 30f else 7f) * state.scale(WeaponStat.DURATION),
    (if (guardian) 30f else 7f) * state.scale(WeaponStat.DURATION),
    if (guardian) 20f else if (ranged) 10f else 50f, if (ranged) 70f else 5f
) {
    private var fireCooldown = 0f
    override fun update(world: CombatWorld, dt: Float) {
        beamEnd = null
        if (guardian) {
            remaining = (remaining - dt).coerceAtLeast(0f)
            active = remaining > 0f
        } else advanceLifetime(dt)
        val target = world.nearestEnemy(position)
        if (!active || target == null) {
            if (guardian) position += (world.player.position + Vec2(20f, -20f) - position) * min(1f, dt * 2f)
            return
        }
        val delta = target.position - position
        angle = atan2(delta.y, delta.x)
        val stopAt = if (ranged) range * 0.6f else target.radius + 2f
        if (delta.length() > stopAt)
            position += delta.normalized() * (if (guardian) 48f else 75f) * state.scale(WeaponStat.SPEED) * dt
        if (ranged) {
            fireCooldown -= dt
            if ((target.position - position).length() <= range && fireCooldown <= 0f) {
                world.addEffect(WeaponEffect(position, target.position, type))
                world.dealDamage(target.id, damage, source, type,
                    if (guardian) 0.01f + world.currentWeaponModifiers.criticalChanceBonus else 0f, 2f)
                beamEnd = target.position
                fireCooldown += (if (guardian) 0.45f else 0.3f) /
                    (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
            }
        } else if ((target.position - position).length() <= target.radius + 5f) hit(world, target, 0.55f)
    }
    fun refuel() { remaining = (remaining + 20f * state.scale(WeaponStat.DURATION)).coerceAtMost(60f * state.scale(WeaponStat.DURATION)) }
}

class SentryActor(state: WeaponRunState, position: Vec2) : WeaponActor(
    state, position, WeaponVisualKind.SENTRY, DamageType.HEAT, Float.MAX_VALUE,
    Float.MAX_VALUE, 30f, 80f
) {
    private var cooldown = 0f
    override fun update(world: CombatWorld, dt: Float) {
        position = world.player.position
        cooldown -= dt
        val target = world.nearestEnemy(position, range) ?: return
        val delta = target.position - position
        val desired = atan2(delta.y, delta.x)
        val difference = atan2(sin(desired - angle), cos(desired - angle))
        angle += difference.coerceIn(-6f * dt, 6f * dt)
        if (abs(difference) < 0.24f && cooldown <= 0f) {
            world.launch(Projectile(position, Vec2(cos(angle), sin(angle)) * 410f, damage,
                source = source, damageType = type, criticalChance = 0.01f + world.currentWeaponModifiers.criticalChanceBonus,
                criticalDamageMultiplier = 2f))
            cooldown += (60f / 86f) / (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
        }
    }
}

class FieldActor(state: WeaponRunState, position: Vec2, kind: WeaponVisualKind,
                 duration: Float, damage: Float, radius: Float, type: DamageType) : WeaponActor(
    state, position, kind, type, duration * state.scale(WeaponStat.DURATION),
    duration * state.scale(WeaponStat.DURATION), damage, radius
) {
    private var cooldown = 0f
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        angle += dt * 4f * state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier
        if (visual == WeaponVisualKind.BLADE) {
            world.enemies.filter { it.isAlive && (it.position - position).length() <= range + it.radius }.forEach { enemy ->
                val relative = enemy.position - position
                val projection = atan2(relative.y, relative.x) - angle
                val spokeDistance = min(abs(sin(projection)), abs(cos(projection))) * relative.length()
                if (spokeDistance <= enemy.radius + 2.5f) hit(world, enemy, 0.16f)
            }
        } else {
            cooldown -= dt
            if (cooldown <= 0f) {
                world.areaDamage(position, range, damage, source, type, allowCrit = visual != WeaponVisualKind.TESLA)
                world.addEffect(WeaponEffect(position, position, type, range, 0.2f))
                cooldown += (if (visual == WeaponVisualKind.BOMBARDMENT) 0.35f else 0.5f) /
                    (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
            }
        }
    }
}

class MovingBladeActor(state: WeaponRunState, position: Vec2, private val velocity: Vec2) : WeaponActor(
    state, position, WeaponVisualKind.BLADE, DamageType.KINETIC, 12f, 12f, 24f, 12f
) {
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        val previous = position
        position += velocity * dt
        angle += dt * 12f * state.scale(WeaponStat.RATE)
        world.enemies.filter { it.isAlive }.forEach { enemy ->
            if (sweptCircleHit(previous - enemy.previousPosition, position - enemy.position, range + enemy.radius) != null)
                hit(world, enemy, 0.22f)
        }
        if (position.x !in -32f..world.width + 32f || position.y !in -32f..world.height + 32f) finished = true
    }
}

class MineActor(state: WeaponRunState, position: Vec2, private val direction: Vec2) : WeaponActor(
    state, position, WeaponVisualKind.MINE, DamageType.EXPLOSIVE, 14f, 14f, 150f, 30f
) {
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        val previous = position
        position += direction * (18f * state.scale(WeaponStat.SPEED) * dt)
        angle += dt
        val hit = world.enemies.any { it.isAlive &&
            sweptCircleHit(previous - it.previousPosition, position - it.position, 4f + it.radius) != null }
        if (hit) {
            world.areaDamage(position, range, damage, source, type)
            world.addEffect(WeaponEffect(position, position, type, range, 0.35f))
            finished = true
        }
    }
}

class OrbitActor(state: WeaponRunState, position: Vec2, initialAngle: Float) : WeaponActor(
    state, position, WeaponVisualKind.ORB, DamageType.CHEMICAL,
    10f * state.scale(WeaponStat.DURATION), 10f * state.scale(WeaponStat.DURATION), 70f, 62f
) {
    init { angle = initialAngle }
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        val previous = position
        angle += dt * 1.8f * state.scale(WeaponStat.SPEED)
        position = world.player.position + Vec2(cos(angle), sin(angle)) * range
        world.enemies.filter { it.isAlive }.forEach {
            if (sweptCircleHit(previous - it.previousPosition, position - it.position, 5f + it.radius) != null)
                hit(world, it, 0.5f)
        }
    }
}

class SpectreWeapon : BaseSecondary(WeaponId.SPECTRE) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        repeat(event.containsCount("aeo").coerceAtMost(8)) {
            world.addActor(DroneActor(state, world.player.position + world.randomDirection() * 5f, false))
        }
    }
}
class HunterWeapon : BaseSecondary(WeaponId.HUNTER) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        repeat(event.containsCount("iou").coerceAtMost(8)) {
            world.addActor(DroneActor(state, world.player.position + world.randomDirection() * 8f, true))
        }
    }
}
class SentryWeapon : BaseSecondary(WeaponId.SENTRY) {
    override fun equip(world: CombatWorld) { world.addActor(SentryActor(state, world.player.position)) }
}
class TeslaMineWeapon : BaseSecondary(WeaponId.TESLA_MINE) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith("bcde")) world.addActor(FieldActor(state, deployment(world, event),
            WeaponVisualKind.TESLA, 8f, 12f, 38f, DamageType.ELECTRIC))
    }
}
class OscillatorWeapon : BaseSecondary(WeaponId.OSCILLATOR) {
    private var direction = Vec2(0f, -1f)
    private var cooldown = 0f
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        direction = (event.position - world.player.position).normalized()
    }
    override fun update(world: CombatWorld, dt: Float) {
        cooldown -= dt
        if (cooldown <= 0f) {
            val angle = atan2(direction.y, direction.x) + sin(world.elapsedSeconds.toFloat() * 3f) * 0.3f * scale(WeaponStat.SPREAD)
            world.launch(Projectile(world.player.position, Vec2(cos(angle), sin(angle)) * 230f,
                (10f * scale(WeaponStat.DAMAGE)).roundToInt(), 2.2f,
                criticalChance = 0.01f + world.currentWeaponModifiers.criticalChanceBonus,
                criticalDamageMultiplier = 2f, source = id, damageType = DamageType.HEAT))
            cooldown += 0.16f / (scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
        }
    }
}
class MeteorWeapon : BaseSecondary(WeaponId.METEOR) {
    private var cooldown = 2f
    override fun update(world: CombatWorld, dt: Float) {
        cooldown -= dt
        if (cooldown <= 0f && !world.hasPickup(PickupKind.METEOR)) {
            world.spawnPickup(PickupKind.METEOR, world.randomPoint(42f))
            cooldown = 9f / scale(WeaponStat.RATE)
        }
    }
    override fun activatePickup(world: CombatWorld, pickup: CombatPickup) {
        if (pickup.kind == PickupKind.METEOR) world.addActor(FieldActor(state, pickup.position,
            WeaponVisualKind.BOMBARDMENT, 2.2f, 80f, 40f, DamageType.EXPLOSIVE))
    }
}
class ChopperWeapon : BaseSecondary(WeaponId.CHOPPER) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        world.addActor(MovingBladeActor(state, world.player.position,
            (event.position - world.player.position).normalized() * 150f))
    }
}
class MinefieldWeapon : BaseSecondary(WeaponId.MINEFIELD) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith("fghi")) repeat(1 + state.bonus(WeaponStat.MINE_COUNT).toInt()) {
            world.addActor(MineActor(state, world.player.position, world.randomDirection()))
        }
    }
}
class BladeTrapWeapon : BaseSecondary(WeaponId.BLADE_TRAP) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith("jklm")) world.addActor(FieldActor(state, deployment(world, event),
            WeaponVisualKind.BLADE, 10f, 5f, 28f, DamageType.KINETIC))
    }
}
class OrbiterWeapon : BaseSecondary(WeaponId.ORBITER) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith("nopq")) {
            val angle = world.randomAngle()
            val start = world.player.position + Vec2(cos(angle), sin(angle)) * (62f * scale(WeaponStat.RANGE))
            world.addActor(OrbitActor(state, start, angle))
        }
    }
}
class GuardianWeapon : BaseSecondary(WeaponId.GUARDIAN) {
    private var drone: DroneActor? = null
    private var fuelCooldown = 8f
    override fun equip(world: CombatWorld) {
        drone = DroneActor(state, world.player.position + Vec2(20f, -20f), true, true).also(world::addActor)
    }
    override fun update(world: CombatWorld, dt: Float) {
        fuelCooldown -= dt
        if (fuelCooldown <= 0f && !world.hasPickup(PickupKind.FUEL)) {
            world.spawnPickup(PickupKind.FUEL, world.randomPoint(36f))
            fuelCooldown = 12f
        }
    }
    override fun activatePickup(world: CombatWorld, pickup: CombatPickup) {
        if (pickup.kind == PickupKind.FUEL) drone?.refuel()
    }
}

object SecondaryWeaponFactory {
    private val factories: Map<WeaponId, () -> SecondaryWeapon> = mapOf(
        WeaponId.SPECTRE to ::SpectreWeapon, WeaponId.SENTRY to ::SentryWeapon,
        WeaponId.TESLA_MINE to ::TeslaMineWeapon, WeaponId.OSCILLATOR to ::OscillatorWeapon,
        WeaponId.METEOR to ::MeteorWeapon, WeaponId.HUNTER to ::HunterWeapon,
        WeaponId.CHOPPER to ::ChopperWeapon, WeaponId.MINEFIELD to ::MinefieldWeapon,
        WeaponId.BLADE_TRAP to ::BladeTrapWeapon, WeaponId.ORBITER to ::OrbiterWeapon,
        WeaponId.GUARDIAN to ::GuardianWeapon
    )
    fun create(id: WeaponId) = factories[id]?.invoke() ?: error("No secondary factory for " + id)
}
