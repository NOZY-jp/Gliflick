package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Xml
import androidx.core.graphics.PathParser
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.RepeaterEvolution
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.WeaponId
import org.xmlpull.v1.XmlPullParser
import kotlin.math.max

/** Draws editable interface glyphs and the independently referenced combat silhouettes. */
class WeaponIconRenderer(private val context: Context) {
    private data class SvgIcon(val path: Path, val left: Float, val top: Float,
                               val width: Float, val height: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val icons = mutableMapOf<Int, SvgIcon>()

    fun draw(canvas: Canvas, id: WeaponId, x: Float, y: Float, radius: Float, color: Int,
             evolution: RepeaterEvolution? = null) {
        drawResource(canvas, resourceFor(id, evolution), x, y, radius, color)
    }

    fun drawActor(canvas: Canvas, id: WeaponId, x: Float, y: Float, radius: Float, color: Int) {
        drawResource(canvas, actorResource(id), x, y, radius, color)
    }

    fun drawFuel(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        drawResource(canvas, R.raw.weapon_pickup_fuel, x, y, radius, color)
    }

    fun drawSpectreSegment(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float,
                           thickness: Float, color: Int) {
        val icon = icons.getOrPut(R.raw.weapon_actor_spectre) { load(R.raw.weapon_actor_spectre) }
        val dx = bx - ax; val dy = by - ay
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 0.001f) return
        canvas.save()
        canvas.translate(ax, ay)
        canvas.rotate(kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat())
        canvas.scale(length / icon.width, thickness / icon.height)
        canvas.translate(-icon.left, -(icon.top + icon.height / 2f))
        paint.color = color
        canvas.drawPath(icon.path, paint)
        canvas.restore()
    }

    // Combat geometry is deliberately exhaustive: a new weapon cannot fall back to its card icon.
    private fun actorResource(id: WeaponId): Int = when (id) {
        WeaponId.REPEATER -> error("Repeater is drawn as the player and its projectiles.")
        WeaponId.SPECTRE -> R.raw.weapon_actor_spectre
        WeaponId.SENTRY -> R.raw.weapon_actor_sentry
        WeaponId.TESLA_MINE -> R.raw.weapon_actor_tesla_mine
        WeaponId.OSCILLATOR -> R.raw.weapon_actor_oscillator
        WeaponId.METEOR -> R.raw.weapon_actor_meteor
        WeaponId.HUNTER -> R.raw.weapon_actor_hunter
        WeaponId.CHOPPER -> R.raw.weapon_actor_chopper
        WeaponId.MINEFIELD -> R.raw.weapon_actor_minefield
        WeaponId.BLADE_TRAP -> R.raw.weapon_actor_blade_trap
        WeaponId.ORBITER -> R.raw.weapon_actor_orbiter
        WeaponId.GUARDIAN -> R.raw.weapon_actor_guardian
    }

    private fun drawResource(canvas: Canvas, resource: Int, x: Float, y: Float, radius: Float, color: Int) {
        val icon = icons.getOrPut(resource) { load(resource) }
        canvas.save()
        canvas.translate(x, y)
        val scale = radius * 2f / max(icon.width, icon.height)
        canvas.scale(scale, scale)
        canvas.translate(-(icon.left + icon.width / 2f), -(icon.top + icon.height / 2f))
        paint.color = color
        canvas.drawPath(icon.path, paint)
        canvas.restore()
    }

    private fun resourceFor(id: WeaponId, evolution: RepeaterEvolution?): Int = when (evolution) {
        RepeaterEvolution.FROST_FANG -> R.raw.evolution_icon_frost_fang
        RepeaterEvolution.RICOCHET -> R.raw.evolution_icon_ricochet
        RepeaterEvolution.SHOTGUN -> R.raw.evolution_icon_shotgun
        RepeaterEvolution.EXPLODING_BULLETS -> R.raw.evolution_icon_exploding_bullets
        null -> when (id) {
            WeaponId.REPEATER -> R.raw.weapon_icon_repeater
            WeaponId.SPECTRE -> R.raw.weapon_icon_spectre
            WeaponId.SENTRY -> R.raw.weapon_icon_sentry
            WeaponId.TESLA_MINE -> R.raw.weapon_icon_tesla_mine
            WeaponId.OSCILLATOR -> R.raw.weapon_icon_oscillator
            WeaponId.METEOR -> R.raw.weapon_icon_meteor
            WeaponId.HUNTER -> R.raw.weapon_icon_hunter
            WeaponId.CHOPPER -> R.raw.weapon_icon_chopper
            WeaponId.MINEFIELD -> R.raw.weapon_icon_minefield
            WeaponId.BLADE_TRAP -> R.raw.weapon_icon_blade_trap
            WeaponId.ORBITER -> R.raw.weapon_icon_orbiter
            WeaponId.GUARDIAN -> R.raw.weapon_icon_guardian
        }
    }

    private fun load(resource: Int): SvgIcon {
        val parser = Xml.newPullParser()
        var left = 0f
        var top = 0f
        var width = 1000f
        var height = 1000f
        var pathData: String? = null
        var fillRule: String? = null
        context.resources.openRawResource(resource).use { input ->
            parser.setInput(input, "UTF-8")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "svg" -> parser.getAttributeValue(null, "viewBox")?.let { viewBox ->
                        val bounds = viewBox.trim().split(Regex("[ ,]+"))
                            .map(String::toFloat)
                        require(bounds.size == 4 && bounds[2] > 0f && bounds[3] > 0f) {
                            "SVG viewBox must contain x, y, width, height."
                        }
                        left = bounds[0]; top = bounds[1]; width = bounds[2]; height = bounds[3]
                    }
                    "path" -> if (pathData == null) {
                        pathData = parser.getAttributeValue(null, "d")
                        fillRule = parser.getAttributeValue(null, "fill-rule")
                    }
                }
            }
        }
        val path = PathParser.createPathFromPathData(requireNotNull(pathData) {
            "The weapon SVG needs a path element."
        }) ?: error("Could not parse weapon SVG path.")
        if (fillRule == "evenodd") path.fillType = Path.FillType.EVEN_ODD
        return SvgIcon(path, left, top, width, height)
    }
}
