package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.data

import android.content.Context
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition

/** Loads the editable word list from res/raw/word_list.txt. */
class WordListRepository(context: Context) {
    private val applicationContext = context.applicationContext

    fun load(): List<WordDefinition> = applicationContext.resources
        .openRawResource(R.raw.word_list)
        .bufferedReader(Charsets.UTF_8)
        .useLines { lines ->
            lines.mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() } }
                .mapIndexed { index, word ->
                    val columns = word.split('\t')
                    WordDefinition(
                        id = "word-${index + 1}",
                        text = columns.getOrElse(1) { columns[0] },
                        displayName = columns[0],
                        languageTag = columns.getOrElse(2) { "ja" }
                    )
                }
                .toList()
        }
}
