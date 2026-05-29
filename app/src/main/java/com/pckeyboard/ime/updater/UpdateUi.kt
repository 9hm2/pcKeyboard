package com.pckeyboard.ime.updater

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pckeyboard.ime.BuildConfig
import com.pckeyboard.ime.R
import kotlinx.coroutines.flow.collect
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
                if (info.apkUrl != null) {
                    runDownloadWithProgress(activity, info)
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

    private fun runDownloadWithProgress(activity: AppCompatActivity, info: UpdateInfo) {
        // Build a small progress sheet so the user can see the download
        // grow in-app instead of relying on a system notification.
        val pad = (activity.resources.displayMetrics.density * 24).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
        }
        val label = TextView(activity).apply {
            text = activity.getString(R.string.update_downloading)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, pad / 2)
        }
        container.addView(label, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        container.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_title, info.versionName))
            .setView(container)
            .setCancelable(false)
            .create()
        dialog.show()

        activity.lifecycleScope.launch {
            UpdateDownloader.download(activity, info).collect { state ->
                if (activity.isFinishing) return@collect
                when (state) {
                    is UpdateDownloader.State.Progress -> {
                        bar.isIndeterminate = false
                        bar.progress = state.percent
                        label.text = activity.getString(
                            R.string.update_download_progress,
                            state.percent,
                            formatMb(state.bytesDownloaded),
                            formatMb(state.totalBytes)
                        )
                    }
                    is UpdateDownloader.State.Done -> {
                        if (dialog.isShowing) dialog.dismiss()
                        UpdateDownloader.startInstall(activity, state.file)
                    }
                    is UpdateDownloader.State.Error -> {
                        if (dialog.isShowing) dialog.dismiss()
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.update_download_failed, state.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun formatMb(bytes: Long): String {
        val mb = bytes / 1_048_576.0
        return String.format("%.1f MB", mb)
    }
}
