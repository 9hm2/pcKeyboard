package com.pckeyboard.ime.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.preference.PreferenceManager
import com.pckeyboard.ime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls the GitHub releases API for the latest published APK and, if newer
 * than the installed build, returns an [UpdateInfo] the UI can offer to the
 * user. Caches the last check timestamp to avoid hammering the API.
 *
 * The CI workflow publishes releases under the same repo path; the
 * matching .apk asset's `browser_download_url` is what we feed to
 * [DownloadManager] when the user accepts the prompt.
 */
class UpdateChecker(private val context: Context) {

    private val prefs =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /**
     * @param force ignore the per-12h throttle cache and check immediately.
     * @return non-null only when the remote release is strictly newer than
     *   the installed build.
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return@withContext null
        }
        try {
            val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "pcKeyboard")
                connectTimeout = 5_000
                readTimeout = 10_000
            }
            try {
                if (conn.responseCode != 200) return@withContext null
                val raw = conn.inputStream.bufferedReader().readText()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

                val obj = JSONObject(raw)
                val tag = obj.optString("tag_name").removePrefix("v").trim()
                if (tag.isEmpty()) return@withContext null
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null

                val body = obj.optString("body", "").trim()
                val pageUrl = obj.optString("html_url", null) ?: REPO_RELEASES_URL
                val assets = obj.optJSONArray("assets") ?: return@withContext null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
                UpdateInfo(
                    versionName = tag,
                    releaseNotes = body,
                    apkUrl = apkUrl,
                    releasePageUrl = pageUrl
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Enqueue an APK download via [DownloadManager]. Returns the download id
     *  (or null if no APK asset is available — caller should open the
     *  release page instead). */
    fun downloadApk(info: UpdateInfo): Long? {
        val url = info.apkUrl ?: return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("pcKeyboard ${info.versionName}")
            setDescription("Downloading update")
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "pckeyboard-${info.versionName}.apk"
            )
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
        }
        return dm.enqueue(request)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.', '-').mapNotNull { it.toIntOrNull() }
        val c = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    companion object {
        private const val REPO = "9hm2/pcKeyboard"
        private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val REPO_RELEASES_URL = "https://github.com/$REPO/releases/latest"
        private const val KEY_LAST_CHECK = "update_last_check_ms"
        private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000  // 12 hours
    }
}

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String?,
    val releasePageUrl: String
)
