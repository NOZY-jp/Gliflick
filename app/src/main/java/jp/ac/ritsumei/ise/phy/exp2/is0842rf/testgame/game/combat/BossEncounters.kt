package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.min

/** A new boss adds its own controller without adding combat/input logic to the director or renderer. */
internal interface BossEncounterBehavior {
    val arrivalRules:EnemyArrivalRules get()=EnemyArrivalRules.NORMAL
    val tendril: Tendril? get() = null
    val orbs: List<PoetOrb> get() = emptyList()
    val poemIndex: Int get() = 0
    fun update(world: CombatWorld, dt: Float)
    fun counterTendril() = Unit
    fun completePhrase(world: CombatWorld) = Unit
    fun resize(sx: Float, sy: Float) = Unit
    fun finish(world: CombatWorld) { world.enemies.forEach { it.cyphered=false;it.wordSealed=false } }
}

internal class GeminiEncounter(private val bodies: List<Enemy>,private val balance: BossBalance): BossEncounterBehavior {
    override var tendril: Tendril? = null; private set
    private var attackClock=0f
    private var healCredit=0f
    override fun update(world: CombatWorld,dt: Float) {
        val alive=bodies.filter { it.isAlive }
        if(alive.size==2) {
            healCredit+=balance.geminiHp*balance.healFractionPerSecond*dt
            val heal=healCredit.toInt();healCredit-=heal
            val highest=alive.maxOf { it.hp }
            alive.forEach { it.heal(min(heal,highest-it.hp)) }
        }
        attackClock+=dt
        tendril?.let {
            val retreat=it.retreatAge
            if(retreat!=null) {
                it.retreatAge=retreat+dt
                if(retreat+dt>=0.24f) tendril=null
            } else {
                it.age+=dt
                if(it.age>=it.duration) { world.player.takeDamage(balance.tendrilDamage);tendril=null }
            }
        }
        if(tendril==null && attackClock>=balance.tendrilInterval) {
            attackClock=0f
            tendril=Tendril(listOf("あ","い","う","え","お")[world.randomIndex(5)],balance.tendrilApproach,balance.tendrilStrokes)
        }
    }
    override fun counterTendril() { tendril?.let {
        if(!it.finished) { it.strokes++;if(it.strokes>=it.required) it.retreatAge=0f }
    } }
    override fun finish(world:CombatWorld) { tendril=null;super.finish(world) }
}

internal class CypherEncounter(private val bodies:List<Enemy>):BossEncounterBehavior {
    override fun update(world:CombatWorld,dt:Float) {
        val boss=bodies.first { it.isAlive };val radius=world.bosses.auraRadius(world)
        world.enemies.forEach { enemy -> enemy.cyphered=enemy.isAlive &&
            (enemy.definition.bossKind==BossKind.CYPHER || (enemy.definition.bossKind==null &&
                (enemy.position-boss.position).length()<=radius)) }
    }
}

internal class PoetEncounter(private val bodies:List<Enemy>,private val balance:BossBalance):BossEncounterBehavior {
    override val arrivalRules=EnemyArrivalRules(0.75f,listOf(ArrivalEdge.RIGHT,ArrivalEdge.BOTTOM,ArrivalEdge.LEFT),chooseEdgeOnce=true)
    private val mutableOrbs=mutableListOf<PoetOrb>()
    override val orbs:List<PoetOrb> get()=mutableOrbs
    override var poemIndex=0;private set
    private var attackClock=0f
    override fun completePhrase(world:CombatWorld) {
        poemIndex++
        if(poemIndex>=PoetPassage.phrases.size) bodies.filter { it.isAlive }.forEach { world.defeatBossBody(it) }
    }
    override fun update(world:CombatWorld,dt:Float) {
        attackClock+=dt
        if(attackClock>=balance.poetShotInterval) {
            attackClock%=balance.poetShotInterval
            world.enemies.filter { it.isAlive && it.definition.bossKind==null && !it.wordSealed }
                .sortedBy { it.id }.take(3).forEach { mutableOrbs+=PoetOrb(bodies.first().position,
                    (it.position-bodies.first().position).normalized()*balance.poetShotSpeed) }
        }
        mutableOrbs.removeAll { orb ->
            orb.age+=dt
            val previous=orb.position;orb.position+=orb.velocity*dt
            val hit=world.enemies.filter { it.isAlive && it.definition.bossKind==null }.flatMap { enemy ->
                enemy.hitCircles().mapNotNull { circle ->
                    sweptCircleHit(previous-circle.previousPosition,orb.position-circle.position,circle.radius+3f)?.let { enemy to it }
                }
            }.minByOrNull { it.second }
            if(hit!=null) hit.first.wordSealed=true
            hit!=null || orb.age>15f || orb.position.x !in -20f..world.width+20f || orb.position.y !in -20f..world.height+20f
        }
    }
    override fun resize(sx:Float,sy:Float) { mutableOrbs.forEach {
        it.position=it.position.scaled(sx,sy)
        it.velocity=it.velocity.scaled(sx,sy).normalized()*it.velocity.length()
    } }
    override fun finish(world:CombatWorld) { mutableOrbs.clear();super.finish(world) }
}
