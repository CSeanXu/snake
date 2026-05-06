package com.example.snake

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.example.snake.databinding.DialogSettingsBinding
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity(), SnakeView.Listener {

    private lateinit var binding: ActivityMainBinding
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private var bestScore: Int = 0
    private val scoreFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val density: Float by lazy { resources.displayMetrics.density }
    private var activeSkinKey: String = SnakeSkin.MINT.key
    private var settingsDialog: AlertDialog? = null

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

        bestScore = prefs.getInt(KEY_BEST, 0)
        binding.bestText.text = formatScore(bestScore)

        binding.snakeView.listener = this
        binding.pauseBtn.setOnClickListener { binding.snakeView.togglePause() }
        binding.settingsBtn.setOnClickListener { openSettingsDialog() }

        // Load persisted preferences and apply to the live game.
        activeSkinKey = prefs.getString(KEY_SKIN, SnakeSkin.MINT.key) ?: SnakeSkin.MINT.key
        binding.snakeView.skin = SnakeSkin.byKey(activeSkinKey)
        binding.snakeView.wrapEnabled = prefs.getBoolean(KEY_WRAP, false)
    }

    private fun openSettingsDialog() {
        // Pause the game while the dialog is open so the snake doesn't run
        // into a wall behind the modal.
        binding.snakeView.pauseIfRunning()

        val dlgBinding = DialogSettingsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dlgBinding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        settingsDialog = dialog

        // Skin swatches inside the dialog
        bindDialogSwatch(dlgBinding.dlgSkinAGroup, dlgBinding.dlgSkinA, SnakeSkin.MINT)
        bindDialogSwatch(dlgBinding.dlgSkinBGroup, dlgBinding.dlgSkinB, SnakeSkin.CANDY)
        bindDialogSwatch(dlgBinding.dlgSkinCGroup, dlgBinding.dlgSkinC, SnakeSkin.PAPER)

        // Wrap-around toggle
        dlgBinding.wrapSwitch.isChecked = binding.snakeView.wrapEnabled
        dlgBinding.wrapSwitch.setOnCheckedChangeListener { _, checked ->
            binding.snakeView.wrapEnabled = checked
            prefs.edit().putBoolean(KEY_WRAP, checked).apply()
        }

        dlgBinding.dlgClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun bindDialogSwatch(group: View, swatch: View, skin: SnakeSkin) {
        renderDialogSwatch(swatch, skin)
        group.setOnClickListener {
            applySkin(skin)
            // re-render this dialog's swatches so the active ring follows
            (settingsDialog?.findViewById<View>(R.id.dlgSkinA))?.let {
                renderDialogSwatch(it, SnakeSkin.MINT)
            }
            (settingsDialog?.findViewById<View>(R.id.dlgSkinB))?.let {
                renderDialogSwatch(it, SnakeSkin.CANDY)
            }
            (settingsDialog?.findViewById<View>(R.id.dlgSkinC))?.let {
                renderDialogSwatch(it, SnakeSkin.PAPER)
            }
        }
    }

    private fun renderDialogSwatch(view: View, skin: SnakeSkin) {
        view.background = makeSwatch(skin.swatch, skin.key == activeSkinKey)
    }

    private fun applySkin(skin: SnakeSkin) {
        activeSkinKey = skin.key
        binding.snakeView.skin = skin
        prefs.edit().putString(KEY_SKIN, skin.key).apply()
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
        settingsDialog?.takeIf { it.isShowing }?.dismiss()
        settingsDialog = null
    }

    override fun onStateChanged(state: GameState, score: Int) {
        binding.scoreText.text = formatScore(score)
        binding.lengthText.text = formatScore(binding.snakeView.length)

        if (state == GameState.OVER && score > bestScore) {
            bestScore = score
            binding.bestText.text = formatScore(bestScore)
            prefs.edit().putInt(KEY_BEST, bestScore).apply()
        }
    }

    private fun formatScore(score: Int): String = scoreFormat.format(score)

    companion object {
        private const val PREFS = "snake_prefs"
        private const val KEY_BEST = "best_score"
        private const val KEY_SKIN = "skin_key"
        private const val KEY_WRAP = "wrap_enabled"
    }
}
