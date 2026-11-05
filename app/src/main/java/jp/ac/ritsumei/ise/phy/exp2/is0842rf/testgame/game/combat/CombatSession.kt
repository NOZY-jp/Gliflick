package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.matching.WordMatchEngine
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.OverlapEvent
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import kotlin.random.Random

class EnemyCaption(val enemyId: Long, val word: WordDefinition, var anchor: Vec2, var state: WordMatchState,
                   val placeBelowEnemy: Boolean = false, val radius: Float = 4.5f, val enemy: Enemy? = null,
                   val normalWord: WordDefinition = word, val cyphered: Boolean = false) {
    var age = 0f
    var retired = false
    var fadeAge: Float? = null
    val alpha get() = (age / 0.24f).coerceIn(0f, 1f) *
        (1f - (fadeAge ?: 0f) / CombatWorld.DEATH_FADE_SECONDS).coerceIn(0f, 1f)
}

class CompletedCaption(val state: WordMatchState, var anchor: Vec2, val placeBelowEnemy: Boolean = false,
                       val radius: Float = 4.5f, val enemy: Enemy? = null) {
    var age = 0f
    val progress get() = (age / 0.42f).coerceIn(0f, 1f)
}

class PickupCaption(val pickup: CombatPickup, val word: WordDefinition, var state: WordMatchState)

