package com.pckeyboard.ime.editor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.pckeyboard.ime.databinding.ActivityThemeEditorBinding
import com.pckeyboard.ime.layout.EnglishLayout
import com.pckeyboard.ime.settings.addSystemBarBottomEndMargin
import com.pckeyboard.ime.settings.addSystemBarBottomPadding
import com.pckeyboard.ime.theme.KeyboardTheme
import com.pckeyboard.ime.theme.ThemeRepository
import com.pckeyboard.ime.theme.Themes
import java.util.UUID

/**
 * Lets a user assemble their own theme: tweak colors, key radius, spacing,
 * preview live against the actual keyboard view, then save.
 */
class ThemeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeEditorBinding
    private lateinit var repo: ThemeRepository
    private lateinit var working: KeyboardTheme
    private var editingExistingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        repo = ThemeRepository(this)
        binding.scrollView.addSystemBarBottomPadding()
        binding.btnSave.addSystemBarBottomEndMargin()

        val incomingId = intent.getStringExtra(EXTRA_THEME_ID)
        working = if (incomingId != null && repo.getThemeById(incomingId) != null) {
            editingExistingId = incomingId
            repo.getThemeById(incomingId)!!.copy()
        } else {
            Themes.DARK.copy(id = "custom_${UUID.randomUUID().toString().take(8)}", name = "My Theme")
        }

        binding.nameInput.setText(working.name)
        binding.nameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                working = working.copy(name = s?.toString().orEmpty().ifBlank { "My Theme" })
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        wireColorInput(binding.colorBackground, working.backgroundColor) {
            working = working.copy(backgroundColor = it); refreshPreview()
        }
        wireColorInput(binding.colorKey, working.keyBackgroundColor) {
            working = working.copy(keyBackgroundColor = it); refreshPreview()
        }
        wireColorInput(binding.colorKeyPressed, working.keyPressedColor) {
            working = working.copy(keyPressedColor = it); refreshPreview()
        }
        wireColorInput(binding.colorText, working.keyTextColor) {
            working = working.copy(keyTextColor = it); refreshPreview()
        }
        wireColorInput(binding.colorSecondaryText, working.secondaryTextColor) {
            working = working.copy(secondaryTextColor = it); refreshPreview()
        }
        wireColorInput(binding.colorModifier, working.modifierKeyColor) {
            working = working.copy(modifierKeyColor = it); refreshPreview()
        }
        wireColorInput(binding.colorModifierText, working.modifierTextColor) {
            working = working.copy(modifierTextColor = it); refreshPreview()
        }
        wireColorInput(binding.colorAccent, working.accentColor) {
            working = working.copy(accentColor = it); refreshPreview()
        }
        wireColorInput(binding.colorAccentText, working.accentTextColor) {
            working = working.copy(accentTextColor = it); refreshPreview()
        }

        binding.radiusSlider.progress = working.keyCornerRadiusDp
        binding.radiusValue.text = working.keyCornerRadiusDp.toString()
        binding.radiusSlider.setOnSeekBarChangeListener(simpleSeekListener {
            working = working.copy(keyCornerRadiusDp = it)
            binding.radiusValue.text = it.toString()
            refreshPreview()
        })

        binding.spacingSlider.progress = working.keySpacingDp
        binding.spacingValue.text = working.keySpacingDp.toString()
        binding.spacingSlider.setOnSeekBarChangeListener(simpleSeekListener {
            working = working.copy(keySpacingDp = it)
            binding.spacingValue.text = it.toString()
            refreshPreview()
        })

        binding.btnSave.setOnClickListener {
            repo.saveCustomTheme(working)
            repo.selectTheme(working.id)
            finish()
        }

        binding.preview.bind(EnglishLayout.main(), working)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun refreshPreview() {
        binding.preview.updateTheme(working)
    }

    private fun wireColorInput(input: android.widget.EditText, initial: Int, onChange: (Int) -> Unit) {
        input.setText(String.format("#%08X", initial))
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val txt = s?.toString().orEmpty().trim()
                val parsed = parseHexColor(txt) ?: return
                onChange(parsed)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun parseHexColor(s: String): Int? {
        var str = s
        if (!str.startsWith("#")) str = "#$str"
        return try { android.graphics.Color.parseColor(str) } catch (_: Throwable) { null }
    }

    private fun simpleSeekListener(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onProgress(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    companion object {
        const val EXTRA_THEME_ID = "extra_theme_id"
    }
}
