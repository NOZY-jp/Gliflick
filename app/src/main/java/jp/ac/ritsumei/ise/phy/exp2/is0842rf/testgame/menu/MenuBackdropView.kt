package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A rasterized Figma grid under two independently animated native outline groups. */
class MenuBackdropView(context: Context) : View(context) {
    var transitionProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val gridBitmap: Bitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.menu_grid,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ALPHA_8 }
    ) ?: error("The title grid PNG could not be decoded")
    private val gridPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        color = context.getColor(R.color.menu_grid)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.menu_grid)
        style = Paint.Style.STROKE
    }
    private var circleExitScale = 1f
    private var squareExitScale = 1f

    init { id = R.id.menuBackdrop }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0) return
        val designHeight = h * DESIGN_WIDTH / w
        // Put the farthest viewport corner inside each outline's inner boundary.
        // This makes the outlines actually leave the screen, including on tall displays.
        circleExitScale = max(1f, (farthestCorner(1208f, -169f, designHeight) + 40f) / 977f)
        squareExitScale = max(1f, (farthestCorner(881f, 988f, designHeight) + 40f) / 246f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        val unit = width / DESIGN_WIDTH
        canvas.save()
        canvas.scale(unit, unit)
        canvas.drawBitmap(gridBitmap, null,
            RectF(0f, 0f, DESIGN_WIDTH, gridBitmap.height.toFloat()), gridPaint)

        outlinePaint.alpha = (255f * (1f - transitionProgress)).roundToInt()
        if (outlinePaint.alpha > 0) {
            canvas.save()
            val circleScale = 1f + (circleExitScale - 1f) * transitionProgress
            canvas.scale(circleScale, circleScale, 1208f, -169f)
            outlinePaint.strokeWidth = 35f
            canvas.drawCircle(1208f, -169f, 1041.5f, outlinePaint)
            outlinePaint.strokeWidth = 12f
            canvas.drawCircle(1208f, -169f, 983f, outlinePaint)
            canvas.restore()

            canvas.save()
            val squareScale = 1f + (squareExitScale - 1f) * transitionProgress
            canvas.scale(squareScale, squareScale, 881f, 988f)
            drawSquare(canvas, 834.642f, 581.779f, 578.212f, 31f)
            drawSquare(canvas, 840.963f, 634.128f, 504.256f, 10f)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawSquare(canvas: Canvas, x: Float, y: Float, size: Float, stroke: Float) {
        canvas.save()
        canvas.rotate(38.4896f, x, y)
        outlinePaint.strokeWidth = stroke
        canvas.drawRect(x, y, x + size, y + size, outlinePaint)
        canvas.restore()
    }

    private fun farthestCorner(cx: Float, cy: Float, designHeight: Float): Float =
        hypot(max(abs(cx), abs(DESIGN_WIDTH - cx)), max(abs(cy), abs(designHeight - cy)))

    companion object { private const val DESIGN_WIDTH = 1080f }
}
