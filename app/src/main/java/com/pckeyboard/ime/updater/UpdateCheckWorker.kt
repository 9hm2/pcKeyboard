package com.pckeyboard.ime.updater

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pckeyboard.ime.settings.KeyboardPrefs
import kotlinx.coroutines.flow.collect

/**
 * Periodic background check for new releases. Skips out when the keyboard
 * isn't the default IME (the user only cares about updates while they're
 * actually using us), and skips out when the user has switched off
 * autoUpdateEnabled in Settings.
 *
 * When a newer release is found the APK is streamed into the app's own
 * cache via [UpdateDownloader]; on completion the worker fires the
 * system installer through a FileProvider URI so the user just sees the
 * standard "Update pcKeyboard?" prompt.
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
            UpdateDownloader.download(ctx, result.info).collect { state ->
                if (state is UpdateDownloader.State.Done) {
                    UpdateDownloader.startInstall(ctx, state.file)
                }
            }
        }
        return Result.success()
    }

    companion object {
        fun isDefaultKeyboard(context: Context): Boolean {
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return false
            return current.startsWith(context.packageName)
        }
    }
}
