package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.WeaponId
import kotlin.math.*

/** Small native vector marks remain legible at phone HUD sizes. */
class WeaponIconRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: Canvas, id: WeaponId, x: Float, y: Float, radius: Float, color: Int) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.12f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
        fun polygon(sides: Int, r: Float = 0.8f, offset: Float = -PI.toFloat() / 2) {
            val path = Path()
            repeat(sides) {
                val a = offset + it * 2f * PI.toFloat() / sides
                val px = cos(a) * r; val py = sin(a) * r
                if (it == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); canvas.drawPath(path, paint)
        }
        when (id) {
            WeaponId.REPEATER -> repeat(3) { i -> line(-0.65f, -0.55f + i * 0.5f, 0.45f, -0.8f + i * 0.5f) }
            WeaponId.SPECTRE -> { polygon(3); line(-0.4f, 0.65f, 0f, 0.35f); line(0.4f, 0.65f, 0f, 0.35f) }
            WeaponId.HUNTER -> { polygon(3); canvas.drawCircle(0f, 0f, 0.25f, paint) }
            WeaponId.SENTRY -> { canvas.drawCircle(0f, 0f, 0.58f, paint); line(0f, 0f, 0f, -0.95f); line(-0.8f, 0.7f, 0.8f, 0.7f) }
            WeaponId.TESLA_MINE -> {
                polygon(4); val p = Path(); p.moveTo(0.2f, -0.7f); p.lineTo(-0.25f, 0f)
                p.lineTo(0.2f, 0f); p.lineTo(-0.2f, 0.7f); canvas.drawPath(p, paint)
            }
            WeaponId.OSCILLATOR -> {
                val p = Path()
                for (i in 0..20) { val px = -0.85f + i * 0.085f; val py = sin(px * 7f) * 0.45f
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }
                canvas.drawPath(p, paint)
            }
            WeaponId.METEOR -> { canvas.drawCircle(0f, 0f, 0.6f, paint); line(-0.9f, 0f, 0.9f, 0f); line(0f, -0.9f, 0f, 0.9f) }
            WeaponId.CHOPPER, WeaponId.BLADE_TRAP -> {
                canvas.drawCircle(0f, 0f, 0.2f, paint)
                repeat(4) { canvas.rotate(90f); val p = Path(); p.moveTo(0f, -0.2f)
                    p.lineTo(0.35f, -0.75f); p.lineTo(-0.15f, -0.85f); p.close(); canvas.drawPath(p, paint) }
            }
            WeaponId.MINEFIELD -> { polygon(6, 0.6f); repeat(6) {
                val a = it * PI.toFloat() / 3f; line(cos(a) * 0.6f, sin(a) * 0.6f, cos(a) * 0.9f, sin(a) * 0.9f) } }
            WeaponId.ORBITER -> { canvas.drawOval(-0.9f, -0.5f, 0.9f, 0.5f, paint); canvas.drawCircle(0.65f, -0.3f, 0.2f, paint) }
            WeaponId.GUARDIAN -> { polygon(5); canvas.drawCircle(0f, 0f, 0.4f, paint); line(-0.65f, 0f, 0.65f, 0f) }
        }
        canvas.restore()
    }
}
