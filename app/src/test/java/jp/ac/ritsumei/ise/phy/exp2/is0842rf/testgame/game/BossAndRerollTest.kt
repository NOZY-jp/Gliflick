package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BossAndRerollTest {
    private val words = listOf(WordDefinition("dog","いぬ","犬"),WordDefinition("cat","ねこ","猫"),WordDefinition("flower","はな","花"))
    private fun world(director: BossDirector = BossDirector()) = CombatWorld(CombatConfig(spawningEnabled=false,playerMaxHp=100000),Random(42),bosses=director)
        .also { it.resize(400f,600f) }
    private fun tokens(value:String)=value.map { InputToken.Stroke(it.toString()) }
    private fun spelling(word:WordDefinition)=TypingLanguages.forTag(word.languageTag).defaultRoman(word.characters)

    @Test fun dropHazardHasTwoMinuteMeanAndCannotBurstOnSameTimestamp() {
        val policy=RerollDropPolicy();val random=Random(56)
        val drops=(1..120000).count { policy.onDeath(it.toDouble(),random) }
        assertTrue("Expected about 1000 drops, got $drops",drops in 920..1080)
        repeat(1000) { assertFalse(policy.onDeath(120000.0,random)) }
    }
    @Test fun aKillDropsAtTheEnemyPositionAndSameFrameKillsDoNotCreateABatch() {
        val w=CombatWorld(CombatConfig(),Random(3),rerollDrops=RerollDropPolicy(0.000001),
            spawnDensity=EnemySpawnDensity { _,_,_,_ -> false }).also { it.resize(400f,600f) }
        w.update(1f)
        val first=w.spawnEnemy(Vec2(80f,200f),EnemyDefinition(speed=0f))
        w.damageEnemy(first.id,1000)
        assertEquals(Vec2(80f,200f),w.pickups.single { it.kind==PickupKind.REROLL }.position)
        val next=w.spawnEnemy(Vec2(120f,200f),EnemyDefinition(speed=0f));w.damageEnemy(next.id,1000)
        assertEquals(1,w.pickups.size)
    }

    @Test fun typedRerollAddsExactlyOneWithoutFiringAndExpirationRemovesItsWord() {
        val w=world();val session=CombatSession(words,w,Random(7));session.resize(400f,600f)
        val pickup=w.spawnPickup(PickupKind.REROLL,Vec2(80f,300f));session.update(0f)
        val word=session.pickupCaptions.single().word
        assertTrue(session.onInput(tokens(spelling(word)),false))
        session.update(0f)
        assertEquals(4,session.rerollsRemaining);assertFalse(w.pickups.any { it.id==pickup.id })
        assertEquals(0,(w.mainWeapon as Repeater).pendingShots)
        w.activatePickup(pickup.id);session.update(0f);assertEquals(4,session.rerollsRemaining)
        w.spawnPickup(PickupKind.REROLL,Vec2(80f,300f));session.update(0f)
        session.update(31f);assertTrue(session.pickupCaptions.isEmpty())
    }
    @Test fun introductionFreezesEveryClockAndOnlyTypedGoStartsTheNextBoss() {
        val director=BossDirector(BossBalance(intervalSeconds=1.0));val w=world(director)
        val session=CombatSession(words,w,Random(3));session.resize(400f,600f)
        session.update(1.2f)
        assertEquals(1.0,w.elapsedSeconds,0.00001);assertEquals(BossKind.GEMINI,director.introduction)
        val time=w.elapsedSeconds;repeat(20) { session.update(0.5f) };assertEquals(time,w.elapsedSeconds,0.0)
        assertFalse(session.handleSpace());assertNull(director.activeKind)
        assertTrue(session.onInput(tokens("xgo"),false));assertEquals(BossKind.GEMINI,director.activeKind)
        assertEquals(2,director.enemies.size);assertEquals(2,session.captions.map { it.word.id }.distinct().size)
        assertEquals(0,(w.mainWeapon as Repeater).pendingShots)
        director.enemies.forEach { w.damageEnemy(it.id,100000) };session.update(0f)
        session.update(1f);assertEquals(BossKind.CYPHER,director.introduction)
    }
    @Test fun bossAdmissionHonorsSixteenBodiesAndDoesNotAwardKillsForFreedSlots() {
        val w=world()
        repeat(16) { w.spawnEnemy(Vec2(it*24f,10f),EnemyDefinition(speed=0f)) }
        w.bosses.begin(w,BossKind.GEMINI)
        assertEquals(16,w.livingPopulation)
        assertEquals(2,w.bosses.enemies.count { it.isAlive })
        assertTrue(kotlin.math.cos(w.bosses.enemies[0].angle)>0f)
        assertTrue(kotlin.math.cos(w.bosses.enemies[1].angle)<0f)
        assertEquals(0,w.kills);assertEquals(0,w.experience)
        assertTrue(w.pickups.isEmpty())
    }

    @Test fun geminiHealsOnlyTheLowerPartnerAndTwentySharedMatcherHitsRepelTheTendril() {
        val w=world();w.bosses.begin(w,BossKind.GEMINI)
        val (a,b)=w.bosses.enemies
        w.damageEnemy(a.id,500);w.update(1f)
        assertTrue(a.hp>2500);assertEquals(3000,b.hp)
        w.damageEnemy(a.id,300);w.damageEnemy(b.id,b.hp-a.hp)
        val equal=a.hp;w.update(1f);assertEquals(equal,a.hp);assertEquals(equal,b.hp)
        val session=CombatSession(words,w,Random(5));session.resize(400f,600f)
        repeat(1202) { session.update(1f/120f) }
        assertNotNull(session.tendrilState)
        val command=spelling(session.tendrilState!!.definition)
        assertTrue(session.onInput(tokens(command.repeat(20)),false))
        assertEquals(20,w.bosses.tendril!!.strokes)
        assertNotNull(w.bosses.tendril!!.retreatAge)
        val hp=w.player.hp
        session.update(0.1f);assertNotNull(w.bosses.tendril)
        session.update(0.2f);assertNull(w.bosses.tendril)
        session.update(6f);assertEquals(hp,w.player.hp)
        session.update(12f);assertNotNull(w.bosses.tendril);session.update(6f)
        assertEquals(hp-60,w.player.hp)
    }
    @Test fun cypherChangesOnlyInsideReadingsKeepsLengthAndRestoresOnExitWithoutMistype() {
        val w=world();w.bosses.begin(w,BossKind.CYPHER)
        assertTrue(w.bosses.enemies.single().cyphered)
        val inside=w.spawnEnemy(Vec2(200f,180f),EnemyDefinition(speed=0f,maxHp=10000))
        val outside=w.spawnEnemy(Vec2(390f,550f),EnemyDefinition(speed=0f,maxHp=10000))
        val session=CombatSession(words,w,Random(8));session.resize(400f,600f);session.update(0f)
        val cipher=session.captions.single { it.enemyId==inside.id }
        assertTrue(cipher.cyphered);assertEquals(cipher.normalWord.readingLength,cipher.word.readingLength)
        assertNotEquals(cipher.normalWord.text,cipher.word.text)
        assertEquals(cipher.word.text,cipher.word.displayName)
        assertFalse(session.captions.single { it.enemyId==outside.id }.cyphered)
        val mistakes=session.mistypeCount
        inside.position=Vec2(390f,550f);session.update(0f)
        assertFalse(session.captions.single { it.enemyId==inside.id }.cyphered)
        assertEquals(mistakes,session.mistypeCount)
        listOf("tsuse","tuse","tuce").forEach { input ->
            val engine=WordMatchEngine(listOf(WordDefinition("cipher","つせ")))
            assertTrue(input.flatMap { engine.accept(it.toString()) }.isNotEmpty())
        }
    }
    @Test fun sealedWordFirstCompletionOnlyRemovesSealAndSecondFiresBothWeaponKinds() {
        val w=world();w.acquireWeapon(WeaponId.HUNTER)
        val enemy=w.spawnEnemy(Vec2(300f,200f),EnemyDefinition(maxHp=10000,speed=0f));enemy.wordSealed=true
        val session=CombatSession(listOf(words.first()),w,Random(3));session.resize(400f,600f)
        val input=spelling(session.captions.single().word)
        session.onInput(tokens(input),false)
        assertFalse(enemy.wordSealed);assertEquals(0,(w.mainWeapon as Repeater).pendingShots);assertTrue(w.weaponActors.isEmpty())
        assertEquals("いぬ",session.captions.single().word.text)
        session.onInput(tokens(input),false)
        assertEquals(3,(w.mainWeapon as Repeater).pendingShots);assertTrue(w.weaponActors.any { it.source==WeaponId.HUNTER })
    }
    @Test fun viewportChangesKeepCypherInsideAndPoetAnchoredBelowTheHud() {
        val w=world();w.bosses.begin(w,BossKind.CYPHER);w.update(2f)
        w.resize(300f,360f);w.update(0.1f)
        val body=w.bosses.enemies.single()
        assertTrue(body.position.x in 0f..300f && body.position.y in 0f..360f)
        w.damageEnemy(body.id,100000);w.update(0f)
        w.bosses.begin(w,BossKind.POET);w.resize(400f,800f)
        assertEquals(Vec2(200f,101f),w.bosses.enemies.single().position)
        assertTrue(w.bosses.enemies.single().hitCircles().isEmpty())
        assertNull(w.nearestEnemy(w.player.position))
    }

    @Test fun poetScalesArrivalExposureOnlyAndRestoresItAfterDefeat() {
        val exposure=mutableListOf<Float>()
        val w=CombatWorld(CombatConfig(),Random(4),spawnDensity=EnemySpawnDensity { dt,_,_,_ -> exposure+=dt;false })
            .also { it.resize(400f,600f) }
        w.update(0.1f);assertEquals(0.1f,exposure.last(),0.000001f)
        w.bosses.begin(w,BossKind.POET);w.update(0.1f)
        assertEquals(0.075f,exposure.last(),0.000001f)
        assertEquals(0.2,w.elapsedSeconds,0.000001)
        repeat(PoetPassage.phrases.size) { w.bosses.completePhrase(w) }
        w.update(0f);w.update(0.1f)
        assertEquals(0.1f,exposure.last(),0.000001f)
        assertEquals(EnemyArrivalRules.NORMAL,w.bosses.arrivalRules)
    }

    @Test fun poetNaturalArrivalsUseAllThreeEdgesAndNeverTheTop() {
        val w=CombatWorld(CombatConfig(progression=ProgressionDefinition(killExperience=0)),Random(46),
            spawnPolicy=FixedEnemySpawnPolicy(EnemyDefinition(speed=0f)),
            spawnDensity=EnemySpawnDensity { _,_,_,_ -> true }).also { it.resize(400f,600f) }
        w.bosses.begin(w,BossKind.POET)
        val edges=mutableSetOf<ArrivalEdge>()
        repeat(240) {
            w.update(1f/120f)
            w.drainEvents().filterIsInstance<CombatEvent.Spawned>().filter { it.enemy.definition.bossKind==null }.forEach { event ->
                val point=event.enemy.spawnOrigin
                assertTrue("Top arrival is forbidden during Poet: $point",point.y>=0f)
                edges+=when {
                    point.x<0f -> ArrivalEdge.LEFT
                    point.x>w.width -> ArrivalEdge.RIGHT
                    point.y>w.height -> ArrivalEdge.BOTTOM
                    else -> error("Natural arrival must be outside an allowed edge: $point")
                }
                w.damageEnemy(event.enemy.id,100000)
            }
            assertTrue(w.livingPopulation<=16)
        }
        assertEquals(setOf(ArrivalEdge.LEFT,ArrivalEdge.RIGHT,ArrivalEdge.BOTTOM),edges)
    }

    @Test fun warderAuraVisibilityUsesTheSameLifetimeAndBoundsAsInvulnerability() {
        val w=world()
        val owner=w.spawnEnemy(Vec2(200f,240f),EnemyCatalog.definition(EnemyKind.WARDER))
        val barrier=w.barriers.single()
        val target=w.spawnEnemy(Vec2(280f,240f),EnemyDefinition(speed=0f))
        assertTrue(w.isWarderAuraActive(barrier));assertTrue(w.isProtected(target))
        owner.position=Vec2(-owner.radius-1f,240f);target.position=owner.position+Vec2(100f,0f)
        assertFalse(w.isWarderAuraActive(barrier));assertFalse(w.isProtected(target))
        owner.position=Vec2(25f,240f)
        assertTrue(w.isWarderAuraActive(barrier));assertTrue(w.isProtected(target))
        w.damageEnemy(owner.id,100000)
        assertFalse(w.isWarderAuraActive(barrier));assertFalse(w.isProtected(target))
    }

    @Test fun poetOrbsTravelBeforeSealingAndOnlyTheWholePassageDefeatsItsBody() {
        val w=world();w.bosses.begin(w,BossKind.POET)
        val target=w.spawnEnemy(Vec2(300f,300f),EnemyDefinition(maxHp=100000,speed=0f))
        val session=CombatSession(words,w,Random(6));session.resize(400f,600f)
        repeat(600) { session.update(1f/120f) }
        assertFalse(target.wordSealed);assertTrue(w.bosses.orbs.isNotEmpty())
        repeat(700) { session.update(1f/120f) };assertTrue(target.wordSealed)
        val body=w.bosses.enemies.single();w.damageEnemy(body.id,100000);assertTrue(body.isAlive)
        PoetPassage.phrases.forEachIndexed { index,_ ->
            val current=checkNotNull(session.poemState)
            assertTrue(session.onInput(tokens(spelling(current.definition)),false))
            assertEquals(index+1,w.bosses.poemIndex)
        }
        session.update(0f);assertFalse(body.isAlive);assertNull(w.bosses.activeKind);assertEquals(1,w.bossesDefeated)
        assertFalse(target.wordSealed)
    }
}
