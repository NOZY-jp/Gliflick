package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.SecondaryEvolution.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WeaponActivationRulesTest {
    private fun world() = CombatWorld(CombatConfig(spawningEnabled = false), Random(17)).also { it.resize(400f, 500f) }
    private fun event(text: String, at: Vec2 = Vec2(330f, 70f)) =
        CompletedCombatWord(WordDefinition("rule", text, languageTag = "en"), 1, at)
    private fun weapon(world: CombatWorld, id: WeaponId): SecondaryWeapon {
        assertTrue(world.acquireWeapon(id)); return world.secondaryWeapons.single()
    }
    private fun evolve(w: CombatWorld, s: WeaponRunState, branch: SecondaryEvolution) {
        s.creditKills(300)
        assertTrue(w.applyUpgrade(EvolutionCatalog.offers(s).first { it.evolution == branch }))
    }
    @Test fun containedGlyphsCountOccurrencesAndDronesStartAtCompletedEnemy() {
        for ((id, glyphs) in listOf(WeaponId.SPECTRE to "aeo", WeaponId.HUNTER to "iou")) {
            val w = world(); val weapon = weapon(w, id)
            weapon.onWord(w, event("xyz")); assertTrue(w.weaponActors.isEmpty())
            val e = event("z${glyphs}${glyphs.first()}z")
            weapon.onWord(w, e)
            assertEquals(4, w.weaponActors.size)
            assertTrue(w.weaponActors.all { it.position == e.position })
        }
    }
    @Test fun startingGlyphsMustBeFirstAndEveryMatchingCompletionDeploysAtEnemy() {
        for ((id, glyph) in listOf(WeaponId.TESLA_MINE to "b", WeaponId.BLADE_TRAP to "j",
            WeaponId.MINEFIELD to "f", WeaponId.ORBITER to "n")) {
            val w = world(); val weapon = weapon(w, id)
            weapon.onWord(w, event("z${glyph}")); assertTrue(w.weaponActors.isEmpty())
            val e = event("${glyph}z"); repeat(2) { weapon.onWord(w, e) }
            assertEquals(if (id == WeaponId.MINEFIELD) 6 else 2, w.weaponActors.size)
            assertTrue(w.weaponActors.all { it.position == e.position })
        }
    }
    @Test fun hunterUsesTenDamageHalfSecondCadenceAndDemoActiveLifetime() {
        val w = world(); val state = weapon(w, WeaponId.HUNTER).state
        val enemy = w.spawnEnemy(Vec2(300f, 100f), EnemyDefinition(maxHp = 1000, speed = 0f))
        val hunter = HunterActor(state, Vec2(280f, 100f))
        // Direct actor updates isolate beam damage from main shots and status ticks.
        hunter.update(w, 0.32f); assertEquals(1000, enemy.hp)
        hunter.update(w, 0.02f); assertEquals(990, enemy.hp)
        hunter.update(w, 0.3f); assertEquals(990, enemy.hp)
        assertTrue(hunter.beams.isNotEmpty())
        hunter.update(w, 0.2f); assertEquals(980, enemy.hp)
        assertEquals(350f / 60f, hunter.initialDuration, 0.0001f)
        hunter.update(w, 6f); assertTrue(hunter.finished)
    }
    @Test fun chopperHasOneFlightSlotAndDualWieldAddsCapacityRatherThanSimultaneousShots() {
        val w = world(); val weapon = weapon(w, WeaponId.CHOPPER)
        val shot = Projectile(w.player.position, Vec2(150f, 0f))
        repeat(8) { weapon.onMainWeaponFire(w, shot) }
        assertEquals(1, w.weaponActors.size)
        evolve(w, weapon.state, DUAL_WIELD)
        weapon.onMainWeaponFire(w, shot); assertEquals(2, w.weaponActors.size)
        weapon.onMainWeaponFire(w, shot); assertEquals(2, w.weaponActors.size)
        w.weaponActors.first().finished = true; w.update(0f)
        weapon.onMainWeaponFire(w, shot); assertEquals(2, w.weaponActors.size)
    }
    @Test fun sawVolleyReleasesOneSlotOnlyAfterEveryBladeFinishes() {
        val w = world(); val weapon = weapon(w, WeaponId.CHOPPER)
        evolve(w, weapon.state, SAW_LAUNCHER)
        val shot = Projectile(w.player.position, Vec2(150f, 0f))
        weapon.onMainWeaponFire(w, shot); assertEquals(2, w.weaponActors.size)
        w.weaponActors.first().finished = true; w.update(0f)
        weapon.onMainWeaponFire(w, shot); assertEquals(1, w.weaponActors.size)
        w.weaponActors.single().finished = true; w.update(0f)
        weapon.onMainWeaponFire(w, shot); assertEquals(2, w.weaponActors.size)
    }
}
