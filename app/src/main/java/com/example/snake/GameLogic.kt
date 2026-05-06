package com.example.snake

import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState { READY, RUNNING, PAUSED, OVER }

enum class FoodKind { NORMAL, SHIELD }

/** A single food on the field. colorIndex is into the renderer's palette;
 *  -1 for SHIELD foods. pulseOffset desyncs the breathing animation. */
data class FoodItem(
    val x: Float,
    val y: Float,
    val kind: FoodKind,
    val colorIndex: Int,
    val pulseOffset: Float,
)

/** Bouncing +N (or shield burst when value < 0) marker the renderer animates. */
data class ScorePopup(val x: Float, val y: Float, val value: Int, val spawnTime: Float)

/**
 * Continuous (non-grid) snake physics with time-based dash / shield /
 * combo systems and a multi-food field. The view feeds in real dt
 * seconds so cooldown timers track wall clock instead of frame rate.
 */
class GameLogic(density: Float, private val random: Random = Random.Default) {

    val bodyRadius: Float = 8f * density
    val foodRadius: Float = 7f * density

    private val speedInitial: Float = 1.6f * density
    private val speedMax: Float = 3.2f * density
    private val speedRamp: Float = 0.005f * density
    private val maxTurnRate: Float = 0.085f
    private val initialBodyLength: Float = 70f * density
    private val growthPerFood: Float = 16f * density
    private val sampleSpacing: Float = 1.6f * density

    var width: Float = 0f; private set
    var height: Float = 0f; private set

    /** When true, the head wraps from one edge to the opposite edge instead
     *  of ending the run. Self-collision still applies. Body trail keeps
     *  raw coordinates; the renderer handles the visual discontinuity. */
    var wrapEnabled: Boolean = false

    val trail: ArrayDeque<PointF> = ArrayDeque()

    var heading: Float = -PI.toFloat() / 2f
        private set
    private var targetHeading: Float = heading
    private var hasTarget: Boolean = false

    /** Foods currently on the field (read-only view for the renderer). */
    val foods: MutableList<FoodItem> = mutableListOf()
    /** How many color slots the renderer's food palette has. */
    val foodPaletteSize: Int = 4
    private val targetNormalFoods: Int = 3

    var score: Int = 0; private set
    var state: GameState = GameState.READY; private set

    private var bodyLength: Float = initialBodyLength
    private var sized: Boolean = false

    // ---------- time-based state ----------
    var time: Float = 0f; private set

    private var dashEndTime: Float = -1f
    private var dashCooldownUntil: Float = 0f
    val dashDuration: Float = 1.4f
    val dashCooldown: Float = 4.2f
    private val dashSpeedMul: Float = 1.85f

    private var shieldEndTime: Float = -1f
    val shieldDuration: Float = 4f

    private var lastFoodEatTime: Float = -100f
    val comboWindow: Float = 3f
    var combo: Int = 0; private set

    private var normalFoodsSinceShield: Int = 0
    private val shieldFoodEvery: Int = 5

    val popups: ArrayDeque<ScorePopup> = ArrayDeque()
    val popupDuration: Float = 0.9f

    // ---------- queries ----------
    val isDashing: Boolean get() = time < dashEndTime
    val dashReady: Boolean get() = time >= dashCooldownUntil
    val dashCooldownProgress: Float get() {
        if (dashReady) return 1f
        val total = dashDuration + dashCooldown
        val remain = (dashCooldownUntil - time).coerceAtLeast(0f)
        return (1f - remain / total).coerceIn(0f, 1f)
    }
    val isShielded: Boolean get() = time < shieldEndTime
    val shieldRemaining: Float get() = (shieldEndTime - time).coerceAtLeast(0f)
    val comboTimeRemaining: Float get() = (comboWindow - (time - lastFoodEatTime)).coerceAtLeast(0f)

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
        popups.clear()
        foods.clear()
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
        time = 0f
        dashEndTime = -1f
        dashCooldownUntil = 0f
        shieldEndTime = -1f
        lastFoodEatTime = -100f
        combo = 0
        normalFoodsSinceShield = 0
        replenishFoods()
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

    fun triggerDash(): Boolean {
        if (state != GameState.RUNNING) return false
        if (!dashReady) return false
        dashEndTime = time + dashDuration
        dashCooldownUntil = dashEndTime + dashCooldown
        return true
    }

    fun tick(dt: Float) {
        if (state != GameState.RUNNING || trail.isEmpty()) return
        time += dt

        if (hasTarget) {
            var delta = targetHeading - heading
            while (delta > PI) delta -= 2f * PI.toFloat()
            while (delta < -PI) delta += 2f * PI.toFloat()
            val turnRate = if (isDashing) maxTurnRate * 1.35f else maxTurnRate
            heading += delta.coerceIn(-turnRate, turnRate)
        }

        val speed = currentSpeed()
        val nx = headX + cos(heading) * speed
        val ny = headY + sin(heading) * speed

        val outBounds = nx < bodyRadius || nx > width - bodyRadius ||
            ny < bodyRadius || ny > height - bodyRadius
        val cx: Float
        val cy: Float
        if (wrapEnabled) {
            cx = wrapCoord(nx, width)
            cy = wrapCoord(ny, height)
        } else if (outBounds && !isShielded) {
            state = GameState.OVER
            return
        } else {
            cx = nx.coerceIn(bodyRadius, width - bodyRadius)
            cy = ny.coerceIn(bodyRadius, height - bodyRadius)
        }

        trail.addLast(PointF(cx, cy))
        trimTrail()

        // food collisions — at most one eaten per tick
        val hit = bodyRadius + foodRadius - 2f
        val hit2 = hit * hit
        var ate: FoodItem? = null
        for (f in foods) {
            val dx = cx - f.x
            val dy = cy - f.y
            if (dx * dx + dy * dy < hit2) { ate = f; break }
        }
        if (ate != null) {
            foods.remove(ate)
            consumeFood(ate)
            replenishFoods()
        }

        if (!isShielded && selfCollision()) {
            state = GameState.OVER
            return
        }

        if (combo > 0 && time - lastFoodEatTime > comboWindow) combo = 0

        while (popups.isNotEmpty() && time - popups.first().spawnTime > popupDuration) {
            popups.removeFirst()
        }
    }

