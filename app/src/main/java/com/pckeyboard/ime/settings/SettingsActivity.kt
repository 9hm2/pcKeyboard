package com.pckeyboard.ime.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pckeyboard.ime.databinding.ActivitySettingsBinding
import com.pckeyboard.ime.editor.ThemeEditorActivity
import com.pckeyboard.ime.theme.KeyboardTheme
import com.pckeyboard.ime.theme.ThemeRepository

/**
 * Settings: tweak keyboard sizing (height, side margins, split mode),
 * pick the active theme, manage custom themes, launch the editor.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: ThemeRepository
    private lateinit var prefs: KeyboardPrefs
    private lateinit var adapter: ThemeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ThemeRepository(this)
        prefs = KeyboardPrefs(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.scrollView.addSystemBarBottomPadding()
        binding.btnNewTheme.addSystemBarBottomEndMargin()

        adapter = ThemeAdapter(
            onSelect = {
                repo.selectTheme(it.id)
                adapter.selectedId = it.id
                adapter.notifyDataSetChanged()
            },
            onEdit = { editTheme(it) },
            onDelete = {
                repo.deleteCustomTheme(it.id)
                reload()
            },
            initialSelectedId = repo.getSelectedTheme().id
        )
        binding.themeList.layoutManager = LinearLayoutManager(this)
        binding.themeList.adapter = adapter

        binding.btnNewTheme.setOnClickListener {
            startActivity(Intent(this, ThemeEditorActivity::class.java))
        }

        wireSizingControls()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun reload() {
        adapter.submit(repo.allThemes())
        adapter.selectedId = repo.getSelectedTheme().id
    }

    private fun editTheme(t: KeyboardTheme) {
        startActivity(
            Intent(this, ThemeEditorActivity::class.java)
                .putExtra(ThemeEditorActivity.EXTRA_THEME_ID, t.id)
        )
    }

    /** Maps prefs.heightScale (0.5 .. 1.6) to seek progress (0 .. 110)
     *  so each step is 1%; 50% .. 160%. */
    private fun heightToProgress(scale: Float): Int =
        ((scale - 0.5f) * 100f).toInt().coerceIn(0, 110)

    private fun progressToHeight(p: Int): Float = 0.5f + p / 100f

    /** prefs.horizontalPadding (0.0 .. 0.30) ↔ progress (0 .. 30) as percent. */
    private fun paddingToProgress(p: Float): Int = (p * 100f).toInt().coerceIn(0, 30)
    private fun progressToPadding(p: Int): Float = p / 100f

    /** splitGapWeight (0.5 .. 6.0) ↔ progress (0 .. 55) so 0.5 + p/10. */
    private fun gapToProgress(g: Float): Int = ((g - 0.5f) * 10f).toInt().coerceIn(0, 55)
    private fun progressToGap(p: Int): Float = 0.5f + p / 10f

    private fun wireSizingControls() {
        // Height
        binding.heightSlider.progress = heightToProgress(prefs.heightScale)
        binding.heightValue.text = formatPercent(prefs.heightScale)
        binding.heightSlider.setOnSeekBarChangeListener(simpleSeek { p ->
            val v = progressToHeight(p)
            prefs.heightScale = v
            binding.heightValue.text = formatPercent(v)
        })

        // Width margin
        binding.widthSlider.progress = paddingToProgress(prefs.horizontalPadding)
        binding.widthValue.text = formatPercent(prefs.horizontalPadding)
        binding.widthSlider.setOnSeekBarChangeListener(simpleSeek { p ->
            val v = progressToPadding(p)
            prefs.horizontalPadding = v
            binding.widthValue.text = formatPercent(v)
        })

        // Split toggle + gap slider
        binding.splitSwitch.isChecked = prefs.splitEnabled
        binding.splitGapContainer.visibility =
            if (prefs.splitEnabled) View.VISIBLE else View.GONE
        binding.splitSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.splitEnabled = checked
            binding.splitGapContainer.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.splitGapSlider.progress = gapToProgress(prefs.splitGapWeight)
        binding.splitGapValue.text = String.format("%.1f", prefs.splitGapWeight)
        binding.splitGapSlider.setOnSeekBarChangeListener(simpleSeek { p ->
            val v = progressToGap(p)
            prefs.splitGapWeight = v
            binding.splitGapValue.text = String.format("%.1f", v)
        })
    }

    private fun formatPercent(v: Float): String = "${(v * 100).toInt()}%"

    private fun simpleSeek(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onProgress(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
