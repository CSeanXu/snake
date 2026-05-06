package com.example.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max

class SnakeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val game = GameLogic(cols = 20, rows = 20)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val ate = game.tick()
            if (ate) {
                tickIntervalMs = max(MIN_TICK_MS, tickIntervalMs - SPEED_UP_PER_FOOD_MS)
            }
            invalidate()
            listener?.onStateChanged(game.state, game.score)
            if (game.state == GameState.RUNNING) {
                mainHandler.postDelayed(this, tickIntervalMs)
            }
        }
    }
    private var tickIntervalMs: Long = INITIAL_TICK_MS

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_grid)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_bg)
        style = Paint.Style.FILL
    }
    private val snakeBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_body)
        style = Paint.Style.FILL
    }
    private val snakeHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_head)
        style = Paint.Style.FILL
    }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.food)
        style = Paint.Style.FILL
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay)
        style = Paint.Style.FILL
    }
    private val overlayTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val overlaySubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
    }

    private val cellRect = RectF()
    private var cellSize: Float = 0f
    private var boardLeft: Float = 0f
    private var boardTop: Float = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            when (game.state) {
                GameState.READY, GameState.PAUSED -> {
                    game.start()
                    scheduleTick()
                }
                GameState.OVER -> {
                    restart()
                }
                GameState.RUNNING -> {}
            }
            invalidate()
            listener?.onStateChanged(game.state, game.score)
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) < SWIPE_THRESHOLD && abs(dy) < SWIPE_THRESHOLD) return false
            if (abs(dx) > abs(dy)) {
                game.requestDirection(if (dx > 0) Direction.RIGHT else Direction.LEFT)
            } else {
                game.requestDirection(if (dy > 0) Direction.DOWN else Direction.UP)
            }
            // First swipe also kicks off the game.
            if (game.state == GameState.READY) {
                game.start()
                scheduleTick()
                listener?.onStateChanged(game.state, game.score)
            }
            return true
        }
    })

    var listener: Listener? = null

    interface Listener {
        fun onStateChanged(state: GameState, score: Int)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = minOf(w, h).toFloat()
        cellSize = side / game.cols
        boardLeft = (w - cellSize * game.cols) / 2f
        boardTop = (h - cellSize * game.rows) / 2f
        overlayTitlePaint.textSize = side * 0.08f
        overlaySubPaint.textSize = side * 0.04f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoard(canvas)
        drawFood(canvas)
        drawSnake(canvas)
        drawOverlay(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        val right = boardLeft + cellSize * game.cols
        val bottom = boardTop + cellSize * game.rows
        canvas.drawRoundRect(boardLeft, boardTop, right, bottom, 16f, 16f, boardPaint)

        // subtle grid
        for (i in 1 until game.cols) {
            val x = boardLeft + i * cellSize
            canvas.drawLine(x, boardTop, x, bottom, gridPaint)
        }
        for (j in 1 until game.rows) {
            val y = boardTop + j * cellSize
            canvas.drawLine(boardLeft, y, right, y, gridPaint)
        }
    }

    private fun drawFood(canvas: Canvas) {
        val cx = boardLeft + game.food.x * cellSize + cellSize / 2f
        val cy = boardTop + game.food.y * cellSize + cellSize / 2f
        canvas.drawCircle(cx, cy, cellSize * 0.38f, foodPaint)
    }

    private fun drawSnake(canvas: Canvas) {
        val snake = game.snake
        if (snake.isEmpty()) return
        val pad = cellSize * 0.08f
        val headIdx = snake.lastIndex
        for (i in snake.indices) {
            val c = snake[i]
            val left = boardLeft + c.x * cellSize + pad
            val top = boardTop + c.y * cellSize + pad
            cellRect.set(left, top, left + cellSize - pad * 2, top + cellSize - pad * 2)
            val paint = if (i == headIdx) snakeHeadPaint else snakeBodyPaint
            val r = cellSize * 0.22f
            canvas.drawRoundRect(cellRect, r, r, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        val (title, sub) = when (game.state) {
            GameState.READY -> resources.getString(R.string.tap_to_start) to resources.getString(R.string.hint_swipe)
            GameState.PAUSED -> resources.getString(R.string.pause) to resources.getString(R.string.tap_to_start)
            GameState.OVER -> resources.getString(R.string.game_over) to resources.getString(R.string.restart)
            GameState.RUNNING -> return
        }
        val right = boardLeft + cellSize * game.cols
        val bottom = boardTop + cellSize * game.rows
        canvas.drawRoundRect(boardLeft, boardTop, right, bottom, 16f, 16f, overlayPaint)
        val cx = (boardLeft + right) / 2f
        val cy = (boardTop + bottom) / 2f
        canvas.drawText(title, cx, cy, overlayTitlePaint)
        canvas.drawText(sub, cx, cy + overlayTitlePaint.textSize * 0.9f, overlaySubPaint)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun togglePause() {
        when (game.state) {
            GameState.RUNNING -> {
                game.pause()
                mainHandler.removeCallbacks(tickRunnable)
            }
            GameState.READY, GameState.PAUSED -> {
                game.start()
                scheduleTick()
            }
            GameState.OVER -> {}
        }
        invalidate()
        listener?.onStateChanged(game.state, game.score)
    }

    fun restart() {
        mainHandler.removeCallbacks(tickRunnable)
        game.reset()
        tickIntervalMs = INITIAL_TICK_MS
        invalidate()
        listener?.onStateChanged(game.state, game.score)
    }

    fun pauseIfRunning() {
        if (game.state == GameState.RUNNING) {
            game.pause()
            mainHandler.removeCallbacks(tickRunnable)
            invalidate()
            listener?.onStateChanged(game.state, game.score)
        }
    }

    private fun scheduleTick() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.postDelayed(tickRunnable, tickIntervalMs)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(tickRunnable)
    }

    val state: GameState get() = game.state
    val score: Int get() = game.score

    companion object {
        private const val INITIAL_TICK_MS = 180L
        private const val MIN_TICK_MS = 70L
        private const val SPEED_UP_PER_FOOD_MS = 4L
        private const val SWIPE_THRESHOLD = 30f
    }
}
