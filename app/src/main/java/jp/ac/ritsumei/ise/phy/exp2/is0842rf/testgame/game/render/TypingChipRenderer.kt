package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.graphics.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters

/** Shared visual language for explicitly typable commands and loot, without tap activation. */
class TypingChipRenderer {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas:Canvas,state:WordMatchState,x:Float,baseline:Float,maxWidth:Float,size:Float,
             horizontalPadding:Float=8f,verticalPadding:Float=4f,alpha:Float=1f) {
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=size;paint.textAlign=Paint.Align.LEFT
        val chars=splitTextCharacters(state.inputText)
        val natural=chars.sumOf { paint.measureText(it).toDouble() }.toFloat().coerceAtLeast(1f)
        if(natural>maxWidth-horizontalPadding*2) paint.textSize*=((maxWidth-horizontalPadding*2)/natural).coerceAtLeast(0.25f)
        val width=chars.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val metrics=paint.fontMetrics
        val bounds=RectF(x-width/2-horizontalPadding,baseline+metrics.ascent-verticalPadding,
            x+width/2+horizontalPadding,baseline+metrics.descent+verticalPadding)
        paint.style=Paint.Style.FILL;paint.color=Color.argb((255*alpha).toInt(),151,151,143)
        canvas.drawRoundRect(bounds,4f,4f,paint)
        var at=x-width/2
        chars.forEachIndexed { index,ch ->
            paint.color=Color.argb(((if(index<state.matchedInputCharacters)255 else 225)*alpha).toInt(),239,239,229)
            canvas.drawText(ch,at,baseline,paint);at+=paint.measureText(ch)
        }
        if(state.matchedInputCharacters>0) {
            val done=chars.take(state.matchedInputCharacters).sumOf { paint.measureText(it).toDouble() }.toFloat()
            paint.color=Color.argb((220*alpha).toInt(),239,239,229)
            canvas.drawRect(x-width/2,bounds.bottom-2.5f,x-width/2+done,bounds.bottom-1.5f,paint)
        }
    }
}
