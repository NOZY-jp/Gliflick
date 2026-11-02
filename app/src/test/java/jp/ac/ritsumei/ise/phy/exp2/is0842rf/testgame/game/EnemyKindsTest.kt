package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class EnemyKindsTest {
    private fun world(cap: Int = 16) = CombatWorld(CombatConfig(spawningEnabled = false, maxEnemiesAlive = cap), Random(37))
        .also { it.resize(800f, 1000f) }
    private fun spawn(w: CombatWorld, kind: EnemyKind, at: Vec2 = Vec2(600f, 200f), tier: Int = 1) =
        w.spawnEnemy(at, EnemyCatalog.definition(kind, tier))
    @Test fun wikiHealthShieldAndShardValuesAreRepresentedSeparately() {
        assertEquals(listOf(100,200,400,800,2400), (1..5).map { EnemyCatalog.definition(EnemyKind.SHIELD_DRONE,it).maxHp })
        assertEquals(listOf(200,400,800,1600,4800), (1..5).map { EnemyCatalog.definition(EnemyKind.SHIELD_DRONE,it).maxShield })
        assertEquals(listOf(300,600,1200,2400,7200), (1..5).map { EnemyCatalog.definition(EnemyKind.SHOOTER,it).maxHp })
        assertEquals(listOf(4,5,6), (1..3).map { EnemyCatalog.definition(EnemyKind.SPLITTER,it).splitter!!.partCount })
        assertEquals(listOf(350,500,650), (1..3).map { EnemyCatalog.definition(EnemyKind.SPLITTER,it).splitter!!.partHp })
    }
    @Test fun shieldAbsorbsOverflowRechargesAfterQuietTimeAndDoesNotHealHp() {
        val shield = RegeneratingShield(100, 200)
        shield.takeDamage(180); assertEquals(100,shield.hp); assertEquals(20f,shield.shield,0f)
        shield.advance(0.49f); assertEquals(20f,shield.shield,0f)
        shield.advance(0.11f); assertEquals(32f,shield.shield,0.001f)
        shield.takeDamage(50); assertEquals(82,shield.hp); assertEquals(0f,shield.shield,0f)
        shield.advance(10f); assertEquals(200f,shield.shield,0f); assertEquals(82,shield.hp)
    }
    @Test fun shieldOnlyHitsProduceFeedbackWithoutKillingOrGrantingExperience() {
        val w=world(); val enemy=spawn(w,EnemyKind.SHIELD_DRONE)
        w.damageEnemy(enemy.id,35)
        assertEquals(100,enemy.hp); assertEquals(165f,enemy.shield,0f)
        assertEquals(35,w.hitFeedback.single().damage); assertEquals(0,w.kills)
    }
    @Test fun shooterDeploysStopsAndFiresOnSourceCadence() {
        val w=world(); val shooter=spawn(w,EnemyKind.SHOOTER,Vec2(-20f,200f))
        shooter.initialAttackDelay=1f
        repeat(1200) { w.update(1f/120f) }
        assertEquals(shooter.deploymentPosition,shooter.position)
        val stopped=shooter.position
        val first=w.enemies.single { it.definition.kind==EnemyKind.SHOOTER_MISSILE }
        assertEquals(200,first.definition.maxHp); assertEquals(shooter.id,first.parentId)
        repeat(600) { w.update(1f/120f) }
        assertEquals(stopped,shooter.position)
        assertTrue(w.enemies.count { it.definition.kind==EnemyKind.SHOOTER_MISSILE } in 1..2)
    }
    @Test fun missileChargesThenUsesItsFrozenDirectionAndSurvivesParentOnceLaunched() {
        val w=world(); val parent=spawn(w,EnemyKind.SHOOTER)
        val missile=spawn(w,EnemyKind.SHOOTER_MISSILE,Vec2(500f,100f))
        missile.parentId=parent.id; missile.flightDirection=Vec2(-1f,0f)
        val start=missile.position
        repeat(720) { w.update(1f/120f) }; assertEquals(start,missile.position)
        repeat(240) { w.update(1f/120f) }; assertTrue(missile.position.x<start.x)
        assertEquals(start.y,missile.position.y,0.001f)
        w.damageEnemy(parent.id,10000); w.update(0f); assertTrue(missile.isAlive)
    }
    @Test fun chargingMissileDisappearsWithParentAndNewMissilesRespectPopulationCap() {
        val w=world(2); val shooter=spawn(w,EnemyKind.SHOOTER)
        shooter.initialAttackDelay=0f; w.update(0f)
        assertEquals(2,w.livingPopulation)
        val missile=w.enemies.single { it.definition.kind==EnemyKind.SHOOTER_MISSILE }
        w.damageEnemy(shooter.id,10000); w.update(0f); assertFalse(missile.isAlive)
        assertEquals(1,w.kills) // destruction of an unlaunched child is not another credited kill.
    }
    @Test fun warderProtectsAllDamagePathsButNotOtherWardersAndUnlinksImmediately() {
        val w=world(); val a=spawn(w,EnemyKind.WARDER,Vec2(100f,100f))
        val b=spawn(w,EnemyKind.WARDER,Vec2(150f,100f))
        val drone=w.spawnEnemy(Vec2(200f,100f))
        assertTrue(w.isProtected(drone)); assertFalse(w.isProtected(a)); assertFalse(w.isProtected(b))
        w.damageEnemy(drone.id,9999)
        w.areaDamage(drone.position,100f,10,WeaponId.METEOR,DamageType.EXPLOSIVE,allowCrit=false)
        assertEquals(80,drone.hp)
        drone.statuses+=EnemyStatus(DamageType.ELECTRIC,WeaponId.HUNTER)
        w.update(0.51f); assertEquals(80,drone.hp)
        w.damageEnemy(a.id,10000); assertEquals(1,w.barriers.size); assertTrue(w.isProtected(drone))
        w.damageEnemy(b.id,10000); assertTrue(w.barriers.isEmpty()); assertFalse(w.isProtected(drone))
        w.damageEnemy(drone.id,35); assertEquals(45,drone.hp)
    }
    @Test fun arcBlocksIncomingAndPenetratingBulletsOnlyFromItsOccupiedSide() {
        for (penetrating in listOf(false,true)) {
            val w=world(); val owner=spawn(w,EnemyKind.WARDER,Vec2(100f,100f))
            w.launch(Projectile(Vec2(180f,100f),Vec2(-600f,0f),50,penetrates=penetrating))
            w.update(0.2f); assertEquals(300,owner.hp); assertTrue(w.projectiles.isEmpty())
        }
        val w=world();val owner=spawn(w,EnemyKind.WARDER,Vec2(100f,100f))
        w.launch(Projectile(Vec2(20f,100f),Vec2(600f,0f),50));w.update(0.15f)
        assertEquals(250,owner.hp)
        assertEquals(2f*PI.toFloat()/3f,w.barriers.single().halfArc*2f,0.0001f)
    }
    @Test fun arcSweepsDoNotTunnelAcrossThinBandOrCloseTheOpenSide() {
        assertNotNull(sweptArcHit(Vec2(100f,0f),Vec2.ZERO,25f,2f,0f,PI.toFloat()/3f))
        assertNull(sweptArcHit(Vec2(-100f,0f),Vec2.ZERO,25f,2f,0f,PI.toFloat()/3f))
        assertNull(sweptArcHit(Vec2(0f,-100f),Vec2(0f,100f),25f,2f,0f,PI.toFloat()/3f))
    }
    @Test fun splitterKeepsPartHealthAndRetiresItsInputWithoutFabricatingChildren() {
        val w=world();val group=spawn(w,EnemyKind.SPLITTER)
        val session=CombatSession(listOf(WordDefinition("aa","あい","愛")),w,Random(1))
        session.update(0f); session.onInput(listOf(InputToken.Stroke("a")),false)
        w.dealDamage(group.id,100,WeaponId.REPEATER,DamageType.KINETIC,inflictStatus=false,hitPart=1)
        w.damageEnemy(group.id,350);session.update(0f)
        assertFalse(group.isAlive); assertEquals(3,w.livingPopulation);assertEquals(1,w.kills)
        assertEquals(listOf(250,350,350),w.enemies.filter { it.isAlive }.map { it.hp }.sorted())
        assertTrue(session.captions.first { it.enemyId==group.id }.retired)
        assertEquals(4,session.captions.size) // retired parent plus three fresh targets
        assertTrue(session.captions.filter { !it.retired }.all { it.state.matchedInputCharacters==0 })
    }
    @Test fun areaDamageCanDestroyAllOriginalPartsAfterFirstPartReleasesFormation() {
        val w=world(); val group=spawn(w,EnemyKind.SPLITTER)
        w.areaDamage(group.position,80f,400,WeaponId.METEOR,DamageType.EXPLOSIVE,allowCrit=false)
        assertEquals(0,w.livingPopulation); assertEquals(4,w.kills)
    }
    @Test fun reservedPartsNeverIncreasePopulationWhenFormationBreaks() {
        val w=world();val group=spawn(w,EnemyKind.SPLITTER)
        repeat(12) { w.spawnEnemy(Vec2(80f,40f+it*12f)) }
        assertEquals(16,w.livingPopulation)
        w.damageEnemy(group.id,350); assertEquals(15,w.livingPopulation)
    }
    @Test fun speciesAppearGraduallyUsingSourceWeightsAndRespectClassCaps() {
        val policy=MixedEnemySpawnPolicy()
        assertTrue(policy.distributionAt(120.0,1).all { it.kind==EnemyKind.DRONE })
        assertTrue(policy.distributionAt(121.0,1).any { it.kind==EnemyKind.SHOOTER && it.weight<0.02 })
        assertTrue(policy.distributionAt(180.0,1).any { it.kind==EnemyKind.SHOOTER })
        assertTrue(policy.distributionAt(300.0,1).any { it.kind==EnemyKind.SHIELD_DRONE })
        assertTrue(policy.distributionAt(480.0,1).any { it.kind==EnemyKind.SPLITTER })
        assertTrue(policy.distributionAt(600.0,1).any { it.kind==EnemyKind.WARDER })
        val w=world();spawn(w,EnemyKind.WARDER)
        assertFalse(policy.distributionAt(600.0,1,w.enemies).any { it.kind==EnemyKind.WARDER })
        repeat(15) { w.spawnEnemy(Vec2(50f,50f)) }
        assertTrue(policy.distributionAt(600.0,1,w.enemies).isEmpty())
    }
    @Test fun penetrationHitsDistinctFormationPartsWithoutRepeatingOnePartOnNextFrame() {
        val w=world();val group=w.spawnEnemy(Vec2(600f,200f),
            EnemyCatalog.definition(EnemyKind.SPLITTER).copy(speed=0f,behaviorFactory={EnemyBehavior { _,_,_-> }}))
        val part=group.hitCircles()[0]
        val shot=Projectile(part.position+Vec2(0f,-20f),Vec2(0f,30f),40,
            source=WeaponId.OSCILLATOR, penetrates=true)
        w.launch(shot)
        repeat(80) { w.update(1f/120f) }
        val hp=group.splitterHealth!!.partHealth.toList()
        repeat(10) { w.update(1f/120f) }
        assertEquals(hp,group.splitterHealth!!.partHealth)
        assertTrue(shot.hitPartKeys.isNotEmpty())
    }
    @Test fun naturalArrivalsMissilesAndSplitsKeepTheSixteenPartLimitOverLongRuns() {
        val w=CombatWorld(CombatConfig(playerMaxHp=10000000),Random(29)).also { it.resize(800f,1000f) }
        var sawHigherDroneTier = false
        repeat(3600) {
            w.update(1f)
            assertTrue("Population ${w.livingPopulation} at ${w.elapsedSeconds}",w.livingPopulation<=16)
            val drones = w.enemies.filter { it.isAlive && it.definition.kind==EnemyKind.DRONE }
            assertTrue(drones.all { it.definition.tier in 1..DroneTierCatalog.highestPublishedTier })
            sawHigherDroneTier = sawHigherDroneTier || drones.any { it.definition.tier >= 3 }
        }
        assertTrue("Mixed arrivals must reach Tier 3+ without a boss", sawHigherDroneTier)
    }

    @Test fun normalArrivalsCannotOccupyTheLastMissileSlotAndSilenceShooters() {
        val w=CombatWorld(CombatConfig(),Random(17),FixedEnemySpawnPolicy(),
            spawnDensity=EnemySpawnDensity { _,_,_,_-> true }).also { it.resize(800f,1000f) }
        val shooter=spawn(w,EnemyKind.SHOOTER,Vec2(50f,100f));shooter.initialAttackDelay=100f
        repeat(14) { w.spawnEnemy(Vec2(200f,100f),EnemyDefinition(speed=0f)) }
        w.update(0f);assertEquals(15,w.livingPopulation)
        w.update(100f);assertEquals(16,w.livingPopulation)
        assertEquals(1,w.enemies.count { it.isAlive && it.definition.kind==EnemyKind.SHOOTER_MISSILE })
    }

    @Test fun damageOverTimeKeepsItsPartAndRemainingDurationAcrossSeparation() {
        val w=world();val group=spawn(w,EnemyKind.SPLITTER)
        group.statuses+=EnemyStatus(DamageType.KINETIC,WeaponId.CHOPPER,partIndex=1)
        w.update(.51f)
        assertEquals(350,group.splitterHealth!!.partHealth[0])
        assertEquals(340,group.splitterHealth!!.partHealth[1])
        w.damageEnemy(group.id,350)
        val survivor=w.enemies.single { it.isAlive && it.statuses.isNotEmpty() }
        assertEquals(340,survivor.hp)
        assertEquals(4.49f,survivor.statuses.single().remaining,.001f)
        w.update(.51f);assertEquals(330,survivor.hp)
    }

    @Test fun breakingTheFirstPartStillRunsChopperBloodbathKillEffects() {
        val zero=object:Random() { override fun nextBits(bitCount:Int)=0 }
        val w=CombatWorld(CombatConfig(spawningEnabled=false),zero,FixedEnemySpawnPolicy()).also { it.resize(800f,1000f) }
        w.acquireWeapon(WeaponId.CHOPPER)
        val state=w.weaponState(WeaponId.CHOPPER)!!;state.creditKills(300)
        assertTrue(w.applyUpgrade(w.evolutionOffers(WeaponId.CHOPPER).first { it.evolution==SecondaryEvolution.BLOODBATH }))
        val group=spawn(w,EnemyKind.SPLITTER)
        group.statuses+=EnemyStatus(DamageType.KINETIC,WeaponId.CHOPPER)
        w.dealDamage(group.id,350,WeaponId.CHOPPER,DamageType.KINETIC)
        assertEquals(10f,w.doubleDamageRemaining,0f);assertEquals(1,w.kills)
    }

}
