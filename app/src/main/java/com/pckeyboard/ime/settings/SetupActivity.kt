package com.pckeyboard.ime.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pckeyboard.ime.BuildConfig
import com.pckeyboard.ime.R
import com.pckeyboard.ime.databinding.ActivitySetupBinding
import com.pckeyboard.ime.updater.UpdateChecker
import com.pckeyboard.ime.updater.UpdateInfo
import kotlinx.coroutines.launch

/**
 * Onboarding screen: gets the keyboard enabled and selected as the active IME,
 * and then routes the user into settings or the theme editor. Also runs the
 * background update check on launch.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private var updateDialogShown = false

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

        // Background update check — silent on failure / no update, throttled
        // to one network call per 12 hours by UpdateChecker.
        lifecycleScope.launch {
            val info = UpdateChecker(this@SetupActivity).checkForUpdate() ?: return@launch
            if (!updateDialogShown && !isFinishing) {
                updateDialogShown = true
                showUpdateDialog(info)
            }
        }
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

    private fun showUpdateDialog(info: UpdateInfo) {
        val notes = info.releaseNotes.ifBlank { getString(R.string.update_no_notes) }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_title, info.versionName))
            .setMessage(
                getString(R.string.update_message, info.versionName, BuildConfig.VERSION_NAME) +
                    "\n\n" + notes.take(1000)
            )
            .setPositiveButton(R.string.update_btn_download) { _, _ -> startDownload(info) }
            .setNegativeButton(R.string.update_btn_later, null)
            .setNeutralButton(R.string.update_btn_view_page) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl)))
            }
            .show()
    }

    private fun startDownload(info: UpdateInfo) {
        val id = UpdateChecker(this).downloadApk(info)
        if (id != null) {
            Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        } else {
            // No APK asset on the release — fall back to opening the page.
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl)))
        }
    }
}
