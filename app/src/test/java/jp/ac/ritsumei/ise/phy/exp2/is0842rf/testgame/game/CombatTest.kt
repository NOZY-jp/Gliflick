package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CombatTest {
    private fun tokens(text: String) = text.map { InputToken.Stroke(it.toString()) }
    private fun world(weapon: MainWeapon = Repeater()) = CombatWorld(
        CombatConfig(maxEnemies = 0), Random(8),
        FixedEnemySpawnPolicy(EnemyDefinition(speed = 0f)), weapon).also { it.resize(400f, 400f) }
    private fun run(world: CombatWorld, seconds: Float) = repeat((seconds * 120).toInt()) { world.update(1f / 120f) }
    private fun session(): CombatSession = CombatSession(listOf(WordDefinition("kaki", "かき", "柿")), world(), Random(1))
        .also { it.resize(400f, 400f) }
    private fun attach(session: CombatSession): Enemy = session.world.spawnEnemy(Vec2(300f, 200f))
        .also { session.update(0f) }

    @Test fun droneMovesAtDefinitionSpeedTowardPlayer() {
        val enemy = Enemy(1, EnemyDefinition(), Vec2(10f, 10f))
        enemy.advance(2f, Vec2(10f, 110f))
        assertEquals(10f, enemy.position.x, 0.001f)
        assertEquals(27f, enemy.position.y, 0.001f)
    }

    @Test fun emittedBulletDoesNotHomeAfterTargetMoves() {
        val weapon = Repeater()
        weapon.target(1, Vec2(100f, 0f))
        val bullet = (weapon.update(0f, Vec2.ZERO) { Vec2(100f, 0f) }.single() as WeaponAction.LaunchProjectile).projectile
        weapon.update(0.12f, Vec2.ZERO) { Vec2(0f, 100f) }
        bullet.advance(0.1f)
        assertEquals(52f, bullet.position.x, 0.001f)
        assertEquals(0f, bullet.position.y, 0.001f)
    }

    @Test fun allThreeShotsEmitEvenAfterTheTargetDies() {
        val weapon = Repeater()
        weapon.target(1, Vec2(100f, 0f))
        assertEquals(1, weapon.update(0f, Vec2.ZERO) { Vec2(100f, 0f) }.size)
        weapon.targetDied(1, Vec2(100f, 0f))
        assertEquals(1, weapon.update(0.121f, Vec2.ZERO) { null }.size)
        assertEquals(1, weapon.update(0.121f, Vec2.ZERO) { null }.size)
        assertEquals(0, weapon.pendingShots)
    }

    @Test fun twoHitKillLeavesThirdBulletFlyingBeyondTheDeadTarget() {
        val world = world(Repeater(RepeaterDefinition(projectile = ProjectileDefinition(damage = 40))))
        val enemy = world.spawnEnemy(Vec2(300f, 200f))
        world.target(enemy.id)
        run(world, 0.5f)
        assertEquals(1, world.kills)
        assertEquals(1, world.projectiles.size)
        assertTrue(world.projectiles.single().position.x > 300f)
        assertEquals(10, world.experience)
    }

    @Test fun frontEnemyInterceptsShotsIntendedForAnotherEnemy() {
        val world = world()
        val front = world.spawnEnemy(Vec2(250f, 200f))
        val back = world.spawnEnemy(Vec2(350f, 200f))
        world.target(back.id)
        run(world, 0.5f)
        assertFalse(front.isAlive)
        assertEquals(80, back.hp)
        assertEquals(1, world.kills)
    }

    @Test fun sweptCollisionFindsFastAndMovingContacts() {
        assertEquals(0.45f, sweptCircleHit(Vec2(-100f, 0f), Vec2(100f, 0f), 10f)!!, 0.0001f)
        assertNull(sweptCircleHit(Vec2(-100f, 11f), Vec2(100f, 11f), 10f))
        assertEquals(0f, sweptCircleHit(Vec2.ZERO, Vec2(100f, 0f), 10f)!!, 0f)
    }

    @Test fun deathHasTenIndependentSquaresWithZeroPeakZeroSize() {
        val burst = DeathBurst(Vec2.ZERO, Random(1))
        assertEquals(10, burst.particles.size)
        assertTrue(burst.particles.map { it.angle }.distinct().size > 1)
        burst.particles.forEach {
            assertEquals(0f, it.sizeAt(0f), 0.0001f)
            assertEquals(it.size, it.sizeAt(it.lifetime / 2), 0.0001f)
            assertEquals(0f, it.sizeAt(it.lifetime), 0.0001f)
        }
        burst.advance(1f)
        assertTrue(burst.isFinished)
    }

    @Test fun killXpAdvancesOneSharedHudProgressValue() {
        val world = world()
        repeat(5) { world.damageEnemy(world.spawnEnemy(Vec2(300f, 200f)).id, 80) }
        assertEquals(2, world.level)
        assertEquals(0f, world.experienceFraction, 0f)
        assertEquals(70, world.nextLevelExperience)
    }

    @Test fun deadPartialCaptionCompletesWithoutFiringOrDuplicatingKillXp() {
        val session = session()
        val enemy = attach(session)
        session.onInput(tokens("k"), false)
        session.world.damageEnemy(enemy.id, 80)
        session.update(0.01f)
        assertTrue(session.captions.single().retired)
        assertNull(session.captions.single().fadeAge)
        assertTrue(session.onInput(tokens("aki"), false))
        assertTrue(session.captions.isEmpty())
        assertEquals(0, (session.world.mainWeapon as Repeater).pendingShots)
        assertEquals(1, session.world.kills)
        assertEquals("kaki", session.completions.single().state.inputText)
        assertEquals(0, session.streak)
        assertEquals(0, session.mistypeCount)
        assertTrue(session.overlapEvents.isEmpty())
    }

    @Test fun unfinishedImeCompositionSurvivesDeathWithoutDoubleAdvancement() {
        val session = session()
        val enemy = attach(session)
        session.onInput(tokens("k"), true)
        session.world.damageEnemy(enemy.id, 80)
        session.update(0.01f)
        assertTrue(session.captions.single().retired)
        session.onInput(tokens("ka"), true)
        assertEquals(2, session.captions.single().state.matchedInputCharacters)
        assertTrue(session.onInput(tokens("kaki"), true))
        assertEquals(0, (session.world.mainWeapon as Repeater).pendingShots)
    }

    @Test fun aRetiredCaptionCannotRestartAtAFreshSuffixAfterAMistake() {
        val session = session()
        val enemy = attach(session)
        session.onInput(tokens("k"), true)
        session.world.damageEnemy(enemy.id, 80)
        session.update(0f)
        session.onInput(tokens("kt"), true)
        assertNotNull(session.captions.single().fadeAge)
        assertFalse(session.onInput(tokens("kaki"), true))
        session.update(0.2f)
        assertTrue(session.captions.isEmpty())
    }

    @Test fun inactiveDeadCaptionFadesImmediately() {
        val session = session()
        val enemy = attach(session)
        session.world.damageEnemy(enemy.id, 80)
        session.update(0.2f)
        assertTrue(session.captions.isEmpty())
        assertFalse(session.onInput(tokens("kaki"), false))
    }

    @Test fun liveCompletionQueuesBurstAndFadesInANewWordVersion() {
        val session = session()
        attach(session)
        val oldId = session.captions.single().word.id
        assertTrue(session.onInput(tokens("kaki"), false))
        assertNotEquals(oldId, session.captions.single().word.id)
        assertEquals(0f, session.captions.single().alpha, 0f)
        assertEquals(3, (session.world.mainWeapon as Repeater).pendingShots)
        assertEquals("kaki", session.completions.single().state.inputText)
    }

    @Test fun dynamicallySpawnedWordsCannotConsumePastComposition() {
        val engine = WordMatchEngine(listOf(WordDefinition("old", "かき")))
        val pending = tokens("ka")
        engine.addWord(WordDefinition("new", "あい"), pending)
        val preview = engine.previewInput(tokens("kai"))
        assertFalse(preview.completedWords.any { it.id == "new" })
        assertEquals(0, preview.words.first { it.definition.id == "new" }.matchedInputCharacters)
    }

    @Test fun retiringObservedRomanKanaCannotCreateANewStartInsideTheExtendedObservation() {
        val engine = WordMatchEngine(listOf(WordDefinition("word", "かか")))
        val first = listOf(InputToken.KanaObservation("か", "k"))
        engine.retireWord("word", first)
        assertTrue(engine.previewInput(listOf(InputToken.KanaObservation("かか", "kak"))).completedWords.isNotEmpty())
        assertTrue(engine.previewInput(listOf(InputToken.KanaObservation("たかか", "takak"))).completedWords.isEmpty())
    }

    @Test fun completionAnimationKeepsTheActuallyRecognizedRomanSpelling() {
        val engine = WordMatchEngine(listOf(WordDefinition("word", "しっぱい")))
        tokens("sippai").forEach { engine.accept(it) }
        assertEquals("sippai", engine.completedSnapshot("word")!!.inputText)
    }

    @Test fun targetRemovalPreservesOtherProgressAndDoesNotCreateAMistake() {
        val engine = WordMatchEngine(listOf(WordDefinition("one", "かき"), WordDefinition("two", "かこ")))
        tokens("ka").forEach { engine.accept(it) }
        engine.removeWord("one")
        assertEquals(0, engine.mistypeCount)
        assertEquals("two", tokens("ko").flatMap { engine.accept(it) }.single().id)
    }

    @Test fun worldAcceptsANewEnemyBehaviorAndMainWeaponWithoutTypeBranches() {
        var targeted = 0L
        val weapon = object : MainWeapon {
            override val displayName = "test"
            override fun target(enemyId: Long, position: Vec2) { targeted = enemyId }
            override fun targetDied(enemyId: Long, position: Vec2) = Unit
            override fun resize(xScale: Float, yScale: Float) = Unit
            override fun update(dt: Float, origin: Vec2, resolve: (Long) -> Vec2?) = emptyList<WeaponAction>()
        }
        val world = world(weapon)
        val definition = EnemyDefinition(behaviorFactory = { EnemyBehavior { enemy, dt, _ -> enemy.position += Vec2(dt * 10f, 0f) } })
        val enemy = world.spawnEnemy(Vec2(300f, 200f), definition)
        world.target(enemy.id)
        world.update(0.5f)
        assertEquals(enemy.id, targeted)
        assertEquals(305f, enemy.position.x, 0.001f)
        assertEquals(200f, enemy.position.y, 0.001f)
    }
}
