package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.CombatSession
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.CombatWorld
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.Enemy
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.EnemyMarkerPattern
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.HitFeedback
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
    private val enemyRed = Color.rgb(158, 91, 96)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val enemyMarkerPaths = EnemyMarkerPattern.entries.associateWith(::createEnemyMarkerPath)
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
            val alpha = (255f * (1f - (enemy.deathAge ?: 0f) / CombatWorld.DEATH_FADE_SECONDS)
                .coerceIn(0f, 1f)).toInt()
            paint.color = enemyRed
            paint.alpha = alpha
            drawEnemyBody(canvas, enemy)
            drawEnemyHealthBar(canvas, enemy, alpha, enemy.spawnOrigin.y < world.height / 2f)
        }
        world.hitFeedback.forEach { drawHitGeometry(canvas, it) }
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
        session.captions.forEach {
            drawCaption(canvas, it.state, it.anchor, it.alpha, 1f, width, it.placeBelowEnemy, it.size)
        }
        session.completions.forEach {
            val eased = 0.5f - cos(it.progress * PI.toFloat()) * 0.5f
            drawCaption(canvas, it.state, it.anchor, 1f - eased, 1f + eased, width, it.placeBelowEnemy, it.size)
        }
        world.hitFeedback.forEach { drawDamageNumber(canvas, it) }
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

    private fun drawEnemyHealthBar(canvas: Canvas, enemy: Enemy, alpha: Int, aboveEnemy: Boolean) {
        if (enemy.hp >= enemy.definition.maxHp) return
        val width = max(12f * enemy.size, enemy.radius * 2.6f) * 2f
        val left = enemy.position.x - width / 2f
        val barHeight = 1.5f * enemy.size
        val gap = 8f * enemy.size
        val top = if (aboveEnemy) enemy.position.y - enemy.radius - gap - barHeight
        else enemy.position.y + enemy.radius + gap
        paint.style = Paint.Style.FILL
        paint.alpha = alpha
        paint.color = Color.rgb(199, 199, 199)
        canvas.drawRect(left, top, left + width, top + barHeight, paint)
        paint.color = Color.rgb(117, 117, 117)
        val hpFraction = (enemy.hp.toFloat() / enemy.definition.maxHp).coerceIn(0f, 1f)
        canvas.drawRect(left, top, left + width * hpFraction, top + barHeight, paint)
        paint.alpha = 255
    }

    private fun drawEnemyBody(canvas: Canvas, enemy: Enemy) {
        val centerX = enemy.position.x
        val centerY = enemy.position.y
        val radius = enemy.radius
        paint.style = Paint.Style.FILL
        if (enemy.definition.shape == EnemyShape.CIRCLE) {
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(radius, radius)
            canvas.drawPath(enemyMarkerPaths.getValue(enemy.definition.markerPattern), paint)
            canvas.restore()
            return
        }
        val path = Path()
        repeat(enemy.definition.shape.sides) { index ->
            val angle = -PI / 2.0 + 2.0 * PI * index / enemy.definition.shape.sides
            val x = centerX + kotlin.math.cos(angle).toFloat() * radius
            val y = centerY + kotlin.math.sin(angle).toFloat() * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(radius * 0.32f, radius * 0.32f)
        canvas.drawPath(enemyMarkerPaths.getValue(enemy.definition.markerPattern), paint)
        canvas.restore()
    }

    private fun createEnemyMarkerPath(pattern: EnemyMarkerPattern): Path {
        val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        fun circle(radius: Float) = path.addCircle(0f, 0f, radius, Path.Direction.CW)
        fun notchedDisk(radius: Float, centerHole: Float, notchOrbit: Float, notchWidth: Float,
                        notchHeight: Float, notchCount: Int = 6) {
            circle(radius)
            circle(centerHole)
            val notch = Path().apply {
                addRoundRect(RectF(-notchWidth / 2f, -notchOrbit - notchHeight / 2f,
                    notchWidth / 2f, -notchOrbit + notchHeight / 2f),
                    notchHeight / 2f, notchHeight / 2f, Path.Direction.CW)
            }
            repeat(notchCount) { index ->
                val rotated = Path()
                val rotation = Matrix().apply { setRotate(index * 360f / notchCount) }
                notch.transform(rotation, rotated)
                path.addPath(rotated)
            }
        }

        when (pattern) {
            EnemyMarkerPattern.DOT -> circle(1f)
            EnemyMarkerPattern.RINGED_DOT -> {
                circle(1f); circle(0.82f); circle(0.31f)
            }
            EnemyMarkerPattern.DOUBLE_RINGED_DOT -> {
                circle(1f); circle(0.86f); circle(0.70f); circle(0.56f); circle(0.30f)
            }
            EnemyMarkerPattern.BULLSEYE -> {
                circle(1f); circle(0.62f); circle(0.31f)
            }
            EnemyMarkerPattern.CENTER_HOLE -> {
                circle(1f); circle(0.15f)
            }
            EnemyMarkerPattern.SEGMENTED_CENTER_HOLE -> notchedDisk(
                radius = 1f, centerHole = 0.16f, notchOrbit = 0.67f,
                notchWidth = 0.25f, notchHeight = 0.085f
            )
            EnemyMarkerPattern.RINGED_SEGMENTED_CENTER_HOLE -> {
                circle(1f); circle(0.93f); circle(0.82f); circle(0.74f)
                notchedDisk(radius = 0.62f, centerHole = 0.16f, notchOrbit = 0.42f,
                    notchWidth = 0.17f, notchHeight = 0.06f)
            }
        }
        return path
    }

    private fun drawHitGeometry(canvas: Canvas, feedback: HitFeedback) {
        if (feedback.ringStrokeWidth > 0.01f) {
            paint.color = red
            paint.alpha = 230
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = feedback.ringStrokeWidth
            canvas.drawCircle(feedback.origin.x, feedback.origin.y, feedback.ringRadius, paint)
            paint.style = Paint.Style.FILL
        }

        feedback.fragments.forEach { fragment ->
            val length = fragment.lengthAt(feedback.age)
            if (length > 0.05f) {
                val center = fragment.positionAt(feedback.origin, feedback.age)
                paint.color = muted
                paint.alpha = 255
                paint.style = Paint.Style.FILL
                canvas.save()
                canvas.rotate(fragment.rotation * 180f / PI.toFloat(), center.x, center.y)
                canvas.drawRect(center.x - length / 2f, center.y - fragment.thickness / 2f,
                    center.x + length / 2f, center.y + fragment.thickness / 2f, paint)
                canvas.restore()
            }
        }

        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawDamageNumber(canvas: Canvas, feedback: HitFeedback) {
        if (feedback.numberAlpha > 0f) {
            val x = feedback.origin.x
            val baseline = feedback.numberBaseline
            paint.color = muted
            paint.alpha = (255f * feedback.numberAlpha).toInt().coerceIn(0, 255)
            paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 16.5f
            canvas.save()
            canvas.scale(feedback.numberScale, feedback.numberScale, x, baseline)
            canvas.drawText(feedback.damage.toString(), x, baseline, paint)
            canvas.restore()
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
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
                            scale: Float, arenaWidth: Float, placeBelowEnemy: Boolean, enemySize: Float) {
        if (alpha <= 0f) return
        paint.typeface = light
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 17f * enemySize
        val maxWidth = min(210f * enemySize, arenaWidth - 20f)
        val naturalWidth = paint.measureText(state.inputText).coerceAtLeast(1f)
        if (naturalWidth > maxWidth) paint.textSize *= maxWidth / naturalWidth
        val wordTextSize = paint.textSize
        val textWidth = paint.measureText(state.inputText)
        val centerX = anchor.x
        paint.textSize = 10.5f * enemySize
        val labelWidth = paint.measureText(state.definition.displayName).coerceAtLeast(1f)
        if (labelWidth > maxWidth) paint.textSize *= maxWidth / labelWidth
        val centerY = anchor.y + (if (placeBelowEnemy) 34f else -22f) * enemySize
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        paint.textSize = wordTextSize
        paint.textAlign = Paint.Align.LEFT
        val baseline = centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        var x = centerX - textWidth / 2f
        splitTextCharacters(state.inputText).forEachIndexed { index, character ->
            paint.color = if (state.isCompleted || index < state.matchedInputCharacters) ink else idle
            paint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            canvas.drawText(character, x, baseline, paint)
            x += paint.measureText(character)
        }
        paint.textSize = 10.5f * enemySize
        val fittedLabelWidth = paint.measureText(state.definition.displayName).coerceAtLeast(1f)
        if (fittedLabelWidth > maxWidth) paint.textSize *= maxWidth / fittedLabelWidth
        paint.textAlign = Paint.Align.CENTER
        paint.color = muted
        paint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawText(state.definition.displayName, centerX, centerY - 16f * enemySize, paint)
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
