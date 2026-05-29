package com.pckeyboard.ime.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for DownloadManager completion broadcasts. When the completed
 * download matches the one [UpdateCheckWorker] enqueued, fire the system
 * installer for the APK. Manifest-registered so it survives the app
 * process being killed between download start and completion.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (completedId == -1L) return

        val prefs = context.getSharedPreferences(
            UpdateCheckWorker.STATE_PREFS, Context.MODE_PRIVATE
        )
        val pendingId = prefs.getLong(UpdateCheckWorker.KEY_PENDING_ID, -1L)
        if (completedId != pendingId) return
        prefs.edit().remove(UpdateCheckWorker.KEY_PENDING_ID).apply()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return
        val uri = dm.getUriForDownloadedFile(completedId) ?: return

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(installIntent)
        } catch (_: Throwable) {
            // No installer available, or device blocked the intent — silently
            // give up; the user can still tap the download notification.
        }
    }
}
