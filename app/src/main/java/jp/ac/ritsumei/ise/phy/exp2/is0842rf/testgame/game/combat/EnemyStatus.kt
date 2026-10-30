package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

class EnemyStatus(val type: DamageType, val source: WeaponId, var remaining: Float = 5f) {
    var cooldown = 0.5f
}
