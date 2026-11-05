package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

/** A presentation clock, independent of combat time while rewards pause the world. */
class RewardFeedback(val weapon: WeaponId?) {
    var age = 0f; private set
    fun advance(dt: Float) { age += dt }
    val finished get() = age >= DURATION
    fun pose(startRadius: Float, targetRadius: Float): RewardRingPose {
        val distance=(startRadius-targetRadius).coerceAtLeast(0f)
        // Match speed at both ends of the rounded reflection. No extra zigzag or radius jump.
        val speed=3f*distance/(TRAVEL_SECONDS+0.75f*BOUNCE_SECONDS)
        val shoulder=targetRadius+speed*BOUNCE_SECONDS/4f
        val radius=when {
            age<TRAVEL_SECONDS -> {
                val t=(age/TRAVEL_SECONDS).coerceIn(0f,1f)
                startRadius+(shoulder-startRadius)*t*t*t
            }
            age<TRAVEL_SECONDS+BOUNCE_SECONDS -> {
                val t=age-TRAVEL_SECONDS-BOUNCE_SECONDS/2f
                targetRadius+speed/BOUNCE_SECONDS*t*t
            }
            else -> {
                val t=((age-TRAVEL_SECONDS-BOUNCE_SECONDS)/TRAVEL_SECONDS).coerceIn(0f,1f)
                shoulder+(startRadius-shoulder)*(1f-(1f-t)*(1f-t)*(1f-t))
            }
        }
        val midpoint=TRAVEL_SECONDS+BOUNCE_SECONDS/2f
        val fade=((age-midpoint)/(DURATION-midpoint)).coerceIn(0f,1f)
        return RewardRingPose(radius,0.65f*(1f-fade))
    }
    companion object {
        const val TRAVEL_SECONDS=0.58f
        const val BOUNCE_SECONDS=0.12f
        const val DURATION=TRAVEL_SECONDS*2f+BOUNCE_SECONDS
    }
}
data class RewardRingPose(val radius:Float,val alpha:Float)
