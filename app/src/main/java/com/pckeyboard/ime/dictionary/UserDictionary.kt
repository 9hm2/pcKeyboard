package com.pckeyboard.ime.dictionary

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject

/**
 * Per-language learning dictionary of the user's own words.
 *
 * Every committed word the main dictionary doesn't know gets counted;
 * once a word has been typed [LEARN_THRESHOLD] times it becomes
 * "known": it shows up in the suggestion strip (ranked by how often the
 * user types it) and is never auto-corrected again. Undoing an
 * auto-correction learns the original word immediately.
 *
 * Stored as a JSON object (word → use count) in **credential-encrypted**
 * SharedPreferences — it's literally a record of what the user types,
 * so like the clipboard history it stays locked until first unlock and
 * every access degrades gracefully during Direct Boot.
 */
class UserDictionary(context: Context, private val langId: String) {

    private val appContext = context.applicationContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(appContext)

    /** Lowercase word → number of times the user committed it. */
    private val counts: MutableMap<String, Int> = load()

    private fun key() = "user_dict_$langId"

    private fun load(): MutableMap<String, Int> {
        return try {
            val raw = prefs.getString(key(), null) ?: return mutableMapOf()
            val obj = JSONObject(raw)
            val m = mutableMapOf<String, Int>()
            for (k in obj.keys()) m[k] = obj.optInt(k, 1)
            m
        } catch (_: Throwable) {
            mutableMapOf()
        }
    }

    private fun persist() {
        try {
            val obj = JSONObject()
            for ((w, c) in counts) obj.put(w, c)
            prefs.edit().putString(key(), obj.toString()).apply()
        } catch (_: Throwable) {
            // CE storage not available (Direct Boot) — keep in memory only.
        }
    }

    /** Counts one committed use of [word] (lowercase). */
    fun recordUse(word: String) {
        counts[word] = (counts[word] ?: 0) + 1
        if (counts.size > MAX_WORDS) prune()
        persist()
    }

    /** Marks [word] as known right away — used when the user undoes an
     *  auto-correction, which is the strongest possible "I meant that". */
    fun forceLearn(word: String) {
        counts[word] = maxOf(counts[word] ?: 0, LEARN_THRESHOLD)
        persist()
    }

    fun isKnown(word: String): Boolean = (counts[word] ?: 0) >= LEARN_THRESHOLD

    /** Use count when [word] is known, else 0. */
    fun knownCount(word: String): Int =
        (counts[word] ?: 0).let { if (it >= LEARN_THRESHOLD) it else 0 }

    /**
     * Known words starting with [prefix] (accent-insensitively, like the
     * main dictionary's completions), most-used first. The map is small
     * (≤ [MAX_WORDS]) so a linear scan is fine.
     */
    fun knownCompletions(prefix: String, n: Int): List<Pair<String, Int>> {
        if (prefix.isEmpty() || n <= 0) return emptyList()
        val skeletonPrefix = WordDictionary.deaccent(prefix)
        return counts.entries.asSequence()
            .filter { it.value >= LEARN_THRESHOLD }
            .filter {
                it.key.length > prefix.length &&
                    WordDictionary.deaccent(it.key).startsWith(skeletonPrefix)
            }
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
            .toList()
    }

    /** Drops the least-used half so frequent words keep their standing. */
    private fun prune() {
        val keep = counts.entries.sortedByDescending { it.value }.take(MAX_WORDS / 2)
        counts.clear()
        for ((w, c) in keep) counts[w] = c
    }

    companion object {
        /** Times a word must be committed before it's trusted. */
        const val LEARN_THRESHOLD = 2
        /** Hard cap on stored words per language. */
        const val MAX_WORDS = 1500
    }
}
