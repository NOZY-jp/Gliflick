package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.data.WordListRepository
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.GameInputController
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.ImeInputTrace
import android.content.pm.ApplicationInfo

private const val TAG = "Gliflick"

open class MainActivity : AppCompatActivity() {
    private lateinit var imeInputHost: GameImeEditText
    private lateinit var receivedInputText: TextView
    private lateinit var defeatedCountText: TextView
    private lateinit var streakText: TextView
    private lateinit var mistypeCountText: TextView
    private lateinit var surfaceView: MySurfaceView
    private lateinit var gameInputController: GameInputController

    private var activityIsResumed = false
    private var imeWasVisible = false
    private var imeReopenIsQueued = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        intent.getStringExtra(EXTRA_MENU_COMMAND)?.let { command ->
            findViewById<TextView>(R.id.inputTitle).text =
                getString(R.string.keyboard_input_from_menu, command)
        }

        imeInputHost = findViewById(R.id.imeInputHost)
        receivedInputText = findViewById(R.id.receivedInputText)
        defeatedCountText = findViewById(R.id.defeatedCountText)
        streakText = findViewById(R.id.streakText)
        mistypeCountText = findViewById(R.id.mistypeCountText)
        surfaceView = findViewById(R.id.mySurfaceView)
        gameInputController = createGameInputController()
        surfaceView.showWords(gameInputController.currentWords())

        imeInputHost.onGameInput = { edit ->
            val update = gameInputController.onInputTokens(edit.tokens, edit.composing, edit.inputText)
            receivedInputText.text = if (update.receivedText.isEmpty()) {
                getString(R.string.keyboard_input_empty)
            } else {
                getString(R.string.keyboard_input_received, update.receivedText)
            }
            update.completedWords.forEach { word ->
                Log.i(TAG, "Word completed: ${word.text}")
            }
            update.overlapEvents.forEach { event ->
                Log.i(TAG, "Shared input: ${event.words.map { it.text }}")
            }
            defeatedCountText.text = getString(R.string.defeated_count, update.defeatedCount)
            streakText.text = getString(R.string.streak_count, update.streak)
            mistypeCountText.text = getString(R.string.mistype_count, update.mistypeCount)
            surfaceView.showWords(update.words)
            imeInputHost.traceSink?.invoke("gameUpdate", "received=${update.receivedText};count=${update.defeatedCount};streak=${update.streak};mistypes=${update.mistypeCount};words=${update.words}")
            update.completedWords.isNotEmpty()
        }
        if (intent.getBooleanExtra("trace_input", false) &&
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val trace = ImeInputTrace(this, imeInputHost, intent.getBooleanExtra("clear_trace", false))
            imeInputHost.traceSink = trace::record
            findViewById<TextView>(R.id.inputTitle).text = "入力記録中"
        }
        imeInputHost.setOnEditorActionListener { _, _, _ -> true }

        val rootView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, imeInsets.bottom)
            )

            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                imeWasVisible = true
                imeReopenIsQueued = false
            } else if (activityIsResumed && imeWasVisible && !imeReopenIsQueued) {
                imeReopenIsQueued = true
                view.postDelayed({
                    imeReopenIsQueued = false
                    val latestInsets = ViewCompat.getRootWindowInsets(view)
                    if (activityIsResumed && latestInsets?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
                        showIme()
                    }
                }, 250L)
            }
            insets
        }
    }

    protected open fun createGameInputController(): GameInputController =
        GameInputController(WordListRepository(this).load())

    override fun onResume() {
        super.onResume()
        activityIsResumed = true
        imeInputHost.post { showIme() }
    }

    override fun onPause() {
        activityIsResumed = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && activityIsResumed) {
            val rootInsets = ViewCompat.getRootWindowInsets(imeInputHost)
            if (rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
                imeInputHost.post { showIme() }
            }
        }
    }

    private fun showIme() {
        if (!activityIsResumed) return
        imeInputHost.requestFocus()
        WindowInsetsControllerCompat(window, imeInputHost).show(WindowInsetsCompat.Type.ime())
    }

    companion object {
        const val EXTRA_MENU_COMMAND = "menu_command"
    }

}
