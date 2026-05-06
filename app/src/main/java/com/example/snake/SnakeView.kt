package com.example.snake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
    val length: Int get() = game.trail.size

    // ---------- frame loop ----------
    private val choreographer = Choreographer.getInstance()
    private var lastReportedState: GameState? = null
    private var lastReportedScore: Int = -1

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            timeSeconds = (frameTimeNanos - bootNanos) / 1_000_000_000f
            if (game.state == GameState.RUNNING) game.tick()

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

    // ---------- joystick (floating + idle preview) ----------
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
        color = COLOR_TEXT
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_MUTED
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val viewRect = RectF()
    private val tonguePath = Path()

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
        drawTrailParticles(canvas)
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

    /** Dense fine grid (main lines + sub-grid) + warm corner glow.
     * Background gradient comes from the parent ConstraintLayout drawable
     * so we leave the bitmap transparent under the lines. */
    private fun drawBackgroundPattern(c: Canvas, canvasWidth: Float, canvasHeight: Float) {
        // sub-grid: 5dp pitch, very faint
        val sub = dp(5f)
        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeWidth = max(0.5f, dp(0.5f))
        pathPaint.color = COLOR_GRID_SUB
        var x = 0f
        while (x < canvasWidth) {
            c.drawLine(x, 0f, x, canvasHeight, pathPaint)
            x += sub
        }
        var y = 0f
        while (y < canvasHeight) {
            c.drawLine(0f, y, canvasWidth, y, pathPaint)
            y += sub
        }

        // main grid: 20dp pitch
        val main = dp(20f)
        pathPaint.strokeWidth = max(0.7f, dp(0.8f))
        pathPaint.color = COLOR_GRID
        x = 0f
        while (x < canvasWidth) {
            c.drawLine(x, 0f, x, canvasHeight, pathPaint)
            x += main
        }
        y = 0f
        while (y < canvasHeight) {
            c.drawLine(0f, y, canvasWidth, y, pathPaint)
            y += main
        }

        // warm corner highlight (top-right by default; right side in landscape too)
        val gx = canvasWidth * 0.72f
        val gy = canvasHeight * 0.18f
        val gr = max(canvasWidth, canvasHeight) * 0.55f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                gx, gy, gr,
                0x66FFEFC8.toInt(), 0x00FFEFC8.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, canvasWidth, canvasHeight, glowPaint)
    }

    /** Apricot energy orb with layered glow halos. */
    private fun drawFood(c: Canvas) {
        val pulse = 1f + 0.08f * sin(timeSeconds * 4f)
        val r = game.foodRadius * pulse
        val x = game.foodX
        val y = game.foodY

        fillPaint.color = colorAlpha(COLOR_FOOD_GLOW, 0.18f)
        c.drawCircle(x, y, r * 2.4f, fillPaint)
        fillPaint.color = colorAlpha(COLOR_FOOD_GLOW, 0.32f)
        c.drawCircle(x, y, r * 1.5f, fillPaint)

        fillPaint.color = COLOR_FOOD
        c.drawCircle(x, y, r, fillPaint)

        // sheen highlight
        fillPaint.color = colorAlpha(Color.WHITE, 0.7f)
        c.drawCircle(x - r * 0.3f, y - r * 0.35f, r * 0.32f, fillPaint)
    }

    /** Fading particle ghosts streaming off the tail. */
    private fun drawTrailParticles(c: Canvas) {
        val pts = game.trail
        if (pts.size < 4) return
        // tangent at the tail
        val tail = pts[0]
        val near = pts[3]
        var tx = tail.x - near.x
        var ty = tail.y - near.y
        val mag = hypot(tx, ty)
        if (mag <= 0.001f) return
        tx /= mag; ty /= mag

        val particles = 7
        val baseR = game.bodyRadius * 0.42f
        val step = game.bodyRadius * 1.2f
        for (i in 0 until particles) {
            val k = i / particles.toFloat()
            val drift = 0.4f * sin(timeSeconds * 1.6f + i * 0.7f)
            val ox = -ty * drift * game.bodyRadius
            val oy = tx * drift * game.bodyRadius
            val px = tail.x + tx * (step + i * step) + ox
            val py = tail.y + ty * (step + i * step) + oy
            val rr = baseR * (1f - k * 0.85f)
            val alpha = 0.55f * (1f - k * 0.95f)
            fillPaint.color = colorAlpha(COLOR_BODY_TAIL, alpha)
            c.drawCircle(px, py, rr, fillPaint)
        }
    }

    private fun drawSnake(c: Canvas) {
        val pts = game.trail
        val n = pts.size
        if (n < 2) return

        val segmentRadius = game.bodyRadius * 1.3f
        val sampleDistance = segmentRadius * 0.82f
        var lastX = Float.NaN
        var lastY = Float.NaN

        // body — overlapping circles, tail-first (head ends up on top)
        for (i in 0 until n) {
            val p = pts[i]
            val forceHead = i == n - 1
            if (!forceHead && !lastX.isNaN() && hypot(p.x - lastX, p.y - lastY) < sampleDistance) {
                continue
            }

            val t = i / (n - 1f)            // 0 = tail, 1 = head
            val tFromHead = 1f - t           // ramp using head-relative t
            val radius = segmentRadius * (0.55f + 0.45f * t)
            val color = bodyColorAt(tFromHead)

            // soft outer halo
            fillPaint.color = colorAlpha(color, 0.16f)
            c.drawCircle(p.x, p.y, radius * 1.5f, fillPaint)

            fillPaint.color = color
            c.drawCircle(p.x, p.y, radius, fillPaint)

            lastX = p.x
            lastY = p.y
        }

        val head = pts[n - 1]
        drawSnakeHead(c, head.x, head.y, game.heading, segmentRadius * 1.15f)
    }

    /** head fill is the brightest mint; tail end fades to deep sage */
    private fun bodyColorAt(t: Float): Int {
        // t: 0 = head, 1 = tail
        return when {
            t < 0.4f -> COLOR_BODY_TOP
            t < 0.75f -> COLOR_BODY_MID
            else -> COLOR_BODY_TAIL
        }
    }

    private fun drawSnakeHead(c: Canvas, x: Float, y: Float, dir: Float, r: Float) {
        val cosH = cos(dir); val sinH = sin(dir)
        val perpX = -sinH; val perpY = cosH

        // round head
        fillPaint.color = COLOR_HEAD
        c.drawCircle(x, y, r, fillPaint)

        // pink cheeks (placed slightly behind eyes, low opacity)
        val cheekFwd = r * 0.08f
        val cheekSide = r * 0.62f
        val cheekR = r * 0.18f
        val cx1 = x + cosH * cheekFwd + perpX * cheekSide
        val cy1 = y + sinH * cheekFwd + perpY * cheekSide
        val cx2 = x + cosH * cheekFwd - perpX * cheekSide
        val cy2 = y + sinH * cheekFwd - perpY * cheekSide
        fillPaint.color = colorAlpha(COLOR_CHEEK, 0.55f)
        c.drawCircle(cx1, cy1, cheekR, fillPaint)
        c.drawCircle(cx2, cy2, cheekR, fillPaint)

        // eyes: white sclera + dark pupil + tiny white highlight
        val eyeFwd = r * 0.32f
        val eyeSide = r * 0.45f
        val eyeR = r * 0.22f
        val pupilR = eyeR * 0.55f
        val pupilOff = eyeR * 0.45f
        val ex1 = x + cosH * eyeFwd + perpX * eyeSide
        val ey1 = y + sinH * eyeFwd + perpY * eyeSide
        val ex2 = x + cosH * eyeFwd - perpX * eyeSide
        val ey2 = y + sinH * eyeFwd - perpY * eyeSide

        fillPaint.color = Color.WHITE
        c.drawCircle(ex1, ey1, eyeR, fillPaint)
        c.drawCircle(ex2, ey2, eyeR, fillPaint)

        val px1 = ex1 + cosH * pupilOff
        val py1 = ey1 + sinH * pupilOff
        val px2 = ex2 + cosH * pupilOff
        val py2 = ey2 + sinH * pupilOff
        fillPaint.color = COLOR_EYE
        c.drawCircle(px1, py1, pupilR, fillPaint)
        c.drawCircle(px2, py2, pupilR, fillPaint)

        // pupil highlight — tiny white dot
        val hl = pupilR * 0.4f
        val hlOff = pupilR * 0.35f
        fillPaint.color = Color.WHITE
        c.drawCircle(px1 - cosH * hlOff - perpX * hlOff, py1 - sinH * hlOff - perpY * hlOff, hl, fillPaint)
        c.drawCircle(px2 - cosH * hlOff - perpX * hlOff, py2 - sinH * hlOff - perpY * hlOff, hl, fillPaint)

        // tiny pink tongue ahead of head
        val tipFwd = r * 1.05f
        val tx = x + cosH * tipFwd
        val ty = y + sinH * tipFwd
        val tlen = r * 0.45f
        val tspread = r * 0.18f
        tonguePath.reset()
        tonguePath.moveTo(tx, ty)
        tonguePath.lineTo(tx + cosH * tlen + perpX * tspread, ty + sinH * tlen + perpY * tspread)
        tonguePath.moveTo(tx, ty)
        tonguePath.lineTo(tx + cosH * tlen - perpX * tspread, ty + sinH * tlen - perpY * tspread)
        pathPaint.style = Paint.Style.STROKE
        pathPaint.color = COLOR_TONGUE
        pathPaint.strokeWidth = r * 0.10f
        pathPaint.strokeCap = Paint.Cap.ROUND
        c.drawPath(tonguePath, pathPaint)
    }

    /** Joystick: idle preview anchored bottom-right; active follows touch. */
    private fun drawJoystick(c: Canvas) {
        // anchor: when active, where finger went down; idle, fixed bottom-right
        val idleX = width - dp(72f)
        val idleY = (height - dp(108f)).coerceAtLeast(dp(140f))
        val baseX = if (joystickActive || joystickAlpha > 0.04f) joyAnchorX else idleX
        val baseY = if (joystickActive || joystickAlpha > 0.04f) joyAnchorY else idleY

        val outerRadius = dp(46f)
        val stickRadius = dp(22f)
        // visible always, but stronger when active
        val a = 0.42f + joystickAlpha.coerceIn(0f, 1f) * 0.55f

        var dx = if (joystickActive) joyStickX - joyAnchorX else 0f
        var dy = if (joystickActive) joyStickY - joyAnchorY else 0f
        val mag = hypot(dx, dy)
        val maxOffset = outerRadius - stickRadius * 0.35f
        if (mag > maxOffset && mag > 0f) {
            val k = maxOffset / mag
            dx *= k
            dy *= k
        }

        // outer frosted ring
        fillPaint.color = colorAlpha(COLOR_SURFACE, 0.55f * a)
        c.drawCircle(baseX, baseY, outerRadius, fillPaint)
        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeWidth = dp(1.5f)
        pathPaint.color = colorAlpha(COLOR_TEXT, 0.18f * a)
        c.drawCircle(baseX, baseY, outerRadius, pathPaint)
        // inner softer ring
        pathPaint.strokeWidth = dp(6f)
        pathPaint.color = colorAlpha(COLOR_SURFACE, 0.45f * a)
        c.drawCircle(baseX, baseY, outerRadius - dp(4f), pathPaint)

        // 8 directional ticks
        pathPaint.strokeWidth = dp(1.4f)
        pathPaint.color = colorAlpha(COLOR_TEXT, 0.45f * a)
        pathPaint.strokeCap = Paint.Cap.ROUND
        val tickInner = outerRadius - dp(14f)
        val tickOuter = outerRadius - dp(8f)
        for (i in 0 until 8) {
            val ang = (i / 8f) * (PI.toFloat() * 2f)
            val cosA = cos(ang); val sinA = sin(ang)
            c.drawLine(
                baseX + cosA * tickInner, baseY + sinA * tickInner,
                baseX + cosA * tickOuter, baseY + sinA * tickOuter,
                pathPaint,
            )
        }

        // travel line from centre to knob (only when active)
        if (joystickActive && mag > 1f) {
            pathPaint.strokeWidth = dp(3f)
            pathPaint.color = colorAlpha(COLOR_ACCENT, 0.55f * a)
            c.drawLine(baseX, baseY, baseX + dx, baseY + dy, pathPaint)
        }

        // knob — mint accent with subtle drop shadow
        val knobX = baseX + dx
        val knobY = baseY + dy
        fillPaint.color = colorAlpha(COLOR_ACCENT, 0.35f * a)
        c.drawCircle(knobX, knobY + dp(2f), stickRadius * 1.05f, fillPaint)
        fillPaint.color = colorAlpha(COLOR_ACCENT, a)
        c.drawCircle(knobX, knobY, stickRadius, fillPaint)
        fillPaint.color = colorAlpha(Color.WHITE, 0.18f * a)
        c.drawCircle(knobX - stickRadius * 0.3f, knobY - stickRadius * 0.35f, stickRadius * 0.32f, fillPaint)

        // 方向 label under the ring
        subPaint.color = colorAlpha(COLOR_TEXT, 0.55f * a)
        subPaint.textSize = dp(10f)
        val labelY = min(height - dp(12f), baseY + outerRadius + dp(20f))
        c.drawText(resources.getString(R.string.sensor_active), baseX, labelY, subPaint)
    }

    private fun drawOverlay(c: Canvas) {
        val (title, sub) = when (game.state) {
            GameState.READY -> resources.getString(R.string.tap_to_start) to resources.getString(R.string.hint_drag)
            GameState.PAUSED -> resources.getString(R.string.pause) to resources.getString(R.string.tap_to_start)
            GameState.OVER -> resources.getString(R.string.game_over) to resources.getString(R.string.tap_restart)
            GameState.RUNNING -> return
        }
        overlayPaint.color = 0xCCFFFDF4.toInt()
        c.drawRect(viewRect, overlayPaint)
        val cx = width / 2f
        val cy = height / 2f
        titlePaint.color = COLOR_TEXT
        c.drawText(title, cx, cy, titlePaint)
        subPaint.color = COLOR_MUTED
        c.drawText(sub, cx, cy + dp(28f), subPaint)
    }

    // ---------- helpers ----------
    private fun dp(v: Float): Float = v * density

    private fun colorAlpha(argb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (a shl 24) or (argb and 0x00FFFFFF)
    }

    companion object {
        // Variant A · 薄荷清新 palette (kept in code so paint state is fast)
        private const val COLOR_TEXT = 0xFF2C3024.toInt()
        private const val COLOR_MUTED = 0xFF6B7060.toInt()
        private const val COLOR_SURFACE = 0xFFFFFDF4.toInt()
        private const val COLOR_GRID = 0x14465037
        private const val COLOR_GRID_SUB = 0x09465037

        private const val COLOR_HEAD = 0xFF9ED6A2.toInt()
        private const val COLOR_BODY_TOP = 0xFF8ACF91.toInt()
        private const val COLOR_BODY_MID = 0xFF74C084.toInt()
        private const val COLOR_BODY_TAIL = 0xFF5AA872.toInt()
        private const val COLOR_CHEEK = 0xFFF4A8B4.toInt()
        private const val COLOR_EYE = 0xFF241C14.toInt()
        private const val COLOR_TONGUE = 0xFFE57F8A.toInt()

        private const val COLOR_FOOD = 0xFFF4A261.toInt()
        private const val COLOR_FOOD_GLOW = 0xFFFCD7A8.toInt()

        private const val COLOR_ACCENT = 0xFF7AAB86.toInt()
    }
}
