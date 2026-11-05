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
    fun choose(elapsedSeconds: Double, playerLevel: Int, random: Random, living: List<Enemy>): EnemyDefinition =
        choose(elapsedSeconds, playerLevel, random)
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
    data object RerollCollected : CombatEvent
}

/** Single-threaded domain simulation. Rendering and keyboard transport do not live here. */
class CombatWorld(
    val config: CombatConfig = CombatConfig(),
    private val random: Random = Random.Default,
    private val spawnPolicy: EnemySpawnPolicy = MixedEnemySpawnPolicy(populationLimit = config.maxEnemiesAlive),
    val mainWeapon: MainWeapon = Repeater(),
    private val spawnDensity: EnemySpawnDensity = (spawnPolicy as? EnemySpawnRate)
        ?.let(::PoissonEnemySpawnDensity) ?: RandomEnemySpawnDensity(),
    val bosses: BossDirector = BossDirector(),
    private val rerollDrops: RerollDropPolicy = RerollDropPolicy()
) {
    val player = Player(config.playerMaxHp)
    init {
        (mainWeapon as? Repeater)?.upgrades?.globalBonus = { stat ->
            EvolutionEffects.globalBonus(weaponStates, WeaponId.REPEATER, stat)
        }
    }
    private val mutableEnemies = mutableListOf<Enemy>()
    private val mutableBarriers = mutableListOf<WarderBarrier>()
    val barriers: List<WarderBarrier> get() = mutableBarriers
    val livingPopulation get() = mutableEnemies.filter { it.isAlive }.sumOf { it.populationCost }
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
    /** Public demo threat budget; arrival timing and Drone-tier gates retain local tuning. */
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
            bosses.resize(sx, sy)
            mutableEnemies.forEach {
                it.position = it.position.scaled(sx, sy)
                it.previousPosition = it.position
                it.deploymentPosition = it.deploymentPosition?.scaled(sx, sy)
                it.flightDirection = it.flightDirection.scaled(sx, sy).normalized()
                it.releaseOffset = it.releaseOffset.scaled(sx, sy)
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
        bosses.onViewportChanged(this)
    }

    fun spawnEnemy(position: Vec2, definition: EnemyDefinition = spawnPolicy.choose(elapsedSeconds, level, random, enemies)): Enemy =
        Enemy(nextEnemyId++, definition, position).also {
        it.deploymentPosition = clampPoint(position, it.radius + 18f)
        if (definition.kind == EnemyKind.SHOOTER) it.initialAttackDelay = (50f + random.nextFloat() * 100f) / 60f
        mutableEnemies += it
        if (definition.kind == EnemyKind.WARDER) mutableBarriers += WarderBarrier(it)
        events += CombatEvent.Spawned(it)
    }

    fun target(enemyId: Long) {
        mutableEnemies.firstOrNull { it.id == enemyId && it.isAttackable }?.let { mainWeapon.target(it.id, it.aimPosition) }
    }

    fun completeWord(word: jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition, enemyId: Long) {
        val enemy = mutableEnemies.firstOrNull { it.id == enemyId && it.isAttackable } ?: return
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
        enemies.filter { it.isAlive && it.definition.bossKind != BossKind.POET && (it.position - position).length() <= range + it.radius }
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
        val pickup = CombatPickup(nextPickupId++, kind, position, lifetime = if(kind == PickupKind.REROLL) 10f else 14f, fuelBonus = fuelBonus)
        mutablePickups += pickup
        events += CombatEvent.PickupSpawned(pickup)
        return pickup
    }
    fun activatePickup(id: Long) {
        val pickup = mutablePickups.firstOrNull { it.id == id } ?: return
        if (pickup.kind == PickupKind.REROLL) events += CombatEvent.RerollCollected
        mutableSecondaryWeapons.forEach { it.activatePickup(this, pickup) }
        if (mutablePickups.remove(pickup)) events += CombatEvent.PickupRemoved(id)
    }
    fun dps(source: WeaponId): Float = damageSamples.filter { it.source == source }.sumOf { it.amount } /
        elapsedSeconds.coerceIn(1.0, 10.0).toFloat()

    fun areaDamage(position: Vec2, radius: Float, damage: Int, source: WeaponId, type: DamageType,
                   allowCrit: Boolean = true, excludedEnemy: Long? = null,
                   criticalChance: Float? = null, criticalMultiplier: Float? = null,
                   excludedPart: Int? = null) {
        val contacts = enemies.filter { it.isAlive }.flatMap { enemy ->
            enemy.hitCircles().filter { circle ->
                (enemy.id != excludedEnemy || (excludedPart != null && circle.part != excludedPart)) &&
                    (circle.position - position).length() <= radius + circle.radius
            }.map { enemy to it.part }
        }
        contacts.forEach { (original, part) ->
            val target = resolvePart(original, part) ?: return@forEach
            dealDamage(target.first.id, damage, source, type,
                if (allowCrit) criticalChance ?: (0.01f + (weaponState(source)?.bonus(WeaponStat.CRIT) ?: 0f) +
                    currentWeaponModifiers.criticalChanceBonus) else 0f,
                criticalMultiplier ?: (2f + (weaponState(source)?.bonus(WeaponStat.CRIT_MULTIPLIER) ?: 0f)),
                hitPart = target.second)
        }
    }

    fun damageEnemy(enemyId: Long, damage: Int, incomingAngle: Float? = null) {
        dealDamage(enemyId, damage, WeaponId.REPEATER, DamageType.KINETIC, inflictStatus = false, incomingAngle = incomingAngle)
    }

    fun dealDamage(enemyId: Long, damage: Int, source: WeaponId, type: DamageType,
                   criticalChance: Float = 0f, criticalMultiplier: Float = 1.5f,
                   inflictStatus: Boolean = true, hitPosition: Vec2? = null, incomingAngle: Float? = null,
                   hitPart: Int = 0, projectileImpact: Boolean = false) {
        mutableEnemies.firstOrNull { it.id == enemyId && it.isAlive }?.let {
            if (isProtected(it) || it.definition.bossKind == BossKind.POET) return
            val bleeding = it.statuses.any { status -> status.type == DamageType.KINETIC && status.partIndex == hitPart }
            val bloodbathRank = weaponState(WeaponId.CHOPPER)?.rank(SecondaryEvolution.BLOODBATH) ?: 0
            val bloodBonus = if (source == WeaponId.CHOPPER && bleeding && bloodbathRank > 0)
                (if (bloodbathRank == 2) 0.25f else 0.1f) else 0f
            val typedDamage = damage * (1f + EvolutionEffects.damageTypeBonus(weaponStates, type)) *
                (1f + bloodBonus) * if (doubleDamageRemaining > 0f) 2f else 1f
            val actual = (typedDamage * if (random.nextFloat() < criticalChance.coerceIn(0f, 1f)) criticalMultiplier else 1f).roundToInt()
            val applied = it.takeDamage(actual, hitPart)
            if (applied > 0) {
                val angle = incomingAngle ?: atan2(it.position.y - player.position.y, it.position.x - player.position.x)
                mutableHitFeedback += HitFeedback(hitPosition ?: it.position, applied, it.radius, angle, random,
                    showRing = projectileImpact && source == WeaponId.REPEATER)
                damageSamples += DamageSample(elapsedSeconds, source, applied)
                if (inflictStatus && it.isAlive) applyAttackStatuses(it, source, type, hitPart)
            }
            if (!it.isAlive || it.splitterHealth?.hasBrokenPart == true) {
                if (source == WeaponId.CHOPPER && bleeding && bloodbathRank > 0 &&
                    random.nextFloat() < if (bloodbathRank == 2) 0.25f else 0.1f) doubleDamageRemaining = 10f
                if (it.definition.splitter != null) releaseSplitter(it, source, true)
                else die(it, awardExperience = true, source = source)
            }
        }
    }

    private fun applyAttackStatuses(enemy: Enemy, source: WeaponId, type: DamageType, part: Int = 0) {
        val state = weaponState(source)
        val saw = weaponState(WeaponId.CHOPPER)?.rank(SecondaryEvolution.SAW_LAUNCHER) ?: 0
        fun roll(type: DamageType, chance: Float) {
            if (chance <= 0f || random.nextFloat() >= chance) return
            if (type == DamageType.COLD) { enemy.frozenSeconds = 2f; return }
            val existing = enemy.statuses.firstOrNull { it.type == type && it.partIndex == part }
            if (existing != null) existing.remaining = 5f else enemy.statuses += EnemyStatus(type, source, partIndex = part)
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
        threat = Threat(EnemySpawnDifficulty.at(elapsedSeconds, level).threatBudget.toDouble())
        mutableBarriers.removeAll { !it.isAlive }
        mutableBarriers.forEach { it.advance(dt) }
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
            if (enemy.parentId != null && enemy.activationProgress < 1f &&
                enemies.none { it.id == enemy.parentId && it.isAlive }) {
                die(enemy, awardExperience = false)
                return@forEach
            }
            enemy.statuses.toList().forEach { status ->
                status.remaining -= dt
                status.cooldown -= dt
                if (status.remaining > 0f && status.cooldown <= 0f) {
                    status.cooldown += 0.5f
                    dealDamage(enemy.id, statusDamage(status.type),
                        status.source, status.type, inflictStatus = false, hitPart = status.partIndex)
                    if (status.type == DamageType.ELECTRIC) enemies.filter {
                        it.isAlive && it.id != enemy.id && (it.position - enemy.position).length() <= 30f * if (weaponStates.any { state -> state.rank(SecondaryEvolution.RESTRICTED_AREA) == 2 }) 1.3f else 1f
                    }.forEach { dealDamage(it.id, statusDamage(status.type), status.source, status.type, inflictStatus = false) }
                }
            }
            enemy.statuses.removeAll { it.remaining <= 0f }
            if (!enemy.isAlive) return@forEach
            enemy.advance(dt, player.position)
            enemy.drainActions().forEach { action -> when (action) {
                is EnemyAction.FireMissile -> launchEnemyMissile(enemy, action.direction)
            } }
            val contact = if (enemy.definition.bossKind == null && enemy.activationProgress >= 1f) enemy.hitCircles().firstOrNull {
                sweptCircleHit(it.previousPosition - player.position, it.position - player.position,
                    it.radius + player.radius) != null
            } else null
            if (contact != null) {
                player.takeDamage(enemy.definition.contactDamage)
                if (enemy.definition.splitter != null) {
                    enemy.takeDamage(Int.MAX_VALUE, contact.part)
                    releaseSplitter(enemy, null, false)
                } else die(enemy, awardExperience = false)
            }
        }
        mutableSecondaryWeapons.forEach { it.update(this, dt) }
        mutableActors.toList().forEach { it.update(this, dt); it.captureMotion(dt) }
        mutableActors.removeAll { it.finished }
        val actions = mainWeapon.update(dt, player.position, { id ->
            mutableEnemies.firstOrNull { it.id == id && it.isAlive }?.aimPosition
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
            val barrierFraction = barriers.filter { it.isAlive }.mapNotNull { it.hitFraction(projectile) }.minOrNull()
            if (projectile.penetrates) {
                val hits = projectileHits(projectile).filter { barrierFraction == null || it.fraction < barrierFraction }
                hits.forEach { hit ->
                    if ((hit.enemy.id to hit.part) in projectile.hitPartKeys) return@forEach
                    val target = resolvePart(hit.enemy, hit.part) ?: return@forEach
                    projectile.hitPartKeys += hit.enemy.id to hit.part
                    projectile.hitPartKeys += target.first.id to target.second
                    dealDamage(target.first.id, projectile.damage, projectile.source, projectile.damageType,
                        projectile.criticalChance, projectile.criticalDamageMultiplier,
                        hitPosition = hit.enemy.previousPosition + (hit.enemy.position - hit.enemy.previousPosition) * hit.fraction,
                        incomingAngle = atan2(projectile.velocity.y, projectile.velocity.x), hitPart = target.second, projectileImpact = true)
                    if (projectile.hitEnemyIds.isEmpty()) projectile.damage = (projectile.damage * projectile.ricochetDamageMultiplier).roundToInt()
                    projectile.hitEnemyIds += target.first.id
                }
                if (barrierFraction != null || outside(projectile.position)) spent += projectile
                return@forEach
            }
            val collision = projectileHits(projectile).firstOrNull()
            if (barrierFraction != null && (collision == null || barrierFraction <= collision.fraction)) {
                spent += projectile
                return@forEach
            }
            val hit = collision?.let { it.enemy to it.fraction }
            if (hit != null) {
                val impactPosition = projectile.previousPosition +
                    (projectile.position - projectile.previousPosition) * hit.second
                projectile.position = impactPosition
                val enemyPositionAtImpact = hit.first.previousPosition +
                    (hit.first.position - hit.first.previousPosition) * hit.second
                val incomingAngle = atan2(projectile.velocity.y, projectile.velocity.x)
                dealDamage(hit.first.id, projectile.damage, projectile.source, projectile.damageType,
                    projectile.criticalChance, projectile.criticalDamageMultiplier,
                    hitPosition = enemyPositionAtImpact, incomingAngle = incomingAngle, hitPart = collision!!.part, projectileImpact = true)
                if (projectile.freezeChance > 0f && random.nextFloat() < projectile.freezeChance)
                    hit.first.frozenSeconds = 2f
                if (projectile.explosionRadius > 0f) {
                    areaDamage(enemyPositionAtImpact, projectile.explosionRadius, projectile.damage,
                        projectile.source, DamageType.EXPLOSIVE, excludedEnemy = hit.first.id,
                        criticalChance = projectile.criticalChance,
                        criticalMultiplier = projectile.criticalDamageMultiplier, excludedPart = collision!!.part)
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
        val livingCount = livingPopulation
        if (config.spawningEnabled && livingCount < config.maxEnemiesAlive &&
            arrivalCapacity(enemies, config.maxEnemiesAlive) > 0 &&
            spawnDensity.shouldSpawn(dt * bosses.arrivalRules.rateMultiplier, elapsedSeconds, level, random, enemies)) {
            val definition = spawnPolicy.choose(elapsedSeconds, level, random, enemies)
            if ((definition.splitter?.partCount ?: 1) <= arrivalCapacity(enemies, config.maxEnemiesAlive))
                spawnEnemy(spawnPosition(definition.radius * definition.size), definition)
        }
        bosses.update(this, dt)
    }

    fun isWarderAuraActive(barrier: WarderBarrier): Boolean = barrier.isAlive && !outside(barrier.owner.position,barrier.owner.radius)

    fun isProtected(enemy: Enemy): Boolean = enemy.definition.bossKind == null && enemy.definition.kind != EnemyKind.WARDER && enemy.isAlive &&
        barriers.any { isWarderAuraActive(it) &&
            (enemy.position - it.owner.position).length() < it.protectionRadius }

    private fun outside(point: Vec2, margin: Float = 32f) =
        point.x !in -margin..width + margin || point.y !in -margin..height + margin

    private data class ProjectileContact(val enemy: Enemy, val part: Int, val fraction: Float)
    private fun projectileHits(projectile: Projectile): List<ProjectileContact> = enemies
        .filter { it.isAlive && (it.id !in projectile.hitEnemyIds ||
            (projectile.penetrates && it.definition.splitter != null)) }.flatMap { enemy ->
            enemy.hitCircles().filter { (enemy.id to it.part) !in projectile.hitPartKeys }.mapNotNull { circle ->
                sweptCircleHit(projectile.previousPosition - circle.previousPosition,
                    projectile.position - circle.position, projectile.radius + circle.radius)
                    ?.let { ProjectileContact(enemy, circle.part, it) }
            }
        }.sortedBy { it.fraction }

    private fun launchEnemyMissile(shooter: Enemy, direction: Vec2) {
        if (!shooter.isAlive || livingPopulation >= config.maxEnemiesAlive) return
        val definition = EnemyCatalog.definition(EnemyKind.SHOOTER_MISSILE, shooter.definition.tier)
        val offset = listOf(60f, 70f, 80f, 85f, 85f)[shooter.definition.tier - 1] * EnemyCatalog.UNIT
        val position = shooter.position + direction * offset
        spawnEnemy(position, definition).also {
            it.flightDirection = direction
            it.parentId = shooter.id
            it.spawnOrigin = shooter.spawnOrigin
        }
    }

    private fun releaseSplitter(enemy: Enemy, source: WeaponId?, award: Boolean) {
        if (enemy.deathAge != null) return
        val partHealth = enemy.splitterHealth!!.partHealth.toList()
        val circles = enemy.hitCircles()
        // Retire the formation before inserting survivors; their population slots were reserved.
        die(enemy, awardExperience = award, source = source,
            lastAim = circles.firstOrNull { partHealth[it.part] == 0 }?.position ?: enemy.position)
        circles.forEach { circle ->
            val hp = partHealth[circle.part]
            if (hp <= 0) return@forEach
            val definition = EnemyCatalog.definition(EnemyKind.SPLITTER_SHARD, enemy.definition.tier)
            spawnEnemy(circle.position, definition).also { shard ->
                enemy.releasedPartIds[circle.part] = shard.id
                projectiles.filter { (enemy.id to circle.part) in it.hitPartKeys }.forEach { projectile ->
                    projectile.hitEnemyIds += shard.id
                    projectile.hitPartKeys += shard.id to 0
                }
                shard.takeDamage(definition.maxHp - hp)
                shard.frozenSeconds = enemy.frozenSeconds
                shard.statuses += enemy.statuses.filter { it.partIndex == circle.part && it.remaining > 0f }.map {
                    EnemyStatus(it.type, it.source, it.remaining).also { copy -> copy.cooldown = it.cooldown }
                }
                shard.angle = enemy.angle + circle.part * 2f * kotlin.math.PI.toFloat() / circles.size
                shard.releaseOffset = (circle.position - enemy.position).normalized() * (12f + random.nextFloat() * 20f)
                shard.spawnOrigin = enemy.spawnOrigin
            }
        }
    }

    private fun resolvePart(enemy: Enemy, part: Int): Pair<Enemy, Int>? {
        if (enemy.isAlive) return enemy to part
        val released = enemy.releasedPartIds[part] ?: return null
        return enemies.firstOrNull { it.id == released && it.isAlive }?.let { it to 0 }
    }

    /** Keep the existing global body cap when a scheduled boss enters a full arena. */
    internal fun prepareBossSlots(count: Int) {
        require(count <= config.maxEnemiesAlive)
        while(livingPopulation + count > config.maxEnemiesAlive) {
            val farthest=enemies.filter { it.isAlive && it.definition.bossKind == null }
                .maxByOrNull { (it.position-player.position).length() } ?: break
            die(farthest, awardExperience=false)
        }
    }

    internal fun defeatBossBody(enemy: Enemy) = die(enemy, awardExperience = true)

    private fun die(enemy: Enemy, awardExperience: Boolean, source: WeaponId? = null,
                    lastAim: Vec2 = enemy.aimPosition) {
        if (enemy.deathAge != null) return
        enemy.takeDamage(Int.MAX_VALUE)
        enemy.deathAge = 0f
        mutableBarriers.removeAll { it.owner.id == enemy.id }
        mainWeapon.targetDied(enemy.id, lastAim)
        mutableBursts += DeathBurst(enemy.position, random)
        events += CombatEvent.Died(enemy.id, enemy.position)
        if (awardExperience) {
            if (config.spawningEnabled && enemy.definition.bossKind == null && rerollDrops.onDeath(elapsedSeconds, random))
                spawnPickup(PickupKind.REROLL, clampPoint(enemy.position, 38f))
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
        val rules=bosses.arrivalRules
        val edge=if(rules.chooseEdgeOnce) rules.edges[random.nextInt(rules.edges.size)] else null
        fun candidate(): Vec2 = when (edge ?: rules.edges[random.nextInt(rules.edges.size)]) {
            ArrivalEdge.TOP -> Vec2(across(width), -margin)
            ArrivalEdge.RIGHT -> Vec2(width + margin, across(height))
            ArrivalEdge.BOTTOM -> Vec2(across(width), height + margin)
            ArrivalEdge.LEFT -> Vec2(-margin, across(height))
        }
        return List(32) { candidate() }.maxBy { point ->
            minOf((point - player.position).length(),
                mutableEnemies.filter { it.isAlive }
                    .minOfOrNull { (point - it.position).length() } ?: Float.MAX_VALUE)
        }
    }

    companion object { const val DEATH_FADE_SECONDS = 0.14f }
}
