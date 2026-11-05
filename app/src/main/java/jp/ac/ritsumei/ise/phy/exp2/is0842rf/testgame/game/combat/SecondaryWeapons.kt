package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import kotlin.math.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.SecondaryEvolution.*

data class CompletedCombatWord(val word: WordDefinition, val targetId: Long, val position: Vec2) {
    /** Dictionary spelling is a gameplay condition, never reconstructed input or matcher input. */
    val glyphs = TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters).lowercase()
    fun containsCount(letters: String) = glyphs.count { it in letters }
    fun startsWith(letters: String) = glyphs.firstOrNull()?.let { it in letters } ?: false
}

enum class WeaponVisualKind { DRONE, SENTRY, TESLA, BLADE, ORB, MINE, BOMBARDMENT, GUARDIAN }

/** Local geometry tuning shared by collision and drawing, independent of run upgrades. */
object WeaponSizeTuning {
    const val AREA_WEAPON_SCALE = 1.5f
    fun forWeapon(id: WeaponId): Float = when (id) {
        WeaponId.TESLA_MINE, WeaponId.METEOR, WeaponId.CHOPPER, WeaponId.MINEFIELD,
        WeaponId.BLADE_TRAP, WeaponId.ORBITER, WeaponId.GUARDIAN -> AREA_WEAPON_SCALE
        WeaponId.REPEATER, WeaponId.SPECTRE, WeaponId.SENTRY, WeaponId.OSCILLATOR, WeaponId.HUNTER -> 1f
    }
}
enum class PickupKind(val title: String) { METEOR("爆撃照準"), FUEL("燃料"), REROLL("リロール") }
class CombatPickup(val id: Long, val kind: PickupKind, var position: Vec2, val lifetime: Float = 14f,
                   val fuelBonus: WeaponStat? = null) {
    var age = 0f
    val remainingFraction get() = (1f - age / lifetime).coerceIn(0f, 1f)
}
class WeaponEffect(val start: Vec2, val end: Vec2, val type: DamageType,
                   val radius: Float = 0f, val lifetime: Float = 0.13f,
                   val source: WeaponId? = null) {
    var age = 0f
    val alpha get() = (1f - age / lifetime).coerceIn(0f, 1f)
}

/** Every secondary has its own activation/controller; the world owns damage and entity lifetimes. */
interface SecondaryWeapon {
    val id: WeaponId
    val state: WeaponRunState
    fun equip(world: CombatWorld) = Unit
    fun onEvolved(world: CombatWorld) = Unit
    fun onWord(world: CombatWorld, event: CompletedCombatWord) = Unit
    fun onMainWeaponFire(world: CombatWorld, projectile: Projectile) = Unit
    fun update(world: CombatWorld, dt: Float) = Unit
    fun activatePickup(world: CombatWorld, pickup: CombatPickup) = Unit
}

abstract class BaseSecondary(override val id: WeaponId) : SecondaryWeapon {
    final override val state = WeaponRunState(id)
    fun scale(stat: WeaponStat) = state.scale(stat)
}

