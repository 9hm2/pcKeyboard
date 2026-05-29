package com.pckeyboard.ime.updater

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pckeyboard.ime.settings.KeyboardPrefs

/**
 * Periodic background check for new releases. Skips out when the keyboard
 * isn't the default IME (the user only cares about updates while they're
 * actually using us), and skips out when the user has switched off
 * autoUpdateEnabled in Settings.
 *
 * When a newer release is found the APK is enqueued via [DownloadManager]
 * (silent download); on completion [UpdateInstallReceiver] fires the
 * system installer to ask the user to confirm the install.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = KeyboardPrefs(ctx)
        if (!prefs.autoUpdateEnabled) return Result.success()
        if (!isDefaultKeyboard(ctx)) return Result.success()

        val checker = UpdateChecker(ctx)
        val result = checker.checkForUpdate(force = true)
        if (result is UpdateChecker.Result.Available) {
            val downloadId = checker.downloadApk(result.info)
            if (downloadId != null) {
                ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_PENDING_ID, downloadId)
                    .apply()
            }
        }
        return Result.success()
    }

    companion object {
        const val STATE_PREFS = "pck_update_state"
        const val KEY_PENDING_ID = "pending_download_id"

        fun isDefaultKeyboard(context: Context): Boolean {
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return false
            return current.startsWith(context.packageName)
        }
    }
}
