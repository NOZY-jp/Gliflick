package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.CombatSession
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.model.WordDefinition
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.render.CombatRenderer
import kotlin.math.min

/** UI-thread ownership keeps frame updates and IME callbacks ordered without shared mutable threads. */
class CombatView(context: Context, private val definitions: List<WordDefinition>) : View(context), Choreographer.FrameCallback {
    var session = CombatSession(definitions)
        private set
    var onInputRequested: (() -> Unit)? = null
    var onRestart: (() -> Unit)? = null
    private val renderer = CombatRenderer(context)
    private var running = false
    private var lastFrame = 0L
    private var accumulator = 0f
    private var unit = resources.displayMetrics.density
    private var accessibilityClock = 0f

    init { id = R.id.combatArena; isClickable = true; isFocusable = false }

    fun start() {
        if (running) return
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        accumulator = 0f
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrame != 0L) accumulator += ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrame = frameTimeNanos
        while (accumulator >= STEP) { session.update(STEP); accumulator -= STEP }
        accessibilityClock += STEP
        if (accessibilityClock >= 0.5f) {
            accessibilityClock = 0f
            contentDescription = "リピーター レベル${session.world.level} HP${session.world.player.hp} " +
                session.captions.filter { !it.retired }.joinToString("、") { it.state.inputText }
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        unit = min(resources.displayMetrics.density, h / 360f).coerceAtLeast(0.25f)
        session.resize(w / unit, h / unit)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(unit, unit)
        renderer.draw(canvas, session, width / unit, height / unit)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (session.world.isGameOver) {
            session = CombatSession(definitions)
            session.resize(width / unit, height / unit)
            onRestart?.invoke()
        }
        onInputRequested?.invoke()
        return true
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    companion object { private const val STEP = 1f / 120f }
}
