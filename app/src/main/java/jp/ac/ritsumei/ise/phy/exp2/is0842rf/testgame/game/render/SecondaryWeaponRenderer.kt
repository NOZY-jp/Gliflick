package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import kotlin.math.*

class SecondaryWeaponRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons = WeaponIconRenderer(context)
    private val ink = Color.rgb(117, 117, 110)
    private fun color(type: DamageType): Int = when (type) {
        DamageType.HEAT -> Color.rgb(158, 91, 96)
        DamageType.ELECTRIC -> Color.rgb(116, 117, 139)
        DamageType.CHEMICAL -> Color.rgb(125, 137, 115)
        DamageType.EXPLOSIVE -> Color.rgb(158, 124, 104)
        DamageType.COLD -> Color.rgb(122, 143, 151)
        else -> ink
    }
    fun draw(canvas: Canvas, world: CombatWorld) {
        world.weaponActors.forEach { actor ->
            val p = actor.position
            paint.color = color(actor.type); paint.alpha = if (actor.active) 155 else 45
            paint.strokeWidth = 0.85f; paint.style = Paint.Style.STROKE
            when (actor.visual) {
                WeaponVisualKind.TESLA -> {
                    paint.alpha = 38
                    canvas.drawCircle(p.x, p.y, actor.range, paint)
                    icons.draw(canvas, actor.source, p.x, p.y, 6f, color(actor.type))
                }
                WeaponVisualKind.BOMBARDMENT -> {
                    paint.alpha = 70
                    canvas.drawCircle(p.x, p.y, actor.range, paint)
                    canvas.drawLine(p.x - actor.range, p.y, p.x + actor.range, p.y, paint)
                    canvas.drawLine(p.x, p.y - actor.range, p.x, p.y + actor.range, paint)
                    paint.alpha = 170
                    repeat(5) { i ->
                        val a = actor.angle * 1.5f + i * 2f * PI.toFloat() / 5f
                        val r = actor.range * (0.25f + (sin(actor.angle * 2 + i) + 1f) * 0.25f)
                        val x = p.x + cos(a) * r; val y = p.y + sin(a) * r
                        canvas.drawLine(x - 3f, y - 12f, x, y, paint)
                    }
                }
                WeaponVisualKind.BLADE -> {
                    canvas.save()
                    canvas.rotate(actor.angle * 180f / PI.toFloat(), p.x, p.y)
                    repeat(4) {
                        canvas.rotate(90f, p.x, p.y)
                        val path = Path()
                        path.moveTo(p.x, p.y - 2f); path.lineTo(p.x + actor.range * 0.3f, p.y - actor.range)
                        path.lineTo(p.x - actor.range * 0.1f, p.y - actor.range * 0.85f); path.close()
                        canvas.drawPath(path, paint)
                    }
                    canvas.restore()
                    canvas.drawCircle(p.x, p.y, 2f, paint)
                }
                WeaponVisualKind.ORB -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(p.x, p.y, 4.5f, paint)
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(p.x, p.y, 6f, paint)
                }
                else -> {
                    canvas.save()
                    canvas.rotate(actor.angle * 180f / PI.toFloat() + 90f, p.x, p.y)
                    icons.draw(canvas, actor.source, p.x, p.y,
                        if (actor.visual == WeaponVisualKind.SENTRY) 7.5f else 5.5f,
                        Color.argb(if (actor.active) 180 else 55, Color.red(color(actor.type)),
                            Color.green(color(actor.type)), Color.blue(color(actor.type))))
                    canvas.restore()
                }
            }
        }
        world.weaponEffects.forEach { effect ->
            paint.color = color(effect.type)
            paint.alpha = (150 * effect.alpha).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (effect.type == DamageType.ELECTRIC) 1.2f else 0.9f
            if (effect.radius > 0f) {
                val radius = effect.radius * (1f - effect.alpha * 0.25f)
                canvas.drawCircle(effect.start.x, effect.start.y, radius, paint)
            } else canvas.drawLine(effect.start.x, effect.start.y, effect.end.x, effect.end.y, paint)
        }
        world.pickups.forEach { pickup ->
            val p = pickup.position
            icons.draw(canvas, if (pickup.kind == PickupKind.METEOR) WeaponId.METEOR else WeaponId.GUARDIAN,
                p.x, p.y, 9f, Color.rgb(158, 91, 96))
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(199, 199, 188)
            canvas.drawRect(p.x - 10f, p.y + 13f, p.x + 10f, p.y + 14.5f, paint)
            paint.color = ink
            canvas.drawRect(p.x - 10f, p.y + 13f, p.x - 10f + 20f * pickup.remainingFraction, p.y + 14.5f, paint)
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = 8f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(pickup.kind.title, p.x, p.y + 25f, paint)
        }
        paint.alpha = 255; paint.style = Paint.Style.FILL
    }

    fun drawArsenal(canvas: Canvas, world: CombatWorld, width: Float, height: Float) {
        val weapons = listOf(WeaponId.REPEATER) + world.secondaryWeapons.map { it.id }
        val left = width / 2f - 67f
        for (slot in 0..4) {
            val x = left + slot * 27f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.65f
            paint.color = Color.rgb(199, 199, 188)
            canvas.drawRoundRect(x, height - 29f, x + 23f, height - 6f, 2f, 2f, paint)
            weapons.getOrNull(slot)?.let { icons.draw(canvas, it, x + 11.5f, height - 17.5f, 8f, ink) }
        }
        paint.style = Paint.Style.FILL
    }
}
