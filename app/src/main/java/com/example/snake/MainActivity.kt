package com.example.snake

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.example.snake.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity(), SnakeView.Listener {

    private lateinit var binding: ActivityMainBinding
    private var bestScore: Int = 0
    private val scoreFormat = NumberFormat.getIntegerInstance(Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // Push play area + floating UI into the safe area defined by display
        // cutouts. We deliberately ignore the system-bar insets so transient
        // swipes-to-show don't reflow the layout mid-game.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            insets
        }

        bestScore = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BEST, 0)
        binding.bestText.text = formatScore(bestScore)

        binding.snakeView.listener = this
        binding.settingsBtn.setOnClickListener { binding.snakeView.togglePause() }
        binding.restartBtn.setOnClickListener { binding.snakeView.restart() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPause() {
        super.onPause()
        binding.snakeView.pauseIfRunning()
    }

    override fun onStateChanged(state: GameState, score: Int) {
        binding.scoreText.text = formatScore(score)
        binding.lengthText.text = formatScore(binding.snakeView.length)

        if (state == GameState.OVER && score > bestScore) {
            bestScore = score
            binding.bestText.text = formatScore(bestScore)
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BEST, bestScore)
                .apply()
        }
    }

    private fun formatScore(score: Int): String = scoreFormat.format(score)

    companion object {
        private const val PREFS = "snake_prefs"
        private const val KEY_BEST = "best_score"
    }
}
