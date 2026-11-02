package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuBackdropView
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuCommand
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.TitleMenuView
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.menu.MenuInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.InputToken

/** Title interaction shares keyboard transport and recognition with gameplay. */
class TitleActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var backdrop: MenuBackdropView
    private lateinit var content: TitleMenuView
    private lateinit var viewport: ScrollView
    private lateinit var editor: GameImeEditText
    private var menuController = MenuInputController()
    private var menuInput = menuController.currentInput()
    private var resumed = false
    private var selecting = false
    private var navigationInProgress = false
    private var typingTick: Runnable? = null
    private var zoom: ValueAnimator? = null
    private var overlay: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.menu_background)) }
        backdrop = MenuBackdropView(this)
        root.addView(backdrop, fill())
        content = TitleMenuView(this)
        viewport = LockedScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(viewport, fill())
        editor = GameImeEditText(this).apply {
            id = R.id.menuInputHost
            alpha = 0f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_FORCE_ASCII or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            contentDescription = getString(R.string.menu_input_accessibility)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, 0, 0, 0)
        }
        root.addView(editor, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START))
        setContentView(root)

        content.onCommandClick = { command ->
            if (!selecting && command.selectable) startTap(command)
        }
        content.onLaunchImeClick = { requestMenuInput() }
        editor.onGameInput = { edit ->
            if (selecting) false
            else {
                menuInput = menuController.onInputTokens(edit.tokens, edit.composing)
                content.showInput(menuInput)
                val completed = menuInput.completedCommand
                if (completed != null && resumed) {
                    selecting = true
                    animateSelection(completed)
                }
                completed != null
            }
        }
        // Shared transport finishes any composition before invoking this callback.
        // Menus use a literal space in "keyboard test"; combat retains its Space action.
        editor.onSpacePressed = {
            if (!selecting) {
                menuInput = menuController.onInputTokens(listOf(InputToken.Stroke(" ")), false)
                content.showInput(menuInput)
            }
            true
        }
        val restored = savedInstanceState?.getString("menu_prefix").orEmpty()
        if (restored.isNotEmpty()) {
            menuInput = menuController.onInputTokens(restored.map { InputToken.Stroke(it.toString()) }, false)
        }
        content.showInput(menuInput)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            // Figma coordinates start at the screen edge; keeping a status-bar top inset
            // here shifts the whole composition down by the height of the status bar.
            viewport.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, keyboard.bottom))
            content.setImeVisible(keyboard.bottom > bars.bottom)
            insets
        }
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, root).isAppearanceLightNavigationBars = true
    }

    private fun startTap(command: MenuCommand) {
        selecting = true
        var count = menuInput.matchedCharacters(command)
        menuController = MenuInputController()
        menuInput = menuController.onInputTokens(command.text.take(count).map {
            InputToken.Stroke(it.toString())
        }, false)
        content.showInput(menuInput)
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
            navigate(command)
            return
        }
        val tick = object : Runnable {
            override fun run() {
                if (!resumed || !selecting) return
                menuInput = menuController.onInputTokens(
                    listOf(InputToken.Stroke(command.text[count++].toString())), false)
                content.showInput(menuInput)
                if (count < command.text.length) root.postDelayed(this, TYPE_INTERVAL_MS)
                else {
                    typingTick = null
                    animateSelection(command)
                }
            }
        }
        typingTick = tick
        root.postDelayed(tick, TYPE_INTERVAL_MS)
    }

    private fun animateSelection(command: MenuCommand) {
        if (!resumed) return
        val row = content.rows.getValue(command)
        val rootLocation = IntArray(2)
        val rowLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        row.getLocationInWindow(rowLocation)
        val word = TextView(this).apply {
            id = R.id.menuSelectionOverlay
            text = command.text
            setTextColor(getColor(R.color.menu_ink))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, row.textSize)
            typeface = row.typeface
            letterSpacing = row.letterSpacing
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        word.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val startX = rowLocation[0] - rootLocation[0] + row.paint.measureText("> ")
        val startY = rowLocation[1] - rootLocation[1] + (row.height - word.measuredHeight) / 2f
        root.addView(word, FrameLayout.LayoutParams(word.measuredWidth, word.measuredHeight))
        word.x = startX
        word.y = startY
        word.pivotX = word.measuredWidth / 2f
        word.pivotY = word.measuredHeight / 2f
        word.alpha = 1f
        overlay = word
        row.alpha = 0f
        var cancelled = false
        zoom = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ZOOM_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val amount = animation.animatedValue as Float
                word.scaleX = 1f + amount
                word.scaleY = 1f + amount
                word.alpha = 1f - amount
                content.alpha = 1f - amount
                backdrop.transitionProgress = amount
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    zoom = null
                    if (!cancelled && resumed) navigate(command)
                }
            })
            start()
        }
    }

    private fun navigate(command: MenuCommand) {
        navigationInProgress = true
        if (command == MenuCommand.QUIT) {
            finishAndRemoveTask()
            return
        }
        val destination = when (command) {
            MenuCommand.START -> CombatActivity::class.java
            MenuCommand.KEYBOARD_TEST -> MainActivity::class.java
            else -> return
        }
        startActivity(Intent(this, destination)
            .putExtra(MainActivity.EXTRA_MENU_COMMAND, command.text))
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (navigationInProgress) {
            navigationInProgress = false
            resetMenuSelection()
        }
        requestMenuInput()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && resumed && !selecting) requestMenuInput()
    }

    private fun requestMenuInput() {
        editor.post {
            if (resumed) {
                editor.requestFocus()
                WindowInsetsControllerCompat(window, editor).show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    override fun onPause() {
        resumed = false
        typingTick?.let(root::removeCallbacks)
        typingTick = null
        if (!navigationInProgress) {
            // Restoring the menu here exposes the fully drawn shapes for a frame
            // while Android is still covering this window with the destination.
            zoom?.cancel()
            zoom = null
            resetMenuSelection()
        }
        super.onPause()
    }

    private fun resetMenuSelection() {
        overlay?.let(root::removeView)
        overlay = null
        if (selecting) {
            menuController = MenuInputController()
            menuInput = menuController.currentInput()
            editor.inputDecoder.reset()
            editor.text?.clear()
            content.showInput(menuInput)
        }
        content.alpha = 1f
        backdrop.transitionProgress = 0f
        content.rows.values.forEach { it.alpha = 1f }
        selecting = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("menu_prefix", if (selecting) "" else menuInput.receivedText)
        super.onSaveInstanceState(outState)
    }

    private fun fill() = FrameLayout.LayoutParams(-1, -1)

    companion object {
        const val TYPE_INTERVAL_MS = 50L
        const val ZOOM_DURATION_MS = 420L
    }
}

/** Keeps the title composition fixed while allowing its menu children to receive taps. */
private class LockedScrollView(context: android.content.Context) : ScrollView(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
    override fun onTouchEvent(ev: MotionEvent): Boolean = false
    override fun requestChildRectangleOnScreen(child: View, rectangle: android.graphics.Rect,
                                               immediate: Boolean): Boolean = false
    override fun scrollTo(x: Int, y: Int) = super.scrollTo(x, 0)
}
