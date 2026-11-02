package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render.*
import org.junit.Assert.*
import org.junit.Test

class CaptionOverlapPolicyTest {
    private val box = CaptionTextBox(10f, 10f, 80f, 30f)
    private fun layer(id: Long, typing: Boolean = false, lost: Double = 0.0, tier: Int = 1,
                      boxes: List<CaptionTextBox> = listOf(box), content: String = "$id") =
        CaptionLayer(id, CaptionPriority(typing, lost, tier, id), boxes, content)
    @Test fun rankingIsTypingThenLostHpFractionThenTierThenNumericId() {
        val input = listOf(layer(2), layer(10), layer(50, tier = 3), layer(70, lost = 0.6),
            layer(80, lost = 0.9), layer(90, typing = true))
        for (order in listOf(input, input.reversed())) {
            val result = CaptionOverlapPolicy.arrange(order)
            assertEquals(listOf(10L, 2L, 50L, 70L, 80L, 90L), result.map { it.layer.owner })
            assertTrue(result.dropLast(1).all { it.subdued })
            assertFalse(result.last().subdued)
        }
    }
    @Test fun readingAndFormalRowsUseTheirOwnBoxesAndTouchingEdgesDoNotOverlap() {
        val upper = layer(1, boxes = listOf(box, CaptionTextBox(10f, 40f, 80f, 50f)))
        val gap = layer(2, boxes = listOf(CaptionTextBox(10f, 30f, 80f, 40f)))
        assertTrue(CaptionOverlapPolicy.arrange(listOf(upper, gap)).none { it.subdued })
        val crossing = layer(3, boxes = listOf(CaptionTextBox(70f, 45f, 90f, 60f)))
        assertTrue(CaptionOverlapPolicy.arrange(listOf(upper, crossing)).first().subdued)
    }
    @Test fun sameEnemyReplacementCrossfadeKeepsBothLabelsAndTheirOriginalOrder() {
        val result = CaptionOverlapPolicy.arrange(listOf(layer(1, content = "new"), layer(1, content = "completed")))
        assertTrue(result.none { it.subdued })
        assertEquals(listOf("new", "completed"), result.map { it.layer.content })
    }
    @Test fun nonOverlappingLabelsStayFullyVisible() {
        val result = CaptionOverlapPolicy.arrange(listOf(layer(1),
            layer(2, boxes = listOf(CaptionTextBox(90f, 10f, 120f, 30f)))))
        assertTrue(result.none { it.subdued })
    }
}
