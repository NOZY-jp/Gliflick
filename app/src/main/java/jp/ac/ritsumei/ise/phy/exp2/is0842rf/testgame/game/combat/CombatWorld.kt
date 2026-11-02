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
    data class PickupSpawned(val pickup: CombatPickup) : CombatEvent
    data class PickupRemoved(val id: Long) : CombatEvent
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
    init {
        (mainWeapon as? Repeater)?.upgrades?.globalBonus = { stat ->
            EvolutionEffects.globalBonus(weaponStates, WeaponId.REPEATER, stat)
        }
    }
    private val mutableEnemies = mutableListOf<Enemy>()
    private val mutableProjectiles = mutableListOf<Projectile>()
    private val mutableBursts = mutableListOf<DeathBurst>()
    private val mutableHitFeedback = mutableListOf<HitFeedback>()
    private val mutableSecondaryWeapons = mutableListOf<SecondaryWeapon>()
    private val mutableWeaponStates = mutableListOf<WeaponRunState>().apply {
        (mainWeapon as? Repeater)?.upgrades?.let(::add)
    }
    private val mutableActors = mutableListOf<WeaponActor>()
    private val mutableEffects = mutableListOf<WeaponEffect>()
    private val mutablePickups = mutableListOf<CombatPickup>()
    val secondaryWeapons: List<SecondaryWeapon> get() = mutableSecondaryWeapons
    val weaponActors: List<WeaponActor> get() = mutableActors
    val weaponEffects: List<WeaponEffect> get() = mutableEffects
    val pickups: List<CombatPickup> get() = mutablePickups
    var currentWeaponModifiers = WeaponRuntimeModifiers(); private set
    private data class DamageSample(val time: Double, val source: WeaponId, val amount: Int)
    private val damageSamples = ArrayDeque<DamageSample>()
    private var nextPickupId = 1L
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
    var pendingLevelUps = 0; private set
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
            mutableActors.forEach { it.resize(sx, sy) }
            mutablePickups.forEach { it.position = it.position.scaled(sx, sy) }
            mutableEffects.clear()
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

    fun completeWord(word: jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition, enemyId: Long) {
        val enemy = mutableEnemies.firstOrNull { it.id == enemyId && it.isAlive } ?: return
        target(enemyId)
        val event = CompletedCombatWord(word, enemyId, enemy.position)
        mutableSecondaryWeapons.forEach { it.onWord(this, event) }
    }

    fun acquireWeapon(id: WeaponId): Boolean {
        if (id == WeaponId.REPEATER || secondaryWeapons.size >= 4 || secondaryWeapons.any { it.id == id }) return false
        val weapon = SecondaryWeaponFactory.create(id)
        mutableSecondaryWeapons += weapon
        mutableWeaponStates += weapon.state
        weapon.state.globalBonus = { stat -> EvolutionEffects.globalBonus(weaponStates, weapon.id, stat) }
        weapon.equip(this)
        return true
    }

    val weaponStates: List<WeaponRunState> get() = mutableWeaponStates
    fun weaponState(id: WeaponId) = weaponStates.firstOrNull { it.id == id }

    private fun nextExtraLetters(): String = EvolutionCatalog.extraLetterGroups.firstOrNull { group ->
        weaponStates.none { state -> group.all { it in state.extraTriggerLetters } }
    }.orEmpty()
    fun evolutionOffers(id: WeaponId): List<UpgradeOffer> = weaponState(id)?.let {
        EvolutionCatalog.offers(it, nextExtraLetters())
    }.orEmpty()

    fun applyUpgrade(offer: UpgradeOffer): Boolean = when (offer.kind) {
        OfferKind.ACQUIRE -> acquireWeapon(offer.weapon)
        OfferKind.UPGRADE -> {
            val state = if (offer.weapon == WeaponId.REPEATER) (mainWeapon as? Repeater)?.upgrades
                else secondaryWeapons.firstOrNull { it.id == offer.weapon }?.state
            state?.improve(offer.effects)
            state != null
        }
        OfferKind.EVOLUTION -> {
            val state = weaponState(offer.weapon)
            if (state != null && state.canEvolve && offer.evolution != null &&
                evolutionOffers(state.id).any { it.evolution == offer.evolution }) {
                if (offer.evolution in EvolutionCatalog.triggerBranches) state.extraTriggerLetters += nextExtraLetters()
                state.evolve(offer.evolution)
                secondaryWeapons.firstOrNull { it.id == offer.weapon }?.onEvolved(this)
                true
            } else false
        }
    }

    fun consumeLevelUp(): Boolean {
        if (pendingLevelUps == 0) return false
        pendingLevelUps--
        return true
    }

    fun grantExperience(amount: Int) {
        require(amount >= 0)
        experience += amount
        while (experience >= nextLevelExperience) {
            experience -= nextLevelExperience
            level++
            pendingLevelUps++
        }
    }

    fun nearestEnemy(position: Vec2, range: Float = Float.MAX_VALUE): Enemy? =
        enemies.filter { it.isAlive && (it.position - position).length() <= range + it.radius }
            .minByOrNull { (it.position - position).length() }
    fun randomIndex(bound: Int) = random.nextInt(bound)
    fun removePickups(kind: PickupKind) {
        mutablePickups.filter { it.kind == kind }.toList().forEach {
            mutablePickups.remove(it); events += CombatEvent.PickupRemoved(it.id)
        }
    }
    var doubleDamageRemaining = 0f; private set
    fun randomAngle() = random.nextFloat() * kotlin.math.PI.toFloat() * 2f
    fun randomDirection() = randomAngle().let { Vec2(kotlin.math.cos(it), kotlin.math.sin(it)) }
    fun clampPoint(point: Vec2, margin: Float) = Vec2(
        point.x.coerceIn(margin.coerceAtMost(width / 2f), (width - margin).coerceAtLeast(width / 2f)),
        point.y.coerceIn(margin.coerceAtMost(height / 2f), (height - margin).coerceAtLeast(height / 2f)))
    fun randomPoint(margin: Float) = clampPoint(Vec2(random.nextFloat() * width, random.nextFloat() * height), margin)
    fun launch(projectile: Projectile) { if (mutableProjectiles.size < 1024) mutableProjectiles += projectile }
    fun addActor(actor: WeaponActor): Boolean {
        if (mutableActors.count { it.source == actor.source } >= 48 || mutableActors.size >= 160) return false
        mutableActors += actor
        return true
    }
    fun addEffect(effect: WeaponEffect) { if (mutableEffects.size < 160) mutableEffects += effect }
    fun hasPickup(kind: PickupKind) = pickups.any { it.kind == kind }
    fun spawnPickup(kind: PickupKind, position: Vec2, fuelBonus: WeaponStat? = null): CombatPickup {
        val pickup = CombatPickup(nextPickupId++, kind, position, fuelBonus = fuelBonus)
        mutablePickups += pickup
        events += CombatEvent.PickupSpawned(pickup)
        return pickup
    }
    fun activatePickup(id: Long) {
        val pickup = mutablePickups.firstOrNull { it.id == id } ?: return
        mutableSecondaryWeapons.forEach { it.activatePickup(this, pickup) }
        if (mutablePickups.remove(pickup)) events += CombatEvent.PickupRemoved(id)
    }
    fun dps(source: WeaponId): Float = damageSamples.filter { it.source == source }.sumOf { it.amount } /
        elapsedSeconds.coerceIn(1.0, 10.0).toFloat()

    fun areaDamage(position: Vec2, radius: Float, damage: Int, source: WeaponId, type: DamageType,
                   allowCrit: Boolean = true, excludedEnemy: Long? = null,
                   criticalChance: Float? = null, criticalMultiplier: Float? = null) {
        enemies.filter { it.isAlive && it.id != excludedEnemy && (it.position - position).length() <= radius + it.radius }
            .forEach { dealDamage(it.id, damage, source, type,
                if (allowCrit) criticalChance ?: (0.01f + (weaponState(source)?.bonus(WeaponStat.CRIT) ?: 0f) +
                    currentWeaponModifiers.criticalChanceBonus) else 0f,
                criticalMultiplier ?: (2f + (weaponState(source)?.bonus(WeaponStat.CRIT_MULTIPLIER) ?: 0f))) }
    }

    fun damageEnemy(enemyId: Long, damage: Int, incomingAngle: Float? = null) {
        dealDamage(enemyId, damage, WeaponId.REPEATER, DamageType.KINETIC, inflictStatus = false, incomingAngle = incomingAngle)
    }

    fun dealDamage(enemyId: Long, damage: Int, source: WeaponId, type: DamageType,
                   criticalChance: Float = 0f, criticalMultiplier: Float = 1.5f,
                   inflictStatus: Boolean = true, hitPosition: Vec2? = null, incomingAngle: Float? = null) {
        mutableEnemies.firstOrNull { it.id == enemyId && it.isAlive }?.let {
            val bleeding = it.statuses.any { status -> status.type == DamageType.KINETIC }
            val bloodbathRank = weaponState(WeaponId.CHOPPER)?.rank(SecondaryEvolution.BLOODBATH) ?: 0
            val bloodBonus = if (source == WeaponId.CHOPPER && bleeding && bloodbathRank > 0)
                (if (bloodbathRank == 2) 0.25f else 0.1f) else 0f
            val typedDamage = damage * (1f + EvolutionEffects.damageTypeBonus(weaponStates, type)) *
                (1f + bloodBonus) * if (doubleDamageRemaining > 0f) 2f else 1f
            val actual = (typedDamage * if (random.nextFloat() < criticalChance.coerceIn(0f, 1f)) criticalMultiplier else 1f).roundToInt()
            val applied = it.takeDamage(actual)
            if (applied > 0) {
                val angle = incomingAngle ?: atan2(it.position.y - player.position.y, it.position.x - player.position.x)
                mutableHitFeedback += HitFeedback(hitPosition ?: it.position, applied, it.radius, angle, random)
                damageSamples += DamageSample(elapsedSeconds, source, applied)
                if (inflictStatus && it.isAlive) applyAttackStatuses(it, source, type)
            }
            if (!it.isAlive) {
                if (source == WeaponId.CHOPPER && bleeding && bloodbathRank > 0 &&
                    random.nextFloat() < if (bloodbathRank == 2) 0.25f else 0.1f) doubleDamageRemaining = 10f
                die(it, awardExperience = true, source = source)
            }
        }
    }

    private fun applyAttackStatuses(enemy: Enemy, source: WeaponId, type: DamageType) {
        val state = weaponState(source)
        val saw = weaponState(WeaponId.CHOPPER)?.rank(SecondaryEvolution.SAW_LAUNCHER) ?: 0
        fun roll(type: DamageType, chance: Float) {
            if (chance <= 0f || random.nextFloat() >= chance) return
            if (type == DamageType.COLD) { enemy.frozenSeconds = 2f; return }
            val existing = enemy.statuses.firstOrNull { it.type == type }
            if (existing != null) existing.remaining = 5f else enemy.statuses += EnemyStatus(type, source)
        }
        var chance = if (type == DamageType.CHEMICAL) 0.1f else if (type == DamageType.COLD) 0.03f else 0.03f
        if (type == DamageType.ELECTRIC) {
            if (weaponActors.any { it.source == WeaponId.TESLA_MINE && it.state.has(SecondaryEvolution.RESTRICTED_AREA) &&
                (it.position - enemy.position).length() <= it.range + enemy.radius }) chance += 0.2f
            if (state?.rank(SecondaryEvolution.STORM_RING) == 2) chance += 0.05f
        }
        if (type == DamageType.CHEMICAL && state?.rank(SecondaryEvolution.INFUSION) == 2) chance += 0.1f
        if (type != DamageType.EXPLOSIVE) roll(type, chance)
        if (source == WeaponId.BLADE_TRAP) {
            val poison = state?.rank(SecondaryEvolution.POISON_COATING) ?: 0
            if (poison > 0) roll(DamageType.CHEMICAL, if (poison == 2) 0.15f else 0.1f)
            if (state?.has(SecondaryEvolution.SAWTOOTH_BLADES) == true)
                roll(DamageType.KINETIC, 0.01f + state.bonus(WeaponStat.CRIT) + currentWeaponModifiers.criticalChanceBonus)
        }
        if (saw > 0 && source in listOf(WeaponId.CHOPPER, WeaponId.REPEATER))
            roll(DamageType.KINETIC, if (saw == 2) 0.19f else 0.07f)
    }

    private fun statusDamage(type: DamageType): Int {
        val extra = when (type) {
            DamageType.ELECTRIC -> if (weaponStates.any { it.rank(SecondaryEvolution.RESTRICTED_AREA) == 2 }) 5 else 0
            DamageType.HEAT -> if (weaponStates.any { it.rank(SecondaryEvolution.STABILIZED_PLASMA) == 2 }) 5 else 0
            DamageType.KINETIC -> if (weaponStates.any { it.rank(SecondaryEvolution.SAWTOOTH_BLADES) == 2 }) 5 else 0
            else -> 0
        }
        val saw = weaponState(WeaponId.CHOPPER)?.rank(SecondaryEvolution.SAW_LAUNCHER) ?: 0
        val multiplier = if (type == DamageType.KINETIC && saw > 0) (if (saw == 2) 1.75f else 1.25f) else 1f
        return (((if (type == DamageType.ELECTRIC) 3 else 10) + extra) * multiplier).roundToInt()
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
        doubleDamageRemaining = (doubleDamageRemaining - dt).coerceAtLeast(0f)
        currentWeaponModifiers = weaponModifiers
        elapsedSeconds += dt
        while (damageSamples.firstOrNull()?.let { it.time < elapsedSeconds - 10.0 } == true) damageSamples.removeFirst()
        mutableEffects.forEach { it.age += dt }
        mutableEffects.removeAll { it.age >= it.lifetime }
        mutablePickups.toList().forEach {
            it.age += dt
            if (it.age >= it.lifetime) {
                mutablePickups.remove(it)
                events += CombatEvent.PickupRemoved(it.id)
            }
        }
        mutableEnemies.filter { it.isAlive }.forEach { enemy ->
            enemy.statuses.toList().forEach { status ->
                status.remaining -= dt
                status.cooldown -= dt
                if (status.remaining > 0f && status.cooldown <= 0f) {
                    status.cooldown += 0.5f
                    dealDamage(enemy.id, statusDamage(status.type),
                        status.source, status.type, inflictStatus = false)
                    if (status.type == DamageType.ELECTRIC) enemies.filter {
                        it.isAlive && it.id != enemy.id && (it.position - enemy.position).length() <= 30f * if (weaponStates.any { state -> state.rank(SecondaryEvolution.RESTRICTED_AREA) == 2 }) 1.3f else 1f
                    }.forEach { dealDamage(it.id, statusDamage(status.type), status.source, status.type, inflictStatus = false) }
                }
            }
            enemy.statuses.removeAll { it.remaining <= 0f }
            if (!enemy.isAlive) return@forEach
            enemy.advance(dt, player.position)
            if ((enemy.position - player.position).length() <= enemy.radius + player.radius) {
                player.takeDamage(enemy.definition.contactDamage)
                die(enemy, awardExperience = false)
            }
        }
        mutableSecondaryWeapons.forEach { it.update(this, dt) }
        mutableActors.toList().forEach { it.update(this, dt); it.captureMotion(dt) }
        mutableActors.removeAll { it.finished }
        val actions = mainWeapon.update(dt, player.position, { id ->
            mutableEnemies.firstOrNull { it.id == id && it.isAlive }?.position
        }, weaponModifiers)
        actions.forEach { action -> when (action) {
            is WeaponAction.LaunchProjectile -> {
                if (action.startsMainWeaponFire)
                    mutableSecondaryWeapons.forEach { it.onMainWeaponFire(this, action.projectile) }
                launch(action.projectile)
            }
        } }
        val spent = mutableSetOf<Projectile>()
        mutableProjectiles.forEach { projectile ->
            projectile.advance(dt)
            if (projectile.penetrates) {
                val hits = mutableEnemies.filter { it.isAlive && it.id !in projectile.hitEnemyIds }.mapNotNull { enemy ->
                    sweptCircleHit(projectile.previousPosition - enemy.previousPosition,
                        projectile.position - enemy.position, projectile.radius + enemy.radius)?.let { enemy to it }
                }.sortedBy { it.second }
                hits.forEach { (enemy, fraction) ->
                    dealDamage(enemy.id, projectile.damage, projectile.source, projectile.damageType,
                        projectile.criticalChance, projectile.criticalDamageMultiplier,
                        hitPosition = enemy.previousPosition + (enemy.position - enemy.previousPosition) * fraction,
                        incomingAngle = atan2(projectile.velocity.y, projectile.velocity.x))
                    if (projectile.hitEnemyIds.isEmpty()) projectile.damage = (projectile.damage * projectile.ricochetDamageMultiplier).roundToInt()
                    projectile.hitEnemyIds += enemy.id
                }
                if (projectile.position.x !in -32f..width + 32f || projectile.position.y !in -32f..height + 32f) spent += projectile
                return@forEach
            }
            val hit = mutableEnemies.asSequence().filter { it.isAlive && it.id !in projectile.hitEnemyIds }.mapNotNull { enemy ->
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
                val incomingAngle = atan2(projectile.velocity.y, projectile.velocity.x)
                dealDamage(hit.first.id, projectile.damage, projectile.source, projectile.damageType,
                    projectile.criticalChance, projectile.criticalDamageMultiplier,
                    hitPosition = enemyPositionAtImpact, incomingAngle = incomingAngle)
                if (projectile.freezeChance > 0f && random.nextFloat() < projectile.freezeChance)
                    hit.first.frozenSeconds = 2f
                if (projectile.explosionRadius > 0f) {
                    areaDamage(enemyPositionAtImpact, projectile.explosionRadius, projectile.damage,
                        projectile.source, DamageType.EXPLOSIVE, excludedEnemy = hit.first.id,
                        criticalChance = projectile.criticalChance,
                        criticalMultiplier = projectile.criticalDamageMultiplier)
                    addEffect(WeaponEffect(enemyPositionAtImpact, enemyPositionAtImpact,
                        DamageType.EXPLOSIVE, projectile.explosionRadius, 0.3f))
                }
                projectile.hitEnemyIds += hit.first.id
                val next = if (projectile.ricochets > 0) enemies.filter {
                    it.isAlive && it.id !in projectile.hitEnemyIds && (it.position - enemyPositionAtImpact).length() <= 140f
                }.let { if (it.isEmpty()) null else it[random.nextInt(it.size)] } else null
                if (next != null) {
                    val direction = (next.position - enemyPositionAtImpact).normalized()
                    projectile.velocity = direction * projectile.velocity.length()
                    projectile.position = enemyPositionAtImpact + direction * (hit.first.radius + projectile.radius + 0.1f)
                    projectile.previousPosition = projectile.position
                    if (!projectile.hasRicocheted) { projectile.damage = (projectile.damage * projectile.ricochetDamageMultiplier).roundToInt(); projectile.hasRicocheted = true }
                    projectile.ricochets--
                } else spent += projectile
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

    private fun die(enemy: Enemy, awardExperience: Boolean, source: WeaponId? = null) {
        if (enemy.deathAge != null) return
        enemy.takeDamage(Int.MAX_VALUE)
        enemy.deathAge = 0f
        mainWeapon.targetDied(enemy.id, enemy.position)
        mutableBursts += DeathBurst(enemy.position, random)
        events += CombatEvent.Died(enemy.id, enemy.position)
        if (awardExperience) {
            kills++
            val state = if (source == WeaponId.REPEATER) (mainWeapon as? Repeater)?.upgrades
                else secondaryWeapons.firstOrNull { it.id == source }?.state
            state?.creditKills()
            grantExperience(config.progression.killExperience)
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
