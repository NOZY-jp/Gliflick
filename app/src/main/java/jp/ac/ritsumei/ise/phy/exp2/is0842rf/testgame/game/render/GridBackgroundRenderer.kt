package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R

/** Draws the same width-anchored transparent Figma grid used by the title backdrop. */
class GridBackgroundRenderer(context: Context) {
    private val bitmap: Bitmap = BitmapFactory.decodeResource(
        context.resources,
        R.drawable.menu_grid,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ALPHA_8 }
    ) ?: error("The title grid PNG could not be decoded")
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        color = context.getColor(R.color.menu_grid)
    }

    fun draw(canvas: Canvas, viewWidth: Float) {
        if (viewWidth <= 0f) return
        val scale = viewWidth / DESIGN_WIDTH
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, DESIGN_WIDTH, bitmap.height.toFloat()), paint)
        canvas.restore()
    }

    private companion object { const val DESIGN_WIDTH = 1080f }
}
