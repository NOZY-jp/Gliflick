package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

data class UiBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
}

/** Logical dp geometry shared by Canvas drawing and tap handling. */
class UpgradeLayout(val width: Float, val height: Float, count: Int) {
    val landscape = width > 660f && width > height * 1.25f
    val compact = height < 440f
    val headerBottom = if (compact) 56f else 74f
    val footerTop = height - 36f
    val reroll = UiBox(width - 140f, height - 32f, width - 12f, height - 6f)
    val cards: List<UiBox> = if (count == 0) emptyList() else if (landscape) {
        val gap = 10f
        val cardWidth = (width - 24f - gap * (count - 1)) / count
        List(count) { UiBox(12f + it * (cardWidth + gap), headerBottom,
            12f + it * (cardWidth + gap) + cardWidth, footerTop - 6f) }
    } else {
        val gap = 6f
        val cardHeight = (footerTop - 6f - headerBottom - gap * (count - 1)) / count
        List(count) { UiBox(12f, headerBottom + it * (cardHeight + gap),
            width - 12f, headerBottom + it * (cardHeight + gap) + cardHeight) }
    }
    fun hitChoice(x: Float, y: Float): Int? = cards.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }
}
