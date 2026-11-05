package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import kotlin.math.*

/** Presents simulated boss state; rendering never mutates words, health or attack clocks. */
class BossRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons = WeaponIconRenderer(context)
    private val chips = TypingChipRenderer()
    private val gray = Color.rgb(117,117,110)
    private val muted = Color.rgb(151,151,143)
    private val red = Color.rgb(158,91,96)
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = gray) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.typeface = Typeface.create("sans-serif",Typeface.NORMAL)
        paint.textSize = size; paint.textAlign = Paint.Align.CENTER; canvas.drawText(value,x,y,paint)
    }
    fun drawField(canvas: Canvas, world: CombatWorld) {
        val bosses = world.bosses
        if(bosses.activeKind == BossKind.CYPHER) bosses.enemies.filter { it.isAlive }.forEach {
            paint.color = Color.argb(85,117,117,110); paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.65f
            canvas.drawCircle(it.position.x,it.position.y,bosses.auraRadius(world),paint)
            val frame = (world.elapsedSeconds * 15).toInt()
            val glyphs = "aBcdEFgHijkLMnOpQrSTuvWxYz!?"
            repeat(12) { index ->
                val angle = index * 2.39996f + frame * 0.15f
                val r = 6f + ((index * 13 + frame * 7) % 15)
                val p = it.position + Vec2(cos(angle),sin(angle)) * r
                text(canvas,glyphs[(index*7 + frame*3) % glyphs.length].toString(),p.x,p.y,9f,
                    Color.argb(80 + (index*17 + frame*29) % 140,158,91,96))
            }
        }
        bosses.tendril?.let { attack ->
            val retreat=(attack.retreatAge?.div(0.24f) ?: 0f).coerceIn(0f,1f)
            if(attack.finished && attack.retreatAge==null) return@let
            val attackTip=world.player.position+Vec2(0f,-90f*(1f-attack.progress))
            bosses.enemies.filter { it.isAlive }.forEach { enemy ->
                val ray=(attackTip-enemy.position).normalized()
                val edgeDistance=enemy.radius*0.97f/max(abs(ray.x),abs(ray.y)).coerceAtLeast(0.01f)
                val start=enemy.position+ray*edgeDistance
                val tip=attackTip+(start-attackTip)*retreat
                val normal=Vec2(-(tip-start).y,(tip-start).x).normalized()
                val middle=start+(tip-start)*0.5f+normal*(sin(attack.age*3f)*3f*(1f-retreat))
                val rootWidth=2.6f*(1f-retreat)
                val path=Path().apply {
                    val edge=start+normal*rootWidth;moveTo(edge.x,edge.y)
                    quadTo(middle.x+normal.x*1.2f,middle.y+normal.y*1.2f,tip.x,tip.y)
                    quadTo(middle.x-normal.x*1.2f,middle.y-normal.y*1.2f,start.x-normal.x*rootWidth,start.y-normal.y*rootWidth);close()
                }
                paint.color=Color.argb((225*(1f-retreat)).toInt(),158,91,96);paint.style=Paint.Style.FILL
                canvas.drawPath(path,paint)
            }
        }
        bosses.orbs.forEach { orb ->
            paint.color = Color.argb(170,117,117,110);paint.style = Paint.Style.FILL
            canvas.drawCircle(orb.position.x,orb.position.y,3f,paint)
            text(canvas,"²",orb.position.x+2f,orb.position.y-2f,8f)
        }
        paint.style = Paint.Style.FILL
    }
    fun drawHud(canvas: Canvas, session: CombatSession, width: Float, height: Float) {
        val world=session.world;val bosses=world.bosses
        bosses.tendril?.takeUnless { it.finished }?.let { attack ->
            val tip=world.player.position+Vec2(0f,-90f*(1f-attack.progress))
            paint.color=gray;paint.style=Paint.Style.FILL;canvas.drawCircle(tip.x,tip.y,10f,paint)
            text(canvas,session.tendrilState?.inputText ?: attack.glyph,tip.x,tip.y+4f,12f,Color.rgb(245,245,236))

        }
        session.poemState?.let { state ->
            val top = (bosses.enemies.firstOrNull()?.position?.y ?: 101f) + 29f
            paint.color=Color.argb(235,232,232,222);paint.style=Paint.Style.FILL
            canvas.drawRect(15f,top,width-15f,top+67f,paint)
            text(canvas,state.definition.displayName,width/2f,top+15f,10.5f)
            prompt(canvas,state,width/2f,top+39f,width-48f,16f)
            val progress = (bosses.poemIndex + state.matchedCharacters.toFloat()/state.definition.readingLength) / PoetPassage.phrases.size
            text(canvas,"${(progress*100).toInt()}%  吾輩は猫である",width/2f,top+57f,8.5f,muted)
            paint.color=gray;canvas.drawRect(15f,top+65f,15f+(width-30f)*progress,top+67f,paint)
        }
        icons.drawReroll(canvas,width-29f,height-18f,5f,muted)
        text(canvas,session.rerollsRemaining.toString(),width-15f,height-15f,8f,muted)
    }
    private fun prompt(canvas: Canvas, state: WordMatchState, x: Float, baseline: Float, width: Float, size: Float) {
        paint.textSize=size;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textAlign=Paint.Align.LEFT
        val chars=splitTextCharacters(state.inputText)
        val total=chars.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val done=chars.take(state.matchedInputCharacters).sumOf { paint.measureText(it).toDouble() }.toFloat()
        val left=if(total<=width)x-total/2f else x-width/2f-(done-width*0.5f).coerceIn(0f,(total-width).coerceAtLeast(0f))
        canvas.save();canvas.clipRect(x-width/2f,baseline-size-2f,x+width/2f,baseline+5f)
        var at=left
        chars.forEachIndexed { i,ch -> paint.color=if(i<state.matchedInputCharacters) Color.rgb(70,70,70) else muted;canvas.drawText(ch,at,baseline,paint);at+=paint.measureText(ch) }
        canvas.restore()
    }
    fun drawIntroduction(canvas: Canvas, session: CombatSession, width: Float, height: Float) {
        val kind=session.world.bosses.introduction ?: return
        paint.color=Color.argb(248,239,239,229);paint.style=Paint.Style.FILL;canvas.drawRect(0f,0f,width,height,paint)
        val top=(height/2f-112f).coerceAtLeast(25f)
        text(canvas,"BOSS",width/2f,top,10f,muted)
        text(canvas,kind.title,width/2f,top+36f,25f)
        var y=top+76f
        kind.description.forEach { sentence ->
            paint.textSize=12f
            var line=""
            sentence.forEach { ch ->
                if(paint.measureText(line+ch)>width-58f) { text(canvas,line,width/2f,y,12f);y+=19f;line="" }
                line+=ch
            }
            text(canvas,line,width/2f,y,12f);y+=24f
        }
        session.bossIntroState?.let { chips.draw(canvas,it,width/2f,(y+31f).coerceAtMost(height-25f),width-48f,25f,16f,7f) }
    }
}
