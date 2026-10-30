package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.CombatSession
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.CombatWorld
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.Vec2
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.EnemyShape
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Presentation only: no matching, damage, spawning, or weapon timing. Coordinates are logical dp. */
class CombatRenderer(context: Context) {
    private val gridBackground = GridBackgroundRenderer(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Color.rgb(70, 70, 70)
    private val idle = Color.rgb(184, 184, 184)
    private val muted = Color.rgb(151, 151, 143)
    private val red = Color.rgb(170, 91, 100)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private var background: Shader? = null
    private var backgroundWidth = 0f
    private var backgroundHeight = 0f

    fun draw(canvas: Canvas, session: CombatSession, width: Float, height: Float) {
        val world = session.world
        if (width != backgroundWidth || height != backgroundHeight) {
            backgroundWidth = width; backgroundHeight = height
            background = RadialGradient(width / 2f, height * 0.5f, max(width, height) * 0.8f,
                intArrayOf(Color.rgb(245, 245, 236), Color.rgb(230, 230, 220)), null, Shader.TileMode.CLAMP)
        }
        paint.shader = background
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        gridBackground.draw(canvas, width)

        world.deathBursts.forEach { burst -> burst.particles.forEach { particle ->
            val size = particle.sizeAt(burst.age)
            if (size > 0f) {
                val point = burst.origin + particle.velocity * burst.age
                paint.color = red
                paint.alpha = (255f * (1f - burst.age / particle.lifetime).coerceIn(0f, 1f)).toInt()
                canvas.save()
                canvas.rotate(particle.angle * 180f / PI.toFloat() + burst.age * 90f, point.x, point.y)
                canvas.drawRect(point.x - size / 2, point.y - size / 2, point.x + size / 2, point.y + size / 2, paint)
                canvas.restore()
            }
        } }
        world.enemies.forEach { enemy ->
            paint.color = red
            paint.alpha = (255f * (1f - (enemy.deathAge ?: 0f) / CombatWorld.DEATH_FADE_SECONDS)
                .coerceIn(0f, 1f)).toInt()
            when (enemy.definition.shape) {
                EnemyShape.CIRCLE -> canvas.drawCircle(enemy.position.x, enemy.position.y, enemy.definition.radius, paint)
            }
        }
        world.projectiles.forEach { bullet ->
            val tail = bullet.position - bullet.velocity.normalized() * min(30f, bullet.age * bullet.velocity.length())
            if ((bullet.position - tail).length() > 0.001f) {
                paint.shader = LinearGradient(tail.x, tail.y, bullet.position.x, bullet.position.y,
                    Color.TRANSPARENT, Color.argb(180, 70, 70, 70), Shader.TileMode.CLAMP)
                paint.alpha = 255
                paint.strokeWidth = 0.9f
                canvas.drawLine(tail.x, tail.y, bullet.position.x, bullet.position.y, paint)
                paint.shader = null
            }
            paint.color = ink
            canvas.drawCircle(bullet.position.x, bullet.position.y, bullet.radius, paint)
        }
        drawPlayer(canvas, world)
        session.captions.forEach { drawCaption(canvas, it.state, it.anchor, it.alpha, 1f, width) }
        session.completions.forEach {
            val eased = 0.5f - cos(it.progress * PI.toFloat()) * 0.5f
            drawCaption(canvas, it.state, it.anchor, 1f - eased, 1f + eased, width)
        }
        drawHud(canvas, world, width)
        if (world.isGameOver) {
            paint.color = Color.argb(200, 239, 239, 229)
            canvas.drawRect(0f, 0f, width, height, paint)
            centered(canvas, "GAME OVER", width / 2f, height / 2f - 12f, 27f, ink)
            centered(canvas, "RETRY", width / 2f, height / 2f + 27f, 17f, ink)
        }
    }

    private fun drawPlayer(canvas: Canvas, world: CombatWorld) {
        val p = world.player.position
        paint.color = Color.rgb(117, 117, 110)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawCircle(p.x, p.y, world.player.radius, paint)
        paint.color = Color.rgb(199, 199, 188)
        paint.strokeWidth = 0.85f
        val r = world.player.radius - 3.8f
        canvas.drawCircle(p.x, p.y, r, paint)
        paint.color = ink
        canvas.drawArc(p.x - r, p.y - r, p.x + r, p.y + r, -90f,
            360f * world.experienceFraction, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(199, 199, 188)
        canvas.drawRect(p.x - 21f, p.y + 29f, p.x + 21f, p.y + 32f, paint)
        paint.color = Color.rgb(117, 117, 110)
        canvas.drawRect(p.x - 21f, p.y + 29f, p.x - 21f + 42f * world.player.hp / world.player.maxHp,
            p.y + 32f, paint)
        centered(canvas, "${world.player.hp} / ${world.player.maxHp}", p.x, p.y + 46f, 10f, muted)
    }

    private fun drawHud(canvas: Canvas, world: CombatWorld, width: Float) {
        val centiseconds = (world.elapsedSeconds * 100).toLong()
        val timer = String.format(Locale.US, "%02d:%02d:%02d", centiseconds / 6000,
            centiseconds / 100 % 60, centiseconds % 100)
        centered(canvas, timer, width / 2f, 32f, 27f, Color.rgb(117, 117, 110),
            Typeface.create("sans-serif-condensed", Typeface.BOLD))
        centered(canvas, "レベル ${world.level} ${world.mainWeapon.displayName}", width / 2f, 53f, 12f, muted)
        paint.color = Color.rgb(199, 199, 188)
        canvas.drawRect(width / 2f - 48f, 64f, width / 2f + 48f, 68f, paint)
        paint.color = Color.rgb(117, 117, 110)
        canvas.drawRect(width / 2f - 48f, 64f, width / 2f - 48f + 96f * world.experienceFraction, 68f, paint)
    }

    private fun drawCaption(canvas: Canvas, state: WordMatchState, anchor: Vec2, alpha: Float,
                            scale: Float, arenaWidth: Float) {
        if (alpha <= 0f) return
        paint.typeface = light
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 17f
        val maxWidth = min(210f, arenaWidth - 20f)
        val naturalWidth = paint.measureText(state.inputText).coerceAtLeast(1f)
        if (naturalWidth > maxWidth) paint.textSize *= maxWidth / naturalWidth
        val textWidth = paint.measureText(state.inputText)
        val centerX = anchor.x.coerceIn(textWidth / 2f + 8f, arenaWidth - textWidth / 2f - 8f)
        val centerY = anchor.y - 22f
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        val baseline = centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        var x = centerX - textWidth / 2f
        splitTextCharacters(state.inputText).forEachIndexed { index, character ->
            paint.color = if (state.isCompleted || index < state.matchedInputCharacters) ink else idle
            paint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            canvas.drawText(character, x, baseline, paint)
            x += paint.measureText(character)
        }
        paint.textSize = 10.5f
        val labelWidth = paint.measureText(state.definition.displayName).coerceAtLeast(1f)
        if (labelWidth > maxWidth) paint.textSize *= maxWidth / labelWidth
        paint.textAlign = Paint.Align.CENTER
        paint.color = muted
        paint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawText(state.definition.displayName, centerX, centerY - 16f, paint)
        canvas.restore()
        paint.alpha = 255
    }

    private fun centered(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int,
                         face: Typeface = light) {
        paint.typeface = face
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawText(text, x, y, paint)
    }
}
