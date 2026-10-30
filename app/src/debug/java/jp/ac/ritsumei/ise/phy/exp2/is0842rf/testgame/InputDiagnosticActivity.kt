package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.os.Bundle
import android.widget.TextView
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.GameInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import kotlin.random.Random

/** Separate debug-only entry point so launcher intents cannot lose the diagnostic flag. */
class InputDiagnosticActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra("trace_input", true)
        super.onCreate(savedInstanceState)
        findViewById<TextView>(R.id.inputTitle).text =
            when {
                intent.getBooleanExtra("streak_board", false) -> "二枚抜き・ミス診断"
                intent.getBooleanExtra("nasal_board", false) -> "ん照合診断 N4"
                else -> "IME照合診断 R2"
            }
        if (intent.getBooleanExtra("capture_nasal", false)) {
            findViewById<TextView>(R.id.inputTitle).text = "ん入力記録 R3"
            findViewById<MySurfaceView>(R.id.mySurfaceView).showWords(
                listOf("hanpatu / hannpatu", "honyaku / honnyaku", "kann").map {
                    WordMatchState(WordDefinition(it, it), 0, false)
                }
            )
            findViewById<GameImeEditText>(R.id.imeInputHost).onGameInput = { edit ->
                findViewById<TextView>(R.id.receivedInputText).text = edit.sourceText
                false
            }
        }
    }

    override fun createGameInputController(): GameInputController {
        val targets = when {
            intent.getBooleanExtra("streak_board", false) ->
                listOf("ぱそこん", "そこ", "えいご", "いご", "ちゃわん", "わんがん", "さんご", "さんぽ")
            intent.getBooleanExtra("nasal_board", false) -> listOf("はんぱつ", "ほんやく", "かん")
            else -> listOf("しまの", "しっぱいの", "ねっとの")
        }
        val pool = (targets + "よびのことば").map { WordDefinition(it, it, it) }
        // Use the production controller and callback, with a repeatable diagnostic board.
        return (0..1000).asSequence().map { GameInputController(pool, activeWordCount = targets.size, random = Random(it)) }
            .first { controller -> controller.currentWords().map { it.definition.text }.toSet() == targets.toSet() }
    }
}
