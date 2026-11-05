package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.*
import kotlin.random.Random

/** Kill-gated random renewal: mean 120 seconds, with fewer short clustered intervals. */
class RerollDropPolicy(val meanSeconds: Double = 120.0) {
    private var nextDropAt: Double? = null
    private var previousDeath = 0.0
    init { require(meanSeconds > 0 && meanSeconds.isFinite()) }
    private fun nextWait(random:Random):Double = (1..4).sumOf {
        -ln1p(-random.nextDouble()) * (meanSeconds/4.0)
    }
    fun onDeath(seconds: Double, random: Random): Boolean {
        if(nextDropAt==null) nextDropAt=nextWait(random)
        if(seconds<=previousDeath) return false
        previousDeath=seconds
        if(seconds<checkNotNull(nextDropAt)) return false
        nextDropAt=seconds+nextWait(random)
        return true
    }
}

enum class BossKind(val title: String, val description: List<String>) {
    GEMINI("ジェミニ", listOf("双子の敵", "互いを回復し続ける。バランスよく攻撃せよ", "触手が出てきたら文字を連打せよ")),
    CYPHER("サイファー", listOf("文字に混沌をもたらす者", "効果範囲内の文字をめちゃくちゃにする", "範囲攻撃で対処せよ")),
    POET("詩人", listOf("詩の響きに魅入られた怪人", "最後までタイプし続けろ", "敵を強化する弾に注意せよ"))
}

enum class ArrivalEdge { TOP, RIGHT, BOTTOM, LEFT }

/** Natural-arrival rules are separate from enemy attacks, movement, and simulation time. */
data class EnemyArrivalRules(
    val rateMultiplier: Float = 1f,
    val edges: List<ArrivalEdge> = ArrivalEdge.entries,
    val chooseEdgeOnce: Boolean = false
) {
    init { require(rateMultiplier>=0f && rateMultiplier.isFinite() && edges.isNotEmpty() && edges.distinct().size==edges.size) }
    companion object { val NORMAL=EnemyArrivalRules() }
}

data class BossBalance(
    val intervalSeconds: Double = 240.0,
    val geminiHp: Int = 12000,
    val cypherHp: Int = 5000,
    val healHpPerSecond: Float = 75f,
    val tendrilInterval: Float = 12f,
    val tendrilApproach: Float = 6f,
    val tendrilStrokes: Int = 10,
    val tendrilDamage: Int = 60,
    val poetShotInterval: Float = 5f,
    val poetShotSpeed: Float = 48f
) {
    init { require(intervalSeconds > 0 && geminiHp > 0 && cypherHp > 0 && tendrilStrokes > 0 &&
        tendrilInterval > 0 && tendrilApproach > 0 && poetShotInterval > 0 && poetShotSpeed > 0) }
}

class Tendril(val glyph: String, val duration: Float, val required: Int) {
    var age = 0f
    var strokes = 0
    var retreatAge: Float? = null
    var counterPulseAge: Float? = null
    val counterScale get() = counterPulseAge?.let {
        val t=(it/0.22f).coerceIn(0f,1f)
        1f+0.32f*sin(PI.toFloat()*t)*(1f-t)
    } ?: 1f
    val progress get() = (age / duration).coerceIn(0f, 1f)
    val finished get() = strokes >= required || age >= duration
}
class PoetOrb(var position: Vec2, var velocity: Vec2) {
    var age = 0f
}
data class PoemPhrase(val label: String, val reading: String)

/** Public-domain Soseki passage listed on the Japanese Wiki; checkpoints preserve completed phrases. */
object PoetPassage {
    val phrases = listOf(
        PoemPhrase("吾輩は猫である", "わがはいはねこである"), PoemPhrase("名前はまだ無い", "なまえはまだない"),
        PoemPhrase("どこで生れたか", "どこでうまれたか"), PoemPhrase("とんと見当がつかぬ", "とんとけんとうがつかぬ"),
        PoemPhrase("何でも薄暗い", "なんでもうすぐらい"), PoemPhrase("じめじめした所で", "じめじめしたところで"),
        PoemPhrase("ニャーニャー泣いていた", "にゃーにゃーないていた"), PoemPhrase("事だけは記憶している", "ことだけはきおくしている"),
        PoemPhrase("吾輩はここで始めて", "わがはいはここではじめて"), PoemPhrase("人間というものを見た", "にんげんというものをみた"),
        PoemPhrase("しかもあとで聞くと", "しかもあとできくと"), PoemPhrase("それは書生という", "それはしょせいという"),
        PoemPhrase("人間中で一番獰悪な", "にんげんじゅうでいちばんどうあくな"), PoemPhrase("種族であったそうだ", "しゅぞくであったそうだ"),
        PoemPhrase("この書生というのは", "このしょせいというのは"), PoemPhrase("時々我々を捕えて", "ときどきわれわれをつかまえて"),
        PoemPhrase("煮て食うという話である", "にてくうというはなしである"), PoemPhrase("しかしその当時は", "しかしそのとうじは"),
        PoemPhrase("何という考もなかったから", "なんというかんがえもなかったから"), PoemPhrase("別段恐しいとも思わなかった", "べつだんおそろしいともおもわなかった"),
        PoemPhrase("ただ彼の掌に載せられて", "ただかれのてのひらにのせられて"), PoemPhrase("スーと持ち上げられた時", "すーともちあげられたとき"),
        PoemPhrase("何だかフワフワした感じが", "なんだかふわふわしたかんじが"), PoemPhrase("あったばかりである", "あったばかりである")
    )
}

