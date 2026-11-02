package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

/** Local Japanese word-pool adaptation of the source game's English trigger groups. */
object WeaponTriggerGlyphs {
    const val SPECTRE = "aeo"
    const val HUNTER = "iou"
    const val TESLA = "bcde"
    const val MINEFIELD = "fghi"
    const val BLADE = "jkm"
    const val ORBITER = "nop"
    val extraGroups = listOf("yz", "w")
    val startingGroups get() = listOf(TESLA, MINEFIELD, BLADE, ORBITER) + extraGroups
    fun label(letters: String) = letters.toList().joinToString("・")
}
