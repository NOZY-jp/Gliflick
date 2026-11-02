package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.SecondaryEvolution.*
import kotlin.math.*

/** Public demo Hunter: 10 damage, 120 hits/minute, 350 frames of active life at 60fps. */
data class HunterDefinition(
    val damage: Float = 10f,
    val hitIntervalSeconds: Float = 0.5f,
    val lifetimeSeconds: Float = 350f / 60f,
    val launchSeconds: Float = 20f / 60f,
    // Scene-space conversions remain separate from the recovered damage/time values.
    val range: Float = 70f,
    val speed: Float = 75f,
    val turnRadiansPerSecond: Float = 12f
)

/** Hunters launch outwards from the completed target, then pursue only outside their beam range. */
class HunterActor(
    state: WeaponRunState, position: Vec2, val enhanced: Boolean = false,
    launchOffset: Vec2 = Vec2.ZERO, val definition: HunterDefinition = HunterDefinition()
) : WeaponActor(
    state, position, WeaponVisualKind.DRONE, DamageType.ELECTRIC,
    definition.lifetimeSeconds * state.scale(WeaponStat.DURATION) * if (enhanced) 1.5f else 1f,
    definition.lifetimeSeconds * state.scale(WeaponStat.DURATION) * if (enhanced) 1.5f else 1f,
    definition.damage, definition.range
) {
    override val formScale get() = if (enhanced) 1.5f else 1f
    override val damageMultiplier get() = if (enhanced) 2f else 1f
    private var launchOrigin = position
    private var launchDestination = position + launchOffset
    private var launchAge = 0f
    private var cooldown = 0f
    private val mutableBeams = mutableListOf<WeaponEffect>()
    val beams: List<WeaponEffect> get() = mutableBeams

    override fun update(world: CombatWorld, dt: Float) {
        beamEnd = null
        mutableBeams.clear()
        var activeDt = dt
        if (launchAge < definition.launchSeconds) {
            val consumed = min(activeDt, definition.launchSeconds - launchAge)
            launchAge += consumed
            activeDt -= consumed
            val progress = (launchAge / definition.launchSeconds).coerceIn(0f, 1f)
            val eased = 1f - (1f - progress).pow(3)
            position = launchOrigin + (launchDestination - launchOrigin) * eased
            if (activeDt <= 0f) return
        }
        advanceLifetime(activeDt)
        if (finished) return
        cooldown = (cooldown - activeDt).coerceAtLeast(-activeDt)
        val target = world.nearestEnemy(position) ?: return
        val delta = target.position - position
        if (delta.length() > range) position += delta.normalized() * min(
            delta.length() - range, definition.speed * state.scale(WeaponStat.SPEED) * formScale * activeDt)
        val desired = atan2(target.position.y - position.y, target.position.x - position.x)
        val turn = atan2(sin(desired - angle), cos(desired - angle))
        val turnLimit = definition.turnRadiansPerSecond * activeDt
        angle += turn.coerceIn(-turnLimit, turnLimit)
        if ((target.position - position).length() > range + 0.001f || abs(turn) > turnLimit) return

        val muzzle = position + Vec2(cos(angle), sin(angle)) * (if (enhanced) 13f else 5f)
        if (state.has(ELECTRIFIED_BOLTS)) {
            if (cooldown <= 0f) {
                world.launch(Projectile(muzzle, (target.position - muzzle).normalized() * 320f,
                    damage, 1.6f, source = source, damageType = type,
                    ricochets = state.rank(CHAIN_LIGHTNING), ricochetDamageMultiplier = 1f))
                cooldown += interval(world) * 2f // Demo doubles the cadence period for bolts.
            }
            return
        }

        val visited = mutableSetOf<Long>()
        val targets = mutableListOf<Enemy>()
        var victim: Enemy? = target
        var start = muzzle
        repeat(1 + state.rank(CHAIN_LIGHTNING)) {
            victim?.let { enemy ->
                visited += enemy.id
                targets += enemy
                mutableBeams += WeaponEffect(start, enemy.position, type, source = source)
                start = enemy.position
                victim = world.enemies.filter { it.isAlive && it.id !in visited &&
                    (it.position - start).length() <= range }.minByOrNull { (it.position - start).length() }
            }
        }
        beamEnd = target.position
        if (cooldown <= 0f) {
            targets.forEach { world.dealDamage(it.id, damage, source, type) }
            cooldown += interval(world)
        }
    }

    private fun interval(world: CombatWorld) = definition.hitIntervalSeconds /
        (state.scale(WeaponStat.RATE) * world.currentWeaponModifiers.rateOfFireMultiplier)

    override fun resize(sx: Float, sy: Float) {
        super.resize(sx, sy)
        launchOrigin = launchOrigin.scaled(sx, sy)
        launchDestination = launchDestination.scaled(sx, sy)
        mutableBeams.clear()
    }
}
