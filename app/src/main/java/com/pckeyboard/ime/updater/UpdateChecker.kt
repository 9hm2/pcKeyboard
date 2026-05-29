package com.pckeyboard.ime.updater

import android.content.Context
import androidx.preference.PreferenceManager
import com.pckeyboard.ime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls the GitHub releases API for the latest published APK.
 *
 * The CI workflow publishes a release on every run. The in-app updater
 * fetches that release, compares the tag to [BuildConfig.VERSION_NAME]
 * and asks the UI to surface an [UpdateInfo] when newer.
 *
 * The throttle cache only applies to *automatic* checks (force = false);
 * manual "Check now" taps always hit the network so users aren't stuck
 * waiting 12 hours after a stale auto-check.
 */
class UpdateChecker(private val context: Context) {

    private val prefs =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    sealed class Result {
        /** A newer release is available. */
        data class Available(val info: UpdateInfo) : Result()
        /** Installed version matches or exceeds the latest release. */
        object UpToDate : Result()
        /** Network / API failure. */
        object Error : Result()
        /** Auto-check skipped because the throttle window hasn't elapsed. */
        object Throttled : Result()
    }

    /**
     * @param force ignore the throttle cache; hits the network unconditionally.
     */
    suspend fun checkForUpdate(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return@withContext Result.Throttled
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
                if (conn.responseCode != 200) return@withContext Result.Error
                val raw = conn.inputStream.bufferedReader().readText()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

                val obj = JSONObject(raw)
                val tag = obj.optString("tag_name").removePrefix("v").trim()
                if (tag.isEmpty()) return@withContext Result.Error
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext Result.UpToDate

                val body = obj.optString("body", "").trim()
                val pageUrl = obj.optString("html_url", null) ?: REPO_RELEASES_URL
                val assets = obj.optJSONArray("assets") ?: return@withContext Result.Error
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
                Result.Available(
                    UpdateInfo(
                        versionName = tag,
                        releaseNotes = body,
                        apkUrl = apkUrl,
                        releasePageUrl = pageUrl
                    )
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            Result.Error
        }
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
