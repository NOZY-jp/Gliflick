package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import kotlin.math.min

/** Formal spelling above, current input spelling below. Gameplay owns all state. */
class WordListCanvasRenderer(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(117, 117, 117)
        textAlign = Paint.Align.CENTER
    }

    fun draw(canvas: Canvas, words: List<WordMatchState>) {
        canvas.drawColor(Color.WHITE)
        if (words.isEmpty() || canvas.width <= 0 || canvas.height <= 0) return
        val columns = if (words.size > 4) 2 else 1
        val rows = (words.size + columns - 1) / columns
        val rowHeight = canvas.height.toFloat() / rows
        val columnWidth = canvas.width.toFloat() / columns
        val availableWidth = (columnWidth - 24f * density).coerceAtLeast(1f)

        words.forEachIndexed { index, word ->
            val row = index / columns
            val centerX = columnWidth * (index % columns + 0.5f)
            val characters = splitTextCharacters(word.inputText)
            paint.textSize = min(24f * density, rowHeight * 0.34f)
            var width = characters.sumOf { paint.measureText(it).toDouble() }.toFloat()
            if (width > availableWidth) {
                paint.textSize *= availableWidth / width
                width = availableWidth
            }
            labelPaint.textSize = min(12f * density, rowHeight * 0.16f)
            val labelWidth = labelPaint.measureText(word.definition.displayName)
            if (labelWidth > availableWidth) labelPaint.textSize *= availableWidth / labelWidth
            val promptMetrics = paint.fontMetrics
            val labelMetrics = labelPaint.fontMetrics
            val gap = 4f * density
            val blockHeight = labelMetrics.descent - labelMetrics.ascent + gap +
                promptMetrics.descent - promptMetrics.ascent
            val top = rowHeight * (row + 0.5f) - blockHeight / 2f
            val labelBaseline = top - labelMetrics.ascent
            val promptBaseline = labelBaseline + labelMetrics.descent + gap - promptMetrics.ascent
            canvas.drawText(word.definition.displayName, centerX, labelBaseline, labelPaint)

            var x = centerX - width / 2f
            characters.forEachIndexed { index, character ->
                paint.color = when {
                    word.isCompleted -> Color.RED
                    index < word.matchedInputCharacters -> Color.YELLOW
                    else -> Color.BLACK
                }
                canvas.drawText(character, x, promptBaseline, paint)
                x += paint.measureText(character)
            }
        }
    }
}