    private fun consumeFood(f: FoodItem) {
        if (f.kind == FoodKind.SHIELD) {
            shieldEndTime = time + shieldDuration
            popups.addLast(ScorePopup(f.x, f.y, -1, time))
            normalFoodsSinceShield = 0
        } else {
            combo = if (time - lastFoodEatTime <= comboWindow) (combo + 1).coerceAtMost(99) else 1
            lastFoodEatTime = time
            val gain = 10 + (combo - 1) * 5
            score += gain
            popups.addLast(ScorePopup(f.x, f.y, gain, time))
            bodyLength += growthPerFood
            normalFoodsSinceShield += 1
        }
    }

    private fun replenishFoods() {
        // top up normal foods
        while (foods.count { it.kind == FoodKind.NORMAL } < targetNormalFoods) {
            val spot = findFoodSpot() ?: break
            val ci = random.nextInt(foodPaletteSize)
            val po = random.nextFloat() * 6.283f
            foods.add(FoodItem(spot.first, spot.second, FoodKind.NORMAL, ci, po))
        }
        // shield slot — at most one on the field at a time
        if (normalFoodsSinceShield >= shieldFoodEvery && foods.none { it.kind == FoodKind.SHIELD }) {
            val spot = findFoodSpot()
            if (spot != null) {
                foods.add(FoodItem(spot.first, spot.second, FoodKind.SHIELD, -1, 0f))
            }
        }
    }

    private fun currentSpeed(): Float {
        val base = min(speedInitial + score * speedRamp, speedMax)
        return if (isDashing) base * dashSpeedMul else base
    }

    private fun trimTrail() {
        var acc = 0f
        var dropBefore = 0
        for (i in trail.size - 1 downTo 1) {
            val a = trail[i]
            val b = trail[i - 1]
            acc += segmentLen(a.x - b.x, a.y - b.y)
            if (acc >= bodyLength) {
                dropBefore = i - 1
                break
            }
        }
        repeat(dropBefore) { trail.removeFirst() }
    }

    private fun selfCollision(): Boolean {
        val safeSkipPx = bodyRadius * 5f
        val collideRadius = bodyRadius * 1.55f
        val collideR2 = collideRadius * collideRadius
        var acc = 0f
        val hx = headX
        val hy = headY
        for (i in trail.size - 2 downTo 0) {
            val a = trail[i + 1]
            val b = trail[i]
            acc += segmentLen(a.x - b.x, a.y - b.y)
            if (acc < safeSkipPx) continue
            val ex = wrapDelta(b.x - hx, width)
            val ey = wrapDelta(b.y - hy, height)
            if (ex * ex + ey * ey < collideR2) return true
        }
        return false
    }

    /** Arc length of one trail segment, accounting for wrap teleports. */
    private fun segmentLen(dx: Float, dy: Float): Float {
        val ax = if (wrapEnabled) wrapDelta(dx, width) else dx
        val ay = if (wrapEnabled) wrapDelta(dy, height) else dy
        return sqrt(ax * ax + ay * ay)
    }

    /** Signed delta in [-max/2, max/2], so a delta near `max` becomes a small
     *  negative value (wrap-aware "shortest path" between two coordinates). */
    private fun wrapDelta(d: Float, max: Float): Float {
        if (!wrapEnabled || max <= 0f) return d
        var v = d
        val half = max / 2f
        while (v > half) v -= max
        while (v < -half) v += max
        return v
    }

    /** Wrap a coordinate into [0, max). */
    private fun wrapCoord(v: Float, max: Float): Float {
        if (max <= 0f) return v
        var r = v
        while (r < 0f) r += max
        while (r >= max) r -= max
        return r
    }

    private fun findFoodSpot(): Pair<Float, Float>? {
        val pad = foodRadius + bodyRadius + 6f
        val minD = bodyRadius + foodRadius + 8f
        val minD2 = minD * minD
        val foodSep = foodRadius * 4f
        val foodSep2 = foodSep * foodSep
        repeat(80) {
            val x = pad + random.nextFloat() * (width - 2f * pad)
            val y = pad + random.nextFloat() * (height - 2f * pad)
            var ok = true
            for (p in trail) {
                val dx = p.x - x; val dy = p.y - y
                if (dx * dx + dy * dy < minD2) { ok = false; break }
            }
            if (ok) {
                for (other in foods) {
                    val dx = other.x - x; val dy = other.y - y
                    if (dx * dx + dy * dy < foodSep2) { ok = false; break }
                }
            }
            if (ok) return Pair(x, y)
        }
        return null
    }
}