abstract class WeaponActor(
    val state: WeaponRunState, var position: Vec2, val visual: WeaponVisualKind,
    private val originalType: DamageType, var remaining: Float, val initialDuration: Float,
    val baseDamage: Float, val baseRange: Float
) {
    var angle = 0f
    var beamEnd: Vec2? = null
    var active = true
    var finished = false
    var age = 0f; private set
    private var trailClock = 0f
    private val mutableTrail = ArrayDeque<Vec2>().apply { add(position) }
    val trail: List<Vec2> get() = mutableTrail.toList()
    val source get() = state.id
    val sizeScale get() = WeaponSizeTuning.forWeapon(source)
    val type get() = EvolutionEffects.type(state, originalType)
    val range get() = baseRange * sizeScale * state.scale(WeaponStat.RANGE) * formScale
    open val formScale get() = 1f
    val damage get() = ((baseDamage + EvolutionEffects.extraDamage(state)) *
        state.scale(WeaponStat.DAMAGE) * damageMultiplier).roundToInt().coerceAtLeast(1)
    protected open val damageMultiplier get() = 1f
    val durationFraction get() = (remaining / initialDuration).coerceIn(0f, 1f)
    protected val hitTimes = mutableMapOf<Long, Double>()
    abstract fun update(world: CombatWorld, dt: Float)
    internal fun captureMotion(dt: Float) {
        age += dt
        trailClock += dt
        if (trailClock >= 1f / 60f) {
            trailClock %= 1f / 60f
            mutableTrail.addLast(position)
            while (mutableTrail.size > 12) mutableTrail.removeFirst()
        }
    }
    open fun resize(sx: Float, sy: Float) {
        position = position.scaled(sx, sy); beamEnd = null
        val resized = mutableTrail.map { it.scaled(sx, sy) }
        mutableTrail.clear(); mutableTrail.addAll(resized)
    }
    protected fun hit(world: CombatWorld, enemy: Enemy, interval: Float, allowCrit: Boolean = true) {
        if (world.elapsedSeconds >= (hitTimes[enemy.id] ?: -1.0)) {
            world.dealDamage(enemy.id, damage, source, type,
                if (allowCrit) 0.01f + state.bonus(WeaponStat.CRIT) + world.currentWeaponModifiers.criticalChanceBonus else 0f,
                2f + state.bonus(WeaponStat.CRIT_MULTIPLIER))
            val poison = state.rank(POISON_COATING)
            if (poison > 0 && enemy.isAttackable) world.dealDamage(enemy.id,
                (5f * poison * state.scale(WeaponStat.DAMAGE)).roundToInt(), source, DamageType.CHEMICAL, inflictStatus = false)
            hitTimes[enemy.id] = world.elapsedSeconds + interval /
                (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
        }
    }
    protected fun advanceLifetime(dt: Float) {
        remaining -= dt
        if (remaining <= 0f) finished = true
    }
}

/** Spectre's continuously flying melee behavior is independent of Hunter targeting and cadence. */
class DroneActor(state: WeaponRunState, position: Vec2, val enhanced: Boolean = false) : WeaponActor(
    state, position, WeaponVisualKind.DRONE, DamageType.KINETIC,
    7f * state.scale(WeaponStat.DURATION), 7f * state.scale(WeaponStat.DURATION), 50f, 5f
) {
    override val formScale get() = if (enhanced) 1.5f else 1f
    override val damageMultiplier get() = if (enhanced) 2f else 1f
    init { if (enhanced) remaining *= 1.5f }
    private var targetId: Long? = null
    private var headingInitialized = false
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        if (finished) {
            if (state.has(SELF_DESTRUCT)) {
                val radius = 22f * (if (state.rank(SELF_DESTRUCT) == 2) 1.3f else 1f) * state.scale(WeaponStat.RANGE)
                world.areaDamage(position, radius, (50f * state.rank(SELF_DESTRUCT) * state.scale(WeaponStat.DAMAGE)).roundToInt(), source, DamageType.EXPLOSIVE)
                world.addEffect(WeaponEffect(position, position, DamageType.EXPLOSIVE, radius, 0.35f, source))
            }
            return
        }
        val target = world.enemies.firstOrNull { it.id == targetId && it.isAttackable }
            ?: world.nearestEnemy(position) ?: return
        targetId = target.id
        val delta = target.position - position
        val desired = atan2(delta.y, delta.x)
        val previous = position
        if (!headingInitialized) { angle = desired; headingInitialized = true }
        val turn = atan2(sin(desired - angle), cos(desired - angle))
        angle += turn.coerceIn(-6f * dt, 6f * dt)
        position += Vec2(cos(angle), sin(angle)) * (75f * state.scale(WeaponStat.SPEED) * formScale * dt)
        if (sweptCircleHit(previous - target.previousPosition,
                position - target.position, target.radius + 5f * formScale) != null) hit(world, target, 0.55f)
    }
}

