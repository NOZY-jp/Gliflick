package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*

/** Equipment and its evolution feedback share their exact slot geometry. */
object ArsenalLayout {
    fun left(width:Float,slot:Int)=width/2f-67f+slot*27f
    fun center(world:CombatWorld,weapon:WeaponId,width:Float,height:Float):Vec2? {
        val slot=(listOf(WeaponId.REPEATER)+world.secondaryWeapons.map { it.id }).indexOf(weapon)
        return if(slot<0) null else Vec2(left(width,slot)+11.5f,height-17.5f)
    }
}
