package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.graphics.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters

/** Shared visual language for explicitly typable commands and loot, without tap activation. */
class TypingChipRenderer {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas:Canvas,state:WordMatchState,x:Float,baseline:Float,maxWidth:Float,size:Float,
             horizontalPadding:Float=8f,verticalPadding:Float=4f,alpha:Float=1f,cornerRadius:Float=4f) {
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=size;paint.textAlign=Paint.Align.LEFT
        val chars=splitTextCharacters(state.inputText)
        val natural=chars.sumOf { paint.measureText(it).toDouble() }.toFloat().coerceAtLeast(1f)
        if(natural>maxWidth-horizontalPadding*2) paint.textSize*=((maxWidth-horizontalPadding*2)/natural).coerceAtLeast(0.25f)
        val width=chars.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val metrics=paint.fontMetrics
        val bounds=RectF(x-width/2-horizontalPadding,baseline+metrics.ascent-verticalPadding,
            x+width/2+horizontalPadding,baseline+metrics.descent+verticalPadding)
        paint.style=Paint.Style.FILL;paint.color=Color.argb((255*alpha).toInt(),151,151,143)
        canvas.drawRoundRect(bounds,cornerRadius,cornerRadius,paint)
        var at=x-width/2
        chars.forEachIndexed { index,ch ->
            val color=if(index<state.matchedInputCharacters) Color.rgb(199,199,188) else Color.rgb(239,239,229)
            paint.color=color;paint.alpha=(255*alpha).toInt()
            canvas.drawText(ch,at,baseline,paint);at+=paint.measureText(ch)
        }
    }
}
