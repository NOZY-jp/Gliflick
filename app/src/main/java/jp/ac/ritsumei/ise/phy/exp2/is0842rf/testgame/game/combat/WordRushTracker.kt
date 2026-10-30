package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.roundToInt

/**
 * Word Rush timing is not published as an exact formula. Keep the prototype tuning
 * separate so it can be adjusted without changing input matching or weapon code.
 */
data class WordRushDefinition(
    val wordsToActivate: Int = 3,
    val secondsPerReadingCharacter: Double = 0.55,
    val activeSeconds: Double = 2.0,
    val rateOfFireBonus: Float = 0.25f
) {
    init {
        require(wordsToActivate >= 2)
        require(secondsPerReadingCharacter > 0.0 && secondsPerReadingCharacter.isFinite())
        require(activeSeconds > 0.0 && activeSeconds.isFinite())
        require(rateOfFireBonus >= 0f && rateOfFireBonus.isFinite())
    }
}

data class CombatTypingBonusDefinition(
    val criticalChancePerReadingCharacter: Float = 0.01f,
    val maximumCriticalStreakCharacters: Int = 5,
    val wordRush: WordRushDefinition = WordRushDefinition()
) {
    init {
        require(criticalChancePerReadingCharacter >= 0f && criticalChancePerReadingCharacter.isFinite())
        require(maximumCriticalStreakCharacters >= 0)
    }
}

/** Read-only presentation values for the combat HUD; the tracker remains the source of truth. */
data class CombatTypingBonusStatus(
    val streakCharacters: Int,
    val criticalChanceBonusPercent: Int,
    val maximumCriticalChanceBonusPercent: Int,
    val isWordRushActive: Boolean,
    val wordRushProgress: Float,
    val rateOfFireBonusPercent: Int
)

/** Tracks only successful, live-target word completions; the shared matcher owns correctness. */
class WordRushTracker(val definition: WordRushDefinition = WordRushDefinition()) {
    private var consecutiveFastWords = 0
    private var lastCompletionTime: Double? = null
    private var activeUntil: Double? = null

    fun isActiveAt(timeSeconds: Double) = activeUntil?.let { timeSeconds < it } ?: false

    fun onWordCompleted(readingLength: Int, timeSeconds: Double) {
        require(readingLength > 0)
        require(timeSeconds >= 0.0 && timeSeconds.isFinite())

        val previous = lastCompletionTime
        val maximumGap = readingLength * definition.secondsPerReadingCharacter
        if (previous != null && timeSeconds - previous <= maximumGap) {
            consecutiveFastWords++
        } else {
            consecutiveFastWords = 1
        }
        lastCompletionTime = timeSeconds

        if (consecutiveFastWords >= definition.wordsToActivate) {
            activeUntil = timeSeconds + definition.activeSeconds
        }
    }

    fun onMistype() {
        consecutiveFastWords = 0
        lastCompletionTime = null
        activeUntil = null
    }

    fun progressAt(timeSeconds: Double): Float {
        val activeExpiry = activeUntil
        if (activeExpiry != null && timeSeconds < activeExpiry) {
            return ((activeExpiry - timeSeconds) / definition.activeSeconds).toFloat().coerceIn(0f, 1f)
        }
        return 0f
    }

    fun rateOfFireMultiplier(timeSeconds: Double): Float {
        val expiry = activeUntil ?: return 1f
        if (timeSeconds >= expiry) {
            activeUntil = null
            return 1f
        }
        return 1f + definition.rateOfFireBonus
    }
}

/** Converts existing matching results into modifiers without moving matching rules into weapons. */
class CombatTypingBonusTracker(val definition: CombatTypingBonusDefinition = CombatTypingBonusDefinition()) {
    private val wordRush = WordRushTracker(definition.wordRush)

    fun isWordRushActiveAt(timeSeconds: Double) = wordRush.isActiveAt(timeSeconds)

    fun onWordCompleted(readingLength: Int, timeSeconds: Double) =
        wordRush.onWordCompleted(readingLength, timeSeconds)

    fun onMistype() = wordRush.onMistype()

    fun modifiers(streak: Int, timeSeconds: Double): WeaponRuntimeModifiers {
        val critBonus = minOf(streak, definition.maximumCriticalStreakCharacters) *
            definition.criticalChancePerReadingCharacter
        val rateMultiplier = wordRush.rateOfFireMultiplier(timeSeconds)
        return WeaponRuntimeModifiers(critBonus, rateMultiplier)
    }

    fun status(streak: Int, timeSeconds: Double): CombatTypingBonusStatus {
        val criticalBonus = minOf(streak, definition.maximumCriticalStreakCharacters) *
            definition.criticalChancePerReadingCharacter
        val maximumCriticalBonus = definition.maximumCriticalStreakCharacters *
            definition.criticalChancePerReadingCharacter
        val rateMultiplier = wordRush.rateOfFireMultiplier(timeSeconds)
        return CombatTypingBonusStatus(
            streakCharacters = streak,
            criticalChanceBonusPercent = (criticalBonus * 100f).roundToInt(),
            maximumCriticalChanceBonusPercent = (maximumCriticalBonus * 100f).roundToInt(),
            isWordRushActive = rateMultiplier > 1f,
            wordRushProgress = wordRush.progressAt(timeSeconds),
            rateOfFireBonusPercent = (definition.wordRush.rateOfFireBonus * 100f).roundToInt()
        )
    }
}
