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
    private val enemyRenderer = EnemyRenderer(context)
    private val secondaryRenderer = SecondaryWeaponRenderer(context)
    private val upgradeRenderer = UpgradeRenderer(context)
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
        secondaryRenderer.draw(canvas, world)

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
        enemyRenderer.drawProtection(canvas, world)
        world.enemies.forEach { enemy ->
            val alpha = (255f * (1f - (enemy.deathAge ?: 0f) / CombatWorld.DEATH_FADE_SECONDS)
                .coerceIn(0f, 1f)).toInt()
            paint.color = enemyRed
            paint.alpha = alpha
            drawEnemyBody(canvas, enemy)
            drawEnemyHealthBar(canvas, enemy, alpha, enemy.spawnOrigin.y < world.height / 2f)
        }
        enemyRenderer.drawBarriers(canvas, world)
        world.hitFeedback.forEach { drawHitGeometry(canvas, it) }
        world.projectiles.forEach { bullet ->
            if (secondaryRenderer.drawProjectile(canvas, bullet)) return@forEach
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
        drawCaptions(canvas, session, width, height)
        drawPendingRewardPrompt(canvas, session)
        world.hitFeedback.forEach { drawDamageNumber(canvas, it) }
        drawHud(canvas, world, width)
        drawTypingBonuses(canvas, session, width)
        secondaryRenderer.drawArsenal(canvas, world, width, height)
        upgradeRenderer.draw(canvas, session, width, height)
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

    private fun drawPendingRewardPrompt(canvas: Canvas, session: CombatSession) {
        if (session.upgradeSelection != null || session.pendingChoiceCount == 0) return
        val levels = session.world.pendingLevelUps
        val evolutions = session.pendingEvolutionChoices
        val label = when {
            levels > 0 && evolutions > 0 -> "強化選択"
            levels > 0 -> "レベルアップ"
            else -> "武器進化"
        }
        val player = session.world.player
        val baseline = player.position.y - player.radius - 13f
        val labelSize = 9f
        val keySize = 8.5f
        val keyWidth = 17f
        val keyHeight = 13f
        val count = "×${session.pendingChoiceCount}"
        paint.typeface = light
        paint.textSize = labelSize
        val labelWidth = paint.measureText(label)
        paint.textSize = labelSize
        val countWidth = paint.measureText(count)
        val totalWidth = labelWidth + 4f + keyWidth + 4f + countWidth
        var x = player.position.x - totalWidth / 2f
        paint.color = muted
        paint.textSize = labelSize
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x, baseline, paint)
        x += labelWidth + 4f
        val top = baseline - keyHeight + 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(247, 247, 239)
        canvas.drawRoundRect(x, top, x + keyWidth, top + keyHeight, 2.5f, 2.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        paint.color = Color.rgb(151, 151, 143)
        canvas.drawRoundRect(x, top, x + keyWidth, top + keyHeight, 2.5f, 2.5f, paint)
        paint.style = Paint.Style.FILL
        paint.color = ink
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = keySize
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("␣", x + keyWidth / 2f, top + 9.4f, paint)
        x += keyWidth + 4f
        paint.typeface = light
        paint.textSize = labelSize
        paint.textAlign = Paint.Align.LEFT
        paint.color = muted
        canvas.drawText(count, x, baseline, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEnemyHealthBar(canvas: Canvas, enemy: Enemy, alpha: Int, aboveEnemy: Boolean) {
        val parts = enemy.splitterHealth?.partHealth
        if (enemy.hp >= enemy.definition.maxHp && enemy.shield >= enemy.maxShield) return
        val fractions = if (parts != null) parts.map { it.toFloat() / enemy.definition.splitter!!.partHp }
            else if (enemy.maxShield > 0) listOf(enemy.shield / enemy.maxShield, enemy.hp.toFloat() / enemy.definition.maxHp)
            else listOf(enemy.hp.toFloat() / enemy.definition.maxHp)
        val width = 24f
        val left = enemy.position.x - width / 2f
        val blockHeight = fractions.size * 3.5f - 2f
        val top = if (aboveEnemy) enemy.position.y - enemy.radius - 8f - blockHeight
            else enemy.position.y + enemy.radius + 8f
        paint.style = Paint.Style.FILL
        fractions.forEachIndexed { row, fraction ->
            val y = top + row * 3.5f
            paint.color = Color.rgb(199, 199, 199); paint.alpha = alpha
            canvas.drawRect(left, y, left + width, y + 1.5f, paint)
            paint.color = Color.rgb(117, 117, 117); paint.alpha = alpha
            canvas.drawRect(left, y, left + width * fraction.coerceIn(0f, 1f), y + 1.5f, paint)
        }
        paint.alpha = 255
    }

    private fun drawEnemyBody(canvas: Canvas, enemy: Enemy) {
        if (enemyRenderer.drawBody(canvas, enemy, paint.color)) return
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
                circle(1f); circle(0.94f); circle(0.5f)
            }
            EnemyMarkerPattern.DOUBLE_RINGED_DOT -> {
                circle(1f); circle(0.96f)
                circle(2f / 2.83f); circle(0.67f)
                circle(1f / 2.83f)
            }
            EnemyMarkerPattern.BULLSEYE -> {
                circle(1f); circle(2f / 2.83f); circle(1f / 2.83f)
            }
            EnemyMarkerPattern.CENTER_HOLE -> {
                circle(1f); circle(1f / 2.83f)
            }
            EnemyMarkerPattern.SEGMENTED_CENTER_HOLE -> notchedDisk(
                radius = 1f, centerHole = 1f / 2.83f, notchOrbit = 0.67f,
                notchWidth = 0.25f, notchHeight = 0.085f
            )
            EnemyMarkerPattern.RINGED_SEGMENTED_CENTER_HOLE -> {
                circle(1f); circle(0.93f); circle(0.82f); circle(0.74f)
                notchedDisk(radius = 0.62f, centerHole = 1f / 2.83f, notchOrbit = 0.42f,
                    notchWidth = 0.17f, notchHeight = 0.06f)
            }
        }
        return path
    }

    private fun drawHitGeometry(canvas: Canvas, feedback: HitFeedback) {
        if (feedback.ringStrokeWidth > 0.01f) {
            paint.color = muted
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

    private fun drawTypingBonuses(canvas: Canvas, session: CombatSession, width: Float) {
        val status = session.typingBonusStatus
        val showStreak = status.streakCharacters > 20
        val showWordRush = status.isWordRushActive
        if (!showStreak && !showWordRush) return

        val centerX = session.world.player.position.x
        val firstBaseline = session.world.player.position.y + 62f
        val rowGap = 17f
        val barWidth = min(38f, (width - 24f).coerceAtLeast(12f))
        var row = 0

        if (showStreak) {
            centered(canvas, "正射 ×${status.streakCharacters} Crit率 +${status.criticalChanceBonusPercent}%",
                centerX, firstBaseline, 8.5f, muted)
            drawBuffMeter(canvas, centerX, firstBaseline + 4f, barWidth,
                status.criticalChanceBonusPercent.toFloat() /
                    status.maximumCriticalChanceBonusPercent.coerceAtLeast(1))
            row++
        }

        if (showWordRush) {
            val baseline = firstBaseline + row * rowGap
            centered(canvas, "速射 ×${status.wordRushStacks}  連射速度 +${status.rateOfFireBonusPercent}%",
                centerX, baseline, 8.5f, ink)
            drawBuffMeter(canvas, centerX, baseline + 4f, barWidth, status.wordRushProgress)
        }
    }

    private fun drawBuffMeter(canvas: Canvas, centerX: Float, top: Float, width: Float, progress: Float) {
        val left = centerX - width / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(199, 199, 188)
        canvas.drawRect(left, top, left + width, top + 1.25f, paint)
        paint.color = Color.rgb(117, 117, 110)
        canvas.drawRect(left, top, left + width * progress.coerceIn(0f, 1f), top + 1.25f, paint)
    }

    private data class CaptionGeometry(val centerX: Float, val centerY: Float,
        val wordSize: Float, val labelSize: Float, val wordWidth: Float, val wordBaseline: Float,
        val scale: Float, val boxes: List<CaptionTextBox>)
    private data class CaptionDraw(val state: WordMatchState, val alpha: Float, val geometry: CaptionGeometry)

    private fun drawCaptions(canvas: Canvas, session: CombatSession, width: Float, height: Float) {
        val layers = mutableListOf<CaptionLayer<CaptionDraw>>()
        fun add(state: WordMatchState, anchor: Vec2, alpha: Float, scale: Float,
                below: Boolean, radius: Float, owner: Long, enemy: Enemy?) {
            if (alpha <= 0f) return
            val geometry = captionGeometry(state, anchor, scale, width, height, below, radius)
            val priority = CaptionPriority(!state.isCompleted && state.matchedInputCharacters > 0,
                enemy?.let { (it.definition.maxHp - it.hp).toDouble() / it.definition.maxHp } ?: 0.0,
                enemy?.definition?.tier ?: 0, enemy?.id ?: if (owner > 0) owner else -owner)
            layers += CaptionLayer(owner, priority, geometry.boxes, CaptionDraw(state, alpha, geometry))
        }
        session.captions.forEach {
            add(it.state, it.anchor, it.alpha, 1f, it.placeBelowEnemy, it.radius, it.enemyId, it.enemy)
        }
        session.completions.forEach {
            val eased = 0.5f - cos(it.progress * PI.toFloat()) * 0.5f
            add(it.state, it.anchor, 1f - eased, 1f + eased, it.placeBelowEnemy, it.radius,
                it.enemy?.id ?: Long.MAX_VALUE, it.enemy)
        }
        session.pickupCaptions.forEach {
            add(it.state, it.pickup.position, 1f, 1f, false, 9f, -it.pickup.id, null)
        }
        CaptionOverlapPolicy.arrange(layers).forEach { drawCaption(canvas, it.layer.content, it.subdued) }
    }

    private fun captionGeometry(state: WordMatchState, anchor: Vec2, scale: Float,
                                width: Float, height: Float, below: Boolean, radius: Float): CaptionGeometry {
        val characters = splitTextCharacters(state.inputText)
        paint.typeface = light
        paint.textSize = 17f
        val maxWidth = min(210f, width - 20f)
        val naturalWidth = characters.sumOf { paint.measureText(it).toDouble() }.toFloat().coerceAtLeast(1f)
        if (naturalWidth > maxWidth) paint.textSize *= maxWidth / naturalWidth
        val wordSize = paint.textSize
        val wordWidth = characters.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val centerY = anchor.y + if (below) radius + 29.5f else -(radius + 17.5f)
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        val reading = CaptionTextBox(anchor.x - wordWidth / 2f, baseline + metrics.ascent,
            anchor.x + wordWidth / 2f, baseline + metrics.descent)
        paint.textSize = 10.5f
        val naturalLabelWidth = paint.measureText(state.definition.displayName).coerceAtLeast(1f)
        if (naturalLabelWidth > maxWidth) paint.textSize *= maxWidth / naturalLabelWidth
        val labelSize = paint.textSize
        val labelWidth = paint.measureText(state.definition.displayName)
        val label = CaptionTextBox(anchor.x - labelWidth / 2f, centerY - 16f + paint.fontMetrics.ascent,
            anchor.x + labelWidth / 2f, centerY - 16f + paint.fontMetrics.descent)
        val boxes = listOf(reading, label).mapNotNull { box ->
            val left = max(0f, anchor.x + (box.left - anchor.x) * scale)
            val right = min(width, anchor.x + (box.right - anchor.x) * scale)
            val top = max(0f, centerY + (box.top - centerY) * scale)
            val bottom = min(height, centerY + (box.bottom - centerY) * scale)
            if (left < right && top < bottom) CaptionTextBox(left, top, right, bottom) else null
        }
        return CaptionGeometry(anchor.x, centerY, wordSize, labelSize, wordWidth, baseline, scale, boxes)
    }

    private fun drawCaption(canvas: Canvas, caption: CaptionDraw, subdued: Boolean) {
        val state = caption.state
        val geometry = caption.geometry
        fun color(value: Int): Int {
            if (!subdued) return value
            val gray = (0.2126f * Color.red(value) + 0.7152f * Color.green(value) + 0.0722f * Color.blue(value)).toInt()
            return Color.rgb(gray, gray, gray)
        }
        val alpha = (255f * caption.alpha * if (subdued) 0.32f else 1f).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.scale(geometry.scale, geometry.scale, geometry.centerX, geometry.centerY)
        paint.typeface = light
        paint.textSize = geometry.wordSize
        paint.textAlign = Paint.Align.LEFT
        var x = geometry.centerX - geometry.wordWidth / 2f
        splitTextCharacters(state.inputText).forEachIndexed { index, character ->
            paint.color = color(if (state.isCompleted || index < state.matchedInputCharacters) ink else idle)
            paint.alpha = alpha
            canvas.drawText(character, x, geometry.wordBaseline, paint)
            x += paint.measureText(character)
        }
        paint.textSize = geometry.labelSize
        paint.textAlign = Paint.Align.CENTER
        paint.color = color(muted)
        paint.alpha = alpha
        canvas.drawText(state.definition.displayName, geometry.centerX, geometry.centerY - 16f, paint)
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
