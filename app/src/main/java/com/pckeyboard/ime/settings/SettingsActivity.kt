package com.pckeyboard.ime.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pckeyboard.ime.BuildConfig
import com.pckeyboard.ime.R
import com.pckeyboard.ime.databinding.ActivitySettingsBinding
import com.pckeyboard.ime.editor.ThemeEditorActivity
import com.pckeyboard.ime.theme.KeyboardTheme
import com.pckeyboard.ime.theme.ThemeRepository
import com.pckeyboard.ime.updater.UpdateUi

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

        binding.installedVersion.text =
            getString(R.string.settings_installed_version, BuildConfig.VERSION_NAME)
        binding.btnCheckUpdates.setOnClickListener { UpdateUi.runManualCheck(this) }

        wireSizingControls()
        buildLanguageList()
        // Silent background auto-check too — same throttle as SetupActivity.
        UpdateUi.runAutoCheck(this)
    }

    /** Populate the Languages card with one MaterialSwitch per shipped pack;
     *  toggles write through to KeyboardPrefs.enabledLanguages so the IME
     *  globe-cycle picks them up on next tap. */
    private fun buildLanguageList() {
        val container = binding.languageList
        container.removeAllViews()
        val packs = com.pckeyboard.ime.layout.LayoutRegistry.available
        val enabled = prefs.enabledLanguages.toMutableSet()
        for (pack in packs) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val label = android.widget.TextView(this).apply {
                text = pack.displayName
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            }
            val toggle = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
                isChecked = pack.id in enabled
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabled.add(pack.id) else enabled.remove(pack.id)
                    // Always keep at least one enabled (English fallback).
                    val safe = if (enabled.isEmpty()) {
                        isChecked = true
                        enabled.add(pack.id)
                        enabled
                    } else enabled
                    prefs.enabledLanguages = safe
                }
            }
            row.addView(label)
            row.addView(toggle)
            container.addView(row)
        }
        val hint = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_languages_hint)
            setPadding(dpToPx(12), 0, dpToPx(12), dpToPx(8))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        }
        container.addView(hint)
    }

    private fun dpToPx(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

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

        // Long-press delay: 150–1000 ms, slider in 10 ms steps (0..85 → +150).
        binding.longPressSlider.progress = (prefs.longPressDelayMs - 150) / 10
        binding.longPressValue.text = "${prefs.longPressDelayMs} ms"
        binding.longPressSlider.setOnSeekBarChangeListener(simpleSeek { p ->
            val v = 150 + p * 10
            prefs.longPressDelayMs = v
            binding.longPressValue.text = "$v ms"
        })
    }

    private fun formatPercent(v: Float): String = "${(v * 100).toInt()}%"

    private fun simpleSeek(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onProgress(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
