package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.*
import android.util.Xml
import androidx.core.graphics.PathParser
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import org.xmlpull.v1.XmlPullParser
import kotlin.math.PI

/** Uses the editable Wiki-referenced SVGs, with procedural owned barriers and protection links. */
class EnemyRenderer(private val context: Context) {
    private val paths = mutableMapOf<Int, Path>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shield = listOf(R.raw.enemy_shield_drone_1, R.raw.enemy_shield_drone_2,
        R.raw.enemy_shield_drone_3, R.raw.enemy_shield_drone_4, R.raw.enemy_shield_drone_5)
    private val shooter = listOf(R.raw.enemy_shooter_1, R.raw.enemy_shooter_2, R.raw.enemy_shooter_3,
        R.raw.enemy_shooter_4, R.raw.enemy_shooter_5)
    private val missile = listOf(R.raw.enemy_shooter_missile_1, R.raw.enemy_shooter_missile_2,
        R.raw.enemy_shooter_missile_3, R.raw.enemy_shooter_missile_4, R.raw.enemy_shooter_missile_5)
    private val shard = listOf(R.raw.enemy_splitter_shard_1, R.raw.enemy_splitter_shard_2, R.raw.enemy_splitter_shard_3)

    fun drawProtection(canvas: Canvas, world: CombatWorld) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.65f
        paint.color = Color.argb(70, 117, 117, 110)
        world.enemies.filter(world::isProtected).forEach { enemy ->
            world.barriers.filter { it.isAlive && (enemy.position - it.owner.position).length() < it.protectionRadius }
                .forEach { canvas.drawLine(it.owner.position.x, it.owner.position.y, enemy.position.x, enemy.position.y, paint) }
            canvas.drawCircle(enemy.position.x, enemy.position.y, enemy.radius + 3f, paint)
        }
    }
    fun drawBarriers(canvas: Canvas, world: CombatWorld) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = Color.rgb(117, 117, 110)
        world.barriers.filter { it.isAlive }.forEach { barrier ->
            val p = barrier.owner.position; val r = barrier.radius
            paint.strokeWidth = barrier.thickness
            canvas.drawArc(p.x - r, p.y - r, p.x + r, p.y + r,
                (barrier.angle - barrier.halfArc) * 180f / PI.toFloat(), 120f, false, paint)
        }
    }
    /** Drone markers retain their existing renderer. */
    fun drawBody(canvas: Canvas, enemy: Enemy, color: Int): Boolean {
        val tier = enemy.definition.tier - 1
        when (enemy.definition.kind) {
            EnemyKind.BOSS -> when (enemy.definition.bossKind) {
                BossKind.GEMINI -> {
                    shape(canvas, R.raw.boss_gemini, enemy.position, enemy.radius, 0f, color)
                    paint.color = color; paint.style = Paint.Style.FILL
                    val pupil = enemy.position + Vec2(kotlin.math.cos(enemy.angle), kotlin.math.sin(enemy.angle)) * (enemy.radius * 0.18f)
                    canvas.drawCircle(pupil.x, pupil.y, enemy.radius * 0.14f, paint)
                }
                BossKind.POET -> shape(canvas, R.raw.boss_poet, enemy.position, enemy.radius, 0f,
                    Color.argb(Color.alpha(color),117,117,110))
                BossKind.CYPHER -> { paint.color = color; paint.style = Paint.Style.FILL; canvas.drawCircle(enemy.position.x,enemy.position.y,enemy.radius,paint) }
                null -> error("Boss needs a kind")
            }
            EnemyKind.DRONE -> return false
            EnemyKind.SPLITTER -> enemy.hitCircles().forEach { circle ->
                if (enemy.deathAge == null || enemy.splitterHealth!!.partHealth[circle.part] == 0)
                    shape(canvas, shard[tier], circle.position, EnemyCatalog.UNIT * 15f,
                        enemy.angle + circle.part * 2f * PI.toFloat() / enemy.definition.splitter!!.partCount, color)
            }
            EnemyKind.SPLITTER_SHARD -> shape(canvas, shard[tier], enemy.position, enemy.radius, enemy.angle, color)
            EnemyKind.SHIELD_DRONE -> shape(canvas, shield[tier], enemy.position, enemy.radius, enemy.angle, color)
            EnemyKind.SHOOTER -> shape(canvas, shooter[tier], enemy.position, enemy.radius, enemy.angle, color)
            EnemyKind.SHOOTER_MISSILE -> {
                val alpha = (Color.alpha(color) * (0.25f + enemy.activationProgress * 0.75f)).toInt()
                shape(canvas, missile[tier], enemy.position, enemy.radius, enemy.angle,
                    (color and 0x00ffffff) or (alpha shl 24))
            }
            EnemyKind.WARDER -> shape(canvas, R.raw.enemy_warder, enemy.position, enemy.radius, enemy.angle, color)
        }
        return true
    }
    private fun shape(canvas: Canvas, resource: Int, p: Vec2, radius: Float, angle: Float, color: Int) {
        val path = paths.getOrPut(resource) {
            val result = Path().apply { fillType = Path.FillType.EVEN_ODD }
            context.resources.openRawResource(resource).use { stream ->
                val xml = Xml.newPullParser(); xml.setInput(stream, null)
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path")
                        xml.getAttributeValue(null, "d")?.let { result.addPath(PathParser.createPathFromPathData(it)!!) }
                    xml.next()
                }
            }
            result
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(angle * 180f / PI.toFloat())
        canvas.scale(radius, radius); canvas.drawPath(path, paint); canvas.restore()
    }
}
