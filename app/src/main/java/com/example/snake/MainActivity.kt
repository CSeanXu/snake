package com.example.snake

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.snake.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SnakeView.Listener {

    private lateinit var binding: ActivityMainBinding
    private var bestScore: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bestScore = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BEST, 0)
        binding.bestText.text = bestScore.toString()

        binding.snakeView.listener = this
        binding.pauseBtn.setOnClickListener { binding.snakeView.togglePause() }
        binding.restartBtn.setOnClickListener { binding.snakeView.restart() }
    }

    override fun onPause() {
        super.onPause()
        binding.snakeView.pauseIfRunning()
    }

    override fun onStateChanged(state: GameState, score: Int) {
        binding.scoreText.text = score.toString()
        binding.pauseBtn.setImageResource(
            if (state == GameState.RUNNING) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.pauseBtn.isEnabled = state != GameState.OVER
        binding.pauseBtn.alpha = if (state == GameState.OVER) 0.4f else 1f

        if (state == GameState.OVER && score > bestScore) {
            bestScore = score
            binding.bestText.text = bestScore.toString()
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BEST, bestScore)
                .apply()
        }
    }

    companion object {
        private const val PREFS = "snake_prefs"
        private const val KEY_BEST = "best_score"
    }
}