/** Guardian has its own pursuit and orbiting fireballs, independent of the two glyph-spawned drones. */
class GuardianActor(state: WeaponRunState, position: Vec2, val index: Int = 0) : WeaponActor(
    state, position, WeaponVisualKind.GUARDIAN, DamageType.HEAT,
    30f * state.scale(WeaponStat.DURATION), 30f * state.scale(WeaponStat.DURATION), 20f, 70f
) {
    var orbitAngle = 0f; private set
    val bodyRadius get() = range * 0.0425f
    val fireballRadius get() = range * 0.0625f
    val orbitRadius get() = range * 0.1875f
    fun fireballPosition(phase: Float = 0f, lookBack: Float = 0f): Vec2 =
        position + Vec2(cos(orbitAngle + phase - lookBack), sin(orbitAngle + phase - lookBack)) * orbitRadius

    override fun update(world: CombatWorld, dt: Float) {
        remaining = (remaining - dt).coerceAtLeast(0f)
        active = remaining > 0f
        if (!active) return
        orbitAngle += dt * 3.6f * state.scale(WeaponStat.RATE)
        val target = world.enemies.filter { it.isAttackable &&
            (it.position - world.player.position).length() <= range + it.radius }
            .minByOrNull { (it.position - position).length() }
        val destination = target?.position ?: (world.player.position + Vec2(20f * cos(index * 2.1f), -20f + 20f * sin(index * 2.1f)))
        val delta = destination - position
        if (delta.length() > 0.01f) {
            angle = atan2(delta.y, delta.x)
            val travel = (delta.length() - if (target != null) orbitRadius * 0.6f else 0f).coerceAtLeast(0f)
            position += delta.normalized() * min(travel, 48f * state.scale(WeaponStat.SPEED) * dt)
        }
        world.enemies.filter { it.isAttackable }.forEach { enemy ->
            if (listOf(0f, PI.toFloat()).any { phase ->
                (enemy.position - fireballPosition(phase)).length() <= enemy.radius + fireballRadius
            }) hit(world, enemy, 0.1f)
        }
    }
    fun refuel() { remaining = (remaining + 20f * state.scale(WeaponStat.DURATION))
        .coerceAtMost(60f * state.scale(WeaponStat.DURATION)) }
}