/** Bridges word lifetimes and physical lifetimes without turning a completed word into a kill. */
class CombatSession(
    private val definitions: List<WordDefinition>,
    val world: CombatWorld = CombatWorld(),
    private val random: Random = Random.Default,
    private val wordLengthPolicy: WordLengthPolicy = ProgressiveGaussianWordLengthPolicy(),
    private val typingBonusDefinition: CombatTypingBonusDefinition = CombatTypingBonusDefinition()
) {
    private val playableDefinitions = definitions.filter { it.readingLength in 2..7 }
    private val engine = WordMatchEngine(emptyList())
    private val typingBonuses = CombatTypingBonusTracker(typingBonusDefinition)
    private val mutableCaptions = mutableListOf<EnemyCaption>()
    private val mutableCompletions = mutableListOf<CompletedCaption>()
    private val mutablePickupCaptions = mutableListOf<PickupCaption>()
    private var bossIntroEngine: WordMatchEngine? = null
    private var bossIntroPreview: WordMatchState? = null
    private var tendrilWord: WordDefinition? = null
    private var poemWord: WordDefinition? = null
    var tendrilState: WordMatchState? = null; private set
    var poemState: WordMatchState? = null; private set
    val bossIntroState get() = (bossIntroPreview ?: bossIntroEngine?.snapshot()?.firstOrNull())?.let {
        it.copy(inputText="go",matchedInputCharacters=if(it.isCompleted)2 else if(it.inputText=="go")it.matchedInputCharacters else 0)
    }
    val tendrilInputText get() = tendrilState?.inputText ?: world.bosses.tendril?.glyph?.let {
        if(engine.inputMode==jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.InputMode.ROMAJI)
            jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages.forTag("ja").defaultRoman(listOf(it)) else it
    }
    private var wordVersion = 0L
    private var pendingTokens: List<InputToken> = emptyList()
    val captions: List<EnemyCaption> get() = mutableCaptions
    val completions: List<CompletedCaption> get() = mutableCompletions
    val pickupCaptions: List<PickupCaption> get() = mutablePickupCaptions
    private val mutableRewardFeedback = mutableListOf<RewardFeedback>()
    val rewardFeedback: List<RewardFeedback> get() = mutableRewardFeedback
    var presentationSeconds = 0.0; private set
    private var notifiedLevel = world.level
    private val notifiedEvolutions = mutableMapOf<WeaponId,Int>()
    var upgradeSelection: UpgradeSelection? = null; private set
    var rerollsRemaining = 3; private set
    val pendingEvolutionChoices get() = world.weaponStates.sumOf { it.pendingEvolutionChoices }
    val pendingChoiceCount get() = world.pendingLevelUps + pendingEvolutionChoices
    val canRerollUpgrades get() = upgradeSelection?.let { selection ->
        rerollsRemaining > 0 && !selection.isChoosing && (!selection.evolution ||
            world.evolutionOffers(selection.offers.first().weapon).size > selection.offers.size)
    } ?: false
    var inputEpoch = 0; private set
    val streak get() = engine.streak
    val isWordRushActive get() = typingBonuses.isWordRushActiveAt(world.elapsedSeconds)
    val typingBonusStatus get() = typingBonuses.status(engine.streak, world.elapsedSeconds)
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
        presentationSeconds += dt
        mutableRewardFeedback.forEach { it.advance(dt) }
        mutableRewardFeedback.removeAll { it.finished }
        notifyRewards()
        upgradeSelection?.let { selection ->
            selection.advance(dt)?.let { offer ->
                check(world.applyUpgrade(offer)) { "An offered upgrade must still be applicable." }
                upgradeSelection = null
                if (offer.kind == OfferKind.EVOLUTION) mutableRewardFeedback += RewardFeedback(offer.weapon)
                notifyRewards()
                if (!openNextReward(continuing = true)) inputEpoch++
            }
            return
        }
        if (world.bosses.introduction != null) return
        val step = if (world.bosses.activeKind == null)
            minOf(dt.toDouble(), (world.bosses.nextAppearanceAt - world.elapsedSeconds).coerceAtLeast(0.0)).toFloat() else dt
        world.update(step, typingBonuses.modifiers(engine.streak, world.elapsedSeconds))
        if (!world.isGameOver && world.bosses.requestIfDue(world.elapsedSeconds)) {
            bossIntroEngine = WordMatchEngine(listOf(WordDefinition("boss-go", "ご")), jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.InputMode.ROMAJI)
            pendingTokens = emptyList()
            inputEpoch++
        }
        syncEvents()
        syncBossWords()
        notifyRewards()
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

    private fun notifyRewards() {
        if (world.level > notifiedLevel) {
            mutableRewardFeedback += RewardFeedback(null)
            notifiedLevel = world.level
        }
        world.weaponStates.forEach { state ->
            val unlocked = state.evolutionsChosen + state.pendingEvolutionChoices
            if (unlocked > (notifiedEvolutions[state.id] ?: 0)) {
                mutableRewardFeedback += RewardFeedback(state.id)
                notifiedEvolutions[state.id] = unlocked
            }
        }
    }

    /** Space starts queued rewards; subsequent choices drain without resuming combat. */
    fun handleSpace(): Boolean {
        if (world.isGameOver || world.bosses.introduction != null) return false
        if (upgradeSelection != null) {
            rerollUpgrades()
            return true
        }
        return openNextReward()
    }

    private fun openNextReward(continuing: Boolean = false): Boolean {
        if (pendingChoiceCount == 0) return false
        val evolution = pendingEvolutionChoices > 0
        val selection = createReward(evolution) ?: return false
        if (!evolution && !world.consumeLevelUp()) return false
        if (continuing) selection.revealImmediately()
        upgradeSelection = selection
        pendingTokens = emptyList()
        refreshStates()
        inputEpoch++
        return true
    }

    private fun createReward(evolution: Boolean, evolutionWeapon: WeaponId? = null): UpgradeSelection? {
        val offers = UpgradeCatalog.roll(world, random, evolution, evolutionWeapon)
        if (offers.isEmpty()) return null
        val words = mutableListOf<WordDefinition>()
        playableDefinitions.filter { it.readingLength == 3 }.shuffled(random).forEach { candidate ->
            if (words.size < offers.size && words.none {
                it.text.first() == candidate.text.first() ||
                    jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages.forTag(it.languageTag)
                        .defaultRoman(it.characters).first() ==
                    jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.language.TypingLanguages.forTag(candidate.languageTag)
                        .defaultRoman(candidate.characters).first()
            }) words += candidate.copy(id = "upgrade-" + wordVersion++ + "-" + words.size)
        }
        // Tiny injected dictionaries used by domain callers still get unambiguous menu commands.
        val fallback = listOf(WordDefinition("u-light", "ひかり", "光"),
            WordDefinition("u-bond", "きずな", "絆"), WordDefinition("u-wing", "つばさ", "翼"))
        val selectedWords = if (words.size == offers.size) words else fallback.take(offers.size)
        return UpgradeSelection(offers, selectedWords, evolution, world.level, engine.inputMode)
    }

    fun rerollUpgrades(): Boolean {
        val selection = upgradeSelection ?: return false
        if (!canRerollUpgrades) return false
        val rerolled = createReward(selection.evolution, selection.offers.first().weapon) ?: return false
        rerollsRemaining--
        upgradeSelection = rerolled
        pendingTokens = emptyList()
        refreshStates()
        inputEpoch++
        return true
    }

    /** Uses the same speculative-composition contract as the existing input test and title menu. */
    fun onInput(tokens: List<InputToken>, composing: Boolean): Boolean {
        bossIntroEngine?.let { intro ->
            bossIntroPreview = if (composing) intro.previewInput(tokens).words.firstOrNull() else null
            if (!composing || intro.previewInput(tokens).completedWords.isNotEmpty()) {
                val done = tokens.flatMap { intro.accept(it) }.isNotEmpty()
                if (done) {
                    world.bosses.begin(world)
                    bossIntroEngine = null
                    bossIntroPreview = null
                    pendingTokens = emptyList()
                    inputEpoch++
                    syncEvents(); syncBossWords()
                }
                return done
            }
            return false
        }
        upgradeSelection?.let { return it.onInput(tokens, composing) }
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
                    if (mutableCaptions.any { it.word.id == word.id && !it.retired && it.enemy?.wordSealed != true }) {
                        typingBonuses.onWordCompleted(world.elapsedSeconds)
                    }
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
        if (id == tendrilWord?.id) {
            world.bosses.counterTendril()
            val old = checkNotNull(tendrilWord)
            if (world.bosses.tendril?.finished == false) {
                tendrilWord = old.copy(id = "tendril-${wordVersion++}")
                engine.replaceWord(id, checkNotNull(tendrilWord))
            } else { engine.removeWord(id); tendrilWord = null; tendrilState = null }
            return
        }
        if (id == poemWord?.id) {
            engine.removeWord(id); poemWord = null; poemState = null
            world.bosses.completePhrase(world)
            syncBossWords()
            return
        }
        mutablePickupCaptions.firstOrNull { it.word.id == id }?.let {
            world.activatePickup(it.pickup.id)
            engine.removeWord(id)
            mutablePickupCaptions.remove(it)
            return
        }
        val caption = mutableCaptions.firstOrNull { it.word.id == id } ?: return
        val completed = engine.completedSnapshot(id) ?: caption.state.copy(isCompleted = true,
            matchedInputCharacters = caption.state.inputText.length)
        mutableCompletions += CompletedCaption(completed, caption.anchor, caption.placeBelowEnemy, caption.radius, caption.enemy)
        mutableCaptions.remove(caption)
        if (caption.retired) {
            engine.removeWord(id)
        } else {
            val sealed = caption.enemy?.wordSealed == true
            if (sealed) caption.enemy?.wordSealed = false else world.completeWord(caption.word, caption.enemyId)
            val normal = if (sealed) caption.normalWord else selectWord(caption.enemyId)
            val next = if (sealed) caption.word.copy(id = "enemy-${caption.enemyId}-word-${wordVersion++}")
                else if (caption.enemy?.cyphered == true) cipherWord(normal) else normal
            engine.replaceWord(id, next)
            mutableCaptions += EnemyCaption(caption.enemyId, next, caption.anchor,
                engine.snapshot().first { it.definition.id == next.id }, caption.placeBelowEnemy, caption.radius, caption.enemy, normal, caption.enemy?.cyphered == true)
        }
    }

    private fun syncEvents() {
        world.drainEvents().forEach { event -> when (event) {
            is CombatEvent.Spawned -> {
                if (event.enemy.definition.bossKind == BossKind.POET) return@forEach
                val normal = selectWord(event.enemy.id)
                val word = if (event.enemy.cyphered) cipherWord(normal) else normal
                engine.addWord(word, pendingTokens)
                val placeBelowEnemy = event.enemy.spawnOrigin.y < world.height / 2f
                mutableCaptions += EnemyCaption(event.enemy.id, word, event.enemy.position,
                    engine.snapshot().first { it.definition.id == word.id }, placeBelowEnemy, event.enemy.radius, event.enemy, normal, event.enemy.cyphered)
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
            is CombatEvent.PickupSpawned -> {
                val word = selectWord(-event.pickup.id).copy(id = "pickup-" + event.pickup.id + "-" + wordVersion++)
                engine.addWord(word, pendingTokens)
                mutablePickupCaptions += PickupCaption(event.pickup, word,
                    engine.snapshot().first { it.definition.id == word.id })
            }
            is CombatEvent.RerollCollected -> rerollsRemaining++
            is CombatEvent.PickupRemoved -> {
                mutablePickupCaptions.filter { it.pickup.id == event.id }.forEach { engine.removeWord(it.word.id) }
                mutablePickupCaptions.removeAll { it.pickup.id == event.id }
            }
        } }
        refreshStates()
    }

    private fun refreshStates() {
        val states = if (pendingTokens.isEmpty()) engine.snapshot() else engine.previewInput(pendingTokens).words
        val byId = states.associateBy { it.definition.id }
        mutableCaptions.forEach { caption -> byId[caption.word.id]?.let { caption.state = it } }
        mutablePickupCaptions.forEach { caption -> byId[caption.word.id]?.let { caption.state = it } }
        tendrilState = tendrilWord?.let { byId[it.id] }
        poemState = poemWord?.let { byId[it.id] }
    }

    private fun pruneRetired() {
        mutableCaptions.filter { it.retired && it.fadeAge == null && it.state.matchedInputCharacters == 0 }
            .forEach { engine.removeWord(it.word.id); it.fadeAge = 0f }
    }

    private fun cipherWord(normal: WordDefinition): WordDefinition {
        val kana = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわ"
        var text: String
        do { text = (0 until normal.readingLength).joinToString("") { kana[random.nextInt(kana.length)].toString() } }
        while (text == normal.text)
        return WordDefinition("cipher-${wordVersion++}", text, text)
    }

    private fun syncBossWords() {
        mutableCaptions.toList().filter { !it.retired && it.enemy?.isAlive == true && it.cyphered != it.enemy.cyphered }
            .forEach { caption ->
                val next = if (caption.enemy!!.cyphered) cipherWord(caption.normalWord)
                    else selectWord(caption.enemyId)
                engine.removeWord(caption.word.id)
                engine.addWord(next, pendingTokens)
                mutableCaptions.remove(caption)
                mutableCaptions += EnemyCaption(caption.enemyId, next, caption.anchor,
                    engine.snapshot().first { it.definition.id == next.id }, caption.placeBelowEnemy, caption.radius,
                    caption.enemy, if (caption.enemy.cyphered) caption.normalWord else next, caption.enemy.cyphered)
            }
        val tendril = world.bosses.tendril?.takeUnless { it.finished }
        if (tendril == null) {
            tendrilWord?.let { engine.removeWord(it.id) }; tendrilWord = null
        } else if (tendrilWord == null) {
            tendrilWord = WordDefinition("tendril-${wordVersion++}", tendril.glyph)
            engine.addWord(checkNotNull(tendrilWord), pendingTokens)
        }
        val phrase = if(world.bosses.activeKind == BossKind.POET)
            PoetPassage.phrases.getOrNull(world.bosses.poemIndex) else null
        if (phrase == null) {
            poemWord?.let { engine.removeWord(it.id) }; poemWord = null
        } else if (poemWord == null) {
            poemWord = WordDefinition("poem-${wordVersion++}", phrase.reading, phrase.label)
            engine.addWord(checkNotNull(poemWord), pendingTokens)
        }
        refreshStates()
    }

    private fun selectWord(enemyId: Long): WordDefinition {
        val used = (mutableCaptions.filter { !it.retired }.map { it.word.text } +
            mutablePickupCaptions.map { it.word.text }).toSet()
        val pool = playableDefinitions.filter { it.text !in used }.ifEmpty { playableDefinitions }
        val groupedByLength = pool.groupBy { it.readingLength }
        val selectedLength = wordLengthPolicy.chooseLength(world.elapsedSeconds, groupedByLength.keys, random)
        val lengthPool = groupedByLength[selectedLength] ?: pool
        return lengthPool[random.nextInt(lengthPool.size)].copy(id = "enemy-$enemyId-word-${wordVersion++}")
    }

}
