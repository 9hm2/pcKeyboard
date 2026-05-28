package com.pckeyboard.ime.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.pckeyboard.ime.R
import com.pckeyboard.ime.databinding.ActivitySetupBinding
import com.pckeyboard.ime.updater.UpdateUi

/**
 * Onboarding screen: gets the keyboard enabled and selected as the active IME,
 * and then routes the user into settings or the theme editor. Also runs the
 * background update check on launch.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.addSystemBarPadding()

        binding.btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.btnSelect.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        UpdateUi.runAutoCheck(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        binding.statusEnabled.text =
            if (enabled) getString(R.string.setup_status_enabled_yes)
            else getString(R.string.setup_status_enabled_no)
        binding.btnSelect.isEnabled = enabled
    }
}
