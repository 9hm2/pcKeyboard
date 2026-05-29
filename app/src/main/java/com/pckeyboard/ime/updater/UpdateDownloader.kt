package com.pckeyboard.ime.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streams the GitHub release APK into the app's private cache directory
 * (so we don't need any storage permission and the file survives a
 * background download cleanly) and then launches the system installer
 * via FileProvider.
 *
 * Used by both:
 *   - the foreground "Download" button in [UpdateUi]'s dialog, which
 *     subscribes to [download] for live percent updates, and
 *   - the background [UpdateCheckWorker], which just awaits [State.Done]
 *     and immediately fires [startInstall].
 */
object UpdateDownloader {

    sealed class State {
        data class Progress(
            val percent: Int,
            val bytesDownloaded: Long,
            val totalBytes: Long
        ) : State()
        data class Done(val file: File) : State()
        data class Error(val message: String) : State()
    }

    /** Destination file used for this update — overwritten on each fresh
     *  attempt so stale partial downloads can't accumulate in the cache. */
    fun apkFileFor(context: Context, info: UpdateInfo): File =
        File(context.cacheDir, "update-${info.versionName.sanitiseForFilename()}.apk")

    /**
     * Streams the APK into [apkFileFor]'s destination, emitting Progress
     * states ~every 1 % of completion plus a final Done (or Error). The
     * caller is responsible for collecting on the main scope when it
     * wants to drive a UI; the actual IO is on Dispatchers.IO.
     */
    fun download(context: Context, info: UpdateInfo): Flow<State> = flow {
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            emit(State.Error("No APK asset on this release"))
            return@flow
        }
        val dest = apkFileFor(context, info)
        if (dest.exists()) dest.delete()

        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "pcKeyboard")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    emit(State.Error("HTTP ${conn.responseCode}"))
                    return@flow
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        // Emit a 0 % tick up front so a UI listener can
                        // render the "started" state immediately.
                        if (total > 0) emit(State.Progress(0, 0, total))
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    emit(State.Progress(pct, downloaded, total))
                                }
                            }
                        }
                    }
                }
                emit(State.Done(dest))
            } finally {
                conn.disconnect()
            }
        } catch (e: Throwable) {
            emit(State.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Hands a content:// URI for [file] to the system package installer.
     * Works from any context (Activity, Service, Worker) because we set
     * NEW_TASK and GRANT_READ_URI_PERMISSION.
     */
    fun startInstall(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            // No installer available, or the device blocked the intent —
            // the file is still in the cache for a retry.
        }
    }

    private fun String.sanitiseForFilename(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")
}
