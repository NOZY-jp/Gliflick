package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model

/** Text actually observed from the keyboard; no missing key is manufactured. */
sealed interface InputToken {
    data class Stroke(val value: String) : InputToken

    /**
     * An IME replaced the observed Roman prefix with kana. This is an observation
     * constraint, not a reconstructed stream of Roman keys.
     */
    data class KanaObservation(
        val kana: String,
        val observedRomanPrefix: String,
        val followingRomanPrefix: String = ""
    ) : InputToken
}