/** Boss scheduling is requested by CombatSession, which owns the paused introduction/input boundary. */
class BossDirector(val balance: BossBalance = BossBalance()) {
    var nextAppearanceAt = balance.intervalSeconds; private set
    var encounter = 0; private set
    var activeKind: BossKind? = null; private set
    var introduction: BossKind? = null; private set
    private val bodies = mutableListOf<Enemy>()
    private var behavior: BossEncounterBehavior? = null
    val enemies: List<Enemy> get() = bodies
    val arrivalRules get() = if(activeKind!=null) behavior?.arrivalRules ?: EnemyArrivalRules.NORMAL else EnemyArrivalRules.NORMAL
    val tendril get() = behavior?.tendril
    val orbs get() = behavior?.orbs.orEmpty()
    val poemIndex get() = behavior?.poemIndex ?: 0
    val poemProgress get() = poemIndex.toFloat() / PoetPassage.phrases.size
    fun auraRadius(world: CombatWorld) = min(world.width, world.height) * 0.42f
    fun requestIfDue(seconds: Double): Boolean {
        if(activeKind != null || introduction != null || seconds+1e-7 < nextAppearanceAt) return false
        introduction=BossKind.entries[encounter % BossKind.entries.size]
        return true
    }
    fun begin(world: CombatWorld, kind: BossKind = checkNotNull(introduction)) {
        check(activeKind == null)
        world.prepareBossSlots(if(kind == BossKind.GEMINI) 2 else 1)
        introduction = null; activeKind = kind; encounter++
        nextAppearanceAt = max(nextAppearanceAt + balance.intervalSeconds,
            (floor(world.elapsedSeconds / balance.intervalSeconds) + 1) * balance.intervalSeconds)
        bodies.clear()
        val count = if (kind == BossKind.GEMINI) 2 else 1
        repeat(count) { index ->
            val hp = when(kind) { BossKind.GEMINI -> balance.geminiHp; BossKind.CYPHER -> balance.cypherHp; BossKind.POET -> 1 }
            val radius = when(kind) { BossKind.GEMINI -> min(24f, world.width * 0.065f); BossKind.CYPHER -> 18f; BossKind.POET -> 23f }
            val position = when(kind) {
                BossKind.GEMINI -> Vec2(world.width * if(index == 0) 0.16f else 0.84f, world.height * 0.43f)
                BossKind.CYPHER -> Vec2(world.width * 0.5f, world.height * 0.27f)
                BossKind.POET -> Vec2(world.width / 2f, 101f.coerceAtMost(world.height * 0.24f))
            }
            val definition = EnemyDefinition(kind = EnemyKind.BOSS, bossKind = kind, maxHp = hp,
                speed = 0f, contactDamage = 0, radius = radius, collisionRadius=if(kind==BossKind.CYPHER)5f else radius,
                behaviorFactory = { when(kind) {
                    BossKind.CYPHER -> CypherWander { Vec2(world.width,world.height) }
                    BossKind.GEMINI -> GeminiFloat(index) { Vec2(world.width,world.height) }
                    BossKind.POET -> EnemyBehavior { _, _, _ -> }
                } })
            bodies += world.spawnEnemy(position, definition).also { it.cyphered=kind==BossKind.CYPHER }
        }
        behavior = when(kind) {
            BossKind.GEMINI -> GeminiEncounter(bodies.toList(), balance)
            BossKind.CYPHER -> CypherEncounter(bodies.toList())
            BossKind.POET -> PoetEncounter(bodies.toList(), balance)
        }
        onViewportChanged(world)
    }
    fun counterTendril() { behavior?.counterTendril() }
    fun completePhrase(world: CombatWorld) { behavior?.completePhrase(world) }
    fun update(world: CombatWorld, dt: Float) {
        if(activeKind == null) return
        if(bodies.none { it.isAlive }) {
            behavior?.finish(world); activeKind=null
            world.notifyBossDefeated()
        } else behavior?.update(world,dt)
    }
    fun resize(sx: Float, sy: Float) { behavior?.resize(sx,sy) }
    fun onViewportChanged(world:CombatWorld) {
        bodies.forEachIndexed { index,enemy ->
            if(!enemy.isAlive) return@forEachIndexed
            when(activeKind) {
                BossKind.GEMINI -> enemy.position=Vec2(world.width*if(index==0)0.16f else 0.84f,world.height*0.43f)
                BossKind.POET -> enemy.position=Vec2(world.width/2f,101f.coerceAtMost(world.height*0.24f))
                else -> Unit
            }
            enemy.previousPosition=enemy.position
            enemy.angle=atan2(world.player.position.y-enemy.position.y,world.player.position.x-enemy.position.x)
        }
    }
}

private class CypherWander(private val bounds:()->Vec2): EnemyBehavior {
    private var age = 0f
    override fun update(enemy: Enemy, dt: Float, playerPosition: Vec2) {
        age += dt
        val size=bounds();val width=size.x;val height=size.y
        enemy.position = Vec2(width * (0.5f + 0.25f * sin(age * 0.35f)), height * (0.27f + 0.12f * sin(age * 0.28f)))
    }
}

/** Physical and caption positions float together; no renderer-only hitbox offset. */
private class GeminiFloat(private val index:Int,private val bounds:()->Vec2):EnemyBehavior {
    private var age=0f
    override fun update(enemy:Enemy,dt:Float,playerPosition:Vec2) {
        age+=dt
        val size=bounds();val phase=if(index==0) 0f else PI.toFloat()
        enemy.position=Vec2(size.x*(if(index==0)0.16f else 0.84f)+4f*sin(age*0.85f+phase),
            size.y*0.43f+6f*sin(age*1.2f+phase))
        enemy.angle=atan2(playerPosition.y-enemy.position.y,playerPosition.x-enemy.position.x)
    }
}
