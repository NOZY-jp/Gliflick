package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import kotlin.math.*

/** Combat silhouettes follow the public gameplay/demo, independently of the square equipment icons. */
class SecondaryWeaponRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons = WeaponIconRenderer(context)
    private val sparklePath=Path()
    private val ink = Color.rgb(117, 117, 110)
    private fun ink(alpha: Int) = Color.argb(alpha.coerceIn(0, 255), 117, 117, 110)
    private fun stroke(alpha: Int, width: Float = 0.7f) {
        paint.shader = null; paint.pathEffect = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.BUTT; paint.color = ink(alpha)
    }
    private fun circle(canvas: Canvas, p: Vec2, radius: Float, alpha: Int, width: Float = 0.7f) {
        stroke(alpha, width); canvas.drawCircle(p.x, p.y, radius, paint)
    }
    private fun actor(canvas: Canvas, id: WeaponId, p: Vec2, radius: Float, angle: Float = 0f, alpha: Int = 190) {
        canvas.save()
        canvas.rotate(angle * 180f / PI.toFloat(), p.x, p.y)
        icons.drawActor(canvas, id, p.x, p.y, radius, ink(alpha))
        canvas.restore()
    }

    fun draw(canvas: Canvas, world: CombatWorld) {
        world.weaponActors.forEach { body ->
            val p = body.position
            when (body.source) {
                WeaponId.SPECTRE -> {
                    val points = body.trail + p
                    for (i in 1 until points.size) {
                        val a = points[i - 1]; val b = points[i]
                        icons.drawSpectreSegment(canvas, a.x, a.y, b.x, b.y,
                            if ((body as DroneActor).enhanced) 3f else 1.5f,
                            ink(210 * i / points.size))
                    }
                    if ((body as DroneActor).enhanced) {
                        paint.style = Paint.Style.FILL; paint.color = ink(190)
                        canvas.drawCircle(p.x, p.y, 2f, paint)
                        circle(canvas, p, 10f, 95, 1f)
                    }
                }
                WeaponId.HUNTER -> {
                    val hunter = body as HunterActor
                    if (hunter.enhanced) {
                        canvas.save(); canvas.rotate(body.angle * 180f / PI.toFloat(), p.x, p.y)
                        icons.drawHunterElite(canvas, p.x, p.y, 13f, ink(190)); canvas.restore()
                    } else actor(canvas, body.source, p, 5f, body.angle)
                    hunter.beams.forEach { beam ->
                        if (beam.type == DamageType.ELECTRIC) drawLightning(canvas, beam)
                        else {
                            stroke(170)
                            canvas.drawLine(beam.start.x, beam.start.y, beam.end.x, beam.end.y, paint)
                        }
                    }
                }
                WeaponId.SENTRY -> {
                    val muzzle = p + Vec2(cos(body.angle), sin(body.angle)) * (world.player.radius + 2f)
                    actor(canvas, body.source, muzzle, 2.5f, body.angle)
                }
                WeaponId.TESLA_MINE -> {
                    circle(canvas, p, body.range, 48)
                    actor(canvas, body.source, p, 4.5f * body.sizeScale, body.angle)
                }
                WeaponId.CHOPPER -> {
                    paint.style = Paint.Style.FILL; paint.color = ink(22)
                    val bounds = RectF(p.x - body.range, p.y - body.range, p.x + body.range, p.y + body.range)
                    val degrees = body.angle * 180f / PI.toFloat()
                    canvas.drawArc(bounds, degrees + 90f, 40f, true, paint)
                    canvas.drawArc(bounds, degrees + 270f, 40f, true, paint)
                    if ((body as MovingBladeActor).saw) {
                        canvas.save(); canvas.rotate(body.angle * 180f / PI.toFloat(), p.x, p.y)
                        icons.drawSaw(canvas, p.x, p.y, body.range, ink(190)); canvas.restore()
                    } else actor(canvas, body.source, p, body.range, body.angle)
                }
                WeaponId.BLADE_TRAP -> actor(canvas, body.source, p, body.range, body.angle)
                WeaponId.MINEFIELD -> {
                    circle(canvas, p, body.range * 0.5f, 48)
                    actor(canvas, body.source, p, 4f * body.sizeScale, body.angle)
                }
                WeaponId.ORBITER -> {
                    val direction = (p - world.player.position).normalized()
                    val length = (p - world.player.position).length() - body.range
                    stroke(30, 0.6f)
                    var distance = 8f
                    while (distance < length) {
                        val a = world.player.position + direction * distance
                        val b = world.player.position + direction * min(distance + 3f, length)
                        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                        distance += 8f
                    }
                    if (body.state.has(SecondaryEvolution.TETHER)) {
                        stroke(90, 0.8f)
                        canvas.drawLine(world.player.position.x, world.player.position.y, p.x, p.y, paint)
                    }
                    val trail = body.trail
                    trail.forEachIndexed { i, point ->
                        val fraction = (i + 1f) / (trail.size + 1f)
                        actor(canvas, body.source, point, body.range * fraction, alpha = (42 * fraction).toInt())
                    }
                    actor(canvas, body.source, p, body.range, alpha = 80)
                    if (body.state.has(SecondaryEvolution.STORM_RING)) {
                        for (i in 1 until trail.size) drawLightning(canvas,
                            WeaponEffect(trail[i - 1], trail[i], DamageType.ELECTRIC))
                        val path = Path()
                        for (i in 0..32) {
                            val angle = i * PI.toFloat() / 16f
                            val radius = body.range * (1.06f + 0.12f * sin(i * 2.7f + body.age * 20f))
                            val point = p + Vec2(cos(angle), sin(angle)) * radius
                            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        }
                        stroke(140, 0.65f); canvas.drawPath(path, paint)
                    }
                }
                WeaponId.GUARDIAN -> drawGuardian(canvas, body as GuardianActor, world)
                WeaponId.METEOR -> actor(canvas, body.source, p, 8f * body.sizeScale, body.age * 0.6f, 125)
                WeaponId.REPEATER, WeaponId.OSCILLATOR -> Unit
            }
        }
        world.weaponEffects.forEach { effect ->
            if (effect.radius == 0f) {
                if (effect.type == DamageType.ELECTRIC) drawLightning(canvas, effect)
                else {
                    stroke((170 * effect.alpha).toInt())
                    canvas.drawLine(effect.start.x, effect.start.y, effect.end.x, effect.end.y, paint)
                }
            } else {
                val progress = 1f - effect.alpha
                if (effect.source == WeaponId.METEOR) {
                    stroke((65 * effect.alpha).toInt(), 1.1f)
                    canvas.drawLine(effect.start.x + world.height, effect.start.y - world.height,
                        effect.start.x, effect.start.y, paint)
                    paint.style = Paint.Style.FILL; paint.color = ink((28 * effect.alpha).toInt())
                    canvas.drawCircle(effect.start.x, effect.start.y, effect.radius * progress, paint)
                }
                circle(canvas, effect.start, effect.radius * (0.65f + progress * 0.45f),
                    (100 * effect.alpha).toInt(), 1.2f * effect.alpha + 0.2f)
            }
        }
        world.pickups.forEach { pickup ->
            val p = pickup.position
            if (pickup.kind == PickupKind.REROLL) {
                icons.drawReroll(canvas, p.x, p.y, 4.8f, ink(210))
            } else if (pickup.kind == PickupKind.METEOR) actor(canvas, WeaponId.METEOR, p,
                8f * WeaponSizeTuning.forWeapon(WeaponId.METEOR), pickup.age * 0.6f)
            else icons.drawFuel(canvas, p.x, p.y, 7f, ink(190), pickup.fuelBonus)
            val scale=if(pickup.kind==PickupKind.REROLL)0.6f else 1f
            canvas.save();canvas.scale(scale,scale,p.x,p.y)
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(199, 199, 188)
            canvas.drawRect(p.x - 10f, p.y + 13f, p.x + 10f, p.y + 14.5f, paint)
            paint.color = ink
            canvas.drawRect(p.x - 10f, p.y + 13f, p.x - 10f + 20f * pickup.remainingFraction, p.y + 14.5f, paint)
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = 8f; paint.textAlign = Paint.Align.CENTER
            if(pickup.kind != PickupKind.REROLL) canvas.drawText(pickup.kind.title + (pickup.fuelBonus?.let { "・${it.title}" } ?: ""), p.x, p.y + 25f, paint)
            canvas.restore()
        }
        paint.alpha = 255; paint.style = Paint.Style.FILL
    }

    fun drawRerollSparkles(canvas:Canvas,world:CombatWorld) {
        world.pickups.filter { it.kind==PickupKind.REROLL }.forEach { drawRerollCloud(canvas,it) }
    }

    private fun drawRerollCloud(canvas: Canvas, pickup: CombatPickup) {
        val tick=(pickup.age*7f).toInt()
        for(index in (tick-3).coerceAtLeast(0)..tick) {
            val age=pickup.age-index/7f
            val seed=abs(sin(index*17.13f))
            val lifetime=0.18f+seed*0.10f
            if(age !in 0f..lifetime) continue
            val progress=age/lifetime
            val side=if(index%2==0)1f else -1f
            val angle=-PI.toFloat()/2f+side*(0.65f+seed*0.2f)
            val speed=90f+seed*40f
            val x=pickup.position.x+side*8f+cos(angle)*speed*age+sin(age*3f+index)*age*3f
            val y=pickup.position.y-4f+sin(angle)*speed*age-4f*age*age
            val size=(2.4f+seed*2.2f)*sin(PI.toFloat()*progress).coerceAtLeast(0f)
            if(size<0.08f) continue
            val alpha=if(progress<0.7f)180 else (180*(1f-progress)/0.3f).toInt()
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=ink(alpha)
            val star=sparklePath.apply {
                reset()
                moveTo(0f,-size);lineTo(size*0.23f,-size*0.23f);lineTo(size,0f)
                lineTo(size*0.23f,size*0.23f);lineTo(0f,size);lineTo(-size*0.23f,size*0.23f)
                lineTo(-size,0f);lineTo(-size*0.23f,-size*0.23f);close()
            }
            canvas.save();canvas.translate(x,y);canvas.rotate(index*137.5f+age*45f);canvas.drawPath(star,paint);canvas.restore()
        }
    }

    private fun drawGuardian(canvas: Canvas, body: GuardianActor, world: CombatWorld) {
        val p = body.position
        circle(canvas, world.player.position, body.range, 40)
        stroke(if (body.active) 70 else 30, 0.55f)
        paint.pathEffect = DashPathEffect(floatArrayOf(2f, 2f), body.age * 2f)
        canvas.drawCircle(p.x, p.y, body.orbitRadius, paint)
        paint.pathEffect = null
        if (body.active) for (phase in listOf(0f, PI.toFloat())) {
            for (i in 6 downTo 0) {
                val point = body.fireballPosition(phase, i * 0.13f)
                circle(canvas, point, body.fireballRadius * (1f - i * 0.095f),
                    175 - i * 17, 0.9f)
            }
        }
        actor(canvas, body.source, p, body.bodyRadius, body.angle, if (body.active) 210 else 75)
        if (!body.active) {
            stroke(150, 0.6f)
            canvas.drawLine(p.x - 1f, p.y - 1f, p.x + 1f, p.y + 1f, paint)
            canvas.drawLine(p.x + 1f, p.y - 1f, p.x - 1f, p.y + 1f, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = ink(50)
        canvas.drawRect(p.x - 4f, p.y + 7f, p.x + 4f, p.y + 8f, paint)
        paint.color = ink(170)
        canvas.drawRect(p.x - 4f, p.y + 7f, p.x - 4f + 8f * body.durationFraction, p.y + 8f, paint)
    }

    private fun drawLightning(canvas: Canvas, effect: WeaponEffect) {
        val delta = effect.end - effect.start
        val length = delta.length()
        if (length < 0.01f) return
        val perpendicular = Vec2(-delta.y / length, delta.x / length)
        val segments = (length / 9f).toInt().coerceIn(2, 12)
        val phase = floor(effect.age * 30f)
        val path = Path().apply { moveTo(effect.start.x, effect.start.y) }
        for (i in 1 until segments) {
            val noise = sin(i * 7.31f + effect.start.x * 0.13f + effect.end.y * 0.09f + phase * 2.7f)
            val point = effect.start + delta * (i.toFloat() / segments) +
                perpendicular * (noise * min(5f, length * 0.09f))
            path.lineTo(point.x, point.y)
        }
        path.lineTo(effect.end.x, effect.end.y)
        stroke((175 * effect.alpha).toInt(), 0.7f)
        canvas.drawPath(path, paint)
    }

    /** Secondary projectiles retain their own appearance instead of sharing Repeater's long trail. */
    fun drawProjectile(canvas: Canvas, bullet: Projectile): Boolean = when (bullet.source) {
        WeaponId.OSCILLATOR -> {
            actor(canvas, WeaponId.OSCILLATOR, bullet.position, if (bullet.damageType == DamageType.ELECTRIC) 2.3f else 1.5f, alpha = 155)
            if (bullet.damageType == DamageType.ELECTRIC) circle(canvas, bullet.position, 3.3f, 90, 0.5f)
            true
        }
        WeaponId.SENTRY -> {
            val direction = bullet.velocity.normalized()
            for (i in 3 downTo 0) {
                val p = bullet.position - direction * min(i * 3f, bullet.age * bullet.velocity.length())
                circle(canvas, p, 2.1f - i * 0.3f, 160 - i * 38, 0.6f)
            }
            true
        }
        else -> false
    }

    fun drawArsenal(canvas: Canvas, world: CombatWorld, width: Float, height: Float) {
        val weapons = listOf(WeaponId.REPEATER) + world.secondaryWeapons.map { it.id }
        for (slot in 0..4) {
            val x = ArsenalLayout.left(width,slot)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.65f
            paint.color = Color.rgb(199, 199, 188)
            canvas.drawRoundRect(x, height - 29f, x + 23f, height - 6f, 2f, 2f, paint)
            weapons.getOrNull(slot)?.let { id ->
                icons.draw(canvas, id, x + 11.5f, height - 17.5f, 8f, ink)
                world.weaponState(id)?.let { state ->
                    paint.style = Paint.Style.FILL; paint.color = ink(45)
                    canvas.drawRect(x, height - 4f, x + 23f, height - 2.5f, paint)
                    paint.color = ink(if (state.canEvolve) 230 else 150)
                    canvas.drawRect(x, height - 4f, x + 23f * state.evolutionProgress, height - 2.5f, paint)
                    if (state.evolutionsChosen > 0) {
                        paint.textSize = 6f; paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText("${state.evolutionsChosen}", x + 22f, height - 23f, paint)
                    }
                }
            }
        }
        paint.style = Paint.Style.FILL
    }
}
