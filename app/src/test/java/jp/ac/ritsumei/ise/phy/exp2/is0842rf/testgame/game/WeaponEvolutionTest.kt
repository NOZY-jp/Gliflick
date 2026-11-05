package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.SecondaryEvolution.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WeaponEvolutionTest {
    private fun world() = CombatWorld(CombatConfig(spawningEnabled = false), Random(42)).also { it.resize(400f, 500f) }
    private fun CombatWorld.weapon(id: WeaponId): SecondaryWeapon {
        acquireWeapon(id)
        return secondaryWeapons.first { it.id == id }
    }
    private fun event(text: String, position: Vec2 = Vec2(320f, 200f)) =
        CompletedCombatWord(WordDefinition("trigger", text, languageTag = "en"), 1, position)
    private fun CombatWorld.evolve(e: SecondaryEvolution) {
        val state = weaponState(e.weapon) ?: weapon(e.weapon).state
        state.creditKills(300)
        assertTrue(applyUpgrade(EvolutionCatalog.offers(state).first { it.evolution == e }))
    }

    @Test fun milestonesAreCumulative50Then150Then300AndStopAtThree() {
        for (id in WeaponId.entries) {
            val s = WeaponRunState(id)
            s.creditKills(49); assertFalse(s.canEvolve)
            s.creditKills(); assertEquals(1, s.pendingEvolutionChoices)
            s.evolve(EvolutionCatalog.available(s).first())
            assertEquals(150, s.nextEvolutionCredit)
            s.creditKills(99); assertFalse(s.canEvolve)
            s.creditKills(); assertEquals(1, s.pendingEvolutionChoices)
            s.evolve(EvolutionCatalog.available(s).first())
            s.creditKills(149); assertFalse(s.canEvolve)
            s.creditKills(); assertTrue(s.canEvolve)
            s.evolve(EvolutionCatalog.available(s).first())
            s.creditKills(10000)
            assertTrue(s.isEvolutionComplete); assertFalse(s.canEvolve)
        }
    }

    @Test fun eachBranchHasReachableRanksAndCanRunInTheRealSimulation() {
        for (branch in SecondaryEvolution.entries) {
            val w = world()
            val weapon = w.weapon(branch.weapon)
            repeat(branch.maxRank) {
                w.evolve(branch)
                assertEquals(it + 1, weapon.state.rank(branch))
                assertEquals(it + 1 < branch.maxRank, EvolutionCatalog.available(weapon.state).contains(branch))
            }
            w.spawnEnemy(Vec2(310f, 190f), EnemyDefinition(maxHp = 100000, speed = 0f))
            weapon.onWord(w, event("abecidofghijklmnopqyzvw"))
            weapon.onMainWeaponFire(w, Projectile(w.player.position, Vec2(200f, 0f)))
            repeat(360) { w.update(1f / 120f) }
            assertTrue(branch.name, w.weaponActors.all { it.position.x.isFinite() && it.position.y.isFinite() })
        }
    }

    @Test fun damageAndDamageOverTimeCreditOnlyTheWeaponThatActuallyKills() {
        val w = world(); val hunter = w.weapon(WeaponId.HUNTER).state
        val victim = w.spawnEnemy(Vec2(350f, 80f), EnemyDefinition(maxHp = 3, speed = 0f))
        victim.statuses += EnemyStatus(DamageType.ELECTRIC, WeaponId.HUNTER)
        w.update(0.51f)
        assertFalse(victim.isAlive); assertEquals(1, hunter.evolutionCredit)
        assertEquals(0, w.weaponState(WeaponId.REPEATER)!!.evolutionCredit)
        w.update(1f); assertEquals(1, hunter.evolutionCredit)
        val contact = w.spawnEnemy(w.player.position, EnemyDefinition(maxHp = 3, speed = 0f))
        w.update(0.01f); assertFalse(contact.isAlive); assertEquals(1, hunter.evolutionCredit)
    }

    @Test fun simultaneousEvolutionRewardsPauseAndStayWithTheirWeaponAcrossRerolls() {
        val w = world()
        w.weapon(WeaponId.SPECTRE).state.creditKills(300)
        w.weapon(WeaponId.HUNTER).state.creditKills(50)
        val s = CombatSession(listOf(WordDefinition("word", "ひかり", "光")), w, Random(3))
        assertEquals(4, s.pendingEvolutionChoices)
        assertTrue(s.handleSpace())
        assertEquals(WeaponId.SPECTRE, s.upgradeSelection!!.offers.first().weapon)
        val time = w.elapsedSeconds; s.update(0.2f); assertEquals(time, w.elapsedSeconds, 0.0)
        assertTrue(s.rerollUpgrades())
        assertTrue(s.upgradeSelection!!.offers.all { it.weapon == WeaponId.SPECTRE })
        repeat(3) {
            s.onInput(s.upgradeSelection!!.states[0].inputText.map { jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken.Stroke(it.toString()) }, false); repeat(200) { s.update(0.01f) }
            assertNotNull(s.upgradeSelection)
        }
        assertEquals(WeaponId.HUNTER, s.upgradeSelection!!.offers.first().weapon)
        assertEquals(1, s.pendingEvolutionChoices)
    }

    @Test fun learningUpgradesExistForAllWeaponsAndDisappearAtTheCap() {
        for (id in WeaponId.entries.filter { it != WeaponId.REPEATER }) {
            val w = world(); val s = w.weapon(id).state
            val offers = UpgradeCatalog.pool(w).filter { it.weapon == id && it.effects.any { e -> e.stat == WeaponStat.EVOLUTION } }
            assertEquals(3, offers.size)
            offers.forEach { assertTrue(w.applyUpgrade(it)) }
            assertEquals(60, s.evolutionCredit); assertTrue(s.canEvolve)
            s.creditKills(300)
            repeat(3) { s.evolve(EvolutionCatalog.available(s).first()) }
            assertFalse(UpgradeCatalog.pool(w).any { it.weapon == id && it.effects.any { e -> e.stat == WeaponStat.EVOLUTION } })
        }
    }

    @Test fun globalModifiersReachExistingAndFutureWeaponsWithoutDoubleCountingLocalUpgrades() {
        val w = world(); val sentry = w.weapon(WeaponId.SENTRY)
        val turret = w.weaponActors.single()
        w.evolve(WATCHER_SUPREME)
        assertEquals(120f, turret.range, 0.001f)
        val tesla = w.weapon(WeaponId.TESLA_MINE)
        assertEquals(1.5f, tesla.state.scale(WeaponStat.RANGE), 0.001f)
        tesla.state.improve(listOf(StatDelta(WeaponStat.RANGE, 0.1f)))
        assertEquals(1.6f, tesla.state.scale(WeaponStat.RANGE), 0.001f)
        w.evolve(STORM_SOVEREIGN)
        assertEquals(1.25f, sentry.state.scale(WeaponStat.RATE), 0.001f)
        w.weapon(WeaponId.SPECTRE); w.evolve(STALKING_ALGORITHMS)
        assertEquals(1.35f, w.weaponState(WeaponId.SPECTRE)!!.scale(WeaponStat.SPEED), 0.001f)
    }

    @Test fun eliteDroneCountersStartWithEvolutionAndCreateTheRightForm() {
        for ((id, branch, glyph, count) in listOf(
            listOf(WeaponId.SPECTRE, SUPERCHARGED, "a", 20),
            listOf(WeaponId.HUNTER, HUNTER_ELITE, "i", 12))) {
            val w = world(); val weapon = w.weapon(id as WeaponId)
            repeat(3) { weapon.onWord(w, event(glyph as String)) }
            w.evolve(branch as SecondaryEvolution)
            repeat(count as Int) { weapon.onWord(w, event(glyph as String)) }
            val actors = w.weaponActors
            assertEquals(1, actors.count { if (it is HunterActor) it.enhanced else (it as DroneActor).enhanced })
            val elite = actors.last(); val normal = actors.first()
            assertEquals(normal.damage * 2, elite.damage)
            assertEquals(if (id == WeaponId.HUNTER) 8.75f else 10.5f, elite.remaining, 0.001f)
        }
    }

    @Test fun sentryAndGuardianTeamCreateSeparatePersistentActors() {
        val w = world(); w.evolve(DEFENSE_GRID); w.evolve(DEFENSE_GRID)
        assertEquals(3, w.weaponActors.filterIsInstance<SentryActor>().size)
        w.evolve(GUARDIAN_TEAM); w.evolve(GUARDIAN_TEAM)
        val drones = w.weaponActors.filterIsInstance<GuardianActor>()
        assertEquals(3, drones.size); assertEquals(3, drones.map { it.position }.distinct().size)
        assertTrue(drones.all { it.damage == 10 })
        drones.forEach { it.remaining = 0f }
        val fuel = w.spawnPickup(PickupKind.FUEL, Vec2(50f, 50f)); w.activatePickup(fuel.id)
        assertTrue(drones.all { it.remaining > 0f })
    }

    @Test fun oscillatorPenetratesEveryEnemyAlongOneSweptStepAtHalfDamageAfterFirst() {
        val w = world()
        val first = w.spawnEnemy(Vec2(260f, 250f), EnemyDefinition(maxHp = 1000, speed = 0f))
        val second = w.spawnEnemy(Vec2(290f, 250f), EnemyDefinition(maxHp = 1000, speed = 0f))
        w.launch(Projectile(w.player.position, Vec2(1000f, 0f), 20, source = WeaponId.OSCILLATOR,
            damageType = DamageType.HEAT, penetrates = true))
        w.update(0.12f)
        assertEquals(980, first.hp); assertEquals(990, second.hp)
        assertEquals(1, w.projectiles.size)
        w.update(0.01f); assertEquals(980, first.hp)
    }

    @Test fun hunterBoltsAreProjectilesAndChainDamageDoesNotHalve() {
        val w = world(); val weapon = w.weapon(WeaponId.HUNTER)
        w.evolve(ELECTRIFIED_BOLTS); w.evolve(CHAIN_LIGHTNING)
        val a = w.spawnEnemy(Vec2(270f, 250f), EnemyDefinition(maxHp = 1000, speed = 0f))
        val b = w.spawnEnemy(Vec2(295f, 250f), EnemyDefinition(maxHp = 1000, speed = 0f))
        weapon.onWord(w, event("i", a.position)); repeat(90) { w.update(1f / 120f) }
        assertTrue(a.hp < 1000); assertEquals(a.hp, b.hp)
    }

    @Test fun boomerangReturnsAndSawLauncherChangesShapeAndCount() {
        val w = world(); val weapon = w.weapon(WeaponId.CHOPPER)
        w.evolve(BOOMERANG)
        weapon.onMainWeaponFire(w, Projectile(w.player.position, Vec2(150f, 0f)))
        val blade = w.weaponActors.single(); var furthest = 0f
        repeat(480) { w.update(1f / 120f); furthest = maxOf(furthest, blade.position.x) }
        assertTrue(furthest > w.width); assertTrue(w.weaponActors.isEmpty())
        w.evolve(SAW_LAUNCHER); w.evolve(SAW_LAUNCHER)
        weapon.onMainWeaponFire(w, Projectile(w.player.position, Vec2(150f, 0f)))
        assertEquals(4, w.weaponActors.size)
        assertTrue(w.weaponActors.filterIsInstance<MovingBladeActor>().all { it.saw && it.range < 15f })
    }

    @Test fun meteorAlternativesAndContinuousBombardmentAreMutuallyExclusive() {
        val w = world(); w.evolve(SURVEILLANCE)
        assertFalse(EvolutionCatalog.available(w.weaponState(WeaponId.METEOR)!!).contains(RELENTLESS))
        w.update(2.1f); assertEquals(2, w.pickups.count { it.kind == PickupKind.METEOR })
        w.activatePickup(w.pickups.first().id); assertFalse(w.hasPickup(PickupKind.METEOR))
        val other = world(); val weapon = other.weapon(WeaponId.METEOR)
        other.evolve(RELENTLESS)
        assertFalse(EvolutionCatalog.available(weapon.state).contains(SURVEILLANCE))
        val field = other.weaponActors.single() as FieldActor
        val start = field.position
        weapon.onWord(other, event("a", Vec2(310f, 120f)))
        repeat(720) { other.update(1f / 120f) }
        assertFalse(field.finished); assertTrue((start - field.position).length() > 20f)
        assertFalse(other.hasPickup(PickupKind.METEOR))
    }

    @Test fun areaAttacksUseWeaponCritModifiersAndAlternativeMarkersAreRemovedExactlyOnce() {
        val w = world(); val weapon = w.weapon(WeaponId.METEOR)
        weapon.state.improve(listOf(StatDelta(WeaponStat.CRIT, 1f), StatDelta(WeaponStat.CRIT_MULTIPLIER, 0.5f)))
        val victim = w.spawnEnemy(Vec2(300f, 100f), EnemyDefinition(maxHp = 1000, speed = 0f))
        w.areaDamage(victim.position, 10f, 10, WeaponId.METEOR, DamageType.EXPLOSIVE)
        assertEquals(975, victim.hp)
        w.evolve(SURVEILLANCE); w.update(2.1f)
        val ids = w.pickups.map { it.id }; assertEquals(2, ids.size)
        w.drainEvents()
        w.activatePickup(ids.first())
        val removed = w.drainEvents().filterIsInstance<CombatEvent.PickupRemoved>().map { it.id }
        assertEquals(ids.sorted(), removed.sorted())
    }

    @Test fun orbiterTwinRotatesOppositelyAndTetherDealsDamageBetweenOrbAndPlayer() {
        val w = world(); val weapon = w.weapon(WeaponId.ORBITER)
        w.evolve(DOPPELGANGER); w.evolve(TETHER)
        val victim = w.spawnEnemy(Vec2(250f, 250f), EnemyDefinition(maxHp = 1000, speed = 0f))
        weapon.onWord(w, event("n", Vec2(300f, 250f))); w.update(0.01f)
        val orbs = w.weaponActors.filterIsInstance<OrbitActor>()
        assertEquals(2, orbs.size)
        assertTrue(orbs[0].position.y > 250f && orbs[1].position.y < 250f)
        assertTrue(victim.hp in 950..979)
    }

    @Test fun selfDestructUsesTheSpectreAsItsExplosionSource() {
        val w = world(); val weapon = w.weapon(WeaponId.SPECTRE)
        w.evolve(SELF_DESTRUCT)
        val victim = w.spawnEnemy(Vec2(300f, 100f), EnemyDefinition(maxHp = 40, speed = 0f))
        val drone = DroneActor(weapon.state, victim.position, false)
        drone.remaining = 0.001f; w.addActor(drone); w.update(0.01f)
        assertFalse(victim.isAlive); assertEquals(301, weapon.state.evolutionCredit)
        assertTrue(w.weaponEffects.any { it.source == WeaponId.SPECTRE && it.type == DamageType.EXPLOSIVE })
    }

    @Test fun extraTriggerLettersAreAllocatedOnceAcrossTheRunAndActuallyActivateWeapons() {
        val w = world(); val tesla = w.weapon(WeaponId.TESLA_MINE)
        val blade = w.weapon(WeaponId.BLADE_TRAP)
        val orbiter = w.weapon(WeaponId.ORBITER)
        w.evolve(ABUNDANT_COILS); w.evolve(BLADE_REPOSITORY)
        assertEquals("yz", tesla.state.extraTriggerLetters)
        assertEquals("w", blade.state.extraTriggerLetters)
        assertFalse(w.evolutionOffers(WeaponId.ORBITER).any { it.evolution == CONTINUUM })
        assertFalse(w.evolutionOffers(WeaponId.TESLA_MINE).any { it.evolution == ABUNDANT_COILS })
        tesla.onWord(w, event("y")); blade.onWord(w, event("w")); orbiter.onWord(w, event("y"))
        assertEquals(1, w.weaponActors.count { it.source == WeaponId.TESLA_MINE })
        assertEquals(1, w.weaponActors.count { it.source == WeaponId.BLADE_TRAP })
        assertFalse(w.weaponActors.any { it.source == WeaponId.ORBITER })
    }

    @Test fun specialFuelReplacesRatherThanStacksBonusesAndExpiresOnSimulationTime() {
        val w = world(); val guardian = w.weapon(WeaponId.GUARDIAN)
        w.evolve(EXPERIMENTAL_FUEL)
        w.activatePickup(w.spawnPickup(PickupKind.FUEL, Vec2(60f, 60f), WeaponStat.DAMAGE).id)
        assertEquals(1.5f, guardian.state.scale(WeaponStat.DAMAGE), 0.001f)
        w.activatePickup(w.spawnPickup(PickupKind.FUEL, Vec2(60f, 60f), WeaponStat.SPEED).id)
        assertEquals(1f, guardian.state.scale(WeaponStat.DAMAGE), 0.001f)
        assertEquals(2f, guardian.state.scale(WeaponStat.SPEED), 0.001f)
        w.evolve(EXPERIMENTAL_FUEL)
        w.activatePickup(w.spawnPickup(PickupKind.FUEL, Vec2(60f, 60f), WeaponStat.RANGE).id)
        assertEquals(1.75f, guardian.state.scale(WeaponStat.RANGE), 0.001f)
        repeat(110) { w.update(0.1f) }; assertEquals(1.75f, guardian.state.scale(WeaponStat.RANGE), 0.001f)
        repeat(20) { w.update(0.1f) }; assertEquals(1f, guardian.state.scale(WeaponStat.RANGE), 0.001f)
    }

    @Test fun conversionAndGlobalElementDamageChangeRealHits() {
        val w = world(); w.evolve(SOLID_CORE); w.evolve(SOLID_CORE)
        val sentry = w.weaponActors.single()
        assertEquals(DamageType.KINETIC, sentry.type); assertEquals(50, sentry.damage)
        val victim = w.spawnEnemy(Vec2(350f, 100f), EnemyDefinition(maxHp = 1000, speed = 0f))
        w.dealDamage(victim.id, 50, WeaponId.SPECTRE, DamageType.KINETIC, inflictStatus = false)
        assertEquals(940, victim.hp)
        w.dealDamage(victim.id, 50, WeaponId.OSCILLATOR, DamageType.HEAT, inflictStatus = false)
        assertEquals(890, victim.hp)
    }

    @Test fun recursiveMinesSpawnWithoutTypingAndSeekersCloseTheDistance() {
        val w = world(); w.evolve(RECURSIVE_MINES); w.evolve(HUNTER_SEEKER)
        val target = w.spawnEnemy(Vec2(360f, 100f), EnemyDefinition(maxHp = 100000, speed = 0f))
        repeat(121) { w.update(0.1f) }
        val mine = w.weaponActors.single { it.source == WeaponId.MINEFIELD }
        val before = (mine.position - target.position).length()
        w.update(0.2f)
        assertTrue((mine.position - target.position).length() < before - 10f)
        assertEquals(22.5f, mine.range, 0.001f)
    }

    @Test fun globalStatusEvolutionsIncreaseTheActualBleedingAndIgniteTicks() {
        val w = world(); w.evolve(SAWTOOTH_BLADES); w.evolve(SAWTOOTH_BLADES)
        w.evolve(STABILIZED_PLASMA); w.evolve(STABILIZED_PLASMA)
        val a = w.spawnEnemy(Vec2(360f, 80f), EnemyDefinition(maxHp = 1000, speed = 0f))
        val b = w.spawnEnemy(Vec2(360f, 150f), EnemyDefinition(maxHp = 1000, speed = 0f))
        a.statuses += EnemyStatus(DamageType.KINETIC, WeaponId.SPECTRE)
        b.statuses += EnemyStatus(DamageType.HEAT, WeaponId.SENTRY)
        w.update(0.51f)
        assertEquals(985, a.hp); assertEquals(985, b.hp)
    }
}
