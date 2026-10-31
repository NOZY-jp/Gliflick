package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.data.WordListRepository

class CombatActivity : AppCompatActivity() {
    private lateinit var arena: CombatView
    private lateinit var editor: GameImeEditText
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.menu_background)) }
        arena = CombatView(this, WordListRepository(this).load())
        root.addView(arena, FrameLayout.LayoutParams(-1, -1))
        editor = GameImeEditText(this).apply {
            id = R.id.combatInputHost
            alpha = 0f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            setSingleLine(true)
            setPadding(0, 0, 0, 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            onGameInput = { edit -> arena.session.onInput(edit.tokens, edit.composing) }
            onSpacePressed = arena::handleSpaceKey
        }
        root.addView(editor, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START))
        setContentView(root)
        arena.onInputRequested = ::showIme
        arena.onRestart = {
            editor.inputDecoder.reset()
            editor.text?.clear()
            getSystemService(InputMethodManager::class.java).restartInput(editor)
        }
        arena.onInputBoundary = {
            editor.inputDecoder.reset()
            editor.text?.let(android.view.inputmethod.BaseInputConnection::removeComposingSpans)
            editor.text?.clear()
            getSystemService(InputMethodManager::class.java).restartInput(editor)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, root).isAppearanceLightNavigationBars = true
    }

    override fun onResume() { super.onResume(); resumed = true; arena.start(); editor.post(::showIme) }
    override fun onPause() { resumed = false; arena.stop(); super.onPause() }
    private fun showIme() {
        if (!resumed || arena.session.world.isGameOver) return
        editor.requestFocus()
        WindowInsetsControllerCompat(window, editor).show(WindowInsetsCompat.Type.ime())
    }
}
