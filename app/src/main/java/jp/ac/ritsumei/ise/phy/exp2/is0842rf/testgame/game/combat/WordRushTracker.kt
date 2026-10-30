package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.roundToInt

/**
 * Word Rush timing is not published as an exact formula. Keep the prototype tuning
 * separate so it can be adjusted without changing input matching or weapon code.
 */
data class WordRushDefinition(
    val stacksToActivate: Int = 5,
    val completionWindowSeconds: Double = 10.0,
    val activeSeconds: Double = 10.0,
    val rateOfFireBonus: Float = 0.5f
) {
    init {
        require(stacksToActivate >= 1)
        require(completionWindowSeconds > 0.0 && completionWindowSeconds.isFinite())
        require(activeSeconds > 0.0 && activeSeconds.isFinite())
        require(rateOfFireBonus >= 0f && rateOfFireBonus.isFinite())
    }
}

data class CombatTypingBonusDefinition(
    val criticalChancePerStreakStep: Float = 0.01f,
    val readingCharactersPerCriticalStep: Int = 20,
    val maximumCriticalStreakSteps: Int = 5,
    val wordRush: WordRushDefinition = WordRushDefinition()
) {
    init {
        require(criticalChancePerStreakStep >= 0f && criticalChancePerStreakStep.isFinite())
        require(readingCharactersPerCriticalStep > 0)
        require(maximumCriticalStreakSteps >= 0)
    }
}

/** Read-only presentation values for the combat HUD; the tracker remains the source of truth. */
data class CombatTypingBonusStatus(
    val streakCharacters: Int,
    val criticalChanceBonusPercent: Int,
    val maximumCriticalChanceBonusPercent: Int,
    val isWordRushActive: Boolean,
    val wordRushStacks: Int,
    val wordRushProgress: Float,
    val rateOfFireBonusPercent: Int
)

/** Tracks completed live-target words; the shared matcher owns correctness. */
class WordRushTracker(val definition: WordRushDefinition = WordRushDefinition()) {
    private var rapidFireStacks = 0
    private var lastCompletionTime: Double? = null
    private var activeUntil: Double? = null

    fun isActiveAt(timeSeconds: Double) = activeUntil?.let { timeSeconds < it } ?: false

    fun onWordCompleted(timeSeconds: Double) {
        require(timeSeconds >= 0.0 && timeSeconds.isFinite())

        val previous = lastCompletionTime
        if (previous != null && timeSeconds - previous <= definition.completionWindowSeconds) {
            rapidFireStacks = minOf(rapidFireStacks + 1, definition.stacksToActivate)
        } else {
            rapidFireStacks = 0
        }
        lastCompletionTime = timeSeconds

        if (rapidFireStacks >= definition.stacksToActivate) {
            activeUntil = timeSeconds + definition.activeSeconds
            rapidFireStacks = 0
        }
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

    fun onWordCompleted(timeSeconds: Double) = wordRush.onWordCompleted(timeSeconds)

    fun modifiers(streak: Int, timeSeconds: Double): WeaponRuntimeModifiers {
        val criticalSteps = minOf(
            streak / definition.readingCharactersPerCriticalStep,
            definition.maximumCriticalStreakSteps
        )
        val critBonus = criticalSteps * definition.criticalChancePerStreakStep
        val rateMultiplier = wordRush.rateOfFireMultiplier(timeSeconds)
        return WeaponRuntimeModifiers(critBonus, rateMultiplier)
    }

    fun status(streak: Int, timeSeconds: Double): CombatTypingBonusStatus {
        val criticalSteps = minOf(
            streak / definition.readingCharactersPerCriticalStep,
            definition.maximumCriticalStreakSteps
        )
        val criticalBonus = criticalSteps * definition.criticalChancePerStreakStep
        val maximumCriticalBonus = definition.maximumCriticalStreakSteps * definition.criticalChancePerStreakStep
        val rateMultiplier = wordRush.rateOfFireMultiplier(timeSeconds)
        val wordRushActive = rateMultiplier > 1f
        return CombatTypingBonusStatus(
            streakCharacters = streak,
            criticalChanceBonusPercent = (criticalBonus * 100f).roundToInt(),
            maximumCriticalChanceBonusPercent = (maximumCriticalBonus * 100f).roundToInt(),
            isWordRushActive = wordRushActive,
            wordRushStacks = if (wordRushActive) definition.wordRush.stacksToActivate else 0,
            wordRushProgress = wordRush.progressAt(timeSeconds),
            rateOfFireBonusPercent = (definition.wordRush.rateOfFireBonus * 100f).roundToInt()
        )
    }
}
