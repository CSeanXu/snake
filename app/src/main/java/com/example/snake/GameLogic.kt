package com.example.snake

import kotlin.random.Random

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    val isHorizontal: Boolean get() = this == LEFT || this == RIGHT
    fun opposite(): Direction = when (this) {
        UP -> DOWN; DOWN -> UP; LEFT -> RIGHT; RIGHT -> LEFT
    }
}

data class Cell(val x: Int, val y: Int)

enum class GameState { READY, RUNNING, PAUSED, OVER }

class GameLogic(
    val cols: Int = 20,
    val rows: Int = 20,
    private val random: Random = Random.Default,
) {
    private val _snake: ArrayDeque<Cell> = ArrayDeque()
    val snake: List<Cell> get() = _snake

    var direction: Direction = Direction.RIGHT
        private set
    private var pendingDirection: Direction = direction

    var food: Cell = Cell(0, 0)
        private set

    var score: Int = 0
        private set

    var state: GameState = GameState.READY
        private set

    init {
        reset()
    }

    fun reset() {
        _snake.clear()
        val cy = rows / 2
        val cx = cols / 2
        // 3-cell snake heading right
        _snake.addLast(Cell(cx - 1, cy))
        _snake.addLast(Cell(cx, cy))
        _snake.addLast(Cell(cx + 1, cy))
        direction = Direction.RIGHT
        pendingDirection = Direction.RIGHT
        score = 0
        spawnFood()
        state = GameState.READY
    }

    fun start() {
        if (state == GameState.READY || state == GameState.PAUSED) {
            state = GameState.RUNNING
        }
    }

    fun pause() {
        if (state == GameState.RUNNING) state = GameState.PAUSED
    }

    fun togglePause() {
        when (state) {
            GameState.RUNNING -> state = GameState.PAUSED
            GameState.PAUSED -> state = GameState.RUNNING
            GameState.READY -> state = GameState.RUNNING
            GameState.OVER -> {}
        }
    }

    /**
     * Queue a direction change. Reverse-direction inputs are ignored.
     * The actual rotation is applied on the next tick so two fast swipes
     * can't fold the snake onto itself.
     */
    fun requestDirection(next: Direction) {
        if (state == GameState.OVER) return
        if (next == direction || next == direction.opposite()) return
        pendingDirection = next
    }

    /** Advance one step. Returns true if the snake ate food this tick. */
    fun tick(): Boolean {
        if (state != GameState.RUNNING) return false

        direction = pendingDirection
        val head = _snake.last()
        val nx = head.x + direction.dx
        val ny = head.y + direction.dy

        if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) {
            state = GameState.OVER
            return false
        }

        val ate = (nx == food.x && ny == food.y)
        // Tail moves out before we check self-collision, unless we're growing.
        if (!ate) _snake.removeFirst()

        for (c in _snake) {
            if (c.x == nx && c.y == ny) {
                state = GameState.OVER
                return false
            }
        }

        _snake.addLast(Cell(nx, ny))

        if (ate) {
            score += 1
            spawnFood()
        }
        return ate
    }

    private fun spawnFood() {
        val total = cols * rows
        if (_snake.size >= total) {
            // Board fully covered — treat as a win by ending the game.
            state = GameState.OVER
            return
        }
        // Pick a random empty cell. Rejection sampling is fine for boards up to ~400 cells.
        while (true) {
            val x = random.nextInt(cols)
            val y = random.nextInt(rows)
            var occupied = false
            for (c in _snake) {
                if (c.x == x && c.y == y) { occupied = true; break }
            }
            if (!occupied) {
                food = Cell(x, y)
                return
            }
        }
    }
}