class SentryActor(state: WeaponRunState, position: Vec2, val index: Int = 0) : WeaponActor(
    state, position, WeaponVisualKind.SENTRY, DamageType.HEAT, Float.MAX_VALUE,
    Float.MAX_VALUE, 30f, 80f
) {
    private var cooldown = 0f
    override fun update(world: CombatWorld, dt: Float) {
        val offset = if (index == 0) Vec2(0f, 0f) else Vec2(cos(index * 2.4f), sin(index * 2.4f)) * 14f
        position = world.player.position + offset
        cooldown -= dt
        val target = world.nearestEnemy(position, range) ?: return
        val delta = target.position - position
        val desired = atan2(delta.y, delta.x)
        val difference = atan2(sin(desired - angle), cos(desired - angle))
        angle += difference.coerceIn(-6f * dt, 6f * dt)
        if (abs(difference) < 0.24f && cooldown <= 0f) {
            world.launch(Projectile(position, Vec2(cos(angle), sin(angle)) * 410f, damage, radius = 2.1f,
                source = source, damageType = type, criticalChance = (0.01f + state.bonus(WeaponStat.CRIT) + world.currentWeaponModifiers.criticalChanceBonus).coerceIn(0f, 1f),
                criticalDamageMultiplier = 2f + state.bonus(WeaponStat.CRIT_MULTIPLIER)))
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
    var destination: Vec2? = null
    var continuous = false
    override fun update(world: CombatWorld, dt: Float) {
        if (!continuous) advanceLifetime(dt)
        destination?.let { target ->
            val delta = target - position
            position += delta.normalized() * min(delta.length(), 35f * state.scale(WeaponStat.SPEED) * dt)
        }
        if (visual == WeaponVisualKind.BLADE)
            angle += dt * 4f * state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier
        if (visual == WeaponVisualKind.BLADE) {
            world.enemies.filter { it.isAttackable && (it.position - position).length() <= range + it.radius }.forEach { enemy ->
                val relative = enemy.position - position
                val projection = atan2(relative.y, relative.x) - angle
                val spokeDistance = min(abs(sin(projection)), abs(cos(projection))) * relative.length()
                if (spokeDistance <= enemy.radius + 2.5f * sizeScale) hit(world, enemy, 0.16f)
            }
        } else {
            cooldown -= dt
            if (cooldown <= 0f) {
                val targets = if (visual == WeaponVisualKind.TESLA) world.enemies.filter {
                    it.isAttackable && (it.position - position).length() <= range + it.radius
                } else emptyList()
                world.areaDamage(position, range, damage, source, type, allowCrit = visual != WeaponVisualKind.TESLA)
                if (visual == WeaponVisualKind.TESLA) {
                    targets.forEach { world.addEffect(WeaponEffect(position, it.position, type, source = source)) }
                } else world.addEffect(WeaponEffect(position, position, type, range, 0.2f, source))
                cooldown += (if (visual == WeaponVisualKind.BOMBARDMENT) 0.35f else 0.5f) /
                    (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)
            }
        }
    }
}

class MovingBladeActor(state: WeaponRunState, position: Vec2, private var velocity: Vec2,
                       val saw: Boolean = false) : WeaponActor(
    state, position, WeaponVisualKind.BLADE, DamageType.KINETIC, 12f, 12f, 24f, 24f
) {
    private var returning = false
    override val formScale get() = if (saw) 0.35f else 1f
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        val previous = position
        if (returning) velocity = (world.player.position - position).normalized() * velocity.length()
        position += velocity * (state.scale(WeaponStat.SPEED) * dt)
        angle += dt * 12f * state.scale(WeaponStat.RATE)
        world.enemies.filter { it.isAttackable }.forEach { enemy ->
            if (sweptCircleHit(previous - enemy.previousPosition, position - enemy.position, range + enemy.radius) != null)
                hit(world, enemy, 0.22f)
        }
        val margin = max(32f, range)
        if (returning && (position - world.player.position).length() < 8f) finished = true
        if (position.x !in -margin..world.width + margin || position.y !in -margin..world.height + margin) {
            if (state.has(BOOMERANG) && !returning) returning = true else if (!returning) finished = true
        }
    }
}

class MineActor(state: WeaponRunState, position: Vec2, private val direction: Vec2) : WeaponActor(
    state, position, WeaponVisualKind.MINE, DamageType.EXPLOSIVE, 14f, 14f, 150f, 30f
) {
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        val previous = position
        val movement = if (state.has(HUNTER_SEEKER)) world.nearestEnemy(position)?.let {
            (it.position - position).normalized() } ?: direction else direction
        position += movement * (18f * state.scale(WeaponStat.SPEED) * dt)
        angle += dt * 4.7f
        val hit = world.enemies.any { it.isAttackable &&
            sweptCircleHit(previous - it.previousPosition, position - it.position, 4f * sizeScale + it.radius) != null }
        if (hit) {
            world.areaDamage(position, range, damage, source, type)
            world.addEffect(WeaponEffect(position, position, type, range, 0.35f, source))
            finished = true
        }
    }
}

class OrbitActor(state: WeaponRunState, position: Vec2, initialAngle: Float, private val clockwise: Boolean = true) : WeaponActor(
    state, position, WeaponVisualKind.ORB, DamageType.CHEMICAL,
    10f * state.scale(WeaponStat.DURATION), 10f * state.scale(WeaponStat.DURATION), 70f, 10f
) {
    private var orbitRadius: Float? = null
    init { angle = initialAngle }
    override fun update(world: CombatWorld, dt: Float) {
        advanceLifetime(dt)
        val previous = position
        if (orbitRadius == null) orbitRadius = (position - world.player.position).length()
        angle += dt * 1.8f * state.scale(WeaponStat.SPEED) * if (clockwise) 1f else -1f
        position = world.player.position + Vec2(cos(angle), sin(angle)) * orbitRadius!!
        world.enemies.filter { it.isAttackable }.forEach {
            if (sweptCircleHit(previous - it.previousPosition, position - it.position, range + it.radius) != null)
                hit(world, it, 0.5f)
            else if (state.has(TETHER) && sweptCircleHit(world.player.position - it.position,
                    position - it.position, it.radius + 0.8f) != null &&
                    world.elapsedSeconds >= (hitTimes[it.id] ?: -1.0)) {
                val fraction = if (state.rank(TETHER) == 2) 0.5f else 0.3f
                world.dealDamage(it.id, (damage * fraction).roundToInt(), source, type)
                hitTimes[it.id] = world.elapsedSeconds + 0.5f / state.scale(WeaponStat.RATE)
            }
        }
    }
    override fun resize(sx: Float, sy: Float) {
        super.resize(sx, sy)
        angle = atan2(sin(angle) * sy, cos(angle) * sx)
        orbitRadius = null
    }
}

