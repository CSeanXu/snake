package com.example.snake

/**
 * Visual palette for the snake. Background, food, and shield colors stay
 * constant across skins — only the snake itself (head, body gradient,
 * cheeks, tongue, accent UI like joystick knob and combo accent) changes.
 */
class SnakeSkin(
    val key: String,
    val displayName: String,
    val head: Int,
    val cheek: Int,
    val tongue: Int,
    val accent: Int,
    val accentDeep: Int,
    val swatch: Int,
    val showCheek: Boolean = true,
    val hasCrown: Boolean = false,
    val hasAimDots: Boolean = false,
    private val bodyStops: IntArray,
) {
    /** @param t 0 = head end, 1 = tail end. */
    fun bodyColorAt(t: Float): Int {
        val n = bodyStops.size
        val idx = (t * n).toInt().coerceIn(0, n - 1)
        return bodyStops[idx]
    }

    val tailColor: Int get() = bodyStops.last()

    companion object {
        val MINT = SnakeSkin(
            key = "A",
            displayName = "薄荷",
            head = 0xFF9ED6A2.toInt(),
            cheek = 0xFFF4A8B4.toInt(),
            tongue = 0xFFE57F8A.toInt(),
            accent = 0xFF7AAB86.toInt(),
            accentDeep = 0xFF4D8C6B.toInt(),
            swatch = 0xFF8ACF91.toInt(),
            bodyStops = intArrayOf(
                0xFF8ACF91.toInt(),
                0xFF74C084.toInt(),
                0xFF5AA872.toInt(),
            ),
        )
        val CANDY = SnakeSkin(
            key = "B",
            displayName = "糖果",
            head = 0xFFA8E6C1.toInt(),
            cheek = 0xFFFF8FA3.toInt(),
            tongue = 0xFFE57F8A.toInt(),
            accent = 0xFFFF6B9D.toInt(),
            accentDeep = 0xFFE5478A.toInt(),
            swatch = 0xFFFFC4D8.toInt(),
            hasCrown = true,
            bodyStops = intArrayOf(
                0xFFB9E3A6.toInt(),
                0xFFFFF1A8.toInt(),
                0xFFFFC4D8.toInt(),
                0xFFCDB9FF.toInt(),
            ),
        )
        val PAPER = SnakeSkin(
            key = "C",
            displayName = "极简",
            head = 0xFF3A3530.toInt(),
            cheek = 0xFFE6A999.toInt(),
            tongue = 0xFF8A7468.toInt(),
            accent = 0xFFD96F4E.toInt(),
            accentDeep = 0xFFA85436.toInt(),
            swatch = 0xFF4A443D.toInt(),
            showCheek = false,
            hasAimDots = true,
            bodyStops = intArrayOf(
                0xFF4A443D.toInt(),
                0xFF6A6058.toInt(),
                0xFF9B8E7E.toInt(),
            ),
        )

        val ALL: List<SnakeSkin> = listOf(MINT, CANDY, PAPER)
        fun byKey(key: String?): SnakeSkin = ALL.find { it.key == key } ?: MINT
    }
}
