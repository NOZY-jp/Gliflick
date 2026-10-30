package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordMatchState
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render.WordListCanvasRenderer

/** Canvas surface that redraws the current word states on demand. */
class MySurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val drawLock = Any()
    private val renderer = WordListCanvasRenderer(resources.displayMetrics.density)

    @Volatile
    private var words: List<WordMatchState> = emptyList()

    init {
        holder.addCallback(this)
    }

    fun showWords(wordStates: List<WordMatchState>) {
        words = wordStates
        drawCurrentFrame()
    }

    fun currentWords(): List<WordMatchState> = words

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawCurrentFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawCurrentFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun drawCurrentFrame() {
        synchronized(drawLock) {
            if (!holder.surface.isValid) return
            val canvas: Canvas = holder.lockCanvas() ?: return
            try {
                renderer.draw(canvas, words)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