class SpectreWeapon : BaseSecondary(WeaponId.SPECTRE) {
    private var produced = 0
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        repeat(event.containsCount(WeaponTriggerGlyphs.SPECTRE).coerceAtMost(8)) {
            if (state.has(SUPERCHARGED)) produced++
            world.addActor(DroneActor(state, event.position,
                state.has(SUPERCHARGED) && produced % 20 == 0))
        }
    }
}
class HunterWeapon : BaseSecondary(WeaponId.HUNTER) {
    private var produced = 0
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        repeat(event.containsCount(WeaponTriggerGlyphs.HUNTER).coerceAtMost(8)) {
            if (state.has(HUNTER_ELITE)) produced++
            val enhanced = state.has(HUNTER_ELITE) && produced % 12 == 0
            world.addActor(HunterActor(state, event.position, enhanced,
                world.randomDirection() * (if (enhanced) 45f else 28f)))
        }
    }
}
class SentryWeapon : BaseSecondary(WeaponId.SENTRY) {
    override fun equip(world: CombatWorld) { syncTurrets(world) }
    override fun onEvolved(world: CombatWorld) { syncTurrets(world) }
    private fun syncTurrets(world: CombatWorld) {
        val count = world.weaponActors.count { it.source == id }
        for (i in count until 1 + state.rank(DEFENSE_GRID)) world.addActor(SentryActor(state, world.player.position, i))
    }
}
class TeslaMineWeapon : BaseSecondary(WeaponId.TESLA_MINE) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith(EvolutionEffects.triggerLetters(state, WeaponTriggerGlyphs.TESLA))) {
            world.addActor(FieldActor(state, event.position,
                WeaponVisualKind.TESLA, 32f, 12f, 38f, DamageType.ELECTRIC).also { it.angle = world.randomAngle() })
        }
    }
}
/** Dense emission history forms a travelling wave; the renderer and swept collision share its dots. */
data class OscillatorMotion(
    val particlesPerSecond: Float = 60f,
    val speed: Float = 360f,
    val amplitudeRadians: Float = 0.2f,
    val phaseSpeed: Float = 6f,
    val turnSpeed: Float = PI.toFloat(),
    val particleRadius: Float = 1.5f
) {
    init {
        require(listOf(particlesPerSecond, speed, amplitudeRadians, phaseSpeed, turnSpeed, particleRadius)
            .all { it > 0f && it.isFinite() })
    }
}

