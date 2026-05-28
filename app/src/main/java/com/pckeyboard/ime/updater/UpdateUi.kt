package com.pckeyboard.ime.updater

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pckeyboard.ime.BuildConfig
import com.pckeyboard.ime.R
import kotlinx.coroutines.launch

/**
 * Shared update-check helpers used by SetupActivity (background check on
 * launch) and SettingsActivity (background check on launch + manual
 * "Check now" button that bypasses the throttle).
 */
object UpdateUi {

    /** Background auto-check. Silently does nothing on throttle / no
     *  update / network error so we don't pester the user. */
    fun runAutoCheck(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val result = UpdateChecker(activity).checkForUpdate(force = false)
            if (result is UpdateChecker.Result.Available && !activity.isFinishing) {
                showUpdateDialog(activity, result.info)
            }
        }
    }

    /** Manual user-triggered check. Always hits the network, and always
     *  gives feedback so the user knows it ran. */
    fun runManualCheck(activity: AppCompatActivity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            when (val result = UpdateChecker(activity).checkForUpdate(force = true)) {
                is UpdateChecker.Result.Available -> {
                    if (!activity.isFinishing) showUpdateDialog(activity, result.info)
                }
                UpdateChecker.Result.UpToDate ->
                    Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show()
                UpdateChecker.Result.Error ->
                    Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show()
                UpdateChecker.Result.Throttled -> {
                    // Can't happen with force=true, but keep the branch exhaustive.
                }
            }
        }
    }

    private fun showUpdateDialog(activity: AppCompatActivity, info: UpdateInfo) {
        val notes = info.releaseNotes.ifBlank { activity.getString(R.string.update_no_notes) }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_title, info.versionName))
            .setMessage(
                activity.getString(R.string.update_message, info.versionName, BuildConfig.VERSION_NAME) +
                    "\n\n" + notes.take(1000)
            )
            .setPositiveButton(R.string.update_btn_download) { _, _ ->
                val id = UpdateChecker(activity).downloadApk(info)
                if (id != null) {
                    Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
                } else {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
                    )
                }
            }
            .setNegativeButton(R.string.update_btn_later, null)
            .setNeutralButton(R.string.update_btn_view_page) { _, _ ->
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
                )
            }
            .show()
    }
}
