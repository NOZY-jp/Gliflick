package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.math.exp
import kotlin.math.floor
import kotlin.random.Random

/** Selects a reading length independently from choosing a word within that length group. */
fun interface WordLengthPolicy {
    fun chooseLength(elapsedSeconds: Double, availableLengths: Set<Int>, random: Random): Int
}

/** Tunable timeline and shape for the endless mode's discrete normal distribution. */
data class WordLengthSchedule(
    val minimumLength: Int = 2,
    val initialMaximumLength: Int = 3,
    val finalMaximumLength: Int = 7,
    val unlockIntervalSeconds: Double = 4.0 * 60.0,
    val initialMean: Double = 2.0,
    val meanStep: Double = 0.5,
    val meanStepSeconds: Double = 2.0 * 60.0,
    val maximumMean: Double = 6.0,
    val standardDeviation: Double = 1.0
) {
    init {
        require(minimumLength > 0)
        require(initialMaximumLength >= minimumLength)
        require(finalMaximumLength >= initialMaximumLength)
        require(unlockIntervalSeconds > 0.0 && unlockIntervalSeconds.isFinite())
        require(initialMean.isFinite() && meanStep >= 0.0 && meanStep.isFinite())
        require(meanStepSeconds > 0.0 && meanStepSeconds.isFinite())
        require(maximumMean >= initialMean && maximumMean.isFinite())
        require(standardDeviation > 0.0 && standardDeviation.isFinite())
    }
}

/**
 * Adds one available reading length to the pool every four minutes and shifts the Gaussian
 * centre by 0.5 every two minutes. Probabilities are normalized over unlocked lengths that
 * currently have words, so a missing corpus group cannot block a spawn.
 */
class ProgressiveGaussianWordLengthPolicy(
    val schedule: WordLengthSchedule = WordLengthSchedule()
) : WordLengthPolicy {
    fun maximumLengthAt(elapsedSeconds: Double): Int {
        val elapsed = elapsedSeconds.coerceAtLeast(0.0)
        val unlocked = schedule.initialMaximumLength + floor(elapsed / schedule.unlockIntervalSeconds).toInt()
        return minOf(unlocked, schedule.finalMaximumLength)
    }

    fun meanAt(elapsedSeconds: Double): Double {
        val elapsed = elapsedSeconds.coerceAtLeast(0.0)
        val steps = floor(elapsed / schedule.meanStepSeconds)
        return minOf(schedule.initialMean + steps * schedule.meanStep, schedule.maximumMean)
    }

    fun probabilitiesAt(elapsedSeconds: Double, availableLengths: Set<Int>): Map<Int, Double> {
        val maximumLength = maximumLengthAt(elapsedSeconds)
        val eligible = availableLengths
            .asSequence()
            .filter { it in schedule.minimumLength..maximumLength }
            .sorted()
            .toList()
        if (eligible.isEmpty()) return emptyMap()

        val mean = meanAt(elapsedSeconds)
        val varianceScale = 2.0 * schedule.standardDeviation * schedule.standardDeviation
        val unnormalized = eligible.associateWith { length ->
            exp(-((length - mean) * (length - mean)) / varianceScale)
        }
        val total = unnormalized.values.sum()
        return unnormalized.mapValues { (_, weight) -> weight / total }
    }

    override fun chooseLength(elapsedSeconds: Double, availableLengths: Set<Int>, random: Random): Int {
        val probabilities = probabilitiesAt(elapsedSeconds, availableLengths)
        check(probabilities.isNotEmpty()) {
            "No available word length is unlocked at $elapsedSeconds seconds."
        }

        var draw = random.nextDouble()
        probabilities.forEach { (length, probability) ->
            draw -= probability
            if (draw <= 0.0) return length
        }
        return probabilities.keys.last()
    }
}
