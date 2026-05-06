package com.example.snake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
    private var backgroundBitmap: Bitmap? = null

    // ---------- joystick (floating) ----------
    private var joystickActive = false
    private var joystickAlpha = 0f
    private var joyAnchorX = 0f
    private var joyAnchorY = 0f
    private var joyStickX = 0f
    private var joyStickY = 0f

    private val joystickRadius = dp(56f)
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
        color = 0xFF006A45.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9CA493.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

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
        backgroundBitmap?.recycle()
        backgroundBitmap = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        viewRect.set(0f, 0f, w.toFloat(), h.toFloat())
        backgroundBitmap?.recycle()
        backgroundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            drawBackgroundPattern(Canvas(it), w.toFloat(), h.toFloat())
        }
        game.setSize(w.toFloat(), h.toFloat())
        titlePaint.textSize = dp(28f)
        subPaint.textSize = dp(11f)
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
        drawBackground(canvas)
        drawFood(canvas)
        drawSnake(canvas)
        drawJoystick(canvas)
        drawOverlay(canvas)
    }

    private fun drawBackground(c: Canvas) {
        backgroundBitmap?.let {
            c.drawBitmap(it, 0f, 0f, null)
            return
        }
        drawBackgroundPattern(c, width.toFloat(), height.toFloat())
    }

    private fun drawBackgroundPattern(c: Canvas, canvasWidth: Float, canvasHeight: Float) {
        c.drawColor(0xFFF7F8EC.toInt())

        val spacing = dp(24f)
        val dotRadius = max(1f, dp(1.05f))
        fillPaint.color = 0xFFDCE1CF.toInt()

        var y = dp(3f)
        while (y < canvasHeight) {
            var x = dp(3f)
            while (x < canvasWidth) {
                c.drawCircle(x, y, dotRadius, fillPaint)
                x += spacing
            }
            y += spacing
        }
    }

    private fun drawFood(c: Canvas) {
        val r = game.foodRadius * (1f + 0.10f * sin(timeSeconds * 4f))
        val x = game.foodX
        val y = game.foodY

        fillPaint.color = 0x22C91822
        c.drawCircle(x, y, r * 2.2f, fillPaint)
        fillPaint.color = 0x66C91822
        c.drawCircle(x, y, r * 1.45f, fillPaint)

        fillPaint.color = 0xFFC91822.toInt()
        c.drawCircle(x, y, r, fillPaint)

        fillPaint.color = 0xCCEFA5A5.toInt()
        c.drawCircle(x - r * 0.32f, y - r * 0.32f, r * 0.32f, fillPaint)
    }

    private fun drawSnake(c: Canvas) {
        val pts = game.trail
        val n = pts.size
        if (n < 2) return

        val segmentRadius = game.bodyRadius * 1.3f
        val sampleDistance = segmentRadius * 0.82f
        var lastX = Float.NaN
        var lastY = Float.NaN

        for (i in 0 until n) {
            val p = pts[i]
            val forceHead = i == n - 1
            if (!forceHead && !lastX.isNaN() && hypot(p.x - lastX, p.y - lastY) < sampleDistance) {
                continue
            }

            val t = i / (n - 1f)
            val radius = segmentRadius * (0.58f + 0.42f * t)
            val alpha = 0.25f + 0.75f * t
            val bodyColor = blendColor(0xFFCFFBE7.toInt(), 0xFF4AA98E.toInt(), t)

            fillPaint.color = colorAlpha(bodyColor, 0.18f * alpha)
            c.drawCircle(p.x, p.y, radius * 1.5f, fillPaint)

            fillPaint.color = colorAlpha(bodyColor, alpha)
            c.drawCircle(p.x, p.y, radius, fillPaint)

            lastX = p.x
            lastY = p.y
        }

        val head = pts[n - 1]
        fillPaint.color = 0x3357B49B
        c.drawCircle(head.x, head.y, segmentRadius * 1.48f, fillPaint)
        fillPaint.color = 0xFF4EA58E.toInt()
        c.drawCircle(head.x, head.y, segmentRadius * 1.1f, fillPaint)
        drawEyes(c, head.x, head.y, game.heading, segmentRadius * 1.1f)
    }

    private fun drawEyes(c: Canvas, hx: Float, hy: Float, heading: Float, r: Float) {
        val cosH = cos(heading); val sinH = sin(heading)
        val perpX = -sinH; val perpY = cosH
        val forward = r * 0.18f
        val side = r * 0.42f
        val eyeR = r * 0.11f

        val ex1 = hx + cosH * forward + perpX * side
        val ey1 = hy + sinH * forward + perpY * side
        val ex2 = hx + cosH * forward - perpX * side
        val ey2 = hy + sinH * forward - perpY * side

        fillPaint.color = 0xFF283B35.toInt()
        c.drawCircle(ex1, ey1, eyeR, fillPaint)
        c.drawCircle(ex2, ey2, eyeR, fillPaint)

        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeCap = Paint.Cap.ROUND
        pathPaint.strokeWidth = r * 0.08f
        pathPaint.color = 0x99283B35.toInt()
        val mx = hx + cosH * r * 0.44f
        val my = hy + sinH * r * 0.44f
        c.drawLine(
            mx - perpX * r * 0.14f,
            my - perpY * r * 0.14f,
            mx + perpX * r * 0.14f,
            my + perpY * r * 0.14f,
            pathPaint
        )
    }

    private fun drawJoystick(c: Canvas) {
        val baseX = width / 2f
        val baseY = (height - dp(86f)).coerceAtLeast(dp(124f))
        val outerRadius = dp(46f)
        val stickRadius = dp(23f)
        val a = 0.56f + joystickAlpha.coerceIn(0f, 1f) * 0.38f

        var dx = joyStickX - joyAnchorX
        var dy = joyStickY - joyAnchorY
        val mag = hypot(dx, dy)
        val maxOffset = outerRadius - stickRadius * 0.35f
        if (mag > maxOffset && mag > 0f) {
            val k = maxOffset / mag
            dx *= k
            dy *= k
        }
        if (!joystickActive && joystickAlpha < 0.04f) {
            dx = 0f
            dy = 0f
        }

        pathPaint.style = Paint.Style.STROKE
        pathPaint.color = colorAlpha(0xFFB8EFD6.toInt(), 0.72f * a)
        pathPaint.strokeWidth = dp(1.8f)
        c.drawCircle(baseX, baseY, outerRadius, pathPaint)
        pathPaint.strokeCap = Paint.Cap.ROUND

        fillPaint.color = colorAlpha(0xFFFFFFFF.toInt(), 0.28f * a)
        c.drawCircle(baseX, baseY, outerRadius - dp(1f), fillPaint)

        fillPaint.color = colorAlpha(0xFFB9F5DC.toInt(), 0.38f * a)
        c.drawCircle(baseX + dx, baseY + dy, stickRadius * 1.15f, fillPaint)
        fillPaint.color = colorAlpha(0xFFFDFEF7.toInt(), 0.92f * a)
        c.drawCircle(baseX + dx, baseY + dy, stickRadius * 0.76f, fillPaint)

        subPaint.color = 0xFFABB3A4.toInt()
        subPaint.textSize = dp(10f)
        val labelY = min(height - dp(20f), baseY + outerRadius + dp(23f))
        c.drawText(resources.getString(R.string.sensor_active), baseX, labelY, subPaint)
    }

    private fun drawOverlay(c: Canvas) {
        val (title, sub) = when (game.state) {
            GameState.READY -> resources.getString(R.string.tap_to_start) to resources.getString(R.string.hint_drag)
            GameState.PAUSED -> resources.getString(R.string.pause) to resources.getString(R.string.tap_to_start)
            GameState.OVER -> resources.getString(R.string.game_over) to resources.getString(R.string.tap_restart)
            GameState.RUNNING -> return
        }
        overlayPaint.color = 0xCCF7F8EC.toInt()
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

    private fun blendColor(start: Int, end: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val sr = (start shr 16) and 0xFF
        val sg = (start shr 8) and 0xFF
        val sb = start and 0xFF
        val er = (end shr 16) and 0xFF
        val eg = (end shr 8) and 0xFF
        val eb = end and 0xFF
        val r = (sr + (er - sr) * t).toInt()
        val g = (sg + (eg - sg) * t).toInt()
        val b = (sb + (eb - sb) * t).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

}
