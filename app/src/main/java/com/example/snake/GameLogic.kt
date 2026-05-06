package com.example.snake

import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState { READY, RUNNING, PAUSED, OVER }

/**
 * Continuous (non-grid) snake physics.
 *
 * The snake is a chain of head positions sampled once per tick. The chain is
 * trimmed from the tail end so its accumulated arc length matches the desired
 * body length, which grows when the head reaches food.
 *
 * The head turns by at most [maxTurnRate] radians per tick, which both gives
 * the FPS-joystick feel and prevents the snake from instantly reversing onto
 * itself.
 */
class GameLogic(density: Float, private val random: Random = Random.Default) {

    val bodyRadius: Float = 8f * density
    val foodRadius: Float = 7f * density

    private val speedInitial: Float = 1.6f * density
    private val speedMax: Float = 3.2f * density
    private val speedRamp: Float = 0.02f * density
    private val maxTurnRate: Float = 0.085f
    private val initialBodyLength: Float = 70f * density
    private val growthPerFood: Float = 16f * density
    private val sampleSpacing: Float = 1.6f * density

    var width: Float = 0f; private set
    var height: Float = 0f; private set

    val trail: ArrayDeque<PointF> = ArrayDeque()

    var heading: Float = -PI.toFloat() / 2f
        private set
    private var targetHeading: Float = heading
    private var hasTarget: Boolean = false

    var foodX: Float = 0f; private set
    var foodY: Float = 0f; private set

    var score: Int = 0; private set
    var state: GameState = GameState.READY; private set

    private var bodyLength: Float = initialBodyLength
    private var sized: Boolean = false

    val headX: Float get() = if (trail.isNotEmpty()) trail.last().x else 0f
    val headY: Float get() = if (trail.isNotEmpty()) trail.last().y else 0f

    fun setSize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        if (sized && w == width && h == height) return
        width = w; height = h
        sized = true
        reset()
    }

    fun reset() {
        if (!sized) return
        trail.clear()
        val landscape = width >= height
        val n = (initialBodyLength / sampleSpacing).toInt()
        if (landscape) {
            val cx = width * 0.32f
            val cy = height * 0.5f
            for (i in n downTo 0) {
                trail.addLast(PointF(cx - i * sampleSpacing, cy))
            }
            heading = 0f
        } else {
            val cx = width / 2f
            val cy = height * 0.62f
            for (i in n downTo 0) {
                trail.addLast(PointF(cx, cy + i * sampleSpacing))
            }
            heading = -PI.toFloat() / 2f
        }
        targetHeading = heading
        hasTarget = false
        bodyLength = initialBodyLength
        score = 0
        state = GameState.READY
        spawnFood()
    }

    fun start() {
        if (state == GameState.READY || state == GameState.PAUSED) state = GameState.RUNNING
    }

    fun pause() {
        if (state == GameState.RUNNING) state = GameState.PAUSED
    }

    fun setTargetHeading(angleRad: Float) {
        if (state == GameState.OVER) return
        targetHeading = angleRad
        hasTarget = true
    }

    fun clearTargetHeading() {
        hasTarget = false
    }

    fun tick() {
        if (state != GameState.RUNNING || trail.isEmpty()) return

        if (hasTarget) {
            var delta = targetHeading - heading
            while (delta > PI) delta -= 2f * PI.toFloat()
            while (delta < -PI) delta += 2f * PI.toFloat()
            heading += delta.coerceIn(-maxTurnRate, maxTurnRate)
        }

        val speed = currentSpeed()
        val nx = headX + cos(heading) * speed
        val ny = headY + sin(heading) * speed

        if (nx < bodyRadius || nx > width - bodyRadius ||
            ny < bodyRadius || ny > height - bodyRadius
        ) {
            state = GameState.OVER
            return
        }

        trail.addLast(PointF(nx, ny))
        trimTrail()

        val dxFood = nx - foodX
        val dyFood = ny - foodY
        val foodHit = bodyRadius + foodRadius - 2f
        if (dxFood * dxFood + dyFood * dyFood < foodHit * foodHit) {
            score += 1
            bodyLength += growthPerFood
            spawnFood()
        }

        if (selfCollision()) state = GameState.OVER
    }

    private fun currentSpeed(): Float = min(speedInitial + score * speedRamp, speedMax)

    private fun trimTrail() {
        var acc = 0f
        var dropBefore = 0
        for (i in trail.size - 1 downTo 1) {
            val a = trail[i]
            val b = trail[i - 1]
            val dx = a.x - b.x
            val dy = a.y - b.y
            acc += sqrt(dx * dx + dy * dy)
            if (acc >= bodyLength) {
                dropBefore = i - 1
                break
            }
        }
        repeat(dropBefore) { trail.removeFirst() }
    }

    private fun selfCollision(): Boolean {
        // Skip the segment of the body adjacent to the head; tight U-turns can
        // legitimately bring the neck very close without "hitting" itself.
        val safeSkipPx = bodyRadius * 5f
        val collideRadius = bodyRadius * 1.55f
        val collideR2 = collideRadius * collideRadius
        var acc = 0f
        val hx = headX
        val hy = headY
        for (i in trail.size - 2 downTo 0) {
            val a = trail[i + 1]
            val b = trail[i]
            val sx = a.x - b.x
            val sy = a.y - b.y
            acc += sqrt(sx * sx + sy * sy)
            if (acc < safeSkipPx) continue
            val ex = b.x - hx
            val ey = b.y - hy
            if (ex * ex + ey * ey < collideR2) return true
        }
        return false
    }

    private fun spawnFood() {
        val pad = foodRadius + bodyRadius + 6f
        val minD = bodyRadius + foodRadius + 8f
        val minD2 = minD * minD
        repeat(60) {
            val x = pad + random.nextFloat() * (width - 2f * pad)
            val y = pad + random.nextFloat() * (height - 2f * pad)
            var ok = true
            for (p in trail) {
                val dx = p.x - x; val dy = p.y - y
                if (dx * dx + dy * dy < minD2) { ok = false; break }
            }
            if (ok) { foodX = x; foodY = y; return }
        }
        foodX = width / 2f
        foodY = height * 0.25f
    }
}
