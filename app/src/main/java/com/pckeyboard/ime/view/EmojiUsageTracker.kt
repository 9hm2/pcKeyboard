package com.pckeyboard.ime.view

import android.content.Context
import androidx.preference.PreferenceManager
import com.pckeyboard.ime.util.directBootSafeContext

/**
 * Persistent emoji frequency tracker. Each commit increments a counter
 * keyed by the emoji string; [recents] returns the top-N emojis ordered
 * by use count, tie-broken by most-recently used. Falls back to a small
 * "starter pack" when the user hasn't tapped anything yet so the Recents
 * tab isn't empty on first open.
 */
class EmojiUsageTracker(context: Context) {

    private val appContext = context.applicationContext
    private val prefs
        get() = PreferenceManager.getDefaultSharedPreferences(
            appContext.directBootSafeContext()
        )

    fun recordUse(emoji: String) {
        if (emoji.isEmpty()) return
        val count = prefs.getInt(countKey(emoji), 0) + 1
        prefs.edit()
            .putInt(countKey(emoji), count)
            .putLong(timeKey(emoji), System.currentTimeMillis())
            .apply()
    }

    fun recents(n: Int = MAX_RECENTS): List<String> {
        val ranked = prefs.all.asSequence()
            .filter { it.key.startsWith(COUNT_PREFIX) && it.value is Int }
            .map { entry ->
                val emoji = entry.key.removePrefix(COUNT_PREFIX)
                val count = entry.value as Int
                val ts = prefs.getLong(timeKey(emoji), 0L)
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
