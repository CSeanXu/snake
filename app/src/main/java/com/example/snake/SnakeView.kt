package com.example.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class SnakeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    interface Listener {
        fun onStateChanged(state: GameState, score: Int)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val game = GameLogic(density)

    val state: GameState get() = game.state
    val score: Int get() = game.score

    // ---------- frame loop ----------
    private val choreographer = Choreographer.getInstance()
    private var lastReportedState: GameState? = null
    private var lastReportedScore: Int = -1

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            timeSeconds = (frameTimeNanos - bootNanos) / 1_000_000_000f
            if (game.state == GameState.RUNNING) game.tick()

            // Animate joystick fade.
            val target = if (joystickActive) 1f else 0f
            joystickAlpha += (target - joystickAlpha) * 0.25f

            invalidate()
            maybeReportState()
            choreographer.postFrameCallback(this)
        }
    }
    private var bootNanos = 0L
    private var timeSeconds = 0f

    // ---------- joystick (floating) ----------
    private var joystickActive = false
    private var joystickAlpha = 0f
    private var joyAnchorX = 0f
    private var joyAnchorY = 0f
    private var joyStickX = 0f
    private var joyStickY = 0f

    private val joystickRadius = dp(56f)
    private val joystickStickRadius = dp(26f)
    private val joystickDeadzone = dp(8f)
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    // ---------- paints ----------
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCB8C0CC.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val bodyPath = Path()
    private val viewRect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    // ---------- lifecycle ----------
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bootNanos = System.nanoTime()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        viewRect.set(0f, 0f, w.toFloat(), h.toFloat())
        game.setSize(w.toFloat(), h.toFloat())
        titlePaint.textSize = dp(34f)
        subPaint.textSize = dp(14f)
    }

    // ---------- input ----------
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                onTouchDown(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!joystickActive) return false
                val idx = event.findPointerIndex(pointerId)
                if (idx < 0) return false
                onTouchMove(event.getX(idx), event.getY(idx))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchRelease()
                pointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onTouchDown(x: Float, y: Float) {
        when (game.state) {
            GameState.OVER -> {
                game.reset()
                game.start()
            }
            GameState.READY, GameState.PAUSED -> game.start()
            GameState.RUNNING -> {}
        }
        joystickActive = true
        joyAnchorX = x; joyAnchorY = y
        joyStickX = x; joyStickY = y
        performClick()
    }

    private fun onTouchMove(x: Float, y: Float) {
        var dx = x - joyAnchorX
        var dy = y - joyAnchorY
        val mag = hypot(dx, dy)
        if (mag > joystickRadius && mag > 0f) {
            val k = joystickRadius / mag
            dx *= k; dy *= k
        }
        joyStickX = joyAnchorX + dx
        joyStickY = joyAnchorY + dy
        if (mag > joystickDeadzone) {
            game.setTargetHeading(atan2(dy, dx))
        }
    }

    private fun onTouchRelease() {
        joystickActive = false
        // We don't clear the heading — the snake keeps moving on its last bearing.
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------- public controls ----------
    fun togglePause() {
        when (game.state) {
            GameState.RUNNING -> game.pause()
            GameState.READY, GameState.PAUSED -> game.start()
            GameState.OVER -> {}
        }
        invalidate()
    }

    fun restart() {
        game.reset()
        game.start()
        joystickActive = false
        joystickAlpha = 0f
        invalidate()
    }

    fun pauseIfRunning() {
        if (game.state == GameState.RUNNING) {
            game.pause()
            invalidate()
        }
    }

    private fun maybeReportState() {
        val s = game.state
        val sc = game.score
        if (s != lastReportedState || sc != lastReportedScore) {
            lastReportedState = s
            lastReportedScore = sc
            listener?.onStateChanged(s, sc)
        }
    }

    // ---------- rendering ----------
    override fun onDraw(canvas: Canvas) {
        drawFood(canvas)
        drawSnake(canvas)
        drawJoystick(canvas)
        drawOverlay(canvas)
    }

    private fun drawFood(c: Canvas) {
        val r = game.foodRadius * (1f + 0.10f * sin(timeSeconds * 4f))
        val x = game.foodX
        val y = game.foodY
        // halo
        fillPaint.color = 0x33F472B6
        c.drawCircle(x, y, r * 2.2f, fillPaint)
        fillPaint.color = 0x80F472B6.toInt()
        c.drawCircle(x, y, r * 1.45f, fillPaint)
        // body
        fillPaint.color = 0xFFF472B6.toInt()
        c.drawCircle(x, y, r, fillPaint)
        // sheen
        fillPaint.color = 0xCCFCE7F3.toInt()
        c.drawCircle(x - r * 0.32f, y - r * 0.32f, r * 0.32f, fillPaint)
    }

    private fun drawSnake(c: Canvas) {
        val pts = game.trail
        val n = pts.size
        if (n < 2) return
        val r = game.bodyRadius

        bodyPath.reset()
        bodyPath.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until n) bodyPath.lineTo(pts[i].x, pts[i].y)

        // Glow halos (cheap two-pass fake blur).
        pathPaint.color = 0x334ADE80
        pathPaint.strokeWidth = r * 3.6f
        c.drawPath(bodyPath, pathPaint)
        pathPaint.color = 0x664ADE80
        pathPaint.strokeWidth = r * 2.7f
        c.drawPath(bodyPath, pathPaint)

        // Subtle outline so the body stays defined against bright glow.
        pathPaint.color = 0xFF0E2B17.toInt()
        pathPaint.strokeWidth = r * 2.15f
        c.drawPath(bodyPath, pathPaint)

        // Body fill.
        pathPaint.color = 0xFF4ADE80.toInt()
        pathPaint.strokeWidth = r * 1.95f
        c.drawPath(bodyPath, pathPaint)

        // Head accent: brighter mint mass at the front, plus eyes.
        val headIdx = n - 1
        val head = pts[headIdx]
        fillPaint.color = 0xFF7CFFB2.toInt()
        c.drawCircle(head.x, head.y, r * 1.1f, fillPaint)
        drawEyes(c, head.x, head.y, game.heading, r)
    }

    private fun drawEyes(c: Canvas, hx: Float, hy: Float, heading: Float, r: Float) {
        val cosH = cos(heading); val sinH = sin(heading)
        val perpX = -sinH; val perpY = cosH
        val forward = r * 0.32f
        val side = r * 0.55f
        val eyeR = r * 0.34f
        val pupilR = eyeR * 0.55f

        val ex1 = hx + cosH * forward + perpX * side
        val ey1 = hy + sinH * forward + perpY * side
        val ex2 = hx + cosH * forward - perpX * side
        val ey2 = hy + sinH * forward - perpY * side

        fillPaint.color = Color.WHITE
        c.drawCircle(ex1, ey1, eyeR, fillPaint)
        c.drawCircle(ex2, ey2, eyeR, fillPaint)

        val pupilOff = eyeR * 0.45f
        fillPaint.color = 0xFF0A0E14.toInt()
        c.drawCircle(ex1 + cosH * pupilOff, ey1 + sinH * pupilOff, pupilR, fillPaint)
        c.drawCircle(ex2 + cosH * pupilOff, ey2 + sinH * pupilOff, pupilR, fillPaint)
    }

    private fun drawJoystick(c: Canvas) {
        if (joystickAlpha < 0.02f) return
        val a = joystickAlpha.coerceIn(0f, 1f)

        // Outer ring.
        pathPaint.style = Paint.Style.STROKE
        pathPaint.color = colorAlpha(0xFFFFFFFF.toInt(), 0.28f * a)
        pathPaint.strokeWidth = dp(2f)
        c.drawCircle(joyAnchorX, joyAnchorY, joystickRadius, pathPaint)
        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeCap = Paint.Cap.ROUND

        // Translucent fill inside ring.
        fillPaint.color = colorAlpha(0xFFFFFFFF.toInt(), 0.06f * a)
        c.drawCircle(joyAnchorX, joyAnchorY, joystickRadius - dp(1f), fillPaint)

        // Stick — soft outer + bright core.
        fillPaint.color = colorAlpha(0xFFFFFFFF.toInt(), 0.35f * a)
        c.drawCircle(joyStickX, joyStickY, joystickStickRadius, fillPaint)
        fillPaint.color = colorAlpha(0xFFFFFFFF.toInt(), 0.95f * a)
        c.drawCircle(joyStickX, joyStickY, joystickStickRadius * 0.65f, fillPaint)
    }

    private fun drawOverlay(c: Canvas) {
        val (title, sub) = when (game.state) {
            GameState.READY -> resources.getString(R.string.tap_to_start) to resources.getString(R.string.hint_drag)
            GameState.PAUSED -> resources.getString(R.string.pause) to resources.getString(R.string.tap_to_start)
            GameState.OVER -> resources.getString(R.string.game_over) to resources.getString(R.string.tap_restart)
            GameState.RUNNING -> return
        }
        overlayPaint.color = 0xB30A0E14.toInt()
        c.drawRect(viewRect, overlayPaint)
        val cx = width / 2f
        val cy = height / 2f
        c.drawText(title, cx, cy, titlePaint)
        c.drawText(sub, cx, cy + dp(28f), subPaint)
    }

    // ---------- helpers ----------
    private fun dp(v: Float): Float = v * density

    private fun colorAlpha(argb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (a shl 24) or (argb and 0x00FFFFFF)
    }
}
