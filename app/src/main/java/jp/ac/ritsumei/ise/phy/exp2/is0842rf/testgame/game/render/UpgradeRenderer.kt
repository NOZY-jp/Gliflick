package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.*
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.splitTextCharacters
import java.util.Locale
import kotlin.math.min

class UpgradeRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons = WeaponIconRenderer(context)
    private val ink = Color.rgb(70, 70, 70)
    private val muted = Color.rgb(117, 117, 110)
    private val accent = Color.rgb(158, 91, 96)
    private val face = Typeface.create("sans-serif", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)

    fun draw(canvas: Canvas, session: CombatSession, width: Float, height: Float) {
        val selection = session.upgradeSelection ?: return
        val layout = UpgradeLayout(width, height, selection.offers.size)
        val alpha = (255f * (selection.age / 0.18f).coerceIn(0f, 1f) * (1f - selection.selectionProgress)).toInt()
        val layer = canvas.saveLayerAlpha(0f, 0f, width, height, alpha)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(239, 239, 229)
        canvas.drawRect(0f, 0f, width, height, paint)
        text(canvas, if (selection.evolution) "WEAPON EVOLUTION" else "LEVEL UP", 16f, 17f, 8.5f, accent, bold)
        text(canvas, if (selection.evolution) "リピーターの進化" else "レベル " + selection.level,
            16f, if (layout.compact) 39f else 45f, 21f, ink, bold)
        val summary = "装備 " + session.world.secondaryWeapons.size + "/4"
        text(canvas, summary, width - 16f, 18f, 9.5f, muted, align = Paint.Align.RIGHT)
        val dps = String.format(Locale.US, "%.1f DPS", session.world.dps(WeaponId.REPEATER))
        val dpsBaseline = if (selection.evolution && width < 360f)
            (if (layout.compact) 52f else 62f) else (if (layout.compact) 39f else 45f)
        text(canvas, "リピーター  " + dps, width - 16f, dpsBaseline,
            9.5f, muted, align = Paint.Align.RIGHT)
        selection.offers.forEachIndexed { index, offer ->
            val box = layout.cards[index]
            val selected = selection.selectedIndex == index
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(247, 247, 239)
            canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, 4f, 4f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 1.8f else 0.65f
            paint.color = if (selected) accent else Color.rgb(199, 199, 188)
            canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, 4f, 4f, paint)
            paint.style = Paint.Style.FILL
            icons.draw(canvas, offer.weapon, box.left + 22.5f, box.top + 22.5f, 12.5f,
                accent, offer.evolution)
            val titleSize = if (layout.landscape) 18f else 16f
            fittedText(canvas, if (offer.kind == OfferKind.EVOLUTION) offer.title else offer.weapon.title,
                box.left + 43f, box.top + if (layout.compact) 20f else 23f,
                titleSize, box.width - 118f, bold)
            text(canvas, if (offer.kind == OfferKind.EVOLUTION) "リピーター / 進化" else offer.weapon.tags,
                box.left + 43f, box.top + if (layout.compact) 31f else 35f, 8f, muted)
            text(canvas, offer.kind.title, box.right - 10f, box.top + 13f, 8f, accent, align = Paint.Align.RIGHT)
            if (offer.kind != OfferKind.ACQUIRE)
                text(canvas, offer.rarity.title, box.right - 10f, box.top + 25f, 7.5f, muted, align = Paint.Align.RIGHT)
            val commandHeight = if (layout.compact) 21f else 25f
            val commandTop = box.bottom - commandHeight - 5f
            if (offer.kind != OfferKind.UPGRADE) {
                wrap(canvas, offer.description, box.left + 11f, box.top + if (layout.compact) 42f else 49f,
                    box.width - 22f, if (layout.compact) 10f else 11.5f, commandTop - 3f)
            } else {
                fittedText(canvas, offer.title, box.left + 11f, box.top + if (layout.compact) 42f else 49f,
                    10f, box.width - 22f, face)
                wrap(canvas, offer.description, box.left + 11f, box.top + if (layout.compact) 54f else 62f,
                    box.width - 22f, if (layout.compact) 10f else 11.5f, commandTop - 3f)
            }
            paint.color = Color.rgb(228, 228, 216)
            canvas.drawRoundRect(box.left + 6f, commandTop, box.right - 6f, box.bottom - 5f, 2f, 2f, paint)
            val state = selection.states[index]
            paint.typeface = face
            paint.textSize = if (layout.compact) 14f else 17f
            paint.textAlign = Paint.Align.LEFT
            val baseline = commandTop + commandHeight * 0.72f
            var x = box.centerX - paint.measureText(state.inputText) / 2f
            for ((i, character) in splitTextCharacters(state.inputText).withIndex()) {
                paint.color = if (state.isCompleted || i < state.matchedInputCharacters) ink else Color.rgb(166, 166, 157)
                canvas.drawText(character, x, baseline, paint)
                x += paint.measureText(character)
            }
            if (state.definition.displayName != state.inputText)
                text(canvas, state.definition.displayName, box.right - 11f, baseline - 1f, 8f, muted,
                    align = Paint.Align.RIGHT)
        }
        session.world.secondaryWeapons.forEachIndexed { i, weapon ->
            icons.draw(canvas, weapon.id, 24f + i * 26f, height - 18f, 9f, muted)
        }
        val reroll = layout.reroll
        paint.color = Color.rgb(228, 228, 216)
        canvas.drawRoundRect(reroll.left, reroll.top, reroll.right, reroll.bottom, 3f, 3f, paint)
        text(canvas, "リロール [␣] ×" + session.rerollsRemaining, reroll.centerX, reroll.top + 17.5f, 11f,
            if (session.canRerollUpgrades) ink else Color.rgb(174, 174, 164), align = Paint.Align.CENTER)
        canvas.restoreToCount(layer)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int,
                     typeface: Typeface = face, align: Paint.Align = Paint.Align.LEFT) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.typeface = typeface
        paint.textSize = size; paint.textAlign = align
        canvas.drawText(value, x, y, paint)
    }
    private fun fittedText(canvas: Canvas, value: String, x: Float, y: Float, size: Float, width: Float, typeface: Typeface) {
        paint.typeface = typeface; paint.textSize = size
        val fitted = min(size, size * width.coerceAtLeast(50f) / paint.measureText(value).coerceAtLeast(1f))
        text(canvas, value, x, y, fitted, ink, typeface)
    }
    private fun wrap(canvas: Canvas, value: String, x: Float, baseline: Float, width: Float, size: Float, bottom: Float) {
        paint.typeface = face; paint.textSize = size
        var line = ""; var y = baseline
        for (character in splitTextCharacters(value)) {
            if (paint.measureText(line + character) > width) {
                if (y > bottom) return
                text(canvas, line, x, y, size, muted)
                y += size * 1.3f; line = ""
            }
            line += character
        }
        if (line.isNotEmpty() && y <= bottom) text(canvas, line, x, y, size, muted)
    }
}
