package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.OverlapEvent
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import kotlin.random.Random

class EnemyCaption(val enemyId: Long, val word: WordDefinition, var anchor: Vec2, var state: WordMatchState,
                   val placeBelowEnemy: Boolean = false, val size: Float = 1f) {
    var age = 0f
    var retired = false
    var fadeAge: Float? = null
    val alpha get() = (age / 0.24f).coerceIn(0f, 1f) *
        (1f - (fadeAge ?: 0f) / CombatWorld.DEATH_FADE_SECONDS).coerceIn(0f, 1f)
}

class CompletedCaption(val state: WordMatchState, var anchor: Vec2, val placeBelowEnemy: Boolean = false,
                       val size: Float = 1f) {
    var age = 0f
    val progress get() = (age / 0.42f).coerceIn(0f, 1f)
}

/** Bridges word lifetimes and physical lifetimes without turning a completed word into a kill. */
class CombatSession(
    private val definitions: List<WordDefinition>,
    val world: CombatWorld = CombatWorld(),
    private val random: Random = Random.Default,
    private val wordLengthPolicy: WordLengthPolicy = ProgressiveGaussianWordLengthPolicy()
) {
    private val playableDefinitions = definitions.filter { it.readingLength in 2..7 }
    private val engine = WordMatchEngine(emptyList())
    private val mutableCaptions = mutableListOf<EnemyCaption>()
    private val mutableCompletions = mutableListOf<CompletedCaption>()
    private var wordVersion = 0L
    private var pendingTokens: List<InputToken> = emptyList()
    val captions: List<EnemyCaption> get() = mutableCaptions
    val completions: List<CompletedCaption> get() = mutableCompletions
    val streak get() = engine.streak
    val mistypeCount get() = engine.mistypeCount
    var overlapEvents: List<OverlapEvent> = emptyList()
        private set

    init { require(playableDefinitions.isNotEmpty()) { "Combat needs words with readings from 2 to 7 characters." } }

    fun resize(width: Float, height: Float) {
        if (world.width > 0f) {
            val sx = width / world.width
            val sy = height / world.height
            mutableCaptions.forEach { it.anchor = it.anchor.scaled(sx, sy) }
            mutableCompletions.forEach { it.anchor = it.anchor.scaled(sx, sy) }
        }
        world.resize(width, height)
        syncEvents()
    }

    fun update(dt: Float) {
        world.update(dt)
        syncEvents()
        mutableCaptions.forEach { caption ->
            caption.age += dt
            caption.fadeAge?.let { caption.fadeAge = it + dt }
            if (!caption.retired) world.enemies.firstOrNull { it.id == caption.enemyId && it.isAlive }
                ?.let { caption.anchor = it.position }
        }
        mutableCaptions.removeAll { (it.fadeAge ?: 0f) >= CombatWorld.DEATH_FADE_SECONDS }
        mutableCompletions.forEach { it.age += dt }
        mutableCompletions.removeAll { it.progress >= 1f }
    }

    /** Uses the same speculative-composition contract as the existing input test and title menu. */
    fun onInput(tokens: List<InputToken>, composing: Boolean): Boolean {
        if (world.isGameOver) return false
        val preview = if (composing) engine.previewInput(tokens) else null
        val apply = !composing || preview!!.completedWords.isNotEmpty()
        var completedAny = false
        val overlaps = mutableListOf<OverlapEvent>()
        if (apply) {
            pendingTokens = emptyList()
            tokens.forEach { token ->
                val completed = engine.accept(token)
                overlaps += engine.lastOverlapEvents
                completed.forEach { word ->
                    completedAny = true
                    complete(word.id)
                }
            }
        } else pendingTokens = tokens.toList()
        overlapEvents = overlaps
        refreshStates()
        pruneRetired()
        return completedAny
    }

    private fun complete(id: String) {
        val caption = mutableCaptions.firstOrNull { it.word.id == id } ?: return
        val completed = engine.completedSnapshot(id) ?: caption.state.copy(isCompleted = true,
            matchedInputCharacters = caption.state.inputText.length)
        mutableCompletions += CompletedCaption(completed, caption.anchor, caption.placeBelowEnemy, caption.size)
        mutableCaptions.remove(caption)
        if (caption.retired) {
            engine.removeWord(id)
        } else {
            world.target(caption.enemyId)
            val next = selectWord(caption.enemyId)
            engine.replaceWord(id, next)
            mutableCaptions += EnemyCaption(caption.enemyId, next, caption.anchor,
                engine.snapshot().first { it.definition.id == next.id }, caption.placeBelowEnemy, caption.size)
        }
    }

    private fun syncEvents() {
        world.drainEvents().forEach { event -> when (event) {
            is CombatEvent.Spawned -> {
                val word = selectWord(event.enemy.id)
                engine.addWord(word, pendingTokens)
                val placeBelowEnemy = event.enemy.spawnOrigin.y < world.height / 2f
                mutableCaptions += EnemyCaption(event.enemy.id, word, event.enemy.position,
                    engine.snapshot().first { it.definition.id == word.id }, placeBelowEnemy, event.enemy.size)
            }
            is CombatEvent.Died -> {
                val caption = mutableCaptions.firstOrNull { it.enemyId == event.enemyId && !it.retired }
                if (caption != null) {
                    caption.anchor = event.position
                    caption.retired = true
                    if (caption.state.matchedInputCharacters > 0) {
                        engine.retireWord(caption.word.id, pendingTokens)
                    } else {
                        engine.removeWord(caption.word.id)
                        caption.fadeAge = 0f
                    }
                }
            }
        } }
        refreshStates()
    }

    private fun refreshStates() {
        val states = if (pendingTokens.isEmpty()) engine.snapshot() else engine.previewInput(pendingTokens).words
        val byId = states.associateBy { it.definition.id }
        mutableCaptions.forEach { caption -> byId[caption.word.id]?.let { caption.state = it } }
    }

    private fun pruneRetired() {
        mutableCaptions.filter { it.retired && it.fadeAge == null && it.state.matchedInputCharacters == 0 }
            .forEach { engine.removeWord(it.word.id); it.fadeAge = 0f }
    }

    private fun selectWord(enemyId: Long): WordDefinition {
        val used = mutableCaptions.filter { !it.retired }.map { it.word.text }.toSet()
        val pool = playableDefinitions.filter { it.text !in used }.ifEmpty { playableDefinitions }
        val groupedByLength = pool.groupBy { it.readingLength }
        val selectedLength = wordLengthPolicy.chooseLength(world.elapsedSeconds, groupedByLength.keys, random)
        val lengthPool = groupedByLength[selectedLength] ?: pool
        return lengthPool[random.nextInt(lengthPool.size)].copy(id = "enemy-$enemyId-word-${wordVersion++}")
    }

}
