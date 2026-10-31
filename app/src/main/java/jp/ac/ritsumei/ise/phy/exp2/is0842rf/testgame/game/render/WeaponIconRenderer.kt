package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Xml
import androidx.core.graphics.PathParser
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
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
             evolution: WeaponEvolution? = null) {
        drawResource(canvas, resourceFor(id, evolution), x, y, radius, color)
    }

    fun drawActor(canvas: Canvas, id: WeaponId, x: Float, y: Float, radius: Float, color: Int) {
        drawResource(canvas, actorResource(id), x, y, radius, color)
    }

    fun drawHunterElite(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) =
        drawResource(canvas, R.raw.weapon_actor_hunter_elite, x, y, radius, color)

    fun drawSaw(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) =
        drawResource(canvas, R.raw.weapon_actor_chopper_saw, x, y, radius, color)

    fun drawFuel(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, bonus: WeaponStat? = null) {
        val resource = when (bonus) {
            WeaponStat.DAMAGE -> R.raw.weapon_pickup_fuel_damage
            WeaponStat.SPEED -> R.raw.weapon_pickup_fuel_speed
            WeaponStat.RANGE -> R.raw.weapon_pickup_fuel_range
            else -> R.raw.weapon_pickup_fuel
        }
        drawResource(canvas, resource, x, y, radius, color)
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

    private fun resourceFor(id: WeaponId, evolution: WeaponEvolution?): Int = when (evolution) {
        RepeaterEvolution.FROST_FANG -> R.raw.evolution_icon_frost_fang
        RepeaterEvolution.RICOCHET -> R.raw.evolution_icon_ricochet
        RepeaterEvolution.SHOTGUN -> R.raw.evolution_icon_shotgun
        RepeaterEvolution.EXPLODING_BULLETS -> R.raw.evolution_icon_exploding_bullets
        is SecondaryEvolution -> when (evolution) {
            SecondaryEvolution.SUPERCHARGED -> R.raw.evolution_icon_supercharged
            SecondaryEvolution.SELF_DESTRUCT -> R.raw.evolution_icon_self_destruct
            SecondaryEvolution.VOLATILE_FUEL -> R.raw.evolution_icon_volatile_fuel
            SecondaryEvolution.STALKING_ALGORITHMS -> R.raw.evolution_icon_stalking_algorithms
            SecondaryEvolution.DEFENSE_GRID -> R.raw.evolution_icon_defense_grid
            SecondaryEvolution.SOLID_CORE -> R.raw.evolution_icon_solid_core
            SecondaryEvolution.WATCHER_SUPREME -> R.raw.evolution_icon_watcher_supreme
            SecondaryEvolution.ABUNDANT_COILS -> R.raw.evolution_icon_abundant_coils
            SecondaryEvolution.ELECTRICAL_BURN -> R.raw.evolution_icon_electrical_burn
            SecondaryEvolution.RESTRICTED_AREA -> R.raw.evolution_icon_restricted_area
            SecondaryEvolution.STORM_SOVEREIGN -> R.raw.evolution_icon_storm_sovereign
            SecondaryEvolution.TWIN_HEADED -> R.raw.evolution_icon_twin_headed
            SecondaryEvolution.BALL_LIGHTNING -> R.raw.evolution_icon_ball_lightning
            SecondaryEvolution.STABILIZED_PLASMA -> R.raw.evolution_icon_stabilized_plasma
            SecondaryEvolution.PRIME_RESONANCE -> R.raw.evolution_icon_prime_resonance
            SecondaryEvolution.SURVEILLANCE -> R.raw.evolution_icon_surveillance
            SecondaryEvolution.ICE_RAIN -> R.raw.evolution_icon_ice_rain
            SecondaryEvolution.RELENTLESS -> R.raw.evolution_icon_relentless
            SecondaryEvolution.SKY_SPOTTER -> R.raw.evolution_icon_sky_spotter
            SecondaryEvolution.HUNTER_ELITE -> R.raw.evolution_icon_hunter_elite
            SecondaryEvolution.ELECTRIFIED_BOLTS -> R.raw.evolution_icon_electrified_bolts
            SecondaryEvolution.CHAIN_LIGHTNING -> R.raw.evolution_icon_chain_lightning
            SecondaryEvolution.PREDATOR_STRAIN -> R.raw.evolution_icon_predator_strain
            SecondaryEvolution.DUAL_WIELD -> R.raw.evolution_icon_dual_wield
            SecondaryEvolution.FLAMING_BLADES -> R.raw.evolution_icon_flaming_blades
            SecondaryEvolution.BOOMERANG -> R.raw.evolution_icon_boomerang
            SecondaryEvolution.BLOODBATH -> R.raw.evolution_icon_bloodbath
            SecondaryEvolution.SAW_LAUNCHER -> R.raw.evolution_icon_saw_launcher
            SecondaryEvolution.MINELAYER -> R.raw.evolution_icon_minelayer
            SecondaryEvolution.INFUSION -> R.raw.evolution_icon_infusion
            SecondaryEvolution.HUNTER_SEEKER -> R.raw.evolution_icon_hunter_seeker
            SecondaryEvolution.RECURSIVE_MINES -> R.raw.evolution_icon_recursive_mines
            SecondaryEvolution.BLADE_REPOSITORY -> R.raw.evolution_icon_blade_repository
            SecondaryEvolution.POISON_COATING -> R.raw.evolution_icon_poison_coating
            SecondaryEvolution.SAWTOOTH_BLADES -> R.raw.evolution_icon_sawtooth_blades
            SecondaryEvolution.CUTTING_EDGE -> R.raw.evolution_icon_cutting_edge
            SecondaryEvolution.CONTINUUM -> R.raw.evolution_icon_continuum
            SecondaryEvolution.STORM_RING -> R.raw.evolution_icon_storm_ring
            SecondaryEvolution.DOPPELGANGER -> R.raw.evolution_icon_doppelganger
            SecondaryEvolution.TETHER -> R.raw.evolution_icon_tether
            SecondaryEvolution.TOXIC_WARD -> R.raw.evolution_icon_toxic_ward
            SecondaryEvolution.EXPERIMENTAL_FUEL -> R.raw.evolution_icon_experimental_fuel
            SecondaryEvolution.GUARDIAN_TEAM -> R.raw.evolution_icon_guardian_team
        }
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
        else -> error("No icon registered for evolution $evolution")
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
