package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

/** Presentation geometry only; neither matching nor enemy health is changed by overlap. */
data class CaptionTextBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: CaptionTextBox) = left < other.right && other.left < right &&
        top < other.bottom && other.top < bottom
}

data class CaptionPriority(val typing: Boolean, val lostHpRatio: Double, val tier: Int, val internalId: Long)
data class CaptionLayer<T>(val owner: Long, val priority: CaptionPriority,
                           val textBoxes: List<CaptionTextBox>, val content: T)
data class CaptionLayerPresentation<T>(val layer: CaptionLayer<T>, val subdued: Boolean)

object CaptionOverlapPolicy {
    private val priorityOrder = compareByDescending<CaptionPriority> { it.typing }
        .thenByDescending { it.lostHpRatio }
        .thenByDescending { it.tier }
        .thenBy { it.internalId }

    /** Low priority is drawn first. Each lower overlapping label is subdued, not removed. */
    fun <T> arrange(layers: List<CaptionLayer<T>>): List<CaptionLayerPresentation<T>> {
        val highFirst = layers.withIndex().sortedWith { a, b ->
            val priority = priorityOrder.compare(a.value.priority, b.value.priority)
            if (priority != 0) priority else b.index.compareTo(a.index)
        }.map { it.value }
        return highFirst.mapIndexed { index, layer ->
            val covered = highFirst.take(index).any { higher ->
                // An outgoing completion and its replacement deliberately cross-fade together.
                higher.owner != layer.owner && layer.textBoxes.any { box -> higher.textBoxes.any(box::overlaps) }
            }
            CaptionLayerPresentation(layer, covered)
        }.reversed()
    }
}