class OscillatorWeapon(private val motion: OscillatorMotion = OscillatorMotion()) : BaseSecondary(WeaponId.OSCILLATOR) {
    private var heading = -PI.toFloat() / 2f
    private var desiredHeading = heading
    private var phase = 0f
    private var cooldown = 1f / motion.particlesPerSecond
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        val delta = event.position - world.player.position
        if (delta.length() > 0.001f) desiredHeading = atan2(delta.y, delta.x)
    }
    override fun update(world: CombatWorld, dt: Float) {
        if (dt <= 0f) return
        val previousHeading = heading
        val turn = atan2(sin(desiredHeading - heading), cos(desiredHeading - heading))
        heading += turn.coerceIn(-motion.turnSpeed * dt, motion.turnSpeed * dt)
        val previousPhase = phase
        phase = (phase + motion.phaseSpeed * dt) % (2f * PI.toFloat())
        cooldown -= dt
        val interval = 1f / (motion.particlesPerSecond * scale(WeaponStat.RATE) *
            world.currentWeaponModifiers.rateOfFireMultiplier)
        while (cooldown <= 0f) {
            // Sample the wave at emission time, including when a simulation step spans several dots.
            val sampleTime = (dt + cooldown).coerceIn(0f, dt)
            val sampleHeading = previousHeading + turn.coerceIn(-motion.turnSpeed * sampleTime, motion.turnSpeed * sampleTime)
            val angle = sampleHeading + sin(previousPhase + motion.phaseSpeed * sampleTime) *
                motion.amplitudeRadians * scale(WeaponStat.SPREAD)
            repeat(if (state.has(TWIN_HEADED)) 2 else 1) { index ->
                val direction = angle + index * PI.toFloat()
                world.launch(Projectile(world.player.position, Vec2(cos(direction), sin(direction)) * motion.speed,
                    ((10f + EvolutionEffects.extraDamage(state)) * scale(WeaponStat.DAMAGE)).roundToInt(), motion.particleRadius,
                    criticalChance = (0.01f + state.bonus(WeaponStat.CRIT) + world.currentWeaponModifiers.criticalChanceBonus).coerceIn(0f, 1f),
                    criticalDamageMultiplier = 2f + state.bonus(WeaponStat.CRIT_MULTIPLIER), source = id,
                    damageType = EvolutionEffects.type(state, DamageType.HEAT), penetrates = state.has(STABILIZED_PLASMA)))
            }
            cooldown += interval
        }
    }
}
class MeteorWeapon : BaseSecondary(WeaponId.METEOR) {
    private var cooldown = 2f
    private var continuous: FieldActor? = null
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) { continuous?.destination = event.position }
    override fun onEvolved(world: CombatWorld) {
        if (state.has(RELENTLESS) && continuous == null) {
            world.removePickups(PickupKind.METEOR)
            continuous = FieldActor(state, world.randomPoint(42f), WeaponVisualKind.BOMBARDMENT,
                2.2f, 80f, 40f, DamageType.EXPLOSIVE).also { it.continuous = true; world.addActor(it) }
        }
    }
    override fun update(world: CombatWorld, dt: Float) {
        if (state.has(RELENTLESS)) { onEvolved(world); return }
        cooldown -= dt
        if (cooldown <= 0f && !world.hasPickup(PickupKind.METEOR)) {
            repeat(1 + state.rank(SURVEILLANCE)) { world.spawnPickup(PickupKind.METEOR, world.randomPoint(42f)) }
            cooldown = 9f / scale(WeaponStat.RATE)
        }
    }
    override fun activatePickup(world: CombatWorld, pickup: CombatPickup) {
        if (pickup.kind == PickupKind.METEOR) {
            world.addActor(FieldActor(state, pickup.position, WeaponVisualKind.BOMBARDMENT,
                2.2f, 80f, 40f, DamageType.EXPLOSIVE))
            world.removePickups(PickupKind.METEOR) // Surveillance supplies alternatives, not extra simultaneous rewards.
        }
    }
}
class ChopperWeapon : BaseSecondary(WeaponId.CHOPPER) {
    // Each flight (including all saws in one volley) occupies one available Chopper slot.
    private val flights = mutableListOf<List<MovingBladeActor>>()
    override fun onMainWeaponFire(world: CombatWorld, projectile: Projectile) {
        flights.removeAll { flight -> flight.all { it.finished } }
        if (flights.size >= 1 + state.rank(DUAL_WIELD)) return
        val direction = projectile.velocity.normalized()
        val heading = atan2(direction.y, direction.x)
        val saw = state.has(SAW_LAUNCHER)
        val count = if (saw) 2 * state.rank(SAW_LAUNCHER) else 1
        val launched = buildList {
            repeat(count) { index ->
                val angle = heading + if (saw) (index - (count - 1) / 2f) * 0.18f else 0f
                val actor = MovingBladeActor(state, world.player.position,
                    Vec2(cos(angle), sin(angle)) * (if (saw) 230f else 150f), saw)
                if (world.addActor(actor)) add(actor)
            }
        }
        if (launched.isNotEmpty()) flights += launched
    }
}
class MinefieldWeapon : BaseSecondary(WeaponId.MINEFIELD) {
    private var recursiveCooldown = 12f
    private val count get() = 3 + state.bonus(WeaponStat.MINE_COUNT).toInt() + if (state.rank(HUNTER_SEEKER) == 2) 1 else 0
    private fun spawn(world: CombatWorld, count: Int, position: Vec2 = world.player.position) { repeat(count) {
        world.addActor(MineActor(state, position, world.randomDirection()))
    } }
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith(EvolutionEffects.triggerLetters(state, WeaponTriggerGlyphs.MINEFIELD))) spawn(world, count, event.position)
    }
    override fun update(world: CombatWorld, dt: Float) {
        if (!state.has(RECURSIVE_MINES)) return
        recursiveCooldown -= dt * scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier
        if (recursiveCooldown <= 0f) {
            recursiveCooldown += 12f
            spawn(world, count / 2 + if (state.rank(RECURSIVE_MINES) == 2) 1 else 0)
        }
    }
}
class BladeTrapWeapon : BaseSecondary(WeaponId.BLADE_TRAP) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith(EvolutionEffects.triggerLetters(state, WeaponTriggerGlyphs.BLADE))) world.addActor(FieldActor(state, event.position,
            WeaponVisualKind.BLADE, 10f, 5f, 28f, DamageType.KINETIC))
    }
}
class OrbiterWeapon : BaseSecondary(WeaponId.ORBITER) {
    override fun onWord(world: CombatWorld, event: CompletedCombatWord) {
        if (event.startsWith(EvolutionEffects.triggerLetters(state, WeaponTriggerGlyphs.ORBITER))) {
            val delta = event.position - world.player.position
            world.addActor(OrbitActor(state, event.position, atan2(delta.y, delta.x)))
            if (state.has(DOPPELGANGER)) world.addActor(OrbitActor(state, event.position, atan2(delta.y, delta.x), false))
        }
    }
}
class GuardianWeapon : BaseSecondary(WeaponId.GUARDIAN) {
    private val drones = mutableListOf<GuardianActor>()
    private var fuelCooldown = 8f
    private var boostRemaining = 0f
    var fuelBoost: WeaponStat? = null; private set
    override fun equip(world: CombatWorld) {
        state.temporaryBonus = { if (it == fuelBoost && boostRemaining > 0f)
            (if (it == WeaponStat.SPEED) 1f else 0.5f) *
                (if (state.rank(EXPERIMENTAL_FUEL) == 2) 1.5f else 1f) else 0f }
        onEvolved(world)
    }
    override fun onEvolved(world: CombatWorld) {
        while (drones.size < 1 + state.rank(GUARDIAN_TEAM)) {
            val i = drones.size
            drones += GuardianActor(state, world.player.position + Vec2(20f * cos(i * 2.1f), -20f + 20f * sin(i * 2.1f)), i).also(world::addActor)
        }
    }
    override fun update(world: CombatWorld, dt: Float) {
        boostRemaining = (boostRemaining - dt).coerceAtLeast(0f)
        fuelCooldown -= dt
        if (fuelCooldown <= 0f && !world.hasPickup(PickupKind.FUEL)) {
            world.spawnPickup(PickupKind.FUEL, world.randomPoint(36f),
                if (state.has(EXPERIMENTAL_FUEL)) listOf(WeaponStat.DAMAGE, WeaponStat.SPEED, WeaponStat.RANGE)[world.randomIndex(3)] else null)
            fuelCooldown = 12f
        }
    }
    override fun activatePickup(world: CombatWorld, pickup: CombatPickup) {
        if (pickup.kind == PickupKind.FUEL) {
            drones.forEach { it.refuel() }
            if (state.has(EXPERIMENTAL_FUEL)) {
                fuelBoost = pickup.fuelBonus
                    ?: listOf(WeaponStat.DAMAGE, WeaponStat.SPEED, WeaponStat.RANGE)[world.randomIndex(3)]
                boostRemaining = if (state.rank(EXPERIMENTAL_FUEL) == 2) 12f else 8f
            }
        }
    }
}

object SecondaryWeaponFactory {
    private val factories: Map<WeaponId, () -> SecondaryWeapon> = mapOf(
        WeaponId.SPECTRE to ::SpectreWeapon, WeaponId.SENTRY to ::SentryWeapon,
        WeaponId.TESLA_MINE to ::TeslaMineWeapon, WeaponId.OSCILLATOR to { OscillatorWeapon() },
        WeaponId.METEOR to ::MeteorWeapon, WeaponId.HUNTER to ::HunterWeapon,
        WeaponId.CHOPPER to ::ChopperWeapon, WeaponId.MINEFIELD to ::MinefieldWeapon,
        WeaponId.BLADE_TRAP to ::BladeTrapWeapon, WeaponId.ORBITER to ::OrbiterWeapon,
        WeaponId.GUARDIAN to ::GuardianWeapon
    )
    fun create(id: WeaponId) = factories[id]?.invoke() ?: error("No secondary factory for " + id)
}
