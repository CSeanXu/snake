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

        updateScore(binding.snakeView.score)
        updatePauseLabel(binding.snakeView.state)
    }

    override fun onPause() {
        super.onPause()
        binding.snakeView.pauseIfRunning()
    }

    override fun onStateChanged(state: GameState, score: Int) {
        updateScore(score)
        updatePauseLabel(state)
        if (state == GameState.OVER && score > bestScore) {
            bestScore = score
            binding.bestText.text = bestScore.toString()
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BEST, bestScore)
                .apply()
        }
    }

    private fun updateScore(score: Int) {
        binding.scoreText.text = score.toString()
    }

    private fun updatePauseLabel(state: GameState) {
        binding.pauseBtn.text = when (state) {
            GameState.PAUSED -> getString(R.string.resume)
            else -> getString(R.string.pause)
        }
        binding.pauseBtn.isEnabled = state != GameState.OVER
    }

    companion object {
        private const val PREFS = "snake_prefs"
        private const val KEY_BEST = "best_score"
    }
}
