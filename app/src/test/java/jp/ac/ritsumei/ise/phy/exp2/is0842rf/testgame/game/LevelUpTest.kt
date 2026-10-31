package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LevelUpTest {
    private fun world() = CombatWorld(CombatConfig(spawningEnabled = false), Random(7)).also { it.resize(400f, 500f) }
    private fun session() = CombatSession(listOf(WordDefinition("kaki", "かき", "柿")), world(), Random(3))
    private fun finish(session: CombatSession) {
        val current = session.upgradeSelection!!
        repeat(300) { if (session.upgradeSelection === current) session.update(1f / 120f) }
        assertNotSame(current, session.upgradeSelection)
    }

    @Test fun levelChoicePausesWorldAndMultipleRewardsAreNotLost() {
        val session = session()
        val enemy = session.world.spawnEnemy(Vec2(200f, 60f))
        session.world.grantExperience(200)
        session.update(0f)
        assertNull(session.upgradeSelection)
        assertEquals(2, session.pendingChoiceCount)
        assertTrue(session.handleSpace())
        val elapsed = session.world.elapsedSeconds
        val position = enemy.position
        repeat(120) { session.update(1f / 120f) }
        assertEquals(elapsed, session.world.elapsedSeconds, 0.0)
        assertEquals(position, enemy.position)
        assertEquals(1, session.world.pendingLevelUps)
        session.tapUpgrade(0)
        finish(session)
        assertNull(session.upgradeSelection)
        assertEquals(1, session.pendingChoiceCount)
        assertTrue(session.handleSpace())
        session.tapUpgrade(0)
        finish(session)
        assertNull(session.upgradeSelection)
        assertEquals(0, session.world.pendingLevelUps)
    }

    @Test fun rewardCompositionUsesSharedMatcherWithoutFiringMainWeapon() {
        val session = session()
        session.world.grantExperience(50)
        session.update(0f)
        assertTrue(session.handleSpace())
        val selection = session.upgradeSelection!!
        val reading = selection.words[1].text
        assertFalse(session.onInput(splitTextCharacters(reading.take(1)).map { InputToken.Stroke(it) }, true))
        assertTrue(session.upgradeSelection!!.states[1].matchedInputCharacters > 0)
        assertTrue(session.onInput(splitTextCharacters(reading).map { InputToken.Stroke(it) }, true))
        finish(session)
        assertEquals(0, (session.world.mainWeapon as Repeater).pendingShots)
    }

    @Test fun rerollsAreFiniteAndDoNotAdvanceGameTime() {
        val session = session()
        session.world.grantExperience(50); session.update(0f)
        assertTrue(session.handleSpace())
        repeat(3) { assertTrue(session.rerollUpgrades()) }
        assertFalse(session.rerollUpgrades())
        assertEquals(0, session.rerollsRemaining)
        assertEquals(0.0, session.world.elapsedSeconds, 0.0)
        assertEquals(3, session.upgradeSelection!!.offers.map { it.id }.distinct().size)
        assertEquals(3, session.upgradeSelection!!.words.map { it.text }.distinct().size)
    }

    @Test fun weaponSlotsRejectDuplicatesAndRemoveUnavailableAcquisitions() {
        val world = world()
        listOf(WeaponId.SPECTRE, WeaponId.SENTRY, WeaponId.METEOR, WeaponId.GUARDIAN).forEach { assertTrue(world.acquireWeapon(it)) }
        assertFalse(world.acquireWeapon(WeaponId.SPECTRE))
        assertFalse(world.acquireWeapon(WeaponId.HUNTER))
        assertFalse(UpgradeCatalog.pool(world).any { it.kind == OfferKind.ACQUIRE })
        assertTrue(UpgradeCatalog.pool(world).any { it.weapon == WeaponId.GUARDIAN && it.kind == OfferKind.UPGRADE })
    }

    @Test fun allElevenWeaponsHaveFunctionalActivationAndDamagePaths() {
        for (id in WeaponId.entries.filter { it != WeaponId.REPEATER }) {
            val world = world()
            assertTrue(world.acquireWeapon(id))
            val word = when (id) {
                WeaponId.SPECTRE -> "aeo"; WeaponId.HUNTER -> "iou"; WeaponId.TESLA_MINE -> "boat"
                WeaponId.MINEFIELD -> "fish"; WeaponId.BLADE_TRAP -> "kite"; WeaponId.ORBITER -> "north"
                else -> "attack"
            }
            val target = world.spawnEnemy(Vec2(250f, 250f), EnemyDefinition(maxHp = 100000, speed = 0f))
            world.completeWord(WordDefinition("trigger", word, languageTag = "en"), target.id)
            if (id == WeaponId.METEOR) {
                repeat(260) { world.update(1f / 120f) }
                val pickup = world.pickups.single { it.kind == PickupKind.METEOR }
                target.position = pickup.position
                world.activatePickup(pickup.id)
            }
            if (id in listOf(WeaponId.MINEFIELD, WeaponId.ORBITER)) {
                target.position = world.weaponActors.first().position
                if ((target.position - world.player.position).length() < 20f) {
                    target.position = Vec2(260f, 250f)
                    world.weaponActors.first().position = target.position
                }
            }
            repeat(180) { world.update(1f / 120f) }
            assertTrue(id.name + " must cause damage", world.dps(id) > 0f)
        }
    }

    @Test fun guardianStopsWithoutFuelAndRestartsAfterTypedPickup() {
        val world = world()
        world.acquireWeapon(WeaponId.GUARDIAN)
        val drone = world.weaponActors.single() as GuardianActor
        drone.remaining = 0f
        world.update(0.01f)
        assertFalse(drone.active)
        val fuel = world.spawnPickup(PickupKind.FUEL, Vec2(80f, 80f))
        world.activatePickup(fuel.id)
        world.update(0.01f)
        assertTrue(drone.active)
        assertTrue(drone.remaining > 19f)
    }

    @Test fun progressUpgradeOpensRealEvolutionAndChangesProjectiles() {
        val session = session()
        val repeater = session.world.mainWeapon as Repeater
        repeater.upgrades.creditKills(50)
        session.update(0f)
        assertTrue(session.handleSpace())
        assertTrue(session.upgradeSelection!!.evolution)
        val offer = session.upgradeSelection!!.offers[0]
        session.tapUpgrade(0); finish(session)
        assertEquals(1, repeater.upgrades.rank(offer.evolution!!))
        assertEquals(1, repeater.upgrades.evolutionsChosen)
        assertFalse(repeater.upgrades.canEvolve)
    }

    @Test fun repeaterDamageCritAndPayloadUpgradesAffectTheActualShot() {
        val world = world()
        val repeater = world.mainWeapon as Repeater
        val pool = UpgradeCatalog.pool(world)
        world.applyUpgrade(pool.first { it.id == "repeater-damage-2" })
        world.applyUpgrade(pool.first { it.id == "repeater-crit-2" })
        repeater.upgrades.evolve(RepeaterEvolution.EXPLODING_BULLETS)
        world.applyUpgrade(UpgradeCatalog.pool(world).first { it.id == "repeater-payload" })
        val shot = (repeater.also { it.target(1, Vec2(300f, 250f)) }
            .update(0f, world.player.position, { Vec2(300f, 250f) }).first() as WeaponAction.LaunchProjectile).projectile
        assertEquals(46, shot.damage)
        assertEquals(0.08f, shot.criticalChance, 0.0001f)
        assertEquals(3.5f, shot.criticalDamageMultiplier, 0.0001f)
        assertEquals(18.75f, shot.explosionRadius, 0.0001f)
    }

    @Test fun ricochetAndExplosionDamageAdditionalEnemiesAndCreditRepeater() {
        for (evolution in listOf(RepeaterEvolution.RICOCHET, RepeaterEvolution.EXPLODING_BULLETS)) {
            val world = world()
            (world.mainWeapon as Repeater).upgrades.evolve(evolution)
            val first = world.spawnEnemy(Vec2(250f, 250f), EnemyDefinition(maxHp = 10, speed = 0f))
            world.spawnEnemy(Vec2(262f, 250f), EnemyDefinition(maxHp = 10, speed = 0f))
            world.target(first.id)
            repeat(60) { world.update(1f / 120f) }
            assertEquals(evolution.name, 2, world.kills)
            assertEquals(2, (world.mainWeapon as Repeater).upgrades.evolutionCredit)
        }
    }

    @Test fun phoneAndLandscapeCardsNeverOverlapAndHaveWorkingHitAreas() {
        for ((width, height) in listOf(320f to 360f, 393f to 500f, 800f to 360f)) {
            val layout = UpgradeLayout(width, height, 3)
            layout.cards.forEachIndexed { i, box ->
                assertTrue(box.height >= 80f)
                assertTrue(box.top >= layout.headerBottom)
                assertTrue(box.bottom < layout.footerTop)
                assertEquals(i, layout.hitChoice(box.centerX, (box.top + box.bottom) / 2f))
                layout.cards.drop(i + 1).forEach { other ->
                    assertFalse(box.contains(other.centerX, (other.top + other.bottom) / 2f))
                }
            }
        }
    }

    @Test fun noRerollIsSpentWhenAllRemainingEvolutionOptionsAreAlreadyVisible() {
        val session = session()
        val state = (session.world.mainWeapon as Repeater).upgrades
        RepeaterEvolution.entries.drop(1).forEach { state.evolve(it); state.evolve(it) }
        state.creditKills(1000)
        session.update(0f)
        assertTrue(session.handleSpace())
        assertEquals(1, session.upgradeSelection!!.offers.size)
        assertFalse(session.canRerollUpgrades)
        assertFalse(session.rerollUpgrades())
        assertEquals(3, session.rerollsRemaining)
    }

    @Test fun kanaAndRomanInputUseTheSameDictionaryGlyphWeaponCondition() {
        val counts = listOf(false, true).map { roman ->
            val world = world()
            val word = WordDefinition("snack", "おかし", "お菓子")
            val session = CombatSession(listOf(word), world, Random(1))
            world.acquireWeapon(WeaponId.SPECTRE)
            world.spawnEnemy(Vec2(300f, 250f), EnemyDefinition(maxHp = 1000, speed = 0f))
            session.update(0f)
            val input = if (roman)
                jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages.forTag("ja")
                    .defaultRoman(word.characters) else word.text
            assertTrue(session.onInput(splitTextCharacters(input).map { InputToken.Stroke(it) }, false))
            world.weaponActors.count { it.source == WeaponId.SPECTRE }
        }
        assertEquals(listOf(2, 2), counts)
    }

    @Test fun pickupWordActivatesMeteorWithoutRetargetingOrFiringRepeater() {
        val session = session()
        session.world.acquireWeapon(WeaponId.METEOR)
        val pickup = session.world.spawnPickup(PickupKind.METEOR, Vec2(80f, 80f))
        session.update(0f)
        val caption = session.pickupCaptions.single { it.pickup.id == pickup.id }
        assertTrue(session.onInput(splitTextCharacters(caption.word.text).map { InputToken.Stroke(it) }, false))
        assertTrue(session.world.weaponActors.any { it.visual == WeaponVisualKind.BOMBARDMENT })
        assertEquals(0, (session.world.mainWeapon as Repeater).pendingShots)
    }
}
