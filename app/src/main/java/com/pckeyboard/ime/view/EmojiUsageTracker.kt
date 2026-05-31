package com.pckeyboard.ime.view

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Persistent emoji frequency tracker. Each commit increments a counter
 * keyed by the emoji string; [recents] returns the top-N emojis ordered
 * by use count, tie-broken by most-recently used. Falls back to a small
 * "starter pack" when the user hasn't tapped anything yet so the Recents
 * tab isn't empty on first open.
 *
 * Stays in **credential-encrypted** storage — the per-user emoji
 * history is personal data and shouldn't be readable on the lock
 * screen. On Direct Boot every access throws; the public methods
 * swallow that and degrade to the starter pack / no-op so the picker
 * still renders something usable.
 */
class EmojiUsageTracker(context: Context) {

    private val appContext = context.applicationContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(appContext)

    fun recordUse(emoji: String) {
        if (emoji.isEmpty()) return
        try {
            val count = prefs.getInt(countKey(emoji), 0) + 1
            prefs.edit()
                .putInt(countKey(emoji), count)
                .putLong(timeKey(emoji), System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {
            // CE storage not yet available (Direct Boot) — silently drop.
        }
    }

    fun recents(n: Int = MAX_RECENTS): List<String> {
        val all = try { prefs.all } catch (_: Throwable) { return STARTER }
        val ranked = all.asSequence()
            .filter { it.key.startsWith(COUNT_PREFIX) && it.value is Int }
            .map { entry ->
                val emoji = entry.key.removePrefix(COUNT_PREFIX)
                val count = entry.value as Int
                val ts = try { prefs.getLong(timeKey(emoji), 0L) } catch (_: Throwable) { 0L }
                Triple(emoji, count, ts)
            }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }
                .thenByDescending { it.third })
            .take(n)
            .map { it.first }
            .toList()
        return if (ranked.isNotEmpty()) ranked else STARTER
    }

    private fun countKey(emoji: String) = "$COUNT_PREFIX$emoji"
    private fun timeKey(emoji: String) = "$TIME_PREFIX$emoji"

    companion object {
        private const val COUNT_PREFIX = "emoji_n_"
        private const val TIME_PREFIX = "emoji_t_"
        private const val MAX_RECENTS = 48

        /** Shown on first open before the user has tapped anything. */
        private val STARTER = listOf(
            "😀", "😂", "🥰", "😍", "😎", "🤔", "👍", "👎",
            "🙏", "👋", "❤️", "🔥", "✨", "🎉", "💯", "🙄",
            "😢", "😭", "😡", "👌", "👏", "🤝", "💪", "🤞"
        )
    }
}
