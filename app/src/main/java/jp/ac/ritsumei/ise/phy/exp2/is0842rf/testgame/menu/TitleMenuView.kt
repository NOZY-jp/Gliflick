package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.R
import kotlin.math.ceil
import kotlin.math.max

/** The original Figma background, at its natural aspect ratio, extends behind the keyboard. */
class MenuBackdropView(context: Context) : View(context) {
    private val bitmap = BitmapFactory.decodeResource(resources, R.drawable.menu_backdrop)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gridPaint = Paint().apply { color = context.getColor(R.color.menu_grid) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = width / bitmap.width.toFloat()
        val bottom = bitmap.height * unit
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), bottom), bitmapPaint)
        // Taller displays continue the grid without stretching the exported geometry.
        if (height > bottom) {
            gridPaint.strokeWidth = 3f * unit
            for (x in 46..bitmap.width step 198) {
                canvas.drawLine(x * unit, bottom, x * unit, height.toFloat(), gridPaint)
            }
            var y = 213f + ceil((bitmap.height - 213f) / 198f) * 198f
            while (y * unit < height) {
                canvas.drawLine(0f, y * unit, width.toFloat(), y * unit, gridPaint)
                y += 198f
            }
        }
    }
}

/** Width-based Figma proportions with native text, full-row buttons and a scrollable small-screen fallback. */
class TitleMenuView(context: Context) : FrameLayout(context) {
    var onCommandClick: ((MenuCommand) -> Unit)? = null
    private val ink = context.getColor(R.color.menu_ink)
    private val untyped = context.getColor(R.color.menu_untyped)
    private val density = resources.displayMetrics.density
    private val title = TextView(context).apply {
        text = context.getString(R.string.menu_title)
        setTextColor(ink)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = 0.015f
        includeFontPadding = false
    }
    val rows: Map<MenuCommand, TextView> = MenuCommand.entries.associateWith { command ->
        TextView(context).apply {
            id = when (command) {
                MenuCommand.PLAY -> R.id.menuPlay
                MenuCommand.ARMORY -> R.id.menuArmory
                MenuCommand.UPGRADES -> R.id.menuUpgrades
                MenuCommand.OPTIONS -> R.id.menuOptions
            }
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.02f
            includeFontPadding = false
            setTextColor(untyped)
            isFocusable = true
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            background = ContextCompat.getDrawable(context, ripple.resourceId)
            setOnClickListener { onCommandClick?.invoke(command) }
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            }
        }
    }
    init {
        id = R.id.menuContent
        addView(title)
        rows.values.forEach(::addView)
        showInput(MenuInputController().currentInput())
    }

    fun showInput(input: MenuInput) {
        rows.forEach { (command, row) ->
            val label = SpannableString("> ${command.text}")
            label.setSpan(ForegroundColorSpan(ink), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val matched = input.matchedCharacters(command)
            if (matched > 0) {
                label.setSpan(ForegroundColorSpan(ink), 2, 2 + matched,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            row.text = label
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val unit = w / 1080f
        val fontScale = resources.configuration.fontScale
        val rowHeight = max(114f * unit, 48f * density * fontScale)
        val contentHeight = max(1120f * unit, 526f * unit + rowHeight * 4 + 16f * density)
        setMeasuredDimension(w, resolveSize(ceil(contentHeight).toInt(), heightMeasureSpec))
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 148f * unit)
        title.measure(exact(904f * unit), exact(231f * unit))
        rows.values.forEach { row ->
            // Long labels still fit at large accessibility font sizes.
            row.setTextSize(TypedValue.COMPLEX_UNIT_PX, 90f * unit * fontScale.coerceAtMost(1.5f))
            row.measure(exact(838f * unit), exact(rowHeight))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val unit = width / 1080f
        title.place(88f * unit, 191f * unit)
        var rowTop = 526f * unit
        rows.values.forEach { row ->
            row.place(121f * unit, rowTop)
            rowTop += row.measuredHeight
        }
    }

    private fun exact(size: Float) = MeasureSpec.makeMeasureSpec(size.toInt(), MeasureSpec.EXACTLY)
    private fun View.place(x: Float, y: Float) = layout(x.toInt(), y.toInt(),
        x.toInt() + measuredWidth, y.toInt() + measuredHeight)
}
