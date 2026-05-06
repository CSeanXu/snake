package com.example.snake

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
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
    private val density: Float by lazy { resources.displayMetrics.density }
    private var activeSkinKey: String = SnakeSkin.MINT.key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            insets
        }

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        bestScore = prefs.getInt(KEY_BEST, 0)
        binding.bestText.text = formatScore(bestScore)

        binding.snakeView.listener = this
        binding.settingsBtn.setOnClickListener { binding.snakeView.togglePause() }

        // Wire skin selector
        val saved = prefs.getString(KEY_SKIN, SnakeSkin.MINT.key)
        applySkin(SnakeSkin.byKey(saved), persist = false)
        bindSkinSwatch(binding.skinA, SnakeSkin.MINT)
        bindSkinSwatch(binding.skinB, SnakeSkin.CANDY)
        bindSkinSwatch(binding.skinC, SnakeSkin.PAPER)
    }

    private fun bindSkinSwatch(view: View, skin: SnakeSkin) {
        view.setOnClickListener { applySkin(skin) }
        renderSwatch(view, skin)
    }

    private fun applySkin(skin: SnakeSkin, persist: Boolean = true) {
        activeSkinKey = skin.key
        binding.snakeView.skin = skin
        if (persist) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SKIN, skin.key)
                .apply()
        }
        // re-render all swatches so the active ring follows the selection
        renderSwatch(binding.skinA, SnakeSkin.MINT)
        renderSwatch(binding.skinB, SnakeSkin.CANDY)
        renderSwatch(binding.skinC, SnakeSkin.PAPER)
    }

    private fun renderSwatch(view: View, skin: SnakeSkin) {
        val selected = skin.key == activeSkinKey
        view.background = makeSwatch(skin.swatch, selected)
    }

    /** A flat colored circle, with an outer ring + inner inset when selected. */
    private fun makeSwatch(color: Int, selected: Boolean): Drawable {
        if (!selected) {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((density * 2f).toInt(), 0xFF2C3024.toInt())
            setColor(0)
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        val pad = (density * 4f).toInt()
        return LayerDrawable(arrayOf(outer, inner)).also {
            it.setLayerInset(1, pad, pad, pad, pad)
        }
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
        private const val KEY_SKIN = "skin_key"
    }
}
